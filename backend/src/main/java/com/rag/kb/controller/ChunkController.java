package com.rag.kb.controller;

import com.rag.kb.chunk.ChunkManagementService;
import com.rag.kb.dto.ChunkDtos;
import com.rag.kb.dto.ChunkDto;
import com.rag.kb.dto.FeedbackDto;
import com.rag.kb.dto.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chunks")
public class ChunkController {

    private final ChunkManagementService chunkManagementService;

    public ChunkController(ChunkManagementService chunkManagementService) {
        this.chunkManagementService = chunkManagementService;
    }

    @GetMapping
    public PageResult<ChunkDto> list(@RequestParam(required = false) String docId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return chunkManagementService.listChunks(docId, status, page, size);
    }

    @PutMapping("/{chunkId}")
    public ChunkDtos.ChunkMutationResult edit(@PathVariable String chunkId,
                                              @Valid @RequestBody ChunkDtos.ChunkUpdateRequest req) {
        return chunkManagementService.edit(chunkId, req.chunkText());
    }

    @PostMapping("/merge")
    public ChunkDtos.ChunkMutationResult merge(@Valid @RequestBody ChunkDtos.MergeRequest req) {
        return chunkManagementService.merge(req.chunkIds(), req.mergedText());
    }

    @PostMapping("/split")
    public ChunkDtos.ChunkMutationResult split(@Valid @RequestBody ChunkDtos.SplitRequest req) {
        return chunkManagementService.split(req.chunkId(), req.pieces());
    }

    @PostMapping("/{chunkId}/feedback")
    public ChunkDtos.FeedbackResult feedback(@PathVariable String chunkId,
                                             @RequestBody ChunkDtos.FeedbackRequest req) {
        return chunkManagementService.submitFeedback(chunkId, req);
    }

    @GetMapping("/{chunkId}/feedback")
    public List<FeedbackDto> feedbackByChunk(@PathVariable String chunkId) {
        return chunkManagementService.listFeedbackByChunk(chunkId);
    }

    /** 全量反馈列表（管理后台） */
    @GetMapping("/feedbacks")
    public PageResult<FeedbackDto> feedbacks(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return chunkManagementService.listFeedback(page, size);
    }
}
