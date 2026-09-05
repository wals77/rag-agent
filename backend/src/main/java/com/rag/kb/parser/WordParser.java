package com.rag.kb.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Word(.docx) 解析：抽取段落文本。docx 原生不暴露物理页码，
 * 这里按每约 40 行近似划分“页”，便于引用溯源展示。
 */
public class WordParser implements DocumentParser {

    private static final int LINES_PER_VIRTUAL_PAGE = 40;

    @Override
    public boolean supports(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".docx");
    }

    @Override
    public ParsedDocument parse(String filename, byte[] bytes) throws IOException {
        ParsedDocument doc = new ParsedDocument(filename);
        StringBuilder pageBuffer = new StringBuilder();
        int pageLines = 0;

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph para : document.getParagraphs()) {
                String line = paragraphText(para).trim();
                if (line.isEmpty()) continue;
                pageBuffer.append(line).append('\n');
                pageLines++;
                if (pageLines >= LINES_PER_VIRTUAL_PAGE) {
                    doc.addPage(doc.totalPages() + 1, pageBuffer.toString().trim());
                    pageBuffer.setLength(0);
                    pageLines = 0;
                }
            }
        }
        if (pageBuffer.length() > 0) {
            doc.addPage(doc.totalPages() + 1, pageBuffer.toString().trim());
        }
        if (doc.totalPages() == 0) {
            doc.addPage(1, "");
        }
        return doc;
    }

    private String paragraphText(XWPFParagraph para) {
        StringBuilder sb = new StringBuilder();
        List<XWPFRun> runs = para.getRuns();
        for (XWPFRun run : runs) {
            sb.append(run.text());
        }
        if (sb.length() == 0) {
            sb.append(para.getText());
        }
        return sb.toString();
    }
}
