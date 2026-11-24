package dev.battlepedia;

import dev.battlepedia.data.*;
import dev.battlepedia.importer.WikiCapture;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Compact, stateful encyclopedia; search scope stays independent of displayed view. */
public final class BattlePediaScreen extends Screen {
    enum View { HOME, BROWSE, DETAIL, SETTINGS }
    record Hit(int left,int top,int right,int bottom,Runnable action){
        boolean contains(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;}
    }
    private final List<Hit> targets=new ArrayList<>();
    final FavoritesStore favorites=new FavoritesStore();
    final PediaPreferences prefs=new PediaPreferences();
    View view=View.HOME, previousView=View.HOME;
    String section="Home",expandedGroup="",query="",selectedKey="",selectedRarity="";
    String notice="Browse the BattlePet encyclopedia";
    int navScroll=0,browserScroll=0,abilityScroll=0,loreScroll=0;
    double browserOffset=0;
    int compactTab=0;
    boolean maximized=false,mobileLore=false;
    int left,top,widthPx,heightPx,sideWidth,mainX,mainWidth,headerHeight,bodyTop,bodyBottom;
    private TextFieldWidget search;
    private SearchIndex index;
    private long indexedRevision=-1;
    private List<CatalogEntry> results=null;
    private final long opened=System.nanoTime();
    private long transition=opened,closing=0,frameTime=opened;
    private double frameMs=16;
    private record Hover(double value,long seen){}
    private final Map<String,Hover> hovers=new HashMap<>();

    public BattlePediaScreen(){
        super(Text.literal("BattlePedia"));
        favorites.load();prefs.load();
    }
    @Override protected void init(){
        updateLayout(0);
        search=new TextFieldWidget(textRenderer,left+25,top+11,
            Math.max(25,sideWidth-48),16,Text.literal("Search BattlePedia"));
        search.setDrawsBackground(false);search.setMaxLength(128);search.setText(query);
        updatePlaceholder();
        search.setChangedListener(value->{
            if(query.equals(value))return;
            query=value;resetBrowser();invalidate();
            view=query.isBlank()&&section.equals("Home")?View.HOME:View.BROWSE;
            animate();
        });
        addDrawableChild(search);
    }
    private void updateLayout(int offset){
        maximized=prefs.size==PediaPreferences.Size.MAXIMIZED;
        // Below these minimums the layout changes to a single detail panel, rather than
        // shrinking fonts or using a fractional rendering transform.
        widthPx=Math.min(width-4,Math.max(Math.min(300,width-4),width*prefs.size.percent/100));
        int totalH=Math.min(height-4,Math.max(Math.min(170,height-4),height*prefs.size.percent/100));
        heightPx=Math.max(80,totalH-28);
        left=(width-widthPx)/2;
        top=(height-totalH)/2+28+offset;
        sideWidth=Math.max(88,Math.min(152,widthPx*22/100));
        mainX=left+sideWidth+6;
        mainWidth=left+widthPx-mainX;
        headerHeight=28;
        bodyTop=top+headerHeight+4;
        bodyBottom=top+heightPx-2;
        if(search!=null){
            search.setPosition(left+25,top+11);
            search.setWidth(Math.max(25,sideWidth-48));
        }
    }
    private void updatePlaceholder(){
        if(search!=null)search.setPlaceholder(Text.literal(section.equals("Home")?"Search everything...":"Search "+section.toLowerCase(Locale.ROOT)+"..."));
    }
    void clearSearch(){if(search!=null)search.setText("");}
    void setSize(PediaPreferences.Size size){
        if(size!=PediaPreferences.Size.MAXIMIZED)prefs.restore=size;
        prefs.size=size;prefs.save();updateLayout(0);resetBrowser();animate();
    }
    void toggleMaximized(){setSize(maximized?prefs.restore:PediaPreferences.Size.MAXIMIZED);}
    void toggleGrid(){prefs.grid=!prefs.grid;prefs.save();resetBrowser();animate();}
    void cycleSort(){
        var options=SearchIndex.Sort.values();
        prefs.sort=options[(prefs.sort.ordinal()+1)%options.length];
        prefs.save();invalidate();resetBrowser();animate();
    }
    void cycleAnimation(){prefs.animation=(prefs.animation+1)%4;prefs.save();animate();}
    void settings(){
        if(view==View.SETTINGS)view=previousView;
        else{previousView=view;view=View.SETTINGS;}
        animate();
    }
    void back(){
        view=section.equals("Home")&&query.isBlank()?View.HOME:View.BROWSE;
        abilityScroll=loreScroll=0;animate();
    }
    void setCompactTab(int tab){
        compactTab=tab;mobileLore=tab==2;abilityScroll=loreScroll=0;animate();
    }
    void showLoreOnCompact(boolean visible){setCompactTab(visible?2:1);}
    void hit(int x,int y,int w,int h,Runnable action){
        if(w>0&&h>0)targets.add(new Hit(x,y,x+w,y+h,action));
    }
    private void invalidate(){results=null;}
    private void resetBrowser(){browserScroll=0;browserOffset=0;}
    private void animate(){transition=System.nanoTime();}
    void chooseSection(String value){
        section=value;
        if(WikiCategories.ALL.stream().anyMatch(c->c.name().equals(value)))WikiCapture.setCategory(value);
        else WikiCapture.setCategory("Unsorted");
        for(var group:SearchIndex.GROUPS.entrySet())if(group.getValue().contains(value))expandedGroup=group.getKey();
        view=value.equals("Home")&&query.isBlank()?View.HOME:View.BROWSE;
        selectedKey=selectedRarity="";resetBrowser();abilityScroll=loreScroll=0;
        compactTab=0;mobileLore=false;
        updatePlaceholder();invalidate();animate();
    }
    void chooseEntry(CatalogEntry entry){
        selectedKey=identity(entry);selectedRarity=resolvedRarity(entry);
        view=View.DETAIL;abilityScroll=loreScroll=0;compactTab=1;mobileLore=false;animate();
    }
    void chooseRarity(String rarity){selectedRarity=rarity;abilityScroll=loreScroll=0;animate();}
    static String resolvedRarity(CatalogEntry e){return e.rarity==null?"Unknown":e.rarity;}
    String identity(CatalogEntry e){return SearchIndex.identity(e);}
    private SearchIndex index(){
        long revision=CatalogStore.INSTANCE.revision();
        if(index==null||revision!=indexedRevision){
            var snap=CatalogStore.INSTANCE.snapshot();
            index=new SearchIndex(snap.entries());indexedRevision=snap.revision();invalidate();
            PediaRenderer.clearCaches();
        }
        return index;
    }
    List<CatalogEntry> browserEntries(){
        SearchIndex i=index();
        if(results==null)results=i.search(section,query,prefs.sort,favorites::has);
        return results;
    }
    int sectionCount(String category){return index().count(category);}
    List<CatalogEntry> selectedVariants(){return index().variants(selectedKey);}
    CatalogEntry activeVariant(){
        var options=selectedVariants();
        if(options.isEmpty())return null;
        for(CatalogEntry e:options)if(resolvedRarity(e).equals(selectedRarity))return e;
        return options.getLast();
    }
    boolean isFavorite(){return !selectedKey.isBlank()&&favorites.has(selectedKey);}
    void toggleFavorite(){
        if(selectedKey.isBlank())return;
        favorites.toggle(selectedKey);invalidate();animate();
        notice=favorites.has(selectedKey)?"Added to favorites":"Removed from favorites";
    }
    void reload(){
        notice="Reloading catalog...";
        CompletableFuture.runAsync(()->CatalogStore.INSTANCE.load()).thenRun(()->{
            if(client!=null)client.execute(()->{invalidate();notice="Loaded "+CatalogStore.INSTANCE.size()+" entries";});
        });
    }
    void importTextDump(){
        notice="Importing pets.txt...";
        CompletableFuture.supplyAsync(()->{
            try{
                var records=DumpParser.read(CatalogStore.importFile());
                int inserted=CatalogStore.INSTANCE.merge(records);
                return "Imported "+records.size()+" variants ("+inserted+" new)";
            }catch(Exception error){return "Import failed: "+error.getMessage();}
        }).thenAccept(message->{if(client!=null)client.execute(()->{invalidate();notice=message;});});
    }
    private double progress(long start,int duration){
        long ms=prefs.duration(duration);
        if(ms==0)return 1;
        double t=Math.clamp((System.nanoTime()-start)/1_000_000.0/ms,0,1);
        return 1-Math.pow(1-t,3);
    }
    double visibility(){return closing==0?progress(opened,180):1-progress(closing,180);}
    double contentReveal(){return progress(transition,160);}
    double hover(String id,boolean active){
        if(prefs.animation==0)return active?1:0;
        Hover old=hovers.get(id);
        double current=old==null?0:old.value;
        if(old!=null&&frameTime-old.seen>350_000_000L)current=0;
        double next=current+((active?1:0)-current)*Math.min(1,frameMs/Math.max(1,prefs.duration(80)));
        hovers.put(id,new Hover(next,frameTime));return next;
    }
    int scrollPixels(int stride,int max){
        browserScroll=Math.clamp(browserScroll,0,max);
        double wanted=browserScroll*stride;
        if(prefs.animation==0)browserOffset=wanted;
        else browserOffset+=(wanted-browserOffset)*Math.min(1,frameMs/Math.max(1,prefs.duration(65)));
        browserOffset=Math.clamp(browserOffset,0,max*stride);
        return (int)Math.round(browserOffset);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float deltaTicks){
        long now=System.nanoTime();
        frameMs=Math.clamp((now-frameTime)/1_000_000.0,0,50);frameTime=now;
        double opacity=visibility();
        if(closing!=0&&opacity<=0.001){super.close();return;}
        updateLayout((int)Math.round((1-opacity)*12));
        targets.clear();
        if(hovers.size()>2048)hovers.entrySet().removeIf(e->now-e.getValue().seen>500_000_000L);
        PediaRenderer.draw(this,context,mouseX,mouseY);
        super.render(context,mouseX,mouseY,deltaTicks);
    }
    @Override public void close(){
        if(prefs.animation==0){super.close();return;}
        if(closing==0)closing=System.nanoTime();
    }
    @Override public boolean mouseClicked(Click click,boolean doubled){
        if(closing!=0)return true;
        if(super.mouseClicked(click,doubled))return true;
        if(click.button()!=0)return false;
        for(int i=targets.size()-1;i>=0;i--){
            Hit target=targets.get(i);
            if(target.contains((int)click.x(),(int)click.y())){target.action().run();return true;}
        }
        return false;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        if(vertical==0||closing!=0)return false;
        int mx=(int)mouseX,my=(int)mouseY,amount=vertical>0?-1:1;
        if(mx<left||mx>=left+widthPx||my<top||my>=top+heightPx)return false;
        if(mx<left+sideWidth){navScroll=Math.max(0,navScroll+amount);return true;}
        if(view==View.BROWSE&&my>=bodyTop&&my<bodyBottom){browserScroll=Math.max(0,browserScroll+amount);return true;}
        if(view==View.DETAIL&&my>=bodyTop&&my<bodyBottom){
            if(PediaRenderer.compactDetail(this)){
                if(mobileLore)loreScroll=Math.max(0,loreScroll+amount*2);
                else abilityScroll=Math.max(0,abilityScroll+amount*2);
            }else if(mx<PediaRenderer.detailCenterRight(this))abilityScroll=Math.max(0,abilityScroll+amount*2);
            else loreScroll=Math.max(0,loreScroll+amount*2);
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }
    @Override public boolean shouldPause(){return false;}
}
