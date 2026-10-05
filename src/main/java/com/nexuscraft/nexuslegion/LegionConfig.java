package com.nexuscraft.nexuslegion;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses config.yml. Every value is clamped on load so a typo can never produce a zero-length timer,
 * a negative damage number or an unbounded army. Anything missing falls back to a built-in default.
 */
final class LegionConfig {

    // -- recruiting --
    String costItem = "GOLD_NUGGET";
    boolean costEnabled = true;
    boolean allowNamed = false;
    String nameFormat = "&e%owner%'s %type%";
    boolean nameVisible = true;

    // -- army size --
    int baseCap = 20;
    List<Integer> capTiers = new ArrayList<>(List.of(30, 40, 60));
    int globalCap = 500;

    // -- combat --
    int tickInterval = 5;
    int attackCooldownTicks = 20;
    double reach = 1.7;
    double chaseRange = 40.0;
    double followDistance = 6.0;
    double teleportDistance = 40.0;
    int targetMemorySeconds = 15;
    boolean autoDefend = true;
    boolean protectFromOwner = true;
    boolean dropsOnDeath = false;
    boolean fightWhenOwnerOffline = false;
    double damageMultiplier = 1.0;

    // -- who can be attacked --
    boolean canTargetPlayers = true;
    boolean canTargetHostileMobs = true;
    boolean canTargetSoldiers = true;
    List<String> protectedWorlds = new ArrayList<>();
    List<String> spawnProtectWorlds = new ArrayList<>(List.of("world"));
    int spawnProtectRadius = 64;

    // -- items --
    int hornCooldownMillis = 500;
    boolean recipesEnabled = true;
    List<String> bannerShape = new ArrayList<>(List.of("GBG", " S ", " S "));
    Map<Character, String> bannerIngredients = new LinkedHashMap<>(Map.of('G', "GOLD_INGOT", 'B', "WHITE_BANNER", 'S', "STICK"));
    List<String> hornShape = new ArrayList<>(List.of("GIG", "GBG", " G "));
    Map<Character, String> hornIngredients = new LinkedHashMap<>(Map.of('G', "GOLD_INGOT", 'I', "IRON_INGOT", 'B', "BONE"));

    final Map<String, SoldierType> types = new LinkedHashMap<>();

    LegionConfig() {
        resetTypes();
    }

    private void resetTypes() {
        types.clear();
        for (SoldierType t : SoldierType.defaults()) {
            types.put(t.entityType(), t);
        }
    }

    void load(ConfigurationSection c) {
        resetTypes();
        if (c == null) {
            return;
        }
        costItem = c.getString("recruit.cost-item", costItem).toUpperCase(Locale.ROOT);
        costEnabled = c.getBoolean("recruit.cost-enabled", costEnabled);
        allowNamed = c.getBoolean("recruit.allow-named-mobs", allowNamed);
        nameFormat = c.getString("recruit.name-format", nameFormat);
        nameVisible = c.getBoolean("recruit.name-visible", nameVisible);

        baseCap = clamp(c.getInt("army.max-soldiers", baseCap), 1, 500);
        List<Integer> tiers = c.getIntegerList("army.cap-tiers");
        if (!tiers.isEmpty()) {
            capTiers = new ArrayList<>();
            for (int t : tiers) {
                capTiers.add(clamp(t, 1, 500));
            }
        }
        globalCap = clamp(c.getInt("army.global-max-soldiers", globalCap), 10, 5000);

        tickInterval = clamp(c.getInt("combat.tick-interval", tickInterval), 1, 40);
        attackCooldownTicks = clamp(c.getInt("combat.attack-cooldown-ticks", attackCooldownTicks), 4, 200);
        reach = clampD(c.getDouble("combat.reach", reach), 0.8, 4.0);
        chaseRange = clampD(c.getDouble("combat.chase-range", chaseRange), 8.0, 128.0);
        followDistance = clampD(c.getDouble("combat.follow-distance", followDistance), 2.0, 32.0);
        teleportDistance = clampD(c.getDouble("combat.teleport-distance", teleportDistance), 8.0, 256.0);
        targetMemorySeconds = clamp(c.getInt("combat.target-memory-seconds", targetMemorySeconds), 1, 600);
        autoDefend = c.getBoolean("combat.auto-defend", autoDefend);
        protectFromOwner = c.getBoolean("combat.protect-soldiers-from-owner", protectFromOwner);
        dropsOnDeath = c.getBoolean("combat.drops-on-death", dropsOnDeath);
        fightWhenOwnerOffline = c.getBoolean("combat.fight-when-owner-offline", fightWhenOwnerOffline);
        damageMultiplier = clampD(c.getDouble("combat.damage-multiplier", damageMultiplier), 0.0, 10.0);

        canTargetPlayers = c.getBoolean("targets.players", canTargetPlayers);
        canTargetHostileMobs = c.getBoolean("targets.hostile-mobs", canTargetHostileMobs);
        canTargetSoldiers = c.getBoolean("targets.other-soldiers", canTargetSoldiers);
        protectedWorlds = new ArrayList<>(c.getStringList("targets.protected-worlds"));
        List<String> spawnWorlds = c.getStringList("targets.spawn-protection.worlds");
        if (!spawnWorlds.isEmpty()) {
            spawnProtectWorlds = new ArrayList<>(spawnWorlds);
        }
        spawnProtectRadius = clamp(c.getInt("targets.spawn-protection.radius", spawnProtectRadius), 0, 2000);

        hornCooldownMillis = clamp(c.getInt("items.horn-cooldown-millis", hornCooldownMillis), 0, 10000);
        recipesEnabled = c.getBoolean("items.recipes-enabled", recipesEnabled);
        List<String> bs = c.getStringList("items.banner.shape");
        ConfigurationSection bi = c.getConfigurationSection("items.banner.ingredients");
        if (bs.size() == 3 && bi != null && !bi.getKeys(false).isEmpty()) {
            bannerShape = new ArrayList<>(bs);
            bannerIngredients = ingredients(bi);
        }
        List<String> hs = c.getStringList("items.horn.shape");
        ConfigurationSection hi = c.getConfigurationSection("items.horn.ingredients");
        if (hs.size() == 3 && hi != null && !hi.getKeys(false).isEmpty()) {
            hornShape = new ArrayList<>(hs);
            hornIngredients = ingredients(hi);
        }

        ConfigurationSection ts = c.getConfigurationSection("soldier-types");
        if (ts != null && !ts.getKeys(false).isEmpty()) {
            Map<String, SoldierType> loaded = new LinkedHashMap<>();
            for (String key : ts.getKeys(false)) {
                String id = key.toUpperCase(Locale.ROOT);
                SoldierType base = SoldierType.defaults().stream().filter(d -> d.entityType().equals(id)).findFirst()
                        .orElse(new SoldierType(id, pretty(id), 2.0, 1.3, 0.5, 8.0, 3));
                String p = "soldier-types." + key + ".";
                loaded.put(id, new SoldierType(id,
                        c.getString(p + "name", base.displayName()),
                        clampD(c.getDouble(p + "damage", base.damage()), 0.0, 40.0),
                        clampD(c.getDouble(p + "speed", base.speed()), 0.5, 3.0),
                        clampD(c.getDouble(p + "scale", base.scale()), 0.0625, 1.0),
                        clampD(c.getDouble(p + "health", base.health()), 1.0, 100.0),
                        clamp(c.getInt(p + "cost", base.cost()), 0, 64)));
            }
            types.clear();
            types.putAll(loaded);
        }
    }

    /** The soldier stats for an entity type name, or null if that mob cannot be enlisted. */
    SoldierType type(String entityTypeName) {
        return entityTypeName == null ? null : types.get(entityTypeName.toUpperCase(Locale.ROOT));
    }

    /** The army-size limit for a player holding the given highest {@code nexuslegion.cap.N} tier (0 = none). */
    int capFor(int bestTierPermission) {
        return Math.max(baseCap, bestTierPermission);
    }

    private static Map<Character, String> ingredients(ConfigurationSection s) {
        Map<Character, String> out = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) {
            if (k.length() == 1) {
                out.put(k.charAt(0), s.getString(k, "AIR").toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    static String pretty(String id) {
        String[] parts = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double clampD(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
