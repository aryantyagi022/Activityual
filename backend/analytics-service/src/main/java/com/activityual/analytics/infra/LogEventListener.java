package com.activityual.analytics.infra;

import com.activityual.analytics.domain.LogFact;
import com.activityual.analytics.domain.LogFactRepository;
import com.activityual.common.events.ActivityLoggedEvent;
import com.activityual.common.events.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventListener {

    private final LogFactRepository repo;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitTopology.QUEUE_ANALYTICS)
    public void onEvent(String payload) throws Exception {
        ActivityLoggedEvent e = mapper.readValue(payload, ActivityLoggedEvent.class);
        if (repo.findByEventId(e.getEventId()).isPresent()) return;
        repo.save(LogFact.builder()
                .eventId(e.getEventId()).userId(e.getUserId())
                .activityId(e.getActivityId()).activityTitle(e.getActivityTitle())
                .activityCategory(e.getActivityCategory()).status(e.getStatus())
                .occurredAt(e.getOccurredAt())
                .occurredOn(e.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate())
                .build());
        log.debug("Recorded fact for event {}", e.getEventId());
    }
}

