package com.nexuscraft.nexuslegion;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everything the players actually do: use the banner, use the horn, fight, and lose soldiers. */
final class LegionListener implements Listener {

    private final LegionConfig cfg;
    private final ArmyRegistry registry;
    private final ArmyService service;
    private final CombatDirector director;
    private final LegionItems items;
    private final Map<UUID, Long> lastEntityCommand = new HashMap<>();
    private final Map<UUID, Long> lastHorn = new HashMap<>();

    LegionListener(LegionConfig cfg, ArmyRegistry registry, ArmyService service, CombatDirector director, LegionItems items) {
        this.cfg = cfg;
        this.registry = registry;
        this.service = service;
        this.director = director;
        this.items = items;
    }

    // ------------------------------------------------------------------ the banner

    @EventHandler(ignoreCancelled = true)
    public void onBannerPlace(BlockPlaceEvent event) {
        if (items.isBanner(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        Entity clicked = event.getRightClicked();

        if (items.isBanner(held)) {
            event.setCancelled(true);
            if (!(clicked instanceof LivingEntity living)) {
                return;
            }
            recruit(player, living);
        } else if (items.isHorn(held)) {
            if (!(clicked instanceof LivingEntity living)) {
                return;
            }
            lastEntityCommand.put(player.getUniqueId(), System.currentTimeMillis());
            if (!hornReady(player)) {
                return;
            }
            sendArmy(player, living);
        }
    }

    private void recruit(Player player, LivingEntity living) {
        ArmyService.Recruit result = service.recruit(player, living);
        SoldierType type = cfg.type(living.getType().name());
        switch (result) {
            case OK -> player.sendMessage("§a" + type.displayName() + " enlisted! §7Army: §f"
                    + registry.count(player.getUniqueId()) + "§7/§f" + service.capFor(player)
                    + "§7. Right-click things with your War Horn to give orders.");
            case NOT_A_MOB -> player.sendMessage("§cThat cannot join an army.");
            case NOT_ENLISTABLE -> player.sendMessage("§cThat kind of animal cannot be a soldier. Try a bunny, chicken, cat, fox, pig, sheep, cow, goat, frog or turtle.");
            case NAMED -> player.sendMessage("§cThat animal has a name tag, so it looks like someone's pet. Not enlisted.");
            case TAMED -> player.sendMessage("§cTamed pets cannot be enlisted.");
            case ALREADY -> player.sendMessage("§cThat animal is already in an army.");
            case CAP -> player.sendMessage("§cYour army is full (§f" + registry.count(player.getUniqueId()) + "§c/§f"
                    + service.capFor(player) + "§c). Disband some soldiers first.");
            case GLOBAL_CAP -> player.sendMessage("§cThe server has too many soldiers right now. Try again later.");
            case NO_FUNDS -> player.sendMessage("§cRecruiting a " + type.displayName() + " costs §f" + service.costText(type) + "§c.");
        }
    }

    // ------------------------------------------------------------------ the horn

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!items.isHorn(event.getItem())) {
            return;
        }
        // Clicking an entity can also arrive here as an "air" click a moment later: ignore that echo.
        Long entityClick = lastEntityCommand.get(player.getUniqueId());
        if (entityClick != null && System.currentTimeMillis() - entityClick < 250) {
            return;
        }
        if (!hornReady(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (player.isSneaking()) {
            Stance now = registry.army(id).stance.toggled();
            registry.setStance(id, now);
            player.sendMessage(now == Stance.FOLLOW ? "§aYour army will §ffollow§a you."
                    : "§aYour army will §fhold§a where it stands.");
        } else {
            director.clearTarget(id);
            player.sendMessage("§aYour army rallies to you.");
        }
    }

    private boolean hornReady(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastHorn.get(player.getUniqueId());
        if (last != null && now - last < cfg.hornCooldownMillis) {
            return false;
        }
        lastHorn.put(player.getUniqueId(), now);
        return true;
    }

    private void sendArmy(Player player, LivingEntity target) {
        UUID id = player.getUniqueId();
        if (registry.count(id) == 0) {
            player.sendMessage("§cYou have no soldiers yet. Right-click a small animal with a Recruitment Banner.");
            return;
        }
        Verdict v = director.setTarget(id, target, true);
        if (v == Verdict.OK) {
            String what = target instanceof Player p ? p.getName() : target.getType().name().toLowerCase().replace('_', ' ');
            player.sendMessage("§6Charge! §7Your army attacks §f" + what + "§7.");
        } else {
            player.sendMessage("§c" + v.reason);
        }
    }

    // ------------------------------------------------------------------ fighting

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageGuard(EntityDamageByEntityEvent event) {
        Entity attacker = resolveAttacker(event.getDamager());
        Entity victim = event.getEntity();
        UUID attackerSoldierOwner = service.ownerOf(attacker);
        UUID victimSoldierOwner = service.ownerOf(victim);

        // Soldiers never hurt their own side.
        if (attackerSoldierOwner != null) {
            boolean ownerHit = victim.getUniqueId().equals(attackerSoldierOwner);
            boolean allyHit = victimSoldierOwner != null
                    && (victimSoldierOwner.equals(attackerSoldierOwner) || registry.isTrusted(attackerSoldierOwner, victimSoldierOwner));
            boolean trustedHit = victim instanceof Player && registry.isTrusted(attackerSoldierOwner, victim.getUniqueId());
            if (ownerHit || allyHit || trustedHit) {
                event.setCancelled(true);
                return;
            }
        }
        // An owner swinging at their own soldier by accident.
        if (cfg.protectFromOwner && victimSoldierOwner != null && attacker != null
                && attacker.getUniqueId().equals(victimSoldierOwner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageReact(EntityDamageByEntityEvent event) {
        if (!cfg.autoDefend) {
            return;
        }
        Entity attacker = resolveAttacker(event.getDamager());
        Entity victim = event.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker) || !(victim instanceof LivingEntity livingVictim)) {
            return;
        }
        // The owner was hurt: hit back at whoever did it.
        if (victim instanceof Player owner && registry.hasArmy(owner.getUniqueId())) {
            director.setTarget(owner.getUniqueId(), livingAttacker, false);
        }
        // The owner attacked something: join in.
        if (attacker instanceof Player owner && registry.hasArmy(owner.getUniqueId())) {
            director.setTarget(owner.getUniqueId(), livingVictim, false);
        }
        // A soldier was hurt: its army defends it.
        UUID soldierOwner = service.ownerOf(victim);
        if (soldierOwner != null && registry.hasArmy(soldierOwner)) {
            director.setTarget(soldierOwner, livingAttacker, false);
        }
    }

    private static Entity resolveAttacker(Entity damager) {
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    // ------------------------------------------------------------------ losing soldiers

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        director.forget(dead.getUniqueId());
        UUID owner = service.ownerOf(dead);
        if (owner == null) {
            return;
        }
        registry.remove(dead.getUniqueId());
        if (!cfg.dropsOnDeath) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            p.sendMessage("§cOne of your soldiers fell. §7Army: §f" + registry.count(owner) + "§7/§f" + service.capFor(p));
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD || event.getCause() == EntityRemoveEvent.Cause.PLAYER_QUIT) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && registry.ownerOf(mob.getUniqueId()) != null) {
            registry.remove(mob.getUniqueId());
        }
        director.forget(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof Mob mob) {
                service.onSoldierLoaded(mob);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastEntityCommand.remove(id);
        lastHorn.remove(id);
    }
}
