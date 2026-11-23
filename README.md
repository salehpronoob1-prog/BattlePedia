# BattlePedia (Fabric 1.21.11)\n\n## New in v0.3: complete offline wiki and refreshed GUI\n\nThe main GUI now uses high-contrast item cards, pixel-perfect native item icons, crisp unshadowed text, spacious search/navigation, and category-sensitive detail screens. The mod ships with 3,134 entries from the 82-page BattlePet wiki dump across 21 non-pet categories, in addition to existing locally saved pet data. No separate import is required for these built-in entries. **Pets**, **Pet Items** and **Favorites** are pinned on the left, with expandable navigation for all other wiki categories. Browse a pet grid and click a pet to open its dedicated detail page with a larger native icon, individual rarity buttons, extracted stats and ability cards, source information, and the unmodified original wiki lore.\n\nYou can switch rarity on the detail page, use the global search in the sidebar, scroll panels independently, and save favorites locally. Missing player-head textures are a data limitation: press **O** in the real wiki GUI to capture the server's native player-head components. This is an actual GUI implementation but only CI compilation has been verified; please report any in-game issues.\n

Standalone, client-side BattlePet encyclopedia for Minecraft. No Meteor Client or server-side plugin required.

## Current features
- Press **B** to open the transparent purple/light-blue encyclopedia.
- All **22 original BattlePet Wiki categories**, plus Unsorted for unidentified entries.
- Search by pet name, category, rarity, stat, and ability description.
- Group pet rarities by pet name; change the rarity filter inside the app.
- Native Minecraft icon rendering and original lore descriptions.
- Local JSON database; changes persist between sessions.
- Press **O while the server's Wiki Database inventory is open** to capture the page without clicking any slots. The capture stores full ItemStack CODEC JSON (including profile data for custom player heads) when available.
- Import additional text dumps via the **Import** button. Built-in wiki items are already available on first install.

## Install
1. Download the compiled JAR from [GitHub Actions](../../actions), open the successful build, and download its `BattlePedia-jar` artifact.
2. Install the matching **Fabric Loader** and **Fabric API** for Minecraft **1.21.11**.
3. Put `battlepedia-*.jar` in your `.minecraft/mods` directory and launch the game.
4. Press **B** in-game to open the browser.

## Add your data
BattlePedia stores data in `.minecraft/config/battlepedia/entries.json`. There are two supported workflows:

**Preprocessed JSON:** Copy an exported `entries.json` into `.minecraft/config/battlepedia/entries.json` before launching. Use **Reload** in the interface if the game is already running.

**Original GUI dump:** Copy `pets.txt` into `.minecraft/config/battlepedia/imports/pets.txt`, then press **Import pets.txt** in the encyclopedia.

You can enrich entries with the real player-head skins by opening the matching BattlePet wiki page and pressing **O** while it is open. A chat message reports the number of captured entries. Capture does not automatically page or click server inventories.

The interface reads local files; **no cloud server, API keys, or online accounts** are needed. Future HTTPS synchronization can build on this cache.

## Data fidelity
- Pets can coexist/stack; **individual abilities** may have non-stacking restrictions.
- Original ability text is preserved and displayed; scaling and damage formulas are not guessed.
- Imported textual entries have default vanilla item icons. Exact head skins require in-game item component capture.
- The importer does not download from the server or perform destructive GUI actions. Capture uses the preloaded name, material and lore index to classify weapons, armor and other items automatically. If the exact item occurs in multiple categories with indistinguishable descriptions, the selected category is used as a fallback.

## Limitations / development status
This is an early working implementation. Compilation is checked through GitHub Actions, but gameplay testing on the BattlePet server is still needed. Crafting recipes, per-ability navigation, favorites, a 13-pet optimizer, and automatic wiki page traversal **are not implemented yet**. The new 21 non-pet category pages are preloaded. Items locked behind your game progression are not invented and retain only the available wiki data. Do not interpret a successful CI build as proof that every feature in the roadmap is implemented.

## Build
Java 21, Gradle and Fabric Loom. `gradle build` or run the included GitHub Actions workflow.
