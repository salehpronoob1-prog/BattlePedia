package dev.battlepedia;

import com.google.gson.JsonParser;
import com.google.gson.JsonOps;
import dev.battlepedia.data.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.Identifier;
import java.util.*;

/** Native Minecraft item rendering. Keeps captured head profiles and components. */
final class PediaRenderer {
  private static final int PURPLE=0xFFA879FF,BLUE=0xFF78D5FF,TEXT=0xFFE9EEFF,MUTED=0xFFB2BFD7;
  private static final Map<String,ItemStack> ICONS=new HashMap<>();
  private PediaRenderer(){}
  static void draw(BattlePediaScreen s,DrawContext ctx,int mx,int my){
    int x=s.x,y=s.y,w=s.w,h=s.h,side=s.side;
    var font=s.getTextRenderer();
    ctx.fill(x,y,x+w,y+h,0xBD14182D);
    ctx.fill(x,y,x+side,y+h,0xDA211B3A);
    ctx.fill(x,y,x+w,y+3,PURPLE);
    ctx.drawTextWithShadow(font,"BATTLEPEDIA",x+9,y+12,PURPLE);
    ctx.drawTextWithShadow(font,s.section, s.contentX+8,y+13,BLUE);
    ctx.drawTextWithShadow(font,s.status.length()>70?s.status.substring(0,67)+"...":s.status,x+side+10,y+h-15,MUTED);
    List<CatalogEntry> entries=s.visible();
    int maxRows=Math.max(1,(h-117)/36);
    int start=Math.min(s.scroll,Math.max(0,entries.size()-maxRows));
    s.scroll=start;
    int listRight=s.contentX+s.contentW-5;
    ctx.enableScissor(s.contentX+3,s.rowsTop,listRight,s.rowsTop+maxRows*36);
    for(int i=0;i<maxRows&&start+i<entries.size();i++){
      CatalogEntry e=entries.get(start+i);
      int ry=s.rowsTop+i*36;
      boolean highlighted=mx>=s.contentX+5&&mx<listRight&&my>=ry&&my<ry+34;
      boolean chosen=(e.category+"|"+e.baseName()).equals(s.selected);
      ctx.fill(s.contentX+5,ry,listRight,ry+33,chosen?0xBB554684:highlighted?0xA3444D73:0x77212A47);
      ctx.fill(s.contentX+5,ry,s.contentX+7,ry+33,chosen?PURPLE:0xFF3E527B);
      ItemStack stack=icon(e);
      ctx.drawItem(stack,s.contentX+12,ry+8);
      String display=trim(font,e.baseName(),Math.max(20,s.contentW-47));
      ctx.drawTextWithShadow(font,display,s.contentX+34,ry+6,TEXT);
      ctx.drawTextWithShadow(font,e.rarity,s.contentX+34,ry+19,rarityColor(e.rarity));
    }
    ctx.disableScissor();
    if(entries.isEmpty()) {
      ctx.drawTextWithShadow(font,"No entries for this filter",s.contentX+10,s.rowsTop+8,MUTED);
      ctx.drawTextWithShadow(font,"Copy pets.txt to config/battlepedia/imports/",s.contentX+10,s.rowsTop+24,MUTED);
      ctx.drawTextWithShadow(font,"then click Import pets.txt",s.contentX+10,s.rowsTop+36,MUTED);
    }
    ctx.drawTextWithShadow(font,entries.size()+" results  -  Scroll to browse",s.contentX+8,y+h-29,MUTED);
    if(s.right>0){
      int dx=x+w-s.right-5,dy=y+35,dw=s.right-7;
      ctx.fill(dx,dy,dx+dw,y+h-24,0xB51B233C);
      ctx.fill(dx,dy,dx+dw,dy+2,BLUE);
      CatalogEntry e=s.detail(entries);
      if(e!=null)detail(s,ctx,e,dx+8,dy+8,dw-16,y+h-34);
      else ctx.drawTextWithShadow(font,"Select an item",dx+8,dy+10,MUTED);
    }
    if(mx>=s.contentX+10&&mx<s.contentX+30&&my>=s.rowsTop&&my<y+h-34){
      int index=start+(my-s.rowsTop)/36;
      if(index>=0&&index<entries.size())ctx.drawItemTooltip(font,icon(entries.get(index)),mx,my);
    }
  }
  private static void detail(BattlePediaScreen s,DrawContext ctx,CatalogEntry e,int x,int y,int w,int bottom){
    var font=s.getTextRenderer();
    ctx.drawItem(icon(e),x,y);
    ctx.drawTextWithShadow(font,trim(font,e.baseName(),w-27),x+23,y+2,TEXT);
    ctx.drawTextWithShadow(font,e.rarity+"  /  "+e.category,x,y+24,rarityColor(e.rarity));
    ctx.fill(x,y+39,x+w,y+40,0xFF49597F);
    int line=0,first=s.detailScroll;
    List<String> lines=new ArrayList<>();
    lines.add("Original catalog description");
    if(e.lore!=null)for(String l:e.lore){
      if(l==null||l.isBlank()){lines.add("");continue;}
      lines.addAll(wrap(font,l,w));
    }
    if(e.lore==null||e.lore.isEmpty())lines.add("No original lore captured.");
    ctx.enableScissor(x,y+48,x+w,bottom);
    for(int i=first;i<lines.size();i++){
      int py=y+52+(line++)*12;
      if(py>=bottom)break;
      String value=lines.get(i);
      boolean restriction=value.toLowerCase(Locale.ROOT).contains("cannot be stacked");
      ctx.drawTextWithShadow(font,value,x,py,restriction?PURPLE:MUTED);
    }
    ctx.disableScissor();
  }
  private static List<String> wrap(net.minecraft.client.font.TextRenderer font,String text,int width){
    List<String> out=new ArrayList<>();
    StringBuilder current=new StringBuilder();
    for(String word:text.split("\\s+")){
      String trial=current.isEmpty()?word:current+" "+word;
      if(font.getWidth(trial)>width&&!current.isEmpty()){out.add(current.toString());current=new StringBuilder(word);}
      else current=new StringBuilder(trial);
    }
    if(!current.isEmpty())out.add(trim(font,current.toString(),width));
    if(out.isEmpty())out.add("");
    return out;
  }
  private static String trim(net.minecraft.client.font.TextRenderer font,String s,int width){
    return font.trimToWidth(s,Math.max(8,width));
  }
  private static int rarityColor(String r){
    return switch(r){case "Common"->0xFFAAAAAA;case "Uncommon"->0xFF55FF55;case "Rare"->0xFF5555FF;
      case "Epic"->0xFFAA00AA;case "Legendary"->0xFFFFAA00;case "Mythic"->0xFFFF55FF;default->MUTED;};
  }
  private static ItemStack icon(CatalogEntry e){
    String key=e.itemId+"|"+(e.stackJson==null?"":e.stackJson);
    ItemStack found=ICONS.get(key);
    if(found!=null)return found;
    ItemStack stack=ItemStack.EMPTY;
    MinecraftClient client=MinecraftClient.getInstance();
    if(e.stackJson!=null&&!e.stackJson.isBlank()&&client.world!=null){
      try{
        var ops=RegistryOps.of(JsonOps.INSTANCE,client.world.getRegistryManager());
        stack=ItemStack.CODEC.parse(ops,JsonParser.parseString(e.stackJson)).result().orElse(ItemStack.EMPTY);
      }catch(Exception ignored){}
    }
    if(stack.isEmpty()) {
      try{
        var item=Registries.ITEM.get(Identifier.of(e.itemId));
        if(item!=null)stack=new ItemStack(item);
      }catch(Exception ignored){stack=new ItemStack(Items.BOOK);}
    }
    if(stack.isEmpty())stack=new ItemStack(Items.BOOK);
    ICONS.put(key,stack);
    return stack;
  }
}
