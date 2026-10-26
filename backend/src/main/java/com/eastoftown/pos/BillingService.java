package com.eastoftown.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
public class BillingService {
    public static final long MAX_MONEY=9_000_000_000_000L;
    public record Line(@NotBlank String variantId,@Min(1) @Max(1000) int quantity,@Min(0) @Max(MAX_MONEY) long discountPaise,String discountMode,@Min(0) @Max(10000) Integer percentageBps) {
        public Line(String variantId,int quantity,long discountPaise){this(variantId,quantity,discountPaise,null,null);}
    }
    public enum Method { CASH, UPI, CARD }
    public record Payment(@NotNull Method method,@Min(1) @Max(MAX_MONEY) long amountPaise) {}
    public record Cart(@NotEmpty @Size(max=100) List<@NotNull @Valid Line> lines,@Min(0) @Max(MAX_MONEY) long billDiscountPaise,String customerId,String discountMode,@Min(0) @Max(10000) Integer percentageBps) {
        public Cart(List<Line> lines,long billDiscountPaise,String customerId){this(lines,billDiscountPaise,customerId,null,null);}
    }
    public record Checkout(@NotNull UUID requestKey,@NotNull @Valid Cart cart,@NotBlank String quoteToken,@NotNull @Size(max=3) List<@NotNull @Valid Payment> payments) {}
    public record Config(@NotBlank @Size(max=120) String shopName,@NotNull @Size(max=300) String address,@NotNull @Size(max=40) String phone,
        @NotNull @Size(max=120) String thankYou,@Pattern(regexp="[A-Z0-9]{1,10}") @NotNull String invoicePrefix,
        @Min(0) @Max(10000) int billingDiscountBps,@Min(58) @Max(80) int receiptWidthMm,@NotNull @Size(max=200) String printer,boolean autoPrint) {}
    public record CustomerInput(@NotBlank @Size(max=120) String name,@Pattern(regexp="[0-9]{7,15}") @NotNull String mobile,@Email @Size(max=160) String email) {}
    public record Tax(@NotBlank String variantId,@Min(0) @Max(10000) int rateBps) {}
    public record Item(String variantId,String productName,String category,String sku,String barcode,String size,String colour,
        long unitPricePaise,Long costPaise,int quantity,long discountPaise,long billDiscountPaise,int taxRateBps,long taxPaise,long lineTotalPaise) {}
    public record Quote(List<Item> items,long subtotalPaise,long discountPaise,long billDiscountPaise,long taxPaise,long totalPaise,String token) {}
    final CatalogRepository repo; final StockService stock; final ObjectMapper json;
    public BillingService(CatalogRepository repo,StockService stock,ObjectMapper json){this.repo=repo;this.stock=stock;this.json=json;}

    public Config config(){try{return json.readValue(repo.jdbc().queryForObject("SELECT value FROM application_settings WHERE key='billing_config'",String.class),Config.class);}catch(Exception e){throw new IllegalStateException("Billing configuration could not be read",e);}}
    @Transactional public Config configure(@Valid Config input,String actor){
        if(input.receiptWidthMm()!=58 && input.receiptWidthMm()!=80)throw new IllegalArgumentException("Choose 58 mm or 80 mm receipt paper");
        lock();repo.jdbc().update("UPDATE application_settings SET value=? WHERE key='billing_config'",encode(input));audit("BILLING_SETTINGS_CHANGED","billing_config",actor);return input;
    }
    @Transactional public Object tax(@Valid Tax input,String actor){lock();
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=?",Integer.class,input.variantId())!=1)throw new IllegalArgumentException("Variant not found");
        repo.jdbc().update("DELETE FROM variant_taxes WHERE variant_id=?",input.variantId());repo.jdbc().update("INSERT INTO variant_taxes VALUES (?,?)",input.variantId(),input.rateBps());audit("VARIANT_TAX_CHANGED",input.variantId(),actor);return Map.of("ok",true);
    }
    public Object taxes(){return repo.jdbc().queryForList("SELECT * FROM variant_taxes");}
    @Transactional public Quote quote(@Valid Cart cart,AuthService.User user){return calculate(cart,user);}
    private Quote calculate(Cart cart,AuthService.User user){
        Cart original=cart;
        List<Line> normalized=new ArrayList<>();long afterLines=0;
        for(var line:cart.lines()){
            var prices=repo.jdbc().queryForList("SELECT selling_paise FROM product_variants WHERE id=?",line.variantId());
            if(prices.isEmpty())throw new IllegalArgumentException("Product variant not found");
            long gross=Math.multiplyExact(number(prices.getFirst(),"selling_paise"),line.quantity());
            long value=discount(gross,line.discountPaise(),line.discountMode(),line.percentageBps());
            normalized.add(new Line(line.variantId(),line.quantity(),value));afterLines=Math.addExact(afterLines,gross-value);
        }
        cart=new Cart(normalized,discount(afterLines,cart.billDiscountPaise(),cart.discountMode(),cart.percentageBps()),cart.customerId());
        Config config=config();List<Map<String,Object>> variants=new ArrayList<>();Set<String> seen=new HashSet<>();long subtotal=0,discount=cart.billDiscountPaise(),net=0;
        for(Line line:cart.lines()){
            if(!seen.add(line.variantId()))throw new IllegalArgumentException("Each variant must appear once; change its quantity instead");
            var rows=repo.jdbc().queryForList("SELECT v.*,p.name,c.name AS category,COALESCE(t.rate_bps,0) AS tax_rate FROM product_variants v JOIN products p ON p.id=v.product_id JOIN categories c ON c.id=p.category_id LEFT JOIN variant_taxes t ON t.variant_id=v.id WHERE v.id=? AND v.active=1",line.variantId());
            if(rows.isEmpty())throw new IllegalArgumentException("Product variant is missing or inactive");
            var v=rows.getFirst();long available=stock.stock(line.variantId());
            if(available<line.quantity())throw new IllegalArgumentException("Insufficient stock for "+v.get("sku")+" (available: "+available+")");
            long gross=Math.multiplyExact(number(v,"selling_paise"),line.quantity());
            if(line.discountPaise()>gross)throw new IllegalArgumentException("Line discount exceeds the line amount for "+v.get("sku"));
            subtotal=Math.addExact(subtotal,gross);discount=Math.addExact(discount,line.discountPaise());net=Math.addExact(net,gross-line.discountPaise());variants.add(v);
        }
        if(cart.billDiscountPaise()>net)throw new IllegalArgumentException("Bill discount exceeds the amount after line discounts");
        if(subtotal>MAX_MONEY)throw new IllegalArgumentException("Bill amount exceeds supported limit");
        if(!user.role().equals("ADMIN") && BigDecimal.valueOf(discount).multiply(BigDecimal.valueOf(10000)).compareTo(BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(config.billingDiscountBps())))>0)
            throw new IllegalArgumentException("Discount exceeds your permitted limit. Ask an administrator or reduce the discount.");
        if(cart.customerId()!=null && !cart.customerId().isBlank() && repo.jdbc().queryForObject("SELECT COUNT(*) FROM customers WHERE id=?",Integer.class,cart.customerId())!=1)throw new IllegalArgumentException("Customer not found");
        // Largest remainder allocation assigns every discounted paise to an invoice line.
        long[] shares=new long[variants.size()];BigInteger[] remainders=new BigInteger[shares.length];long assigned=0;
        for(int i=0;i<shares.length;i++){
            long base=number(variants.get(i),"selling_paise")*cart.lines().get(i).quantity()-cart.lines().get(i).discountPaise();
            var qr=BigInteger.valueOf(cart.billDiscountPaise()).multiply(BigInteger.valueOf(base)).divideAndRemainder(BigInteger.valueOf(Math.max(1,net)));
            shares[i]=qr[0].longValueExact();remainders[i]=qr[1];assigned+=shares[i];
        }
        List<Integer> order=new ArrayList<>();for(int i=0;i<shares.length;i++)order.add(i);order.sort((a,b)->remainders[b].compareTo(remainders[a]));
        for(int i=0;i<cart.billDiscountPaise()-assigned;i++)shares[order.get(i)]++;
        List<Item> items=new ArrayList<>();long taxTotal=0;
        for(int i=0;i<variants.size();i++){
            var v=variants.get(i);var line=cart.lines().get(i);long unit=number(v,"selling_paise"),base=unit*line.quantity()-line.discountPaise()-shares[i];int rate=(int)number(v,"tax_rate");
            long tax=BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(rate)).divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP).longValueExact();taxTotal=Math.addExact(taxTotal,tax);
            items.add(new Item(line.variantId(),v.get("name").toString(),v.get("category").toString(),v.get("sku").toString(),v.get("barcode").toString(),v.get("size").toString(),v.get("colour").toString(),unit,v.get("cost_paise")==null?null:number(v,"cost_paise"),line.quantity(),line.discountPaise(),shares[i],rate,tax,base+tax));
        }
        long total=Math.addExact(subtotal-discount,taxTotal);if(total>MAX_MONEY)throw new IllegalArgumentException("Total exceeds supported limit");
        String token=hash(encode(items)+encode(original)+encode(config));return new Quote(items,subtotal,discount,cart.billDiscountPaise(),taxTotal,total,token);
    }
    static long discount(long base,long fixed,String mode,Integer bps){
        if(mode==null || mode.equals("INR")){if(bps!=null && bps!=0)throw new IllegalArgumentException("Percentage is only allowed in percentage mode");return fixed;}
        if(!mode.equals("PERCENT") || bps==null || bps<0 || bps>10000 || fixed!=0)throw new IllegalArgumentException("Choose INR or percentage (0–100), with only one discount value");
        return BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(bps)).divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP).longValueExact();
    }
    @Transactional public Object checkout(@Valid Checkout input,AuthService.User user){
        lock();String digest=hash(encode(input));var previous=repo.jdbc().queryForList("SELECT * FROM checkout_requests WHERE request_key=?",input.requestKey().toString());
        if(!previous.isEmpty()){
            var old=previous.getFirst();if(!old.get("request_hash").equals(digest)||!old.get("actor_id").equals(user.id()))throw new IllegalArgumentException("Checkout key was already used for different details");
            return invoice(old.get("invoice_id").toString());
        }
        Quote quote=calculate(input.cart(),user);if(!quote.token().equals(input.quoteToken()))throw new IllegalArgumentException("Prices or tax settings changed. Review the updated bill before confirming again.");
        long paid=0;Set<Method> methods=new HashSet<>();for(Payment payment:input.payments()){
            if(!methods.add(payment.method()))throw new IllegalArgumentException("Enter one amount per payment method");paid=Math.addExact(paid,payment.amountPaise());
        }
        if(paid!=quote.totalPaise())throw new IllegalArgumentException("Recorded payments must equal the final amount payable");
        String id=UUID.randomUUID().toString(),now=timestamp(Instant.now());String customerId=input.cart().customerId();Map<String,Object> customer=customerId==null||customerId.isBlank()?Map.of():repo.jdbc().queryForMap("SELECT * FROM customers WHERE id=?",customerId);
        repo.jdbc().update("INSERT INTO invoices(id,invoice_number,status,subtotal_paise,discount_paise,tax_paise,total_paise,customer_name,customer_phone,created_by,created_at,customer_id,shop_snapshot,bill_discount_paise) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,nextNumber(),"COMPLETED",quote.subtotalPaise(),quote.discountPaise(),quote.taxPaise(),quote.totalPaise(),customer.get("name"),customer.get("mobile"),user.id(),now,customer.get("id"),encode(config()),quote.billDiscountPaise());
        repo.jdbc().update("UPDATE invoices SET discount_input_json=? WHERE id=?",encode(input.cart()),id);
        int position=0;
        for(Item line:quote.items()){
            repo.jdbc().update("INSERT INTO invoice_items(id,invoice_id,variant_id,product_name,sku,size,colour,unit_price_paise,quantity,discount_paise,tax_paise,line_total_paise,category,barcode,cost_paise,tax_rate_bps,bill_discount_paise,line_position) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),id,line.variantId(),line.productName(),line.sku(),line.size(),line.colour(),line.unitPricePaise(),line.quantity(),line.discountPaise(),line.taxPaise(),line.lineTotalPaise(),line.category(),line.barcode(),line.costPaise(),line.taxRateBps(),line.billDiscountPaise(),position++);
            stock.movement(line.variantId(),-line.quantity(),"SALE","Invoice sale",id,user.id());
        }
        for(Payment payment:input.payments())repo.jdbc().update("INSERT INTO payments VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),id,payment.method().name(),payment.amountPaise(),now);
        repo.jdbc().update("INSERT INTO checkout_requests VALUES (?,?,?,?)",input.requestKey().toString(),digest,user.id(),id);audit("INVOICE_COMPLETED",id,user.id());return invoice(id);
    }
    void lock(){repo.jdbc().update("UPDATE application_settings SET value=value WHERE key='write_lock'");}
    @Transactional public Object requestStatus(UUID key,AuthService.User user){
        lock();var rows=repo.jdbc().queryForList("SELECT * FROM checkout_requests WHERE request_key=?",key.toString());
        if(rows.isEmpty())return Map.of("found",false);
        if(!rows.getFirst().get("actor_id").equals(user.id()))throw new IllegalArgumentException("Checkout belongs to a different user");
        return Map.of("found",true,"sale",invoice(rows.getFirst().get("invoice_id").toString()));
    }
    private String nextNumber(){
        long sequence=Long.parseLong(repo.jdbc().queryForObject("SELECT value FROM application_settings WHERE key='invoice_sequence'",String.class));
        String prefix=config().invoicePrefix()+"-"+LocalDate.now(ZoneId.of("Asia/Kolkata")).toString().replace("-","")+"-",candidate;
        do{sequence=Math.addExact(sequence,1);candidate=prefix+String.format(Locale.ROOT,"%06d",sequence);}while(repo.jdbc().queryForObject("SELECT COUNT(*) FROM invoices WHERE invoice_number=?",Integer.class,candidate)>0);
        repo.jdbc().update("UPDATE application_settings SET value=? WHERE key='invoice_sequence'",Long.toString(sequence));return candidate;
    }
    public Map<String,Object> invoice(String id){var rows=repo.jdbc().queryForList("SELECT * FROM invoices WHERE id=?",id);if(rows.isEmpty())throw new IllegalArgumentException("Invoice not found");return Map.of("invoice",rows.getFirst(),"items",repo.jdbc().queryForList("SELECT * FROM invoice_items WHERE invoice_id=? ORDER BY line_position,id",id),"payments",repo.jdbc().queryForList("SELECT * FROM payments WHERE invoice_id=? ORDER BY method",id),"reversals",repo.jdbc().queryForList("SELECT * FROM returns WHERE invoice_id=? ORDER BY created_at,id",id),"refunds",repo.jdbc().queryForList("SELECT f.* FROM refunds f JOIN returns r ON r.id=f.return_id WHERE r.invoice_id=? ORDER BY f.created_at,f.id",id));}
    public Object history(String search,LocalDate from,LocalDate to,Long amount){
        if(from!=null && to!=null && from.isAfter(to))throw new IllegalArgumentException("Start date must precede end date");
        String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        List<Object> args=new ArrayList<>(List.of(pattern,pattern,pattern));String sql="SELECT * FROM invoices WHERE (invoice_number LIKE ? ESCAPE '!' OR customer_name LIKE ? ESCAPE '!' OR customer_phone LIKE ? ESCAPE '!')";
        if(from!=null){sql+=" AND created_at>=?";args.add(timestamp(from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()));}
        if(to!=null){sql+=" AND created_at<?";args.add(timestamp(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()));}
        if(amount!=null){sql+=" AND total_paise=?";args.add(amount);}return repo.jdbc().queryForList(sql+" ORDER BY created_at DESC,id DESC LIMIT 500",args.toArray());
    }
    public Object customers(String search){String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";return repo.jdbc().queryForList("SELECT id,name,mobile,email FROM customers WHERE name LIKE ? ESCAPE '!' OR mobile LIKE ? ESCAPE '!' ORDER BY name LIMIT 100",pattern,pattern);}
    @Transactional public Object customer(@Valid CustomerInput input,String actor){lock();
        if(input.name().trim().isEmpty())throw new IllegalArgumentException("Enter a customer name");
        if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM customers WHERE mobile=?",Integer.class,input.mobile())>0)throw new IllegalArgumentException("A customer with this mobile already exists. Search and select that customer.");
        String id=UUID.randomUUID().toString();String now=timestamp(Instant.now());repo.jdbc().update("INSERT INTO customers(id,name,mobile,email,created_at,updated_at) VALUES (?,?,?,?,?,?)",id,input.name().trim(),input.mobile(),input.email(),now,now);audit("CUSTOMER_CREATED",id,actor);return repo.jdbc().queryForMap("SELECT id,name,mobile,email FROM customers WHERE id=?",id);
    }
    void audit(String action,String id,String actor){repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),action,id,actor,Instant.now().toString());}
    static String timestamp(Instant instant){return new java.time.format.DateTimeFormatterBuilder().appendInstant(9).toFormatter().format(instant);}
    static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
