package dev.itemswap;

import dev.itemswap.config.ItemSwapConfig;
import dev.itemswap.gui.ItemSwapScreen;
import dev.itemswap.swap.SwapEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ItemSwapClient implements ClientModInitializer {
    public static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        ItemSwapConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.itemswap.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.itemswap"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new ItemSwapScreen());
                }
            }
            SwapEngine.tick(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SwapEngine.resetAll());
    }
}
