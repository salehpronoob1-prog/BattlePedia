package dev.battlepedia;

import dev.battlepedia.data.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * BattlePedia's full-screen dashboard. The left navigation is ALWAYS accessible:
 * Pets and Pet Items are pinned above expandable wiki category groups.
 */
public final class BattlePediaScreen extends Screen {
    enum View { HOME, BROWSE, DETAIL }

    record Hit(int left, int top, int right, int bottom, Runnable action) {
        boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
    }

    private final List<Hit> targets = new ArrayList<>();
    final FavoritesStore favorites = new FavoritesStore();
    View view = View.BROWSE;
    String section = "Pets";
    String expandedGroup = "";
    String query = "";
    String selectedKey = "";
    String selectedRarity = "";
    String notice = "Browse the BattlePet encyclopedia";
    int navScroll = 0, browserScroll = 0, abilityScroll = 0, loreScroll = 0;
    int left, top, widthPx, heightPx, sideWidth, mainX, mainWidth, headerHeight, bodyTop, bodyBottom;
    private TextFieldWidget search;

    public BattlePediaScreen() {
        super(Text.literal("BattlePedia"));
        favorites.load();
    }

    @Override
    protected void init() {
        left = 10;
        top = 10;
        widthPx = Math.max(250, width - 20);
        heightPx = Math.max(170, height - 20);
        sideWidth = Math.max(115, Math.min(174, widthPx / 5));
        mainX = left + sideWidth + 10;
        mainWidth = left + widthPx - mainX - 9;
        headerHeight = 49;
        bodyTop = top + headerHeight + 6;
        bodyBottom = top + heightPx - 25;

        // Search stays in the sidebar rather than stealing the catalog header.
        search = new TextFieldWidget(textRenderer, left + 10, top + 56,
            sideWidth - 20, 19, Text.literal("Search BattlePedia"));
        search.setPlaceholder(Text.literal("Search items..."));
        search.setMaxLength(128);
        search.setText(query);
        search.setChangedListener(value -> {
            if (!query.equals(value)) {
                query = value;
                browserScroll = 0;
                if (!query.isBlank()) view = View.BROWSE;
            }
        });
        addDrawableChild(search);
    }

    void hit(int x, int y, int w, int h, Runnable action) {
        if (w > 0 && h > 0) targets.add(new Hit(x, y, x + w, y + h, action));
    }

    void chooseSection(String value) {
        section = value;
        view = value.equals("Home") ? View.HOME : View.BROWSE;
        selectedKey = "";
        selectedRarity = "";
        browserScroll = 0;
        abilityScroll = 0;
        loreScroll = 0;
        query = "";
        if (search != null) search.setText("");
    }

    void chooseEntry(CatalogEntry entry) {
        selectedKey = entry.category + "|" + entry.baseName();
        selectedRarity = resolvedRarity(entry);
        view = View.DETAIL;
        abilityScroll = 0;
        loreScroll = 0;
    }

    void chooseRarity(String rarity) {
        selectedRarity = rarity;
        abilityScroll = 0;
        loreScroll = 0;
    }

    static String resolvedRarity(CatalogEntry entry) {
        if (entry.rarity != null && !entry.rarity.equals("Unknown")) return entry.rarity;
        if (entry.lore != null) for (int i = entry.lore.size() - 1; i >= 0; i--) {
            String line = entry.lore.get(i).trim().toUpperCase(Locale.ROOT);
            if (line.matches("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)( GUILD)? PET.*$")) {
                return CatalogEntry.detectRarity(line);
            }
        }
        return "Unknown";
    }

    private static int rarityOrder(String r) {
        return switch (r) {
            case "Common" -> 1;
            case "Uncommon" -> 2;
            case "Rare" -> 3;
            case "Epic" -> 4;
            case "Legendary" -> 5;
            case "Mythic" -> 6;
            default -> 0;
        };
    }

    String identity(CatalogEntry entry) {
        return entry.category + "|" + entry.baseName();
    }

    List<CatalogEntry> browserEntries() {
        Map<String, CatalogEntry> unique = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CatalogEntry e : CatalogStore.INSTANCE.all()) {
            if (!query.isBlank()) {
                if (!e.matches(query)) continue;
            } else if (section.equals("Favorites")) {
                if (!favorites.has(identity(e))) continue;
            } else if (!section.equals(e.category)) {
                continue;
            }
            String key = identity(e);
            CatalogEntry previous = unique.get(key);
            if (previous == null || rarityOrder(resolvedRarity(e)) > rarityOrder(resolvedRarity(previous))) {
                unique.put(key, e);
            }
        }
        return new ArrayList<>(unique.values());
    }

    List<CatalogEntry> selectedVariants() {
        if (selectedKey.isBlank()) return List.of();
        List<CatalogEntry> result = new ArrayList<>();
        for (CatalogEntry e : CatalogStore.INSTANCE.all()) {
            if (selectedKey.equals(identity(e))) result.add(e);
        }
        result.sort(Comparator.comparingInt(e -> rarityOrder(resolvedRarity(e))));
        return result;
    }

    CatalogEntry activeVariant() {
        List<CatalogEntry> options = selectedVariants();
        if (options.isEmpty()) return null;
        if (!selectedRarity.isBlank()) for (CatalogEntry e : options) {
            if (resolvedRarity(e).equals(selectedRarity)) return e;
        }
        return options.get(options.size() - 1);
    }

    boolean isFavorite() { return !selectedKey.isBlank() && favorites.has(selectedKey); }

    void toggleFavorite() {
        if (selectedKey.isBlank()) return;
        favorites.toggle(selectedKey);
        notice = favorites.has(selectedKey) ? "Added to favorites" : "Removed from favorites";
    }

    void reload() {
        CatalogStore.INSTANCE.load();
        notice = "Loaded " + CatalogStore.INSTANCE.size() + " entries";
    }

    void importTextDump() {
        notice = "Importing pets.txt...";
        CompletableFuture.supplyAsync(() -> {
            try {
                List<CatalogEntry> records = DumpParser.read(CatalogStore.importFile());
                int inserted = CatalogStore.INSTANCE.merge(records);
                return "Imported " + records.size() + " variants (" + inserted + " new)";
            } catch (Exception error) {
                return "Import failed: " + error.getMessage();
            }
        }).thenAccept(message -> {
            if (client != null) client.execute(() -> notice = message);
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        targets.clear();
        PediaRenderer.draw(this, context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        int mx = (int) click.x(), my = (int) click.y();
        // A reverse traversal honors buttons rendered above other elements.
        for (int i = targets.size() - 1; i >= 0; i--) {
            Hit target = targets.get(i);
            if (target.contains(mx, my)) {
                target.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int mx = (int) mouseX, my = (int) mouseY;
        int amount = vertical > 0 ? -2 : 2;
        if (mx < left + sideWidth) {
            navScroll = Math.max(0, navScroll + amount);
            return true;
        }
        if (view == View.BROWSE && mx >= mainX && my >= bodyTop && my < bodyBottom) {
            browserScroll = Math.max(0, browserScroll + amount);
            return true;
        }
        if (view == View.DETAIL && my >= bodyTop && my < bodyBottom) {
            int centerRight = PediaRenderer.detailCenterRight(this);
            if (mx < centerRight) abilityScroll = Math.max(0, abilityScroll + amount);
            else loreScroll = Math.max(0, loreScroll + amount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
