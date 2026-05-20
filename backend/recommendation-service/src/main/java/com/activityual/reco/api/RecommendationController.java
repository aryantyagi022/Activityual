package com.activityual.reco.api;

import com.activityual.common.security.CurrentUser;
import com.activityual.reco.domain.Recommendation;
import com.activityual.reco.domain.RecommendationRepository;
import com.activityual.reco.engine.RecommendationEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationRepository repo;
    private final RecommendationEngine engine;

    public record ComputeReq(@NotNull UUID userId, @NotNull UUID activityId) {}
    public record AcceptReq(@NotNull UUID recommendationId, boolean accepted) {}

    @PostMapping("/compute")
    public void compute(@Valid @RequestBody ComputeReq r) {
        engine.recompute(r.userId(), r.activityId());
    }

    @GetMapping("/{userId}")
    public List<Recommendation> list(@PathVariable UUID userId) {
        return repo.findByUserIdAndDismissedFalseAndAcceptedFalseOrderByConfidenceDesc(userId);
    }

    @PostMapping("/accept")
    public Recommendation accept(@Valid @RequestBody AcceptReq r) {
        Recommendation rec = repo.findById(r.recommendationId())
                .orElseThrow(() -> new IllegalArgumentException("not found"));
        if (!rec.getUserId().equals(CurrentUser.requireId())) throw new IllegalStateException("forbidden");
        rec.setAccepted(r.accepted());
        rec.setDismissed(!r.accepted());
        return repo.save(rec);
    }

    @GetMapping("/{userId}/heatmap/{activityId}")
    public Map<String, Map<Integer, Double>> heatmap(@PathVariable UUID userId, @PathVariable UUID activityId) {
        return engine.heatmap(userId, activityId);
    }
}

