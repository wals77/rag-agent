package com.rag.kb.rag;

import com.rag.kb.entity.DocumentChunk;
import com.rag.kb.repository.DocumentChunkRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * 父章节上下文扩展：Markdown 检索命中某个子块（超长 ## 章节拆出的子块）时，
 * 主动把同一父章节（同一 ##）下的所有子块一并纳入上下文，确保模型拿到完整逻辑链。
 * 父章节子块通过 parent_chunk_id 关联（见 ChunkingEngine#buildMd）。
 */
@Component
public class SectionContextExpander {

    /** 每个父章节最多扩展的兄弟子块数 */
    private static final int MAX_SIBLINGS_PER_PARENT = 6;
    /** 扩展后候选总数上限 */
    private static final int MAX_TOTAL = 12;

    private final DocumentChunkRepository chunkRepository;

    public SectionContextExpander(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public List<RetrievalService.Candidate> expand(List<RetrievalService.Candidate> ranked) {
        return expand(ranked, chunkRepository::findByParentChunkId);
    }

    /** 可注入兄弟块加载器的核心逻辑，便于测试 */
    public List<RetrievalService.Candidate> expand(List<RetrievalService.Candidate> ranked,
                                                   Function<String, List<DocumentChunk>> siblingLoader) {
        if (ranked == null || ranked.isEmpty()) {
            return ranked;
        }
        LinkedHashMap<String, RetrievalService.Candidate> out = new LinkedHashMap<>();
        for (RetrievalService.Candidate c : ranked) {
            if (out.size() >= MAX_TOTAL) {
                break;
            }
            out.putIfAbsent(c.chunk().getChunkId(), c);
            String parentId = c.chunk().getParentChunkId();
            if (parentId == null || parentId.isBlank()) {
                continue;
            }
            List<DocumentChunk> siblings;
            try {
                siblings = siblingLoader.apply(parentId);
            } catch (Exception e) {
                continue;
            }
            int added = 0;
            for (DocumentChunk sib : siblings) {
                if (added >= MAX_SIBLINGS_PER_PARENT || out.size() >= MAX_TOTAL) {
                    break;
                }
                if (!DocumentChunk.STATUS_ACTIVE.equals(sib.getStatus())) {
                    continue;
                }
                if (out.containsKey(sib.getChunkId())) {
                    continue;
                }
                out.put(sib.getChunkId(), new RetrievalService.Candidate(sib, c.score(), List.of("parent_section")));
                added++;
            }
        }
        return new ArrayList<>(out.values());
    }
}