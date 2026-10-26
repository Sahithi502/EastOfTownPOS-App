package com.eastoftown.pos;

import java.util.*;
import org.springframework.stereotype.Repository;

/** SQL ownership for reporting. No SQLite date/grouping functions in business rules. */
@Repository
public class ReportRepository {
    private final CatalogRepository repo;
    public ReportRepository(CatalogRepository repo){this.repo=repo;}
    public List<Map<String,Object>> invoices(ReportService.Range r){
        var rows=new ArrayList<>(bounded("SELECT * FROM invoices WHERE status='COMPLETED' AND created_at>=? AND created_at<? ORDER BY created_at,id",r));
        for(var x:bounded("SELECT i.*,r.id AS reversal_id,r.created_at AS reversal_date,r.total_paise AS refund_total FROM returns r JOIN invoices i ON i.id=r.invoice_id WHERE r.created_at>=? AND r.created_at<? ORDER BY r.created_at,r.id",r)){
            var sums=repo.jdbc().queryForMap("SELECT SUM(gross_paise) AS gross,SUM(discount_paise) AS discount,SUM(tax_paise) AS tax FROM return_items WHERE return_id=?",x.get("reversal_id"));
            x.put("id",x.get("reversal_id"));x.put("created_at",x.get("reversal_date"));x.put("subtotal_paise",-ReportService.n(sums,"gross"));x.put("discount_paise",-ReportService.n(sums,"discount"));x.put("tax_paise",-ReportService.n(sums,"tax"));x.put("total_paise",-ReportService.n(x,"refund_total"));x.put("is_reversal",true);rows.add(x);
        }return limit(rows);
    }
    public List<Map<String,Object>> items(ReportService.Range r){
        var rows=new ArrayList<>(bounded("SELECT x.*,v.product_id,i.invoice_number,i.customer_id,i.created_at AS sold_at FROM invoice_items x JOIN invoices i ON i.id=x.invoice_id JOIN product_variants v ON v.id=x.variant_id WHERE i.status='COMPLETED' AND i.created_at>=? AND i.created_at<? ORDER BY i.created_at,x.line_position,x.id",r));
        for(var x:bounded("SELECT x.*,v.product_id,i.invoice_number,i.customer_id,r.created_at AS sold_at,ri.quantity AS returned_qty,ri.restock,ri.discount_paise AS returned_discount,ri.tax_paise AS returned_tax,ri.total_paise AS returned_total FROM return_items ri JOIN returns r ON r.id=ri.return_id JOIN invoice_items x ON x.id=ri.invoice_item_id JOIN invoices i ON i.id=x.invoice_id JOIN product_variants v ON v.id=x.variant_id WHERE r.created_at>=? AND r.created_at<? ORDER BY r.created_at,ri.id",r)){
            x.put("quantity",-ReportService.n(x,"returned_qty"));x.put("discount_paise",-ReportService.n(x,"returned_discount"));x.put("bill_discount_paise",0L);x.put("tax_paise",-ReportService.n(x,"returned_tax"));x.put("line_total_paise",-ReportService.n(x,"returned_total"));x.put("cost_quantity",ReportService.n(x,"restock")==1?ReportService.n(x,"quantity"):0L);rows.add(x);
        }return limit(rows);
    }
    public List<Map<String,Object>> payments(ReportService.Range r){var rows=new ArrayList<>(bounded("SELECT p.*,i.invoice_number FROM payments p JOIN invoices i ON i.id=p.invoice_id WHERE i.status='COMPLETED' AND p.created_at>=? AND p.created_at<? ORDER BY p.created_at,p.id",r));for(var p:bounded("SELECT f.*,r.invoice_id,i.invoice_number FROM refunds f JOIN returns r ON r.id=f.return_id JOIN invoices i ON i.id=r.invoice_id WHERE f.created_at>=? AND f.created_at<? ORDER BY f.created_at,f.id",r)){p.put("amount_paise",-ReportService.n(p,"amount_paise"));rows.add(p);}return limit(rows);}
    private List<Map<String,Object>> limit(List<Map<String,Object>> rows){if(rows.size()>20000)throw new IllegalArgumentException("This report exceeds 20,000 source records. Choose a shorter date range.");return rows;}
    public List<Map<String,Object>> expenses(ReportService.Range r){return repo.jdbc().queryForList("SELECT e.*,c.name AS category FROM expenses e JOIN expense_categories c ON c.id=e.category_id WHERE e.effective_date>=? AND e.effective_date<=? ORDER BY e.effective_date,e.id LIMIT 20001",r.from().toString(),r.to().toString());}
    public List<Map<String,Object>> reversals(ReportService.Range r){return bounded("SELECT r.*,i.invoice_number FROM returns r JOIN invoices i ON i.id=r.invoice_id WHERE r.created_at>=? AND r.created_at<? ORDER BY r.created_at,r.id",r);}
    public List<Map<String,Object>> movements(ReportService.Range r){return bounded("SELECT s.*,v.sku,v.size,v.colour,p.name AS product,a.effective_date FROM stock_movements s JOIN product_variants v ON v.id=s.variant_id JOIN products p ON p.id=v.product_id LEFT JOIN stock_adjustments a ON a.id=s.reference_id WHERE s.created_at>=? AND s.created_at<? ORDER BY s.created_at,s.id",r);}
    private List<Map<String,Object>> bounded(String sql,ReportService.Range r){var rows=repo.jdbc().queryForList(sql+" LIMIT 20001",r.start(),r.end());if(rows.size()>20000)throw new IllegalArgumentException("This report exceeds 20,000 source records. Choose a shorter date range.");return rows;}
    public List<Map<String,Object>> inventory(){return repo.jdbc().queryForList("""
        SELECT v.id,v.sku,v.size,v.colour,v.active,v.cost_paise,v.selling_paise,v.reorder_level,p.name AS product,c.name AS category,
        COALESCE(s.units,0) AS units FROM product_variants v JOIN products p ON p.id=v.product_id JOIN categories c ON c.id=p.category_id
        LEFT JOIN (SELECT variant_id,SUM(quantity) AS units FROM stock_movements GROUP BY variant_id) s ON s.variant_id=v.id ORDER BY p.name,v.sku
        """);}
    public List<Map<String,Object>> customers(String search,int offset,int limit){String pattern=pattern(search);return repo.jdbc().queryForList("""
        SELECT c.*,COALESCE(s.completed_purchases,0) AS completed_purchases,COALESCE(s.amount_paise,0) AS amount_paise,s.last_purchase,
        (SELECT invoice_number FROM invoices WHERE customer_id=c.id AND status='COMPLETED' ORDER BY created_at DESC,id DESC LIMIT 1) AS last_invoice
        FROM customers c LEFT JOIN (SELECT customer_id,COUNT(*) AS completed_purchases,SUM(total_paise-COALESCE((SELECT SUM(total_paise) FROM returns r WHERE r.invoice_id=invoices.id),0)) AS amount_paise,MAX(created_at) AS last_purchase FROM invoices WHERE status='COMPLETED' GROUP BY customer_id) s ON s.customer_id=c.id
        WHERE c.name LIKE ? ESCAPE '!' OR c.mobile LIKE ? ESCAPE '!' ORDER BY c.name,c.id LIMIT ? OFFSET ?
        """,pattern,pattern,limit,offset);}
    public int customerCount(String search){return repo.jdbc().queryForObject("SELECT COUNT(*) FROM customers WHERE name LIKE ? ESCAPE '!' OR mobile LIKE ? ESCAPE '!'",Integer.class,pattern(search),pattern(search));}
    public List<Map<String,Object>> customerInvoices(String id,int offset,int limit){return repo.jdbc().queryForList("SELECT * FROM invoices WHERE customer_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",id,limit,offset);}
    static String pattern(String value){return "%"+value.replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
}
