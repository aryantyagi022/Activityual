package com.activityual.tracking.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    List<ActivityLog> findByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(UUID userId, Instant from, Instant to);
    List<ActivityLog> findTop200ByUserIdOrderByOccurredAtDesc(UUID userId);
}

