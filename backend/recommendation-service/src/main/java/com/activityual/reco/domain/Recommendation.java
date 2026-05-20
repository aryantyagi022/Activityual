package com.activityual.reco.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommendations",
       indexes = { @Index(name = "idx_reco_user_act", columnList = "userId,activityId") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recommendation {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID activityId;
    @Column(nullable = false) private String activityTitle;

    @Column(nullable = false) private String kind;
    @Column(nullable = false, length = 1000) private String message;
    @Column(nullable = false) private double confidence;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private boolean accepted;
    @Column(nullable = false) private boolean dismissed;
}

