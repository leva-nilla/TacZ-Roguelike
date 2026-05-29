package com.levanilla.rogue.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    
    @Shadow public abstract Item getItem();

    @Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void onGetMaxStackSize(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.hasTag() && self.getTag().contains(com.levanilla.rogue.core.service.RogueItemFactory.CONSUMABLE_MAX_STACK_KEY)) {
            cir.setReturnValue(Math.max(1, self.getTag().getInt(com.levanilla.rogue.core.service.RogueItemFactory.CONSUMABLE_MAX_STACK_KEY)));
            return;
        }
        if (self.hasTag() && self.getTag().contains("TacRogueUtilityMaxStack")) {
            cir.setReturnValue(Math.max(1, self.getTag().getInt("TacRogueUtilityMaxStack")));
            return;
        }
        Item item = this.getItem();
        if (item != null) {
            ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(item);
            if (registryName != null && "tacz".equals(registryName.getNamespace()) && "ammo".equals(registryName.getPath())) {
                int baseSize = 60; // fallback
                if (self.hasTag() && self.getTag().contains("AmmoId")) {
                    String ammoId = self.getTag().getString("AmmoId");
                    baseSize = com.levanilla.rogue.core.registry.AmmoDatabase.getAmmoStackSize(ammoId);
                }
                int capLevel = com.levanilla.rogue.core.RunManager.getGlobalAmmoCapacityLevel();
                // 初期(レベル0)はTacZの基本サイズ(baseSize)からスタートし、レベルに応じて容量を増加 (0.5x/level)
                int finalLimit = Math.round(baseSize * (1.5f + (capLevel * 0.5f)));
                
                cir.setReturnValue(finalLimit);
            } else {
                // 弾薬以外のアイテムは、Bigger Stacksの設定によらず、本来のアイテムの最大スタック数（Minecraftデフォルト）に落とす
                int originalLimit = item.getMaxStackSize(self);
                // フォーマットが古い可能性があるため、64より大きい場合は64にクリップするか、アイテム自身の制限に揃える
                if (cir.getReturnValue() != null && cir.getReturnValue() > 64) {
                    cir.setReturnValue(Math.min(64, originalLimit <= 0 ? 64 : originalLimit));
                }
            }
        }
    }
}
