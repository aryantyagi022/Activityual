package com.activityual.reco.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "log_facts",
       indexes = { @Index(name = "idx_rf_user_act", columnList = "userId,activityId") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogFact {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, unique = true) private UUID eventId;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID activityId;
    @Column(nullable = false) private String activityTitle;
    @Column(nullable = false) private String activityCategory;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private Instant occurredAt;
    @Column(nullable = false) private int hourOfDay;
    @Column(nullable = false) private int dayOfWeek;
}

