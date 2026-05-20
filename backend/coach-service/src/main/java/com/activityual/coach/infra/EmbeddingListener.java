package com.activityual.coach.infra;

import com.activityual.coach.rag.ChromaClient;
import com.activityual.common.events.ActivityLoggedEvent;
import com.activityual.common.events.RabbitTopology;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingListener {

    private final ChromaClient chroma;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitTopology.QUEUE_COACH)
    public void onEvent(String payload) throws Exception {
        ActivityLoggedEvent e = mapper.readValue(payload, ActivityLoggedEvent.class);
        String doc = "On %s the user %s the activity '%s' (category %s)."
                .formatted(DateTimeFormatter.ISO_INSTANT.format(e.getOccurredAt()),
                           e.getStatus(), e.getActivityTitle(), e.getActivityCategory());
        chroma.upsert(e.getEventId().toString(), doc, Map.of(
                "user_id", e.getUserId().toString(),
                "activity_id", e.getActivityId().toString(),
                "category", e.getActivityCategory(),
                "status", e.getStatus()
        ));
        log.debug("Embedded event {}", e.getEventId());
    }
}

