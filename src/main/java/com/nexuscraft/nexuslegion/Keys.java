package com.nexuscraft.nexuslegion;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Every data tag this plugin writes, in one place. */
final class Keys {
    final NamespacedKey soldierOwner;
    final NamespacedKey soldierType;
    final NamespacedKey soldierEpoch;
    final NamespacedKey origScale;
    final NamespacedKey origHealth;
    final NamespacedKey origName;
    final NamespacedKey itemKind;
    final NamespacedKey recipeBanner;
    final NamespacedKey recipeHorn;

    Keys(Plugin plugin) {
        soldierOwner = new NamespacedKey(plugin, "soldier_owner");
        soldierType = new NamespacedKey(plugin, "soldier_type");
        soldierEpoch = new NamespacedKey(plugin, "soldier_epoch");
        origScale = new NamespacedKey(plugin, "orig_scale");
        origHealth = new NamespacedKey(plugin, "orig_health");
        origName = new NamespacedKey(plugin, "orig_name");
        itemKind = new NamespacedKey(plugin, "item_kind");
        recipeBanner = new NamespacedKey(plugin, "recipe_banner");
        recipeHorn = new NamespacedKey(plugin, "recipe_horn");
    }
}
