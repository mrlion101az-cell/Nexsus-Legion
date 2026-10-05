package com.nexuscraft.nexuslegion;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The two tools: the Recruitment Banner (enlist a mob) and the War Horn (command the army). Both
 *  are ordinary items tagged in item data -- right-clicking works the same on Java and Bedrock. */
final class LegionItems {

    static final String BANNER = "banner";
    static final String HORN = "horn";

    private final Keys keys;

    LegionItems(Keys keys) {
        this.keys = keys;
    }

    ItemStack banner(int amount) {
        return make(Material.RED_BANNER, amount, BANNER, "§c§lRecruitment Banner", List.of(
                "§7Right-click a small animal to enlist it",
                "§7as a tiny soldier in your army.",
                "§7Costs a few gold nuggets per recruit.",
                "§8Cannot be placed."));
    }

    ItemStack horn(int amount) {
        return make(Material.GOAT_HORN, amount, HORN, "§6§lWar Horn", List.of(
                "§7Right-click a player or monster: send your army.",
                "§7Right-click the air: call them back.",
                "§7Sneak + right-click the air: follow / hold."));
    }

    private ItemStack make(Material material, int amount, String kind, String name, List<String> lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, kind);
        item.setItemMeta(meta);
        return item;
    }

    boolean isBanner(ItemStack item) {
        return BANNER.equals(kind(item));
    }

    boolean isHorn(ItemStack item) {
        return HORN.equals(kind(item));
    }

    private String kind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keys.itemKind, PersistentDataType.STRING);
    }
}
