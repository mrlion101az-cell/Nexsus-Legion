package com.nexuscraft.nexuslegion;

/** What a soldier does when it has nothing to attack. */
enum Stance {
    /** Stay close to the owner (the default). */
    FOLLOW,
    /** Stay where it is until given a target. */
    HOLD;

    static Stance parse(String raw, Stance fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    Stance toggled() {
        return this == FOLLOW ? HOLD : FOLLOW;
    }
}
