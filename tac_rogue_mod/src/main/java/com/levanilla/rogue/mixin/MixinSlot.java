package com.levanilla.rogue.mixin;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class MixinSlot {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void onMayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot)(Object)this;
        if (slot.getItem().is(net.minecraft.world.item.Items.BARRIER) &&
            slot.getItem().hasTag() && slot.getItem().getOrCreateTag().getBoolean("rogue_item_locked")) {
            cir.setReturnValue(false); // ロックされたアイテムの取り出しを絶対に防ぐ
        }
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void onMayPlace(net.minecraft.world.item.ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot)(Object)this;
        if (slot.getItem().is(net.minecraft.world.item.Items.BARRIER) &&
            slot.getItem().hasTag() && slot.getItem().getOrCreateTag().getBoolean("rogue_item_locked")) {
            cir.setReturnValue(false); // ロックされたスロットへのアイテムの上書き配置を防ぐ
            return;
        }
        
        // 拡張インベントリ(スロット13〜35)には銃・近接武器を一切配置させない
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
            int containerSlot = slot.getContainerSlot();
            if (containerSlot >= 13 && containerSlot <= 35) {
                if (stack.hasTag()) {
                    net.minecraft.nbt.CompoundTag tag = stack.getTag();
                    if (tag != null && (tag.contains("GunId") || tag.contains("MeleeWeaponId"))) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}
