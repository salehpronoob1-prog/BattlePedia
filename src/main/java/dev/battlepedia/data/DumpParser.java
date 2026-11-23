package dev.battlepedia.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Imports GUI Dump output from the BattlePet wiki. Never guesses missing values. */
public final class DumpParser {
  private static final Pattern ITEM = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\s+x([0-9]+))?");
  private DumpParser(){}
  public static List<CatalogEntry> read(Path source) throws IOException {
    if(!Files.exists(source))throw new IOException("Missing input: "+source);
    List<CatalogEntry> entries=new ArrayList<>();
    String title="";
    try(BufferedReader reader=Files.newBufferedReader(source,StandardCharsets.UTF_8)){
      String line;
      while((line=reader.readLine())!=null){
        if(line.startsWith("===== GUI DUMP PAGE")){
          String[] header=line.split("\\|",2);
          title=header.length>1?header[1].replace("=====","").trim():"";
          continue;
        }
        if(!line.startsWith("Slot "))continue;
        String[] bits=line.split("\\s*\\|\\s*",4);
        if(bits.length<3)continue;
        String name=bits[1].trim();
        if(name.isEmpty()||name.equalsIgnoreCase("Close")||name.equalsIgnoreCase("Next Page")
          ||name.equalsIgnoreCase("Previous Page")||name.toLowerCase(Locale.ROOT).startsWith("search query:"))continue;
        Matcher m=ITEM.matcher(bits[2]);
        if(!m.find())continue;
        String itemId=m.group(1);
        if(itemId.endsWith("glass_pane")||itemId.equals("minecraft:barrier")||itemId.equals("minecraft:arrow"))continue;
        CatalogEntry entry=new CatalogEntry();
        entry.name=name;
        entry.itemId=itemId;
        entry.count=m.group(2)==null?1:Integer.parseInt(m.group(2));
        entry.category=WikiCategories.fromTitle(title);
        entry.rarity=CatalogEntry.detectRarity(name);
        entry.source=title;
        try{entry.originalSlot=Integer.parseInt(bits[0].replace("Slot","").trim());}
        catch(NumberFormatException ignored){}
        if(bits.length>3){
          String lore=bits[3].trim();
          if(lore.startsWith("Lore:"))for(String l:lore.substring(5).split(" / ",-1))entry.lore.add(l.trim());
        }
        if(entry.rarity.equals("Unknown")){
          for(String l:entry.lore){
            String r=CatalogEntry.detectRarity(l);
            if(!r.equals("Unknown")){entry.rarity=r;break;}
          }
        }
        entries.add(entry);
      }
    }
    return entries;
  }
}
