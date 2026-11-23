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
  private CatalogStore() {}
  public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("battlepedia"); }
  public static Path dataFile() { return dir().resolve("entries.json"); }
  public static Path importFile() { return dir().resolve("imports").resolve("pets.txt"); }
  public synchronized void load() {
    entries.clear();
    if (!Files.exists(dataFile())) return;
    try (var r = Files.newBufferedReader(dataFile(),StandardCharsets.UTF_8)) {
      List<CatalogEntry> all = GSON.fromJson(r, LIST_TYPE);
      if(all!=null) for(CatalogEntry e:all) if(valid(e)) entries.put(e.key(),e);
    } catch(Exception ex) { LOG.error("Catalog load failed; existing data was not modified",ex); }
  }
  private static boolean valid(CatalogEntry e) {
    return e!=null && e.name!=null && !e.name.isBlank() && e.category!=null && e.rarity!=null;
  }
  public synchronized int size(){ return entries.size(); }
  public synchronized List<CatalogEntry> all(){ return new ArrayList<>(entries.values()); }
  public synchronized int merge(Collection<CatalogEntry> batch) throws IOException {
    int added=0;
    for(CatalogEntry e:batch){
      if(!valid(e))continue;
      CatalogEntry previous=entries.get(e.key());
      if(previous==null)added++;
      if(previous!=null && (e.stackJson==null || e.stackJson.isBlank()))e.stackJson=previous.stackJson;
      if(previous!=null && (e.lore==null || e.lore.isEmpty()))e.lore=previous.lore;
      entries.put(e.key(),e);
    }
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
