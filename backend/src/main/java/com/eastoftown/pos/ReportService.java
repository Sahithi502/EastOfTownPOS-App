package com.eastoftown.pos;

import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    public static final ZoneId ZONE=ZoneId.of("Asia/Kolkata");
    public record Range(LocalDate from,LocalDate to,String start,String end){}
    public record Column(String key,String label,String kind){}
    public record Report(String title,Range range,String note,List<Column> columns,List<Map<String,Object>> rows){}
    final ReportRepository repo;
    public ReportService(ReportRepository repo){this.repo=repo;}
    public static Range range(String preset,LocalDate from,LocalDate to,LocalDate today){
        switch(preset){
            case "today" -> {from=today;to=today;}
            case "yesterday" -> {from=today.minusDays(1);to=from;}
            case "week" -> {from=today.with(java.time.DayOfWeek.MONDAY);to=today;}
            case "month" -> {from=today.withDayOfMonth(1);to=today;}
            case "year" -> {from=today.withDayOfYear(1);to=today;}
            case "financial-year" -> {from=LocalDate.of(today.getMonthValue()<4?today.getYear()-1:today.getYear(),4,1);to=today;}
            case "custom" -> {if(from==null || to==null)throw new IllegalArgumentException("Choose both dates for a custom range");}
            default -> throw new IllegalArgumentException("Unknown date filter");
        }
        if(from.isAfter(to) || from.getYear()<1900 || to.getYear()>9998)throw new IllegalArgumentException("Choose a valid date range with start on or before end");
        // Second-precision prefixes compare consistently with legacy ISO instants with or without fractions.
        var format=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);
        return new Range(from,to,format.format(from.atStartOfDay(ZONE).toInstant()),format.format(to.plusDays(1).atStartOfDay(ZONE).toInstant()));
    }
    static long n(Map<String,Object> r,String key){return r.get(key)==null?0:((Number)r.get(key)).longValue();}
    static long safe(long value){if(value>9_007_199_254_740_991L || value< -9_007_199_254_740_991L)throw new IllegalArgumentException("Report total exceeds supported display precision. Choose a smaller date range.");return value;}
    static void add(Map<String,Object> row,String key,long amount){row.put(key,safe(Math.addExact(n(row,key),amount)));}
    static long sum(List<Map<String,Object>> rows,String key){long total=0;for(var row:rows)total=safe(Math.addExact(total,n(row,key)));return total;}
    static Map<String,Object> row(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put(pairs[i].toString(),pairs[i+1]);return result;}
    static Column text(String key,String label){return new Column(key,label,"TEXT");}
    static Column integer(String key,String label){return new Column(key,label,"INTEGER");}
    static Column money(String key,String label){return new Column(key,label,"MONEY");}
    @Transactional(readOnly=true) public Object dashboard(Range range){
        var invoices=repo.invoices(range);var payments=repo.payments(range);var inventory=repo.inventory();var totals=salesTotals(invoices);
        var paymentTotals=paymentRows(payments);var inventoryTotals=inventoryTotals(inventory);
        var low=inventory.stream().filter(x->n(x,"active")==1 && n(x,"units")>0 && n(x,"units")<=n(x,"reorder_level")).toList();
        var out=inventory.stream().filter(x->n(x,"active")==1 && n(x,"units")<=0).toList();
        var products=groupItems(repo.items(range),"variants");products.sort(Comparator.comparingLong((Map<String,Object> x)->n(x,"quantity")).reversed().thenComparing(x->x.get("name").toString()));
        return row("range",range,"sales",totals,"payments",paymentTotals,"inventory",inventoryTotals,"lowStockCount",low.size(),"outOfStockCount",out.size(),"lowStock",low.stream().limit(20).toList(),"outOfStock",out.stream().limit(20).toList(),"recentInvoices",invoices.reversed().stream().limit(10).toList(),"bestsellers",products.stream().limit(10).toList());
    }
    static Map<String,Object> salesTotals(List<Map<String,Object>> invoices){long gross=sum(invoices,"subtotal_paise"),discount=sum(invoices,"discount_paise"),total=sum(invoices,"total_paise");return row("gross_paise",gross,"discount_paise",discount,"net_sales_paise",gross-discount,"tax_paise",sum(invoices,"tax_paise"),"total_paise",total,"bills",invoices.size(),"average_paise",invoices.isEmpty()?0:BigDecimal.valueOf(total).divide(BigDecimal.valueOf(invoices.size()),0,RoundingMode.HALF_UP).longValueExact());}
    static Map<String,Object> inventoryTotals(List<Map<String,Object>> inventory){long units=0,value=0,unknown=0;for(var x:inventory){units=safe(Math.addExact(units,n(x,"units")));if(x.get("cost_paise")==null)unknown=safe(Math.addExact(unknown,n(x,"units")));else value=safe(Math.addExact(value,Math.multiplyExact(n(x,"units"),n(x,"cost_paise"))));}return row("units",units,"known_cost_value_paise",value,"unknown_cost_units",unknown);}
    static List<Map<String,Object>> paymentRows(List<Map<String,Object>> payments){var grouped=new TreeMap<String,Map<String,Object>>();for(var p:payments){var x=grouped.computeIfAbsent(p.get("method").toString(),key->row("method",key,"collections_paise",0L,"records",0L));add(x,"collections_paise",n(p,"amount_paise"));add(x,"records",1);}return new ArrayList<>(grouped.values());}
    static List<Map<String,Object>> groupItems(List<Map<String,Object>> items,String type){
        var grouped=new TreeMap<String,Map<String,Object>>();for(var item:items){String key=switch(type){case "categories"->String.valueOf(item.get("category"));case "products"->item.get("product_id").toString();default->item.get("variant_id").toString();};
            var r=grouped.computeIfAbsent(key,k->row("name",type.equals("categories")?item.get("category"):item.get("product_name"),"category",item.get("category"),"sku",item.get("sku"),"size",item.get("size"),"colour",item.get("colour"),"quantity",0L,"gross_paise",0L,"discount_paise",0L,"net_sales_paise",0L,"tax_paise",0L,"total_paise",0L,"known_revenue_paise",0L,"known_cost_paise",0L,"unknown_cost_units",0L));
            r.put("name",type.equals("categories")?item.get("category"):item.get("product_name"));r.put("category",item.get("category"));r.put("sku",item.get("sku"));r.put("size",item.get("size"));r.put("colour",item.get("colour"));
            long gross=Math.multiplyExact(n(item,"unit_price_paise"),n(item,"quantity")),discount=n(item,"discount_paise")+n(item,"bill_discount_paise"),net=gross-discount;
            add(r,"quantity",n(item,"quantity"));add(r,"gross_paise",gross);add(r,"discount_paise",discount);add(r,"net_sales_paise",net);add(r,"tax_paise",n(item,"tax_paise"));add(r,"total_paise",n(item,"line_total_paise"));
            if(item.get("cost_paise")==null)add(r,"unknown_cost_units",n(item,"quantity"));else{add(r,"known_revenue_paise",net);add(r,"known_cost_paise",Math.multiplyExact(n(item,"cost_paise"),n(item,"quantity")));}
            r.put("profit_estimate_paise",n(r,"known_revenue_paise")-n(r,"known_cost_paise"));
        }return new ArrayList<>(grouped.values());
    }
    public static final Set<String> TYPES=Set.of("daily","monthly","yearly","products","categories","variants","payments","stock","inventory","profit","customers","customer-history");
    @Transactional(readOnly=true) public Report report(String type,Range range,String customerId){
        if(!TYPES.contains(type))throw new IllegalArgumentException("Unknown report");
        String note="INR. Indian business dates. Completed invoices only; net sales exclude tax. Returns/refunds will be available in Stage 5.";
        List<Column> amounts=List.of(money("gross_paise","Gross sales"),money("discount_paise","Discounts"),money("net_sales_paise","Net sales ex tax"),money("tax_paise","Tax"),money("total_paise","Invoice value"));
        List<Column> columns=new ArrayList<>();List<Map<String,Object>> rows;
        switch(type){
            case "daily","monthly","yearly" -> {
                columns.add(text("period","Period"));columns.add(integer("bills","Bills"));columns.addAll(amounts);var groups=new TreeMap<String,Map<String,Object>>();
                for(var inv:repo.invoices(range)){String date=Instant.parse(inv.get("created_at").toString()).atZone(ZONE).toLocalDate().toString(),key=date.substring(0,type.equals("daily")?10:type.equals("monthly")?7:4);var r=groups.computeIfAbsent(key,k->row("period",key,"bills",0L,"gross_paise",0L,"discount_paise",0L,"net_sales_paise",0L,"tax_paise",0L,"total_paise",0L));add(r,"bills",1);add(r,"gross_paise",n(inv,"subtotal_paise"));add(r,"discount_paise",n(inv,"discount_paise"));add(r,"net_sales_paise",n(inv,"subtotal_paise")-n(inv,"discount_paise"));add(r,"tax_paise",n(inv,"tax_paise"));add(r,"total_paise",n(inv,"total_paise"));}rows=new ArrayList<>(groups.values());
            }
            case "products","categories","variants","profit" -> {
                columns.add(text("name",type.equals("categories")?"Category":"Product"));if(!type.equals("categories"))columns.add(text("category","Category"));
                if(type.equals("variants")||type.equals("profit"))columns.addAll(List.of(text("sku","SKU"),text("size","Size"),text("colour","Colour")));
                columns.add(integer("quantity","Units sold"));columns.addAll(amounts);rows=groupItems(repo.items(range),type);
                if(type.equals("profit")){columns.addAll(List.of(money("known_revenue_paise","Revenue with known cost"),money("known_cost_paise","Known cost of sales"),money("profit_estimate_paise","Gross profit estimate"),integer("unknown_cost_units","Units missing cost")));note+=" Profit uses cost snapshotted at sale, after all discounts and excluding tax; excludes units with unknown costs. It is not net profit and excludes operating expenses.";}
            }
            case "payments" -> {columns.addAll(List.of(text("method","Method"),integer("records","Payment records"),money("collections_paise","Collections")));rows=paymentRows(repo.payments(range));note+=" Split payments count as separate method records; collections exclude cash change.";}
            case "inventory" -> {columns.addAll(List.of(text("product","Product"),text("category","Category"),text("sku","SKU"),text("size","Size"),text("colour","Colour"),integer("active","Active (1/0)"),integer("units","Current units"),money("cost_paise","Current unit cost"),money("value_paise","Known cost value"),integer("unknown_cost_units","Units missing cost")));rows=repo.inventory();for(var r:rows){r.put("value_paise",r.get("cost_paise")==null?null:Math.multiplyExact(n(r,"units"),n(r,"cost_paise")));r.put("unknown_cost_units",r.get("cost_paise")==null?n(r,"units"):0);}note="CURRENT inventory across active and inactive variants, independent of date filter. Valued at current recorded cost, not historical FIFO. Blank cost/value means unknown; zero cost is a known zero.";}
            case "stock" -> {columns.addAll(List.of(text("created_at","Recorded UTC"),text("effective_date","Adjustment effective date"),text("product","Product"),text("sku","SKU"),text("size","Size"),text("colour","Colour"),integer("quantity","Signed units"),text("kind","Movement"),text("reason","Reason"),text("reference_id","Reference"),text("actor","Actor ID")));rows=repo.movements(range);note="Signed stock ledger filtered by recording time within Indian business dates. Positive adds units; negative removes units. Adjustment effective date is shown separately (blank for other movements).";}
            case "customers" -> {columns.addAll(List.of(text("name","Customer at last sale"),text("mobile","Mobile at last sale"),integer("bills","Completed purchases"),money("total_paise","Amount purchased incl tax"),text("last_purchase","Last purchase UTC"),text("last_invoice","Last invoice")));var groups=new LinkedHashMap<String,Map<String,Object>>();for(var i:repo.invoices(range)){if(i.get("customer_id")==null)continue;var r=groups.computeIfAbsent(i.get("customer_id").toString(),k->row("name",i.get("customer_name"),"mobile",i.get("customer_phone"),"bills",0L,"total_paise",0L));r.put("name",i.get("customer_name"));r.put("mobile",i.get("customer_phone"));r.put("last_purchase",i.get("created_at"));r.put("last_invoice",i.get("invoice_number"));add(r,"bills",1);add(r,"total_paise",n(i,"total_paise"));}rows=new ArrayList<>(groups.values());note+=" Customer totals are for the selected period, exclude guests and group by permanent customer ID.";}
            case "customer-history" -> {
                if(customerId==null || !customerId.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Select a customer for purchase-history export");
                columns.addAll(List.of(text("invoice_number","Invoice"),text("sold_at","Sale UTC"),text("product_name","Product at sale"),text("sku","SKU"),text("size","Size"),text("colour","Colour"),integer("quantity","Quantity"),money("unit_price_paise","Unit price"),money("discount_total_paise","All line discounts"),money("tax_paise","Tax"),money("line_total_paise","Line total"),text("payment_summary","Invoice payments")));
                Map<String,String> methods=new HashMap<>();for(var p:repo.payments(range))methods.merge(p.get("invoice_id").toString(),p.get("method")+" INR "+BigDecimal.valueOf(n(p,"amount_paise"),2).toPlainString(),(a,b)->a+", "+b);
                rows=repo.items(range).stream().filter(x->customerId.equals(x.get("customer_id"))).toList();for(var r:rows){r.put("discount_total_paise",n(r,"discount_paise")+n(r,"bill_discount_paise"));r.put("payment_summary",methods.getOrDefault(r.get("invoice_id").toString(),"No collection"));}note+=" Invoice payment amounts repeat on each product row; do not sum those repeated descriptions.";
            }
            default -> throw new IllegalArgumentException("Unknown report");
        }
        return new Report(type.replace('-',' ')+" report",range,note,columns,rows);
    }
}
