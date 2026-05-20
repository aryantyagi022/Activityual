package com.activityual.reco.engine;

import com.activityual.reco.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationEngine {

    private static final int MIN_SAMPLES = 4;

    private final LogFactRepository facts;
    private final RecommendationRepository recos;

    @Transactional
    public void recompute(UUID userId, UUID activityId) {
        List<LogFact> rows = facts.findByUserIdAndActivityId(userId, activityId);
        if (rows.size() < MIN_SAMPLES) return;
        recos.deleteOpenForActivity(userId, activityId);
        String title = rows.get(0).getActivityTitle();

        Map<String, double[]> tod = new HashMap<>();
        for (LogFact f : rows) {
            String b = bucket(f.getHourOfDay());
            double[] arr = tod.computeIfAbsent(b, k -> new double[2]);
            arr[1] += 1;
            if (!"missed".equalsIgnoreCase(f.getStatus())) arr[0] += 1;
        }
        tod.entrySet().stream()
                .filter(e -> e.getValue()[1] >= MIN_SAMPLES / 2.0)
                .max(Comparator.comparingDouble(e -> e.getValue()[0] / e.getValue()[1]))
                .ifPresent(best -> {
                    double rate = best.getValue()[0] / best.getValue()[1];
                    if (rate >= 0.6) save(userId, activityId, title, "time-of-day",
                            "You complete '%s' best in the %s (%.0f%% success). Try scheduling it then."
                                    .formatted(title, best.getKey(), rate * 100), rate);
                });

        double[] weekday = new double[2], weekend = new double[2];
        for (LogFact f : rows) {
            double[] arr = (f.getDayOfWeek() == 6 || f.getDayOfWeek() == 7) ? weekend : weekday;
            arr[1] += 1;
            if (!"missed".equalsIgnoreCase(f.getStatus())) arr[0] += 1;
        }
        if (weekday[1] >= 2 && weekend[1] >= 2) {
            double wd = weekday[0] / weekday[1], we = weekend[0] / weekend[1];
            if (Math.abs(wd - we) >= 0.25) {
                String when = we > wd ? "weekends" : "weekdays";
                double conf = Math.max(wd, we);
                save(userId, activityId, title, "day-of-week",
                        "Your '%s' completion is much higher on %s (%.0f%% vs %.0f%%)."
                                .formatted(title, when, Math.max(wd, we) * 100, Math.min(wd, we) * 100), conf);
            }
        }

        long missed = rows.stream().filter(f -> "missed".equalsIgnoreCase(f.getStatus())).count();
        double missRate = missed * 1.0 / rows.size();
        if (missRate > 0.4) {
            save(userId, activityId, title, "frequency",
                    "You miss '%s' %.0f%% of the time. Consider lowering the target frequency."
                            .formatted(title, missRate * 100), Math.min(1.0, missRate));
        }
        log.debug("Recomputed recommendations for user {} activity {}", userId, activityId);
    }

    private void save(UUID uid, UUID aid, String title, String kind, String msg, double conf) {
        recos.save(Recommendation.builder()
                .userId(uid).activityId(aid).activityTitle(title)
                .kind(kind).message(msg).confidence(conf)
                .createdAt(Instant.now()).accepted(false).dismissed(false)
                .build());
    }

    private static String bucket(int hour) {
        if (hour < 6)  return "night";
        if (hour < 12) return "morning";
        if (hour < 18) return "afternoon";
        return "evening";
    }

    public Map<String, Map<Integer, Double>> heatmap(UUID userId, UUID activityId) {
        List<LogFact> rows = facts.findByUserIdAndActivityId(userId, activityId);

        Map<String, Map<Integer, Double>> out = new HashMap<>();
        for (LogFact f : rows) {
            String key = "missed".equalsIgnoreCase(f.getStatus()) ? "missed" : "done";
            out.computeIfAbsent(key, k -> new HashMap<>())
                    .merge(f.getDayOfWeek(), 1.0, Double::sum);
        }
        return out;
    }
}

