package dev.battlepedia.data;

import java.util.List;

/** Labels and original menu icons are transcribed from the BattlePet Wiki GUI dump. */
public final class WikiCategories {
    public record Category(String group, String name, String icon) {}
    public static final List<Category> ALL = List.of(
        new Category("Weapons", "Melee Weapons", "minecraft:diamond_sword"),
        new Category("Weapons", "Ranged Weapons", "minecraft:bow"),
        new Category("Weapons", "Magic Weapons", "minecraft:blaze_rod"),
        new Category("Weapons", "Tank Weapons", "minecraft:diamond_axe"),
        new Category("Weapons", "Executioner Weapons", "minecraft:diamond_hoe"),
        new Category("Armor", "Helmets", "minecraft:diamond_helmet"),
        new Category("Armor", "Chestplates", "minecraft:diamond_chestplate"),
        new Category("Armor", "Leggings", "minecraft:diamond_leggings"),
        new Category("Armor", "Boots", "minecraft:diamond_boots"),
        new Category("Resources", "Pickaxes & Drills", "minecraft:diamond_pickaxe"),
        new Category("Resources", "Fishing Rods", "minecraft:fishing_rod"),
        new Category("Upgrades", "Enchantments", "minecraft:enchanted_book"),
        new Category("Upgrades", "Artifacts", "minecraft:magma_cream"),
        new Category("Upgrades", "Power Cores", "minecraft:end_portal_frame"),
        new Category("Upgrades", "Brews", "minecraft:potion"),
        new Category("Resources", "Crafting Ingredients", "minecraft:diamond"),
        new Category("Pets", "Pets", "minecraft:creeper_spawn_egg"),
        new Category("Pets", "Pet Items", "minecraft:bone"),
        new Category("Equipment", "Necklaces", "minecraft:leather_helmet"),
        new Category("Equipment", "Cloaks", "minecraft:leather_chestplate"),
        new Category("Equipment", "Belts", "minecraft:leather_leggings"),
        new Category("Equipment", "Gloves", "minecraft:leather_boots"),
        new Category("Other", "Unsorted", "minecraft:book")
    );
    private WikiCategories() {}
    public static String fromTitle(String title) {
        if (title == null) return "Unsorted";
        String lower = title.toLowerCase(java.util.Locale.ROOT);
        for (Category c : ALL) {
            if (!c.name.equals("Unsorted") && lower.contains(c.name.toLowerCase(java.util.Locale.ROOT))) return c.name;
        }
        if (lower.contains("pet item")) return "Pet Items";
        if (lower.contains("pet catalog") || lower.contains("pets")) return "Pets";
        if (lower.contains("helmet")) return "Helmets";
        if (lower.contains("chestplate")) return "Chestplates";
        if (lower.contains("legging")) return "Leggings";
        if (lower.contains("boots")) return "Boots";
        return "Unsorted";
    }
    public static String icon(String name) {
        return ALL.stream().filter(c -> c.name.equals(name)).map(Category::icon).findFirst().orElse("minecraft:book");
    }
}
