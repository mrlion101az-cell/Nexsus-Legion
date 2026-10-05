package com.nexuscraft.nexuslegion;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nexuscraft.nexuslegion.Kit.expect;

/** Pure-logic checks: who can be attacked, protected zones, config clamping, the army roster. */
public final class RulesTest {

    static TargetFacts facts(boolean owner, boolean ownSoldier, boolean trustedPlayer, boolean trustedSoldier,
                             boolean player, boolean hostile, boolean soldier, boolean creative, boolean vanished,
                             boolean invulnerable, boolean zone) {
        return new TargetFacts(owner, ownSoldier, trustedPlayer, trustedSoldier, player, hostile, soldier,
                creative, vanished, invulnerable, zone);
    }

    static TargetFacts plainPlayer() {
        return facts(false, false, false, false, true, false, false, false, false, false, false);
    }

    public static void main(String[] args) {
        LegionConfig cfg = new LegionConfig();

        // ---------------------------------------------------------------- SoldierRules
        expect(SoldierRules.check(plainPlayer(), cfg) == Verdict.OK, "an ordinary survival player can be attacked");
        expect(SoldierRules.check(facts(true, false, false, false, true, false, false, false, false, false, false), cfg) == Verdict.SELF,
                "the owner is never a target");
        expect(SoldierRules.check(facts(false, true, false, false, false, false, true, false, false, false, false), cfg) == Verdict.SELF,
                "the owner's own soldier is never a target");
        expect(SoldierRules.check(facts(false, false, true, false, true, false, false, false, false, false, false), cfg) == Verdict.TRUSTED,
                "a trusted player is never a target");
        expect(SoldierRules.check(facts(false, false, false, true, false, false, true, false, false, false, false), cfg) == Verdict.TRUSTED,
                "a trusted player's soldier is never a target");
        expect(SoldierRules.check(facts(false, false, false, false, true, false, false, true, false, false, false), cfg) == Verdict.GAMEMODE,
                "creative/spectator players are safe");
        expect(SoldierRules.check(facts(false, false, false, false, true, false, false, false, true, false, false), cfg) == Verdict.VANISHED,
                "vanished staff are safe");
        expect(SoldierRules.check(facts(false, false, false, false, true, false, false, false, false, true, false), cfg) == Verdict.INVULNERABLE,
                "invulnerable players are safe");
        expect(SoldierRules.check(facts(false, false, false, false, true, false, false, false, false, false, true), cfg) == Verdict.PROTECTED,
                "a player in a protected zone is safe");
        expect(SoldierRules.check(facts(false, false, false, false, false, true, false, false, false, false, false), cfg) == Verdict.OK,
                "a hostile monster can be attacked");
        expect(SoldierRules.check(facts(false, false, false, false, false, false, true, false, false, false, false), cfg) == Verdict.OK,
                "an enemy soldier can be attacked");
        expect(SoldierRules.check(facts(false, false, false, false, false, false, false, false, false, false, false), cfg) == Verdict.KIND,
                "a villager/animal (not player, hostile or soldier) cannot be attacked");
        expect(SoldierRules.check(facts(true, true, true, true, true, true, true, true, true, true, true), cfg) == Verdict.SELF,
                "SELF outranks every other rule");
        cfg.canTargetPlayers = false;
        expect(SoldierRules.check(plainPlayer(), cfg) == Verdict.KIND, "players can be switched off in config");
        cfg.canTargetPlayers = true;
        cfg.canTargetSoldiers = false;
        expect(SoldierRules.check(facts(false, false, false, false, false, false, true, false, false, false, false), cfg) == Verdict.KIND,
                "armies fighting armies can be switched off");
        cfg.canTargetSoldiers = true;

        // ---------------------------------------------------------------- ProtectedZones
        expect(ProtectedZones.isProtected(cfg, "world", 10, 10, 0, 0), "inside the spawn radius is protected");
        expect(ProtectedZones.isProtected(cfg, "world", 64, 0, 0, 0), "exactly on the radius is protected");
        expect(!ProtectedZones.isProtected(cfg, "world", 65, 0, 0, 0), "just outside the radius is not");
        expect(!ProtectedZones.isProtected(cfg, "world_nether", 0, 0, 0, 0), "an unlisted world has no spawn protection");
        expect(ProtectedZones.isProtected(cfg, "WORLD", 0, 0, 0, 0), "world names match case-insensitively");
        cfg.spawnProtectRadius = 0;
        expect(!ProtectedZones.isProtected(cfg, "world", 0, 0, 0, 0), "radius 0 switches spawn protection off");
        cfg.protectedWorlds.add("lobby");
        expect(ProtectedZones.isProtected(cfg, "lobby", 5000, 5000, 0, 0), "a protected world is protected everywhere");

        // ---------------------------------------------------------------- Stance
        expect(Stance.parse("HOLD", null) == Stance.HOLD && Stance.parse("hold", null) == Stance.HOLD, "stance parses");
        expect(Stance.parse("nonsense", Stance.FOLLOW) == Stance.FOLLOW && Stance.parse(null, null) == null, "bad stance falls back");
        expect(Stance.FOLLOW.toggled() == Stance.HOLD && Stance.HOLD.toggled() == Stance.FOLLOW, "stance toggles");

        // ---------------------------------------------------------------- config defaults + clamping
        LegionConfig d = new LegionConfig();
        d.load(null);
        expect(d.types.size() == 10 && d.type("rabbit") != null && d.type("RABBIT").displayName().equals("Bunny"),
                "ten built-in soldier types, looked up case-insensitively");
        expect(d.type("ZOMBIE") == null && d.type(null) == null, "a non-listed mob is not enlistable");
        expect(d.capFor(0) == 20 && d.capFor(40) == 40 && d.capFor(5) == 20, "cap is the base, or a higher permission tier");

        Kit.MapSection c = new Kit.MapSection()
                .put("army.max-soldiers", 9999).put("combat.tick-interval", 0).put("combat.reach", 99.0)
                .put("combat.attack-cooldown-ticks", -5).put("combat.damage-multiplier", -3.0)
                .put("targets.players", false).put("targets.spawn-protection.radius", -10)
                .put("recruit.cost-item", "iron_nugget").put("army.cap-tiers", List.of(0, 5000, 25))
                .put("soldier-types.rabbit.damage", 1000.0).put("soldier-types.rabbit.scale", 0.0)
                .put("soldier-types.rabbit.cost", 5000).put("soldier-types.rabbit.name", "Hopper")
                .put("soldier-types.llama.damage", 4.0);
        LegionConfig l = new LegionConfig();
        l.load(c);
        expect(l.baseCap == 500, "army size is clamped to 500");
        expect(l.tickInterval == 1, "tick interval cannot be zero");
        expect(l.reach == 4.0, "reach is clamped");
        expect(l.attackCooldownTicks == 4, "cooldown cannot be negative");
        expect(l.damageMultiplier == 0.0, "negative damage multiplier is clamped to 0");
        expect(!l.canTargetPlayers, "boolean settings are read");
        expect(l.spawnProtectRadius == 0, "negative spawn radius is clamped");
        expect(l.costItem.equals("IRON_NUGGET"), "cost item is upper-cased");
        expect(l.capTiers.equals(List.of(1, 500, 25)), "cap tiers are clamped: " + l.capTiers);
        expect(l.types.size() == 2 && l.type("RABBIT").damage() == 40.0 && l.type("RABBIT").scale() == 0.0625
                && l.type("RABBIT").cost() == 64 && l.type("RABBIT").displayName().equals("Hopper"),
                "soldier-types overrides are read and clamped; unlisted defaults are dropped");
        expect(l.type("LLAMA") != null && l.type("LLAMA").displayName().equals("Llama") && l.type("LLAMA").health() == 8.0,
                "a brand-new type gets sane defaults for the fields it did not set");

        // ---------------------------------------------------------------- ArmyRegistry
        ArmyRegistry r = new ArmyRegistry();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        expect(r.add(owner, s1) && r.add(owner, s2) && r.count(owner) == 2 && r.total() == 2, "soldiers are counted");
        expect(!r.add(owner, s1), "adding twice is not a second soldier");
        expect(r.ownerOf(s1).equals(owner), "owner lookup by soldier");
        r.add(other, s1);
        expect(r.count(owner) == 1 && r.count(other) == 1 && r.ownerOf(s1).equals(other), "re-assigning moves a soldier between owners");
        expect(r.remove(s1).equals(other) && r.count(other) == 0 && r.remove(s1) == null, "removal reports the former owner once");
        expect(r.trust(owner, other) && !r.trust(owner, other) && r.isTrusted(owner, other) && !r.isTrusted(other, owner),
                "trust is one-directional");
        expect(r.untrust(owner, other) && !r.isTrusted(owner, other) && !r.untrust(owner, other), "untrust");
        r.consumeDirty();
        r.setStance(owner, Stance.HOLD);
        expect(r.consumeDirty() && !r.consumeDirty(), "dirty flag is raised by a change and consumed once");
        r.setStance(owner, Stance.HOLD);
        expect(!r.consumeDirty(), "setting the same stance again is not a change");

        // disband
        r.add(owner, UUID.randomUUID());
        r.add(owner, UUID.randomUUID());
        int epochBefore = r.army(owner).epoch;
        List<UUID> gone = r.disband(owner);
        expect(gone.size() == 3 && r.count(owner) == 0 && r.total() == 0 && r.army(owner).epoch == epochBefore + 1,
                "disband forgets everyone and bumps the epoch: " + gone.size());

        // save / load round trip
        ArmyRegistry a = new ArmyRegistry();
        UUID o2 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        UUID x1 = UUID.randomUUID();
        UUID x2 = UUID.randomUUID();
        a.add(o2, x1);
        a.add(o2, x2);
        a.trust(o2, t2);
        a.setStance(o2, Stance.HOLD);
        a.disband(UUID.randomUUID());
        a.army(o2).epoch = 3;
        Map<String, Map<String, Object>> snap = a.snapshot();
        ArmyRegistry b = new ArmyRegistry();
        b.restore(snap);
        expect(b.count(o2) == 2 && b.ownerOf(x1).equals(o2) && b.ownerOf(x2).equals(o2), "soldiers survive a save/load");
        expect(b.isTrusted(o2, t2) && b.army(o2).stance == Stance.HOLD && b.army(o2).epoch == 3, "trust, stance and epoch survive");
        expect(!b.consumeDirty(), "a fresh load is not dirty");
        Map<String, Map<String, Object>> junk = new java.util.HashMap<>();
        junk.put("not-a-uuid", Map.of("soldiers", List.of("also-bad")));
        junk.put(o2.toString(), Map.of("soldiers", List.of("bad", x1.toString()), "stance", "???"));
        ArmyRegistry c2 = new ArmyRegistry();
        c2.restore(junk);
        expect(c2.count(o2) == 1 && c2.army(o2).stance == Stance.FOLLOW, "hand-edited junk is skipped, not fatal");
        expect(new ArmyRegistry().snapshot().isEmpty(), "an empty registry saves nothing");

        Kit.finish();
    }
}
