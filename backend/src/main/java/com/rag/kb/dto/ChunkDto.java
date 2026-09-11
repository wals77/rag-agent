package com.rag.kb.dto;

import java.time.LocalDateTime;

public class ChunkDto {

    private String chunkId;
    private String docId;
    private String docName;
    private Integer pageNum;
    private String chapterTitle;
    private Integer headingLevel;
    private String chunkText;
    private String rawContent;
    private Integer charStart;
    private Integer charEnd;
    private String splitMethod;
    private String llmModel;
    private String status;
    private String mergedIntoChunkId;
    private String parentChunkId;
    private String editedVersionOf;
    private Float qualityScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ChunkDto from(com.rag.kb.entity.DocumentChunk c) {
        ChunkDto o = new ChunkDto();
        o.chunkId = c.getChunkId();
        o.docId = c.getDocId();
        o.docName = c.getDocName();
        o.pageNum = c.getPageNum();
        o.chapterTitle = c.getChapterTitle();
        o.headingLevel = c.getHeadingLevel();
        o.chunkText = c.getChunkText();
        o.rawContent = c.getRawContent();
        o.charStart = c.getCharStart();
        o.charEnd = c.getCharEnd();
        o.splitMethod = c.getSplitMethod();
        o.llmModel = c.getLlmModel();
        o.status = c.getStatus();
        o.mergedIntoChunkId = c.getMergedIntoChunkId();
        o.parentChunkId = c.getParentChunkId();
        o.editedVersionOf = c.getEditedVersionOf();
        o.qualityScore = c.getQualityScore();
        o.createdAt = c.getCreatedAt();
        o.updatedAt = c.getUpdatedAt();
        return o;
    }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String v) { this.chunkId = v; }
    public String getDocId() { return docId; }
    public void setDocId(String v) { this.docId = v; }
    public String getDocName() { return docName; }
    public void setDocName(String v) { this.docName = v; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer v) { this.pageNum = v; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String v) { this.chapterTitle = v; }
    public Integer getHeadingLevel() { return headingLevel; }
    public void setHeadingLevel(Integer v) { this.headingLevel = v; }
    public String getChunkText() { return chunkText; }
    public void setChunkText(String v) { this.chunkText = v; }
    public String getRawContent() { return rawContent; }
    public void setRawContent(String v) { this.rawContent = v; }
    public Integer getCharStart() { return charStart; }
    public void setCharStart(Integer v) { this.charStart = v; }
    public Integer getCharEnd() { return charEnd; }
    public void setCharEnd(Integer v) { this.charEnd = v; }
    public String getSplitMethod() { return splitMethod; }
    public void setSplitMethod(String v) { this.splitMethod = v; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String v) { this.llmModel = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getMergedIntoChunkId() { return mergedIntoChunkId; }
    public void setMergedIntoChunkId(String v) { this.mergedIntoChunkId = v; }
    public String getParentChunkId() { return parentChunkId; }
    public void setParentChunkId(String v) { this.parentChunkId = v; }
    public String getEditedVersionOf() { return editedVersionOf; }
    public void setEditedVersionOf(String v) { this.editedVersionOf = v; }
    public Float getQualityScore() { return qualityScore; }
    public void setQualityScore(Float v) { this.qualityScore = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
