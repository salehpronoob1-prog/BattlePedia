package dev.battlepedia;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.battlepedia.data.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real, dynamic BattlePedia UI. Visual reference: fantasy three-column encyclopedia. */
final class PediaRenderer {
    private PediaRenderer() {}
    private static final int WHITE=PediaChrome.INK, MUTED=PediaChrome.DIM, PURPLE=PediaChrome.VIOLET;
    private static final int BLUE=PediaChrome.BLUE, GOLD=PediaChrome.GOLD, GREEN=PediaChrome.GREEN;
    private static final Map<String,ItemStack> ICONS=new HashMap<>();
    private static final Pattern HIGHLIGHT=Pattern.compile("(?i)(\\b(?:Farming Fortune|Bonus Pest Chance|Crit Damage|Crit Chance|Magic Find|Ferocity|Enchanted|Strength|Health|Intelligence|Defense|Damage|Mana|Healing)\\b|[+\\-]?\\d+(?:\\.\\d+)?%?|\\b(?:PASSIVE|ACTIVE|RIGHT CLICK|LEFT CLICK)\\b)");

    record Nav(String label,String icon,String section,String group,int indent) {}
    record Ability(String name,List<String> description) {}
    record LoreInfo(String type,String source,List<String> stats,List<Ability> abilities,boolean craftable) {}

    static boolean compactDetail(BattlePediaScreen s) { return s.mainWidth<540; }
    static int detailCenterRight(BattlePediaScreen s) { return detailColumns(s)[3]; }

    /** [portraitX, portraitW, centerX, tooltipX, centerW, tooltipW] */
    private static int[] detailColumns(BattlePediaScreen s){
        int w=s.mainWidth;
        if(compactDetail(s)){
            int pw=Math.max(104,Math.min(132,w/3));
            int cx=s.mainX+pw+8;
            return new int[]{s.mainX,pw,cx,s.mainX+w,Math.max(95,s.mainX+w-cx),0};
        }
        int pw=Math.max(134,Math.min(202,(int)(w*0.225)));
        int remaining=w-pw-18;
        int cw=Math.max(215,(int)(remaining*0.55));
        int cx=s.mainX+pw+9,rx=cx+cw+9;
        return new int[]{s.mainX,pw,cx,rx,cw,Math.max(0,s.mainX+w-rx)};
    }

    static void draw(BattlePediaScreen s,DrawContext ctx,int mouseX,int mouseY){
        ctx.fill(0,0,s.width,s.height,0xFF0B0B20);
        PediaChrome.banner(ctx,s.width,s.top+11);
        // The decorative sky remains visible above the interface, like the reference screenshot.
        ctx.fill(0,s.top+11,s.width,s.height,0xEE0C0C24);
        sidebar(s,ctx,mouseX,mouseY);
        switch(s.view){
            case HOME -> home(s,ctx,mouseX,mouseY);
            case DETAIL -> detail(s,ctx,mouseX,mouseY);
            case BROWSE -> browser(s,ctx,mouseX,mouseY);
        }
        // Notices appear unobtrusively rather than occupying a permanent footer.
        if(s.notice!=null && !s.notice.equals("Browse the BattlePet encyclopedia")){
            text(ctx,s.getTextRenderer(),crop(s.getTextRenderer(),s.notice,Math.max(70,s.mainWidth-18)),
                s.mainX+10,s.bodyBottom-12,0xFFB8A6CF);
        }
    }

    private static void sidebar(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        TextRenderer font=s.getTextRenderer();
        int x=s.left,y=s.top,w=s.sideWidth,h=s.heightPx;
        // Brand is outside the nav card and sits on the illustrated sky.
        PediaChrome.paw(ctx,x+5,7,PURPLE);
        text(ctx,font,crop(font,"BattlePedia",w-43),x+33,7,WHITE);
        text(ctx,font,crop(font,"BattlePet Encyclopedia",w-43),x+33,20,0xFFD4C5EF);
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xF415112A,0xFF8F5BB0,true);
        PediaChrome.pixelFrame(ctx,x+8,y+10,w-16,32,0xF80C0F22,0xFF5C4C88,false);
        drawNative(ctx,vanilla("minecraft:spyglass"),x+12,y+18);
        PediaChrome.div(ctx,x+9,y+49,w-18);

        List<Nav> nav=new ArrayList<>();
        nav.add(new Nav("Home","minecraft:oak_door","Home","",0));
        nav.add(new Nav("Pets","minecraft:creeper_spawn_egg","Pets","",0));
        nav.add(new Nav("Pet Items","minecraft:bone","Pet Items","",0));
        addGroup(nav,"Weapons","minecraft:diamond_sword",s.expandedGroup,
            List.of("Melee Weapons","Ranged Weapons","Magic Weapons","Tank Weapons","Executioner Weapons"));
        addGroup(nav,"Armor","minecraft:diamond_chestplate",s.expandedGroup,
            List.of("Helmets","Chestplates","Leggings","Boots"));
        addGroup(nav,"Equipment","minecraft:leather_chestplate",s.expandedGroup,
            List.of("Necklaces","Cloaks","Belts","Gloves"));
        nav.add(new Nav("Artifacts","minecraft:magma_cream","Artifacts","",0));
        nav.add(new Nav("Enchantments","minecraft:enchanted_book","Enchantments","",0));
        nav.add(new Nav("Power Cores","minecraft:end_portal_frame","Power Cores","",0));
        nav.add(new Nav("Brews","minecraft:potion","Brews","",0));
        nav.add(new Nav("Crafting Ingredients","minecraft:diamond","Crafting Ingredients","",0));
        addGroup(nav,"Resources","minecraft:diamond_pickaxe",s.expandedGroup,
            List.of("Pickaxes & Drills","Fishing Rods"));
        nav.add(new Nav("Unsorted","minecraft:book","Unsorted","",0));

        int navY=y+57,navBottom=y+h-48,rowH=25;
        int capacity=Math.max(1,(navBottom-navY)/rowH);
        s.navScroll=Math.min(s.navScroll,Math.max(0,nav.size()-capacity));
        ctx.enableScissor(x+4,navY,x+w-4,navBottom);
        for(int i=s.navScroll;i<nav.size()&&i<s.navScroll+capacity;i++){
            Nav n=nav.get(i);
            int cy=navY+(i-s.navScroll)*rowH;
            int nx=x+7+n.indent*11,nw=w-14-n.indent*11;
            boolean group=!n.group.isBlank(),active=n.section.equals(s.section)&&!group;
            boolean hover=inside(mx,my,nx,cy,nw,23);
            if(active) {
                PediaChrome.pixelFrame(ctx,nx,cy,nw,23,0xF45A287F,0xFFE475FB,true);
                ctx.fill(nx+2,cy+4,nx+4,cy+19,0xFFF2B5FF);
            }else if(hover){
                PediaChrome.pixelFrame(ctx,nx,cy,nw,23,0xE3372453,0xFF76549E,false);
            }else if(n.indent==0){
                ctx.fill(nx+2,cy+23,nx+nw-2,cy+24,0x44463664);
            }
            drawNative(ctx,vanilla(n.icon),nx+5,cy+4);
            int labelColor=active?WHITE:n.indent>0?0xFFBAAACC:0xFFE7E2F1;
            text(ctx,font,crop(font,n.label,nw-(group?45:31)),nx+25,cy+7,labelColor);
            if(group)text(ctx,font,n.group.equals(s.expandedGroup)?"-":"+",nx+nw-13,cy+7,PURPLE);
            if(group)s.hit(nx,cy,nw,23,()->{
                s.expandedGroup=s.expandedGroup.equals(n.group)?"":n.group;
                s.navScroll=0;
            });
            else s.hit(nx,cy,nw,23,()->s.chooseSection(n.section));
        }
        ctx.disableScissor();
        if(nav.size()>capacity)text(ctx,font,(s.navScroll+1)+"-"+Math.min(nav.size(),s.navScroll+capacity)
            +" / "+nav.size(),x+12,navBottom+2,0xFF8C7AA9);
        PediaChrome.div(ctx,x+9,y+h-43,w-18);
        boolean fav=s.section.equals("Favorites"),favHover=inside(mx,my,x+8,y+h-36,w-16,29);
        if(fav||favHover)PediaChrome.pixelFrame(ctx,x+8,y+h-36,w-16,29,
            fav?0xEB543173:0xE42D1C47,fav?PURPLE:PediaChrome.LINE,fav);
        drawNative(ctx,vanilla("minecraft:nether_star"),x+18,y+h-30);
        text(ctx,font,"Favorites",x+40,y+h-25,fav?WHITE:0xFFF5E2FD);
        s.hit(x+8,y+h-36,w-16,29,()->s.chooseSection("Favorites"));
    }

    private static void addGroup(List<Nav> nav,String group,String icon,String expanded,List<String> children){
        nav.add(new Nav(group,icon,group,group,0));
        if(group.equals(expanded))
            for(String child:children)nav.add(new Nav(child,WikiCategories.icon(child),child,"",1));
    }

    private static void header(BattlePediaScreen s,DrawContext ctx,String crumb,boolean back,int mx,int my){
        int x=s.mainX,y=s.top+1,w=s.mainWidth;
        TextRenderer font=s.getTextRenderer();
        PediaChrome.titleStrip(ctx,x,y,w,31);
        int textX=x+13;
        if(back){
            action(s,ctx,x+10,y+5,30,21,"<",()->{
                s.view=BattlePediaScreen.View.BROWSE;s.abilityScroll=0;s.loreScroll=0;
            },mx,my,false,false);
            textX=x+47;
        }
        int textWidth=Math.max(30,w-(back?195:160));
        text(ctx,font,crop(font,crumb,textWidth),textX,y+11,WHITE);
        int maxX=x+w-31;
        action(s,ctx,maxX,y+5,22,21,s.maximized?"[]":"[ ]",s::toggleMaximized,mx,my,false,false);
        if(s.view==BattlePediaScreen.View.DETAIL){
            action(s,ctx,x+w-157,y+5,118,21,s.isFavorite()?"* Favorited":"* Add to Favorites",
                s::toggleFavorite,mx,my,s.isFavorite(),true);
        }else{
            action(s,ctx,x+w-119,y+5,48,21,"Reload",s::reload,mx,my,false,false);
            action(s,ctx,x+w-68,y+5,32,21,"Import",s::importTextDump,mx,my,false,false);
        }
    }

    private static void action(BattlePediaScreen s,DrawContext ctx,int x,int y,int w,int h,
                               String label,Runnable run,int mx,int my,boolean selected,boolean gold){
        boolean hover=inside(mx,my,x,y,w,h);
        int edge=gold?(selected?GOLD:0xFFAD80C8):(selected?PURPLE:hover?0xFFBD8DFF:0xFF6B5996);
        PediaChrome.pixelFrame(ctx,x,y,w,h,selected?0xF84E225B:hover?0xF840265A:0xF82A2041,edge,hover||selected);
        String str=crop(s.getTextRenderer(),label,w-8);
        text(ctx,s.getTextRenderer(),str,x+(w-s.getTextRenderer().getWidth(str))/2,y+(h-9)/2,
            selected&&gold?GOLD:WHITE);
        s.hit(x,y,w,h,run);
    }

    private static void home(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        header(s,ctx,"Home  >  Explore the BattlePet Encyclopedia",false,mx,my);
        int x=s.mainX,y=s.bodyTop,w=s.mainWidth,h=s.bodyBottom-y;
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xEE12132F,PURPLE,true);
        PediaChrome.softGlow(ctx,x+w/2,y+70,60,0xFF7531B8);
        text(ctx,s.getTextRenderer(),"WELCOME TO BATTLEPEDIA",x+19,y+19,GOLD);
        text(ctx,s.getTextRenderer(),crop(s.getTextRenderer(),"Explore pets, magical equipment and original wiki lore",w-38),
            x+19,y+36,WHITE);
        text(ctx,s.getTextRenderer(),CatalogStore.INSTANCE.size()+" catalog variants loaded locally",
            x+19,y+54,GREEN);
        List<String> categories=List.of("Pets","Pet Items","Melee Weapons","Ranged Weapons","Artifacts",
            "Enchantments","Chestplates","Power Cores","Crafting Ingredients");
        int cols=w>490?3:2,gap=9,cardW=(w-38-(cols-1)*gap)/cols;
        int y0=y+81;
        ctx.enableScissor(x+7,y0,x+w-7,y+h-12);
        for(int i=0;i<categories.size();i++){
            String c=categories.get(i);
            int tx=x+19+(i%cols)*(cardW+gap);
            int ty=y0+(i/cols)*57;
            boolean hover=inside(mx,my,tx,ty,cardW,49);
            PediaChrome.pixelFrame(ctx,tx,ty,cardW,49,hover?0xF83D255B:0xF5211C3D,
                hover?PURPLE:0xFF594775,hover);
            drawLarge(ctx,vanilla(WikiCategories.icon(c)),tx+11,ty+9,2);
            text(ctx,s.getTextRenderer(),crop(s.getTextRenderer(),c,cardW-57),
                tx+48,ty+12,WHITE);
            int count=CatalogStore.INSTANCE.sectionCount(c);
            text(ctx,s.getTextRenderer(),count+" entries",tx+48,ty+27,MUTED);
            s.hit(tx,ty,cardW,49,()->s.chooseSection(c));
        }
        ctx.disableScissor();
    }

    private static void browser(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        List<CatalogEntry> items=s.browserEntries();
        String crumb=s.query.isBlank()?s.section:"Search  >  "+s.query;
        header(s,ctx,crumb+"  /  "+items.size()+" entries",false,mx,my);
        int x=s.mainX,y=s.bodyTop,w=s.mainWidth,h=s.bodyBottom-y;
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xEE14132C,0xFF6D4F9C,false);
        text(ctx,s.getTextRenderer(),s.section.toUpperCase(Locale.ROOT)+" COLLECTION",x+14,y+12,PURPLE);
        text(ctx,s.getTextRenderer(),crop(s.getTextRenderer(),
            "Select an item to inspect stats, rarity variants and original wiki lore",w-28),x+14,y+27,MUTED);
        PediaChrome.div(ctx,x+12,y+45,w-24);
        int cols=w>=670?3:w>=358?2:1,gap=8;
        int cardW=(w-28-(cols-1)*gap)/cols,cardH=84;
        int listTop=y+53,listBottom=y+h-13,rows=(items.size()+cols-1)/cols;
        int visible=Math.max(1,(listBottom-listTop+gap)/(cardH+gap));
        s.browserScroll=Math.min(s.browserScroll,Math.max(0,rows-visible));
        ctx.enableScissor(x+5,listTop,x+w-5,listBottom);
        for(int row=s.browserScroll;row<rows&&row<s.browserScroll+visible;row++){
            for(int col=0;col<cols;col++){
                int i=row*cols+col;if(i>=items.size())break;
                CatalogEntry entry=items.get(i);
                int cx=x+14+col*(cardW+gap),cy=listTop+(row-s.browserScroll)*(cardH+gap);
                boolean hover=inside(mx,my,cx,cy,cardW,cardH);
                int rarity=PediaChrome.rarity(BattlePediaScreen.resolvedRarity(entry));
                PediaChrome.pixelFrame(ctx,cx,cy,cardW,cardH,
                    hover?0xF43E244E:0xF5211C3B,hover?PURPLE:0xFF59457D,hover);
                ctx.fill(cx+4,cy+3,cx+cardW-4,cy+5,rarity);
                PediaChrome.softGlow(ctx,cx+39,cy+33,22,rarity);
                drawLarge(ctx,icon(entry),cx+23,cy+17,2);
                String name=crop(s.getTextRenderer(),entry.baseName(),cardW-78);
                text(ctx,s.getTextRenderer(),name,cx+71,cy+19,WHITE);
                String rarityLabel=BattlePediaScreen.resolvedRarity(entry);
                text(ctx,s.getTextRenderer(),rarityLabel,cx+71,cy+35,rarity);
                PediaChrome.div(ctx,cx+9,cy+61,cardW-18);
                String stat=firstStat(entry);
                text(ctx,s.getTextRenderer(),crop(s.getTextRenderer(),stat.isBlank()?"VIEW ORIGINAL ENTRY":stat,cardW-32),
                    cx+10,cy+69,stat.isBlank()?MUTED:GREEN);
                text(ctx,s.getTextRenderer(),">",cx+cardW-16,cy+69,PURPLE);
                s.hit(cx,cy,cardW,cardH,()->s.chooseEntry(entry));
            }
        }
        ctx.disableScissor();
        if(items.isEmpty()){
            text(ctx,s.getTextRenderer(),"No entries found.",x+23,listTop+17,WHITE);
            text(ctx,s.getTextRenderer(),crop(s.getTextRenderer(),"Try another category or capture /wiki using O.",w-46),
                x+23,listTop+37,MUTED);
        }
    }

    private static String firstStat(CatalogEntry e){
        if(e.lore==null)return "";
        for(String key:List.of("Damage:","Health:","Strength:","Ferocity:","Defense:","Intelligence:","Magic Find:"))
            for(String l:e.lore)if(l.startsWith(key))return l;
        return "";
    }

    private static void detail(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        CatalogEntry entry=s.activeVariant();
        if(entry==null){s.view=BattlePediaScreen.View.BROWSE;browser(s,ctx,mx,my);return;}
        header(s,ctx,entry.category+"  >  "+entry.baseName(),true,mx,my);
        int[] columns=detailColumns(s);
        int px=columns[0],pw=columns[1],cx=columns[2],rx=columns[3],cw=columns[4],rw=columns[5];
        int y=s.bodyTop,h=s.bodyBottom-y;
        portrait(s,ctx,entry,px,y,pw,h);
        if(compactDetail(s)){
            int tabH=26;
            action(s,ctx,cx,y,cw/2-2,22,"Stats / Skills",()->s.showLoreOnCompact(false),mx,my,
                !s.mobileLore,false);
            action(s,ctx,cx+cw/2+2,y,cw-cw/2-2,22,"Tooltip",()->s.showLoreOnCompact(true),mx,my,
                s.mobileLore,false);
            if(s.mobileLore)tooltip(s,ctx,entry,cx,y+tabH,cw,h-tabH);
            else center(s,ctx,entry,cx,y+tabH,cw,h-tabH,mx,my);
        }else{
            center(s,ctx,entry,cx,y,cw,h,mx,my);
            tooltip(s,ctx,entry,rx,y,rw,h);
        }
    }

    private static void portrait(BattlePediaScreen s,DrawContext ctx,CatalogEntry e,
                                 int x,int y,int w,int h){
        TextRenderer font=s.getTextRenderer();
        int heroH=Math.max(148,Math.min(218,(int)(h*0.59)));
        int rarity=PediaChrome.rarity(BattlePediaScreen.resolvedRarity(e));
        PediaChrome.pixelFrame(ctx,x,y,w,heroH,0xF21D0E31,0xFFAA5FDC,true);
        ctx.fill(x+4,y+4,x+w-4,y+6,0x66382A66);
        PediaChrome.softGlow(ctx,x+w/2,y+heroH/2-16,Math.min(w/2-5,64),0xFF9B32D6);
        int scale=Math.max(3,Math.min(5,(w-26)/16));
        drawLarge(ctx,icon(e),x+(w-16*scale)/2,y+Math.max(28,(heroH-16*scale)/2-6),scale);
        String name=crop(font,e.baseName(),w-14);
        PediaChrome.center(ctx,font,name,x+w/2,y+heroH-43,WHITE);
        LoreInfo info=analyze(e);
        String type=crop(font,info.type(),w-16);
        PediaChrome.center(ctx,font,type,x+w/2,y+heroH-28,MUTED);
        PediaChrome.center(ctx,font,BattlePediaScreen.resolvedRarity(e).toUpperCase(Locale.ROOT),
            x+w/2,y+heroH-14,rarity);
        int infoY=y+heroH+9,infoH=h-heroH-9;
        if(infoH<34)return;
        PediaChrome.pixelFrame(ctx,x,infoY,w,infoH,0xF21A1733,0xFF665196,false);
        text(ctx,font,e.category.equals("Pets")?"PET DETAILS":"ITEM DETAILS",x+10,infoY+12,PURPLE);
        PediaChrome.div(ctx,x+9,infoY+27,w-18);
        int lineY=infoY+35;
        profileRow(ctx,font,"Type",info.type(),x+10,lineY,w-20,GREEN);
        lineY+=23;
        if(lineY<infoY+infoH-17){
            PediaChrome.div(ctx,x+10,lineY-4,w-20);
            profileRow(ctx,font,"Source",info.source(),x+10,lineY,w-20,GREEN);
            lineY+=30;
        }
        if(lineY<infoY+infoH-16){
            PediaChrome.div(ctx,x+10,lineY-5,w-20);
            profileRow(ctx,font,"Craftable",info.craftable()?"Yes":"Not recorded",
                x+10,lineY,w-20,info.craftable()?GREEN:MUTED);
        }
    }

    private static void profileRow(DrawContext ctx,TextRenderer font,String key,String value,
                                   int x,int y,int w,int color){
        text(ctx,font,key,x,y,MUTED);
        String shortValue=crop(font,value,Math.max(35,w-font.getWidth(key)-12));
        text(ctx,font,shortValue,x+w-font.getWidth(shortValue),y,color);
    }

    private static void center(BattlePediaScreen s,DrawContext ctx,CatalogEntry e,
                               int x,int y,int w,int h,int mx,int my){
        TextRenderer font=s.getTextRenderer();
        List<CatalogEntry> variants=s.selectedVariants();
        List<String> rarities=new ArrayList<>();
        for(CatalogEntry v:variants){
            String r=BattlePediaScreen.resolvedRarity(v);
            if(!rarities.contains(r))rarities.add(r);
        }
        int perRow=Math.max(1,Math.min(5,(w-18)/49));
        int rowCount=(rarities.size()+perRow-1)/perRow;
        int tabH=28+rowCount*26+6;
        PediaChrome.pixelFrame(ctx,x,y,w,tabH,0xF1181735,0xFF675495,false);
        text(ctx,font,"Rarity",x+10,y+11,WHITE);
        int tabW=(w-16-4*(perRow-1))/perRow;
        for(int i=0;i<rarities.size();i++){
            String rarity=rarities.get(i);
            int tx=x+8+(i%perRow)*(tabW+4),ty=y+27+(i/perRow)*26;
            boolean selected=rarity.equals(BattlePediaScreen.resolvedRarity(e));
            int color=PediaChrome.rarity(rarity);
            PediaChrome.pixelFrame(ctx,tx,ty,tabW,23,
                selected?0xF541244D:0xFA25233D,selected?color:0xFF68627D,selected);
            String label=crop(font,rarity,tabW-6);
            PediaChrome.center(ctx,font,label,tx+tabW/2,ty+8,selected?color:MUTED);
            s.hit(tx,ty,tabW,23,()->s.chooseRarity(rarity));
        }
        int bodyY=y+tabH+8,bodyH=h-tabH-8;
        if(bodyH<34)return;
        PediaChrome.pixelFrame(ctx,x,bodyY,w,bodyH,0xF0171531,0xFF77589F,false);
        int color=PediaChrome.rarity(BattlePediaScreen.resolvedRarity(e));
        String heading=BattlePediaScreen.resolvedRarity(e)+" Variant";
        PediaChrome.center(ctx,font,crop(font,heading,w-46),x+w/2,bodyY+14,color);
        ctx.fill(x+13,bodyY+18,x+29,bodyY+19,0xFFAC7864);
        ctx.fill(x+w-29,bodyY+18,x+w-13,bodyY+19,0xFFAC7864);
        PediaChrome.div(ctx,x+9,bodyY+35,w-18);

        LoreInfo info=analyze(e);
        int clipTop=bodyY+43,clipBottom=bodyY+bodyH-8;
        int padding=9,contentW=w-padding*2;
        List<String> statRows=info.stats();
        List<Ability> abilities=info.abilities();
        int lineSize=12;
        // Measure content before applying scroll to avoid endless empty scrolling.
        List<List<String>> wrapped=new ArrayList<>();
        int statsPixel=Math.max(0,statRows.size())*29;
        int abilityPixel=0;
        for(Ability a:abilities){
            List<String> lines=new ArrayList<>();
            for(String raw:a.description()){
                if(raw.isBlank()){lines.add("");continue;}
                lines.addAll(wrap(font,raw,contentW-49));
            }
            wrapped.add(lines);
            int bodyPx=0;for(String l:lines)bodyPx+=l.isBlank()?7:lineSize;
            abilityPixel+=Math.max(47,30+bodyPx)+8;
        }
        int total=statsPixel+12+abilityPixel;
        int maxScroll=Math.max(0,(total-(clipBottom-clipTop)+17)/18);
        s.abilityScroll=Math.min(s.abilityScroll,maxScroll);
        int cursor=clipTop-s.abilityScroll*18;
        ctx.enableScissor(x+5,clipTop,x+w-5,clipBottom);
        for(String stat:statRows){
            if(cursor+27>=clipTop&&cursor<clipBottom){
                PediaChrome.pixelFrame(ctx,x+padding,cursor,contentW,26,0xF2252242,0xFF625277,false);
                drawNative(ctx,vanilla(statIcon(stat)),x+padding+9,cursor+5);
                int colon=stat.indexOf(':');
                if(colon>0){
                    String key=stat.substring(0,colon+1),val=stat.substring(colon+1).trim();
                    text(ctx,font,crop(font,key,Math.max(35,contentW/2-18)),x+padding+32,cursor+8,WHITE);
                    String v=crop(font,val,Math.max(36,contentW/2-12));
                    text(ctx,font,v,x+padding+contentW-10-font.getWidth(v),cursor+8,GREEN);
                }else{
                    text(ctx,font,crop(font,stat,contentW-45),x+padding+32,cursor+8,WHITE);
                }
            }
            cursor+=29;
        }
        cursor+=statRows.isEmpty()?3:10;
        if(abilities.isEmpty() && statRows.isEmpty())
            text(ctx,font,"No combat stats or abilities recorded",x+15,cursor+4,MUTED);
        for(int i=0;i<abilities.size();i++){
            Ability a=abilities.get(i);
            List<String> lines=wrapped.get(i);
            int bodyPx=0;for(String l:lines)bodyPx+=l.isBlank()?7:lineSize;
            int cardH=Math.max(47,30+bodyPx);
            if(cursor+cardH>=clipTop&&cursor<clipBottom){
                PediaChrome.pixelFrame(ctx,x+padding,cursor,contentW,cardH,
                    0xF3231D3A,0xFF5A4B77,false);
                drawNative(ctx,vanilla(abilityIcon(a.name())),x+padding+8,cursor+11);
                text(ctx,font,crop(font,a.name(),contentW-42),x+padding+31,cursor+11,GOLD);
                int ty=cursor+28;
                for(String l:lines){
                    if(l.isBlank()){ty+=7;continue;}
                    richLine(ctx,font,l,x+padding+31,ty,MUTED);
                    ty+=lineSize;
                }
            }
            cursor+=cardH+8;
        }
        ctx.disableScissor();
        if(total>clipBottom-clipTop)
            text(ctx,font,"SCROLL",x+w-42,bodyY+bodyH-16,0xFF9F85BD);
    }

    private static String statIcon(String line){
        String low=line.toLowerCase(Locale.ROOT);
        if(low.contains("health"))return "minecraft:red_dye";
        if(low.contains("intelligence")||low.contains("mana"))return "minecraft:lapis_lazuli";
        if(low.contains("strength")||low.contains("damage"))return "minecraft:iron_sword";
        if(low.contains("defense"))return "minecraft:iron_chestplate";
        if(low.contains("ferocity"))return "minecraft:blaze_powder";
        return "minecraft:emerald";
    }

    private static String abilityIcon(String name){
        String low=name.toLowerCase(Locale.ROOT);
        if(low.contains("pest"))return "minecraft:spider_eye";
        if(low.contains("compact")||low.contains("storage"))return "minecraft:chest";
        if(low.contains("heal"))return "minecraft:golden_apple";
        if(low.contains("scaveng")||low.contains("fortune"))return "minecraft:emerald";
        if(low.contains("fire")||low.contains("flame"))return "minecraft:blaze_powder";
        if(low.contains("magic")||low.contains("ritual"))return "minecraft:amethyst_shard";
        return "minecraft:nether_star";
    }

    private static void tooltip(BattlePediaScreen s,DrawContext ctx,CatalogEntry e,
                                int x,int y,int w,int h){
        TextRenderer font=s.getTextRenderer();
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xF41B1533,0xFF9461B7,true);
        ctx.fill(x+4,y+4,x+w-4,y+28,0xFF302044);
        drawNative(ctx,vanilla("minecraft:grass_block"),x+9,y+9);
        text(ctx,font,crop(font,"Minecraft Tooltip (Original)",w-39),x+31,y+13,WHITE);
        int innerX=x+10,innerY=y+35,innerW=w-20,innerH=h-45;
        if(innerW<25||innerH<25)return;
        PediaChrome.pixelFrame(ctx,innerX,innerY,innerW,innerH,0xF50C0919,0xFF7F399D,false);
        int textX=innerX+7,clipTop=innerY+6,clipBottom=innerY+innerH-6;
        int contentWidth=innerW-14;
        // Rich Text components contain the actual Minecraft formatting when the server
        // sent ItemStacks. Plain dumps stay lossless and use conservative color hints.
        List<Text> nativeLines=null;
        if(e.stackJson!=null&&!e.stackJson.isBlank()){
            LoreComponent lore=icon(e).get(DataComponentTypes.LORE);
            if(lore!=null && !lore.lines().isEmpty())nativeLines=lore.lines();
        }
        int rawHeight=21;
        List<String> lines=e.lore==null?List.of():e.lore;
        if(nativeLines!=null){
            for(Text part:nativeLines)rawHeight+=part.getString().isBlank()?7:Math.max(1,font.wrapLines(part,contentWidth).size())*11;
        }else{
            for(String part:lines)rawHeight+=part.isBlank()?7:Math.max(1,wrap(font,part,contentWidth).size())*11;
        }
        s.loreScroll=Math.min(s.loreScroll,Math.max(0,(rawHeight-(clipBottom-clipTop)+11)/14));
        int pos=clipTop-s.loreScroll*14;
        ctx.enableScissor(innerX+4,clipTop,innerX+innerW-4,clipBottom);
        if(pos+12>=clipTop&&pos<clipBottom)
            text(ctx,font,crop(font,e.baseName(),contentWidth),textX,pos,
                PediaChrome.rarity(BattlePediaScreen.resolvedRarity(e)));
        pos+=21;
        if(nativeLines!=null){
            for(Text part:nativeLines){
                if(part.getString().isBlank()){pos+=7;continue;}
                for(OrderedText wrapped:font.wrapLines(part,contentWidth)){
                    if(pos+10>=clipTop&&pos<clipBottom)ctx.drawText(font,wrapped,textX,pos,WHITE,false);
                    pos+=11;
                }
            }
        }else{
            for(String part:lines){
                if(part.isBlank()){pos+=7;continue;}
                for(String line:wrap(font,part,contentWidth)){
                    if(pos+10>=clipTop&&pos<clipBottom)richLine(ctx,font,line,textX,pos,tooltipColor(part));
                    pos+=11;
                }
            }
        }
        ctx.disableScissor();
    }

    private static int tooltipColor(String raw){
        String line=raw.toLowerCase(Locale.ROOT);
        if(line.startsWith("pet ability:")||line.startsWith("item ability:")||line.startsWith("summon ability:"))
            return GOLD;
        if(line.startsWith("source npc:")||line.startsWith("dropped by:")||line.startsWith("slayer sources:"))
            return 0xFF48E9F9;
        if(line.contains("cannot be stacked"))return 0xFF9A8EA2;
        if(line.startsWith("requires")||line.startsWith("click to view")||line.startsWith("this pet needs"))
            return 0xFFFFDF6A;
        if(line.matches("(?i)^(common|uncommon|rare|epic|legendary|mythic|unique|special).*"))
            return PediaChrome.rarity(CatalogEntry.detectRarity(line));
        if(line.matches("(?i)^[a-z][a-z0-9' %/()-]{1,40}:\\s*[+\\-]?\\d+.*"))
            return GREEN;
        return WHITE;
    }

    private static void richLine(DrawContext ctx,TextRenderer font,String line,int x,int y,int base){
        if(line==null||line.isEmpty())return;
        Matcher m=HIGHLIGHT.matcher(line);
        int off=0,cursor=0;
        while(m.find()){
            if(m.start()>off){
                String part=line.substring(off,m.start());
                text(ctx,font,part,x+cursor,y,base);
                cursor+=font.getWidth(part);
            }
            String part=m.group();
            int color=part.matches("[+\\-]?\\d+(?:\\.\\d+)?%?")?GREEN:
                part.equalsIgnoreCase("PASSIVE")||part.equalsIgnoreCase("ACTIVE")?GOLD:
                part.equalsIgnoreCase("Enchanted")?GREEN:GOLD;
            text(ctx,font,part,x+cursor,y,color);
            cursor+=font.getWidth(part);
            off=m.end();
        }
        if(off<line.length())text(ctx,font,line.substring(off),x+cursor,y,base);
    }

    private static LoreInfo analyze(CatalogEntry e){
        String type=e.category.equals("Pets")?"Pet":e.category,source="Not recorded";
        List<String> stats=new ArrayList<>();
        List<Ability> abilities=new ArrayList<>();
        String currentName=null;
        List<String> description=new ArrayList<>();
        boolean footer=false,readingSource=false,craftable=false;
        for(String raw:e.lore==null?List.<String>of():e.lore){
            String line=raw==null?"":raw.trim(),low=line.toLowerCase(Locale.ROOT);
            if(e.category.equals("Pets")&&line.endsWith(" Pet")
                &&!line.matches("(?i)^(common|uncommon|rare|epic|legendary|mythic)( guild)? pet$"))
                type=line;
            if(low.startsWith("source npc:")||low.startsWith("dropped by:")||low.startsWith("slayer sources:")){
                footer=true;readingSource=true;
                String remainder=line.substring(line.indexOf(':')+1).trim();
                if(!remainder.isEmpty())source=remainder;
                continue;
            }
            if(readingSource && line.startsWith("-")){
                String value=line.replaceFirst("^-\\s*","").trim();
                if(source.equals("Not recorded"))source=value;
                else if(!source.contains(value))source+=", "+value;
                continue;
            }
            if(!line.isEmpty()&&!line.startsWith("-"))readingSource=false;
            if(low.contains("click to view crafting recipe"))craftable=true;
            if(low.startsWith("pet ability:")||low.startsWith("item ability:")||low.startsWith("summon ability:")){
                footer=false;
                if(currentName!=null)abilities.add(new Ability(currentName,new ArrayList<>(description)));
                currentName=line.substring(line.indexOf(':')+1).trim();
                description.clear();
                continue;
            }
            if(low.startsWith("requires")||low.startsWith("this pet needs")||
               low.startsWith("click to view")||
               low.matches("^(common|uncommon|rare|epic|legendary|mythic|unique|special)( guild| dungeon)? (pet|sword|bow|helmet|chestplate|leggings|boots|item|accessory).*"))
                footer=true;
            if(footer)continue;
            if(currentName!=null){
                if(!line.isBlank()||!description.isEmpty())description.add(line);
            }else if(line.matches("(?i)^[a-z][a-z0-9' %/()-]{1,40}:\\s*[+\\-]?\\d+.*")){
                stats.add(line);
            }
        }
        if(currentName!=null)abilities.add(new Ability(currentName,new ArrayList<>(description)));
        List<Ability> cleaned=new ArrayList<>();
        for(Ability a:abilities){
            List<String> lines=new ArrayList<>(a.description());
            while(!lines.isEmpty()&&lines.get(lines.size()-1).isBlank())lines.remove(lines.size()-1);
            cleaned.add(new Ability(a.name(),lines));
        }
        return new LoreInfo(type,source,stats,cleaned,craftable);
    }

    private static List<String> wrap(TextRenderer font,String str,int pixels){
        List<String> result=new ArrayList<>();
        if(str==null||str.isBlank())return result;
        StringBuilder line=new StringBuilder();
        for(String word:str.split("\\s+")){
            String test=line.length()==0?word:line+" "+word;
            if(font.getWidth(test)>pixels && line.length()>0){
                result.add(crop(font,line.toString(),pixels));
                line=new StringBuilder(word);
            }else line=new StringBuilder(test);
        }
        if(line.length()>0)result.add(crop(font,line.toString(),pixels));
        return result;
    }

    private static boolean inside(int mx,int my,int x,int y,int w,int h){
        return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }

    private static void text(DrawContext ctx,TextRenderer font,String str,int x,int y,int color){
        ctx.drawText(font,str,x,y,color,false);
    }

    private static String crop(TextRenderer font,String str,int pixels){
        return PediaChrome.crop(font,str,pixels);
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
            // Don't cache an incomplete player head before registries become available.
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
        ctx.getMatrices().scale(factor,factor);
        ctx.drawItem(item,0,0);
        ctx.getMatrices().popMatrix();
    }
}
