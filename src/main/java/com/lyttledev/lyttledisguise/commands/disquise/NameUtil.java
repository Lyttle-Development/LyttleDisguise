package com.lyttledev.lyttledisguise.commands.disquise;

import dev.iiahmed.disguise.DisguiseResponse;

import java.util.regex.Pattern;

/**
 * Name sanitization and candidate generation respecting 16-char limit and allowed characters.
 */
final class NameUtil {

    private static final Pattern NAME_ALLOWED = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final int NAME_MAX = 16;

    private NameUtil() {}

    static String sanitizeBaseName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) s = "Player";
        s = sanitizeToAllowed(s);
        if (s.isEmpty()) s = "Player";
        return truncateToMax(s, NAME_MAX);
    }

    static String buildCandidate(String base, int attempt) {
        if (attempt <= 1) return truncateToMax(base, NAME_MAX);
        final String suffix = "_" + attempt;
        final int keep = Math.max(1, NAME_MAX - suffix.length());
        final String kept = truncateToMax(base, keep);
        return sanitizeToAllowed(kept + suffix);
    }

    static String getEffectiveNameFromResult(String requested, DisguiseResponse result) {
        // We cannot read-back the exact applied nickname; return requested best-effort.
        return requested;
    }

    private static String sanitizeToAllowed(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') out.append(c);
            else out.append('_');
        }
        String r = out.toString().replaceAll("_+", "_");
        r = r.replaceAll("^_+", "").replaceAll("_+$", "");
        if (r.isEmpty()) r = "_";
        if (!NAME_ALLOWED.matcher(r).matches()) {
            r = r.replaceAll("[^a-zA-Z0-9_]", "_");
            if (r.isEmpty()) r = "_";
        }
        return r;
    }

    private static String truncateToMax(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}