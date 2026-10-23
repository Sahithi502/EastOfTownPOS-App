package com.eastoftown.pos;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;

@Component
public class ExcelFiles {
    public static final List<String> FIELDS=List.of("name","category","sku","size","colour","barcode","cost","price","quantity","reorderLevel","active");
    public record SourceRow(int row,List<String> cells,Set<Integer> numeric){}
    public record SheetData(List<String> headers,List<SourceRow> rows){}
    SheetData read(byte[] bytes){
        if(bytes.length==0 || bytes.length>10*1024*1024)throw new IllegalArgumentException("Choose a non-empty Excel file no larger than 10 MB");
        try(Workbook workbook=WorkbookFactory.create(new ByteArrayInputStream(bytes))){
            if(workbook.getNumberOfSheets()==0)throw new IllegalArgumentException("Workbook has no sheet");
            Sheet sheet=workbook.getSheetAt(0);
            Row header=sheet.getRow(0);
            if(header==null || header.getLastCellNum()<1 || header.getLastCellNum()>64)throw new IllegalArgumentException("First sheet must have a header row and at most 64 columns");
            if(sheet.getLastRowNum()>5000)throw new IllegalArgumentException("Import is limited to 5,000 data rows per file");
            DataFormatter formatter=new DataFormatter(Locale.ROOT);
            List<String> headers=new ArrayList<>();List<SourceRow> rows=new ArrayList<>();
            for(int c=0;c<header.getLastCellNum();c++){String value=formatter.formatCellValue(header.getCell(c)).trim();headers.add(value.isBlank()?"Column "+(c+1):value);}
            for(int r=1;r<=sheet.getLastRowNum();r++){
                Row row=sheet.getRow(r);if(row==null)continue;
                if(row.getLastCellNum()>headers.size())throw new IllegalArgumentException("Row "+(r+1)+" contains columns beyond the header row");
                List<String> cells=new ArrayList<>();Set<Integer> numeric=new HashSet<>();
                for(int c=0;c<headers.size();c++){
                    Cell cell=row.getCell(c);String value="";
                    if(cell!=null){if(cell.getCellType()==CellType.FORMULA)value="[FORMULA]";else if(cell.getCellType()==CellType.ERROR)value="[CELL ERROR]";else{value=formatter.formatCellValue(cell).trim();if(cell.getCellType()==CellType.NUMERIC)numeric.add(c);}}
                    if(value.length()>1000)throw new IllegalArgumentException("Cell at row "+(r+1)+" exceeds 1,000 characters");cells.add(value);
                }
                if(cells.stream().anyMatch(v->!v.isBlank()))rows.add(new SourceRow(r+1,cells,numeric));
            }
            if(rows.isEmpty())throw new IllegalArgumentException("Workbook contains no data rows");
            return new SheetData(headers,rows);
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Cannot read this workbook. Use an unencrypted .xlsx or .xls file with values, not formulas.");}
    }
    byte[] template(List<Map<String,Object>> categories){
        try(XSSFWorkbook book=new XSSFWorkbook()){
            Sheet sheet=book.createSheet("Stock");Row header=sheet.createRow(0);
            var text=book.createCellStyle();text.setDataFormat(book.createDataFormat().getFormat("@"));
            for(int c=0;c<FIELDS.size();c++){header.createCell(c).setCellValue(FIELDS.get(c));sheet.setColumnWidth(c,22*256);sheet.setDefaultColumnStyle(c,text);}
            sheet.createFreezePane(0,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,0,0,FIELDS.size()-1));
            Sheet notes=book.createSheet("Instructions");
            String[] instructions={"Fill Stock sheet; remove blank rows. One row per sellable size/colour. No example business records are included.","Required: name, category (or category override), sku, size, colour, price. SKU and barcode cells must be text.","cost optional (blank means unknown); price/cost use plain decimal amounts, maximum 2 places.","quantity = opening units for NEW variants, or signed adjustment delta for EXISTING variants in ADJUST_DELTA mode.","DETAILS_ONLY ignores quantity. OPENING_NEW ignores existing stock. No mode replaces current stock.","active true/false; blank keeps existing status or defaults to true. reorderLevel blank defaults to 3.","Existing SKU identifies an update. Blank existing barcode preserves it; different barcode is rejected.","New category names are created only when the confirmed import commits. Matching name/category groups new variants.","Only first sheet is imported. Maximum 5,000 rows, 64 columns, 10 MB. Formulas/encrypted files are rejected.","New variants with no barcode receive a permanent numeric Code 128 value. Keep existing barcodes unchanged."};
            for(int r=0;r<instructions.length;r++)notes.createRow(r).createCell(0).setCellValue(instructions[r]);notes.setColumnWidth(0,120*256);
            Sheet cats=book.createSheet("Categories");cats.createRow(0).createCell(0).setCellValue("Existing categories");int row=1;for(var category:categories)cats.createRow(row++).createCell(0).setCellValue(category.get("name").toString());
            return bytes(book);
        }catch(IOException e){throw new IllegalStateException("Unable to create Excel template",e);}
    }
    byte[] errors(ImportService.Preview preview){
        try(XSSFWorkbook book=new XSSFWorkbook()){
            Sheet sheet=book.createSheet("Validation");Row head=sheet.createRow(0);String[] columns={"Excel row","SKU","Action","Errors","Warnings"};
            for(int i=0;i<columns.length;i++)head.createCell(i).setCellValue(columns[i]);int r=1;
            for(var row:preview.rows())if(!row.errors().isEmpty() || !row.warnings().isEmpty()){
                Row target=sheet.createRow(r++);target.createCell(0).setCellValue(row.row());target.createCell(1).setCellValue(row.values().getOrDefault("sku",""));target.createCell(2).setCellValue(row.action());target.createCell(3).setCellValue(String.join("; ",row.errors()));target.createCell(4).setCellValue(String.join("; ",row.warnings()));
            }
            for(int i=0;i<columns.length;i++)sheet.setColumnWidth(i,(i>2?80:20)*256);
            return bytes(book);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    static byte[] bytes(Workbook book)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();book.write(out);return out.toByteArray();}
}
