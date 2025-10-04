package com.lyttledev.lyttledisguise.commands.disquise;

import com.lyttledev.lyttledisguise.LyttleDisguise;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import dev.iiahmed.disguise.DisguiseProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /disguise command entrypoint with lightweight parsing and tab completion.
 * Delegates business logic to DisguiseService.
 */
public final class DisguiseCommand implements CommandExecutor, TabCompleter {

    private final LyttleDisguise plugin;
    private final DisguiseService service;

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

        final String firstArg = args[0];

        // reset path
        if ("reset".equalsIgnoreCase(firstArg)) {
            service.resetDisguise(player);
            return true;
        }

        // normal semantics
        final String newNameRaw = firstArg;
        boolean doFetch = true;
        String fetchTarget = firstArg;

        if (args.length >= 2) {
            final String secondArg = args[1];
            if ("false".equalsIgnoreCase(secondArg)) {
                doFetch = false;
            } else if ("true".equalsIgnoreCase(secondArg)) {
                doFetch = true;
                fetchTarget = firstArg;
            } else {
                doFetch = true;
                fetchTarget = secondArg;
            }
        }

        service.applyDisguise(player, newNameRaw, doFetch ? fetchTarget : null, doFetch);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        final List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);

            if ("reset".startsWith(prefix)) suggestions.add("reset");

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p == null) continue;
                suggestions.add(p.getName());
            }
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op == null) continue;
                final String name = op.getName();
                if (name == null) continue;
                // if (op.isOnline()) continue; // offline only
                if (!name.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                if (suggestions.contains(name)) continue;
                suggestions.add(name);
            }
            return suggestions;
        }

        if (args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);

            // boolean hints only when being typed
            if ("true".startsWith(prefix)) suggestions.add("true");
            if ("false".startsWith(prefix)) suggestions.add("false");

            int cap = 20;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (cap <= 0) break;
                if (op == null) continue;
                final String name = op.getName();
                if (name == null) continue;
                if (op.isOnline()) continue; // offline only
                if (!name.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                suggestions.add(name);
                cap--;
            }
            return suggestions;
        }

        return suggestions;
    }
}