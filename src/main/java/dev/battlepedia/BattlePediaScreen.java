package dev.battlepedia;

import dev.battlepedia.data.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BattlePediaScreen extends Screen {
  static final String[] RARITIES={"All","Common","Uncommon","Rare","Epic","Legendary","Mythic","Unknown"};
  String section="Pets",rarity="All",query="",status="Local-first encyclopedia";
  String selected="";
  int categoryPage=0,scroll=0,detailScroll=0;
  int x,y,w,h,side,right,contentX,contentW,rowsTop;
  private TextFieldWidget search;
  public BattlePediaScreen(){super(Text.literal("BattlePedia"));}
  @Override protected void init(){
    x=12;y=12;w=width-24;h=height-24;
    side=Math.min(132,Math.max(95,w/4));
    right=w>=580?Math.min(210,w/3):0;
    contentX=x+side+12;
    contentW=w-side-right-24;
    rowsTop=y+82;
    search=new TextFieldWidget(textRenderer,contentX+8,y+30,Math.max(55,contentW-16),18,Text.literal("Search BattlePedia"));
    search.setPlaceholder(Text.literal("Search names, stats, abilities..."));
    search.setText(query);
    search.setChangedListener(s->{query=s;scroll=0;});
    addDrawableChild(search);
    addDrawableChild(ButtonWidget.builder(Text.literal("Rarity: "+rarity), b->{
      int index=Arrays.asList(RARITIES).indexOf(rarity);
      rarity=RARITIES[(index+1)%RARITIES.length];scroll=0;detailScroll=0;clearAndInit();
    }).dimensions(contentX+8,y+54,Math.max(70,contentW-16),20).build());
    int navRows=Math.max(3,Math.min(10,(h-94)/23));
    List<WikiCategories.Category> categories=WikiCategories.ALL;
    int maxPage=Math.max(0,(categories.size()-1)/navRows);
    categoryPage=Math.max(0,Math.min(categoryPage,maxPage));
    for(int i=0;i<navRows;i++){
      int at=categoryPage*navRows+i;
      if(at>=categories.size())break;
      WikiCategories.Category cat=categories.get(at);
      addDrawableChild(ButtonWidget.builder(Text.literal(cat.name()),b->{
        section=cat.name();scroll=0;detailScroll=0;selected="";clearAndInit();
      }).dimensions(x+6,y+35+i*23,side-12,20).build());
    }
    addDrawableChild(ButtonWidget.builder(Text.literal("<"),b->{categoryPage=Math.max(0,categoryPage-1);clearAndInit();})
      .dimensions(x+6,y+h-27,(side-16)/2,19).build());
    addDrawableChild(ButtonWidget.builder(Text.literal(">"),b->{categoryPage=Math.min(maxPage,categoryPage+1);clearAndInit();})
      .dimensions(x+10+(side-16)/2,y+h-27,(side-16)/2,19).build());
    int buttonW=Math.max(73,Math.min(110,contentW/2-3));
    addDrawableChild(ButtonWidget.builder(Text.literal("Import pets.txt"),b->importLocal())
      .dimensions(x+w-buttonW-7,y+9,buttonW,19).build());
    addDrawableChild(ButtonWidget.builder(Text.literal("Reload"),b->{
      CatalogStore.INSTANCE.load();status="Reloaded "+CatalogStore.INSTANCE.size()+" entries";selected="";
    }).dimensions(x+w-buttonW-66,y+9,59,19).build());
  }
  private void importLocal(){
    status="Reading "+CatalogStore.importFile().toString();
    CompletableFuture.supplyAsync(()->{
      try{
        List<CatalogEntry> parsed=DumpParser.read(CatalogStore.importFile());
        int added=CatalogStore.INSTANCE.merge(parsed);
        return "Imported "+parsed.size()+" variants; "+added+" new entries";
      }catch(Exception e){return "Import failed: "+e.getMessage();}
    }).thenAccept(message->{
      if(client!=null)client.execute(()->{status=message;scroll=0;});
    });
  }
  /** One row per pet name in the grid; the rarity filter picks its variant. */
  List<CatalogEntry> visible(){
    Map<String,CatalogEntry> grouped=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for(CatalogEntry e:CatalogStore.INSTANCE.all()){
      if(query.isBlank()&&!e.category.equals(section))continue;
      if(!e.matches(query))continue;
      if(!rarity.equals("All")&&!rarity.equals(e.rarity))continue;
      String key=e.category+"|"+e.baseName();
      CatalogEntry old=grouped.get(key);
      if(old==null||rank(e.rarity)>rank(old.rarity))grouped.put(key,e);
    }
    return new ArrayList<>(grouped.values());
  }
  private int rank(String r){
    int idx=Arrays.asList(RARITIES).indexOf(r);
    return idx<0?0:idx;
  }
  CatalogEntry detail(List<CatalogEntry> visible){
    if(selected.isBlank())return visible.isEmpty()?null:visible.get(0);
    for(CatalogEntry e:visible)if((e.category+"|"+e.baseName()).equals(selected))return e;
    return visible.isEmpty()?null:visible.get(0);
  }
  @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
    PediaRenderer.draw(this,ctx,mouseX,mouseY);
    super.render(ctx,mouseX,mouseY,delta);
  }
  @Override public boolean mouseClicked(Click click,boolean doubled){
    if(super.mouseClicked(click,doubled))return true;
    if(click.button()!=0)return false;
    int mx=(int)click.x(),my=(int)click.y();
    if(mx>=contentX+5&&mx<contentX+contentW-5&&my>=rowsTop&&my<y+h-34){
      int index=scroll+(my-rowsTop)/36;
      List<CatalogEntry> entries=visible();
      if(index>=0&&index<entries.size()){
        CatalogEntry entry=entries.get(index);
        selected=entry.category+"|"+entry.baseName();
        detailScroll=0;return true;
      }
    }
    return false;
  }
  @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
    if(mx>=contentX&&mx<contentX+contentW){
      scroll=Math.max(0,scroll+(vertical>0?-2:2));return true;
    }
    if(right>0&&mx>=x+w-right){
      detailScroll=Math.max(0,detailScroll+(vertical>0?-2:2));return true;
    }
    return super.mouseScrolled(mx,my,horizontal,vertical);
  }
  @Override public boolean shouldPause(){return false;}
}
