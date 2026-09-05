package com.rag.kb.chunk;

/** 规则粗切后的父块 */
public class RoughChunk {

    private final String text;
    private final int pageNum;
    private final String chapterTitle;
    private final int charStart;
    private final int charEnd;

    public RoughChunk(String text, int pageNum, String chapterTitle, int charStart, int charEnd) {
        this.text = text;
        this.pageNum = pageNum;
        this.chapterTitle = chapterTitle;
        this.charStart = charStart;
        this.charEnd = charEnd;
    }

    public String text() { return text; }
    public int pageNum() { return pageNum; }
    public String chapterTitle() { return chapterTitle; }
    public int charStart() { return charStart; }
    public int charEnd() { return charEnd; }
}
