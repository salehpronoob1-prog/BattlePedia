package dev.battlepedia.data;

import java.util.*;
import java.util.regex.*;

/** The wiki's actual type footer outranks names and the currently selected tab. */
public final class LoreRules {
    private static final Pattern FOOTER = Pattern.compile("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|UNIQUE|SPECIAL)(?: [A-Z][A-Z ]*)?$");
    private static final Pattern PET = Pattern.compile("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|UNIQUE|SPECIAL)(?: GUILD)? PET$");
    private LoreRules() {}

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
    }
    public static boolean isPet(List<String> lore) {
        if (lore == null) return false;
        for (String l : lore) {
            String line=clean(l);
            if (PET.matcher(line.toUpperCase(Locale.ROOT)).matches()
                || line.startsWith("This pet needs to be kept in your")) return true;
        }
        return false;
    }
    public static String rarity(List<String> lore) {
        if (lore != null) for (int i=lore.size()-1;i>=0;i--) {
            Matcher m=FOOTER.matcher(clean(lore.get(i)));
            if(m.matches()) return CatalogEntry.detectRarity(m.group(1));
        }
        return "Unknown";
    }
    public static boolean isWikiPage(String title) {
        String t=clean(title).toLowerCase(Locale.ROOT);
        if(t.equals("wiki database") || t.startsWith("wiki database (page:") ||
           t.equals("pet catalog") || t.startsWith("pet catalog (page:")) return true;
        for (WikiCategories.Category c:WikiCategories.ALL) {
            if(c.name().equals("Unsorted"))continue;
            String n=c.name().toLowerCase(Locale.ROOT);
            if(t.equals(n+" wiki") || t.startsWith(n+" wiki (page:") ||
               t.equals("wiki: "+n) || t.startsWith("wiki: "+n+" (page:"))return true;
        }
        return false;
    }
    public static boolean isMenuCapture(CatalogEntry e) {
        return clean(e.source).equalsIgnoreCase("Sack Selection Menu");
    }
    public static CatalogEntry copy(CatalogEntry e,String category) {
        CatalogEntry c=new CatalogEntry();
        c.name=e.name;c.category=category;c.rarity=e.rarity;c.itemId=e.itemId;
        c.stackJson=e.stackJson;c.lore=e.lore;c.source=e.source;c.originalSlot=e.originalSlot;c.count=e.count;
        return c;
    }

    /** Keep all exact wiki category matches; do not guess when shared weapons have identical lore. */
    public static List<CatalogEntry> normalize(CatalogEntry e) {
        if(isMenuCapture(e)) return List.of();
        String r=rarity(e.lore);
        if(!r.equals("Unknown"))e.rarity=r;
        if(isPet(e.lore)){
            e.category="Pets";
            return List.of(e);
        }
        if("Pets".equals(e.category)||"Unsorted".equals(e.category)){
            Set<String> exact=WikiPreset.exactCategories(e.name,e.itemId,e.lore);
            if(!exact.isEmpty())return exact.stream().map(c->copy(e,c)).toList();
            String matched=WikiPreset.category(e.name,e.itemId,e.lore,"Unsorted");
            e.category=matched.equals("Pets")?"Unsorted":matched;
        }
        return List.of(e);
    }
}
