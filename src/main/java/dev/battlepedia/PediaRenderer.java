package dev.battlepedia;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.battlepedia.data.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Translucent purple/blue encyclopedia UI. All panels, rarity tabs, navigation,
 * favorites and card click areas are drawn in-game, not screenshot overlays.
 */
final class PediaRenderer {
    private PediaRenderer() {}

    private static final int PURPLE = 0xFFBF83FF, BLUE = 0xFF65DBF8, GOLD = 0xFFFABF59;
    private static final int WHITE = 0xFFF3F6FF, MUTED = 0xFF9BAAC4, GREEN = 0xFF70F0B3;
    private static final int SURFACE = 0xF50D1428, TOP = 0xF41B1E37, SIDEBAR = 0xF0171932;
    private static final int TILE = 0xF5232B45, FRAME = 0xFF67519A, EDGE = 0xFF394563;
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    record Nav(String label, String icon, String section, String group, int indent) {}
    record Ability(String name, List<String> description) {}
    record LoreInfo(String type, String source, List<String> stats, List<Ability> abilities) {}

    static int detailCenterRight(BattlePediaScreen s) {
        return detailColumns(s)[3];
    }

    /** [portraitX, portraitW, centerX, rightPanelX, centerW, rightW] */
    private static int[] detailColumns(BattlePediaScreen s) {
        int width = s.mainWidth, portrait = Math.max(118, Math.min(170, (int)(width * 0.255)));
        if (width < 465) {
            int centerX = s.mainX + portrait + 8;
            return new int[]{s.mainX, portrait, centerX, s.mainX + width, Math.max(82, s.mainX + width - centerX), 0};
        }
        int remaining = width - portrait - 16;
        int centerWidth = Math.max(160, (int)(remaining * 0.55));
        int rightX = s.mainX + portrait + 8 + centerWidth + 8;
        return new int[]{s.mainX, portrait, s.mainX + portrait + 8, rightX, centerWidth, s.mainX + width - rightX};
    }

    static void draw(BattlePediaScreen s, DrawContext ctx, int mouseX, int mouseY) {
        // Transparent on purpose: the world is visible through the dashboard.
        ctx.fill(0, 0, s.width, s.height, 0xC2080C1A);
        panel(ctx, s.left, s.top, s.widthPx, s.heightPx, SURFACE, 0xFF4D356E);
        ctx.fill(s.left + 1, s.top + 1, s.left + s.widthPx - 1, s.top + 4, PURPLE);
        for (int i=0;i<9;i++) ctx.fill(s.mainX+i*8,s.top+5,s.mainX+i*8+3,s.top+7,0x554A90CF);
        sidebar(s, ctx, mouseX, mouseY);
        if (s.view == BattlePediaScreen.View.DETAIL) {
            detail(s, ctx, mouseX, mouseY);
        } else if (s.view == BattlePediaScreen.View.HOME) {
            home(s, ctx, mouseX, mouseY);
        } else {
            browser(s, ctx, mouseX, mouseY);
        }
        TextRenderer font = s.getTextRenderer();
        text(ctx, font, truncate(font, s.notice, Math.max(90, s.mainWidth - 20)),
            s.mainX + 7, s.top + s.heightPx - 16, MUTED);
    }

    private static void sidebar(BattlePediaScreen s, DrawContext ctx, int mx, int my) {
        TextRenderer font = s.getTextRenderer();
        int x = s.left, y = s.top, w = s.sideWidth, h = s.heightPx;
        panel(ctx, x + 1, y + 5, w - 2, h - 6, SIDEBAR, 0xFF574580);
        ctx.fill(x + w - 2, y + 8, x + w, y + h - 5, 0xFF694589);
        drawNative(ctx, vanilla("minecraft:nether_star"), x + 13, y + 17);
        text(ctx, font, truncate(font, "BattlePedia", w - 46), x + 35, y + 14, WHITE);
        text(ctx, font, "THE BATTLEPET COMPENDIUM", x + 13, y + 33, BLUE);
        ctx.fill(x + 13, y + 49, x + w - 13, y + 50, 0xFF66558D);
        // Search field is positioned over the following space by Screen.init().
        ctx.fill(x + 12, y + 59, x + w - 12, y + 84, 0xF60B1024);
        border(ctx, x + 12, y + 59, w - 24, 25, 0xFF7864A7);

        List<Nav> nav = new ArrayList<>();
        nav.add(new Nav("Home", "minecraft:compass", "Home", "", 0));
        nav.add(new Nav("Pets", "minecraft:creeper_spawn_egg", "Pets", "", 0));
        nav.add(new Nav("Pet Items", "minecraft:bone", "Pet Items", "", 0));
        nav.add(new Nav("Favorites", "minecraft:nether_star", "Favorites", "", 0));
        addGroup(nav, "Weapons", "minecraft:diamond_sword", s.expandedGroup,
            List.of("Melee Weapons", "Ranged Weapons", "Magic Weapons", "Tank Weapons", "Executioner Weapons"));
        addGroup(nav, "Armor", "minecraft:diamond_chestplate", s.expandedGroup,
            List.of("Helmets", "Chestplates", "Leggings", "Boots"));
        addGroup(nav, "Equipment", "minecraft:leather_chestplate", s.expandedGroup,
            List.of("Necklaces", "Cloaks", "Belts", "Gloves"));
        addGroup(nav, "Upgrades", "minecraft:enchanted_book", s.expandedGroup,
            List.of("Enchantments", "Artifacts", "Power Cores", "Brews"));
        addGroup(nav, "Resources", "minecraft:diamond_pickaxe", s.expandedGroup,
            List.of("Pickaxes & Drills", "Fishing Rods", "Crafting Ingredients"));
        nav.add(new Nav("Unsorted", "minecraft:book", "Unsorted", "", 0));

        int navY = y + 94, navBottom = y + h - 46, rowH = 27;
        int capacity = Math.max(1, (navBottom - navY) / rowH);
        s.navScroll = Math.min(s.navScroll, Math.max(0, nav.size() - capacity));
        ctx.enableScissor(x + 4, navY, x + w - 4, navBottom);
        for (int i = s.navScroll; i < nav.size() && i < s.navScroll + capacity; i++) {
            final Nav item = nav.get(i);
            int drawY = navY + (i - s.navScroll) * rowH;
            int tx = x + 9 + item.indent * 11, tw = w - 18 - item.indent * 11;
            boolean group = !item.group.isEmpty();
            boolean active = item.section.equals(s.section) && !group;
            boolean hover = inside(mx, my, tx, drawY, tw, 23);
            if (active) {
                panel(ctx, tx, drawY, tw, 23, 0xB15A307F, PURPLE);
                ctx.fill(tx, drawY + 3, tx + 3, drawY + 20, PURPLE);
            } else if (hover) {
                panel(ctx, tx, drawY, tw, 23, 0x99463863, 0xFF604578);
            } else if (group) {
                ctx.fill(tx, drawY + 22, tx + tw, drawY + 23, 0x663E345F);
            }
            drawNative(ctx, vanilla(item.icon), tx + 5, drawY + 4);
            int textColor = active ? WHITE : (group ? 0xFFDFDBFF : MUTED);
            text(ctx, font, truncate(font, item.label, tw - (group ? 37 : 24)),
                tx + 24, drawY + 7, textColor);
            if (group) {
                text(ctx, font, s.expandedGroup.equals(item.group) ? "-" : "+", tx + tw - 12, drawY + 7, PURPLE);
                s.hit(tx, drawY, tw, 23, () -> {
                    s.expandedGroup = s.expandedGroup.equals(item.group) ? "" : item.group;
                    s.navScroll = Math.min(s.navScroll, Math.max(0, nav.size() - capacity));
                });
            } else {
                s.hit(tx, drawY, tw, 23, () -> s.chooseSection(item.section));
            }
        }
        ctx.disableScissor();
        if (nav.size() > capacity) {
            text(ctx, font, (s.navScroll + 1) + "-" + Math.min(nav.size(), s.navScroll + capacity)
                + " / " + nav.size() + "  (scroll)", x + 12, navBottom + 5, MUTED);
        }
        ctx.fill(x + 9, y + h - 27, x + w - 9, y + h - 26, 0xFF4E3C6A);
        text(ctx, font, "v0.3  |  Offline wiki  |  Fabric", x + 13, y + h - 19, BLUE);
    }

    private static void addGroup(List<Nav> nav, String group, String icon, String expanded, List<String> children) {
        nav.add(new Nav(group, icon, group, group, 0));
        if (group.equals(expanded)) for (String child : children) {
            nav.add(new Nav(child, WikiCategories.icon(child), child, "", 1));
        }
    }

    private static void header(BattlePediaScreen s, DrawContext ctx, String breadcrumb, boolean back, int mx, int my) {
        int x = s.mainX, y = s.top + 7, w = s.mainWidth;
        TextRenderer font = s.getTextRenderer();
        panel(ctx, x, y, w, 49, TOP, EDGE);
        ctx.fill(x+1,y+1,x+4,y+48,PURPLE);
        if (back) {
            action(s, ctx, x + 7, y + 11, 31, 27, "<", () -> {
                s.view = BattlePediaScreen.View.BROWSE;
                s.abilityScroll = 0;
                s.loreScroll = 0;
            }, mx, my, false);
            text(ctx, font, truncate(font, breadcrumb, w - 183), x + 47, y + 21, WHITE);
        } else {
            text(ctx, font, truncate(font, breadcrumb, w - 183), x + 14, y + 21, WHITE);
        }
        if (s.view == BattlePediaScreen.View.DETAIL) {
            int fw = Math.min(99, Math.max(75, w / 6));
            action(s, ctx, x + w - fw - 8, y + 12, fw, 25,
                s.isFavorite() ? "* Favorited" : "* Favorite", s::toggleFavorite, mx, my, s.isFavorite());
        } else {
            action(s, ctx, x + w - 115, y + 12, 51, 25, "Reload", s::reload, mx, my, false);
            action(s, ctx, x + w - 60, y + 12, 53, 25, "Import", s::importTextDump, mx, my, false);
        }
    }

    private static void home(BattlePediaScreen s, DrawContext ctx, int mx, int my) {
        header(s, ctx, "Home  /  Welcome to BattlePedia", false, mx, my);
        int x = s.mainX, y = s.bodyTop, w = s.mainWidth, h = s.bodyBottom - y;
        panel(ctx, x, y, w, h, 0xB3181B33, FRAME);
        TextRenderer font = s.getTextRenderer();
        text(ctx, font, "Explore the BattlePet Wiki", x + 15, y + 17, WHITE);
        text(ctx, font, "Browse pets, compare rarities and read their original abilities.", x + 15, y + 36, MUTED);
        int count = CatalogStore.INSTANCE.size();
        text(ctx, font, count + " catalog variants loaded locally", x + 15, y + 59, BLUE);
        List<String> featured = List.of("Pets", "Pet Items", "Melee Weapons", "Armor", "Artifacts", "Enchantments");
        int cardW = Math.max(90, (w - 40) / 3);
        for (int i = 0; i < featured.size(); i++) {
            String label = featured.get(i);
            int cx = x + 12 + i % 3 * (cardW + 7), cy = y + 84 + i / 3 * 69;
            panel(ctx, cx, cy, cardW, 62, TILE, inside(mx,my,cx,cy,cardW,62) ? PURPLE : EDGE);
            drawNative(ctx, vanilla(WikiCategories.icon(label.equals("Armor") ? "Chestplates" : label)),
                cx + 9, cy + 9);
            text(ctx, font, truncate(font, label, cardW - 13), cx + 8, cy + 39, WHITE);
            s.hit(cx, cy, cardW, 62, () -> s.chooseSection(label.equals("Armor") ? "Chestplates" : label));
        }
    }

    private static void browser(BattlePediaScreen s, DrawContext ctx, int mx, int my) {
        List<CatalogEntry> items = s.browserEntries();
        String title = s.query.isBlank() ? s.section : "Search / " + s.query;
        header(s, ctx, title + "   /   " + items.size() + " ITEMS", false, mx, my);
        int x = s.mainX, y = s.bodyTop, w = s.mainWidth, h = s.bodyBottom - y;
        TextRenderer font = s.getTextRenderer();
        panel(ctx, x, y, w, h, 0xEB151D35, EDGE);
        ctx.fill(x+1,y+1,x+w-1,y+3,0xFF387CBA);
        text(ctx, font, s.section.equals("Pets") ? "YOUR PET COMPENDIUM" : s.section.toUpperCase(Locale.ROOT) + "  /  INDEX",
            x + 15, y + 13, BLUE);
        String hint = items.size() + " indexed entries | Open a card for stats, abilities and original tooltip";
        text(ctx,font,truncate(font,hint,w-30),x+15,y+32,MUTED);
        ctx.fill(x+13,y+49,x+w-13,y+50,0xFF33425F);

        int cols = w >= 760 ? 3 : w >= 390 ? 2 : 1;
        int gap = 10, cardWidth = (w - 30 - gap * (cols - 1)) / cols;
        int cardHeight = 102;
        int listTop = y + 60, listBottom = y + h - 19;
        int totalRows = (items.size()+cols-1)/cols;
        int visibleRows = Math.max(1, (listBottom-listTop+gap)/(cardHeight+gap));
        s.browserScroll = Math.min(s.browserScroll, Math.max(0,totalRows-visibleRows));
        ctx.enableScissor(x+5,listTop,x+w-5,listBottom);
        for(int row=s.browserScroll;row<totalRows && row<s.browserScroll+visibleRows;row++){
            for(int col=0;col<cols;col++){
                int index=row*cols+col;
                if(index>=items.size())break;
                CatalogEntry entry=items.get(index);
                int cx=x+15+col*(cardWidth+gap);
                int cy=listTop+(row-s.browserScroll)*(cardHeight+gap);
                boolean hover=inside(mx,my,cx,cy,cardWidth,cardHeight);
                int rarity=rarityColor(BattlePediaScreen.resolvedRarity(entry));
                panel(ctx,cx,cy,cardWidth,cardHeight,hover?0xFF35415D:TILE,hover?PURPLE:EDGE);
                ctx.fill(cx+1,cy+1,cx+cardWidth-1,cy+3,rarity);
                panel(ctx,cx+11,cy+13,48,48,0xF70E172C,hover?0xFF7063A1:0xFF46546F);
                // Render genuine ItemStacks at an exact 2x pixel scale: no blurry thumbnails.
                drawLarge(ctx,icon(entry),cx+19,cy+21,2);
                String name=entry.baseName();
                int nameX=cx+69;
                text(ctx,font,truncate(font,name,Math.max(40,cardWidth-82)),nameX,cy+20,WHITE);
                text(ctx,font,BattlePediaScreen.resolvedRarity(entry).toUpperCase(Locale.ROOT),
                    nameX,cy+38,rarity);
                ctx.fill(cx+10,cy+69,cx+cardWidth-10,cy+70,0xAA41506C);
                String stat=firstStat(entry);
                text(ctx,font,truncate(font,stat.isBlank()?"VIEW ITEM DETAILS":stat,cardWidth-35),cx+11,cy+80,
                    stat.isBlank()?MUTED:GREEN);
                text(ctx,font,">",cx+cardWidth-18,cy+80,hover?WHITE:BLUE);
                s.hit(cx,cy,cardWidth,cardHeight,()->s.chooseEntry(entry));
            }
        }
        ctx.disableScissor();
        if(items.isEmpty()){
            text(ctx,font,"Nothing indexed here yet",x+20,listTop+20,WHITE);
            text(ctx,font,"Open /wiki and press O, or search for another item.",x+20,listTop+39,MUTED);
        }
        String info = items.size()+" items  /  "+Math.max(1,totalRows)+" rows  /  Scroll to browse";
        text(ctx,font,truncate(font,info,w-24),x+15,y+h-13,MUTED);
    }

    private static String firstStat(CatalogEntry e) {
        if(e.lore==null)return "";
        for(String key:List.of("Damage:","Health:","Defense:","Strength:","Ferocity:","Intelligence:","Crit Damage:","Magic Find:"))
            for(String line:e.lore) if(line.startsWith(key))return line;
        return "";
    }

    private static void detail(BattlePediaScreen s, DrawContext ctx, int mx, int my) {
        CatalogEntry current = s.activeVariant();
        if (current == null) {
            s.view = BattlePediaScreen.View.BROWSE;
            browser(s,ctx,mx,my);
            return;
        }
        header(s, ctx, current.category + "  >  " + current.baseName(), true, mx, my);
        int[] cols = detailColumns(s);
        int px = cols[0], pw = cols[1], centerX = cols[2], rightX = cols[3], midW = cols[4], rightW = cols[5];
        int top = s.bodyTop, bottom = s.bodyBottom;
        portrait(s,ctx,current,px,top,pw,bottom - top);
        center(s,ctx,current,centerX,top,midW,bottom - top,mx,my);
        if (rightW > 0) {
            tooltip(s,ctx,current,rightX,top,rightW,bottom - top);
        } else {
            // Compact windows can still read the complete tooltip via a bottom strip.
            text(ctx, s.getTextRenderer(), "Original lore also appears in the ability descriptions.",
                centerX + 6, bottom - 14, MUTED);
        }
    }

    private static void portrait(BattlePediaScreen s, DrawContext ctx, CatalogEntry current,
                                 int x, int y, int w, int h) {
        TextRenderer font = s.getTextRenderer();
        int rarity=rarityColor(BattlePediaScreen.resolvedRarity(current));
        int heroH=Math.max(150,Math.min(220,(int)(h*0.58)));
        panel(ctx,x,y,w,heroH,0xEF17172F,FRAME);
        ctx.fill(x+1,y+1,x+w-1,y+4,rarity);
        text(ctx,font,truncate(font,current.category.toUpperCase(Locale.ROOT),w-20),
            x+10,y+14,BLUE);
        // Crisp native ItemStack portrait, framed separately from the background.
        int well=Math.min(105,Math.max(69,w-32));
        int wellX=x+(w-well)/2,wellY=y+33;
        panel(ctx,wellX,wellY,well,well,0xF40C1328,0xFF76589A);
        for(int i=1;i<=4;i++)
            border(ctx,wellX+i*3,wellY+i*3,well-i*6,well-i*6,0x222F86E0);
        int zoom=Math.min(3,Math.max(2,(well-16)/16));
        int px=wellX+(well-zoom*16)/2,py=wellY+(well-zoom*16)/2;
        drawLarge(ctx,icon(current),px,py,zoom);
        int labelY=Math.min(y+heroH-48,wellY+well+10);
        String name=truncate(font,current.baseName(),w-14);
        text(ctx,font,name,x+(w-font.getWidth(name))/2,labelY,WHITE);
        String rarityName=BattlePediaScreen.resolvedRarity(current);
        text(ctx,font,rarityName.toUpperCase(Locale.ROOT),x+10,y+heroH-20,rarity);
        text(ctx,font,"WIKI ENTRY",x+w-Math.min(w-10,font.getWidth("WIKI ENTRY")+10),y+heroH-20,MUTED);

        int infoTop=y+heroH+8,infoH=h-heroH-8;
        if(infoH<45)return;
        panel(ctx,x,infoTop,w,infoH,0xEA1B213B,EDGE);
        LoreInfo info=analyze(current);
        text(ctx,font,current.category.equals("Pets")?"PET PROFILE":"ITEM PROFILE",x+10,infoTop+13,PURPLE);
        ctx.fill(x+10,infoTop+30,x+w-10,infoTop+31,0xFF44516B);
        text(ctx,font,"CLASSIFICATION",x+10,infoTop+39,MUTED);
        text(ctx,font,truncate(font,info.type(),w-20),x+10,infoTop+55,WHITE);
        if(infoH>93){
            ctx.fill(x+10,infoTop+79,x+w-10,infoTop+80,0x9944516B);
            text(ctx,font,"WHERE TO GET IT",x+10,infoTop+88,MUTED);
            List<String> lines=wrap(font,info.source(),Math.max(50,w-20));
            for(int i=0;i<Math.min(3,lines.size());i++)
                if(infoTop+104+i*13<infoTop+infoH-5)
                    text(ctx,font,lines.get(i),x+10,infoTop+104+i*13,BLUE);
        }
        if(infoH>166){
            ctx.fill(x+10,infoTop+154,x+w-10,infoTop+155,0x9944516B);
            text(ctx,font,"LOCAL OFFLINE REFERENCE",x+10,infoTop+163,GREEN);
        }
    }

    private static void center(BattlePediaScreen s,DrawContext ctx,CatalogEntry current,
                               int x,int y,int w,int h,int mx,int my){
        TextRenderer font=s.getTextRenderer();
        List<CatalogEntry> variants=s.selectedVariants();
        List<String> rarities=new ArrayList<>();
        for(CatalogEntry e:variants){
            String r=BattlePediaScreen.resolvedRarity(e);
            if(!rarities.contains(r))rarities.add(r);
        }
        int perRow=Math.max(1,w/74);
        int rowCount=(rarities.size()+perRow-1)/perRow;
        int rarityHeight=29+rowCount*27+6;
        panel(ctx,x,y,w,rarityHeight,0xCF1B1D3A,FRAME);
        text(ctx,font,"RARITY",x+10,y+11,WHITE);
        int tabW=(w-16-(perRow-1)*4)/perRow;
        for(int i=0;i<rarities.size();i++){
            String rarity=rarities.get(i);
            boolean selected=rarity.equals(BattlePediaScreen.resolvedRarity(current));
            int cx=x+8+(i%perRow)*(tabW+4),cy=y+29+(i/perRow)*27;
            int color=rarityColor(rarity);
            panel(ctx,cx,cy,tabW,23,selected?0xB250396F:0xAB1B2038,selected?color:0xFF534760);
            if(selected)ctx.fill(cx+1,cy+1,cx+tabW-1,cy+3,color);
            String label=truncate(font,rarity,tabW-6);
            text(ctx,font,label,cx+(tabW-font.getWidth(label))/2,cy+8,selected?color:MUTED);
            s.hit(cx,cy,tabW,23,()->s.chooseRarity(rarity));
        }

        LoreInfo info=analyze(current);
        int innerY=y+rarityHeight+7;
        int statRows=Math.min(5,info.stats().size());
        int statsHeight=33+Math.max(1,statRows)*20;
        int available=h-rarityHeight-7;
        if (statsHeight>available/2)statsHeight=Math.max(48,available/2);
        panel(ctx,x,innerY,w,statsHeight,0xD21C1C37,FRAME);
        String title=BattlePediaScreen.resolvedRarity(current).toUpperCase(Locale.ROOT)+" VARIANT";
        text(ctx,font,truncate(font,title,w-20),x+10,innerY+11,rarityColor(BattlePediaScreen.resolvedRarity(current)));
        ctx.fill(x+9,innerY+26,x+w-9,innerY+27,0x99554872);
        int sy=innerY+33;
        if(info.stats().isEmpty()) {
            text(ctx,font,"No combat stats recorded",x+12,sy,MUTED);
        }else{
            for(int i=0;i<statRows&&sy+i*20+10<innerY+statsHeight;i++){
                String stat=info.stats().get(i);
                int colon=stat.indexOf(':');
                if(colon>0){
                    String name=stat.substring(0,colon).trim();
                    String value=stat.substring(colon+1).trim();
                    text(ctx,font,truncate(font,name,w/2),x+11,sy+i*20,WHITE);
                    int off=font.getWidth(value);
                    text(ctx,font,truncate(font,value,w/2),x+w-10-off,sy+i*20,GREEN);
                }else text(ctx,font,truncate(font,stat,w-20),x+11,sy+i*20,WHITE);
                if(i+1<statRows)ctx.fill(x+9,sy+i*20+13,x+w-9,sy+i*20+14,0x553D3E5A);
            }
        }
        int abilityY=innerY+statsHeight+7;
        int abilityHeight=y+h-abilityY;
        if(abilityHeight<30)return;
        panel(ctx,x,abilityY,w,abilityHeight,0xC31A1B35,FRAME);
        text(ctx,font,(current.category.equals("Pets")?"PET ABILITIES":"ABILITIES")+"  ("+info.abilities().size()+")",x+11,abilityY+10,PURPLE);
        ctx.fill(x+9,abilityY+26,x+w-9,abilityY+27,0xFF604074);
        int startY=abilityY+33, clippingBottom=abilityY+abilityHeight-7;
        int contentY=startY-s.abilityScroll*18;
        ctx.enableScissor(x+3,startY,x+w-3,clippingBottom);
        if(info.abilities().isEmpty())text(ctx,font,"No active abilities recorded",x+10,startY+8,MUTED);
        for(Ability ability:info.abilities()){
            List<String> lines=new ArrayList<>();
            for(String part:ability.description()) {
                if(part.isBlank()){lines.add("");continue;}
                lines.addAll(wrap(font,part,w-28));
            }
            int bodyHeight=0;
            for(String line:lines)bodyHeight+=line.isBlank()?6:12;
            int cardH=28+Math.max(12,bodyHeight)+7;
            if(contentY+cardH>=startY && contentY<clippingBottom){
                panel(ctx,x+8,contentY,w-16,cardH,0xCE242343,0xFF555075);
                ctx.fill(x+9,contentY+1,x+12,contentY+cardH-1,PURPLE);
                String name=truncate(font,ability.name(),w-32);
                text(ctx,font,name,x+18,contentY+9,GOLD);
                int tY=contentY+26;
                for(String line:lines){
                    if(line.isBlank()){tY+=6;continue;}
                    int color=line.toLowerCase(Locale.ROOT).contains("cannot be stacked")?PURPLE:MUTED;
                    text(ctx,font,line,x+18,tY,color);
                    tY+=12;
                }
            }
            contentY+=cardH+7;
        }
        ctx.disableScissor();
    }

    private static void tooltip(BattlePediaScreen s,DrawContext ctx,CatalogEntry current,int x,int y,int w,int h){
        TextRenderer font=s.getTextRenderer();
        panel(ctx,x,y,w,h,0xE1151429,0xFF9E5CD5);
        ctx.fill(x+1,y+1,x+w-1,y+3,PURPLE);
        drawNative(ctx,vanilla("minecraft:book"),x+9,y+12);
        text(ctx,font,truncate(font,"MINECRAFT TOOLTIP",w-40),x+30,y+16,WHITE);
        ctx.fill(x+8,y+35,x+w-8,y+36,0xFF694285);
        int pad=x+10,clipTop=y+42,clipBottom=y+h-8;
        int offset=s.loreScroll*16, currentY=clipTop-offset;
        ctx.enableScissor(x+5,clipTop,x+w-5,clipBottom);
        String name=current.baseName();
        text(ctx,font,truncate(font,name,w-20),pad,currentY,rarityColor(BattlePediaScreen.resolvedRarity(current)));
        currentY+=18;
        for(String sourceLine:current.lore==null?List.<String>of():current.lore){
            if(sourceLine.isBlank()){currentY+=7;continue;}
            List<String> segments=wrap(font,sourceLine,w-20);
            int color=tooltipColor(sourceLine);
            for(String line:segments){
                if(currentY+11>clipTop && currentY<clipBottom)
                    text(ctx,font,line,pad,currentY,color);
                currentY+=12;
            }
        }
        ctx.disableScissor();
    }

    private static int tooltipColor(String s){
        String value=s.toLowerCase(Locale.ROOT);
        if(value.contains("cannot be stacked"))return PURPLE;
        if(value.startsWith("pet ability:")||value.startsWith("item ability:")||value.startsWith("summon ability:"))return GOLD;
        if(value.startsWith("source npc")||value.contains("click to view"))return BLUE;
        if(value.contains("requires")||value.contains("hotbar")||value.contains("activate its abilities"))return 0xFFFFE078;
        if(value.matches("^(common|uncommon|rare|epic|legendary|mythic)( guild)? pet.*"))
            return rarityColor(CatalogEntry.detectRarity(value));
        if(value.matches("^[a-z][a-z0-9' %/-]{2,35}:\\s*[+\\-]?[0-9].*$"))return GREEN;
        return MUTED;
    }

    private static LoreInfo analyze(CatalogEntry entry) {
        String type=entry.category.equals("Pets")?"Pet":"Item",source="See original tooltip";
        List<String> stats=new ArrayList<>();
        List<Ability> abilities=new ArrayList<>();
        String currentName=null;
        List<String> abilityLines=new ArrayList<>();
        List<String> lore=entry.lore==null?List.of():entry.lore;
        boolean readingSource=false;
        boolean footer=false;
        for(String raw:lore){
            String line=raw==null?"":raw.trim();
            String lower=line.toLowerCase(Locale.ROOT);
            if(line.endsWith(" Pet")&&!line.toUpperCase(Locale.ROOT).matches("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)( GUILD)? PET$")
                && type.equals("Pet"))type=line;
            if(!entry.category.equals("Pets") && line.toUpperCase(Locale.ROOT).matches("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|UNIQUE|SPECIAL)( DUNGEON)? [A-Z ]+$")) {
                String[] fragments=line.trim().split(" ");
                type=fragments[fragments.length-1].substring(0,1).toUpperCase(Locale.ROOT)
                    +fragments[fragments.length-1].substring(1).toLowerCase(Locale.ROOT);
            }
            if(lower.startsWith("source npc:") || lower.startsWith("dropped by:") || lower.startsWith("slayer sources:")) {
                readingSource=true;footer=true;
                String remainder=line.substring(line.indexOf(':')+1).trim();
                if(!remainder.isEmpty())source=remainder;
                continue;
            }
            if(readingSource && !line.isEmpty() && line.startsWith("-")) {
                source=line.replaceFirst("^-\\s*","").trim();readingSource=false;continue;
            }
            if(lower.startsWith("pet ability:") || lower.startsWith("item ability:") || lower.startsWith("summon ability:")){
                footer=false;
                if(currentName!=null)abilities.add(new Ability(currentName,new ArrayList<>(abilityLines)));
                currentName=line.substring(line.indexOf(':')+1).trim();
                abilityLines.clear();
                continue;
            }
            if(lower.startsWith("this pet needs")||lower.startsWith("requires ")||
               lower.startsWith("click to view")||lower.matches("^(common|uncommon|rare|epic|legendary|mythic)( guild)? pet.*")){
                footer=true;
            }
            if(footer)continue;
            if(currentName!=null) {
                if(!line.isBlank()||!abilityLines.isEmpty())abilityLines.add(line);
            } else if(line.matches("^[A-Za-z][A-Za-z0-9' %/()-]{1,40}:\\s*[+\\-]?[0-9].*$")) {
                stats.add(line);
            }
        }
        if(currentName!=null)abilities.add(new Ability(currentName,new ArrayList<>(abilityLines)));
        // Remove excess blank trailing lines in ability cards.
        List<Ability> cleaned=new ArrayList<>();
        for(Ability ability:abilities) {
            List<String> lines=new ArrayList<>(ability.description());
            while(!lines.isEmpty()&&lines.get(lines.size()-1).isBlank())lines.remove(lines.size()-1);
            cleaned.add(new Ability(ability.name(),lines));
        }
        return new LoreInfo(type,source,stats,cleaned);
    }

    private static void action(BattlePediaScreen s,DrawContext ctx,int x,int y,int w,int h,
                               String label,Runnable callback,int mx,int my,boolean selected){
        boolean hover=inside(mx,my,x,y,w,h);
        panel(ctx,x,y,w,h,selected?0xB3523277:hover?0xE24E386D:0xAB2D264B,
            selected?GOLD:hover?PURPLE:FRAME);
        TextRenderer font=s.getTextRenderer();
        String shortText=truncate(font,label,w-8);
        text(ctx,font,shortText,x+(w-font.getWidth(shortText))/2,y+(h-8)/2,selected?GOLD:WHITE);
        s.hit(x,y,w,h,callback);
    }

    private static boolean inside(int mx,int my,int x,int y,int w,int h) {
        return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }

    private static void panel(DrawContext ctx,int x,int y,int w,int h,int fill,int line){
        if(w<2||h<2)return;
        ctx.fill(x,y,x+w,y+h,fill);
        border(ctx,x,y,w,h,line);
    }

    private static void border(DrawContext ctx,int x,int y,int w,int h,int line){
        ctx.fill(x,y,x+w,y+1,line);
        ctx.fill(x,y+h-1,x+w,y+h,line);
        ctx.fill(x,y,x+1,y+h,line);
        ctx.fill(x+w-1,y,x+w,y+h,line);
    }

    private static void text(DrawContext ctx,TextRenderer font,String str,int x,int y,int color){
        ctx.drawText(font,str,x,y,color,false);
    }

    private static String truncate(TextRenderer font,String str,int pixels){
        if(str==null)return "";
        if(font.getWidth(str)<=pixels)return str;
        if(pixels<font.getWidth(".."))return "";
        return font.trimToWidth(str,pixels-font.getWidth(".."))+"..";
    }

    private static List<String> wrap(TextRenderer font,String str,int pixels){
        List<String> result=new ArrayList<>();
        if(str==null||str.isBlank())return result;
        StringBuilder line=new StringBuilder();
        for(String word:str.split("\\s+")){
            String trial=line.isEmpty()?word:line+" "+word;
            if(font.getWidth(trial)>pixels && !line.isEmpty()){
                result.add(truncate(font,line.toString(),pixels));
                line=new StringBuilder(word);
            } else line=new StringBuilder(trial);
        }
        if(!line.isEmpty())result.add(truncate(font,line.toString(),pixels));
        return result;
    }

    private static int rarityColor(String r){
        return switch(r){
            case "Common" -> 0xFFAAAAAA;
            case "Uncommon" -> 0xFF55FF55;
            case "Rare" -> 0xFF5555FF;
            case "Epic" -> 0xFFCC55FF;
            case "Legendary" -> 0xFFFFAA00;
            case "Mythic" -> 0xFFFF55FF;
            case "Unique" -> 0xFF75DBFF;
            case "Special" -> 0xFFFF6B8F;
            default -> MUTED;
        };
    }

    private static ItemStack vanilla(String itemId){
        try{
            var item=Registries.ITEM.get(Identifier.of(itemId));
            return item==null?new ItemStack(Items.BOOK):new ItemStack(item);
        }catch(Exception ignored){return new ItemStack(Items.BOOK);}
    }

    static ItemStack icon(CatalogEntry entry){
        String key=entry.itemId+"|"+(entry.stackJson==null?"":entry.stackJson);
        ItemStack found=ICONS.get(key);
        if(found!=null)return found;
        ItemStack stack=ItemStack.EMPTY;
        MinecraftClient client=MinecraftClient.getInstance();
        if(entry.stackJson!=null&&!entry.stackJson.isBlank()&&client.world!=null){
            try{
                var ops=RegistryOps.of(JsonOps.INSTANCE,client.world.getRegistryManager());
                stack=ItemStack.CODEC.parse(ops,JsonParser.parseString(entry.stackJson))
                    .result().orElse(ItemStack.EMPTY);
            }catch(Exception ignored){}
        }
        if(stack.isEmpty()){
            stack=vanilla(entry.itemId);
            if(entry.stackJson!=null&&!entry.stackJson.isBlank())return stack;
        }
        ICONS.put(key,stack);
        return stack;
    }

    private static void drawNative(DrawContext ctx,ItemStack item,int x,int y){
        ctx.drawItem(item,x,y);
    }

    private static void drawLarge(DrawContext ctx,ItemStack item,int x,int y,int factor){
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x,y);
        ctx.getMatrices().scale((float)factor,(float)factor);
        ctx.drawItem(item,0,0);
        ctx.getMatrices().popMatrix();
    }
}
