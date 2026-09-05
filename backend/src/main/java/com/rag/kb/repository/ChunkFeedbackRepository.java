package com.rag.kb.repository;

import com.rag.kb.entity.ChunkFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChunkFeedbackRepository extends JpaRepository<ChunkFeedback, Integer> {

    List<ChunkFeedback> findByChunkIdOrderByCreatedAtDesc(String chunkId);

    Page<ChunkFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
