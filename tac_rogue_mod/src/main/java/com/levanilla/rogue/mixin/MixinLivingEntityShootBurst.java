package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.service.TacZFireRateService;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.BurstData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.tacz.guns.entity.shooter.LivingEntityShoot.class, remap = false)
public abstract class MixinLivingEntityShootBurst {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;

    @Inject(method = "consumeAmmoFromPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void tacRogue$skipLobbyInventoryAmmo(int amount, ItemStack gunStack, boolean needCheckAmmo, CallbackInfo ci) {
        if (isLobbyShooter()) {
            ci.cancel();
        }
    }

    @Redirect(
        method = "shoot",
        at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;reduceCurrentAmmoCount(Lnet/minecraft/world/item/ItemStack;)V"),
        remap = false
    )
    private void tacRogue$skipLobbyMagazineAmmo(IGun gun, ItemStack stack) {
        if (!isLobbyShooter()) {
            gun.reduceCurrentAmmoCount(stack);
        }
    }

    @Redirect(
        method = "getShootCoolDown(J)J",
        at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/pojo/data/gun/BurstData;getMinInterval()D"),
        remap = false
    )
    private double tacRogue$adjustServerBurstMinInterval(BurstData burstData, long timestamp) {
        double minInterval = burstData.getMinInterval();
        if (shooter == null
            || (shooter.level().dimension() != CommonEventHandler.ROGUE_DIM
                && shooter.level().dimension() != CommonEventHandler.LOBBY_DIM)) return minInterval;
        if (data == null || data.currentGunItem == null) return minInterval;

        ItemStack stack = data.currentGunItem.get();
        if (stack == null || stack.isEmpty()) return minInterval;
        return TacZFireRateService.adjustedShootIntervalSeconds(minInterval, stack, shooter);
    }

    private boolean isLobbyShooter() {
        return shooter != null && shooter.level().dimension() == CommonEventHandler.LOBBY_DIM;
    }
}
