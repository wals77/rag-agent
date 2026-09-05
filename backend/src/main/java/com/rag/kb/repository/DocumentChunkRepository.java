package com.rag.kb.repository;

import com.rag.kb.entity.DocumentChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {

    long countByDocIdAndStatus(String docId, String status);

    List<DocumentChunk> findByDocId(String docId);

    List<DocumentChunk> findByDocIdAndStatus(String docId, String status);

    List<DocumentChunk> findByDocIdAndStatusIn(String docId, Collection<String> statuses);

    Page<DocumentChunk> findByDocIdAndStatus(String docId, String status, Pageable pageable);

    Page<DocumentChunk> findByDocId(String docId, Pageable pageable);

    Page<DocumentChunk> findByStatus(String status, Pageable pageable);

    List<DocumentChunk> findByParentChunkId(String parentChunkId);

    List<DocumentChunk> findByMergedIntoChunkId(String mergedIntoChunkId);

    List<DocumentChunk> findTop20ByChunkIdIn(Collection<String> chunkIds);

    List<DocumentChunk> findByChunkIdIn(Collection<String> chunkIds);

    List<DocumentChunk> findByChunkIdInAndStatus(Collection<String> chunkIds, String status);

    List<DocumentChunk> findByEditedVersionOf(String editedVersionOf);
}
