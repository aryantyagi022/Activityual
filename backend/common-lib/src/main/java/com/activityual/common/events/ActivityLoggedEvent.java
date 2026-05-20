package com.activityual.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLoggedEvent {
    private UUID eventId;
    private UUID userId;
    private UUID activityId;
    private String activityTitle;
    private String activityCategory;

    private String status;
    private Instant occurredAt;
}

