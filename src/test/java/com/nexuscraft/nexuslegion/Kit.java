package com.nexuscraft.nexuslegion;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared test doubles: a world with positions, players, and mobs. */
final class Kit {

    static int checks;
    static int failures;

    static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static void finish() {
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.exit(1);
        }
    }

    static final class TestPlugin extends JavaPlugin {
    }

    static final World WORLD = new World("world");

    static final class TestPlayer extends Player {
        final String name;
        final List<String> messages = new ArrayList<>();
        final Set<String> perms = new HashSet<>();
        Location loc;
        GameMode mode = GameMode.SURVIVAL;
        boolean invulnerable;
        boolean sneak;
        boolean valid = true;

        @Override
        public boolean isValid() {
            return valid;
        }

        TestPlayer(String name, double x, double z) {
            this.name = name;
            this.loc = new Location(WORLD, x, 64, z);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Location getLocation() {
            return loc;
        }

        @Override
        public boolean teleport(Location l) {
            loc = l;
            return true;
        }

        @Override
        public void sendMessage(String m) {
            messages.add(m);
        }

        @Override
        public boolean hasPermission(String node) {
            return perms.contains(node);
        }

        @Override
        public GameMode getGameMode() {
            return mode;
        }

        @Override
        public boolean isInvulnerable() {
            return invulnerable;
        }

        @Override
        public boolean isSneaking() {
            return sneak;
        }

        String lastMessage() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
        }

        void give(org.bukkit.Material m, int n) {
            getInventory().addItem(new ItemStack(m, n));
        }
    }

    static class TestMob extends Mob {
        final EntityType type;
        Location loc;
        boolean valid = true;

        TestMob(EntityType type, double x, double z) {
            this.type = type;
            this.loc = new Location(WORLD, x, 64, z);
        }

        @Override
        public EntityType getType() {
            return type;
        }

        @Override
        public Location getLocation() {
            return loc;
        }

        @Override
        public boolean teleport(Location l) {
            loc = l;
            return true;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public boolean isOnGround() {
            return true;
        }
    }

    static final class TestTamed extends TestMob implements Tameable {
        boolean tamed = true;

        TestTamed(EntityType type, double x, double z) {
            super(type, x, z);
        }

        @Override
        public boolean isTamed() {
            return tamed;
        }

        @Override
        public void setTamed(boolean tame) {
            tamed = tame;
        }

        @Override
        public AnimalTamer getOwner() {
            return null;
        }

        @Override
        public void setOwner(AnimalTamer tamer) {
        }
    }

    static final class TestMonster extends Monster {
        Location loc;

        TestMonster(double x, double z) {
            this.loc = new Location(WORLD, x, 64, z);
        }

        @Override
        public Location getLocation() {
            return loc;
        }
    }

    static <T extends org.bukkit.entity.Entity> T register(T e) {
        Bukkit.ENTITIES.put(e.getUniqueId(), e);
        return e;
    }

    static TestPlayer online(TestPlayer p) {
        Bukkit.PLAYERS.put(p.getUniqueId(), p);
        Bukkit.ENTITIES.put(p.getUniqueId(), p);
        return p;
    }

    static void reset() {
        Bukkit.PLAYERS.clear();
        Bukkit.ENTITIES.clear();
    }

    /** A map-backed ConfigurationSection so config parsing can be tested with real values. */
    static final class MapSection implements ConfigurationSection {
        final Map<String, Object> values = new HashMap<>();

        MapSection put(String path, Object v) {
            values.put(path, v);
            return this;
        }

        @Override public boolean getBoolean(String path) { return getBoolean(path, false); }
        @Override public boolean getBoolean(String path, boolean def) { return values.get(path) instanceof Boolean b ? b : def; }
        @Override public int getInt(String path) { return getInt(path, 0); }
        @Override public int getInt(String path, int def) { return values.get(path) instanceof Number n ? n.intValue() : def; }
        @Override public long getLong(String path) { return getLong(path, 0L); }
        @Override public long getLong(String path, long def) { return values.get(path) instanceof Number n ? n.longValue() : def; }
        @Override public double getDouble(String path) { return getDouble(path, 0.0); }
        @Override public double getDouble(String path, double def) { return values.get(path) instanceof Number n ? n.doubleValue() : def; }
        @Override public String getString(String path) { return getString(path, null); }
        @Override public String getString(String path, String def) { return values.get(path) instanceof String s ? s : def; }
        @Override @SuppressWarnings("unchecked") public List<String> getStringList(String path) {
            return values.get(path) instanceof List<?> l ? (List<String>) l : new ArrayList<>();
        }
        @Override @SuppressWarnings("unchecked") public List<Integer> getIntegerList(String path) {
            return values.get(path) instanceof List<?> l ? (List<Integer>) l : new ArrayList<>();
        }
        @Override public List<Double> getDoubleList(String path) { return new ArrayList<>(); }
        @Override public List<?> getList(String path) { return new ArrayList<>(); }
        @Override public List<Map<?, ?>> getMapList(String path) { return new ArrayList<>(); }
        @Override public ItemStack getItemStack(String path) { return null; }
        @Override public ItemStack getItemStack(String path, ItemStack def) { return def; }
        @Override public ConfigurationSection getConfigurationSection(String path) {
            MapSection sub = new MapSection();
            String prefix = path + ".";
            boolean any = false;
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    sub.values.put(e.getKey().substring(prefix.length()), e.getValue());
                    any = true;
                }
            }
            return any ? sub : null;
        }
        @Override public ConfigurationSection createSection(String path) { return new MapSection(); }
        @Override public void set(String path, Object value) { values.put(path, value); }
        @Override public Set<String> getKeys(boolean deep) {
            Set<String> keys = new LinkedHashSet<>();
            for (String k : values.keySet()) {
                keys.add(deep || !k.contains(".") ? k : k.substring(0, k.indexOf('.')));
            }
            return keys;
        }
        @Override public boolean isSet(String path) { return values.containsKey(path); }
        @Override public boolean contains(String path) { return values.containsKey(path); }
    }
}
