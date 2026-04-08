package com.levanilla.rogue.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * 専用ホットバー描画 — [GUN1][GUN2] | [MELEE] | [ITEM x5] | [AMMO1][AMMO2]
 */
public final class HotbarRenderer {

    private HotbarRenderer() {}

    public static void render(GuiGraphics graphics, Minecraft mc, Player player, int screenWidth, int screenHeight) {
        int slotSize = 20;
        int separatorWidth = 4;
        // [GUN1][GUN2] | [MELEE] | [ITEM x6] | [AMMO x4]
        int totalSlots = 2 + 1 + 6 + 4; // 13 slots
        int totalWidth = totalSlots * slotSize + separatorWidth * 3;
        int baseX = (screenWidth - totalWidth) / 2;
        int y = screenHeight - 24;

        // 背景
        graphics.fill(baseX - 2, y - 2, baseX + totalWidth + 2, y + slotSize + 2, 0xBB000000);
        graphics.fill(baseX - 2, y - 2, baseX + totalWidth + 2, y, 0xFF00AAFF);

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

        // === ITEM SLOTS (3-8) ===
        for (int i = 3; i <= 8; i++) {
            boolean selected = i == player.getInventory().selected;
            int slotColor = selected ? 0xFF225522 : 0x4422FF22;
            int borderColor = selected ? 0xFF33FF33 : 0x8822AA22;
            graphics.fill(x, y, x + slotSize - 1, y + slotSize, slotColor);
            graphics.renderOutline(x, y, slotSize - 1, slotSize, borderColor);

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

        // === AMMO SLOTS (9-12) ===
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
                    graphics.drawString(mc.font, "§8AMMO", 0, 0, 0x44FFFFFF, false);
                    graphics.pose().popPose();
                }
            }
            x += slotSize;
        }
    }
}
