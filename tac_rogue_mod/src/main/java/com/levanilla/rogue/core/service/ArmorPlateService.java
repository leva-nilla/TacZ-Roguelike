package com.levanilla.rogue.core.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ArmorPlateService {
    private static final String EXPIRE_TICK_KEY = "TacRogueArmorPlateExpireTick";
    private static final java.util.UUID ARMOR_PLATE_UUID =
        java.util.UUID.fromString("b2d3e4f5-6789-4abc-def0-123456789abc");
    private static final double ARMOR_BONUS = 8.0D;
    private static final long DURATION_TICKS = 600L;
    private static final int ARMOR_PLATE_MODEL = 39011;

    private ArmorPlateService() {}

    public static boolean isArmorPlate(ItemStack stack) {
        if (!stack.is(Items.IRON_INGOT) || !stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        if (tag.getBoolean("rogue_item") && tag.getInt("CustomModelData") == ARMOR_PLATE_MODEL) return true;
        if (tag.getInt("CustomModelData") == ARMOR_PLATE_MODEL) return true;
        if (stack.hasCustomHoverName()) {
            String name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
            return name.contains("armor plate") || name.contains("アーマープレート");
        }
        return false;
    }

    public static void use(ServerPlayer player, ItemStack stack) {
        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr == null) {
            player.displayClientMessage(Component.translatable("message.tac_rogue.armor_plate_unavailable"), true);
            return;
        }

        long expireTick = player.server.getTickCount() + DURATION_TICKS;
        player.getPersistentData().putLong(EXPIRE_TICK_KEY, expireTick);
        ensureModifier(player);
        stack.shrink(1);

        player.displayClientMessage(Component.translatable(
            "message.tac_rogue.armor_plate_used",
            String.format(java.util.Locale.ROOT, "%.0f", ARMOR_BONUS),
            String.format(java.util.Locale.ROOT, "%.0f", player.getAttributeValue(Attributes.ARMOR))), true);
    }

    public static void tick(ServerPlayer player) {
        long expireTick = player.getPersistentData().getLong(EXPIRE_TICK_KEY);
        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr == null) return;

        if (expireTick <= 0L) {
            if (armorAttr.getModifier(ARMOR_PLATE_UUID) != null) {
                armorAttr.removeModifier(ARMOR_PLATE_UUID);
            }
            return;
        }

        if (player.server.getTickCount() >= expireTick) {
            armorAttr.removeModifier(ARMOR_PLATE_UUID);
            player.getPersistentData().remove(EXPIRE_TICK_KEY);
            return;
        }

        ensureModifier(player);
    }

    private static void ensureModifier(ServerPlayer player) {
        AttributeInstance armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr == null) return;
        AttributeModifier existing = armorAttr.getModifier(ARMOR_PLATE_UUID);
        if (existing != null && Math.abs(existing.getAmount() - ARMOR_BONUS) < 0.001D) return;

        armorAttr.removeModifier(ARMOR_PLATE_UUID);
        armorAttr.addTransientModifier(new AttributeModifier(
            ARMOR_PLATE_UUID,
            "TacRogue Armor Plate",
            ARMOR_BONUS,
            AttributeModifier.Operation.ADDITION));
    }
}
