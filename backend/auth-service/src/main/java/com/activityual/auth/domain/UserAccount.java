package com.activityual.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAccount {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private String email;
    @Column(nullable = false) private String passwordHash;
    @Column(nullable = false) private String displayName;
    @Column(nullable = false) private Instant createdAt;
}

