package com.eastoftown.pos;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Service;

@Service
public class ReportExport {
    final LabelService labels;
    public ReportExport(LabelService labels){this.labels=labels;}
    public byte[] excel(ReportService.Report report){
        try(var book=new XSSFWorkbook()){
            var sheet=book.createSheet("Report");var info=book.createSheet("Read me");
            String[] notes={"East of Town Men's Fashion",report.title(),report.range().from()+" to "+report.range().to()+" (Asia/Kolkata)",report.note(),"Amounts are INR with two decimal places. Blank = unknown. Text cells are literal values, never formulas.","Generated from committed local database records. Keep customer exports secure."};
            for(int i=0;i<notes.length;i++)info.createRow(i).createCell(0).setCellValue(notes[i]);info.setColumnWidth(0,120*256);
            var heading=book.createCellStyle();var bold=book.createFont();bold.setBold(true);heading.setFont(bold);
            var amount=book.createCellStyle();amount.setDataFormat(book.createDataFormat().getFormat("#,##0.00;[Red]-#,##0.00"));
            var header=sheet.createRow(0);for(int c=0;c<report.columns().size();c++){var cell=header.createCell(c);cell.setCellValue(report.columns().get(c).label());cell.setCellStyle(heading);sheet.setColumnWidth(c,24*256);}
            int index=1;for(var source:report.rows()){var row=sheet.createRow(index++);for(int c=0;c<report.columns().size();c++){var col=report.columns().get(c);Object value=source.get(col.key());var cell=row.createCell(c);if(value==null)continue;
                if(col.kind().equals("MONEY")){long paise=((Number)value).longValue();if(BigDecimal.valueOf(paise).precision()>15)cell.setCellValue(BigDecimal.valueOf(paise,2).toPlainString());else cell.setCellValue(BigDecimal.valueOf(paise,2).doubleValue());cell.setCellStyle(amount);}
                else if(col.kind().equals("INTEGER"))cell.setCellValue(((Number)value).longValue());else cell.setCellValue(value.toString());
            }}
            sheet.createFreezePane(0,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,index-1),0,report.columns().size()-1));sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(0,0,-1,-1));sheet.getPrintSetup().setLandscape(true);return ExcelFiles.bytes(book);
        }catch(IOException e){throw new IllegalStateException("Could not export report workbook",e);}
    }
    public byte[] pdf(ReportService.Report report){
        try(var document=new PDDocument()){
            PDFont font=labels.font(document);float width=report.columns().size()>10?1190:842,height=report.columns().size()>10?842:595,margin=26;
            float totalWeight=0;for(var col:report.columns())totalWeight+=weight(col);float[] widths=new float[report.columns().size()];for(int i=0;i<widths.length;i++)widths[i]=(width-2*margin)*weight(report.columns().get(i))/totalWeight;
            try(var table=new Table(document,font,report,width,height,margin,widths)){
                table.newPage();if(report.rows().isEmpty())table.message("No matching records.");
                for(var row:report.rows()){
                    List<List<String>> cells=new ArrayList<>();int count=1;for(int i=0;i<widths.length;i++){var col=report.columns().get(i);Object value=row.get(col.key());String text=value==null?"Unknown":col.kind().equals("MONEY")?BigDecimal.valueOf(((Number)value).longValue(),2).toPlainString():value.toString();var lines=LabelService.wrap(font,clean(text),8,widths[i]-6,10000);cells.add(lines);count=Math.max(count,lines.size());}
                    int offset=0;while(offset<count){int room=(int)((table.y-margin-10)/11);if(room<1){table.newPage();continue;}int take=Math.min(room,count-offset);table.draw(cells,offset,take,8);offset+=take;if(offset<count)table.newPage();}
                }
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream();document.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Could not export PDF report",e);}
    }
    static float weight(ReportService.Column c){return c.kind().equals("TEXT")?1.5f:c.kind().equals("MONEY")?1.3f:.85f;}
    static String clean(String text){return text.replaceAll("[\\p{Cntrl}]"," ");}
    static class Table implements AutoCloseable {
        final PDDocument document;final PDFont font;final ReportService.Report report;final float width,height,margin;final float[] widths;PDPageContentStream stream;float y;int page=0;
        Table(PDDocument d,PDFont f,ReportService.Report r,float w,float h,float m,float[] widths){document=d;font=f;report=r;width=w;height=h;margin=m;this.widths=widths;}
        void newPage()throws IOException{if(stream!=null)stream.close();var p=new PDPage(new PDRectangle(width,height));document.addPage(p);stream=new PDPageContentStream(document,p);y=height-margin;LabelService.text(stream,font,"East of Town - "+report.title(),13,margin,y);y-=18;LabelService.text(stream,font,report.range().from()+" to "+report.range().to()+" / Asia/Kolkata / INR / page "+(++page),9,margin,y);y-=15;for(String line:LabelService.wrap(font,clean(report.note()),8,width-2*margin,100)){LabelService.text(stream,font,line,8,margin,y);y-=11;}y-=8;List<List<String>> heads=new ArrayList<>();int lines=1;for(int i=0;i<widths.length;i++){var h=LabelService.wrap(font,report.columns().get(i).label(),8,widths[i]-6,100);heads.add(h);lines=Math.max(lines,h.size());}draw(heads,0,lines,8);}
        void draw(List<List<String>> cells,int offset,int count,int size)throws IOException{float x=margin;for(int i=0;i<cells.size();i++){for(int j=0;j<count;j++)if(offset+j<cells.get(i).size())LabelService.text(stream,font,cells.get(i).get(offset+j),size,x+3,y-j*11);x+=widths[i];}y-=count*11+8;stream.moveTo(margin,y+13);stream.lineTo(width-margin,y+13);stream.setLineWidth(.2f);stream.stroke();}
        void message(String value)throws IOException{LabelService.text(stream,font,value,10,margin,y);}
        public void close()throws IOException{if(stream!=null)stream.close();}
    }
}
