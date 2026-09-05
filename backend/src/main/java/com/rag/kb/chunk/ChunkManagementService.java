package com.rag.kb.chunk;

import com.rag.kb.config.RagProperties;
import com.rag.kb.dto.ChunkDtos;
import com.rag.kb.dto.ChunkDto;
import com.rag.kb.dto.FeedbackDto;
import com.rag.kb.dto.PageResult;
import com.rag.kb.embedding.EmbeddingService;
import com.rag.kb.entity.ChunkFeedback;
import com.rag.kb.entity.DocumentChunk;
import com.rag.kb.es.EsIndexService;
import com.rag.kb.exception.BizException;
import com.rag.kb.repository.ChunkFeedbackRepository;
import com.rag.kb.repository.DocumentChunkRepository;
import com.rag.kb.util.IdGen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 分块管理（后台）：分块列表 / 编辑(生成 edited 版本) / 合并 / 拆分 / 反馈。
 * 所有变更都维护 parent / merged_into / edited_version_of 溯源链，并同步更新 ES 向量索引。
 */
@Service
public class ChunkManagementService {

    private static final Logger log = LoggerFactory.getLogger(ChunkManagementService.class);

    private final DocumentChunkRepository chunkRepository;
    private final ChunkFeedbackRepository feedbackRepository;
    private final EmbeddingService embeddingService;
    private final EsIndexService esIndexService;
    private final RagProperties props;

    public ChunkManagementService(DocumentChunkRepository chunkRepository,
                                  ChunkFeedbackRepository feedbackRepository,
                                  EmbeddingService embeddingService,
                                  EsIndexService esIndexService,
                                  RagProperties props) {
        this.chunkRepository = chunkRepository;
        this.feedbackRepository = feedbackRepository;
        this.embeddingService = embeddingService;
        this.esIndexService = esIndexService;
        this.props = props;
    }

    public PageResult<ChunkDto> listChunks(String docId, String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, size),
                Sort.by(Sort.Direction.ASC, "pageNum").and(Sort.by(Sort.Direction.ASC, "charStart")));
        Page<DocumentChunk> p;
        if (docId != null && !docId.isBlank()) {
            p = status != null && !status.isBlank()
                    ? chunkRepository.findByDocIdAndStatus(docId, status, pr)
                    : chunkRepository.findByDocId(docId, pr);
        } else if (status != null && !status.isBlank()) {
            p = chunkRepository.findByStatus(status, pr);
        } else {
            p = chunkRepository.findAll(pr);
        }
        return new PageResult<>(p.getContent().stream().map(ChunkDto::from).toList(),
                p.getTotalElements(), p.getNumber(), p.getSize());
    }

    /** 手动编辑分块：生成 active 的新 edited 版本，原块退化为 edited */
    @Transactional
    public ChunkDtos.ChunkMutationResult edit(String chunkId, String newText) {
        DocumentChunk src = mustActive(chunkId);
        if (newText == null || newText.isBlank()) {
            throw new BizException("分块内容不能为空");
        }
        DocumentChunk edited = copyOf(src);
        edited.setChunkId(IdGen.chunkId(src.getDocId(), "e"));
        edited.setChunkText(newText.trim());
        edited.setEditedVersionOf(src.getChunkId());
        edited.setSplitMethod(DocumentChunk.SPLIT_METHOD_LLM);
        edited.setLlmModel(props.getChatModel());
        edited.setQualityScore(estimateQuality(newText));
        src.setStatus(DocumentChunk.STATUS_EDITED);
        chunkRepository.save(src);
        chunkRepository.save(edited);
        replaceInEs(src, edited);
        return new ChunkDtos.ChunkMutationResult(ChunkDto.from(edited), List.of(ChunkDto.from(src)), null);
    }

    /** 合并多个分块为一块（要求同一文档） */
    @Transactional
    public ChunkDtos.ChunkMutationResult merge(List<String> chunkIds, String mergedText) {
        if (chunkIds == null || chunkIds.size() < 2) {
            throw new BizException("合并至少需要 2 个分块");
        }
        List<DocumentChunk> sources = new ArrayList<>();
        for (String id : chunkIds.stream().distinct().toList()) {
            sources.add(mustActive(id));
        }
        String docId = sources.get(0).getDocId();
        if (sources.stream().anyMatch(c -> !docId.equals(c.getDocId()))) {
            throw new BizException("仅支持合并同一文档内的分块");
        }
        sources.sort(Comparator.comparing(DocumentChunk::getPageNum, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DocumentChunk::getCharStart, Comparator.nullsLast(Integer::compareTo)));
        String text = mergedText;
        if (text == null || text.isBlank()) {
            StringBuilder sb = new StringBuilder();
            for (DocumentChunk c : sources) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(c.getChunkText());
            }
            text = sb.toString();
        }
        DocumentChunk first = sources.get(0);
        DocumentChunk merged = copyOf(first);
        merged.setChunkId(IdGen.chunkId(docId, "m"));
        merged.setChunkText(text.trim());
        merged.setStatus(DocumentChunk.STATUS_ACTIVE);
        merged.setMergedIntoChunkId(null);
        int minChar = sources.stream().map(DocumentChunk::getCharStart)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).min().orElse(first.getCharStart());
        int maxChar = sources.stream().map(DocumentChunk::getCharEnd)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).max().orElse(first.getCharEnd());
        merged.setCharStart(minChar);
        merged.setCharEnd(maxChar);
        merged.setSplitMethod(DocumentChunk.SPLIT_METHOD_RULE);
        merged.setLlmModel(null);
        chunkRepository.save(merged);
        List<DocumentChunk> retired = new ArrayList<>();
        for (DocumentChunk c : sources) {
            c.setStatus(DocumentChunk.STATUS_MERGED);
            c.setMergedIntoChunkId(merged.getChunkId());
            chunkRepository.save(c);
            retired.add(c);
            deleteFromEs(c.getChunkId());
        }
        indexNew(merged);
        return new ChunkDtos.ChunkMutationResult(ChunkDto.from(merged),
                retired.stream().map(ChunkDto::from).toList(), null);
    }

    /** 拆分为多个子块 */
    @Transactional
    public ChunkDtos.ChunkMutationResult split(String chunkId, List<String> pieces) {
        DocumentChunk src = mustActive(chunkId);
        List<String> cleaned = pieces == null ? List.of()
                : pieces.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
        if (cleaned.size() < 2) {
            throw new BizException("拆分至少需要给出 2 个非空子块");
        }
        src.setStatus(DocumentChunk.STATUS_SPLIT);
        chunkRepository.save(src);
        deleteFromEs(src.getChunkId());

        List<DocumentChunk> children = new ArrayList<>();
        for (String piece : cleaned) {
            DocumentChunk child = copyOf(src);
            child.setChunkId(IdGen.chunkId(src.getDocId(), "s"));
            child.setChunkText(piece);
            child.setParentChunkId(src.getChunkId());
            child.setSplitMethod(DocumentChunk.SPLIT_METHOD_LLM);
            child.setLlmModel(props.getChatModel());
            child.setStatus(DocumentChunk.STATUS_ACTIVE);
            chunkRepository.save(child);
            indexNew(child);
            children.add(child);
        }
        return new ChunkDtos.ChunkMutationResult(null,
                List.of(ChunkDto.from(src)), null);
    }

    /** 用户反馈。type=wrong_content 且提供 corrected_text 时自动生成 edited 修正版本 */
    @Transactional
    public ChunkDtos.FeedbackResult submitFeedback(String chunkId, ChunkDtos.FeedbackRequest req) {
        DocumentChunk chunk = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new BizException("分块不存在: " + chunkId));
        ChunkFeedback fb = new ChunkFeedback();
        fb.setChunkId(chunkId);
        fb.setFeedbackType(req.feedbackType());
        fb.setUserComment(req.userComment());
        fb.setOldChunkText(chunk.getChunkText());
        fb.setUserId(req.userId());
        fb.setCorrectedText(req.correctedText());
        ChunkFeedback saved = feedbackRepository.save(fb);

        String notice = null;
        DocumentChunk newChunk = null;
        boolean wrongContent = "wrong_content".equals(req.feedbackType());
        if (wrongContent && req.correctedText() != null && !req.correctedText().isBlank()
                && DocumentChunk.STATUS_ACTIVE.equals(chunk.getStatus())) {
            DocumentChunk edited = copyOf(chunk);
            edited.setChunkId(IdGen.chunkId(chunk.getDocId(), "f"));
            edited.setChunkText(req.correctedText().trim());
            edited.setEditedVersionOf(chunk.getChunkId());
            edited.setSplitMethod(DocumentChunk.SPLIT_METHOD_LLM);
            edited.setLlmModel(props.getChatModel());
            chunk.setStatus(DocumentChunk.STATUS_EDITED);
            chunkRepository.save(chunk);
            chunkRepository.save(edited);
            replaceInEs(chunk, edited);
            newChunk = edited;
            notice = "已根据修正文本生成新的分块版本 " + edited.getChunkId();
        }
        return new ChunkDtos.FeedbackResult(saved.getFeedbackId(),
                newChunk == null ? "recorded" : "applied", notice,
                newChunk == null ? null : ChunkDto.from(newChunk));
    }

    public PageResult<FeedbackDto> listFeedback(int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChunkFeedback> p = feedbackRepository.findAllByOrderByCreatedAtDesc(pr);
        List<FeedbackDto> items = p.getContent().stream()
                .map(f -> FeedbackDto.from(f, chunkRepository.findById(f.getChunkId()).orElse(null)))
                .toList();
        return new PageResult<>(items, p.getTotalElements(), p.getNumber(), p.getSize());
    }

    public List<FeedbackDto> listFeedbackByChunk(String chunkId) {
        return feedbackRepository.findByChunkIdOrderByCreatedAtDesc(chunkId).stream()
                .map(f -> FeedbackDto.from(f, chunkRepository.findById(f.getChunkId()).orElse(null)))
                .toList();
    }

    // ---------------- helpers ----------------

    private DocumentChunk mustActive(String chunkId) {
        DocumentChunk c = chunkRepository.findById(chunkId)
                .orElseThrow(() -> new BizException("分块不存在: " + chunkId));
        if (!DocumentChunk.STATUS_ACTIVE.equals(c.getStatus())) {
            throw new BizException("仅 active 分块可操作，当前状态: " + c.getStatus());
        }
        return c;
    }

    private DocumentChunk copyOf(DocumentChunk src) {
        DocumentChunk c = new DocumentChunk();
        c.setDocId(src.getDocId());
        c.setDocName(src.getDocName());
        c.setPageNum(src.getPageNum());
        c.setChapterTitle(src.getChapterTitle());
        c.setCharStart(src.getCharStart());
        c.setCharEnd(src.getCharEnd());
        c.setStatus(DocumentChunk.STATUS_ACTIVE);
        return c;
    }

    private void indexNew(DocumentChunk c) {
        try {
            List<Float> vector = embeddingService.embed(c.getChunkText());
            esIndexService.ensureIndex();
            esIndexService.bulkIndexWithVectors(List.of(new EsIndexService.IndexPayload(
                    new EsIndexService.EsDoc(c.getChunkId(), c.getDocId(), c.getDocName(),
                            c.getPageNum(), c.getChapterTitle(), c.getChunkText()), vector)));
        } catch (Exception e) {
            log.warn("索引新分块失败 chunk={}: {}", c.getChunkId(), e.getMessage());
        }
    }

    private void replaceInEs(DocumentChunk retired, DocumentChunk fresh) {
        deleteFromEs(retired.getChunkId());
        indexNew(fresh);
    }

    private void deleteFromEs(String chunkId) {
        try {
            esIndexService.deleteById(chunkId);
        } catch (Exception e) {
            log.warn("删除 ES 分块失败 chunk={}: {}", chunkId, e.getMessage());
        }
    }

    private float estimateQuality(String text) {
        if (text == null) return 0.5f;
        int len = text.length();
        if (len >= 120 && len <= 1200) return 1.0f;
        if (len >= 60) return 0.8f;
        return Math.max(0.2f, len / 200.0f);
    }
}
