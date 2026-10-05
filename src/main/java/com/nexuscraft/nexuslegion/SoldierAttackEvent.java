package com.nexuscraft.nexuslegion;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired just before a soldier hits something. Cancel it to protect the target: any other plugin
 * (land claims, safe zones, prisons, arenas) can listen for this and veto an attack, which is how
 * "protected zones" extend beyond the built-in spawn radius.
 */
public final class SoldierAttackEvent extends Event implements Cancellable {

    private final Mob soldier;
    private final UUID ownerId;
    private final LivingEntity target;
    private double damage;
    private boolean cancelled;
    private static final HandlerList HANDLERS = new HandlerList();

    SoldierAttackEvent(Mob soldier, UUID ownerId, LivingEntity target, double damage) {
        this.soldier = soldier;
        this.ownerId = ownerId;
        this.target = target;
        this.damage = damage;
    }

    public Mob getSoldier() {
        return soldier;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = Math.max(0.0, damage);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
