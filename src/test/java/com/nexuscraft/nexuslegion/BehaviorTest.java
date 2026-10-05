package com.nexuscraft.nexuslegion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;

import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexuslegion.Kit.expect;

/** Recruiting, the combat loop, the listeners and the command, against the stub server. */
public final class BehaviorTest {

    static final Kit.TestPlugin PLUGIN = new Kit.TestPlugin();
    static LegionConfig cfg;
    static ArmyRegistry registry;
    static Keys keys;
    static ArmyService service;
    static CombatDirector director;
    static LegionItems items;
    static LegionListener listener;
    static LegionCommand command;

    static void fresh() {
        Kit.reset();
        PluginManager.HOOK = null;
        cfg = new LegionConfig();
        cfg.hornCooldownMillis = 0;
        registry = new ArmyRegistry();
        keys = new Keys(PLUGIN);
        service = new ArmyService(PLUGIN, cfg, registry, keys);
        director = new CombatDirector(PLUGIN, cfg, registry, service, keys);
        items = new LegionItems(keys);
        listener = new LegionListener(cfg, registry, service, director, items);
        command = new LegionCommand(cfg, registry, service, director, items, keys, () -> { });
    }

    static Kit.TestPlayer player(String name, double x, double z) {
        return Kit.online(new Kit.TestPlayer(name, x, z));
    }

    static Kit.TestMob mob(EntityType t, double x, double z) {
        return Kit.register(new Kit.TestMob(t, x, z));
    }

    static void tick(int times) {
        for (int i = 0; i < times; i++) {
            director.run();
        }
    }

    static Mob enlistRabbit(Kit.TestPlayer owner, double x, double z) {
        Kit.TestMob m = mob(EntityType.RABBIT, x, z);
        service.enlist(owner, m, cfg.type("RABBIT"));
        return m;
    }

    public static void main(String[] args) {
        recruiting();
        releasing();
        combat();
        listeners();
        commands();
        Kit.finish();
    }

    // ================================================================== recruiting

    static void recruiting() {
        fresh();
        Kit.TestPlayer alice = player("Alice", 1000, 1000);
        Kit.TestMob rabbit = mob(EntityType.RABBIT, 1001, 1000);

        expect(service.recruit(alice, rabbit) == ArmyService.Recruit.NO_FUNDS, "no gold -> NO_FUNDS");
        expect(registry.count(alice.getUniqueId()) == 0 && !service.isSoldier(rabbit), "a failed recruit changes nothing");
        alice.give(Material.GOLD_NUGGET, 1);
        alice.give(Material.GOLD_NUGGET, 1);
        expect(service.recruit(alice, rabbit) == ArmyService.Recruit.OK, "two nuggets in two stacks pays for a bunny");
        int left = 0;
        for (ItemStack s : alice.getInventory().getContents()) {
            if (s != null && s.getType() == Material.GOLD_NUGGET) {
                left += s.getAmount();
            }
        }
        expect(left == 0, "the cost is taken: " + left + " left");
        expect(service.isSoldier(rabbit) && service.ownerOf(rabbit).equals(alice.getUniqueId()), "the rabbit is Alice's soldier");
        expect(rabbit.getAttribute(Attribute.SCALE).getBaseValue() == 0.45, "it shrinks to the type's scale");
        expect(rabbit.getAttribute(Attribute.MAX_HEALTH).getBaseValue() == 6.0 && rabbit.getHealth() == 6.0, "its health is set");
        expect("§eAlice's Bunny".equals(rabbit.getCustomName()) && rabbit.isCustomNameVisible(), "it gets a name: " + rabbit.getCustomName());
        expect(rabbit.isPersistent() && !rabbit.getRemoveWhenFarAway(), "it never despawns");
        expect(registry.count(alice.getUniqueId()) == 1, "the army has one soldier");
        expect(service.recruit(alice, rabbit) == ArmyService.Recruit.ALREADY, "an enlisted mob cannot be enlisted again");

        // kinds of refusal
        expect(service.recruit(alice, mob(EntityType.ZOMBIE, 1, 1)) == ArmyService.Recruit.NOT_ENLISTABLE, "a zombie cannot be enlisted");
        expect(service.recruit(alice, alice) == ArmyService.Recruit.NOT_A_MOB, "a player cannot be enlisted");
        Kit.TestMob named = mob(EntityType.CHICKEN, 1, 1);
        named.setCustomName("Clucky");
        alice.give(Material.GOLD_NUGGET, 64);
        expect(service.recruit(alice, named) == ArmyService.Recruit.NAMED, "a name-tagged animal is refused");
        cfg.allowNamed = true;
        expect(service.recruit(alice, named) == ArmyService.Recruit.OK && "§eAlice's Chicken".equals(named.getCustomName()), "unless config allows it");
        cfg.allowNamed = false;
        Kit.TestTamed cat = Kit.register(new Kit.TestTamed(EntityType.CAT, 1, 1));
        expect(service.recruit(alice, cat) == ArmyService.Recruit.TAMED, "a tamed pet is refused");
        cat.setTamed(false);
        expect(service.recruit(alice, cat) == ArmyService.Recruit.OK, "a wild cat is fine");

        // cap
        fresh();
        cfg.baseCap = 2;
        Kit.TestPlayer bob = player("Bob", 1000, 1000);
        bob.give(Material.GOLD_NUGGET, 64);
        expect(service.recruit(bob, mob(EntityType.RABBIT, 1, 1)) == ArmyService.Recruit.OK, "soldier 1");
        expect(service.recruit(bob, mob(EntityType.RABBIT, 1, 1)) == ArmyService.Recruit.OK, "soldier 2");
        expect(service.recruit(bob, mob(EntityType.RABBIT, 1, 1)) == ArmyService.Recruit.CAP, "soldier 3 hits the cap");
        bob.perms.add("nexuslegion.cap.30");
        expect(service.capFor(bob) == 30, "a permission tier raises the cap");
        expect(service.recruit(bob, mob(EntityType.RABBIT, 1, 1)) == ArmyService.Recruit.OK, "and lets him recruit more");
        cfg.globalCap = 3;
        expect(service.recruit(bob, mob(EntityType.RABBIT, 1, 1)) == ArmyService.Recruit.GLOBAL_CAP, "the server-wide cap is enforced");
        cfg.globalCap = 500;

        // free recruiting, and renamed nuggets do not count as money
        fresh();
        Kit.TestPlayer cy = player("Cy", 1000, 1000);
        cfg.costEnabled = false;
        expect(service.recruit(cy, mob(EntityType.GOAT, 1, 1)) == ArmyService.Recruit.OK, "recruiting is free when cost is off");
        cfg.costEnabled = true;
        ItemStack fake = new ItemStack(Material.GOLD_NUGGET, 10);
        fake.setItemMeta(fake.getItemMeta());
        var meta = fake.getItemMeta();
        meta.setDisplayName("Special");
        fake.setItemMeta(meta);
        cy.getInventory().addItem(fake);
        expect(service.recruit(cy, mob(EntityType.RABBIT, 1, 1)) == ArmyService.Recruit.NO_FUNDS, "renamed/special nuggets are not payment");
    }

    // ================================================================== releasing

    static void releasing() {
        fresh();
        Kit.TestPlayer alice = player("Alice", 1000, 1000);
        Kit.TestMob pet = mob(EntityType.RABBIT, 1000, 1001);
        pet.setCustomName("Thumper");
        cfg.allowNamed = true;
        alice.give(Material.GOLD_NUGGET, 10);
        service.recruit(alice, pet);
        pet.setAI(false);
        service.release(pet);
        expect(!service.isSoldier(pet) && registry.count(alice.getUniqueId()) == 0, "released: not a soldier any more");
        expect(pet.getAttribute(Attribute.SCALE).getBaseValue() == 1.0, "size restored");
        expect(pet.getAttribute(Attribute.MAX_HEALTH).getBaseValue() == 20.0, "max health restored");
        expect("Thumper".equals(pet.getCustomName()) && !pet.isCustomNameVisible(), "its old name is restored");
        expect(pet.hasAI(), "its AI is switched back on");
        expect(pet.getPersistentDataContainer().get(keys.soldierOwner, PersistentDataType.STRING) == null, "tags are gone");

        // disband: loaded soldiers released, unloaded ones go stale via the epoch
        Mob a = enlistRabbit(alice, 1, 1);
        Mob b = enlistRabbit(alice, 2, 2);
        Kit.TestMob c = new Kit.TestMob(EntityType.RABBIT, 3, 3); // not registered in the world: "unloaded"
        service.enlist(alice, c, cfg.type("RABBIT"));
        int released = service.disband(alice.getUniqueId());
        expect(released == 2 && !service.isSoldier(a) && !service.isSoldier(b), "disband releases the loaded soldiers");
        expect(registry.count(alice.getUniqueId()) == 0, "and empties the roster");
        expect(c.getPersistentDataContainer().get(keys.soldierOwner, PersistentDataType.STRING) != null, "the unloaded one is still tagged");
        service.onSoldierLoaded(c);
        expect(!service.isSoldier(c) && c.getAttribute(Attribute.SCALE).getBaseValue() == 1.0,
                "...but is released as soon as it loads (stale epoch)");
        // adoption of a current-epoch orphan (e.g. registry file lost)
        Kit.TestMob d = Kit.register(new Kit.TestMob(EntityType.RABBIT, 4, 4));
        service.enlist(alice, d, cfg.type("RABBIT"));
        registry.remove(d.getUniqueId());
        expect(registry.count(alice.getUniqueId()) == 0, "orphaned: tagged but not on the roster");
        service.onSoldierLoaded(d);
        expect(registry.count(alice.getUniqueId()) == 1 && registry.ownerOf(d.getUniqueId()).equals(alice.getUniqueId()),
                "a current soldier that loads is adopted back onto the roster");
    }

    // ================================================================== combat loop

    static void combat() {
        fresh();
        Kit.TestPlayer alice = player("Alice", 1000, 1000);
        Kit.TestPlayer bob = player("Bob", 1030, 1000);
        UUID aid = alice.getUniqueId();
        Mob s1 = enlistRabbit(alice, 1001, 1000);
        Mob s2 = enlistRabbit(alice, 1002, 1000);

        expect(director.setTarget(aid, bob, true) == Verdict.OK, "Bob can be targeted");
        tick(1);
        expect(s1.getPathfinder().lastEntity == bob && s1.getPathfinder().lastSpeed == 1.5, "far soldiers charge at the target at their speed");
        expect(s2.getPathfinder().lastEntity == bob, "every soldier charges");
        expect(((org.bukkit.entity.LivingEntity) bob).lastDamage == 0.0, "nobody has hit yet");

        // reach: put s1 next to Bob
        ((Kit.TestMob) s1).loc = new Location(Kit.WORLD, 1030.5, 64, 1000);
        tick(1);
        expect(bob.lastDamage == 2.0 && bob.lastDamager == s1, "a soldier in reach hits for its type's damage");
        bob.lastDamage = 0.0;
        tick(1);
        tick(1);
        expect(bob.lastDamage == 0.0, "the cooldown stops a second hit straight away");
        tick(3);
        expect(bob.lastDamage == 2.0, "after the cooldown it hits again");

        // events
        bob.lastDamage = 0.0;
        PluginManager.HOOK = e -> {
            if (e instanceof SoldierAttackEvent ev) {
                ev.setCancelled(true);
            }
        };
        tick(8);
        expect(bob.lastDamage == 0.0, "another plugin cancelling SoldierAttackEvent prevents the hit");
        PluginManager.HOOK = e -> {
            if (e instanceof SoldierAttackEvent ev) {
                ev.setDamage(ev.getDamage() * 3);
            }
        };
        tick(8);
        expect(bob.lastDamage == 6.0, "a listener can change the damage: " + bob.lastDamage);
        PluginManager.HOOK = null;

        // protected zone: target walks into spawn protection
        bob.loc = new Location(Kit.WORLD, 10, 64, 10);
        alice.messages.clear();
        tick(1);
        expect(director.targetOf(aid) == null, "the army stands down when the target enters a protected zone");
        expect(alice.messages.stream().anyMatch(m -> m.contains("stands down")), "and tells the owner");

        // verdict at order time
        Kit.TestPlayer carl = player("Carl", 1000, 1010);
        carl.mode = org.bukkit.GameMode.CREATIVE;
        expect(director.setTarget(aid, carl, true) == Verdict.GAMEMODE, "creative players cannot be targeted");
        carl.mode = org.bukkit.GameMode.SURVIVAL;
        carl.addTestMetadata("vanished");
        expect(director.setTarget(aid, carl, true) == Verdict.VANISHED, "vanished players cannot be targeted");
        Kit.TestPlayer dee = player("Dee", 1000, 1012);
        registry.trust(aid, dee.getUniqueId());
        expect(director.setTarget(aid, dee, true) == Verdict.TRUSTED, "trusted players cannot be targeted");
        expect(director.setTarget(aid, alice, true) == Verdict.SELF, "you cannot target yourself");
        expect(director.setTarget(aid, (org.bukkit.entity.LivingEntity) s1, true) == Verdict.SELF, "or your own soldier");

        // out of chase range
        fresh();
        alice = player("Alice", 1000, 1000);
        aid = alice.getUniqueId();
        bob = player("Bob", 1030, 1000);
        enlistRabbit(alice, 1001, 1000);
        director.setTarget(aid, bob, true);
        bob.loc = new Location(Kit.WORLD, 1100, 64, 1000);
        tick(1);
        expect(director.targetOf(aid) == null, "a target beyond chase range is dropped");

        // memory: automatic targets expire, explicit ones do not
        bob.loc = new Location(Kit.WORLD, 1030, 64, 1000);
        director.setTarget(aid, bob, false);
        tick(50);
        expect(director.targetOf(aid) != null, "an automatic target is still remembered after 250 ticks");
        tick(20);
        expect(director.targetOf(aid) == null, "...and forgotten after 15 seconds");
        director.setTarget(aid, bob, true);
        tick(200);
        expect(director.targetOf(aid) != null, "an explicit horn order is not forgotten");
        Kit.TestMob zombie = Kit.register(new Kit.TestMob(EntityType.ZOMBIE, 1005, 1000));
        Kit.TestMonster husk = Kit.register(new Kit.TestMonster(1004, 1000));
        director.setTarget(aid, husk, false);
        expect(director.targetOf(aid).equals(bob.getUniqueId()), "an automatic reaction does not override a horn order");
        bob.valid = false;
        director.forget(bob.getUniqueId());
        director.setTarget(aid, husk, false);
        expect(husk.getUniqueId().equals(director.targetOf(aid)), "but takes over once that target is gone");
        expect(zombie != null, "(zombie placed)");

        // following, teleporting, holding
        fresh();
        alice = player("Alice", 1000, 1000);
        aid = alice.getUniqueId();
        Kit.TestMob near = (Kit.TestMob) enlistRabbit(alice, 1003, 1000);
        Kit.TestMob mid = (Kit.TestMob) enlistRabbit(alice, 1020, 1000);
        Kit.TestMob far = (Kit.TestMob) enlistRabbit(alice, 1200, 1000);
        tick(1);
        expect(near.getPathfinder().lastEntity == null, "a soldier already close to its owner is left alone");
        expect(mid.getPathfinder().lastEntity == alice, "a soldier further than the follow distance walks to its owner");
        expect(far.loc.getX() == 1000 && far.loc.getZ() == 1000, "a very distant soldier teleports to its owner");
        registry.setStance(aid, Stance.HOLD);
        mid.setAI(true);
        tick(1);
        expect(!mid.hasAI(), "HOLD switches a grounded soldier's AI off so it stays put");
        Kit.TestPlayer bob2 = player("Bob", 1021, 1000);
        director.setTarget(aid, bob2, true);
        tick(1);
        expect(mid.hasAI(), "a target switches the AI back on");
        director.clearTarget(aid);
        registry.setStance(aid, Stance.FOLLOW);
        tick(1);
        expect(mid.hasAI(), "FOLLOW keeps the AI on");

        // offline owner, unloaded soldiers, dead soldiers
        fresh();
        alice = player("Alice", 1000, 1000);
        aid = alice.getUniqueId();
        Kit.TestMob far2 = (Kit.TestMob) enlistRabbit(alice, 1200, 1000);
        Kit.TestMob unloaded = new Kit.TestMob(EntityType.RABBIT, 1, 1);
        service.enlist(alice, unloaded, cfg.type("RABBIT"));
        Bukkit.PLAYERS.remove(aid);
        tick(1);
        expect(far2.loc.getX() == 1200, "with the owner offline the army does nothing");
        Bukkit.PLAYERS.put(aid, alice);
        tick(1);
        expect(far2.loc.getX() == 1000, "once the owner is back it follows again");
        expect(registry.count(aid) == 2, "an unloaded soldier is left on the roster, not pruned");
        far2.valid = false;
        tick(1);
        expect(registry.count(aid) == 1, "a loaded soldier that is invalid is pruned");
    }

    // ================================================================== listeners

    static void listeners() {
        fresh();
        Kit.TestPlayer alice = player("Alice", 1000, 1000);
        UUID aid = alice.getUniqueId();
        Kit.TestPlayer bob = player("Bob", 1020, 1000);

        // placing the banner is blocked, a normal banner is fine
        BlockPlaceEvent place = new BlockPlaceEvent(null, alice, items.banner(1));
        listener.onBannerPlace(place);
        expect(place.isCancelled(), "the Recruitment Banner cannot be placed as a block");
        BlockPlaceEvent plain = new BlockPlaceEvent(null, alice, new ItemStack(Material.RED_BANNER));
        listener.onBannerPlace(plain);
        expect(!plain.isCancelled(), "an ordinary banner still can");
        expect(items.isBanner(items.banner(1)) && !items.isHorn(items.banner(1)) && items.isHorn(items.horn(1)), "items are told apart");
        expect(!items.isBanner(null) && !items.isBanner(new ItemStack(Material.RED_BANNER)), "a plain banner is not the Recruitment Banner");

        // banner on a mob
        alice.getInventory().setItemInMainHand(items.banner(1));
        alice.give(Material.GOLD_NUGGET, 10);
        Kit.TestMob rabbit = mob(EntityType.RABBIT, 1001, 1000);
        PlayerInteractEntityEvent click = new PlayerInteractEntityEvent(alice, rabbit);
        listener.onInteractEntity(click);
        expect(click.isCancelled(), "using the banner on an animal cancels the normal interaction");
        expect(service.isSoldier(rabbit) && alice.lastMessage().contains("enlisted"), "the animal joins and the owner is told: " + alice.lastMessage());
        Kit.TestMob zombie = mob(EntityType.ZOMBIE, 1003, 1000);
        listener.onInteractEntity(new PlayerInteractEntityEvent(alice, zombie));
        expect(alice.lastMessage().contains("cannot be a soldier"), "refusals are explained: " + alice.lastMessage());

        // the off hand does nothing
        PlayerInteractEntityEvent offhand = new PlayerInteractEntityEvent(alice, mob(EntityType.RABBIT, 1002, 1000));
        offhand.setHand(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        listener.onInteractEntity(offhand);
        expect(registry.count(aid) == 1, "an off-hand click is ignored");

        // horn on a player sends the army
        alice.getInventory().setItemInMainHand(items.horn(1));
        listener.onInteractEntity(new PlayerInteractEntityEvent(alice, bob));
        expect(director.targetOf(aid) != null && alice.lastMessage().contains("Charge"), "the horn on a player sends the army: " + alice.lastMessage());
        // ... and the echo "air click" that follows must not call them back
        listener.onInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, items.horn(1), null));
        expect(director.targetOf(aid) != null, "the air-click echo right after an entity click is ignored");
        sleep(300);
        listener.onInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, items.horn(1), null));
        expect(director.targetOf(aid) == null && alice.lastMessage().contains("rallies"), "a real air click rallies the army");
        alice.sneak = true;
        listener.onInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, items.horn(1), null));
        expect(registry.army(aid).stance == Stance.HOLD, "sneak + air click holds");
        listener.onInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, items.horn(1), null));
        expect(registry.army(aid).stance == Stance.FOLLOW, "and toggles back");
        alice.sneak = false;
        listener.onInteract(new PlayerInteractEvent(alice, Action.LEFT_CLICK_AIR, items.horn(1), null));
        listener.onInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, new ItemStack(Material.STICK), null));
        expect(registry.army(aid).stance == Stance.FOLLOW, "left clicks and other items do nothing");
        // horn refusals
        Kit.TestPlayer creative = player("Cre", 1100, 1000);
        creative.mode = org.bukkit.GameMode.CREATIVE;
        listener.onInteractEntity(new PlayerInteractEntityEvent(alice, creative));
        expect(alice.lastMessage().contains("cannot be attacked"), "ordering an attack on a protected player is refused: " + alice.lastMessage());
        Kit.TestPlayer loner = player("Loner", 5000, 5000);
        loner.getInventory().setItemInMainHand(items.horn(1));
        listener.onInteractEntity(new PlayerInteractEntityEvent(loner, bob));
        expect(loner.lastMessage().contains("no soldiers"), "the horn without an army explains itself: " + loner.lastMessage());
        // horn cooldown
        cfg.hornCooldownMillis = 5000;
        director.clearTarget(aid);
        alice.getInventory().setItemInMainHand(items.horn(1));
        listener.onInteractEntity(new PlayerInteractEntityEvent(alice, bob));
        director.clearTarget(aid);
        sleep(300);
        listener.onInteractEntity(new PlayerInteractEntityEvent(alice, bob));
        expect(director.targetOf(aid) == null, "the horn cooldown blocks rapid repeat orders");
        cfg.hornCooldownMillis = 0;

        // damage guard
        fresh();
        alice = player("Alice", 1000, 1000);
        aid = alice.getUniqueId();
        bob = player("Bob", 1020, 1000);
        Mob mine = enlistRabbit(alice, 1001, 1000);
        Mob mine2 = enlistRabbit(alice, 1002, 1000);
        Kit.TestPlayer dee = player("Dee", 1050, 1000);
        Mob deeSoldier = enlistRabbit(dee, 1051, 1000);
        Mob bobSoldier = enlistRabbit(bob, 1021, 1000);
        registry.trust(aid, dee.getUniqueId());

        expect(cancelled(mine, alice), "a soldier cannot hurt its owner");
        expect(cancelled(mine, mine2), "or a fellow soldier");
        expect(cancelled(mine, dee), "or a trusted player");
        expect(cancelled(mine, deeSoldier), "or a trusted player's soldier");
        expect(!cancelled(mine, bob), "but can hurt a stranger");
        expect(!cancelled(mine, bobSoldier), "or a stranger's soldier");
        expect(cancelled(alice, mine), "an owner cannot hurt their own soldier by accident");
        cfg.protectFromOwner = false;
        expect(!cancelled(alice, mine), "unless that protection is off");
        cfg.protectFromOwner = true;
        expect(!cancelled(bob, mine), "a stranger can hurt it");
        // projectile shot by an owned soldier-hitting-owner path
        expect(!cancelled(bob, alice), "ordinary player-vs-player damage is untouched");

        // auto-defend
        Kit.TestMonster husk = Kit.register(new Kit.TestMonster(1005, 1000));
        react(husk, alice);
        expect(husk.getUniqueId().equals(director.targetOf(aid)), "when the owner is hurt the army targets the attacker");
        director.clearTarget(aid);
        react(alice, husk);
        expect(husk.getUniqueId().equals(director.targetOf(aid)), "when the owner attacks, the army joins in");
        director.clearTarget(aid);
        react(bob, mine);
        expect(bob.getUniqueId().equals(director.targetOf(aid)), "when a soldier is hurt the army defends it");
        director.clearTarget(aid);
        react(husk, bob);
        expect(director.targetOf(aid) == null, "other people's fights do not involve the army");
        cfg.autoDefend = false;
        react(husk, alice);
        expect(director.targetOf(aid) == null, "auto-defend can be switched off");
        cfg.autoDefend = true;
        react(dee, alice);
        expect(director.targetOf(aid) == null, "a trusted friend hitting the owner does not start a war");

        // death
        alice.messages.clear();
        EntityDeathEvent death = new EntityDeathEvent(mine);
        death.getDrops().add(new ItemStack(Material.STICK));
        death.setDroppedExp(5);
        director.setTarget(aid, bob, true);
        listener.onDeath(death);
        expect(registry.count(aid) == 1 && registry.ownerOf(mine.getUniqueId()) == null, "a fallen soldier leaves the roster");
        expect(death.getDrops().isEmpty() && death.getDroppedExp() == 0, "and drops nothing");
        expect(alice.lastMessage().contains("fell"), "the owner is told: " + alice.lastMessage());
        EntityDeathEvent bobDeath = new EntityDeathEvent(bob);
        director.setTarget(aid, bob, true);
        listener.onDeath(bobDeath);
        expect(director.targetOf(aid) == null, "armies stop chasing something that died");
        cfg.dropsOnDeath = true;
        EntityDeathEvent keep = new EntityDeathEvent(mine2);
        keep.getDrops().add(new ItemStack(Material.STICK));
        listener.onDeath(keep);
        expect(keep.getDrops().size() == 1, "drops can be switched on");
        cfg.dropsOnDeath = false;

        // removal and loading
        Mob a1 = enlistRabbit(alice, 1, 1);
        listener.onRemove(new EntityRemoveEvent(a1, EntityRemoveEvent.Cause.UNLOAD));
        expect(registry.ownerOf(a1.getUniqueId()) != null, "an unload is not a loss");
        listener.onRemove(new EntityRemoveEvent(a1, EntityRemoveEvent.Cause.OUT_OF_WORLD));
        expect(registry.ownerOf(a1.getUniqueId()) == null, "falling out of the world is");
        Kit.TestMob stale = new Kit.TestMob(EntityType.RABBIT, 9, 9);
        service.enlist(alice, stale, cfg.type("RABBIT"));
        service.disband(aid);
        listener.onEntitiesLoad(new EntitiesLoadEvent(List.of(stale, new Kit.TestMob(EntityType.COW, 1, 1))));
        expect(!service.isSoldier(stale), "loading a disbanded soldier's chunk releases it");
    }

    static boolean cancelled(org.bukkit.entity.Entity damager, org.bukkit.entity.Entity victim) {
        EntityDamageByEntityEvent e = new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        listener.onDamageGuard(e);
        return e.isCancelled();
    }

    static void react(org.bukkit.entity.Entity damager, org.bukkit.entity.Entity victim) {
        listener.onDamageReact(new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK));
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ================================================================== command

    static void commands() {
        fresh();
        Kit.TestPlayer alice = player("Alice", 1000, 1000);
        UUID aid = alice.getUniqueId();
        Kit.TestPlayer bob = player("Bob", 1020, 1000);
        enlistRabbit(alice, 1001, 1000);
        enlistRabbit(alice, 1002, 1000);

        run(alice, "status");
        expect(alice.lastMessage().contains("follow") || alice.messages.stream().anyMatch(m -> m.contains("2")), "status shows the army");
        run(alice, "list");
        expect(alice.messages.stream().anyMatch(m -> m.contains("Bunny")), "list names the types");
        run(alice, "stance", "hold");
        expect(registry.army(aid).stance == Stance.HOLD, "/legion stance hold");
        run(alice, "stance", "banana");
        expect(alice.lastMessage().contains("follow or hold") && registry.army(aid).stance == Stance.HOLD, "a bad stance is refused");
        run(alice, "stance", "follow");
        run(alice, "attack", "Bob");
        expect(director.targetOf(aid) != null, "/legion attack <player>");
        run(alice, "rally");
        expect(director.targetOf(aid) == null, "/legion rally");
        run(alice, "attack", "Nobody");
        expect(alice.lastMessage().contains("not online"), "attacking a missing player is explained");
        run(alice, "trust", "Bob");
        expect(registry.isTrusted(aid, bob.getUniqueId()), "/legion trust");
        run(alice, "attack", "Bob");
        expect(alice.lastMessage().contains("trusted"), "a trusted friend cannot be attacked: " + alice.lastMessage());
        run(alice, "trusted");
        expect(alice.messages.stream().anyMatch(m -> m.contains("Bob")), "/legion trusted lists them");
        run(alice, "untrust", "Bob");
        expect(!registry.isTrusted(aid, bob.getUniqueId()), "/legion untrust");
        run(alice, "trust", "Alice");
        expect(alice.lastMessage().contains("another player"), "you cannot trust yourself");

        run(alice, "disband");
        expect(registry.count(aid) == 2 && alice.lastMessage().contains("disband confirm"), "disband asks for confirmation first");
        run(alice, "disband", "confirm");
        expect(registry.count(aid) == 0, "disband confirm releases everyone");
        run(alice, "disband");
        expect(alice.lastMessage().contains("no soldiers"), "disband with nobody says so");
        run(alice, "recipes");
        expect(alice.messages.stream().anyMatch(m -> m.contains("Recruitment Banner")), "/legion recipes");
        run(alice, "help");
        expect(alice.messages.stream().anyMatch(m -> m.contains("NexusLegion")), "/legion help");
        alice.messages.clear();
        run(alice, "frobnicate");
        expect(alice.messages.stream().anyMatch(m -> m.contains("NexusLegion")), "an unknown subcommand shows help");

        // staff
        run(alice, "give", "banner");
        expect(alice.lastMessage().contains("permission"), "give needs permission");
        alice.perms.add("nexuslegion.admin");
        run(alice, "give", "banner", "Alice", "3");
        boolean got = false;
        for (ItemStack s : alice.getInventory().getContents()) {
            got |= s != null && items.isBanner(s) && s.getAmount() == 3;
        }
        expect(got, "staff can give 3 banners");
        run(alice, "give", "horn");
        boolean horn = false;
        for (ItemStack s : alice.getInventory().getContents()) {
            horn |= s != null && items.isHorn(s);
        }
        expect(horn, "staff can give themselves a horn");
        run(alice, "give", "banner", "Alice", "lots");
        expect(alice.lastMessage().contains("number"), "a bad amount is explained");
        run(alice, "give", "sword");
        expect(alice.lastMessage().contains("Usage"), "a bad item shows usage");
        int[] reloads = {0};
        LegionCommand withReload = new LegionCommand(cfg, registry, service, director, items, keys, () -> reloads[0]++);
        withReload.onCommand(alice, null, "legion", new String[] {"reload"});
        expect(reloads[0] == 1, "staff can reload");
        alice.perms.clear();
        withReload.onCommand(alice, null, "legion", new String[] {"reload"});
        expect(reloads[0] == 1, "reload needs permission");
        expect(command.onTabComplete(alice, null, "legion", new String[] {"st"}).containsAll(List.of("status", "stance")), "tab completion of subcommands");
        expect(command.onTabComplete(alice, null, "legion", new String[] {"attack", "Bo"}).equals(List.of("Bob")), "tab completion of players");
    }

    static void run(Player p, String... args) {
        command.onCommand(p, null, "legion", args);
    }
}
