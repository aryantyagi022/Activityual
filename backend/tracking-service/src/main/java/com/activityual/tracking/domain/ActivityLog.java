package com.activityual.tracking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_logs",
       indexes = { @Index(name = "idx_log_user_time", columnList = "userId,occurredAt") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityLog {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID activityId;
    @Column(nullable = false) private String activityTitle;
    @Column(nullable = false) private String activityCategory;

    @Column(nullable = false) private String status;
    @Column(nullable = false) private Instant occurredAt;
    @Column(nullable = false) private Instant createdAt;
}

