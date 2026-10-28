package com.finance.common.util;
import com.finance.common.exception.BusinessException;
import com.finance.entity.Bill;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class BillFileFormatTest {
    @Test void csvPreservesQuotedCommaNewlineEscapedQuotesAndBom(){List<List<String>> rows=BillFileFormat.csv("\uFEFFa,b,c\r\n1,\"two, \"\"quoted\"\"\nlines\",3\r\n");assertEquals(2,rows.size());assertEquals("two, \"quoted\"\nlines",rows.get(1).get(1));}
    @Test void csvRejectsUnclosedQuotesAndUnexpectedSuffix(){assertThrows(BusinessException.class,()->BillFileFormat.csv("a,\"open"));assertThrows(BusinessException.class,()->BillFileFormat.csv("a,\"close\"extra"));assertThrows(BusinessException.class,()->BillFileFormat.csv("a,b\n"+String.join("\n",Collections.nCopies(501,"1,2"))));}
    @Test void xlsxContainsNumbersAndLiteralFormulaTextWithEscapedXml()throws Exception{Bill b=new Bill();b.setType(1);b.setBillDate(LocalDate.of(2026,10,9));b.setAmount(new BigDecimal("12.34"));b.setRemark("=HYPERLINK(\"danger\") & <tag>\u0000");Map<String,String> parts=new HashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(BillFileFormat.xlsx(Collections.singletonList(b))))){ZipEntry e;while((e=zip.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[1024];int n;while((n=zip.read(buf))!=-1)out.write(buf,0,n);parts.put(e.getName(),out.toString("UTF-8"));}}String sheet=parts.get("xl/worksheets/sheet1.xml");assertTrue(parts.containsKey("[Content_Types].xml"));assertTrue(sheet.contains("<c t=\"n\"><v>12.34</v>"));assertTrue(sheet.contains("=HYPERLINK"));assertTrue(sheet.contains("&amp; &lt;tag&gt;"));assertFalse(sheet.contains("<f>"));assertFalse(sheet.contains("\u0000"));}
}
