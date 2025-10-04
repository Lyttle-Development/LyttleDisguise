package com.lyttledev.lyttledisguise.commands.disquise;

import com.lyttledev.lyttledisguise.LyttleDisguise;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import dev.iiahmed.disguise.DisguiseProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * /disguise command entrypoint with lightweight parsing and tab completion.
 * Delegates business logic to DisguiseService.
 */
public final class DisguiseCommand implements CommandExecutor, TabCompleter {
    private final LyttleDisguise plugin;
    private final DisguiseService service;

    private static final int PLAYER_SUGGESTION_CAP = 35;
    private static final int NAME_SUGGESTION_CAP = 35;
    private static final List<String> HARD_CODED = Arrays.asList("username", "skinname", "clear");

    public DisguiseCommand(@NotNull LyttleDisguise plugin, @NotNull DisguiseProvider provider) {
        this.plugin = plugin;
        this.service = new DisguiseService(plugin, provider, new SkinResolver(plugin));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            plugin.message.sendMessage(sender, "players_only");
            return true;
        }
        final Player player = (Player) sender;

        if (!player.hasPermission("lyttledisguise.use")) {
            plugin.message.sendMessage(sender, "no_permission");
            return true;
        }

        if (args.length < 1) {
            plugin.message.sendMessage(sender, "disguise_usage",
                    new Replacements.Builder().add("<LABEL>", label).build());
            return true;
        }

        // Parse: /disguise [<player>] (username|skinname|clear) <name>
        int argOffset = 0;
        Player target = player;

        // Target other player if first argument is a player name (and not 'username', 'skinname', 'clear')
        String firstArg = args[0];
        if (args.length >= 2
                && !firstArg.equalsIgnoreCase("username")
                && !firstArg.equalsIgnoreCase("skinname")
                && !firstArg.equalsIgnoreCase("clear")) {
            Player found = Bukkit.getPlayerExact(firstArg);
            if (found != null && (player.hasPermission("lyttledisguise.disguise.others") || player.isOp())) {
                target = found;
                argOffset = 1;
            }
        }

        String mode = args[argOffset];

        // Handle /disguise clear (self) or /disguise <player> clear (others)
        if ("clear".equalsIgnoreCase(mode)) {
            service.resetDisguise(target);
            return true;
        }

        // /disguise username <name> or /disguise skinname <name> (self)
        // /disguise <player> username <name> or /disguise <player> skinname <name> (others)
        if (args.length - argOffset < 2) {
            plugin.message.sendMessage(sender, "disguise_usage",
                    new Replacements.Builder().add("<LABEL>", label).build());
            return true;
        }

        String name = args[argOffset + 1];

        switch (mode.toLowerCase(Locale.ROOT)) {
            case "username":
                service.applyDisguise(target, name, name, true);
                break;
            case "skinname":
                service.applyDisguise(target, target.getName(), name, true);
                break;
            default:
                plugin.message.sendMessage(sender, "disguise_usage",
                        new Replacements.Builder().add("<LABEL>", label).build());
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        boolean canTargetOthers = sender.hasPermission("lyttledisguise.disguise.others") || sender.isOp();

        // /disguise <tab> (arg 1)
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            // Always add hardcoded options first and always visible
            for (String hard : HARD_CODED) {
                if (hard.startsWith(prefix)) suggestions.add(hard);
            }
            // Online players for <player> argument (others)
            if (canTargetOthers) {
                int cnt = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (cnt++ >= PLAYER_SUGGESTION_CAP) break;
                    String name = p.getName();
                    if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        suggestions.add(name);
                    }
                }
            }
            return suggestions;
        }

        // Others-syntax detection
        boolean othersSyntax = false;
        int argOffset = 0;
        Player targetPlayer = null;
        if (args.length >= 2
                && !args[0].equalsIgnoreCase("username")
                && !args[0].equalsIgnoreCase("skinname")
                && !args[0].equalsIgnoreCase("clear")) {
            Player found = Bukkit.getPlayerExact(args[0]);
            if (found != null && canTargetOthers) {
                othersSyntax = true;
                targetPlayer = found;
                argOffset = 1;
            }
        }

        // /disguise <player> <tab> (arg 2 in others-syntax)
        if (args.length == 2 && othersSyntax) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String hard : HARD_CODED) {
                if (hard.startsWith(prefix)) suggestions.add(hard);
            }
            return suggestions;
        }

        // /disguise <player> clear [no more options]
        if (args.length >= 3 && othersSyntax && "clear".equalsIgnoreCase(args[1])) {
            return suggestions;
        }

        // /disguise <player> username <tab> (arg 3 in others-syntax)
        if (args.length == 3 && othersSyntax) {
            String mode = args[1];
            if ("clear".equalsIgnoreCase(mode)) return suggestions;
            String prefix = args[2].toLowerCase(Locale.ROOT);
            Set<String> names = new LinkedHashSet<>();
            int cap = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(name);
                    if (++cap >= NAME_SUGGESTION_CAP) break;
                }
            }
            if (cap < NAME_SUGGESTION_CAP) {
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    String name = op.getName();
                    if (name != null && !names.contains(name) && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(name);
                        if (++cap >= NAME_SUGGESTION_CAP) break;
                    }
                }
            }
            suggestions.addAll(names);
            return suggestions;
        }

        // /disguise username <tab> or /disguise skinname <tab> (arg 2 in self)
        if (args.length == 2 && !othersSyntax) {
            String mode = args[0];
            if ("clear".equalsIgnoreCase(mode)) return suggestions;
            String prefix = args[1].toLowerCase(Locale.ROOT);
            Set<String> names = new LinkedHashSet<>();
            int cap = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(name);
                    if (++cap >= NAME_SUGGESTION_CAP) break;
                }
            }
            if (cap < NAME_SUGGESTION_CAP) {
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    String name = op.getName();
                    if (name != null && !names.contains(name) && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        names.add(name);
                        if (++cap >= NAME_SUGGESTION_CAP) break;
                    }
                }
            }
            suggestions.addAll(names);
            return suggestions;
        }

        // /disguise username <name> [no more options] (arg 3 in self)
        if (args.length >= 3 && !othersSyntax && "clear".equalsIgnoreCase(args[0])) {
            return suggestions;
        }
        if (args.length == 3 && !othersSyntax) {
            return suggestions;
        }

        return suggestions;
    }
}