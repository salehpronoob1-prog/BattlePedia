package dev.battlepedia.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A distinct wiki entry variant. Raw lore is authoritative; no mechanics are invented. */
public final class CatalogEntry {
    public String name = "";
    public String category = "Unsorted";
    public String rarity = "Unknown";
    public String itemId = "minecraft:book";
    /** Full Minecraft ItemStack CODEC JSON, including head profiles, when captured in-game. */
    public String stackJson = "";
    public List<String> lore = new ArrayList<>();
    public String source = "";
    public int originalSlot = -1;
    public int count = 1;
    /** Zero for older records whose capture time is unknown. */
    public long capturedAt = 0;

    private static final Pattern COLOR = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern RARITY = Pattern.compile("(?i)\\b(Mythic|Legendary|Epic|Rare|Uncommon|Common|Unique|Special)\\b");

    public String key() {
        return category.toLowerCase(Locale.ROOT) + "|" + baseName().toLowerCase(Locale.ROOT) + "|" + rarity.toLowerCase(Locale.ROOT);
    }

    /** Keep multiple rarities on one detail page, never collapse distinct categories. */
    public String baseName() {
        String clean = COLOR.matcher(name == null ? "" : name).replaceAll("").trim();
        return clean.replaceFirst("(?i)^(mythic|legendary|epic|rare|uncommon|common)\\s+", "")
            .replaceFirst("(?i)\\s*\\[(mythic|legendary|epic|rare|uncommon|common)\\]$", "").trim();
    }

    public static String detectRarity(String text) {
        if (text == null) return "Unknown";
        Matcher m = RARITY.matcher(text);
        return m.find() ? m.group(1).substring(0,1).toUpperCase(Locale.ROOT)
            + m.group(1).substring(1).toLowerCase(Locale.ROOT) : "Unknown";
    }

    public boolean matches(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return true;
        if ((name + " " + category + " " + rarity + " " + itemId).toLowerCase(Locale.ROOT).contains(q)) return true;
        if (lore != null) for (String s : lore) if (s != null && s.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }
}
