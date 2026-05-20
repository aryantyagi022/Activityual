package com.activityual.analytics.api;

import com.activityual.analytics.domain.LogFact;
import com.activityual.analytics.domain.LogFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final LogFactRepository repo;

    public record Summary(long total, long done, long missed, long completed,
                          double consistencyPct,
                          List<ActivityStat> mostConsistent, List<ActivityStat> mostMissed,
                          Map<UUID, Integer> streaks) {}
    public record ActivityStat(UUID activityId, String title, long done, long missed, double rate) {}

    @GetMapping("/{userId}")
    public Summary summary(@PathVariable UUID userId) {
        List<LogFact> all = repo.findByUserIdOrderByOccurredAtDesc(userId);
        long done = all.stream().filter(f -> "done".equalsIgnoreCase(f.getStatus())).count();
        long completed = all.stream().filter(f -> "completed".equalsIgnoreCase(f.getStatus())).count();
        long missed = all.stream().filter(f -> "missed".equalsIgnoreCase(f.getStatus())).count();
        long total = all.size();
        double pct = total == 0 ? 0 : (done + completed) * 100.0 / total;

        Map<UUID, List<LogFact>> byAct = all.stream().collect(Collectors.groupingBy(LogFact::getActivityId));
        List<ActivityStat> stats = byAct.entrySet().stream().map(e -> {
            long d = e.getValue().stream().filter(f -> !"missed".equalsIgnoreCase(f.getStatus())).count();
            long m = e.getValue().stream().filter(f -> "missed".equalsIgnoreCase(f.getStatus())).count();
            double r = (d + m) == 0 ? 0 : d * 100.0 / (d + m);
            return new ActivityStat(e.getKey(), e.getValue().get(0).getActivityTitle(), d, m, r);
        }).toList();

        var mostConsistent = stats.stream().sorted(Comparator.comparingDouble(ActivityStat::rate).reversed()).limit(3).toList();
        var mostMissed     = stats.stream().sorted(Comparator.comparingLong(ActivityStat::missed).reversed()).limit(3).toList();

        return new Summary(total, done, missed, completed, pct, mostConsistent, mostMissed, streaks(byAct));
    }

    @GetMapping("/{userId}/streaks")
    public Map<UUID, Integer> streaks(@PathVariable UUID userId) {
        return streaks(repo.findByUserIdOrderByOccurredAtDesc(userId).stream()
                .collect(Collectors.groupingBy(LogFact::getActivityId)));
    }

    private Map<UUID, Integer> streaks(Map<UUID, List<LogFact>> byAct) {
        Map<UUID, Integer> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        byAct.forEach((id, facts) -> {

            Set<LocalDate> doneDates = facts.stream()
                    .filter(f -> !"missed".equalsIgnoreCase(f.getStatus()))
                    .map(LogFact::getOccurredOn).collect(Collectors.toSet());
            int streak = 0;
            LocalDate d = today;
            while (doneDates.contains(d)) { streak++; d = d.minus(1, ChronoUnit.DAYS); }
            result.put(id, streak);
        });
        return result;
    }
}

