package com.nexuscraft.nexuslegion;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Turning mobs into soldiers (and back), counting armies, and answering "may this be attacked". */
final class ArmyService {

    enum Recruit {
        OK, NOT_A_MOB, NOT_ENLISTABLE, NAMED, TAMED, ALREADY, CAP, GLOBAL_CAP, NO_FUNDS
    }

    private final Plugin plugin;
    private final LegionConfig cfg;
    private final ArmyRegistry registry;
    private final Keys keys;

    ArmyService(Plugin plugin, LegionConfig cfg, ArmyRegistry registry, Keys keys) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.registry = registry;
        this.keys = keys;
    }

    // ------------------------------------------------------------------ identity

    /** The owner recorded on this entity, or null if it is not a soldier. */
    UUID ownerOf(Entity e) {
        if (e == null) {
            return null;
        }
        UUID fromRegistry = registry.ownerOf(e.getUniqueId());
        if (fromRegistry != null) {
            return fromRegistry;
        }
        return tagOwner(e);
    }

    UUID tagOwner(Entity e) {
        String raw = e.getPersistentDataContainer().get(keys.soldierOwner, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    boolean isSoldier(Entity e) {
        return ownerOf(e) != null;
    }

    // ------------------------------------------------------------------ caps and costs

    int capFor(Player p) {
        int best = 0;
        for (int tier : cfg.capTiers) {
            if (tier > best && p.hasPermission("nexuslegion.cap." + tier)) {
                best = tier;
            }
        }
        return cfg.capFor(best);
    }

    private Material costMaterial() {
        try {
            return Material.valueOf(cfg.costItem);
        } catch (IllegalArgumentException e) {
            return Material.GOLD_NUGGET;
        }
    }

    int costOf(SoldierType t) {
        return cfg.costEnabled ? t.cost() : 0;
    }

    private int countItem(Player p, Material m) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && s.getType() == m && !s.hasItemMeta()) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private void takeItem(Player p, Material m, int amount) {
        int left = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != m || s.hasItemMeta()) {
                continue;
            }
            int take = Math.min(left, s.getAmount());
            left -= take;
            if (take >= s.getAmount()) {
                p.getInventory().setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
            }
        }
    }

    String costText(SoldierType t) {
        int c = costOf(t);
        return c <= 0 ? "free" : c + " " + LegionConfig.pretty(cfg.costItem).toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ recruiting

    Recruit recruit(Player owner, LivingEntity target) {
        if (!(target instanceof Mob mob) || target instanceof Player) {
            return Recruit.NOT_A_MOB;
        }
        SoldierType type = cfg.type(mob.getType().name());
        if (type == null) {
            return Recruit.NOT_ENLISTABLE;
        }
        if (isSoldier(mob)) {
            return Recruit.ALREADY;
        }
        if (mob instanceof Tameable t && t.isTamed()) {
            return Recruit.TAMED;
        }
        if (!cfg.allowNamed && mob.getCustomName() != null) {
            return Recruit.NAMED;
        }
        UUID id = owner.getUniqueId();
        if (registry.count(id) >= capFor(owner)) {
            return Recruit.CAP;
        }
        if (registry.total() >= cfg.globalCap) {
            return Recruit.GLOBAL_CAP;
        }
        int cost = costOf(type);
        Material m = costMaterial();
        if (cost > 0 && countItem(owner, m) < cost) {
            return Recruit.NO_FUNDS;
        }
        if (cost > 0) {
            takeItem(owner, m, cost);
        }
        enlist(owner, mob, type);
        return Recruit.OK;
    }

    void enlist(Player owner, Mob mob, SoldierType type) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        UUID ownerId = owner.getUniqueId();
        pdc.set(keys.soldierOwner, PersistentDataType.STRING, ownerId.toString());
        pdc.set(keys.soldierType, PersistentDataType.STRING, type.entityType());
        pdc.set(keys.soldierEpoch, PersistentDataType.INTEGER, registry.army(ownerId).epoch);

        AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
        if (scale != null) {
            pdc.set(keys.origScale, PersistentDataType.DOUBLE, scale.getBaseValue());
            scale.setBaseValue(type.scale());
        }
        AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            pdc.set(keys.origHealth, PersistentDataType.DOUBLE, hp.getBaseValue());
            hp.setBaseValue(type.health());
            mob.setHealth(type.health());
        }
        pdc.set(keys.origName, PersistentDataType.STRING, mob.getCustomName() == null ? "" : mob.getCustomName());
        mob.setCustomName(ColorText.translate(cfg.nameFormat
                .replace("%owner%", owner.getName()).replace("%type%", type.displayName())));
        mob.setCustomNameVisible(cfg.nameVisible);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);

        registry.add(ownerId, mob.getUniqueId());

        World w = mob.getWorld();
        Location l = mob.getLocation();
        if (w != null && l != null) {
            w.playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            w.spawnParticle(Particle.HAPPY_VILLAGER, l, 8, 0.3, 0.3, 0.3);
        }
    }

    /** Turns a soldier back into an ordinary mob and forgets it. */
    void release(Mob mob) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        Double os = pdc.get(keys.origScale, PersistentDataType.DOUBLE);
        AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
        if (scale != null && os != null) {
            scale.setBaseValue(os);
        }
        Double oh = pdc.get(keys.origHealth, PersistentDataType.DOUBLE);
        AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && oh != null) {
            hp.setBaseValue(oh);
            mob.setHealth(Math.min(mob.getHealth(), oh));
        }
        String name = pdc.get(keys.origName, PersistentDataType.STRING);
        mob.setCustomName(name == null || name.isEmpty() ? null : name);
        mob.setCustomNameVisible(false);
        mob.setAI(true);
        mob.getPathfinder().stopPathfinding();
        pdc.remove(keys.soldierOwner);
        pdc.remove(keys.soldierType);
        pdc.remove(keys.soldierEpoch);
        pdc.remove(keys.origScale);
        pdc.remove(keys.origHealth);
        pdc.remove(keys.origName);
        registry.remove(mob.getUniqueId());
    }

    /** Releases every loaded soldier of this owner and bumps the epoch for the unloaded ones. */
    int disband(UUID owner) {
        int released = 0;
        for (UUID id : registry.disband(owner)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Mob m) {
                release(m);
                released++;
            }
        }
        return released;
    }

    /** Adopts or releases a soldier that just loaded with the chunk it was in. */
    void onSoldierLoaded(Mob mob) {
        UUID owner = tagOwner(mob);
        if (owner == null) {
            return;
        }
        Integer epoch = mob.getPersistentDataContainer().get(keys.soldierEpoch, PersistentDataType.INTEGER);
        if (epoch != null && epoch < registry.army(owner).epoch) {
            release(mob);
            return;
        }
        if (registry.ownerOf(mob.getUniqueId()) == null) {
            registry.add(owner, mob.getUniqueId());
        }
    }

    List<Mob> loadedSoldiers(UUID owner) {
        List<Mob> out = new ArrayList<>();
        for (UUID id : new ArrayList<>(registry.army(owner).soldiers)) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Mob m && m.isValid()) {
                out.add(m);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ attack policy

    Verdict verdict(UUID owner, LivingEntity target) {
        UUID tid = target.getUniqueId();
        UUID soldierOwner = ownerOf(target);
        boolean isSoldier = soldierOwner != null;
        Player tp = target instanceof Player p ? p : null;
        boolean zone = false;
        Location l = target.getLocation();
        if (l != null && l.getWorld() != null) {
            Location spawn = l.getWorld().getSpawnLocation();
            zone = ProtectedZones.isProtected(cfg, l.getWorld().getName(), l.getX(), l.getZ(),
                    spawn == null ? 0 : spawn.getX(), spawn == null ? 0 : spawn.getZ());
        }
        TargetFacts facts = new TargetFacts(
                tid.equals(owner),
                isSoldier && owner.equals(soldierOwner),
                tp != null && registry.isTrusted(owner, tid),
                isSoldier && registry.isTrusted(owner, soldierOwner),
                tp != null,
                target instanceof Enemy && !isSoldier,
                isSoldier,
                tp != null && (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR),
                tp != null && tp.hasMetadata("vanished"),
                tp != null && tp.isInvulnerable(),
                zone);
        return SoldierRules.check(facts, cfg);
    }

}
