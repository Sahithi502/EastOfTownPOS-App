package com.eastoftown.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import javax.imageio.ImageIO;
import javax.print.PrintServiceLookup;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.printing.*;
import org.springframework.stereotype.Service;

@Service
public class LabelService {
    public record Item(@NotBlank String variantId,@Min(1) @Max(100) int copies){}
    public record Request(@NotEmpty @Size(max=50) List<@NotNull @Valid Item> items,@Min(35) @Max(110) double widthMm,@Min(25) @Max(100) double heightMm,boolean thermal){
        public Request(List<Item> items,double widthMm,double heightMm){this(items,widthMm,heightMm,false);}
    }
    record Label(String id,String name,String sku,String variant,String barcode,long price,int copies){}
    final CatalogRepository repo;final BarcodeService barcode;
    public LabelService(CatalogRepository repo,BarcodeService barcode){this.repo=repo;this.barcode=barcode;}
    List<Label> labels(Request request){
        if(request.items().stream().mapToInt(Item::copies).sum()>200)throw new IllegalArgumentException("Print at most 200 labels per job");
        List<Label> labels=new ArrayList<>();
        for(var item:request.items()){
            var rows=repo.jdbc().queryForList("SELECT v.*,p.name FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.id=?",item.variantId());if(rows.isEmpty())throw new IllegalArgumentException("Variant no longer exists");var row=rows.getFirst();
            if(((Number)row.get("active")).intValue()!=1)throw new IllegalArgumentException("Activate the variant before printing labels: "+row.get("sku"));
            String code=row.get("barcode").toString();
            // Ten quiet modules at each end; never shrink below the minimum bar width.
            int modules=barcode.modules(code).length+20;
            if(!request.thermal() && (request.widthMm()-6)/modules<0.19)throw new IllegalArgumentException("Individual label is too narrow for SKU "+row.get("sku")+". Choose Thermal POS roll — 80 mm × automatic height to fit long barcodes, or increase the individual label width. The stored barcode will be preserved.");
            labels.add(new Label(item.variantId(),row.get("name").toString(),row.get("sku").toString(),row.get("size")+" / "+row.get("colour"),code,((Number)row.get("selling_paise")).longValue(),item.copies()));
        }
        return labels;
    }
    public Object preview(Request request){
        if(request.thermal())return List.of(Map.of("variantId","thermal-roll","sku","80 mm thermal roll","copies",1,"barcode","selected products","image","data:image/png;base64,"+Base64.getEncoder().encodeToString(png(request))));
        var source=labels(request);var single=source.stream().map(l->new Label(l.id(),l.name(),l.sku(),l.variant(),l.barcode(),l.price(),1)).toList();
        try(var document=org.apache.pdfbox.Loader.loadPDF(render(single,request.widthMm(),request.heightMm()))){
            var renderer=new org.apache.pdfbox.rendering.PDFRenderer(document);List<Map<String,Object>> list=new ArrayList<>();
            for(int i=0;i<source.size();i++){
                var label=source.get(i);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(renderer.renderImageWithDPI(i,203),"png",out);
                list.add(Map.of("variantId",label.id(),"name",label.name(),"sku",label.sku(),"variant",label.variant(),"barcode",label.barcode(),"pricePaise",label.price(),"copies",label.copies(),"image","data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray())));
            }return list;
        }catch(IOException e){throw new IllegalStateException("Unable to render label preview",e);}
    }
    byte[] png(String value){try{
        boolean[] bars=barcode.modules(value);BufferedImage img=new BufferedImage((bars.length+20)*3,100,BufferedImage.TYPE_BYTE_BINARY);
        var graphics=img.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,img.getWidth(),img.getHeight());graphics.setColor(java.awt.Color.BLACK);for(int x=0;x<bars.length;x++)if(bars[x])graphics.fillRect((x+10)*3,0,3,100);graphics.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(img,"png",out);return out.toByteArray();
    }catch(IOException e){throw new IllegalStateException(e);}}
    public byte[] pdf(Request request){var source=labels(request);return request.thermal()?thermal(source):render(source,request.widthMm(),request.heightMm());}
    private byte[] png(Request request){
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf(request))){
            var box=document.getPage(0).getMediaBox();
            if((box.getWidth()*203/72)*(box.getHeight()*203/72)>16000000)throw new IllegalArgumentException("Preview is too tall. Select fewer copies or export PDF.");
            ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0,203),"png",out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Unable to render thermal preview",e);}
    }
    byte[] thermal(List<Label> labels){
        try(PDDocument document=new PDDocument()){
            PDFont font=font(document);float width=80*72/25.4f,margin=4*72/25.4f,content=width-2*margin;
            float height=55;
            for(var label:labels)height+=label.copies()*(wrap(font,label.name(),10,content,100).size()*12+wrap(font,label.variant(),9,content,100).size()*11+wrap(font,"SKU: "+label.sku(),8,content,100).size()*10+thermalBarcodeHeight(label.barcode(),content)+44+wrap(font,label.barcode(),8,content,100).size()*10);
            if(height>2000*72/25.4f)throw new IllegalArgumentException("Thermal roll exceeds 2000 mm. Print fewer copies per job.");
            PDPage page=new PDPage(new PDRectangle(width,height));document.addPage(page);
            try(PDPageContentStream stream=new PDPageContentStream(document,page)){
                float y=height-20;text(stream,font,"EAST OF TOWN",11,margin,y);y-=15;text(stream,font,"Product barcodes",9,margin,y);y-=20;
                for(var label:labels)for(int copy=0;copy<label.copies();copy++){
                    for(String line:wrap(font,label.name(),10,content,100)){text(stream,font,line,10,margin,y);y-=12;}
                    for(String line:wrap(font,label.variant(),9,content,100)){text(stream,font,line,9,margin,y);y-=11;}
                    for(String line:wrap(font,"SKU: "+label.sku(),8,content,100)){text(stream,font,line,8,margin,y);y-=10;}
                    text(stream,font,"INR "+java.math.BigDecimal.valueOf(label.price(),2).toPlainString(),10,margin,y);y-=12;
                    boolean[] bars=barcode.modules(label.barcode());boolean rotated=rotateThermal(label.barcode(),content);
                    float barHeight=thermalBarcodeHeight(label.barcode(),content),module=rotated?2*72/203f:content/(bars.length+20);y-=barHeight;
                    stream.saveGraphicsState();
                    if(rotated)stream.transform(new org.apache.pdfbox.util.Matrix(0,1,-1,0,margin+35,y));
                    for(int b=0;b<bars.length;b++)if(bars[b])stream.addRect(rotated?(b+10)*module:margin+(b+10)*module,rotated?0:y,module,35);
                    stream.fill();stream.restoreGraphicsState();y-=12;
                    for(String line:wrap(font,label.barcode(),8,content,100)){text(stream,font,line,8,margin,y);y-=10;}
                    y-=8;stream.moveTo(margin,y);stream.lineTo(width-margin,y);stream.setLineWidth(.3f);stream.stroke();y-=12;
                }
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream();document.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Could not create thermal barcode document",e);}
    }
    private boolean rotateThermal(String code,float contentWidth){return contentWidth/(barcode.modules(code).length+20)<2*72/203f;}
    private float thermalBarcodeHeight(String code,float contentWidth){return rotateThermal(code,contentWidth)?(barcode.modules(code).length+20)*2*72/203f:35;}
    byte[] render(List<Label> labels,double widthMm,double heightMm){
        try(PDDocument document=new PDDocument()){
            PDFont font=font(document);float width=(float)(widthMm*72/25.4),height=(float)(heightMm*72/25.4);
            for(var label:labels)for(int copy=0;copy<label.copies();copy++){
                PDPage page=new PDPage(new PDRectangle(width,height));document.addPage(page);
                try(PDPageContentStream stream=new PDPageContentStream(document,page)){
                    float margin=8,y=height-12;
                    for(String line:wrap(font,label.name(),8,width-2*margin,2)){text(stream,font,line,8,margin,y);y-=10;}
                    text(stream,font,label.variant(),7,margin,y);y-=10;
                    text(stream,font,"INR "+java.math.BigDecimal.valueOf(label.price(),2).toPlainString(),8,margin,y);
                    boolean[] bars=barcode.modules(label.barcode());float module=(width-2*margin)/(bars.length+20);float bottom=height<95?16:22;float barHeight=Math.min(30,Math.max(12,y-bottom-10));
                    for(int b=0;b<bars.length;b++)if(bars[b])stream.addRect(margin+(b+10)*module,bottom,module,barHeight);stream.fill();
                    text(stream,font,label.barcode(),6,margin,Math.max(7,bottom-9));
                }
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream();document.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Could not create label PDF",e);}
    }
    PDFont font(PDDocument document)throws IOException{
        String windows=System.getenv("WINDIR");if(windows!=null){File font=new File(windows,"Fonts/arial.ttf");if(font.isFile())return PDType0Font.load(document,font);}
        // Linux CI has DejaVu; neither system font file is redistributed with the application.
        File font=new File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");if(font.isFile())return PDType0Font.load(document,font);
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }
    static List<String> wrap(PDFont font,String value,int size,float max,int lines)throws IOException{
        List<String> result=new ArrayList<>();StringBuilder current=new StringBuilder();
        for(int point:value.codePoints().toArray()){
            String candidate=current.toString()+new String(Character.toChars(point));
            try{if(font.getStringWidth(candidate)/1000*size>max){result.add(current.toString());current.setLength(0);if(result.size()==lines){String last=result.removeLast();while(!last.isEmpty()&&font.getStringWidth(last+"...")/1000*size>max)last=last.substring(0,last.length()-1);result.add(last+"...");return result;}}current.appendCodePoint(point);}catch(IllegalArgumentException e){throw new IllegalArgumentException("System label font cannot print a character in the product name. Install a compatible font or use supported text.");}
        }
        if(!current.isEmpty())result.add(current.toString());return result;
    }
    static void text(PDPageContentStream stream,PDFont font,String value,int size,float x,float y)throws IOException{stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(value);stream.endText();}
    public Object printers(){return Arrays.stream(PrintServiceLookup.lookupPrintServices(null,null)).map(javax.print.PrintService::getName).sorted().toList();}
    public Object print(Request request,String printer,boolean test){
        if(printer==null || printer.isBlank())throw new IllegalArgumentException("Select a printer");
        var selected=Arrays.stream(PrintServiceLookup.lookupPrintServices(null,null)).filter(p->p.getName().equals(printer)).findFirst().orElseThrow(()->new IllegalArgumentException("Printer unavailable. Save the labels as PDF instead."));
        var testLabels=List.of(new Label("test","East of Town test label","TEST","M / Black","20000000000000",0,1));
        byte[] bytes=test?(request.thermal()?thermal(testLabels):render(testLabels,request.widthMm(),request.heightMm())):pdf(request);
        try(PDDocument document=org.apache.pdfbox.Loader.loadPDF(bytes)){
            PrinterJob job=PrinterJob.getPrinterJob();job.setPrintService(selected);job.setJobName(test?"East of Town - TEST LABEL":"East of Town - barcode labels");
            var box=document.getPage(0).getMediaBox();Paper paper=new Paper();double width=box.getWidth(),height=box.getHeight();paper.setSize(width,height);paper.setImageableArea(0,0,width,height);PageFormat format=new PageFormat();format.setPaper(paper);
            Book book=new Book();book.append(new PDFPrintable(document,Scaling.ACTUAL_SIZE),format,document.getNumberOfPages());job.setPageable(book);job.print();
            return Map.of("status","submitted","message","Job submitted to Windows print spooler. Check the printer for paper and output.");
        }catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Printing failed. Save the PDF and check the printer.");}
    }
}
