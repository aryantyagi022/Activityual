package com.activityual.tracking.infra;

import com.activityual.common.events.RabbitTopology;
import com.activityual.tracking.domain.OutboxEvent;
import com.activityual.tracking.domain.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository repo;
    private final RabbitTemplate rabbit;

    @Scheduled(fixedDelayString = "${outbox.relay-ms:2000}")
    @Transactional
    public void drain() {
        var batch = repo.findTop100BySentFalseOrderByCreatedAtAsc();
        for (OutboxEvent e : batch) {
            try {
                rabbit.convertAndSend(RabbitTopology.EXCHANGE, e.getRoutingKey(), e.getPayload());
                e.setSent(true);
                e.setSentAt(Instant.now());
                repo.save(e);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}: {}", e.getId(), ex.getMessage());
            }
        }
    }
}

