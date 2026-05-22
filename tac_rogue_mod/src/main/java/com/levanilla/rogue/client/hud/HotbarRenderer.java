package com.levanilla.rogue.client.hud;

import com.levanilla.rogue.client.ClientPerformanceProfiler;
import com.levanilla.rogue.client.TacZGuiIconRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 専用ホットバー描画 — [GUN1][GUN2] | [MELEE] | [ITEM x9] | [AMMO1][AMMO2]
 */
public final class HotbarRenderer {

    private HotbarRenderer() {}

    private static final int SLOT_SIZE = 18;
    private static final int SEPARATOR_WIDTH = 4;
    private static final int EXTENDED_ITEM_GAP = 3;
    private static final int TOTAL_SLOTS = 2 + 1 + 9 + 4;
    private static final int TOTAL_WIDTH = TOTAL_SLOTS * SLOT_SIZE + SEPARATOR_WIDTH * 3 + EXTENDED_ITEM_GAP;
    private static final int HOTBAR_Y_OFFSET = 25;
    private static final int HOTBAR_TOP_PADDING = 11;
    private static final int HOTBAR_BOTTOM_PADDING = 3;
    private static final Component GUN_LABEL = Component.translatable("hud.tac_rogue.hotbar.gun");
    private static final Component KIT_LABEL = Component.translatable("hud.tac_rogue.hotbar.kit");
    private static final Component AMMO_LABEL = Component.translatable("hud.tac_rogue.hotbar.ammo");
    private static final Component AMMO_DIM_LABEL = Component.translatable("hud.tac_rogue.hotbar.ammo_dim");
    private static final String[] ITEM_SLOT_LABELS = {"4", "5", "6", "7", "8", "9", "E1", "E2", "E3"};

    public static void render(GuiGraphics graphics, Minecraft mc, Player player, int screenWidth, int screenHeight) {
        int slotSize = SLOT_SIZE;
        int separatorWidth = SEPARATOR_WIDTH;
        int extendedItemGap = EXTENDED_ITEM_GAP;
        Bounds bounds = bounds(screenWidth, screenHeight);
        int totalWidth = bounds.width();
        int baseX = bounds.x();
        int y = screenHeight - HOTBAR_Y_OFFSET;

        // 背景
        long sectionStart = ClientPerformanceProfiler.onHudSectionStart();
        graphics.fill(baseX - 3, y - 11, baseX + totalWidth + 3, y + slotSize + 3, 0xCC02070D);
        graphics.fill(baseX - 3, y - 11, baseX + totalWidth + 3, y - 9, 0xFF55DDAA);
        graphics.drawString(mc.font, GUN_LABEL, baseX + 3, y - 9, 0x88FF7755, false);
        graphics.drawString(mc.font, KIT_LABEL, baseX + 62, y - 9, 0x8822FF77, false);
        graphics.drawString(mc.font, AMMO_LABEL, baseX + totalWidth - 56, y - 9, 0x88FFFF66, false);
        ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_BG, sectionStart);

        int x = baseX;
        var inventory = player.getInventory();
        var items = inventory.items;
        int selectedSlot = inventory.selected;

        sectionStart = ClientPerformanceProfiler.onHudSectionStart();

        // === GUN SLOTS (0, 1) ===
        for (int i = 0; i < 2; i++) {
            boolean selected = i == selectedSlot;
            int slotColor = selected ? 0xFF884422 : 0x44FF4422;
            int borderColor = selected ? 0xFFFF6633 : 0x88FF4422;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);
            graphics.drawString(mc.font, String.valueOf(i + 1), x + 1, y + 1, 0x88FF4444, false);

            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 2);
                graphics.renderItemDecorations(mc.font, stack, x + 1, y + 2);
            }
            x += slotSize;
        }
        ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_GUNS, sectionStart);

        // セパレーター
        sectionStart = ClientPerformanceProfiler.onHudSectionStart();
        graphics.fill(x + 1, y + 2, x + 2, y + slotSize - 2, 0x66FFFFFF);
        x += separatorWidth;

        // === MELEE SLOT (2) ===
        {
            boolean selected = 2 == selectedSlot;
            int slotColor = selected ? 0xFF224488 : 0x442244FF;
            int borderColor = selected ? 0xFF3366FF : 0x882244FF;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);
            graphics.drawString(mc.font, "M", x + 1, y + 1, 0x884488FF, false);

            ItemStack stack = items.get(2);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 2);
                renderItemDecorationsIfNeeded(graphics, mc, player, stack, x + 1, y + 2);
            }
            x += slotSize;
        }
        ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_MELEE, sectionStart);

        // セパレーター
        sectionStart = ClientPerformanceProfiler.onHudSectionStart();
        graphics.fill(x + 1, y + 2, x + 2, y + slotSize - 2, 0x66FFFFFF);
        x += separatorWidth;

        // === ITEM SLOTS (3-11) ===
        for (int i = com.levanilla.rogue.core.GameConstants.SLOT_ITEM_START;
             i <= com.levanilla.rogue.core.GameConstants.SLOT_ITEM_END; i++) {
            if (i == 9) {
                graphics.fill(x - 1, y + 2, x, y + slotSize - 2, 0x5577FFAA);
                x += extendedItemGap;
            }
            boolean selected = i == selectedSlot;
            boolean extended = i >= 9;
            int slotColor = selected ? 0xFF225522 : extended ? 0x5533AA66 : 0x4422FF22;
            int borderColor = selected ? 0xFF33FF33 : extended ? 0xAA55DDAA : 0x8822AA22;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);
            String slotLabel = ITEM_SLOT_LABELS[i - com.levanilla.rogue.core.GameConstants.SLOT_ITEM_START];
            long itemLabelStart = ClientPerformanceProfiler.onHudSectionStart();
            graphics.pose().pushPose();
            graphics.pose().translate(x + 1, y + 1, 200);
            graphics.pose().scale(0.5f, 0.5f, 1.0f);
            graphics.drawString(mc.font, slotLabel, 0, 0, extended ? 0xAA88FFCC : 0x8822FF22, false);
            graphics.pose().popPose();
            ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_ITEM_LABELS, itemLabelStart);

            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                long itemIconStart = ClientPerformanceProfiler.onHudSectionStart();
                graphics.renderItem(stack, x + 1, y + 2);
                renderItemDecorationsIfNeeded(graphics, mc, player, stack, x + 1, y + 2);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_ITEM_ICONS, itemIconStart);
            }
            x += slotSize;
        }
        ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_ITEMS, sectionStart);

        // セパレーター
        sectionStart = ClientPerformanceProfiler.onHudSectionStart();
        graphics.fill(x + 1, y + 2, x + 2, y + slotSize - 2, 0x66FFFFFF);
        x += separatorWidth;

        // === AMMO SLOTS (12-15) ===
        for (int ammoIdx = 0; ammoIdx < 4; ammoIdx++) {
            int slotIndex = com.levanilla.rogue.core.GameConstants.SLOT_AMMO_GUN1_START + ammoIdx;
            // Gun 1 ammo = orange tint, Gun 2 ammo = cyan tint
            boolean isGun2 = ammoIdx >= 2;
            int slotColor = isGun2 ? 0x4400AAFF : 0x44FFFF00;
            int borderColor = isGun2 ? 0x880066AA : 0x88AAAA00;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);

            ItemStack ammoStack = items.get(slotIndex);
            if (!ammoStack.isEmpty()) {
                long ammoIconStart = ClientPerformanceProfiler.onHudSectionStart();
                renderAmmoSlotIcon(graphics, ammoStack, x + 1, y + 2);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_AMMO_ICONS, ammoIconStart);
                long ammoDecorStart = ClientPerformanceProfiler.onHudSectionStart();
                renderAmmoCount(graphics, mc, ammoStack, x + 1, y + 2);
                ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_AMMO_DECORATIONS, ammoDecorStart);
            } else {
                // 空スロット: 対応銃の弾薬タイプ名を小さく表示
                int gunSlot = isGun2 ? 1 : 0;
                ItemStack gunStack = items.get(gunSlot);
                if (isGunStack(gunStack)) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + 2, y + 7, 0);
                    graphics.pose().scale(0.5f, 0.5f, 1.0f);
                    graphics.drawString(mc.font, AMMO_DIM_LABEL, 0, 0, 0x44FFFFFF, false);
                    graphics.pose().popPose();
                }
            }
            x += slotSize;
        }
        ClientPerformanceProfiler.onHudSectionEnd(ClientPerformanceProfiler.HudSection.HOTBAR_AMMO, sectionStart);
    }

    private static void renderAmmoSlotIcon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!TacZGuiIconRenderer.renderLightweightIcon(graphics, Minecraft.getInstance().font, stack, x, y, false)) {
            graphics.renderItem(stack, x, y);
        }
    }

    private static void renderAmmoCount(GuiGraphics graphics, Minecraft mc, ItemStack stack, int x, int y) {
        if (stack.getCount() <= 1) return;
        String count = String.valueOf(stack.getCount());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.drawString(mc.font, count, x + 19 - 2 - mc.font.width(count), y + 6 + 3, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    private static boolean isGunStack(ItemStack stack) {
        var tag = stack.getTag();
        return !stack.isEmpty() && tag != null && tag.contains("GunId");
    }

    private static void renderItemDecorationsIfNeeded(GuiGraphics graphics, Minecraft mc, Player player, ItemStack stack, int x, int y) {
        if (stack.getCount() > 1 || stack.isBarVisible() || player.getCooldowns().isOnCooldown(stack.getItem())) {
            graphics.renderItemDecorations(mc.font, stack, x, y);
        }
    }

    public static Bounds bounds(int screenWidth, int screenHeight) {
        int baseX = (screenWidth - TOTAL_WIDTH) / 2;
        int y = screenHeight - HOTBAR_Y_OFFSET;
        return new Bounds(baseX, y - HOTBAR_TOP_PADDING, TOTAL_WIDTH, SLOT_SIZE + HOTBAR_TOP_PADDING + HOTBAR_BOTTOM_PADDING);
    }

    public record Bounds(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }
}
