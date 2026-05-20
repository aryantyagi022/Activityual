package com.activityual.tracking.api;

import com.activityual.common.events.ActivityLoggedEvent;
import com.activityual.common.events.RabbitTopology;
import com.activityual.common.security.CurrentUser;
import com.activityual.tracking.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final ActivityLogRepository logs;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;

    public record LogReq(@NotNull UUID activityId, @NotBlank String activityTitle,
                         @NotBlank String activityCategory,
                         @NotBlank String status, Instant occurredAt) {}

    @PostMapping
    @Transactional
    public ResponseEntity<ActivityLog> log(@Valid @RequestBody LogReq r) throws JsonProcessingException {
        UUID uid = CurrentUser.requireId();
        Instant when = r.occurredAt() == null ? Instant.now() : r.occurredAt();
        ActivityLog saved = logs.save(ActivityLog.builder()
                .userId(uid).activityId(r.activityId())
                .activityTitle(r.activityTitle()).activityCategory(r.activityCategory())
                .status(r.status()).occurredAt(when).createdAt(Instant.now())
                .build());

        ActivityLoggedEvent evt = ActivityLoggedEvent.builder()
                .eventId(UUID.randomUUID()).userId(uid).activityId(r.activityId())
                .activityTitle(r.activityTitle()).activityCategory(r.activityCategory())
                .status(r.status()).occurredAt(when).build();

        outbox.save(OutboxEvent.builder()
                .routingKey(RabbitTopology.ROUTING_KEY_LOGGED)
                .payload(mapper.writeValueAsString(evt))
                .sent(false).createdAt(Instant.now()).build());

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/due-today")
    public List<ActivityLog> dueToday() {
        UUID uid = CurrentUser.requireId();
        ZoneId z = ZoneOffset.UTC;
        Instant from = LocalDate.now(z).atStartOfDay(z).toInstant();
        Instant to = from.plus(Duration.ofDays(1));
        return logs.findByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(uid, from, to);
    }

    @GetMapping("/due-week")
    public List<ActivityLog> dueWeek() {
        UUID uid = CurrentUser.requireId();
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(7));
        return logs.findByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(uid, from, to);
    }

    @GetMapping("/recent")
    public List<ActivityLog> recent() {
        return logs.findTop200ByUserIdOrderByOccurredAtDesc(CurrentUser.requireId());
    }
}

