package com.activityual.notif;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false, length = 1000) private String message;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private boolean readFlag;
}

