package com.activityual.analytics.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "log_facts",
       indexes = { @Index(name = "idx_fact_user_act_date", columnList = "userId,activityId,occurredOn") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogFact {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private UUID eventId;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID activityId;
    @Column(nullable = false) private String activityTitle;
    @Column(nullable = false) private String activityCategory;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private LocalDate occurredOn;
    @Column(nullable = false) private Instant occurredAt;
}

