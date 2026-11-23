package dev.battlepedia.importer;

import com.mojang.serialization.JsonOps;
import dev.battlepedia.data.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Captures real GUI ItemStacks without clicking server menus or sending packets. */
public final class WikiCapture {
  private WikiCapture(){}
  public static String capture(MinecraftClient client) {
    if(client.world==null||client.player==null||!(client.currentScreen instanceof HandledScreen<?> screen))
      return "Open a BattlePet wiki inventory first.";
    String title=screen.getTitle().getString();
    String category=WikiCategories.fromTitle(title);
    // The top-level wiki is navigation, not catalog data.
    if(title.equalsIgnoreCase("BattlePet Wiki"))return "Open a category before capturing.";
    List<CatalogEntry> batch=new ArrayList<>();
    var handler=client.player.currentScreenHandler;
    int menuSlots=Math.max(0,handler.slots.size()-36);
    for(int i=0;i<menuSlots;i++){
      Slot slot=handler.slots.get(i);
      ItemStack stack=slot.getStack();
      if(stack.isEmpty())continue;
      String id=Registries.ITEM.getId(stack.getItem()).toString();
      if(id.endsWith("glass_pane")||id.equals("minecraft:barrier")||id.equals("minecraft:arrow"))continue;
      String name=stack.getName().getString();
      if(name.isBlank()||name.equalsIgnoreCase("Next Page")||name.equalsIgnoreCase("Previous Page")
        ||name.equalsIgnoreCase("Close")||name.toLowerCase(java.util.Locale.ROOT).startsWith("search query"))continue;
      CatalogEntry entry=new CatalogEntry();
      entry.name=name;entry.category=(category.equals("Unsorted") && title.contains("Wiki Database") && id.equals("minecraft:player_head"))?"Pets":category;
      entry.rarity=CatalogEntry.detectRarity(name);
      entry.itemId=id;entry.count=stack.getCount();entry.originalSlot=i;entry.source=title;
      LoreComponent lore=stack.get(DataComponentTypes.LORE);
      if(lore!=null)for(Text t:lore.lines())entry.lore.add(t.getString());
      if(entry.rarity.equals("Unknown"))for(String l:entry.lore){
        String r=l.toUpperCase(java.util.Locale.ROOT).trim().matches("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC) PET.*")?CatalogEntry.detectRarity(l):"Unknown";
        if(!r.equals("Unknown")){entry.rarity=r;break;}
      }
      try{
        var ops=RegistryOps.of(JsonOps.INSTANCE,client.world.getRegistryManager());
        entry.stackJson=ItemStack.CODEC.encodeStart(ops,stack).result().map(Object::toString).orElse("");
      }catch(Exception ignored){}
      batch.add(entry);
    }
    try{
      int added=CatalogStore.INSTANCE.merge(batch);
      return "Captured "+batch.size()+" entries ("+added+" new) from "+title;
    }catch(IOException ex){return "Could not save catalog: "+ex.getMessage();}
  }
}
