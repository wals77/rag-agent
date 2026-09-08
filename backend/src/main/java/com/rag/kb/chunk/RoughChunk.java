package com.rag.kb.chunk;

/** 规则粗切后的父块 */
public class RoughChunk {

    private final String text;
    private final int pageNum;
    private final String chapterTitle;
    private final int charStart;
    private final int charEnd;
    /** 标题层级（1-6），非标题驱动拆分时为 null */
    private final Integer headingLevel;
    /** 所属父章节（如 ## 章节路径）的唯一 key，用于同章节子块分组；无章节归属时为 null */
    private final String sectionKey;
    /** 是否为父章节整体内容块（仅用于落库 status=split 的父节点，不参与检索） */
    private final boolean sectionParent;

    public RoughChunk(String text, int pageNum, String chapterTitle, int charStart, int charEnd) {
        this(text, pageNum, chapterTitle, charStart, charEnd, null, null, false);
    }

    public RoughChunk(String text, int pageNum, String chapterTitle, int charStart, int charEnd,
                      Integer headingLevel, String sectionKey, boolean sectionParent) {
        this.text = text;
        this.pageNum = pageNum;
        this.chapterTitle = chapterTitle;
        this.charStart = charStart;
        this.charEnd = charEnd;
        this.headingLevel = headingLevel;
        this.sectionKey = sectionKey;
        this.sectionParent = sectionParent;
    }

    public String text() { return text; }
    public int pageNum() { return pageNum; }
    public String chapterTitle() { return chapterTitle; }
    public int charStart() { return charStart; }
    public int charEnd() { return charEnd; }
    public Integer headingLevel() { return headingLevel; }
    public String sectionKey() { return sectionKey; }
    public boolean isSectionParent() { return sectionParent; }
}