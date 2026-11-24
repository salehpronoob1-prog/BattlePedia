package dev.battlepedia;

import dev.battlepedia.data.CatalogEntry;
import java.util.*;
import java.util.function.Predicate;

/** Immutable text index; no ItemStack decoding or disk reads during a search. */
final class SearchIndex {
    static final Map<String,List<String>> GROUPS=Map.of(
        "Weapons",List.of("Melee Weapons","Ranged Weapons","Magic Weapons","Tank Weapons","Executioner Weapons"),
        "Armor",List.of("Helmets","Chestplates","Leggings","Boots"),
        "Equipment",List.of("Necklaces","Cloaks","Belts","Gloves"),
        "Resources",List.of("Pickaxes & Drills","Fishing Rods"));
    enum Sort { RELEVANCE, ALPHABETICAL, RARITY, RECENT, FAVORITES }
    record Indexed(CatalogEntry entry,String id,String name,String ability,String stats,String text,int rarity){}
    record Match(Indexed item,int score){}
    private final List<Indexed> rows=new ArrayList<>();
    private final Map<String,List<CatalogEntry>> variants=new HashMap<>();
    private final Map<String,Integer> counts=new HashMap<>();

    SearchIndex(Collection<CatalogEntry> entries){
        for(CatalogEntry e:entries){
            String id=identity(e),name=normal(e.baseName());
            StringBuilder ability=new StringBuilder(),stats=new StringBuilder(),text=new StringBuilder();
            for(String line:e.lore==null?List.<String>of():e.lore){
                String l=normal(line);
                text.append(l).append(' ');
                if(l.matches("^(pet |item |summon )?ability:.*"))ability.append(l).append(' ');
                if(l.matches("^[a-z][a-z0-9' %/()-]{1,40}:\\s*[+\\-]?\\d+.*"))stats.append(l).append(' ');
            }
            rows.add(new Indexed(e,id,name,ability.toString(),stats.toString(),
                normal(e.name+" "+e.category+" "+e.rarity+" "+e.itemId+" "+e.source)+" "+text,
                rarity(e.rarity)));
            variants.computeIfAbsent(id,k->new ArrayList<>()).add(e);
        }
        for(List<CatalogEntry> group:variants.values()){
            group.sort(Comparator.comparingInt(e->rarity(e.rarity)));
            counts.merge(group.getFirst().category,1,Integer::sum);
        }
    }
    static String identity(CatalogEntry e){return e.category+"|"+e.baseName();}
    static String normal(String value){
        return value==null?"":value.replaceAll("(?i)§[0-9a-fk-or]","").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");
    }
    static int rarity(String r){
        return switch(r==null?"Unknown":r){
            case "Common"->1;case "Uncommon"->2;case "Rare"->3;case "Epic"->4;
            case "Legendary"->5;case "Mythic"->6;case "Unique","Special"->7;default->0;
        };
    }
    static boolean inScope(String scope,CatalogEntry e,Predicate<String> favorite){
        if(scope.equals("Home"))return true;
        if(scope.equals("Favorites"))return favorite.test(identity(e));
        return GROUPS.getOrDefault(scope,List.of(scope)).contains(e.category);
    }
    private static boolean containsAll(String text,String[] words){
        for(String word:words)if(!text.contains(word))return false;
        return true;
    }
    private static int score(Indexed row,String query,String[] words){
        if(query.isEmpty())return 0;
        if(!containsAll(row.text,words))return -1;
        if(row.name.equals(query))return 500;
        if(containsAll(row.name,words))return 400;
        if(containsAll(row.ability,words))return 300;
        if(containsAll(row.stats,words))return 200;
        return 100;
    }
    List<CatalogEntry> search(String scope,String raw,Sort sort,Predicate<String> favorite){
        String q=normal(raw);
        String[] words=q.isEmpty()?new String[0]:q.split(" ");
        Map<String,Match> best=new LinkedHashMap<>();
        for(Indexed row:rows){
            if(!inScope(scope,row.entry,favorite))continue;
            int score=score(row,q,words);
            if(score<0)continue;
            Match old=best.get(row.id);
            if(old==null||score>old.score||score==old.score&&(sort==Sort.RECENT && row.entry.capturedAt!=old.item.entry.capturedAt ? row.entry.capturedAt>old.item.entry.capturedAt : row.rarity>old.item.rarity))
                best.put(row.id,new Match(row,score));
        }
        Comparator<Match> name=Comparator.comparing(m->m.item.name);
        Comparator<Match> order=switch(sort){
            case ALPHABETICAL -> name;
            case RARITY -> Comparator.<Match>comparingInt(m->m.item.rarity).reversed();
            case RECENT -> Comparator.<Match>comparingLong(m->m.item.entry.capturedAt).reversed();
            case FAVORITES -> Comparator.<Match>comparingInt(m->favorite.test(m.item.id)?1:0).reversed();
            default -> Comparator.<Match>comparingInt(Match::score).reversed();
        };
        return best.values().stream().sorted(order.thenComparing(name).thenComparing(m->m.item.id))
            .map(m->m.item.entry).toList();
    }
    List<CatalogEntry> variants(String id){return variants.getOrDefault(id,List.of());}
    int count(String category){return counts.getOrDefault(category,0);}
}
