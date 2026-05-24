package com.levanilla.rogue.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.tacz.guns.entity.shooter.LivingEntityCrawl.class, remap = false)
public abstract class MixinLivingEntityCrawl {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;

    @Inject(method = "tickCrawling", at = @At("HEAD"), cancellable = true)
    private void tacRogue$keepNonGunCrawlServer(CallbackInfo ci) {
        if (!tacRogue$isTacRogueDimension() || tacRogue$currentItemIsGun()) return;
        ci.cancel();
        if (shooter.isSpectator() || shooter.isPassenger() || shooter.isSwimming() || !shooter.onGround()) {
            data.isCrawling = false;
        }
        tacRogue$setCrawlPose();
    }

    @Unique
    private boolean tacRogue$currentItemIsGun() {
        if (data.currentGunItem == null) return false;
        ItemStack stack = data.currentGunItem.get();
        return !stack.isEmpty() && stack.getItem() instanceof IGun;
    }

    @Unique
    private boolean tacRogue$isTacRogueDimension() {
        return shooter != null
            && shooter.level() != null
            && "tac_rogue".equals(shooter.level().dimension().location().getNamespace());
    }

    @Unique
    private void tacRogue$setCrawlPose() {
        if (data.isCrawling) {
            if (shooter instanceof Player player) {
                player.setForcedPose(Pose.SWIMMING);
            } else {
                shooter.setPose(Pose.SWIMMING);
            }
        } else if (shooter instanceof Player player) {
            player.setForcedPose(null);
        }
    }
}
