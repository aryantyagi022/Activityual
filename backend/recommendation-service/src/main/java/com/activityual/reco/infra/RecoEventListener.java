package com.activityual.reco.infra;

import com.activityual.common.events.ActivityLoggedEvent;
import com.activityual.common.events.RabbitTopology;
import com.activityual.reco.domain.LogFact;
import com.activityual.reco.domain.LogFactRepository;
import com.activityual.reco.engine.RecommendationEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class RecoEventListener {

    private final LogFactRepository facts;
    private final RecommendationEngine engine;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitTopology.QUEUE_RECO)
    @Transactional
    public void onEvent(String payload) throws Exception {
        ActivityLoggedEvent e = mapper.readValue(payload, ActivityLoggedEvent.class);
        if (facts.findByEventId(e.getEventId()).isPresent()) return;
        var ldt = e.getOccurredAt().atZone(ZoneOffset.UTC);
        facts.save(LogFact.builder()
                .eventId(e.getEventId()).userId(e.getUserId())
                .activityId(e.getActivityId()).activityTitle(e.getActivityTitle())
                .activityCategory(e.getActivityCategory()).status(e.getStatus())
                .occurredAt(e.getOccurredAt())
                .hourOfDay(ldt.getHour()).dayOfWeek(ldt.getDayOfWeek().getValue())
                .build());
        engine.recompute(e.getUserId(), e.getActivityId());
    }
}

