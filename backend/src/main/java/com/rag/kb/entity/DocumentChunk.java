package com.rag.kb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_MERGED = "merged";
    public static final String STATUS_SPLIT = "split";
    public static final String STATUS_EDITED = "edited";

    public static final String SPLIT_METHOD_LLM = "LLM";
    public static final String SPLIT_METHOD_RULE = "RULE";
    public static final String SPLIT_METHOD_MD = "MD";

    @Id
    @Column(name = "chunk_id", length = 64)
    private String chunkId;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(name = "doc_name", length = 255)
    private String docName;

    @Column(name = "page_num")
    private Integer pageNum;

    @Column(name = "chapter_title", length = 255)
    private String chapterTitle;

    /** Markdown 标题层级（1-6），非标题驱动拆分时为空 */
    @Column(name = "heading_level")
    private Integer headingLevel;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "LONGTEXT")
    private String chunkText;

    /** 原始 Markdown 文本（清洗前），供前端展示引用原文/人工核对 */
    @Column(name = "raw_content", columnDefinition = "LONGTEXT")
    private String rawContent;

    @Column(name = "char_start")
    private Integer charStart;

    @Column(name = "char_end")
    private Integer charEnd;

    @Column(name = "split_method", length = 20)
    private String splitMethod = SPLIT_METHOD_LLM;

    @Column(name = "llm_model", length = 50)
    private String llmModel;

    @Column(name = "status", length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "merged_into_chunk_id", length = 64)
    private String mergedIntoChunkId;

    @Column(name = "parent_chunk_id", length = 64)
    private String parentChunkId;

    @Column(name = "edited_version_of", length = 64)
    private String editedVersionOf;

    @Column(name = "quality_score")
    private Float qualityScore;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

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
