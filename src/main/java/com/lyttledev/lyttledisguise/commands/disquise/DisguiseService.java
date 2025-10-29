package com.lyttledev.lyttledisguise.commands.disquise;

import com.lyttledev.lyttledisguise.LyttleDisguise;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import dev.iiahmed.disguise.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Encapsulates disguise workflows:
 * - cleanup and reset
 * - name sanitization and collision retries
 * - skin fetch orchestration and application
 */
final class DisguiseService {

    private final LyttleDisguise plugin;
    private final DisguiseProvider provider;
    private final SkinResolver skinResolver;

    DisguiseService(@NotNull LyttleDisguise plugin,
                    @NotNull DisguiseProvider provider,
                    @NotNull SkinResolver skinResolver) {
        this.plugin = plugin;
        this.provider = provider;
        this.skinResolver = skinResolver;
    }

    void resetDisguise(@NotNull Player player) {
        final long start = System.currentTimeMillis();
        final UndisguiseResponse res = provider.undisguise(player);
        if (res == UndisguiseResponse.SUCCESS || res == UndisguiseResponse.FAIL_ALREADY_UNDISGUISED) {
            plugin.message.sendMessage(player, "disguise_reset",
                    new Replacements.Builder()
                            .add("<DURATION>", String.valueOf(System.currentTimeMillis() - start))
                            .build());
        } else {
            plugin.message.sendMessage(player, "disguise_undisguise_failed",
                    new Replacements.Builder().add("<RESULT>", res.toString()).build());
        }
    }

    void applyEntityDisguise(@NotNull Player player, @NotNull EntityType entityType) {
        // cleanup stale disguise to free any previous state
        preCleanup(player);

        final long start = System.currentTimeMillis();
        
        try {
            final Disguise disguise = Disguise.builder()
                    .setEntity(entityType)
                    .build();
            
            final DisguiseResponse result = provider.disguise(player, disguise);
            
            if (result == DisguiseResponse.SUCCESS) {
                plugin.message.sendMessage(player, "disguise_entity_applied",
                        new Replacements.Builder()
                                .add("<ENTITY_TYPE>", entityType.name())
                                .add("<RESULT>", result.toString())
                                .add("<DURATION>", String.valueOf(System.currentTimeMillis() - start))
                                .build());
            } else {
                plugin.message.sendMessage(player, "disguise_entity_failed",
                        new Replacements.Builder()
                                .add("<ENTITY_TYPE>", entityType.name())
                                .add("<RESULT>", result.toString())
                                .build());
            }
        } catch (Exception ex) {
            plugin.message.sendMessage(player, "disguise_update_failed",
                    new Replacements.Builder()
                            .add("<ERROR>", ex.getMessage() == null ? "Unknown error" : ex.getMessage())
                            .build());
        }
    }

    void applyDisguise(@NotNull Player player,
                       @NotNull String newNameRaw,
                       String fetchTargetOrNull,
                       boolean doFetch) {

        // cleanup stale disguise to free any previous nickname registration
        preCleanup(player);

        final String baseName = NameUtil.sanitizeBaseName(newNameRaw);

        if (!doFetch) {
            final long start = System.currentTimeMillis();
            final DisguiseResponse result = applyWithRetries(player, baseName, null);
            plugin.message.sendMessage(player, "disguise_done",
                    new Replacements.Builder()
                            .add("<NEW_NAME>", NameUtil.getEffectiveNameFromResult(baseName, result))
                            .add("<SKIN_TARGET>", "-")
                            .add("<RESULT>", result.toString())
                            .add("<DURATION>", String.valueOf(System.currentTimeMillis() - start))
                            .build());
            return;
        }

        final String target = fetchTargetOrNull != null ? fetchTargetOrNull : baseName;
        plugin.message.sendMessage(player, "disguise_resolving",
                new Replacements.Builder().add("<TARGET>", target).build());

        final long start = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final SkinResolver.SkinData data = skinResolver.resolve(target);
                if (data == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.message.sendMessage(player, "disguise_resolve_failed",
                                    new Replacements.Builder().add("<TARGET>", target).build()));
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    final DisguiseResponse result = applyWithRetries(player, baseName, data);
                    plugin.message.sendMessage(player, "disguise_done",
                            new Replacements.Builder()
                                    .add("<NEW_NAME>", NameUtil.getEffectiveNameFromResult(baseName, result))
                                    .add("<SKIN_TARGET>", target)
                                    .add("<RESULT>", result.toString())
                                    .add("<DURATION>", String.valueOf(System.currentTimeMillis() - start))
                                    .build());
                });
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.message.sendMessage(player, "disguise_update_failed",
                                new Replacements.Builder()
                                        .add("<ERROR>", ex.getMessage() == null ? "Unknown error" : ex.getMessage())
                                        .build()));
            }
        });
    }

    private void preCleanup(Player player) {
        if (provider.isDisguised(player)) {
            final UndisguiseResponse res = provider.undisguise(player);
            plugin.message.sendMessage(player, "disguise_cleanup_result",
                    new Replacements.Builder().add("<RESULT>", res.toString()).build());
        }
    }

    // Attempt with base name; on collision, try suffixed variants.
    private DisguiseResponse applyWithRetries(Player player, String baseName, SkinResolver.SkinData skin) {
        final int maxAttempts = 25;
        DisguiseResponse last = DisguiseResponse.FAIL_EMPTY_DISGUISE;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            final String candidate = NameUtil.buildCandidate(baseName, attempt);
            final Disguise.Builder builder = Disguise.builder().setName(candidate);

            // Apply skin preference: textures/signature first, then UUID via SkinAPI fallback
            if (skin != null) {
                if (skin.textures != null && skin.signature != null) {
                    builder.setSkin(skin.textures, skin.signature);
                } else if (skin.uuid != null) {
                    builder.setSkin(SkinAPI.MOJANG, skin.uuid);
                }
            }

            final Disguise disguise = builder.build();
            final DisguiseResponse result = provider.disguise(player, disguise);

            if (result == DisguiseResponse.SUCCESS) {
                if (attempt > 1) {
                    plugin.message.sendMessage(player, "disguise_name_retry_success",
                            new Replacements.Builder().add("<CANDIDATE>", candidate).build());
                }
                return result;
            }

            if (result == DisguiseResponse.FAIL_NAME_ALREADY_ONLINE
                    || result == DisguiseResponse.FAIL_NAME_INVALID
                    || result == DisguiseResponse.FAIL_NAME_TOO_LONG) {

                if (attempt < maxAttempts) {
                    plugin.message.sendMessage(player, "disguise_name_retry",
                            new Replacements.Builder().add("<CANDIDATE>", candidate).build());
                    last = result;
                } else {
                    plugin.message.sendMessage(player, "disguise_name_giveup",
                            new Replacements.Builder()
                                    .add("<BASE>", baseName)
                                    .add("<ATTEMPTS>", String.valueOf(maxAttempts))
                                    .build());
                    return result;
                }
            } else {
                return result;
            }
        }
        return last;
    }
}