package dev.battlepedia;

import dev.battlepedia.data.CatalogStore;
import dev.battlepedia.importer.WikiCapture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Independent Fabric client: B = encyclopedia, O = capture an open wiki page. */
public final class BattlePediaClient implements ClientModInitializer {
  private static KeyBinding openKey;
  private boolean captureHeld=false;
  @Override public void onInitializeClient(){
    CatalogStore.INSTANCE.load();
    openKey=KeyBindingHelper.registerKeyBinding(new KeyBinding(
      "key.battlepedia.open",GLFW.GLFW_KEY_B,
      new KeyBinding.Category(Identifier.of("battlepedia","general"))));
    ClientTickEvents.END_CLIENT_TICK.register(client->{
      while(openKey.wasPressed()) client.setScreen(new BattlePediaScreen());
      // Screen keyboard events often suppress regular keybind state. Poll only
      // while a container GUI is open, with explicit one-press capture.
      boolean held=client.getWindow()!=null && GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_O)==GLFW.GLFW_PRESS;
      if(held&&!captureHeld && client.currentScreen instanceof HandledScreen<?> && client.player!=null){
        String title=client.currentScreen.getTitle().getString();
        if(title.toLowerCase(java.util.Locale.ROOT).contains("wiki")||
          !dev.battlepedia.data.WikiCategories.fromTitle(title).equals("Unsorted") ||
          !WikiCapture.selectedCategory().equals("Unsorted")){
          client.player.sendMessage(Text.literal("[BattlePedia] "+WikiCapture.capture(client)),false);
        }
      }
      captureHeld=held;
    });
  }
}
