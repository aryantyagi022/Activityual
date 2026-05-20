package com.activityual.activity.api;

import com.activityual.activity.domain.Activity;
import com.activityual.activity.domain.ActivityRepository;
import com.activityual.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityRepository repo;

    public record ActivityReq(@NotBlank String title, @NotBlank String category,
                              @NotBlank String frequency, String notes) {}

    @GetMapping
    public List<Activity> list(@RequestParam(required = false) String category) {
        UUID uid = CurrentUser.requireId();
        return category == null
                ? repo.findByUserIdOrderByCreatedAtDesc(uid)
                : repo.findByUserIdAndCategory(uid, category);
    }

    @GetMapping("/{id}")
    public Activity get(@PathVariable UUID id) {
        Activity a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Not found"));
        if (!a.getUserId().equals(CurrentUser.requireId())) throw new IllegalStateException("Forbidden");
        return a;
    }

    @PostMapping
    public ResponseEntity<Activity> create(@Valid @RequestBody ActivityReq r) {
        Instant now = Instant.now();
        Activity a = repo.save(Activity.builder()
                .userId(CurrentUser.requireId())
                .title(r.title()).category(r.category())
                .frequency(r.frequency()).notes(r.notes())
                .createdAt(now).updatedAt(now).build());
        return ResponseEntity.ok(a);
    }

    @PutMapping("/{id}")
    public Activity update(@PathVariable UUID id, @Valid @RequestBody ActivityReq r) {
        Activity a = get(id);
        a.setTitle(r.title());
        a.setCategory(r.category());
        a.setFrequency(r.frequency());
        a.setNotes(r.notes());
        a.setUpdatedAt(Instant.now());
        return repo.save(a);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Activity a = get(id);
        repo.delete(a);
        return ResponseEntity.noContent().build();
    }
}

