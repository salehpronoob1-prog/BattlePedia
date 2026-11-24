package dev.battlepedia.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Type;
import java.util.*;

/** Local-first catalog with atomic persistence and no fabricated entries. */
public final class CatalogStore {
  public static final CatalogStore INSTANCE = new CatalogStore();
  private static final Logger LOG = LoggerFactory.getLogger("BattlePedia");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private static final Type LIST_TYPE = new TypeToken<List<CatalogEntry>>() {}.getType();
  private final Map<String,CatalogEntry> entries = new LinkedHashMap<>();
  public record Snapshot(long revision,List<CatalogEntry> entries){}
  private volatile Snapshot published=new Snapshot(0,List.of());
  public long revision(){return published.revision();}
  public Snapshot snapshot(){return published;}
  private void publish(){published=new Snapshot(published.revision()+1,List.copyOf(entries.values()));}
  private CatalogStore() {}
  public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("battlepedia"); }
  public static Path dataFile() { return dir().resolve("entries.json"); }
  public static Path importFile() { return dir().resolve("imports").resolve("pets.txt"); }
  public synchronized void load() {
    entries.clear();
    // Ship the full wiki database with the mod, including first-time installs.
    // Saved ItemStacks and player-head textures from the user's local database take precedence.
    for (CatalogEntry builtin : WikiPreset.records()) entries.put(builtin.key(), builtin);
    // Create the directories on first launch, including in separate
    // Modrinth instance folders. Existing catalogs are never overwritten.
    try {
      Files.createDirectories(importFile().getParent());
      if (Files.notExists(dataFile())) {
        try {
          Files.writeString(dataFile(), "[]\n", StandardCharsets.UTF_8,
              StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
          // Another initialization created it; load that file normally.
        }
      }
    } catch (IOException ex) {
      LOG.error("Could not initialize BattlePedia config directory at " + dir(), ex);
      publish();
      return;
    }
    try (var r = Files.newBufferedReader(dataFile(),StandardCharsets.UTF_8)) {
      List<CatalogEntry> all = GSON.fromJson(r, LIST_TYPE);
      if(all!=null) for(CatalogEntry e:all) if(valid(e)) {
        for(CatalogEntry normalized:LoreRules.normalize(e)) putPreferred(normalized);
      }
    } catch(Exception ex) { LOG.error("Catalog load failed; existing data was not modified",ex); }
    publish();
  }
  private static boolean valid(CatalogEntry e) {
    return e!=null && e.name!=null && !e.name.isBlank() && e.category!=null && e.rarity!=null;
  }
  public int size(){ return published.entries().size(); }
  public int sectionCount(String category){
    return (int) published.entries().stream().filter(e -> e.category.equals(category)).map(CatalogEntry::baseName).distinct().count();
  }
  public List<CatalogEntry> all(){ return new ArrayList<>(published.entries()); }
  private static boolean hasStack(CatalogEntry e) { return e.stackJson!=null && !e.stackJson.isBlank(); }
  private void putPreferred(CatalogEntry e) {
    CatalogEntry previous=entries.get(e.key());
    if(previous!=null && hasStack(previous) && !hasStack(e))return;
    if(previous!=null && (e.lore==null || e.lore.isEmpty())) e.lore=previous.lore;
    if(previous!=null && e.capturedAt==0)e.capturedAt=previous.capturedAt;
    entries.put(e.key(),e);
  }
  public synchronized int merge(Collection<CatalogEntry> batch) throws IOException {
    int added=0;
    for(CatalogEntry e:batch){
      if(!valid(e))continue;
      for(CatalogEntry normalized:LoreRules.normalize(e)){
        if(!entries.containsKey(normalized.key()))added++;
        putPreferred(normalized);
      }
    }
    publish();
    save();
    return added;
  }
  private void save() throws IOException {
    Files.createDirectories(dir());
    Path tmp=dir().resolve("entries.json.tmp");
    Files.writeString(tmp,GSON.toJson(new ArrayList<>(entries.values())),StandardCharsets.UTF_8);
    try{Files.move(tmp,dataFile(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    catch(AtomicMoveNotSupportedException e){Files.move(tmp,dataFile(),StandardCopyOption.REPLACE_EXISTING);}
  }
}
