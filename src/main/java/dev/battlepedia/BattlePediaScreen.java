package dev.battlepedia;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** First-pass navigation shell. The world remains visible through the translucent UI. */
public final class BattlePediaScreen extends Screen {
    private static final int PANEL = 0xCC14182D;
    private static final int SIDEBAR = 0xDD211B3A;
    private static final int PURPLE = 0xFFA879FF;
    private static final int BLUE = 0xFF78D5FF;
    private String section = "Pets";
    private String rarity = "All";
    private static final String[] CATEGORIES = {"Pets", "Pet Items", "Weapons", "Armor", "Equipment", "Artifacts", "Enchantments", "Crafting", "Settings"};
    private static final String[] RARITIES = {"All", "Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
    public BattlePediaScreen() { super(Text.literal("BattlePedia")); }
    @Override protected void init() {
        int x = Math.max(8, width / 12), y = Math.max(8, height / 12);
        int sidebarWidth = Math.max(100, (width - x * 2) / 5);
        int i = 0;
        for (String category : CATEGORIES) {
            final String selected = category;
            addDrawableChild(ButtonWidget.builder(Text.literal(category), b -> { section = selected; clearAndInit(); })
                .dimensions(x + 8, y + 34 + (i++) * 23, sidebarWidth - 16, 20).build());
        }
        int rx = x + sidebarWidth + 16;
        int available = width - x - rx - 12;
        int buttonWidth = Math.max(44, available / RARITIES.length - 3);
        for (int j = 0; j < RARITIES.length; j++) {
            String selected = RARITIES[j];
            addDrawableChild(ButtonWidget.builder(Text.literal(selected), b -> { rarity = selected; clearAndInit(); })
                .dimensions(rx + j * (buttonWidth + 3), y + 54, buttonWidth, 20).build());
        }
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = Math.max(8, width / 12), y = Math.max(8, height / 12);
        int w = width - x * 2, h = height - y * 2, side = Math.max(100, w / 5);
        context.fill(x, y, x + w, y + h, PANEL);
        context.fill(x, y, x + side, y + h, SIDEBAR);
        context.fill(x, y, x + w, y + 3, PURPLE);
        context.drawTextWithShadow(textRenderer, "BattlePedia", x + 12, y + 12, PURPLE);
        context.drawTextWithShadow(textRenderer, section + "  /  " + rarity, x + side + 16, y + 15, BLUE);
        context.drawTextWithShadow(textRenderer, "Pet database importer coming next", x + side + 16, y + 94, 0xFFE4E7FF);
        context.drawTextWithShadow(textRenderer, "Transparent UI prototype  -  ESC to close", x + side + 16, y + h - 20, 0xFF9CA9C8);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
}
