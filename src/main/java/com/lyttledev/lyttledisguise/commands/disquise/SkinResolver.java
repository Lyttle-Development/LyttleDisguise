package com.lyttledev.lyttledisguise.commands.disquise;

import com.lyttledev.lyttledisguise.LyttleDisguise;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves skin information (textures/signature or UUID) for a target input.
 * Supports never-joined usernames using Ashcon/Mojang/PlayerDB and sessionserver.
 */
final class SkinResolver {

    static final class SkinData {
        final String textures;
        final String signature;
        final UUID uuid;

        SkinData(String textures, String signature, UUID uuid) {
            this.textures = textures;
            this.signature = signature;
            this.uuid = uuid;
        }
    }

    private static final Pattern UUID_HYPHENATED = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern UUID_STRIPPED = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final LyttleDisguise plugin;
    private final HttpClient http;

    SkinResolver(@NotNull LyttleDisguise plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    SkinData resolve(@NotNull String input) throws Exception {
        // UUID literal
        final UUID parsed = parseUuidFlexible(input);
        if (parsed != null) {
            final SkinData viaSession = fetchSessionTextures(parsed);
            if (viaSession != null) return viaSession;
            return new SkinData(null, null, parsed);
        }

        // Ashcon by username (never-joined support)
        final SkinData ashcon = fetchAshconTexturesByName(input);
        if (ashcon != null) return ashcon;

        // Mojang -> UUID -> sessionserver
        final UUID mojangUuid = fetchMojangUuidByName(input);
        if (mojangUuid != null) {
            final SkinData viaSession = fetchSessionTextures(mojangUuid);
            if (viaSession != null) return viaSession;
            return new SkinData(null, null, mojangUuid);
        }

        // PlayerDB -> UUID -> sessionserver
        final UUID playerDbUuid = fetchPlayerDbUuidByName(input);
        if (playerDbUuid != null) {
            final SkinData viaSession = fetchSessionTextures(playerDbUuid);
            if (viaSession != null) return viaSession;
            return new SkinData(null, null, playerDbUuid);
        }

        // Online player
        final Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            final SkinData viaSession = fetchSessionTextures(online.getUniqueId());
            if (viaSession != null) return viaSession;
            return new SkinData(null, null, online.getUniqueId());
        }

        // Known offline (has joined before)
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (offline != null && offline.hasPlayedBefore() && offline.getUniqueId() != null) {
            final SkinData viaSession = fetchSessionTextures(offline.getUniqueId());
            if (viaSession != null) return viaSession;
            return new SkinData(null, null, offline.getUniqueId());
        }

        return null;
    }

    private UUID parseUuidFlexible(String s) {
        if (s == null) return null;
        if (UUID_HYPHENATED.matcher(s).matches()) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        }
        if (UUID_STRIPPED.matcher(s).matches()) {
            try {
                final String hyphenated = s.substring(0, 8) + "-" +
                        s.substring(8, 12) + "-" +
                        s.substring(12, 16) + "-" +
                        s.substring(16, 20) + "-" +
                        s.substring(20);
                return UUID.fromString(hyphenated);
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    // sessionserver: UUID -> textures/signature
    private SkinData fetchSessionTextures(UUID uuid) throws Exception {
        if (uuid == null) return null;
        final String dashed = uuid.toString().replace("-", "");
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + dashed + "?unsigned=false"))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "LyttleDisguise/1.0 (+https://github.com/Lyttle-Development)")
                .GET().build();

        final HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        final String body = res.body();
        final int texNameIdx = body.indexOf("\"name\":\"textures\"");
        if (texNameIdx == -1) return null;

        final int valIdx = body.indexOf("\"value\":\"", texNameIdx);
        if (valIdx == -1) return null;
        final int valStart = valIdx + 9;
        final int valEnd = body.indexOf('"', valStart);
        if (valEnd == -1) return null;
        final String value = body.substring(valStart, valEnd);

        final int sigIdx = body.indexOf("\"signature\":\"", texNameIdx);
        if (sigIdx == -1) return null;
        final int sigStart = sigIdx + 13;
        final int sigEnd = body.indexOf('"', sigStart);
        if (sigEnd == -1) return null;
        final String signature = body.substring(sigStart, sigEnd);

        return new SkinData(value, signature, uuid);
        // NOTE: We return textures/signature and uuid. Caller will prefer textures/signature.
    }

    // Ashcon API: username -> raw textures/signature
    private SkinData fetchAshconTexturesByName(String username) throws Exception {
        if (username == null || username.isBlank()) return null;

        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.ashcon.app/mojang/v2/user/" + username))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "LyttleDisguise/1.0 (+https://github.com/Lyttle-Development)")
                .GET().build();

        final HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        final String body = res.body();
        final int rawIdx = body.indexOf("\"raw\"");
        if (rawIdx == -1) return null;

        final int vIdx = body.indexOf("\"value\":\"", rawIdx);
        if (vIdx == -1) return null;
        final int vStart = vIdx + 9;
        final int vEnd = body.indexOf('"', vStart);
        if (vEnd == -1) return null;
        final String value = body.substring(vStart, vEnd);

        final int sIdx = body.indexOf("\"signature\":\"", rawIdx);
        if (sIdx == -1) return null;
        final int sStart = sIdx + 13;
        final int sEnd = body.indexOf('"', sStart);
        if (sEnd == -1) return null;
        final String signature = body.substring(sStart, sEnd);

        // optionally extract uuid
        UUID uuid = null;
        final int uIdx = body.indexOf("\"uuid\":\"");
        if (uIdx != -1) {
            final int uStart = uIdx + 8;
            final int uEnd = body.indexOf('"', uStart);
            if (uEnd != -1) uuid = parseUuidFlexible(body.substring(uStart, uEnd));
        }
        return new SkinData(value, signature, uuid);
    }

    // Mojang: username -> UUID (32hex)
    private UUID fetchMojangUuidByName(String username) throws Exception {
        if (username == null || username.isBlank()) return null;

        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "LyttleDisguise/1.0 (+https://github.com/Lyttle-Development)")
                .GET().build();

        final HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        final String body = res.body();
        final int idIdx = body.indexOf("\"id\":\"");
        if (idIdx == -1) return null;
        final int start = idIdx + 6;
        final int end = body.indexOf('"', start);
        if (end == -1) return null;
        final String id = body.substring(start, end).trim();
        return parseUuidFlexible(id);
    }

    // PlayerDB: username -> UUID (raw_id or dashed id)
    private UUID fetchPlayerDbUuidByName(String username) throws Exception {
        if (username == null || username.isBlank()) return null;

        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://playerdb.co/api/player/minecraft/" + username))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "LyttleDisguise/1.0 (+https://github.com/Lyttle-Development)")
                .GET().build();

        final HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        final String body = res.body();
        int idx = body.indexOf("\"raw_id\":\"");
        if (idx != -1) {
            final int s = idx + 10;
            final int e = body.indexOf('"', s);
            if (e != -1) {
                final String raw = body.substring(s, e);
                final UUID u = parseUuidFlexible(raw);
                if (u != null) return u;
            }
        }
        idx = body.indexOf("\"id\":\"");
        if (idx != -1) {
            final int s = idx + 6;
            final int e = body.indexOf('"', s);
            if (e != -1) {
                final String id = body.substring(s, e);
                final UUID u = parseUuidFlexible(id);
                if (u != null) return u;
            }
        }
        return null;
    }
}