package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 専用ホットバー描画 — [GUN1][GUN2] | [MELEE] | [ITEM x9] | [AMMO1][AMMO2]
 */
public final class HotbarRenderer {

    private HotbarRenderer() {}

    public static void render(GuiGraphics graphics, Minecraft mc, Player player, int screenWidth, int screenHeight) {
        int slotSize = 18;
        int separatorWidth = 4;
        int extendedItemGap = 3;
        // [GUN1][GUN2] | [MELEE] | [ITEM x9] | [AMMO x4]
        int totalSlots = 2 + 1 + 9 + 4; // 16 slots
        int totalWidth = totalSlots * slotSize + separatorWidth * 3 + extendedItemGap;
        int baseX = (screenWidth - totalWidth) / 2;
        int y = screenHeight - 25;

        // 背景
        graphics.fill(baseX - 3, y - 11, baseX + totalWidth + 3, y + slotSize + 3, 0xCC02070D);
        graphics.fill(baseX - 3, y - 11, baseX + totalWidth + 3, y - 9, 0xFF55DDAA);
        graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.hotbar.gun"), baseX + 3, y - 9, 0x88FF7755, false);
        graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.hotbar.kit"), baseX + 62, y - 9, 0x8822FF77, false);
        graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.hotbar.ammo"), baseX + totalWidth - 56, y - 9, 0x88FFFF66, false);

        int x = baseX;

        // === GUN SLOTS (0, 1) ===
        for (int i = 0; i < 2; i++) {
            boolean selected = i == player.getInventory().selected;
            int slotColor = selected ? 0xFF884422 : 0x44FF4422;
            int borderColor = selected ? 0xFFFF6633 : 0x88FF4422;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);
            graphics.drawString(mc.font, "§c" + (i + 1), x + 1, y + 1, 0x88FF4444, false);

            net.minecraft.world.item.ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 2);
                graphics.renderItemDecorations(mc.font, stack, x + 1, y + 2);

                // 残弾数表示: マガジン / 合計予備弾
                if (stack.hasTag() && stack.getTag() != null && stack.getTag().contains("GunId")) {
                    int mag = stack.getTag().getInt("GunCurrentAmmoCount");
                    // 対応する弾薬スロット(9-10 for gun1, 11-12 for gun2)の合計
                    int ammoStart = (i == 0)
                        ? com.levanilla.rogue.core.GameConstants.SLOT_AMMO_GUN1_START
                        : com.levanilla.rogue.core.GameConstants.SLOT_AMMO_GUN2_START;
                    int ammoEnd = (i == 0)
                        ? com.levanilla.rogue.core.GameConstants.SLOT_AMMO_GUN1_END
                        : com.levanilla.rogue.core.GameConstants.SLOT_AMMO_GUN2_END;
                    int reserve = 0;
                    for (int s = ammoStart; s <= ammoEnd; s++) {
                        net.minecraft.world.item.ItemStack a = player.getInventory().items.get(s);
                        if (!a.isEmpty()) reserve += a.getCount();
                    }
                    String ammoText = mag + "§8/§7" + reserve;
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + 1, y + slotSize - 7, 200);
                    graphics.pose().scale(0.6f, 0.6f, 1.0f);
                    graphics.drawString(mc.font, ammoText, 0, 0, 0xFFFFCC44, false);
                    graphics.pose().popPose();
                }
            }
            x += slotSize;
        }

        // セパレーター
        graphics.fill(x + 1, y + 2, x + 2, y + slotSize - 2, 0x66FFFFFF);
        x += separatorWidth;

        // === MELEE SLOT (2) ===
        {
            boolean selected = 2 == player.getInventory().selected;
            int slotColor = selected ? 0xFF224488 : 0x442244FF;
            int borderColor = selected ? 0xFF3366FF : 0x882244FF;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);
            graphics.drawString(mc.font, "§9M", x + 1, y + 1, 0x884488FF, false);

            net.minecraft.world.item.ItemStack stack = player.getInventory().items.get(2);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 2);
                graphics.renderItemDecorations(mc.font, stack, x + 1, y + 2);
            }
            x += slotSize;
        }

        // セパレーター
        graphics.fill(x + 1, y + 2, x + 2, y + slotSize - 2, 0x66FFFFFF);
        x += separatorWidth;

        // === ITEM SLOTS (3-11) ===
        for (int i = com.levanilla.rogue.core.GameConstants.SLOT_ITEM_START;
             i <= com.levanilla.rogue.core.GameConstants.SLOT_ITEM_END; i++) {
            if (i == 9) {
                graphics.fill(x - 1, y + 2, x, y + slotSize - 2, 0x5577FFAA);
                x += extendedItemGap;
            }
            boolean selected = i == player.getInventory().selected;
            boolean extended = i >= 9;
            int slotColor = selected ? 0xFF225522 : extended ? 0x5533AA66 : 0x4422FF22;
            int borderColor = selected ? 0xFF33FF33 : extended ? 0xAA55DDAA : 0x8822AA22;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);
            String slotLabel = extended ? "E" + (i - 8) : String.valueOf(i + 1);
            graphics.pose().pushPose();
            graphics.pose().translate(x + 1, y + 1, 200);
            graphics.pose().scale(0.5f, 0.5f, 1.0f);
            graphics.drawString(mc.font, slotLabel, 0, 0, extended ? 0xAA88FFCC : 0x8822FF22, false);
            graphics.pose().popPose();

            net.minecraft.world.item.ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 2);
                graphics.renderItemDecorations(mc.font, stack, x + 1, y + 2);
            }
            x += slotSize;
        }

        // セパレーター
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

            net.minecraft.world.item.ItemStack ammoStack = player.getInventory().items.get(slotIndex);
            if (!ammoStack.isEmpty()) {
                graphics.renderItem(ammoStack, x + 1, y + 2);
                graphics.renderItemDecorations(mc.font, ammoStack, x + 1, y + 2);
            } else {
                // 空スロット: 対応銃の弾薬タイプ名を小さく表示
                int gunSlot = isGun2 ? 1 : 0;
                net.minecraft.world.item.ItemStack gunStack = player.getInventory().items.get(gunSlot);
                if (!gunStack.isEmpty() && gunStack.hasTag() && gunStack.getTag().contains("GunId")) {
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + 2, y + 7, 0);
                    graphics.pose().scale(0.5f, 0.5f, 1.0f);
                    graphics.drawString(mc.font, Component.translatable("hud.tac_rogue.hotbar.ammo_dim"), 0, 0, 0x44FFFFFF, false);
                    graphics.pose().popPose();
                }
            }
            x += slotSize;
        }
    }
}
