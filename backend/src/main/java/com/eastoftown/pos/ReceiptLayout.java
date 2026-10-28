package com.eastoftown.pos;

import java.io.*;
import java.math.BigDecimal;
import java.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

/** Presentation only: all prices, discounts, taxes and payments are saved sale snapshots. */
final class ReceiptLayout {
    private record Cell(String text, PDFont font, int size, float x) {}
    private record Row(List<Cell> cells, float height, boolean rule) {}
    private final PDFont regular, bold;
    private final float width, margin=10, content;
    private final List<Row> rows=new ArrayList<>();
    private final Map<Integer,Integer> groups=new HashMap<>();
    private String invoiceNumber;
    private ReceiptLayout(PDDocument doc, LabelService labels, int widthMm) throws IOException {
        regular=labels.font(doc); bold=boldFont(doc); width=widthMm*72/25.4f; content=width-2*margin;
    }
    static String amount(Object value) {
        var format=new DecimalFormat("#,##0.00",DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(BigDecimal.valueOf(((Number)value).longValue(),2));
    }
    static String money(Object value) { return "Rs. "+amount(value); }
    private static PDFont boldFont(PDDocument doc) throws IOException {
        String windows=System.getenv("WINDIR");
        if(windows!=null){var file=new File(windows,"Fonts/arialbd.ttf");if(file.isFile())return PDType0Font.load(doc,file);}
        var file=new File("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");
        return file.isFile()?PDType0Font.load(doc,file):new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }
    private float measure(String text,PDFont font,int size) throws IOException { return font.getStringWidth(text)/1000*size; }
    private Cell cell(String text,int size,boolean heavy,float left,float available,String align) throws IOException {
        var font=heavy?bold:regular;
        while(size>7&&measure(text,font,size)>available)size--;
        float textWidth=measure(text,font,size);
        if(textWidth>available+.1f)throw new IllegalArgumentException("Receipt amount is too wide for the selected paper");
        float x=left+(align.equals("right")?available-textWidth:align.equals("center")?(available-textWidth)/2:0);
        return new Cell(text,font,size,x);
    }
    private List<String> wrap(String value,PDFont font,int size,float available) throws IOException {
        List<String> result=new ArrayList<>();
        for(String paragraph:value.split("\\R",-1)){
            String line="";
            for(String word:paragraph.strip().split("\\s+")){
                if(word.isEmpty())continue;
                String candidate=line.isEmpty()?word:line+" "+word;
                if(!line.isEmpty()&&measure(candidate,font,size)>available){result.add(line);line="";}
                if(measure(word,font,size)>available){
                    var parts=LabelService.wrap(font,word,size,available,1000);
                    result.addAll(parts.subList(0,parts.size()-1));line=parts.getLast();
                }else line=line.isEmpty()?word:line+" "+word;
            }
            if(!line.isEmpty())result.add(line);
        }
        return result;
    }
    private void text(String value,int size,boolean heavy,boolean center) throws IOException {
        for(String line:wrap(value,heavy?bold:regular,size,content))rows.add(new Row(List.of(cell(line,size,heavy,margin,content,center?"center":"left")),size+4,false));
    }
    private void space(float height){rows.add(new Row(List.of(),height,false));}
    private void rule(){rows.add(new Row(List.of(),12,true));}
    private void keepTogether(int start){groups.put(start,rows.size());}
    private void summary(String label,Object value,int size,boolean heavy) throws IOException {
        float labelWidth=measure(label,heavy?bold:regular,size)+8;
        rows.add(new Row(List.of(cell(label,size,heavy,margin,labelWidth,"left"),cell(money(value),size,heavy,margin+labelWidth,content-labelWidth,"right")),size+6,false));
    }
    @SuppressWarnings("unchecked")
    static byte[] render(Map<String,Object> data,Map<String,Object> shop,int widthMm,LabelService labels) {
        try(var document=new PDDocument()){
            var layout=new ReceiptLayout(document,labels,widthMm);layout.sale(data,shop);layout.write(document);
            var out=new ByteArrayOutputStream();document.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Unable to create receipt PDF",e);}
    }
    @SuppressWarnings("unchecked")
    private void sale(Map<String,Object> data,Map<String,Object> shop) throws IOException {
        var invoice=(Map<String,Object>)data.get("invoice");
        invoiceNumber=invoice.get("invoice_number").toString();
        var items=(List<Map<String,Object>>)data.get("items");
        var payments=(List<Map<String,Object>>)data.get("payments");
        var reversals=(List<Map<String,Object>>)data.get("reversals");
        var refunds=(List<Map<String,Object>>)data.get("refunds");
        String name=Objects.toString(shop.get("shopName"),"East of Town Men's Fashion");
        boolean east=name.strip().equalsIgnoreCase("East of Town Men's Fashion")||name.strip().equalsIgnoreCase("East of Town");
        text(east?"EAST OF TOWN":name.toUpperCase(Locale.ROOT),width>200?16:13,true,true);
        if(east)text("MEN'S WEAR & FASHION",8,false,true);
        text(Objects.toString(shop.get("address"),""),8,false,true);
        String phone=Objects.toString(shop.get("phone"),"");if(!phone.isBlank())text("Tel: "+phone,8,false,true);
        space(3);rule();text("SALES RECEIPT",11,true,true);space(7);
        text("Bill: "+invoice.get("invoice_number"),8,false,false);
        text("Date: "+DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a",Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata")).format(Instant.parse(invoice.get("created_at").toString())),8,false,false);
        text("Customer: "+Objects.toString(invoice.get("customer_name"),"Guest"),8,false,false);
        if(invoice.get("customer_phone")!=null)text("Mobile: "+invoice.get("customer_phone"),8,false,false);
        if(!reversals.isEmpty())text("Returns / cancellation recorded",8,true,false);
        rule();
        float amountWidth=content*.30f,quantityWidth=content*.12f;
        for(var item:items){
            long gross=Math.multiplyExact(BillingService.number(item,"unit_price_paise"),BillingService.number(item,"quantity"));
            amountWidth=Math.max(amountWidth,Math.min(content*.46f,measure(amount(gross),regular,9)+4));
            quantityWidth=Math.max(quantityWidth,measure(item.get("quantity").toString(),regular,9)+6);
        }
        float itemWidth=content-amountWidth-quantityWidth-8,quantityLeft=margin+itemWidth+4,amountLeft=width-margin-amountWidth;
        rows.add(new Row(List.of(cell("ITEM",9,true,margin,itemWidth,"left"),cell("QTY",8,true,quantityLeft,quantityWidth,"center"),cell("AMOUNT",8,true,amountLeft,amountWidth,"right")),13,false));rule();
        for(var item:items){
            int itemStart=rows.size();
            var names=wrap(item.get("product_name").toString(),regular,9,itemWidth);
            long gross=Math.multiplyExact(BillingService.number(item,"unit_price_paise"),BillingService.number(item,"quantity"));
            for(int i=0;i<names.size();i++){
                var cells=new ArrayList<Cell>();cells.add(cell(names.get(i),9,false,margin,itemWidth,"left"));
                if(i==0){cells.add(cell(item.get("quantity").toString(),9,false,quantityLeft,quantityWidth,"center"));cells.add(cell(amount(gross),9,false,amountLeft,amountWidth,"right"));}
                rows.add(new Row(cells,13,false));
            }
            text(item.get("size")+" / "+item.get("colour")+" | "+item.get("sku"),7,false,false);
            text("Unit: "+money(item.get("unit_price_paise")),7,false,false);
            long discount=Math.addExact(BillingService.number(item,"discount_paise"),BillingService.number(item,"bill_discount_paise")),tax=BillingService.number(item,"tax_paise");
            if(discount>0)text("Discount: - "+money(discount),7,false,false);
            if(tax>0)text("Tax "+BigDecimal.valueOf(BillingService.number(item,"tax_rate_bps"),2).stripTrailingZeros().toPlainString()+"%: "+money(tax),7,false,false);
            if(discount>0||tax>0)text("Line total: "+money(item.get("line_total_paise")),7,false,false);
            space(7);
            keepTogether(itemStart);
        }
        int summaryStart=rows.size();
        rule();summary("Subtotal",invoice.get("subtotal_paise"),9,false);
        if(BillingService.number(invoice,"discount_paise")>0){
            float labelWidth=64;rows.add(new Row(List.of(cell("Discount",9,false,margin,labelWidth,"left"),cell("- "+money(invoice.get("discount_paise")),9,false,margin+labelWidth,content-labelWidth,"right")),15,false));
        }
        if(BillingService.number(invoice,"tax_paise")>0)summary("Tax",invoice.get("tax_paise"),9,false);
        rule();summary("TOTAL",invoice.get("total_paise"),13,true);space(4);
        String paymentLabel=reversals.isEmpty()?"Payment: ":"Original payment: ";
        if(payments.isEmpty())text(paymentLabel+"No payment due",8,false,false);
        else if(payments.size()==1)text(paymentLabel+payments.getFirst().get("method")+" (Paid)",8,false,false);
        else{
            text(paymentLabel+"Split (Paid)",8,false,false);
            for(var payment:payments)summary(payment.get("method").toString(),payment.get("amount_paise"),8,false);
        }
        keepTogether(summaryStart);
        if(!reversals.isEmpty()){
            rule();text("RECORDED REVERSALS",9,true,false);
            for(var reversal:reversals)text(reversal.get("kind")+": "+money(reversal.get("total_paise"))+" | "+reversal.get("reason"),8,false,false);
            for(var refund:refunds)summary("Refund "+refund.get("method"),refund.get("amount_paise"),8,false);
            text("Original sale totals above are preserved.",7,false,false);
        }
        int footerStart=rows.size();rule();text(Objects.toString(shop.get("thankYou"),"Thank you! Visit again").toUpperCase(Locale.ROOT),9,true,true);keepTogether(footerStart);
    }
    private void write(PDDocument document) throws IOException {
        List<List<Row>> pages=new ArrayList<>();List<Row> pageRows=new ArrayList<>();float used=0;
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);float block=groups.containsKey(i)?(float)rows.subList(i,groups.get(i)).stream().mapToDouble(Row::height).sum():row.height();
            if(((block<=1380&&used+block>1380)||used+row.height()>1380)&&!pageRows.isEmpty()){pages.add(pageRows);pageRows=new ArrayList<>();used=0;}
            pageRows.add(row);used+=row.height();
        }
        if(!pageRows.isEmpty())pages.add(pageRows);
        for(int index=0;index<pages.size();index++){
            var pageContent=pages.get(index);float extra=(pages.size()>1?14:0)+(index>0?32:0);
            float height=(float)pageContent.stream().mapToDouble(Row::height).sum()+margin*2+extra;
            var page=new PDPage(new PDRectangle(width,height));document.addPage(page);
            try(var stream=new PDPageContentStream(document,page)){
                float top=height-margin;
                if(index>0){textOnPage(stream,new Cell("SALES RECEIPT - continued",bold,8,margin),top);top-=14;textOnPage(stream,cell("Bill: "+invoiceNumber,7,false,margin,content,"left"),top);top-=18;}
                for(var row:pageContent){
                    if(row.rule()){stream.saveGraphicsState();stream.setStrokingColor(.65f);stream.setLineWidth(.5f);stream.setLineDashPattern(new float[]{2,2},0);stream.moveTo(margin,top-6);stream.lineTo(width-margin,top-6);stream.stroke();stream.restoreGraphicsState();}
                    for(var cell:row.cells())textOnPage(stream,cell,top);
                    top-=row.height();
                }
                if(pages.size()>1)LabelService.text(stream,regular,"Page "+(index+1)+" / "+pages.size(),7,margin,margin);
            }
        }
    }
    private static void textOnPage(PDPageContentStream stream,Cell cell,float top) throws IOException {LabelService.text(stream,cell.font(),cell.text(),cell.size(),cell.x(),top-cell.size());}
}
