package com.eastoftown.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportService {
    public enum Mode{DETAILS_ONLY,OPENING_NEW,ADJUST_DELTA}
    public record Config(Map<String,Integer> mapping,String categoryOverride,Mode mode,String reason){}
    public record ImportRow(int row,Map<String,String> values,String variantId,String action,int stockDelta,List<String> errors,List<String> warnings){}
    public record Preview(String batchId,String token,List<ImportRow> rows,int created,int updated,int skipped,int failed,List<String> newCategories){}
    final ImportRepository imports;final CatalogRepository repo;final CatalogService catalog;final StockService stock;final ExcelFiles excel;final ObjectMapper json;final Validator validator;
    public ImportService(ImportRepository imports,CatalogRepository repo,CatalogService catalog,StockService stock,ExcelFiles excel,ObjectMapper json,Validator validator){this.imports=imports;this.repo=repo;this.catalog=catalog;this.stock=stock;this.excel=excel;this.json=json;this.validator=validator;}
    @Transactional public Object upload(String filename,byte[] bytes){
        if(filename==null || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls)$"))throw new IllegalArgumentException("Select an .xlsx or .xls file");
        var data=excel.read(bytes);String clean=filename.replace('\\','/');clean=clean.substring(clean.lastIndexOf('/')+1);if(clean.length()>200)clean=clean.substring(clean.length()-200);
        String id=imports.upload(clean,write(data));return Map.of("batchId",id,"headers",data.headers(),"sampleRows",data.rows().stream().limit(5).toList(),"rowCount",data.rows().size());
    }
    @Transactional public Preview preview(String id,Config config){
        var batch=imports.batch(id);if(batch.get("state").equals("COMMITTED"))throw new IllegalArgumentException("This batch has already been imported");
        var data=read(batch.get("raw_json").toString(),ExcelFiles.SheetData.class);validateConfig(config,data);
        String token=UUID.randomUUID().toString();Preview result=plan(id,token,data,config);imports.preview(id,write(config),token,imports.revision());return result;
    }
    void validateConfig(Config config,ExcelFiles.SheetData data){
        if(config==null || config.mapping()==null || config.mode()==null)throw new IllegalArgumentException("Select a stock policy and map the required columns");
        Set<Integer> used=new HashSet<>();
        for(var mapping:config.mapping().entrySet())if(!ExcelFiles.FIELDS.contains(mapping.getKey()) || mapping.getValue()==null || mapping.getValue()<0 || mapping.getValue()>=data.headers().size() || !used.add(mapping.getValue()))throw new IllegalArgumentException("Map each column only once using a valid header");
        for(String required:List.of("name","sku","size","colour","price"))if(!config.mapping().containsKey(required))throw new IllegalArgumentException("Map required column: "+required);
        if(!config.mapping().containsKey("category") && (config.categoryOverride()==null || config.categoryOverride().isBlank()))throw new IllegalArgumentException("Map category or assign one category to the file");
        if(config.mode()!=Mode.DETAILS_ONLY && !config.mapping().containsKey("quantity"))throw new IllegalArgumentException("Map quantity for the selected stock policy");
        if(config.mode()==Mode.ADJUST_DELTA && (config.reason()==null || config.reason().trim().length()<3 || config.reason().length()>300))throw new IllegalArgumentException("Enter a stock-adjustment reason (3–300 characters)");
    }
    Preview plan(String id,String token,ExcelFiles.SheetData data,Config config){
        List<ImportRow> result=new ArrayList<>();Map<String,Map<String,Object>> existing=new HashMap<>();
        for(var row:repo.jdbc().queryForList("SELECT v.*,p.name,p.category_id,c.name AS category FROM product_variants v JOIN products p ON p.id=v.product_id JOIN categories c ON c.id=p.category_id"))existing.put(row.get("sku").toString().toLowerCase(Locale.ROOT),row);
        Map<String,Integer> skuCounts=new HashMap<>(),barcodeCounts=new HashMap<>(),combinationCounts=new HashMap<>();
        for(var source:data.rows()){
            var values=values(source,config);count(skuCounts,values.get("sku").toLowerCase(Locale.ROOT));count(barcodeCounts,values.get("barcode"));
            count(combinationCounts,combination(values));
        }
        Set<String> categories=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);Set<String> known=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(var c:repo.categories())known.add(c.get("name").toString());
        Map<String,String> productChanges=new HashMap<>();
        for(var source:data.rows()){
            Map<String,String> values=values(source,config);List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();String sku=values.get("sku");
            Map<String,Object> old=existing.get(sku.toLowerCase(Locale.ROOT));String variant=old==null?null:old.get("id").toString();int quantity=0,delta=0;
            for(String key:List.of("sku","barcode")){Integer column=config.mapping().get(key);if(column!=null && source.numeric().contains(column) && !values.get(key).isBlank())errors.add(key+" must be a text cell to preserve leading zeros and precision");}
            if(source.cells().contains("[FORMULA]") || source.cells().contains("[CELL ERROR]"))errors.add("Replace formulas/cell errors with plain values");
            if(skuCounts.getOrDefault(sku.toLowerCase(Locale.ROOT),0)>1)errors.add("Duplicate SKU in file");
            String code=values.get("barcode");if(!code.isBlank() && barcodeCounts.getOrDefault(code,0)>1)errors.add("Duplicate barcode in file");
            if(combinationCounts.getOrDefault(combination(values),0)>1)errors.add("Duplicate product/size/colour in file");
            if(old!=null && !code.isBlank() && !code.equals(old.get("barcode")))errors.add("Existing barcode cannot be changed");
            if(!code.isBlank() && repo.jdbc().queryForObject("SELECT COUNT(*) FROM product_variants WHERE barcode=? AND id!=?",Integer.class,code,variant==null?"":variant)>0)errors.add("Barcode belongs to another variant");
            if(values.get("category").isBlank() || values.get("category").length()>80)errors.add("Category is required (max 80 characters)");
            if(!values.get("category").isBlank() && !known.contains(values.get("category")))categories.add(values.get("category"));
            try{
                if(config.mode()!=Mode.DETAILS_ONLY){quantity=integer(values.get("quantity"),0);if(Math.abs((long)quantity)>1000000)errors.add("Quantity exceeds one million units");}
                else if(!values.get("quantity").isBlank())warnings.add("Quantity ignored: product details only");
                if(old!=null && config.mode()==Mode.OPENING_NEW && quantity!=0)warnings.add("Existing stock preserved; opening quantities apply only to new variants");
                if(old==null && config.mode()!=Mode.DETAILS_ONLY && quantity<0)errors.add("New variant opening stock cannot be negative");
                delta=old==null?(config.mode()==Mode.DETAILS_ONLY?0:quantity):(config.mode()==Mode.ADJUST_DELTA?quantity:0);
                if(old!=null && stock.stock(variant)+delta<0)errors.add("Adjustment would make stock negative");
                CatalogService.VariantInput input=input(values,old==null?null:old.get("product_id").toString(),"preview-category",old==null?(code.isBlank()?null:code):old.get("barcode").toString(),old==null?Math.max(0,delta):0,old==null?0:((Number)old.get("version")).longValue(),old==null || ((Number)old.get("active")).intValue()==1);
                for(var failure:validator.validate(input))errors.add(failure.getPropertyPath()+": "+failure.getMessage());
                String name=values.get("name"),category=values.get("category");
                if(old!=null){String key=old.get("product_id").toString();String change=name+"\u0000"+category;String previous=productChanges.putIfAbsent(key,change);if(previous!=null && !previous.equals(change))errors.add("Conflicting shared product name/category in this file");if(!name.equals(old.get("name")) || !category.equalsIgnoreCase(old.get("category").toString()))warnings.add("Name/category change affects every size and colour of this product");}
                if(repo.jdbc().queryForObject("SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id JOIN categories c ON c.id=p.category_id WHERE LOWER(p.name)=LOWER(?) AND c.name=? COLLATE NOCASE AND v.size=? AND v.colour=? AND v.id!=?",Integer.class,name,category,values.get("size"),values.get("colour"),variant==null?"":variant)>0)errors.add("Product/size/colour already exists under a different SKU");
                String action=old==null?"CREATE":"UPDATE";
                if(old!=null && same(input,values,old) && delta==0)action="SKIP";
                result.add(new ImportRow(source.row(),values,variant,errors.isEmpty()?action:"ERROR",delta,errors,warnings));
            }catch(IllegalArgumentException|ArithmeticException e){errors.add("Invalid decimal, quantity, active flag or threshold: "+e.getMessage());result.add(new ImportRow(source.row(),values,variant,"ERROR",0,errors,warnings));}
        }
        return new Preview(id,token,result,(int)result.stream().filter(r->r.action().equals("CREATE")).count(),(int)result.stream().filter(r->r.action().equals("UPDATE")).count(),(int)result.stream().filter(r->r.action().equals("SKIP")).count(),(int)result.stream().filter(r->!r.errors().isEmpty()).count(),List.copyOf(categories));
    }
    private boolean same(CatalogService.VariantInput input,Map<String,String> values,Map<String,Object> old){return input.name().equals(old.get("name"))&&values.get("category").equalsIgnoreCase(old.get("category").toString())&&input.size().equals(old.get("size"))&&input.colour().equals(old.get("colour"))&&Objects.equals(input.cost()==null?null:CatalogService.paise(input.cost()),old.get("cost_paise")==null?null:((Number)old.get("cost_paise")).longValue())&&CatalogService.paise(input.price())==((Number)old.get("selling_paise")).longValue()&&input.reorderLevel()==((Number)old.get("reorder_level")).intValue()&&input.active()==(((Number)old.get("active")).intValue()==1);}
    @Transactional public Object commit(String id,String token,boolean confirm,String actor){
        if(!confirm)throw new IllegalArgumentException("Confirm the preview before importing");
        repo.jdbc().update("UPDATE application_settings SET value=value WHERE key='write_lock'");
        var batch=imports.batch(id);
        if(token==null || !token.equals(batch.get("preview_token")))throw new IllegalArgumentException("Preview expired. Generate a new preview");
        if(batch.get("state").equals("COMMITTED"))return repo.jdbc().queryForMap("SELECT * FROM import_history WHERE batch_id=?",id);
        if(!batch.get("state").equals("PREVIEWED") || !imports.revision().equals(batch.get("revision")))throw new IllegalArgumentException("Catalog or stock changed since preview. Preview again before saving");
        Config config=read(batch.get("config_json").toString(),Config.class);var data=read(batch.get("raw_json").toString(),ExcelFiles.SheetData.class);
        Preview preview=plan(id,token,data,config);if(preview.failed()>0)throw new IllegalArgumentException("Correct all row errors before importing. Nothing was saved");
        for(String name:preview.newCategories())catalog.category(name,actor);
        for(var row:preview.rows()){
            if(row.action().equals("SKIP"))continue;
            var values=row.values();String category=repo.jdbc().queryForObject("SELECT id FROM categories WHERE name=? COLLATE NOCASE",String.class,values.get("category"));
            Map<String,Object> old=row.variantId()==null?null:repo.jdbc().queryForMap("SELECT * FROM product_variants WHERE id=?",row.variantId());
            String productId=old==null?null:old.get("product_id").toString();
            if(old==null){var products=repo.jdbc().queryForList("SELECT id FROM products WHERE LOWER(name)=LOWER(?) AND category_id=? ORDER BY id LIMIT 1",String.class,values.get("name"),category);if(!products.isEmpty())productId=products.getFirst();}
            String barcode=old==null?(values.get("barcode").isBlank()?null:values.get("barcode")):old.get("barcode").toString();
            var input=input(values,productId,category,barcode,old==null?row.stockDelta():0,old==null?0:((Number)old.get("version")).longValue(),old==null || ((Number)old.get("active")).intValue()==1);
            catalog.save(row.variantId(),input,actor);
            if(old!=null && row.stockDelta()!=0)stock.adjust(new StockService.Adjustment(row.variantId(),row.stockDelta(),stock.stock(row.variantId()),config.reason(),LocalDate.now(ZoneId.of("Asia/Kolkata")),UUID.randomUUID()),actor);
        }
        String history=UUID.randomUUID().toString(),now=Instant.now().toString();
        repo.jdbc().update("INSERT INTO import_history VALUES (?,?,?,?,?,?,?,?,?,?)",history,id,batch.get("filename"),config.mode().name(),preview.created(),preview.updated(),preview.skipped(),0,actor,now);
        repo.jdbc().update("UPDATE import_batches SET state='COMMITTED' WHERE id=?",id);
        repo.jdbc().update("INSERT INTO audit_logs VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),"IMPORT_COMMITTED",id,actor,now);
        return repo.jdbc().queryForMap("SELECT * FROM import_history WHERE id=?",history);
    }
    public byte[] errors(String id){var batch=imports.batch(id);if(batch.get("config_json")==null)throw new IllegalArgumentException("Preview the workbook first");return excel.errors(plan(id,batch.get("preview_token").toString(),read(batch.get("raw_json").toString(),ExcelFiles.SheetData.class),read(batch.get("config_json").toString(),Config.class)));}
    Map<String,String> values(ExcelFiles.SourceRow row,Config config){Map<String,String> values=new LinkedHashMap<>();for(String field:ExcelFiles.FIELDS)values.put(field,config.mapping().containsKey(field)?row.cells().get(config.mapping().get(field)).trim():"");if(config.categoryOverride()!=null && !config.categoryOverride().isBlank())values.put("category",config.categoryOverride().trim());return values;}
    static String combination(Map<String,String> v){return v.get("name").toLowerCase(Locale.ROOT)+"\u0000"+v.get("category").toLowerCase(Locale.ROOT)+"\u0000"+v.get("size")+"\u0000"+v.get("colour");}
    static void count(Map<String,Integer> counts,String key){if(!key.isBlank())counts.merge(key,1,Integer::sum);}
    static int integer(String value,int fallback){return value.isBlank()?fallback:Integer.parseInt(value);}
    static CatalogService.VariantInput input(Map<String,String> v,String product,String category,String barcode,int opening,long version,boolean defaultActive){
        String active=v.get("active");if(!active.isBlank() && !Set.of("true","false").contains(active.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("active must be true or false");
        return new CatalogService.VariantInput(product,v.get("name"),category,v.get("sku"),v.get("size"),v.get("colour"),barcode,v.get("cost").isBlank()?null:new BigDecimal(v.get("cost")),new BigDecimal(v.get("price")),opening,integer(v.get("reorderLevel"),3),active.isBlank()?defaultActive:Boolean.parseBoolean(active),version);
    }
    String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw new IllegalArgumentException("Import data is unreadable. Upload the file again");}}
}
