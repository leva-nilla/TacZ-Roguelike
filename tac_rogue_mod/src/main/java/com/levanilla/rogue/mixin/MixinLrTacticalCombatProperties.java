package com.levanilla.rogue.mixin;

import com.levanilla.rogue.core.CommonEventHandler;
import com.levanilla.rogue.core.WeaponRarity;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = me.xjqsh.lrtactical.capability.CombatProperties.class, remap = false)
public abstract class MixinLrTacticalCombatProperties {
    private static final int MELEE_PREPARE_GUARD_TICKS = 3;

    @Shadow @Final private Player entity;
    @Shadow private int coolDownTick;
    @Shadow private int lastMaxTick;
    @Shadow private boolean preparingAttack;
    @Shadow private int preparingAttackCnt;

    @Shadow public abstract void postAttack(MeleeAction action, int actionCount, List<Entity> targets);

    @Unique private MeleeAction tacRogue$serverFallbackAction;
    @Unique private int tacRogue$serverFallbackActionCount;
    @Unique private int tacRogue$serverFallbackDelayTicks;
    @Unique private boolean tacRogue$serverFallbackArmed;

    @Inject(
        method = "preAttack",
        at = @At(
            value = "FIELD",
            target = "Lme/xjqsh/lrtactical/capability/CombatProperties;lastMaxTick:I",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void tacRogue$scaleMeleeCooldownForPerk(MeleeAction action, Vec3 pos, Vec3 look,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || entity.level() == null) return;
        if (entity.level().dimension() != CommonEventHandler.ROGUE_DIM
                && entity.level().dimension() != CommonEventHandler.LOBBY_DIM) {
            return;
        }
        int adjusted = WeaponRarity.getMeleeSpeedPerkAdjustedTicks(this.coolDownTick, entity);
        adjusted = Math.max(adjusted, tacRogue$minimumCooldownForHit(action));
        if (adjusted != this.coolDownTick) {
            this.coolDownTick = adjusted;
            this.lastMaxTick = adjusted;
        }
    }

    @Inject(method = "preAttack", at = @At("RETURN"), remap = false)
    private void tacRogue$armServerMeleeHitFallback(MeleeAction action, Vec3 pos, Vec3 look,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            tacRogue$clearServerFallback();
            return;
        }
        if (entity == null || entity.level() == null || entity.level().isClientSide()) return;
        if (entity.level().dimension() != CommonEventHandler.ROGUE_DIM
                && entity.level().dimension() != CommonEventHandler.LOBBY_DIM) {
            return;
        }
        ItemStack stack = entity.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof IMeleeWeapon weapon)) {
            tacRogue$clearServerFallback();
            return;
        }
        int delay = Math.max(0, weapon.getAttackDelay(entity, stack, action));
        tacRogue$serverFallbackAction = action;
        tacRogue$serverFallbackActionCount = this.preparingAttackCnt;
        tacRogue$serverFallbackDelayTicks = Math.min(delay, Math.max(0, this.lastMaxTick - 1));
        tacRogue$serverFallbackArmed = true;
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void tacRogue$performServerMeleeHitFallback(CallbackInfo ci) {
        if (!tacRogue$serverFallbackArmed || entity == null || entity.level() == null || entity.level().isClientSide()) {
            return;
        }
        if (!this.preparingAttack || tacRogue$serverFallbackAction == null) {
            tacRogue$clearServerFallback();
            return;
        }
        int elapsed = Math.max(0, this.lastMaxTick - this.coolDownTick);
        if (elapsed < tacRogue$serverFallbackDelayTicks) return;

        ItemStack stack = entity.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof IMeleeWeapon weapon)) {
            tacRogue$clearServerFallback();
            return;
        }
        List<Entity> targets = weapon.collectTargets(
                entity,
                stack,
                tacRogue$serverFallbackAction,
                entity.getEyePosition(),
                entity.getLookAngle());
        this.postAttack(tacRogue$serverFallbackAction, tacRogue$serverFallbackActionCount, targets);
        tacRogue$clearServerFallback();
    }

    @Inject(method = "resetMeleeSync", at = @At("HEAD"), remap = false)
    private void tacRogue$clearFallbackOnReset(CallbackInfo ci) {
        tacRogue$clearServerFallback();
    }

    private int tacRogue$minimumCooldownForHit(MeleeAction action) {
        ItemStack stack = entity.getMainHandItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof IMeleeWeapon weapon)) {
            return MELEE_PREPARE_GUARD_TICKS;
        }
        int delay = Math.max(0, weapon.getAttackDelay(entity, stack, action));
        return Math.max(MELEE_PREPARE_GUARD_TICKS, delay + MELEE_PREPARE_GUARD_TICKS);
    }

    @Unique
    private void tacRogue$clearServerFallback() {
        tacRogue$serverFallbackAction = null;
        tacRogue$serverFallbackActionCount = 0;
        tacRogue$serverFallbackDelayTicks = 0;
        tacRogue$serverFallbackArmed = false;
    }
}
