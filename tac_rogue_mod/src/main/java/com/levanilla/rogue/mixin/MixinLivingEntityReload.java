package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.WeaponRarity;
import com.levanilla.rogue.core.CommonEventHandler;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.tacz.guns.entity.shooter.LivingEntityReload.class, remap = false)
public abstract class MixinLivingEntityReload {
    @Shadow @Final private ShooterDataHolder data;
    @Shadow @Final private LivingEntity shooter;

    @Inject(method = "reload", at = @At("TAIL"), remap = false)
    private void tacRogue$accelerateReloadByRarity(CallbackInfo ci) {
        if (data == null || data.reloadTimestamp < 0 || data.currentGunItem == null) return;
        if (shooter == null
            || (shooter.level().dimension() != CommonEventHandler.ROGUE_DIM
                && shooter.level().dimension() != CommonEventHandler.LOBBY_DIM)) return;

        ItemStack stack = data.currentGunItem.get();
        if (stack == null || stack.isEmpty()) return;

        float reloadMult = WeaponRarity.getEffectiveReloadMult(stack, shooter);
        if (reloadMult >= 0.995f) return;

        long totalMs = getReloadTotalMs(stack);
        if (totalMs <= 0) return;

        long offsetMs = Math.round(totalMs * (1.0f - reloadMult));
        if (offsetMs > 0) {
            data.reloadTimestamp -= offsetMs;
        }
    }

    private long getReloadTotalMs(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return 0L;
        try {
            var gunIndex = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
            if (gunIndex.isEmpty()) return 0L;

            GunReloadData reloadData = gunIndex.get().getGunData().getReloadData();
            ReloadState.StateType stateType = data.reloadStateType;
            if (stateType == null) return 0L;

            float seconds;
            if (stateType.isReloadingEmpty()) {
                seconds = reloadData.getCooldown().getEmptyTime();
            } else if (stateType.isReloadingTactical()) {
                seconds = reloadData.getCooldown().getTacticalTime();
            } else {
                return 0L;
            }
            return Math.max(1L, Math.round(seconds * 1000.0f));
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
