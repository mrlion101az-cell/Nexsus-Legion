package com.nexuscraft.nexuslegion;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /legion} (aliases {@code /army}, {@code /minilegion}). */
final class LegionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("help", "status", "list", "stance", "rally", "attack",
            "trust", "untrust", "trusted", "disband", "recipes", "give", "reload");

    private final LegionConfig cfg;
    private final ArmyRegistry registry;
    private final ArmyService service;
    private final CombatDirector director;
    private final LegionItems items;
    private final Keys keys;
    private final Runnable onReload;

    LegionCommand(LegionConfig cfg, ArmyRegistry registry, ArmyService service, CombatDirector director,
                  LegionItems items, Keys keys, Runnable onReload) {
        this.cfg = cfg;
        this.registry = registry;
        this.service = service;
        this.director = director;
        this.items = items;
        this.keys = keys;
        this.onReload = onReload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, args);
            case "reload" -> reload(sender);
            default -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Run this in-game. (Staff: /legion give, /legion reload work from the console.)");
                    return true;
                }
                playerCommand(player, sub, args);
            }
        }
        return true;
    }

    private void playerCommand(Player p, String sub, String[] args) {
        UUID id = p.getUniqueId();
        switch (sub) {
            case "status", "info" -> status(p);
            case "list" -> list(p);
            case "stance" -> {
                if (args.length < 2) {
                    p.sendMessage("§7Your army is set to §f" + registry.army(id).stance.name().toLowerCase(Locale.ROOT)
                            + "§7. Use §f/legion stance follow§7 or §f/legion stance hold§7.");
                    return;
                }
                Stance s = Stance.parse(args[1], null);
                if (s == null) {
                    p.sendMessage("§cUse follow or hold.");
                    return;
                }
                registry.setStance(id, s);
                p.sendMessage(s == Stance.FOLLOW ? "§aYour army will §ffollow§a you." : "§aYour army will §fhold§a where it stands.");
            }
            case "rally" -> {
                director.clearTarget(id);
                p.sendMessage("§aYour army rallies to you.");
            }
            case "attack" -> attack(p, args);
            case "trust" -> trust(p, args, true);
            case "untrust" -> trust(p, args, false);
            case "trusted" -> trusted(p);
            case "disband" -> disband(p, args);
            case "recipes" -> recipes(p);
            default -> help(p);
        }
    }

    private void help(Player p) {
        p.sendMessage("§6§lNexusLegion §7-- tiny armies of animals");
        p.sendMessage("§e1. §7Craft a §cRecruitment Banner§7 (§f/legion recipes§7) and right-click a small animal.");
        p.sendMessage("§e2. §7Craft a §6War Horn§7: right-click a player or monster to send your army, the air to call it back.");
        p.sendMessage("§f/legion status §7- your army   §f/legion list §7- who is in it");
        p.sendMessage("§f/legion stance <follow|hold>   /legion rally   /legion attack <player>");
        p.sendMessage("§f/legion trust|untrust <player>§7 - friends your army never attacks");
        p.sendMessage("§f/legion disband§7 - release every soldier");
    }

    private void status(Player p) {
        UUID id = p.getUniqueId();
        Army a = registry.army(id);
        int loaded = service.loadedSoldiers(id).size();
        p.sendMessage("§6§lYour army §7-- §f" + a.soldiers.size() + "§7/§f" + service.capFor(p) + " soldiers"
                + (loaded < a.soldiers.size() ? " §8(" + loaded + " nearby, the rest are in unloaded areas)" : ""));
        p.sendMessage("§7Stance: §f" + a.stance.name().toLowerCase(Locale.ROOT)
                + "§7   Trusted friends: §f" + a.trusted.size()
                + "§7   Target: §f" + (director.targetOf(id) == null ? "none" : "engaged"));
    }

    private void list(Player p) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Mob m : service.loadedSoldiers(p.getUniqueId())) {
            String t = m.getPersistentDataContainer().get(keys.soldierType, PersistentDataType.STRING);
            SoldierType type = cfg.type(t);
            counts.merge(type == null ? String.valueOf(t) : type.displayName(), 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            p.sendMessage("§7No soldiers nearby.");
            return;
        }
        p.sendMessage("§6Soldiers nearby:");
        counts.forEach((k, v) -> p.sendMessage("§7 - §f" + k + " §7x" + v));
    }

    private void attack(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /legion attack <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            p.sendMessage("§cThat player is not online.");
            return;
        }
        if (registry.count(p.getUniqueId()) == 0) {
            p.sendMessage("§cYou have no soldiers yet.");
            return;
        }
        Verdict v = director.setTarget(p.getUniqueId(), target, true);
        p.sendMessage(v == Verdict.OK ? "§6Charge! §7Your army attacks §f" + target.getName() + "§7." : "§c" + v.reason);
    }

    private void trust(Player p, String[] args, boolean add) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /legion " + (add ? "trust" : "untrust") + " <player>");
            return;
        }
        OfflinePlayer other = Bukkit.getPlayerExact(args[1]) != null ? Bukkit.getPlayerExact(args[1]) : Bukkit.getOfflinePlayer(args[1]);
        if (other == null || other.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage("§cPick another player.");
            return;
        }
        boolean changed = add ? registry.trust(p.getUniqueId(), other.getUniqueId())
                : registry.untrust(p.getUniqueId(), other.getUniqueId());
        p.sendMessage(changed
                ? (add ? "§aYour army will never attack §f" + args[1] + "§a or their soldiers."
                : "§a" + args[1] + " is no longer trusted.")
                : (add ? "§7" + args[1] + " was already trusted." : "§7" + args[1] + " was not trusted."));
    }

    private void trusted(Player p) {
        List<String> names = new ArrayList<>();
        for (UUID u : registry.army(p.getUniqueId()).trusted) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(u);
            names.add(o != null && o.getName() != null ? o.getName() : u.toString().substring(0, 8));
        }
        p.sendMessage(names.isEmpty() ? "§7You trust nobody yet. Use §f/legion trust <player>§7."
                : "§6Trusted: §f" + String.join("§7, §f", names));
    }

    private void disband(Player p, String[] args) {
        UUID id = p.getUniqueId();
        int total = registry.count(id);
        if (total == 0) {
            p.sendMessage("§7You have no soldiers.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            p.sendMessage("§cThis releases all §f" + total + "§c soldiers back into the wild. "
                    + "Type §f/legion disband confirm§c to do it.");
            return;
        }
        director.clearTarget(id);
        int released = service.disband(id);
        p.sendMessage("§aDisbanded. §f" + released + "§a soldiers returned to normal"
                + (released < total ? "§7 (" + (total - released) + " more will turn normal when their area loads)" : "") + "§a.");
    }

    private void recipes(Player p) {
        p.sendMessage("§6§lRecipes §7(crafting table)");
        p.sendMessage("§cRecruitment Banner§7: " + describe(cfg.bannerShape, cfg.bannerIngredients));
        p.sendMessage("§6War Horn§7: " + describe(cfg.hornShape, cfg.hornIngredients));
        p.sendMessage("§7Enlisting costs a few §f" + LegionConfig.pretty(cfg.costItem).toLowerCase(Locale.ROOT)
                + "§7 per animal (more for bigger fighters).");
    }

    private static String describe(List<String> shape, Map<Character, String> ing) {
        StringBuilder sb = new StringBuilder();
        for (String row : shape) {
            sb.append("[").append(row.replace(' ', '.')).append("] ");
        }
        sb.append("§8where ");
        ing.forEach((k, v) -> sb.append(k).append("=").append(LegionConfig.pretty(v).toLowerCase(Locale.ROOT)).append(' '));
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ staff

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexuslegion.admin")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return;
        }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("banner") || args[1].equalsIgnoreCase("horn"))) {
            sender.sendMessage("§7Usage: §f/legion give <banner|horn> [player] [amount]");
            return;
        }
        Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage("§cPick an online player.");
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cThe amount must be a number.");
                return;
            }
        }
        ItemStack item = args[1].equalsIgnoreCase("banner") ? items.banner(amount) : items.horn(amount);
        for (ItemStack rest : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        sender.sendMessage("§aGave " + amount + " " + args[1].toLowerCase(Locale.ROOT) + " to " + target.getName() + ".");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("nexuslegion.admin")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return;
        }
        onReload.run();
        sender.sendMessage("§7[NexusLegion] §fconfig.yml reloaded.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stance")) {
            out.addAll(List.of("follow", "hold"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("banner", "horn"));
        } else if (args.length == 2 && List.of("attack", "trust", "untrust").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
