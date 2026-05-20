package com.activityual.common.security;

import java.util.Optional;
import java.util.UUID;

public final class CurrentUser {
    public record User(UUID id, String email) {}

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    public static void set(User u) { HOLDER.set(u); }
    public static void clear() { HOLDER.remove(); }
    public static Optional<User> get() { return Optional.ofNullable(HOLDER.get()); }
    public static UUID requireId() {
        return get().map(User::id).orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
    private CurrentUser() {}
}

