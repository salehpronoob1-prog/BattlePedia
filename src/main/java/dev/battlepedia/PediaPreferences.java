package dev.battlepedia;

import dev.battlepedia.data.CatalogStore;
import java.nio.file.*;
import java.util.Properties;

final class PediaPreferences {
    enum Size { SMALL(65),MEDIUM(80),LARGE(90),MAXIMIZED(98);
        final int percent; Size(int p){percent=p;} }
    Size size=Size.MEDIUM;
    Size restore=Size.MEDIUM;
    boolean grid=true;
    SearchIndex.Sort sort=SearchIndex.Sort.RELEVANCE;
    int animation=1; // 0 off, 1 normal, 2 fast, 3 slow
    void load(){
        Properties p=new Properties();
        try(var r=Files.newBufferedReader(CatalogStore.dir().resolve("ui.properties"))){
            p.load(r);
            size=Size.valueOf(p.getProperty("size","MEDIUM"));
            restore=Size.valueOf(p.getProperty("restore","MEDIUM"));
            grid=Boolean.parseBoolean(p.getProperty("grid","true"));
            sort=SearchIndex.Sort.valueOf(p.getProperty("sort","RELEVANCE"));
            animation=Math.clamp(Integer.parseInt(p.getProperty("animation","1")),0,3);
        }catch(Exception ignored){}
    }
    void save(){
        Properties p=new Properties();
        p.setProperty("size",size.name());p.setProperty("restore",restore.name());
        p.setProperty("grid",Boolean.toString(grid));p.setProperty("sort",sort.name());
        p.setProperty("animation",Integer.toString(animation));
        try{
            Files.createDirectories(CatalogStore.dir());
            try(var w=Files.newBufferedWriter(CatalogStore.dir().resolve("ui.properties"))){p.store(w,"BattlePedia UI preferences");}
        }catch(Exception ignored){}
    }
    long duration(int ms){return switch(animation){case 0->0;case 2->ms/2;case 3->ms*2L;default->ms;};}
    String animationLabel(){return switch(animation){case 0->"Off";case 2->"Fast";case 3->"Slow";default->"Normal";};}
}
