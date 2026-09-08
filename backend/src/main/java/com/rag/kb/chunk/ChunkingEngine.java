package com.rag.kb.chunk;

import com.rag.kb.config.RagProperties;
import com.rag.kb.entity.Document;
import com.rag.kb.entity.DocumentChunk;
import com.rag.kb.parser.ParsedDocument;
import com.rag.kb.util.IdGen;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块引擎：
 * 1. 规则粗切得到父块（RULE）
 * 2. LLM 对父块做语义细分 -> 子块(LLM, parent_chunk_id=父块)；父块状态置为 split（不参与检索）
 * 3. LLM 不可用/无需细分时，父块本身作为 active 块参与检索
 */
@Service
public class ChunkingEngine {

    private final LlmChunkingService llmChunking;
    private final RagProperties props;

    public ChunkingEngine(LlmChunkingService llmChunking, RagProperties props) {
        this.llmChunking = llmChunking;
        this.props = props;
    }

    public static class ChunkBatch {
        /** 需要写入 MySQL 的全部行（含被拆分的父块） */
        private final List<DocumentChunk> chunks = new ArrayList<>();
        /** active 子集，用于向量化 + ES 入库 */
        private final List<DocumentChunk> actives = new ArrayList<>();

        public List<DocumentChunk> chunks() { return chunks; }
        public List<DocumentChunk> actives() { return actives; }
    }

    public ChunkBatch build(Document doc, ParsedDocument parsed, List<RoughChunk> roughs) {
        ChunkBatch batch = new ChunkBatch();
        if (isMarkdown(doc.getDocName())) {
            // Markdown：AST 结构化拆分结果直接落库，不使用 LLM 细分
            buildMd(doc, roughs, batch);
            return batch;
        }
        for (RoughChunk rc : roughs) {
            List<String> pieces = llmChunking.refine(rc.text());
            if (pieces == null) {
                // 规则父块直接作为 active 块
                DocumentChunk c = newChunk(doc, rc, null, rc.text(), rc.charStart(), rc.charEnd(),
                        DocumentChunk.SPLIT_METHOD_RULE, null, null);
                batch.chunks().add(c);
                batch.actives().add(c);
            } else {
                // 保存父块（status=split，仅作为溯源父节点，不参与检索）
                DocumentChunk parent = newChunk(doc, rc, null, rc.text(), rc.charStart(), rc.charEnd(),
                        DocumentChunk.SPLIT_METHOD_RULE, null, null);
                parent.setStatus(DocumentChunk.STATUS_SPLIT);
                batch.chunks().add(parent);

                // 计算每个子块在父块中的字符区间（忽略空白差异，按序定位）
                List<int[]> spans = locate(pieces, rc.text());
                int offset = 0;
                for (int i = 0; i < pieces.size(); i++) {
                    int[] span = spans.get(i);
                    DocumentChunk child = newChunk(doc, rc, parent.getChunkId(), pieces.get(i),
                            rc.charStart() + span[0], rc.charStart() + span[1],
                            DocumentChunk.SPLIT_METHOD_LLM, props.getChatModel(), null);
                    child.setParentChunkId(parent.getChunkId());
                    batch.chunks().add(child);
                    batch.actives().add(child);
                }
            }
        }
        return batch;
    }

    /**
     * Markdown 落库：同一父章节（sectionKey 相同且连续）的超长拆分子块挂到
     * 父章节块（status=split）之下，供检索命中子块时扩展整章上下文。
     */
    private void buildMd(Document doc, List<RoughChunk> roughs, ChunkBatch batch) {
        DocumentChunk parent = null;
        String currentKey = null;
        for (RoughChunk rc : roughs) {
            String key = rc.sectionKey();
            if (key == null || !key.equals(currentKey)) {
                parent = null;
                currentKey = key;
            }
            if (rc.isSectionParent()) {
                DocumentChunk p = newChunk(doc, rc, null, rc.text(), rc.charStart(), rc.charEnd(),
                        DocumentChunk.SPLIT_METHOD_MD, null, null);
                p.setStatus(DocumentChunk.STATUS_SPLIT);
                batch.chunks().add(p);
                parent = p;
                continue;
            }
            DocumentChunk c = newChunk(doc, rc, parent == null ? null : parent.getChunkId(),
                    rc.text(), rc.charStart(), rc.charEnd(), DocumentChunk.SPLIT_METHOD_MD, null, null);
            batch.chunks().add(c);
            batch.actives().add(c);
        }
    }

    private boolean isMarkdown(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    private DocumentChunk newChunk(Document doc, RoughChunk rc, String parentId, String text,
                                   int charStart, int charEnd, String method, String llmModel, String tag) {
        DocumentChunk c = new DocumentChunk();
        c.setChunkId(IdGen.chunkId(doc.getDocId(), method.toLowerCase()));
        c.setDocId(doc.getDocId());
        c.setDocName(doc.getDocName());
        c.setPageNum(rc.pageNum());
        c.setChapterTitle(rc.chapterTitle());
        c.setHeadingLevel(rc.headingLevel());
        c.setChunkText(text);
        c.setCharStart(charStart);
        c.setCharEnd(charEnd);
        c.setSplitMethod(method);
        c.setLlmModel(llmModel);
        c.setStatus(DocumentChunk.STATUS_ACTIVE);
        c.setParentChunkId(parentId);
        c.setMergedIntoChunkId(null);
        c.setEditedVersionOf(null);
        c.setQualityScore(estimateQuality(text));
        return c;
    }

    /** 子块按序出现在原文中的 [start,end) 区间 */
    private List<int[]> locate(List<String> pieces, String original) {
        String norm = original.replaceAll("\\s+", "");
        int cursor = 0;
        List<int[]> spans = new ArrayList<>();
        for (String p : pieces) {
            String np = p.replaceAll("\\s+", "");
            int idx = norm.indexOf(np, cursor);
            if (idx < 0) {
                // 找不到则退回 parent 末位置，尽量不抛错
                spans.add(new int[]{cursor, Math.min(norm.length(), cursor + np.length())});
                cursor = Math.min(norm.length(), cursor + np.length());
                continue;
            }
            spans.add(new int[]{idx, idx + np.length()});
            cursor = idx + np.length();
        }
        return spans;
    }

    /** 简单质量评分：过短或超长会略微降权，供后台查看参考 */
    private float estimateQuality(String text) {
        if (text == null) return 0.5f;
        int len = text.length();
        if (len >= 120 && len <= 1200) return 1.0f;
        if (len >= 60) return 0.8f;
        return Math.max(0.2f, len / 200.0f);
    }
}
