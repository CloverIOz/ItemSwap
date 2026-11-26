package dev.itemswap.gui;

import dev.itemswap.config.BindConfig;
import dev.itemswap.config.ItemSwapConfig;
import dev.itemswap.swap.SwapEngine;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ItemSwapScreen extends Screen {
    private static final int CELL = 20;

    private final ItemSwapConfig cfg = ItemSwapConfig.get();
    private int selected = 0;
    private boolean listening = false;

    private ButtonWidget keyBtn, targetBtn, modeBtn, enabledBtn, handBtn, resetBtn;
    private final ButtonWidget[] tabs = new ButtonWidget[ItemSwapConfig.MAX_BINDS];

    private int cx, top, gridX, gridY;

    public ItemSwapScreen() {
        super(Text.literal("ItemSwap"));
    }

    private BindConfig bind() {
        return cfg.binds.get(selected);
    }

    // ----------------------------------------------------------------- init

    @Override
    protected void init() {
        cx = width / 2;
        top = height / 2 - 100;
        gridX = cx - 200;
        gridY = top + 26;

        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i] = addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(i + 1)), btn -> {
                selected = idx;
                listening = false;
                refresh();
            }).dimensions(gridX + i * 37, top, 33, 20).build());
        }

        int rx = cx;
        int rw = 200;

        keyBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
            listening = true;
            refresh();
        }).dimensions(rx, top + 26, rw, 20).build());

        targetBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
            BindConfig b = bind();
            if (b.useCurrentSlot) {
                b.useCurrentSlot = false;
                b.hotbarSlot = 0;
            } else if (b.hotbarSlot >= 8) {
                b.useCurrentSlot = true;
            } else {
                b.hotbarSlot++;
            }
            refresh();
        }).dimensions(rx, top + 50, rw, 20).build());

        modeBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
            BindConfig b = bind();
            b.mode = b.mode == BindConfig.Mode.STRICT ? BindConfig.Mode.SMART : BindConfig.Mode.STRICT;
            refresh();
        }).dimensions(rx, top + 74, rw, 20).build());

        enabledBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
            BindConfig b = bind();
            b.enabled = !b.enabled;
            refresh();
        }).dimensions(rx, top + 98, 98, 20).build());

        handBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Образец из руки"), btn -> {
            if (client != null && client.player != null) {
                if (!takeSample(client.player.getMainHandStack(), bind())) {
                    client.player.sendMessage(Text.literal("[ItemSwap] Рука пуста").formatted(Formatting.RED), true);
                }
            }
        }).dimensions(rx + 102, top + 98, 98, 20).build());

        resetBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить бинд"), btn -> {
            cfg.binds.set(selected, new BindConfig());
            listening = false;
            refresh();
        }).dimensions(rx, top + 122, rw, 20).build());

        addDrawableChild(new DelaySlider(cx - 200, top + 152, 400, 20));

        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), btn -> close())
                .dimensions(cx - 100, top + 178, 200, 20).build());

        refresh();
    }

    private void refresh() {
        if (keyBtn == null) return;
        BindConfig b = bind();

        if (listening) {
            keyBtn.setMessage(Text.literal("> Нажмите клавишу / кнопку мыши <").formatted(Formatting.YELLOW));
        } else {
            keyBtn.setMessage(Text.literal("Клавиша: ").append(keyText(b.key)));
        }

        targetBtn.setMessage(Text.literal("Слот хотбара: "
                + (b.useCurrentSlot ? "текущий в руке" : String.valueOf(b.hotbarSlot + 1))));
        modeBtn.setMessage(Text.literal("Режим: "
                + (b.mode == BindConfig.Mode.STRICT ? "По ячейке" : "По типу / названию")));
        enabledBtn.setMessage(Text.literal("Бинд: " + (b.enabled ? "ВКЛ" : "ВЫКЛ")));

        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setMessage(Text.literal(String.valueOf(i + 1))
                    .formatted(i == selected ? Formatting.YELLOW : Formatting.WHITE));
        }
    }

    private static Text keyText(String id) {
        try {
            InputUtil.Key k = InputUtil.fromTranslationKey(id);
            if (k == InputUtil.UNKNOWN_KEY || k.getCode() < 0) return Text.literal("не задана");
            return k.getLocalizedText();
        } catch (Exception e) {
            return Text.literal("не задана");
        }
    }

    private static boolean takeSample(ItemStack stack, BindConfig b) {
        if (stack.isEmpty()) return false;
        b.sampleItem = Registries.ITEM.getId(stack.getItem()).toString();
        b.sampleName = SwapEngine.nameOf(stack);
        return true;
    }

    // --------------------------------------------------------------- render

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        int c = width / 2;
        int t = height / 2 - 100;
        ctx.fill(c - 210, t - 20, c + 210, t + 208, 0xD0101010);
        ctx.drawBorder(c - 210, t - 20, 420, 228, 0xFF555555);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawText(textRenderer, "ItemSwap", cx - 205, top - 14, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Бинд " + (selected + 1) + " из " + ItemSwapConfig.MAX_BINDS,
                cx, top + 6, 0xFFFFFF55, true);

        drawGrid(ctx, mouseX, mouseY);

        BindConfig b = bind();
        String src = b.sourceSlot >= 9
                ? "ряд " + ((b.sourceSlot - 9) / 9 + 1) + ", ячейка " + ((b.sourceSlot - 9) % 9 + 1)
                : "не выбран";
        String sample = b.sampleItem.isEmpty()
                ? "не задан"
                : b.sampleItem + (b.sampleName.isEmpty() ? "" : " «" + b.sampleName + "»");

        int ty = gridY + 4 * CELL + 10;
        ctx.drawText(textRenderer, textRenderer.trimToWidth("Источник: " + src, 190), gridX, ty, 0xFF55FF55, true);
        ctx.drawText(textRenderer, textRenderer.trimToWidth("Образец: " + sample, 190), gridX, ty + 12, 0xFFAAAAAA, true);

        ctx.drawText(textRenderer, "Клик по ячейке: источник (+образец)", cx, top + 150 - 26, 0xFF888888, false);
    }

    private void drawGrid(DrawContext ctx, int mouseX, int mouseY) {
        if (client == null || client.player == null) return;
        ClientPlayerEntity p = client.player;
        BindConfig b = bind();

        ItemStack tooltip = ItemStack.EMPTY;
        int tx = 0, ty = 0;

        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                int x = gridX + c * CELL;
                int y = gridY + r * CELL + (r == 3 ? 6 : 0);
                int invIndex = r == 3 ? c : 9 + r * 9 + c;
                ItemStack st = p.getInventory().getStack(invIndex);

                ctx.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF373737);

                boolean hover = mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2;
                if (hover) ctx.fill(x, y, x + CELL - 2, y + CELL - 2, 0x60FFFFFF);

                if (r < 3 && b.sourceSlot == invIndex) {
                    ctx.drawBorder(x - 1, y - 1, CELL, CELL, 0xFF55FF55);
                }
                if (r == 3 && !b.useCurrentSlot && b.hotbarSlot == c) {
                    ctx.drawBorder(x - 1, y - 1, CELL, CELL, 0xFF55FFFF);
                }

                if (!st.isEmpty()) ctx.drawItem(st, x + 1, y + 1);

                if (hover && !st.isEmpty()) {
                    tooltip = st;
                    tx = mouseX;
                    ty = mouseY;
                }
            }
        }

        if (!tooltip.isEmpty()) {
            ctx.drawItemTooltip(textRenderer, tooltip, tx, ty);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listening) {
            if (button >= 2) {
                bind().key = InputUtil.Type.MOUSE.createFromCode(button).getTranslationKey();
            }
            listening = false;
            refresh();
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && client != null && client.player != null) {
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 9; c++) {
                    int x = gridX + c * CELL;
                    int y = gridY + r * CELL + (r == 3 ? 6 : 0);
                    if (mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2) {
                        BindConfig b = bind();
                        if (r == 3) {
                            b.useCurrentSlot = false;
                            b.hotbarSlot = c;
                        } else {
                            int invIndex = 9 + r * 9 + c;
                            b.sourceSlot = invIndex;
                            takeSample(client.player.getInventory().getStack(invIndex), b);
                        }
                        refresh();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // cancel
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                bind().key = "key.keyboard.unknown";
            } else {
                bind().key = InputUtil.fromKeyCode(keyCode, scanCode).getTranslationKey();
            }
            listening = false;
            refresh();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        ItemSwapConfig.save();
    }

    // --------------------------------------------------------------- slider

    private class DelaySlider extends SliderWidget {
        DelaySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(),
                    (ItemSwapConfig.get().delayMs - ItemSwapConfig.MIN_DELAY)
                            / (double) (ItemSwapConfig.MAX_DELAY - ItemSwapConfig.MIN_DELAY));
            updateMessage();
        }

        private int current() {
            return ItemSwapConfig.MIN_DELAY
                    + (int) Math.round(value * (ItemSwapConfig.MAX_DELAY - ItemSwapConfig.MIN_DELAY));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Задержка: " + current() + " мс (±15 случайно)"));
        }

        @Override
        protected void applyValue() {
            ItemSwapConfig.get().delayMs = current();
        }
    }
}
