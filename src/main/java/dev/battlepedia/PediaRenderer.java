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
    private static final Map<CatalogEntry,ItemStack> ICONS=new IdentityHashMap<>();
    private static final Map<CatalogEntry,LoreInfo> LORE=new IdentityHashMap<>();
    private static final Map<String,ItemStack> VANILLA=new HashMap<>();
    static void clearCaches(){ICONS.clear();LORE.clear();}
    private static int mix(int a,int b,double t){
        int result=0;
        for(int shift=0;shift<=24;shift+=8)result|=((int)Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;
        return result;
    }
    private static final Pattern HIGHLIGHT=Pattern.compile("(?i)(\\b(?:Farming Fortune|Bonus Pest Chance|Crit Damage|Crit Chance|Magic Find|Ferocity|Enchanted|Strength|Health|Intelligence|Defense|Damage|Mana|Healing)\\b|[+\\-]?\\d+(?:\\.\\d+)?%?|\\b(?:PASSIVE|ACTIVE|RIGHT CLICK|LEFT CLICK)\\b)");

    record Nav(String label,String icon,String section,String group,int indent) {}
    record Ability(String name,List<String> description) {}
    record LoreInfo(String type,String source,List<String> stats,List<Ability> abilities,boolean craftable) {}

    static boolean compactDetail(BattlePediaScreen s) { return s.mainWidth<540 || s.bodyBottom-s.bodyTop<230; }
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
        double visible=s.visibility();
        ctx.fill(0,0,s.width,s.height,((int)(65*visible)<<24)|0x080615);
        int x=s.left,y=s.top-28,w=s.widthPx,h=s.heightPx+28;
        PediaChrome.pixelFrame(ctx,x-2,y-2,w+4,h+4,0xF0120C26,0xFF9050C2,true);
        ctx.enableScissor(x,y,x+w,y+28);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x,y);
        PediaChrome.banner(ctx,w,28);
        ctx.getMatrices().popMatrix();
        ctx.disableScissor();
        sidebar(s,ctx,mouseX,mouseY);
        switch(s.view){
            case HOME -> home(s,ctx,mouseX,mouseY);
            case DETAIL -> detail(s,ctx,mouseX,mouseY);
            case BROWSE -> browser(s,ctx,mouseX,mouseY);
            case SETTINGS -> settings(s,ctx,mouseX,mouseY);
        }
        double reveal=s.contentReveal();
        if(reveal<0.999){
            int alpha=(int)(175*(1-reveal));
            ctx.fill(s.mainX,s.bodyTop,s.mainX+s.mainWidth,s.bodyBottom,(alpha<<24)|0x15102B);
        }
        if(visible<0.999){
            int alpha=(int)(185*(1-visible));
            ctx.fill(x,y,x+w,y+h,(alpha<<24)|0x100B21);
        }
    }

    private static void sidebar(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        TextRenderer font=s.getTextRenderer();
        int x=s.left,y=s.top,w=s.sideWidth,h=s.heightPx;
        // Brand is outside the nav card and sits on the illustrated sky.
        PediaChrome.paw(ctx,x+5,y-24,PURPLE);
        text(ctx,font,crop(font,"BattlePedia",w-43),x+33,y-23,WHITE);
        text(ctx,font,crop(font,"BattlePet Encyclopedia",w-43),x+33,y-12,0xFFD4C5EF);
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xF415112A,0xFF8F5BB0,true);
        PediaChrome.pixelFrame(ctx,x+5,y+5,w-10,25,0xF80C0F22,0xFF5C4C88,false);
        drawNative(ctx,vanilla("minecraft:spyglass"),x+8,y+10);
        action(s,ctx,x+w-21,y+9,15,16,"x",s::clearSearch,mx,my,false,false);
        PediaChrome.div(ctx,x+6,y+34,w-12);

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

        int navY=y+39,navBottom=y+h-30,rowH=20;
        int capacity=Math.max(1,(navBottom-navY)/rowH);
        s.navScroll=Math.min(s.navScroll,Math.max(0,nav.size()-capacity));
        ctx.enableScissor(x+4,navY,x+w-4,navBottom);
        for(int i=s.navScroll;i<nav.size()&&i<s.navScroll+capacity;i++){
            Nav n=nav.get(i);
            int cy=navY+(i-s.navScroll)*rowH;
            int nx=x+7+n.indent*11,nw=w-14-n.indent*11;
            boolean group=!n.group.isBlank(),active=n.section.equals(s.section);
            boolean hover=inside(mx,my,nx,cy,nw,19);
            double glow=s.hover("nav:"+n.section,active||hover);
            if(active) {
                PediaChrome.pixelFrame(ctx,nx,cy,nw,19,mix(0xF42C1C46,0xF45A287F,glow),mix(0xFF76549E,0xFFE475FB,glow),true);
                ctx.fill(nx+2,cy+3,nx+4,cy+16,0xFFF2B5FF);
            }else if(glow>0.02){
                PediaChrome.pixelFrame(ctx,nx,cy,nw,19,mix(0xF415112A,0xE3372453,glow),0xFF76549E,false);
            }else if(n.indent==0){
                ctx.fill(nx+2,cy+19,nx+nw-2,cy+20,0x44463664);
            }
            drawNative(ctx,vanilla(n.icon),nx+4,cy+2);
            int labelColor=active?WHITE:n.indent>0?0xFFBAAACC:0xFFE7E2F1;
            text(ctx,font,crop(font,n.label,nw-(group?45:31)),nx+23,cy+6,labelColor);
            if(group)text(ctx,font,n.group.equals(s.expandedGroup)?"-":"+",nx+nw-13,cy+6,PURPLE);
            if(group){
                s.hit(nx,cy,nw-18,19,()->{s.expandedGroup=n.group;s.chooseSection(n.section);});
                s.hit(nx+nw-18,cy,18,19,()->s.toggleGroup(n.group));
            }else s.hit(nx,cy,nw,19,()->s.chooseSection(n.section));
        }
        ctx.disableScissor();
        if(nav.size()>capacity)text(ctx,font,(s.navScroll+1)+"-"+Math.min(nav.size(),s.navScroll+capacity),x+9,navBottom+1,0xFF8C7AA9);
        PediaChrome.div(ctx,x+6,y+h-20,w-12);
        boolean fav=s.section.equals("Favorites"),favHover=inside(mx,my,x+5,y+h-19,w-10,18);
        if(fav||favHover)PediaChrome.pixelFrame(ctx,x+5,y+h-19,w-10,18,
            fav?0xEB543173:0xE42D1C47,fav?PURPLE:PediaChrome.LINE,fav);
        drawNative(ctx,vanilla("minecraft:nether_star"),x+8,y+h-18);
        text(ctx,font,"Favorites",x+28,y+h-13,fav?WHITE:0xFFF5E2FD);
        s.hit(x+5,y+h-19,w-10,18,()->s.chooseSection("Favorites"));
    }

    private static void addGroup(List<Nav> nav,String group,String icon,String expanded,List<String> children){
        nav.add(new Nav(group,icon,group,group,0));
        if(group.equals(expanded))
            for(String child:children)nav.add(new Nav(child,WikiCategories.icon(child),child,"",1));
    }

    private static void header(BattlePediaScreen s,DrawContext ctx,String crumb,boolean back,int mx,int my){
        int x=s.mainX,y=s.top,w=s.mainWidth;
        TextRenderer font=s.getTextRenderer();
        PediaChrome.titleStrip(ctx,x,y,w,27);
        int textX=x+8;
        if(back){
            action(s,ctx,x+4,y+4,20,19,"<",s::back,mx,my,false,false);
            textX=x+29;
        }
        int right=x+w-4;
        action(s,ctx,right-20,y+4,20,19,s.maximized?"-":"+",s::toggleMaximized,mx,my,false,false);
        right-=23;
        action(s,ctx,right-20,y+4,20,19,"*",s::settings,mx,my,s.view==BattlePediaScreen.View.SETTINGS,false);
        right-=23;
        if(back){
            action(s,ctx,right-20,y+4,20,19,s.isFavorite()?"★":"☆",s::toggleFavorite,mx,my,s.isFavorite(),true);
            right-=23;
        }
        text(ctx,font,crop(font,crumb,Math.max(12,right-textX-4)),textX,y+10,WHITE);
    }

    private static void settings(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        header(s,ctx,"Settings",false,mx,my);
        int x=s.mainX,y=s.bodyTop,w=s.mainWidth,h=s.bodyBottom-y;
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xF0191533,0xFF77589F,false);
        TextRenderer font=s.getTextRenderer();
        text(ctx,font,"WINDOW SIZE",x+9,y+9,PURPLE);
        var sizes=PediaPreferences.Size.values();
        int bw=(w-21)/2;
        for(int i=0;i<sizes.length;i++){
            var size=sizes[i];
            action(s,ctx,x+8+(i%2)*(bw+5),y+22+(i/2)*22,bw,19,
                size.name()+" "+size.percent+"%",()->s.setSize(size),mx,my,s.prefs.size==size,false);
        }
        int controls=y+68;
        action(s,ctx,x+8,controls,w-16,17,"Animations: "+s.prefs.animationLabel(),s::cycleAnimation,mx,my,false,false);
        action(s,ctx,x+8,controls+19,bw,17,"Reload",s::reload,mx,my,false,false);
        action(s,ctx,x+13+bw,controls+19,bw,17,"Import",s::importTextDump,mx,my,false,false);
        if(h>128)text(ctx,font,crop(font,s.notice,w-18),x+9,y+117,MUTED);
    }

    private static void action(BattlePediaScreen s,DrawContext ctx,int x,int y,int w,int h,
                               String label,Runnable run,int mx,int my,boolean selected,boolean gold){
        boolean hover=inside(mx,my,x,y,w,h);
        int edge=gold?(selected?GOLD:0xFFAD80C8):(selected?PURPLE:hover?0xFFBD8DFF:0xFF6B5996);
        double glow=s.hover("action:"+label+":"+x+":"+y,selected||hover);
        PediaChrome.pixelFrame(ctx,x,y,w,h,mix(0xF82A2041,selected?0xF84E225B:0xF840265A,glow),edge,hover||selected);
        String str=crop(s.getTextRenderer(),label,w-8);
        text(ctx,s.getTextRenderer(),str,x+(w-s.getTextRenderer().getWidth(str))/2,y+(h-9)/2,
            selected&&gold?GOLD:WHITE);
        s.hit(x,y,w,h,run);
    }

    private static void home(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        header(s,ctx,"Home / BattlePet Encyclopedia",false,mx,my);
        int x=s.mainX,y=s.bodyTop,w=s.mainWidth,h=s.bodyBottom-y;
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xF012132F,PURPLE,true);
        TextRenderer font=s.getTextRenderer();
        text(ctx,font,"WELCOME TO BATTLEPEDIA",x+9,y+8,GOLD);
        text(ctx,font,crop(font,CatalogStore.INSTANCE.size()+" variants | Search all wiki items",w-18),x+9,y+20,MUTED);
        List<String> categories=List.of("Pets","Pet Items","Melee Weapons","Ranged Weapons","Magic Weapons","Tank Weapons",
            "Executioner Weapons","Helmets","Chestplates","Leggings","Boots","Necklaces","Cloaks","Belts","Gloves",
            "Artifacts","Enchantments","Power Cores","Brews","Crafting Ingredients","Pickaxes & Drills","Fishing Rods");
        int cols=Math.max(1,w/112),gap=4,cardW=(w-14-(cols-1)*gap)/cols;
        int y0=y+34,bottom=y+h-5,stride=36,rows=(categories.size()+cols-1)/cols;
        int visible=Math.max(1,(bottom-y0)/stride),offset=s.scrollPixels(stride,Math.max(0,rows-visible));
        ctx.enableScissor(x+4,y0,x+w-4,bottom);
        for(int i=0;i<categories.size();i++){
            String c=categories.get(i);
            int tx=x+7+(i%cols)*(cardW+gap),ty=y0+(i/cols)*stride-offset;
            if(ty+32<=y0||ty>=bottom)continue;
            boolean hover=my>=y0&&my<bottom&&inside(mx,my,tx,ty,cardW,32);
            double glow=s.hover("home:"+c,hover);
            PediaChrome.pixelFrame(ctx,tx,ty,cardW,32,mix(0xF5211C3D,0xF83D255B,glow),
                mix(0xFF594775,PURPLE,glow),hover);
            drawNative(ctx,vanilla(WikiCategories.icon(c)),tx+5,ty+8);
            text(ctx,font,crop(font,c,cardW-28),tx+25,ty+6,WHITE);
            text(ctx,font,s.sectionCount(c)+" entries",tx+25,ty+18,MUTED);
            int hitY=Math.max(ty,y0),hitBottom=Math.min(ty+32,bottom);
            s.hit(tx,hitY,cardW,hitBottom-hitY,()->s.chooseSection(c));
        }
        ctx.disableScissor();
    }

    private static void browser(BattlePediaScreen s,DrawContext ctx,int mx,int my){
        List<CatalogEntry> items=s.browserEntries();
        String crumb=s.section+(s.query.isBlank()?"":" > "+s.query)+" / "+items.size();
        header(s,ctx,crumb,false,mx,my);
        int x=s.mainX,y=s.bodyTop,w=s.mainWidth,h=s.bodyBottom-y;
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xF014132C,0xFF6D4F9C,false);
        int modeW=Math.min(55,(w-20)/3),sortW=Math.min(102,w-modeW-19);
        action(s,ctx,x+7,y+5,modeW,18,s.prefs.grid?"Grid":"Details",s::toggleGrid,mx,my,false,false);
        action(s,ctx,x+11+modeW,y+5,sortW,18,s.prefs.sort.name(),s::cycleSort,mx,my,false,false);
        PediaChrome.div(ctx,x+7,y+27,w-14);
        boolean grid=s.prefs.grid;
        int cols=Math.max(1,Math.min(grid?12:5,(w-12)/(grid?72:150))),gap=4;
        int cardW=(w-14-gap*(cols-1))/cols,cardH=grid?65:62;
        int stride=cardH+gap,listTop=y+33,listBottom=y+h-5,rows=(items.size()+cols-1)/cols;
        int viewport=Math.max(1,listBottom-listTop);
        int max=Math.max(0,rows-Math.max(1,viewport/stride));
        int offset=s.scrollPixels(stride,max),first=offset/stride,last=Math.min(rows,first+viewport/stride+2);
        CatalogEntry hovered=null;
        TextRenderer font=s.getTextRenderer();
        ctx.enableScissor(x+4,listTop,x+w-4,listBottom);
        for(int row=first;row<last;row++)for(int col=0;col<cols;col++){
            int i=row*cols+col;if(i>=items.size())break;
            CatalogEntry entry=items.get(i);
            int cx=x+7+col*(cardW+gap),cy=listTop+row*stride-offset;
            boolean hover=my>=listTop&&my<listBottom&&inside(mx,my,cx,cy,cardW,cardH);
            if(hover)hovered=entry;
            double glow=s.hover("card:"+entry.key(),hover);
            int rarity=PediaChrome.rarity(BattlePediaScreen.resolvedRarity(entry));
            PediaChrome.pixelFrame(ctx,cx,cy,cardW,cardH,mix(0xF5211C3B,0xF43E244E,glow),
                mix(0xFF59457D,PURPLE,glow),hover);
            ctx.fill(cx+3,cy+2,cx+cardW-3,cy+4,rarity);
            if(grid){
                drawNative(ctx,icon(entry),cx+(cardW-16)/2,cy+6);
                PediaChrome.center(ctx,font,crop(font,entry.baseName(),cardW-8),cx+cardW/2,cy+25,WHITE);
                PediaChrome.center(ctx,font,crop(font,BattlePediaScreen.resolvedRarity(entry),cardW-8),cx+cardW/2,cy+36,rarity);
                String stat=firstStat(entry);
                PediaChrome.center(ctx,font,crop(font,s.section.equals("Home")?entry.category:stat,cardW-8),cx+cardW/2,cy+46,s.section.equals("Home")?MUTED:GREEN);
                if(s.section.equals("Home"))PediaChrome.center(ctx,font,crop(font,stat,cardW-8),cx+cardW/2,cy+56,GREEN);
            }else{
                drawLarge(ctx,icon(entry),cx+7,cy+11,2);
                text(ctx,font,crop(font,entry.baseName(),cardW-52),cx+44,cy+9,WHITE);
                text(ctx,font,crop(font,BattlePediaScreen.resolvedRarity(entry),cardW-52),cx+44,cy+20,rarity);
                String label=s.section.equals("Home")?entry.category:firstStat(entry);
                text(ctx,font,crop(font,label,cardW-52),cx+44,cy+31,GREEN);
                text(ctx,font,crop(font,s.section.equals("Home")?firstStat(entry):entry.category,cardW-12),cx+6,cy+49,MUTED);
            }
            if(s.favorites.has(s.identity(entry)))text(ctx,font,"★",cx+cardW-12,cy+6,GOLD);
            int hitY=Math.max(cy,listTop),hitBottom=Math.min(cy+cardH,listBottom);
            s.hit(cx,hitY,cardW,hitBottom-hitY,()->s.chooseEntry(entry));
        }
        ctx.disableScissor();
        if(items.isEmpty())text(ctx,font,crop(font,"No matches in "+s.section,w-20),x+10,listTop+8,MUTED);
        if(hovered!=null)hoverTooltip(s,ctx,hovered,mx,my);
    }

    private static void hoverTooltip(BattlePediaScreen s,DrawContext ctx,CatalogEntry e,int mx,int my){
        TextRenderer font=s.getTextRenderer();
        int w=Math.min(240,s.width-8);
        List<String> lines=new ArrayList<>();
        lines.add(e.baseName());lines.add(e.category+" / "+BattlePediaScreen.resolvedRarity(e));
        for(String l:e.lore==null?List.<String>of():e.lore){
            if(l.isBlank())continue;
            lines.addAll(wrap(font,l,w-14));
            if(lines.size()>=11)break;
        }
        if(lines.size()>11)lines=new ArrayList<>(lines.subList(0,11));
        lines.add("Click for full original tooltip");
        int h=lines.size()*11+10,x=Math.clamp(mx+10,4,Math.max(4,s.width-w-4)),y=Math.clamp(my+10,4,Math.max(4,s.height-h-4));
        PediaChrome.pixelFrame(ctx,x,y,w,h,0xFD10091C,0xFFA053CE,true);
        for(int i=0;i<lines.size();i++)text(ctx,font,crop(font,lines.get(i),w-12),x+6,y+5+i*11,i==0?PediaChrome.rarity(e.rarity):i==1?PURPLE:WHITE);
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
        int y=s.bodyTop,h=s.bodyBottom-y;
        if(compactDetail(s)){
            int x=s.mainX,w=s.mainWidth,bw=(w-8)/3;
            String[] names={"Profile","Stats","Tooltip"};
            for(int i=0;i<3;i++){
                int tab=i;
                action(s,ctx,x+i*(bw+4),y,bw,19,names[i],()->s.setCompactTab(tab),mx,my,s.compactTab==i,false);
            }
            if(s.compactTab==0)portrait(s,ctx,entry,x,y+23,w,h-23);
            else if(s.compactTab==2)tooltip(s,ctx,entry,x,y+23,w,h-23);
            else center(s,ctx,entry,x,y+23,w,h-23,mx,my);
        }else{
            int[] c=detailColumns(s);
            portrait(s,ctx,entry,c[0],y,c[1],h);
            center(s,ctx,entry,c[2],y,c[4],h,mx,my);
            tooltip(s,ctx,entry,c[3],y,c[5],h);
        }
    }

    private static void portrait(BattlePediaScreen s,DrawContext ctx,CatalogEntry e,
                                 int x,int y,int w,int h){
        TextRenderer font=s.getTextRenderer();
        int heroH=Math.min(h,Math.max(75,Math.min(180,(int)(h*0.59))));
        int rarity=PediaChrome.rarity(BattlePediaScreen.resolvedRarity(e));
        PediaChrome.pixelFrame(ctx,x,y,w,heroH,0xF21D0E31,0xFFAA5FDC,true);
        ctx.fill(x+4,y+4,x+w-4,y+6,0x66382A66);
        PediaChrome.softGlow(ctx,x+w/2,y+heroH/2-16,Math.min(w/2-5,64),0xFF9B32D6);
        int scale=Math.max(1,Math.min(4,Math.min((w-26)/16,(heroH-45)/16)));
        drawLarge(ctx,icon(e),x+(w-16*scale)/2,y+Math.max(5,(heroH-45-16*scale)/2),scale);
        String name=crop(font,e.baseName(),w-14);
        PediaChrome.center(ctx,font,name,x+w/2,y+heroH-43,WHITE);
        LoreInfo info=analyze(e);
        String type=crop(font,info.type(),w-16);
        PediaChrome.center(ctx,font,type,x+w/2,y+heroH-28,MUTED);
        PediaChrome.center(ctx,font,BattlePediaScreen.resolvedRarity(e).toUpperCase(Locale.ROOT),
            x+w/2,y+heroH-14,rarity);
        int infoY=y+heroH+9,infoH=h-heroH-9;
        if(infoH<45)return;
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
        int perRow=Math.max(1,Math.min(8,(w-14)/44));
        int rowCount=(rarities.size()+perRow-1)/perRow;
        boolean shortPanel=h<145;
        int tabH=shortPanel?22:19+rowCount*18+4;
        PediaChrome.pixelFrame(ctx,x,y,w,tabH,0xF1181735,0xFF675495,false);
        if(shortPanel){
            action(s,ctx,x+5,y+3,w-10,16,"Rarity: "+BattlePediaScreen.resolvedRarity(e)+" >",s::nextRarity,mx,my,true,false);
        }else{
        text(ctx,font,"Rarity",x+8,y+5,WHITE);
        int tabW=(w-16-4*(perRow-1))/perRow;
        for(int i=0;i<rarities.size();i++){
            String rarity=rarities.get(i);
            int tx=x+8+(i%perRow)*(tabW+4),ty=y+17+(i/perRow)*18;
            boolean selected=rarity.equals(BattlePediaScreen.resolvedRarity(e));
            int color=PediaChrome.rarity(rarity);
            PediaChrome.pixelFrame(ctx,tx,ty,tabW,16,
                selected?0xF541244D:0xFA25233D,selected?color:0xFF68627D,selected);
            String label=crop(font,rarity,tabW-6);
            PediaChrome.center(ctx,font,label,tx+tabW/2,ty+4,color);
            s.hit(tx,ty,tabW,16,()->s.chooseRarity(rarity));
        }
        }
        int bodyY=y+tabH+4,bodyH=h-tabH-4;
        if(bodyH<22)return;
        PediaChrome.pixelFrame(ctx,x,bodyY,w,bodyH,0xF0171531,0xFF77589F,false);
        int color=PediaChrome.rarity(BattlePediaScreen.resolvedRarity(e));
        String heading=BattlePediaScreen.resolvedRarity(e)+" Variant";
        PediaChrome.center(ctx,font,crop(font,heading,w-46),x+w/2,bodyY+6,color);
        ctx.fill(x+13,bodyY+10,x+29,bodyY+11,0xFFAC7864);
        ctx.fill(x+w-29,bodyY+10,x+w-13,bodyY+11,0xFFAC7864);
        PediaChrome.div(ctx,x+9,bodyY+18,w-18);

        LoreInfo info=analyze(e);
        int clipTop=bodyY+23,clipBottom=bodyY+bodyH-4;
        if(clipBottom<=clipTop)return;
        int padding=9,contentW=w-padding*2;
        List<String> statRows=info.stats();
        List<Ability> abilities=info.abilities();
        int lineSize=12;
        // Measure content before applying scroll to avoid endless empty scrolling.
        List<List<String>> wrapped=new ArrayList<>();
        int statsPixel=Math.max(0,statRows.size())*22;
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
        int cursor=clipTop-s.detailScrollPixels(false,maxScroll);
        ctx.enableScissor(x+5,clipTop,x+w-5,clipBottom);
        for(String stat:statRows){
            if(cursor+20>=clipTop&&cursor<clipBottom){
                PediaChrome.pixelFrame(ctx,x+padding,cursor,contentW,20,0xF2252242,0xFF625277,false);
                drawNative(ctx,vanilla(statIcon(stat)),x+padding+9,cursor+2);
                int colon=stat.indexOf(':');
                if(colon>0){
                    String key=stat.substring(0,colon+1),val=stat.substring(colon+1).trim();
                    text(ctx,font,crop(font,key,Math.max(35,contentW/2-18)),x+padding+32,cursor+6,WHITE);
                    String v=crop(font,val,Math.max(36,contentW/2-12));
                    text(ctx,font,v,x+padding+contentW-10-font.getWidth(v),cursor+6,GREEN);
                }else{
                    text(ctx,font,crop(font,stat,contentW-45),x+padding+32,cursor+6,WHITE);
                }
            }
            cursor+=22;
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
        int pos=clipTop-s.detailScrollPixels(true,Math.max(0,(rawHeight-(clipBottom-clipTop)+11)/14));
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

    private static LoreInfo analyze(CatalogEntry e){return LORE.computeIfAbsent(e,PediaRenderer::parseLore);}
    private static LoreInfo parseLore(CatalogEntry e){
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

    private static ItemStack vanilla(String itemId){return VANILLA.computeIfAbsent(itemId,PediaRenderer::makeVanilla);}
    private static ItemStack makeVanilla(String itemId){
        try{
            var item=Registries.ITEM.get(Identifier.of(itemId));
            return item==null?new ItemStack(Items.BOOK):new ItemStack(item);
        }catch(Exception ignored){return new ItemStack(Items.BOOK);}
    }

    static ItemStack icon(CatalogEntry entry){
        ItemStack found=ICONS.get(entry);
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
        ICONS.put(entry,stack);
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
