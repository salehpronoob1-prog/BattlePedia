package dev.battlepedia;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.battlepedia.data.CatalogStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Type;
import java.util.*;

/** Per-instance favorites, deliberately kept separate from imported catalog data. */
final class FavoritesStore {
    private static final Gson GSON = new Gson();
    private static final Type SET_TYPE = new TypeToken<Set<String>>() {}.getType();
    private final Set<String> keys = new HashSet<>();

    boolean has(String id) { return keys.contains(id); }

    void toggle(String id) {
        if (!keys.add(id)) keys.remove(id);
        try { save(); } catch (IOException ignored) {
            // Current-session favorite is still updated if config is read-only.
        }
    }

    void load() {
        Path file = CatalogStore.dir().resolve("favorites.json");
        if (!Files.isRegularFile(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Set<String> saved = GSON.fromJson(reader, SET_TYPE);
            if (saved != null) keys.addAll(saved);
        } catch (Exception ignored) { }
    }

    private void save() throws IOException {
        Files.createDirectories(CatalogStore.dir());
        Path file = CatalogStore.dir().resolve("favorites.json");
        Path tmp = CatalogStore.dir().resolve("favorites.json.tmp");
        Files.writeString(tmp, GSON.toJson(new TreeSet<>(keys)), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
