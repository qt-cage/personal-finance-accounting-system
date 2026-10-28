package com.finance.common.util;

import com.finance.common.exception.BusinessException;
import com.finance.entity.Bill;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** 有界 CSV 解析和纯数据 XLSX；文本单元格不解释为公式。 */
public final class BillFileFormat {
    private BillFileFormat() {}
    public static List<List<String>> csv(String source) {
        if(source.startsWith("\uFEFF"))source=source.substring(1);
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();
        boolean quoted=false,closed=false;
        for(int i=0;i<source.length();i++) {
            char c=source.charAt(i);
            if(quoted){
                if(c=='"'){if(i+1<source.length()&&source.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}
                else cell.append(c);
            }else if(c=='"'){
                if(cell.length()!=0||closed)throw invalid();quoted=true;
            }else if(c==','||c=='\r'||c=='\n'){
                row.add(cell.toString());cell.setLength(0);closed=false;
                if(c!=','){
                    if(c=='\r'&&i+1<source.length()&&source.charAt(i+1)=='\n')i++;
                    rows.add(row);row=new ArrayList<>();
                    if(rows.size()>501)throw new BusinessException(400,"一次最多导入500笔账单");
                }
            }else{
                if(closed)throw invalid();cell.append(c);
            }
            if(cell.length()>1000||row.size()>6)throw invalid();
        }
        if(quoted)throw invalid();
        if(cell.length()>0||closed||!row.isEmpty()){row.add(cell.toString());rows.add(row);}
        if(rows.size()>501)throw new BusinessException(400,"一次最多导入500笔账单");
        return rows;
    }
    private static BusinessException invalid(){return new BusinessException(400,"CSV格式无效，请使用通用模板并正确填写引号");}
    public static byte[] xlsx(List<Bill> bills) {
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        sheet.append("<row r=\"1\">");
        for(String name:Arrays.asList("日期","类型","分类","账户","转入账户","金额","备注"))textCell(sheet,name);
        sheet.append("</row>");int n=2;
        for(Bill b:bills){
            sheet.append("<row r=\"").append(n++).append("\">");
            textCell(sheet,b.getBillDate().toString());textCell(sheet,b.getType()==1?"支出":b.getType()==2?"收入":"转账");
            textCell(sheet,b.getCategoryName());textCell(sheet,b.getAccountName());textCell(sheet,b.getTransferAccountName());
            sheet.append("<c t=\"n\"><v>").append(b.getAmount().toPlainString()).append("</v></c>");textCell(sheet,b.getRemark());sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");
        try(ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(out)){
            part(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            part(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            part(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"账单\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            part(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            part(zip,"xl/worksheets/sheet1.xml",sheet.toString());zip.finish();return out.toByteArray();
        }catch(IOException error){throw new IllegalStateException("无法生成Excel文件",error);}
    }
    private static void part(ZipOutputStream zip,String path,String data)throws IOException{zip.putNextEntry(new ZipEntry(path));zip.write(data.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private static void textCell(StringBuilder xml,String text){xml.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(Objects.toString(text,""))).append("</t></is></c>");}
    private static String escape(String text){StringBuilder out=new StringBuilder();text.codePoints().filter(c->c==9||c==10||c==13||(c>=32&&c<=0xD7FF)||(c>=0xE000&&c<=0xFFFD)||(c>=0x10000&&c<=0x10FFFF)).forEach(out::appendCodePoint);return out.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
