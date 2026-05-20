package com.activityual.reco.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LogFactRepository extends JpaRepository<LogFact, UUID> {
    Optional<LogFact> findByEventId(UUID eventId);
    List<LogFact> findByUserIdAndActivityId(UUID userId, UUID activityId);
    List<LogFact> findByUserId(UUID userId);
}

