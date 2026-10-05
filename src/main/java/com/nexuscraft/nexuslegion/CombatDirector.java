package com.nexuscraft.nexuslegion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The army's brain: runs every few ticks, decides what each loaded soldier does (fight the army's
 * current target, follow its owner, or hold), and swings. Soldiers are ordinary passive mobs, so
 * the movement is Paper's own pathfinder and the "attack" is a plain damage call from the soldier --
 * after the attack has been checked against the rules and offered to other plugins as a cancellable
 * {@link SoldierAttackEvent}.
 */
final class CombatDirector implements Runnable {

    /** The army-wide target: set by the war horn (explicit) or by auto-defend. */
    private static final class State {
        UUID target;
        boolean explicit;
        long setAtTick;
    }

    private final Plugin plugin;
    private final LegionConfig cfg;
    private final ArmyRegistry registry;
    private final ArmyService service;
    private final Keys keys;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Long> nextAttack = new HashMap<>();
    private long tick;

    CombatDirector(Plugin plugin, LegionConfig cfg, ArmyRegistry registry, ArmyService service, Keys keys) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.registry = registry;
        this.service = service;
        this.keys = keys;
    }

    // ------------------------------------------------------------------ targeting API

    /** Points the whole army at a target. Returns the verdict; the target is only set when it is OK. */
    Verdict setTarget(UUID owner, LivingEntity target, boolean explicit) {
        Verdict v = service.verdict(owner, target);
        if (v != Verdict.OK) {
            return v;
        }
        State s = states.computeIfAbsent(owner, k -> new State());
        // A deliberate horn order is never overridden by an automatic reaction.
        if (s.explicit && !explicit && s.target != null && !s.target.equals(target.getUniqueId())
                && stillValid(s, owner)) {
            return Verdict.OK;
        }
        s.target = target.getUniqueId();
        s.explicit = explicit;
        s.setAtTick = tick;
        return Verdict.OK;
    }

    void clearTarget(UUID owner) {
        State s = states.get(owner);
        if (s != null) {
            s.target = null;
            s.explicit = false;
        }
    }

    UUID targetOf(UUID owner) {
        State s = states.get(owner);
        return s == null ? null : s.target;
    }

    /** Called when something dies or is removed: no army should keep chasing it. */
    void forget(UUID entity) {
        for (State s : states.values()) {
            if (entity.equals(s.target)) {
                s.target = null;
                s.explicit = false;
            }
        }
        nextAttack.remove(entity);
    }

    private boolean stillValid(State s, UUID owner) {
        Entity e = s.target == null ? null : Bukkit.getEntity(s.target);
        return e instanceof LivingEntity le && le.isValid();
    }

    // ------------------------------------------------------------------ the loop

    @Override
    public void run() {
        tick += cfg.tickInterval;
        for (UUID owner : registry.owners()) {
            if (registry.count(owner) == 0) {
                continue;
            }
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer == null || !ownerPlayer.isOnline()) {
                if (!cfg.fightWhenOwnerOffline) {
                    continue;
                }
            }
            LivingEntity target = resolveTarget(owner, ownerPlayer);
            Stance stance = registry.army(owner).stance;
            for (UUID id : new java.util.ArrayList<>(registry.army(owner).soldiers)) {
                Entity e = Bukkit.getEntity(id);
                if (e == null) {
                    continue; // in an unloaded chunk
                }
                if (!(e instanceof Mob mob) || !mob.isValid()) {
                    registry.remove(id);
                    continue;
                }
                drive(mob, owner, ownerPlayer, target, stance);
            }
        }
    }

    private LivingEntity resolveTarget(UUID owner, Player ownerPlayer) {
        State s = states.get(owner);
        if (s == null || s.target == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(s.target);
        boolean memoryExpired = !s.explicit && (tick - s.setAtTick) > cfg.targetMemorySeconds * 20L;
        if (!(e instanceof LivingEntity le) || !le.isValid() || memoryExpired) {
            clearTarget(owner);
            return null;
        }
        if (ownerPlayer != null && ownerPlayer.getLocation() != null && le.getLocation() != null
                && ownerPlayer.getLocation().getWorld() == le.getLocation().getWorld()
                && ownerPlayer.getLocation().distanceSquared(le.getLocation()) > cfg.chaseRange * cfg.chaseRange) {
            clearTarget(owner);
            tell(ownerPlayer, "§7Your army lost the target and stands down.");
            return null;
        }
        Verdict v = service.verdict(owner, le);
        if (v != Verdict.OK) {
            clearTarget(owner);
            tell(ownerPlayer, "§7Your army stands down. " + (v.reason == null ? "" : v.reason));
            return null;
        }
        return le;
    }

    private void drive(Mob mob, UUID owner, Player ownerPlayer, LivingEntity target, Stance stance) {
        String typeName = mob.getPersistentDataContainer().get(keys.soldierType, PersistentDataType.STRING);
        SoldierType type = cfg.type(typeName != null ? typeName : mob.getType().name());
        if (type == null) {
            return;
        }
        Location here = mob.getLocation();
        if (here == null) {
            return;
        }

        if (target != null) {
            ensureAi(mob, true);
            Location there = target.getLocation();
            if (there == null || there.getWorld() != here.getWorld()) {
                return;
            }
            if (here.distanceSquared(there) <= cfg.reach * cfg.reach) {
                attack(mob, owner, target, type);
            } else {
                mob.getPathfinder().moveTo(target, type.speed());
            }
            return;
        }

        if (stance == Stance.HOLD) {
            if (mob.isOnGround()) {
                ensureAi(mob, false);
            }
            return;
        }
        ensureAi(mob, true);
        if (ownerPlayer == null || ownerPlayer.getLocation() == null) {
            return;
        }
        Location home = ownerPlayer.getLocation();
        if (home.getWorld() != here.getWorld()
                || here.distanceSquared(home) > cfg.teleportDistance * cfg.teleportDistance) {
            mob.teleport(home);
        } else if (here.distanceSquared(home) > cfg.followDistance * cfg.followDistance) {
            mob.getPathfinder().moveTo(ownerPlayer, type.speed());
        }
    }

    private void attack(Mob mob, UUID owner, LivingEntity target, SoldierType type) {
        long due = nextAttack.getOrDefault(mob.getUniqueId(), 0L);
        if (tick < due) {
            return;
        }
        SoldierAttackEvent event = new SoldierAttackEvent(mob, owner, target, type.damage() * cfg.damageMultiplier);
        Bukkit.getPluginManager().callEvent(event);
        nextAttack.put(mob.getUniqueId(), tick + cfg.attackCooldownTicks);
        if (event.isCancelled() || event.getDamage() <= 0.0) {
            return;
        }
        target.damage(event.getDamage(), mob);
        World w = mob.getWorld();
        Location l = target.getLocation();
        if (w != null && l != null) {
            w.spawnParticle(Particle.CRIT, l, 4, 0.2, 0.3, 0.2);
            w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4f, 1.8f);
        }
    }

    private static void ensureAi(Mob mob, boolean wanted) {
        if (mob.hasAI() != wanted) {
            mob.setAI(wanted);
        }
    }

    private static void tell(Player p, String message) {
        if (p != null) {
            p.sendMessage(message);
        }
    }

}
