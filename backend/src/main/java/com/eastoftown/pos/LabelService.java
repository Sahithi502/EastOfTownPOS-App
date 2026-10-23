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
    public record Request(@NotEmpty @Size(max=50) List<@NotNull @Valid Item> items,@Min(35) @Max(110) double widthMm,@Min(25) @Max(100) double heightMm){}
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
            if((request.widthMm()-6)/(barcode.modules(code).length+20)<0.19)throw new IllegalArgumentException("Label is too narrow for barcode "+row.get("sku")+". Increase width; the stored barcode will be preserved.");
            labels.add(new Label(item.variantId(),row.get("name").toString(),row.get("sku").toString(),row.get("size")+" / "+row.get("colour"),code,((Number)row.get("selling_paise")).longValue(),item.copies()));
        }
        return labels;
    }
    public Object preview(Request request){
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
    public byte[] pdf(Request request){return render(labels(request),request.widthMm(),request.heightMm());}
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
        byte[] bytes=test?render(List.of(new Label("test","East of Town test label","TEST","M / Black","20000000000000",0,1)),request.widthMm(),request.heightMm()):pdf(request);
        try(PDDocument document=org.apache.pdfbox.Loader.loadPDF(bytes)){
            PrinterJob job=PrinterJob.getPrinterJob();job.setPrintService(selected);job.setJobName(test?"East of Town - TEST LABEL":"East of Town - barcode labels");
            Paper paper=new Paper();double width=request.widthMm()*72/25.4,height=request.heightMm()*72/25.4;paper.setSize(width,height);paper.setImageableArea(0,0,width,height);PageFormat format=new PageFormat();format.setPaper(paper);
            Book book=new Book();book.append(new PDFPrintable(document,Scaling.ACTUAL_SIZE),format,document.getNumberOfPages());job.setPageable(book);job.print();
            return Map.of("status","submitted","message","Job submitted to Windows print spooler. Check the printer for paper and output.");
        }catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Printing failed. Save the PDF and check the printer.");}
    }
}
