package com.nexuscraft.nexuslegion;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One player's army: which soldiers are theirs, who they trust, how the army behaves. */
final class Army {
    final Set<UUID> soldiers = new LinkedHashSet<>();
    final Set<UUID> trusted = new LinkedHashSet<>();
    Stance stance = Stance.FOLLOW;
    /** Bumped by "disband". A soldier recruited under an older epoch is stale and gets released
     *  the next time it loads, which makes disbanding work even for soldiers in unloaded chunks. */
    int epoch;
}
