package com.nexuscraft.nexuslegion;

/** Everything {@link SoldierRules} needs to know about a potential target, already reduced to plain
 *  booleans so the rules themselves are pure and testable without a server. */
record TargetFacts(boolean isOwner, boolean isOwnSoldier, boolean isTrustedPlayer, boolean isTrustedSoldier,
                   boolean isPlayer, boolean isHostileMob, boolean isSoldier,
                   boolean creativeOrSpectator, boolean vanished, boolean invulnerable, boolean inProtectedZone) {
}
