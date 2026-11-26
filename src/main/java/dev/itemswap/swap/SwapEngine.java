package dev.itemswap.swap;

import dev.itemswap.config.BindConfig;
import dev.itemswap.config.ItemSwapConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Legit toggle swap: open inventory -> delay -> SWAP click -> delay -> close.
 * No item usage is ever triggered, the player presses RMB himself.
 */
public final class SwapEngine {
    private enum Phase { IDLE, WAIT_SWAP, WAIT_CLOSE }

    private static final class State {
        boolean swapped;
        int fromSlot = -1; // screen slot id the item was taken from
        int hotbar = 0;    // hotbar index used for the swap
    }

    private static final int N = ItemSwapConfig.MAX_BINDS;
    private static final State[] STATES = new State[N];
    private static final boolean[] WAS_DOWN = new boolean[N];
    private static final Random RNG = new Random();

    static {
        for (int i = 0; i < N; i++) STATES[i] = new State();
    }

    private static Phase phase = Phase.IDLE;
    private static long nextAt = 0;
    private static int activeBind = -1;
    private static boolean reversing = false;
    private static int plannedHotbar = 0;

    private SwapEngine() {}

    public static void resetAll() {
        for (State s : STATES) {
            s.swapped = false;
            s.fromSlot = -1;
        }
        phase = Phase.IDLE;
        activeBind = -1;
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            resetAll();
            return;
        }
        if (phase != Phase.IDLE) {
            process(client);
            updateKeys(client, false);
        } else {
            updateKeys(client, client.currentScreen == null);
        }
    }

    // ---------------------------------------------------------------- keys

    private static void updateKeys(MinecraftClient client, boolean allowTrigger) {
        ItemSwapConfig cfg = ItemSwapConfig.get();
        long window = client.getWindow().getHandle();
        for (int i = 0; i < N; i++) {
            BindConfig b = cfg.binds.get(i);
            boolean down = b.enabled && isDown(window, b.key);
            if (down && !WAS_DOWN[i] && allowTrigger && phase == Phase.IDLE) {
                start(client, i);
            }
            WAS_DOWN[i] = down;
        }
    }

    private static boolean isDown(long window, String keyId) {
        if (keyId == null || keyId.isEmpty()) return false;
        InputUtil.Key k;
        try {
            k = InputUtil.fromTranslationKey(keyId);
        } catch (Exception e) {
            return false;
        }
        if (k == InputUtil.UNKNOWN_KEY || k.getCode() < 0) return false;
        if (k.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, k.getCode()) == GLFW.GLFW_PRESS;
        }
        if (k.getCategory() == InputUtil.Type.KEYSYM) {
            return InputUtil.isKeyPressed(window, k.getCode());
        }
        return false;
    }

    // ---------------------------------------------------------------- start

    private static void start(MinecraftClient client, int i) {
        if (client.player.isCreative()) {
            msg(client, "Креатив не поддерживается", Formatting.RED);
            return;
        }
        ItemSwapConfig cfg = ItemSwapConfig.get();
        BindConfig b = cfg.binds.get(i);
        State st = STATES[i];

        if (st.swapped) {
            reversing = true;
            plannedHotbar = st.hotbar;
        } else {
            if (b.mode == BindConfig.Mode.STRICT && (b.sourceSlot < 9 || b.sourceSlot > 35)) {
                msg(client, "Бинд " + (i + 1) + ": не выбрана ячейка", Formatting.RED);
                return;
            }
            if (b.mode == BindConfig.Mode.SMART && b.sampleItem.isEmpty()) {
                msg(client, "Бинд " + (i + 1) + ": не задан образец предмета", Formatting.RED);
                return;
            }
            int hb = b.useCurrentSlot ? client.player.getInventory().selectedSlot : b.hotbarSlot;
            for (int j = 0; j < N; j++) {
                if (j != i && STATES[j].swapped && STATES[j].hotbar == hb) {
                    msg(client, "Слот хотбара " + (hb + 1) + " занят другим биндом", Formatting.RED);
                    return;
                }
            }
            reversing = false;
            plannedHotbar = hb;
        }

        activeBind = i;
        client.setScreen(new InventoryScreen(client.player));
        phase = Phase.WAIT_SWAP;
        nextAt = System.currentTimeMillis() + jitter();
    }

    // -------------------------------------------------------------- process

    private static void process(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (now < nextAt) return;

        // The player closed the inventory himself (or another screen appeared): abort.
        if (!(client.currentScreen instanceof InventoryScreen)) {
            phase = Phase.IDLE;
            activeBind = -1;
            return;
        }

        if (phase == Phase.WAIT_SWAP) {
            ItemSwapConfig cfg = ItemSwapConfig.get();
            BindConfig b = cfg.binds.get(activeBind);
            State st = STATES[activeBind];
            ScreenHandler handler = client.player.currentScreenHandler;

            if (reversing) {
                // Always swap back, even if the hotbar slot became empty.
                click(client, handler, st.fromSlot, st.hotbar);
                st.swapped = false;
                st.fromSlot = -1;
            } else {
                int src = findSource(handler, b);
                if (src < 0) {
                    msg(client, "Предмет не найден", Formatting.RED);
                    client.player.closeHandledScreen();
                    phase = Phase.IDLE;
                    activeBind = -1;
                    return;
                }
                click(client, handler, src, plannedHotbar);
                st.swapped = true;
                st.fromSlot = src;
                st.hotbar = plannedHotbar;
            }
            phase = Phase.WAIT_CLOSE;
            nextAt = now + jitter();
        } else if (phase == Phase.WAIT_CLOSE) {
            client.player.closeHandledScreen();
            phase = Phase.IDLE;
            activeBind = -1;
        }
    }

    private static void click(MinecraftClient client, ScreenHandler handler, int slot, int hotbar) {
        client.interactionManager.clickSlot(handler.syncId, slot, hotbar, SlotActionType.SWAP, client.player);
    }

    // --------------------------------------------------------------- search

    private static int findSource(ScreenHandler handler, BindConfig b) {
        boolean validPreferred = b.sourceSlot >= 9 && b.sourceSlot <= 35;

        if (b.mode == BindConfig.Mode.STRICT) {
            if (!validPreferred) return -1;
            return handler.getSlot(b.sourceSlot).getStack().isEmpty() ? -1 : b.sourceSlot;
        }

        // SMART: try the remembered cell first, then scan the whole main inventory.
        if (validPreferred && matches(handler.getSlot(b.sourceSlot).getStack(), b)) {
            return b.sourceSlot;
        }
        for (int s = 9; s <= 35; s++) {
            if (matches(handler.getSlot(s).getStack(), b)) return s;
        }
        return -1;
    }

    public static boolean matches(ItemStack stack, BindConfig b) {
        if (stack.isEmpty() || b.sampleItem.isEmpty()) return false;
        if (!Registries.ITEM.getId(stack.getItem()).toString().equals(b.sampleItem)) return false;
        return nameOf(stack).equals(b.sampleName);
    }

    public static String nameOf(ItemStack stack) {
        Text n = stack.get(DataComponentTypes.CUSTOM_NAME);
        return n == null ? "" : n.getString();
    }

    // ----------------------------------------------------------------- misc

    private static long jitter() {
        int base = ItemSwapConfig.get().delayMs;
        int j = RNG.nextInt(31) - 15; // -15..+15 ms
        return Math.max(30, base + j);
    }

    private static void msg(MinecraftClient client, String text, Formatting color) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[ItemSwap] " + text).formatted(color), true);
        }
    }
}
