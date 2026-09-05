package com.rag.kb.parser;

import java.util.ArrayList;
import java.util.List;

/** 解析结果：按页切分的纯文本 */
public class ParsedDocument {

    public static class ParsedPage {
        private final int pageNum;
        private final String text;

        public ParsedPage(int pageNum, String text) {
            this.pageNum = pageNum;
            this.text = text;
        }

        public int getPageNum() { return pageNum; }
        public String getText() { return text; }
    }

    private final String docName;
    private final List<ParsedPage> pages = new ArrayList<>();
    /** 是否存在使用 OCR 识别出的页面 */
    private boolean ocrUsed;

    public ParsedDocument(String docName) {
        this.docName = docName;
    }

    public void addPage(int pageNum, String text) {
        pages.add(new ParsedPage(pageNum, text));
    }

    public String getDocName() { return docName; }
    public List<ParsedPage> getPages() { return pages; }
    public int totalPages() { return pages.size(); }
    public boolean isOcrUsed() { return ocrUsed; }
    public void setOcrUsed(boolean ocrUsed) { this.ocrUsed = ocrUsed; }

    /** 拼接全文（页间以单个换行分隔），供规则粗切做全局字符定位 */
    public String fullText() {
        StringBuilder sb = new StringBuilder();
        for (ParsedPage p : pages) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.getText());
        }
        return sb.toString();
    }
}
