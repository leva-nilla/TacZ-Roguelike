package com.levanilla.rogue.core;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 特定種別のアイテムのみ配置を許可するカスタムスロット。
 * InventoryMenu のホットバースロットを差し替えて使用する。
 */
public class RestrictedSlot extends Slot {

    public enum SlotType {
        /** GunId タグを持つアイテムのみ */
        GUN,
        /** MeleeWeaponId タグを持つアイテムのみ */
        MELEE
    }

    private final SlotType slotType;

    public RestrictedSlot(Container container, int slotIndex, int x, int y, SlotType slotType) {
        super(container, slotIndex, x, y);
        this.slotType = slotType;
    }

    /**
     * このスロットにアイテムを置けるかどうかを判定。
     * 空の ItemStack は常に許可（スロットからの取り出し用）。
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return true;
        return switch (slotType) {
            case GUN -> stack.hasTag() && stack.getTag().contains("GunId");
            case MELEE -> stack.hasTag() && stack.getTag().contains("MeleeWeaponId");
        };
    }
}
