package com.eastoftown.pos;

import java.io.*;
import java.awt.print.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.print.PrintServiceLookup;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.printing.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ReceiptService {
    final BillingService billing;final LabelService labels;final com.fasterxml.jackson.databind.ObjectMapper json;
    public ReceiptService(BillingService billing,LabelService labels,com.fasterxml.jackson.databind.ObjectMapper json){this.billing=billing;this.labels=labels;this.json=json;}
    static String money(Object n){return "INR "+BigDecimal.valueOf(((Number)n).longValue(),2).toPlainString();}
    @SuppressWarnings("unchecked")
    public byte[] pdf(String id,int widthMm){
        if(widthMm!=58 && widthMm!=80)throw new IllegalArgumentException("Choose 58 mm or 80 mm paper");
        Map<String,Object> data=billing.invoice(id),invoice=(Map<String,Object>)data.get("invoice");
        Map<String,Object> shop;
        try{shop=json.readValue(invoice.get("shop_snapshot").toString(),Map.class);}catch(Exception e){throw new IllegalStateException("Receipt settings unavailable",e);}
        List<String> source=new ArrayList<>();source.add(shop.getOrDefault("shopName","East of Town Men's Fashion").toString());
        source.add(shop.getOrDefault("address","").toString());source.add(shop.getOrDefault("phone","").toString());source.add("Invoice: "+invoice.get("invoice_number"));
        source.add(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("Asia/Kolkata")).format(Instant.parse(invoice.get("created_at").toString())));
        if(invoice.get("customer_name")!=null){source.add("Customer: "+invoice.get("customer_name"));source.add("Mobile: "+invoice.get("customer_phone"));}
        source.add("----------------------------------");
        for(var item:(List<Map<String,Object>>)data.get("items")){
            source.add(item.get("product_name").toString());source.add(item.get("size")+" / "+item.get("colour")+" | "+item.get("sku"));
            source.add(item.get("quantity")+" x "+money(item.get("unit_price_paise")));
            long discount=BillingService.number(item,"discount_paise")+BillingService.number(item,"bill_discount_paise");
            if(discount>0)source.add("Discount: -"+money(discount));
            if(BillingService.number(item,"tax_paise")>0)source.add("Tax "+BigDecimal.valueOf(BillingService.number(item,"tax_rate_bps"),2).toPlainString()+"%: "+money(item.get("tax_paise")));
            source.add("Line total: "+money(item.get("line_total_paise")));source.add("");
        }
        source.add("----------------------------------");source.add("Subtotal: "+money(invoice.get("subtotal_paise")));source.add("Discounts: -"+money(invoice.get("discount_paise")));
        if(BillingService.number(invoice,"tax_paise")>0)source.add("Tax: "+money(invoice.get("tax_paise")));
        source.add("TOTAL: "+money(invoice.get("total_paise")));
        for(var payment:(List<Map<String,Object>>)data.get("payments"))source.add(payment.get("method")+": "+money(payment.get("amount_paise")));
        source.add(shop.getOrDefault("thankYou","Thank you!").toString());
        for(var reversal:(List<Map<String,Object>>)data.get("reversals"))source.add("Recorded "+reversal.get("kind")+": "+money(reversal.get("total_paise"))+" | "+reversal.get("reason"));
        for(var refund:(List<Map<String,Object>>)data.get("refunds"))source.add("Refund "+refund.get("method")+": "+money(refund.get("amount_paise")));
        if(!((List<?>)data.get("reversals")).isEmpty())source.add("Original sale totals above are preserved.");
        return render(source,widthMm);
    }
    byte[] render(List<String> source,int widthMm){
        try(PDDocument document=new PDDocument()){
            var font=labels.font(document);float width=widthMm*72/25.4f,margin=10;int size=9;
            List<String> lines=new ArrayList<>();for(String text:source)for(String part:text.split("\\R",-1)){if(part.isEmpty())lines.add("");else lines.addAll(LabelService.wrap(font,part,size,width-margin*2,1000));}
            int perPage=110;int count=Math.max(1,(lines.size()+perPage-1)/perPage);
            for(int p=0;p<count;p++){int start=p*perPage,end=Math.min(lines.size(),start+perPage);float height=(end-start)*13+margin*2+16;
                var page=new PDPage(new PDRectangle(width,height));document.addPage(page);
                try(var stream=new PDPageContentStream(document,page)){float y=height-margin-10;for(String line:lines.subList(start,end)){LabelService.text(stream,font,line,size,margin,y);y-=13;}if(count>1)LabelService.text(stream,font,"Page "+(p+1)+" / "+count,8,margin,margin);}
            }
            var out=new ByteArrayOutputStream();document.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Unable to create receipt PDF",e);}
    }
    public Object print(String id,int widthMm,String printer,String actor){
        byte[] bytes=pdf(id,widthMm);Object result=submit(bytes,printer,"East of Town receipt");billing.audit("RECEIPT_PRINT_SUBMITTED",id,actor);return result;
    }
    public Object test(int widthMm,String printer){return submit(render(List.of("East of Town","TEST RECEIPT - NOT A SALE","Paper width: "+widthMm+" mm","INR 1234.56","Check alignment and full text."),widthMm),printer,"East of Town TEST receipt");}
    Object submit(byte[] bytes,String printer,String name){
        if(printer==null||printer.isBlank())throw new IllegalArgumentException("Select a receipt printer or save PDF");
        var service=Arrays.stream(PrintServiceLookup.lookupPrintServices(null,null)).filter(p->p.getName().equals(printer)).findFirst().orElseThrow(()->new IllegalArgumentException("Printer unavailable. The invoice is saved; use Save PDF or reprint later."));
        try(var document=org.apache.pdfbox.Loader.loadPDF(bytes)){
            PrinterJob job=PrinterJob.getPrinterJob();job.setPrintService(service);job.setJobName(name);Book book=new Book();
            for(var page:document.getPages()){var box=page.getMediaBox();Paper paper=new Paper();paper.setSize(box.getWidth(),box.getHeight());paper.setImageableArea(0,0,box.getWidth(),box.getHeight());PageFormat format=new PageFormat();format.setPaper(paper);
                final int pageIndex=book.getNumberOfPages();var printable=new PDFPrintable(document,Scaling.ACTUAL_SIZE);book.append((g,f,i)->printable.print(g,f,pageIndex),format);}
            job.setPageable(book);job.print();return Map.of("status","submitted","message","Receipt submitted to Windows print spooler. Check paper and printed output.");
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Printing failed. Invoice remains saved. Save PDF or reprint later.");}
    }
}
