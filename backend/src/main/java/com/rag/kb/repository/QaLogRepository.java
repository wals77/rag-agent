package com.rag.kb.repository;

import com.rag.kb.entity.QaLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaLogRepository extends JpaRepository<QaLog, Integer> {
}
