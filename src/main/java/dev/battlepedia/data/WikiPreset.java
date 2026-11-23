package dev.battlepedia.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Immutable wiki reference imported from the 82-page BattlePet GUI dump. */
public final class WikiPreset {
    private static final List<CatalogEntry> RECORDS = load();
    private static final Map<String,List<CatalogEntry>> BY_NAME = index();

    private WikiPreset() {}

    private static List<CatalogEntry> load() {
        List<CatalogEntry> list = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String path = "/data/battlepedia/wiki-" + i + ".tsv";
            try (var stream = WikiPreset.class.getResourceAsStream(path)) {
                if (stream == null) continue;
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] columns = line.split("\\t", 5);
                        if (columns.length < 5) continue;
                        CatalogEntry entry = new CatalogEntry();
                        entry.category = columns[0]; entry.name = columns[1];
                        entry.rarity = columns[2]; entry.itemId = columns[3];
                        entry.source = "BattlePet Wiki / " + entry.category;
                        entry.lore = new ArrayList<>(Arrays.asList(columns[4].split("\\u001f", -1)));
                        list.add(entry);
                    }
                }
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger("BattlePedia").error("Wiki preset load failed: " + path, ex);
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static Map<String,List<CatalogEntry>> index() {
        Map<String,List<CatalogEntry>> map = new HashMap<>();
        for (CatalogEntry e : RECORDS)
            map.computeIfAbsent(normal(e.name), ignored -> new ArrayList<>()).add(e);
        return map;
    }

    public static List<CatalogEntry> records() { return RECORDS; }
    public static String normal(String s) {
        return (s == null ? "" : s.replaceAll("(?i)§[0-9a-fk-or]", "").trim()).toLowerCase(Locale.ROOT);
    }

    /** Returns Unsorted on genuinely ambiguous matches, never guesses a category from the item material. */
    public static String category(String name, String itemId, List<String> lore, String preferred) {
        List<CatalogEntry> candidates = BY_NAME.getOrDefault(normal(name), List.of());
        if (candidates.isEmpty()) return "Unsorted";
        List<CatalogEntry> sameMaterial = candidates.stream()
            .filter(e -> e.itemId.equals(itemId)).toList();
        if (!sameMaterial.isEmpty()) candidates = sameMaterial;
        Set<String> categories = candidates.stream().map(e -> e.category).collect(Collectors.toSet());
        if (categories.size() == 1) return categories.iterator().next();

        int best = -1;
        Set<String> tied = new HashSet<>();
        Set<String> given = (lore == null ? List.<String>of() : lore).stream()
            .map(WikiPreset::normal).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        for (CatalogEntry e : candidates) {
            int score = 0;
            for (String part : e.lore) if (!part.isBlank() && given.contains(normal(part))) score++;
            if (score > best) { best = score; tied.clear(); tied.add(e.category); }
            else if (score == best) tied.add(e.category);
        }
        if (best >= 2 && tied.size() == 1) return tied.iterator().next();
        if (preferred != null && categories.contains(preferred)) return preferred;
        return "Unsorted";
    }
}
