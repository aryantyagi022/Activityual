package com.activityual.tracking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = { @Index(name = "idx_outbox_unsent", columnList = "sent") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private String routingKey;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Column(nullable = false) private boolean sent;
    @Column(nullable = false) private Instant createdAt;
    private Instant sentAt;
}

