package com.lyttledev.lyttledisguise.commands.disquise;

import com.lyttledev.lyttledisguise.LyttleDisguise;
import com.lyttledev.lyttledisguise.types.Configs;
import com.lyttledev.lyttledisguise.commands.disquise.NameUtil;
import com.lyttledev.lyttledisguise.commands.disquise.SkinResolver;
import com.lyttledev.lyttledisguise.commands.disquise.DisguiseService;
import com.lyttledev.lyttledisguise.commands.disquise.DisguiseService.*;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import dev.iiahmed.disguise.DisguiseProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /disguise command entrypoint with lightweight parsing and tab completion.
 * Delegates business logic to DisguiseService.
 */
public final class DisguiseCommand implements CommandExecutor, TabCompleter {
    private final LyttleDisguise plugin;
    private final DisguiseService service;

    private static final int PLAYER_SUGGESTION_CAP = 35;
    private static final int NAME_SUGGESTION_CAP = 35;
    private static final List<String> HARD_CODED = Arrays.asList("username", "skinname", "entity", "clear");
    private static final List<String> ENTITY_TYPES = getValidEntityTypes();

    public DisguiseCommand(@NotNull LyttleDisguise plugin, @NotNull DisguiseProvider provider) {
        this.plugin = plugin;
        this.service = new DisguiseService(plugin, provider, new SkinResolver(plugin));
    }

    private static List<String> getValidEntityTypes() {
        List<String> types = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            // Filter out non-living/special entity types
            if (type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER) {
                types.add(type.name());
            }
        }
        return types;
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

        // Parse: /disguise [<player>] (username|skinname|entity|clear) <name|entity_type>
        int argOffset = 0;
        Player target = player;

        // Target other player if first argument is a player name (and not a command keyword)
        String firstArg = args[0];
        if (args.length >= 2
                && !firstArg.equalsIgnoreCase("username")
                && !firstArg.equalsIgnoreCase("skinname")
                && !firstArg.equalsIgnoreCase("entity")
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
            // Notify initiator if acting on someone else
            if (target != player) {
                plugin.message.sendMessage(player, "disguise_cleared_other",
                        new Replacements.Builder()
                                .add("<TARGET_PLAYER>", target.getName())
                                .build());
            }
            return true;
        }

        // /disguise entity <entity_type> (self)
        // /disguise <player> entity <entity_type> (others)
        // /disguise username <name> or /disguise skinname <name> (self)
        // /disguise <player> username <name> or /disguise <player> skinname <name> (others)
        if (args.length - argOffset < 2) {
            plugin.message.sendMessage(sender, "disguise_usage",
                    new Replacements.Builder().add("<LABEL>", label).build());
            return true;
        }

        String argument = args[argOffset + 1];

        switch (mode.toLowerCase(Locale.ROOT)) {
            case "entity":
                try {
                    EntityType entityType = EntityType.valueOf(argument.toUpperCase(Locale.ROOT));
                    if (!entityType.isAlive() || !entityType.isSpawnable() || entityType == EntityType.PLAYER) {
                        plugin.message.sendMessage(sender, "disguise_invalid_entity",
                                new Replacements.Builder().add("<ENTITY>", argument).build());
                        return true;
                    }
                    service.applyEntityDisguise(target, entityType);
                } catch (IllegalArgumentException e) {
                    plugin.message.sendMessage(sender, "disguise_invalid_entity",
                            new Replacements.Builder().add("<ENTITY>", argument).build());
                    return true;
                }
                break;
            case "username":
                service.applyDisguise(target, argument, argument, true);
                break;
            case "skinname":
                service.applyDisguise(target, target.getName(), argument, true);
                break;
            default:
                plugin.message.sendMessage(sender, "disguise_usage",
                        new Replacements.Builder().add("<LABEL>", label).build());
                return true;
        }

        // Inform initiator when acting on someone else
        if (target != player) {
            plugin.message.sendMessage(player, "disguise_started_other",
                    new Replacements.Builder()
                            .add("<TARGET_PLAYER>", target.getName())
                            .add("<MODE>", mode.toLowerCase(Locale.ROOT))
                            .add("<NAME>", argument)
                            .build());
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
        if (args.length >= 2
                && !args[0].equalsIgnoreCase("username")
                && !args[0].equalsIgnoreCase("skinname")
                && !args[0].equalsIgnoreCase("entity")
                && !args[0].equalsIgnoreCase("clear")) {
            Player found = Bukkit.getPlayerExact(args[0]);
            if (found != null && canTargetOthers) {
                othersSyntax = true;
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

        // If 'clear', stop suggesting further options
        if (othersSyntax && args.length >= 3 && "clear".equalsIgnoreCase(args[1])) {
            return suggestions;
        }
        if (!othersSyntax && args.length >= 2 && "clear".equalsIgnoreCase(args[0])) {
            return suggestions;
        }

        // /disguise <player> username <tab> (arg 3 in others-syntax) -> name: online + offline
        // /disguise <player> entity <tab> -> entity types
        if (args.length == 3 && othersSyntax) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            String prefix = args[2].toLowerCase(Locale.ROOT);
            
            if ("entity".equals(mode)) {
                addEntitySuggestions(prefix, suggestions);
            } else if ("username".equals(mode) || "skinname".equals(mode)) {
                addNameSuggestions(prefix, suggestions);
            }
            return suggestions;
        }

        // /disguise username <tab> or /disguise skinname <tab> (arg 2 in self) -> name: online + offline
        // /disguise entity <tab> -> entity types
        if (args.length == 2 && !othersSyntax) {
            String mode = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            
            if ("entity".equals(mode)) {
                addEntitySuggestions(prefix, suggestions);
            } else if ("username".equals(mode) || "skinname".equals(mode)) {
                addNameSuggestions(prefix, suggestions);
            }
            return suggestions;
        }

        // No further suggestions beyond required args
        return suggestions;
    }

    private void addNameSuggestions(String prefix, List<String> out) {
        // Online first
        int cap = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String n = p.getName();
            if (n != null && n.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(n);
                if (++cap >= NAME_SUGGESTION_CAP) return;
            }
        }
        // Then offline (dedupe)
        Set<String> existing = new HashSet<>(out);
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String n = op.getName();
            if (n != null
                    && !existing.contains(n)
                    && n.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(n);
                if (++cap >= NAME_SUGGESTION_CAP) return;
            }
        }
    }

    private void addEntitySuggestions(String prefix, List<String> out) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String entityType : ENTITY_TYPES) {
            if (entityType.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                out.add(entityType);
            }
        }
    }
}