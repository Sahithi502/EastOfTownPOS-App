package com.eastoftown.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiptLayoutTest {
    final BillingService billing=mock(BillingService.class);
    final ObjectMapper json=new ObjectMapper();
    final ReceiptService receipts=new ReceiptService(billing,new LabelService(null,null),json);
    Map<String,Object> data,invoice;
    List<Map<String,Object>> items;
    static Map<String,Object> row(Object... values){var row=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)row.put(values[i].toString(),values[i+1]);return row;}
    static Map<String,Object> item(String name,long unit,int quantity,long discount,long tax){return row("product_name",name,"size","M","colour","Blue","sku","TEST-ONLY-SKU","unit_price_paise",unit,"quantity",quantity,"discount_paise",0L,"bill_discount_paise",discount,"tax_paise",tax,"tax_rate_bps",500L,"line_total_paise",unit*quantity-discount+tax);}
    @BeforeEach void setup() throws Exception {
        invoice=row("invoice_number","TEST-PREVIEW-000001","created_at","2026-10-28T06:30:00Z","customer_name","TEST ONLY Customer","customer_phone","+919999800011","status","COMPLETED","subtotal_paise",349700L,"discount_paise",20000L,"tax_paise",0L,"total_paise",329700L,"shop_snapshot",json.writeValueAsString(row("shopName","East of Town Men's Fashion","address","Test shop address","phone","0000000000","thankYou","Thank you! Visit again")));
        items=new ArrayList<>(List.of(item("Formal Shirt",99900,2,20000,0),item("Jeans",149900,1,0,0)));
        data=row("invoice",invoice,"items",items,"payments",List.of(row("method","UPI","amount_paise",329700L)),"reversals",List.of(),"refunds",List.of());
        when(billing.invoice("test-only")).thenReturn(data);
    }
    String text(int width) throws Exception {try(var pdf=Loader.loadPDF(receipts.pdf("test-only",width))){return new PDFTextStripper().getText(pdf);}}
    @Test void referenceLayoutKeepsSnapshotsAndReconcilesTheDisplayedAmounts() throws Exception {
        for(int width:List.of(58,80))try(var pdf=Loader.loadPDF(receipts.pdf("test-only",width))){
            assertEquals(1,pdf.getNumberOfPages());assertEquals(width*72/25.4,pdf.getPage(0).getMediaBox().getWidth(),.01);
            String text=new PDFTextStripper().getText(pdf);
            for(String required:List.of("EAST OF TOWN","MEN'S WEAR & FASHION","SALES RECEIPT","TEST-PREVIEW-000001","28/10/2026","ITEM","QTY","AMOUNT","1,998.00","1,499.00","3,497.00","200.00","3,297.00","Line total: Rs. 1,798.00","Payment: UPI (Paid)","THANK YOU! VISIT AGAIN"))assertTrue(text.contains(required),required);
            assertFalse(text.contains("VAS TRALAYA"));assertFalse(text.contains("Hyderabad"));bounds(pdf);
            Path dir=Files.createDirectories(Path.of("target","test-fixtures"));Files.write(dir.resolve("reference-receipt-"+width+".pdf"),receipts.pdf("test-only",width));ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,150),"png",dir.resolve("reference-receipt-"+width+".png").toFile());
        }
    }
    @Test void splitPaymentTaxAndReturnsRemainVisibleWithoutChangingOriginalTotals() throws Exception {
        items.set(0,item("Formal Shirt",99900,2,20000,8990));invoice.put("tax_paise",8990L);invoice.put("total_paise",338690L);
        data.put("payments",List.of(row("method","CASH","amount_paise",100000L),row("method","UPI","amount_paise",238690L)));
        data.put("reversals",List.of(row("kind","RETURN","total_paise",10000L,"reason","TEST ONLY return")));data.put("refunds",List.of(row("method","CASH","amount_paise",10000L)));
        String text=text(80);for(String required:List.of("Tax 5%: Rs. 89.90","3,386.90","Original payment: Split (Paid)","1,000.00","2,386.90","Returns / cancellation recorded","RECORDED REVERSALS","Refund CASH","Original sale totals above are preserved."))assertTrue(text.contains(required),required);
        assertEquals(338690L,invoice.get("total_paise"));
        try(var pdf=Loader.loadPDF(receipts.pdf("test-only",80))){bounds(pdf);ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,150),"png",Files.createDirectories(Path.of("target","test-fixtures")).resolve("reference-receipt-return.png").toFile());}
    }
    @Test void longReceiptsWrapAndPaginateWithoutClippingOrLosingItems() throws Exception {
        items.clear();for(int n=0;n<100;n++)items.add(item("TEST ITEM "+n+" Long oversized cotton shirt with a very long descriptive product name",100,1,0,0));
        invoice.put("subtotal_paise",10000L);invoice.put("discount_paise",0L);invoice.put("total_paise",10000L);
        for(int width:List.of(58,80))try(var pdf=Loader.loadPDF(receipts.pdf("test-only",width))){assertTrue(pdf.getNumberOfPages()>1);String text=new PDFTextStripper().getText(pdf);for(int n=0;n<100;n++)assertTrue(text.contains("TEST ITEM "+n+" "));assertTrue(text.contains("TOTAL"));assertTrue(text.contains("THANK YOU! VISIT AGAIN"));bounds(pdf);for(int page=1;page<=pdf.getNumberOfPages();page++){var reader=new PDFTextStripper();reader.setStartPage(page);reader.setEndPage(page);String part=reader.getText(pdf);assertTrue(part.contains("TEST-PREVIEW-000001"));assertEquals(occurrences(part,"TEST ITEM "),occurrences(part,"Unit: Rs. 1.00"),"Each item's details must stay on its page");}if(width==58){var renderer=new PDFRenderer(pdf);var dir=Files.createDirectories(Path.of("target","test-fixtures"));ImageIO.write(renderer.renderImageWithDPI(0,100),"png",dir.resolve("reference-receipt-long-first.png").toFile());ImageIO.write(renderer.renderImageWithDPI(pdf.getNumberOfPages()-1,100),"png",dir.resolve("reference-receipt-long-last.png").toFile());}}
    }
    @Test void largeAmountsStayInsideBothPaperWidths() throws Exception {
        items.clear();items.add(item("TEST ONLY expensive stock",999999999L,1000,0,0));invoice.put("subtotal_paise",999999999000L);invoice.put("discount_paise",0L);invoice.put("total_paise",999999999000L);
        for(int width:List.of(58,80))try(var pdf=Loader.loadPDF(receipts.pdf("test-only",width))){assertTrue(new PDFTextStripper().getText(pdf).contains("9,999,999,990.00"));bounds(pdf);}
    }
    @Test void guestZeroTotalAndCustomShopDetailsAreSupported() throws Exception {
        invoice.put("customer_name",null);invoice.put("customer_phone",null);invoice.put("shop_snapshot",json.writeValueAsString(row("shopName","Custom test shop","address","First test line\nSecond test line","phone","","thankYou","Custom thanks")));
        items.clear();items.add(item("TEST ONLY gift",100,1,100,0));invoice.put("subtotal_paise",100L);invoice.put("discount_paise",100L);invoice.put("total_paise",0L);data.put("payments",List.of());
        String text=text(58);assertTrue(text.contains("CUSTOM TEST SHOP"));assertTrue(text.contains("Customer: Guest"));assertTrue(text.contains("No payment due"));assertTrue(text.contains("CUSTOM THANKS"));assertFalse(text.contains("Mobile:"));
    }
    @Test void invalidPaperIsRejectedBeforeLoadingAnInvoice(){assertThrows(IllegalArgumentException.class,()->receipts.pdf("test-only",79));verify(billing,never()).invoice(anyString());}
    static int occurrences(String value,String needle){return (value.length()-value.replace(needle,"").length())/needle.length();}
    static void bounds(org.apache.pdfbox.pdmodel.PDDocument pdf) throws Exception {
        for(int n=0;n<pdf.getNumberOfPages();n++){
            var box=pdf.getPage(n).getMediaBox();final float width=box.getWidth(),height=box.getHeight();
            var reader=new PDFTextStripper(){@Override protected void processTextPosition(TextPosition p){assertTrue(p.getXDirAdj()>=9,"Left clipping: "+p.getUnicode());assertTrue(p.getXDirAdj()+p.getWidthDirAdj()<=width-9+.5f,"Right clipping: "+p.getUnicode());assertTrue(p.getYDirAdj()>0&&p.getYDirAdj()<height-5,"Vertical clipping: "+p.getUnicode());super.processTextPosition(p);}};
            reader.setStartPage(n+1);reader.setEndPage(n+1);reader.getText(pdf);
        }
    }
}
