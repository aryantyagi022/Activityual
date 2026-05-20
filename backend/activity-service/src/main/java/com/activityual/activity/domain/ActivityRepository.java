package com.activityual.activity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Activity> findByUserIdAndCategory(UUID userId, String category);
}

