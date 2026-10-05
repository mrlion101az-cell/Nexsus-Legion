package com.nexuscraft.nexuslegion;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NexusLegionPlugin extends JavaPlugin {

    private LegionConfig cfg;
    private ArmyRegistry registry;
    private Keys keys;
    private ArmyService service;
    private CombatDirector director;
    private LegionItems items;
    private BukkitTask combatTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.cfg = new LegionConfig();
        cfg.load(getConfig());
        this.keys = new Keys(this);
        this.registry = new ArmyRegistry();
        loadArmies();
        this.service = new ArmyService(this, cfg, registry, keys);
        this.director = new CombatDirector(this, cfg, registry, service, keys);
        this.items = new LegionItems(keys);

        getServer().getPluginManager().registerEvents(new LegionListener(cfg, registry, service, director, items), this);
        LegionCommand command = new LegionCommand(cfg, registry, service, director, items, keys, this::reload);
        getCommand("legion").setExecutor(command);
        getCommand("legion").setTabCompleter(command);

        registerRecipes();
        schedule();
        getLogger().info("NexusLegion enabled -- " + cfg.types.size() + " soldier types, "
                + registry.total() + " soldier(s) on file.");
    }

    @Override
    public void onDisable() {
        if (combatTask != null) {
            combatTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        saveArmies();
        Bukkit.removeRecipe(keys.recipeBanner);
        Bukkit.removeRecipe(keys.recipeHorn);
    }

    private void schedule() {
        if (combatTask != null) {
            combatTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        combatTask = getServer().getScheduler().runTaskTimer(this, director, cfg.tickInterval, cfg.tickInterval);
        // Changes are written within a second or so, not only on shutdown, so a crash loses nothing.
        saveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (registry.consumeDirty()) {
                saveArmies();
            }
        }, 20L, 20L);
    }

    private void reload() {
        reloadConfig();
        cfg.load(getConfig());
        registerRecipes();
        schedule();
    }

    private void registerRecipes() {
        Bukkit.removeRecipe(keys.recipeBanner);
        Bukkit.removeRecipe(keys.recipeHorn);
        if (!cfg.recipesEnabled) {
            return;
        }
        add(new ShapedRecipe(keys.recipeBanner, items.banner(1)), cfg.bannerShape, cfg.bannerIngredients);
        add(new ShapedRecipe(keys.recipeHorn, items.horn(1)), cfg.hornShape, cfg.hornIngredients);
    }

    private void add(ShapedRecipe recipe, List<String> shape, Map<Character, String> ingredients) {
        recipe.shape(shape.toArray(new String[0]));
        for (Map.Entry<Character, String> e : ingredients.entrySet()) {
            Material m = Material.matchMaterial(e.getValue());
            if (m == null || m == Material.AIR) {
                getLogger().warning("Recipe ingredient '" + e.getKey() + "' = '" + e.getValue() + "' is not a material -- recipe skipped.");
                return;
            }
            recipe.setIngredient(e.getKey(), m);
        }
        try {
            Bukkit.addRecipe(recipe);
        } catch (IllegalArgumentException bad) {
            getLogger().warning("Could not register a recipe (" + bad.getMessage() + ") -- check items.* in config.yml.");
        }
    }

    // ------------------------------------------------------------------ saving

    private void loadArmies() {
        File file = new File(getDataFolder(), "armies.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("armies");
        if (root == null) {
            return;
        }
        Map<String, Map<String, Object>> data = new HashMap<>();
        for (String owner : root.getKeys(false)) {
            Map<String, Object> m = new HashMap<>();
            m.put("soldiers", root.getStringList(owner + ".soldiers"));
            m.put("trusted", root.getStringList(owner + ".trusted"));
            m.put("stance", root.getString(owner + ".stance", "FOLLOW"));
            m.put("epoch", root.getInt(owner + ".epoch", 0));
            data.put(owner, m);
        }
        registry.restore(data);
    }

    private void saveArmies() {
        getDataFolder().mkdirs();
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Map<String, Object>> e : registry.snapshot().entrySet()) {
            for (Map.Entry<String, Object> f : e.getValue().entrySet()) {
                yml.set("armies." + e.getKey() + "." + f.getKey(), f.getValue());
            }
        }
        try {
            yml.save(new File(getDataFolder(), "armies.yml"));
        } catch (IOException ex) {
            getLogger().warning("Failed to save armies.yml: " + ex.getMessage());
        }
    }
}
