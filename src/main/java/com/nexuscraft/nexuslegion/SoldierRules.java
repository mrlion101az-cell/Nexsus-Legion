package com.nexuscraft.nexuslegion;

/** The whole "who may be attacked" policy in one pure place. The first rule that applies wins. */
final class SoldierRules {

    private SoldierRules() {
    }

    static Verdict check(TargetFacts f, LegionConfig cfg) {
        if (f.isOwner() || f.isOwnSoldier()) {
            return Verdict.SELF;
        }
        if (f.isTrustedPlayer() || f.isTrustedSoldier()) {
            return Verdict.TRUSTED;
        }
        boolean kindOk = (f.isPlayer() && cfg.canTargetPlayers)
                || (f.isHostileMob() && cfg.canTargetHostileMobs)
                || (f.isSoldier() && cfg.canTargetSoldiers);
        if (!kindOk) {
            return Verdict.KIND;
        }
        if (f.vanished()) {
            return Verdict.VANISHED;
        }
        if (f.isPlayer() && f.creativeOrSpectator()) {
            return Verdict.GAMEMODE;
        }
        if (f.isPlayer() && f.invulnerable()) {
            return Verdict.INVULNERABLE;
        }
        if (f.inProtectedZone()) {
            return Verdict.PROTECTED;
        }
        return Verdict.OK;
    }
}
