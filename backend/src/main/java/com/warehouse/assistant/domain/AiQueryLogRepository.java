package com.warehouse.assistant.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiQueryLogRepository extends JpaRepository<AiQueryLog, Long> {

    List<AiQueryLog> findTop15ByUserIdOrderByCreatedAtDesc(Long userId);

    Page<AiQueryLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
