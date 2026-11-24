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
  private static String selectedCategory = "Unsorted";
  public static void setCategory(String category) {
    if (WikiCategories.ALL.stream().anyMatch(c -> c.name().equals(category))) selectedCategory = category;
  }
  public static String selectedCategory() { return selectedCategory; }
  public static String capture(MinecraftClient client) {
    if(client.world==null||client.player==null||!(client.currentScreen instanceof HandledScreen<?> screen))
      return "Open a BattlePet wiki inventory first.";
    String title=screen.getTitle().getString();
    if(!LoreRules.isWikiPage(title))return "Open a BattlePet wiki inventory first.";
    String detected=WikiCategories.fromTitle(title);
    String category = !selectedCategory.equals("Unsorted") ? selectedCategory : detected;
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
      if(id.endsWith("glass_pane")||id.equals("minecraft:barrier")||id.equals("minecraft:arrow")||id.equals("minecraft:structure_void"))continue;
      String name=stack.getName().getString();
      if(name.isBlank()||name.equalsIgnoreCase("Next Page")||name.equalsIgnoreCase("Previous Page")
        ||name.equalsIgnoreCase("Close")||name.equalsIgnoreCase("Back")||name.equalsIgnoreCase("Locked Item!")||name.toLowerCase(java.util.Locale.ROOT).startsWith("search query"))continue;
      CatalogEntry entry=new CatalogEntry();
      entry.name=name;
      entry.category=category;
      entry.rarity=CatalogEntry.detectRarity(name);
      entry.itemId=id;entry.count=stack.getCount();entry.originalSlot=i;entry.source=title;entry.capturedAt=System.currentTimeMillis();
      LoreComponent lore=stack.get(DataComponentTypes.LORE);
      if(lore!=null)for(Text t:lore.lines())entry.lore.add(t.getString());
      if(entry.rarity.equals("Unknown"))for(String l:entry.lore){
        String upper=l.toUpperCase(java.util.Locale.ROOT).trim();
        if(upper.matches("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|UNIQUE|SPECIAL)( DUNGEON| GUILD)? (SWORD|BOW|CROSSBOW|AXE|SHIELD|WAND|SCYTHE|PET|HELMET|CHESTPLATE|LEGGINGS|BOOTS|ACCESSORY|NECKLACE|CLOAK|BELT|GLOVES|PICKAXE|DRILL|FISHING ROD|ITEM).*")){
          entry.rarity=CatalogEntry.detectRarity(upper); break;
        }
      }
      // The server calls every category Wiki Database. Match each actual item against
      // the shipped name/material/lore index; explicit target is only a tie-breaker.
      if(LoreRules.isPet(entry.lore)) entry.category="Pets";
      else {
        String matched=WikiPreset.category(name,id,entry.lore,category);
        entry.category=matched;
      }
      try{
        var ops=RegistryOps.of(JsonOps.INSTANCE,client.world.getRegistryManager());
        entry.stackJson=ItemStack.CODEC.encodeStart(ops,stack).result().map(Object::toString).orElse("");
      }catch(Exception ignored){}
      batch.add(entry);
    }
    try{
      int added=CatalogStore.INSTANCE.merge(batch);
      return "Saved "+batch.size()+" item stacks ("+added+" new) using wiki description matching; browse categories with B";
    }catch(IOException ex){return "Could not save catalog: "+ex.getMessage();}
  }
}
