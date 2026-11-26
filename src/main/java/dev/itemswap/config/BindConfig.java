package dev.itemswap.config;

public class BindConfig {
    public enum Mode { STRICT, SMART }

    public boolean enabled = true;
    /** Translation key of the InputUtil.Key, e.g. "key.keyboard.x" or "key.mouse.4". */
    public String key = "key.keyboard.unknown";
    /** Target hotbar slot, 0..8 (shown to the player as 1..9). */
    public int hotbarSlot = 0;
    /** If true, the currently selected hotbar slot is used at the moment of the key press. */
    public boolean useCurrentSlot = false;
    public Mode mode = Mode.STRICT;
    /** Source slot id in the player screen handler: 9..35 (main inventory). -1 = not set. */
    public int sourceSlot = -1;
    /** Sample item id for smart search, e.g. "minecraft:potion". */
    public String sampleItem = "";
    /** Sample custom name (plain text). Empty = item without a custom name. */
    public String sampleName = "";
}
