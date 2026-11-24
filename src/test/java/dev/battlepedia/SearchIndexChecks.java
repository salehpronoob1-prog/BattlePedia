
package dev.battlepedia;
import dev.battlepedia.data.CatalogEntry;
import java.util.*;

/** Dependency-free regression suite, executed by Gradle check. */
public final class SearchIndexChecks {
    static CatalogEntry e(String name,String cat,String rarity,String... lore){
        CatalogEntry e=new CatalogEntry();e.name=name;e.category=cat;e.rarity=rarity;e.lore=List.of(lore);return e;
    }
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        var rare=e("Archer","Pets","Rare","Pet Ability: Arrow Rain","Deals arrow damage");
        var mythic=e("Archer","Pets","Mythic","Pet Ability: Healing");
        var bow=e("Arrow Bow","Ranged Weapons","Epic","Arrow Damage: +12");
        var artifact=e("Arrow","Artifacts","Rare","Strength: +2");
        var staff=e("Mana Staff","Magic Weapons","Legendary","Intelligence: +50");
        var petItem=e("Arrow Charm","Pet Items","Rare","Arrow damage");
        bow.capturedAt=42;
        var index=new SearchIndex(List.of(rare,mythic,bow,artifact,staff,petItem));
        var favorite=(java.util.function.Predicate<String>)(s->s.equals("Pets|Archer"));
        var relevance=SearchIndex.Sort.RELEVANCE;
        var pets=index.search("Pets","ARROW",relevance,favorite);
        check(pets.equals(List.of(rare)),"Pet scope and matching lower rarity");
        check(index.search("Home","arrow",relevance,favorite).getFirst()==artifact,"Exact name first");
        check(index.search("Home","arrow damage",relevance,favorite).size()==3,"Multiple words across lore");
        check(index.search("Weapons","",relevance,favorite).size()==2,"Parent category");
        check(index.search("Weapons","mana",relevance,favorite).equals(List.of(staff)),"Parent category search");
        check(index.search("Favorites","arrow",relevance,favorite).equals(List.of(rare)),"Favorite scope");
        check(index.search("Pets","",relevance,favorite).equals(List.of(mythic)),"Default highest rarity");
        check(index.search("Pets","arrow healing",relevance,favorite).isEmpty(),"Never combine different rarity lore");
        check(index.variants("Pets|Archer").size()==2,"Rarity switching");
        check(index.search("Home","",SearchIndex.Sort.RECENT,favorite).getFirst()==bow,"Recent capture");
        check(index.search("Home","",SearchIndex.Sort.FAVORITES,favorite).getFirst()==mythic,"Favorite sort");
        check(index.count("Pets")==1,"Unique pet count");
        System.out.println("SearchIndex checks passed");
    }
}
