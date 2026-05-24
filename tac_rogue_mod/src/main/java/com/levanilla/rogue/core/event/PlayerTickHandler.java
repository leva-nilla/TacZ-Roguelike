package com.levanilla.rogue.core.event;

import com.levanilla.rogue.core.*;
import com.levanilla.rogue.core.service.ArmorPlateService;
import com.levanilla.rogue.core.service.InventoryRuleService;
import com.levanilla.rogue.core.service.LowHealthChallengeService;
import com.levanilla.rogue.core.service.PlayerPerkTickService;
import com.levanilla.rogue.core.service.PlayerRegenService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;



import static com.levanilla.rogue.core.CommonEventHandler.LOBBY_DIM;
import static com.levanilla.rogue.core.CommonEventHandler.ROGUE_DIM;

/**
 * プレイヤーの毎チック処理：スタミナ、ステルス、インベントリ制限、スロット制約。
 */
@Mod.EventBusSubscriber(modid = "tac_rogue")
public class PlayerTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;

        PlayerPerkTickService.PerkSnapshot perks = PlayerPerkTickService.getSnapshot(player);
        if (perks.modifierCount(PerkDefinition.Modifier.TITANIC) > 0 && player.isSprinting()) {
            player.setSprinting(false);
        }

        // スタミナの自然回復/消費処理
        float adrenalineBonus = getAdrenalineBonus(perks);
        StaminaManager.tick(player, 1.0f + (adrenalineBonus / 100.0f));
        ArmorPlateService.tick(player);

        // === 潜伏効果: スニーク/伏せ中はモブの検知範囲を低下 ===
        if (player.level().dimension() == ROGUE_DIM && player.tickCount % GameConstants.STEALTH_CHECK_INTERVAL == 0) {
            applyStealth(player, perks);
        }

        // ゲームモードをアドベンチャーに固定
        if ((player.level().dimension() == ROGUE_DIM || player.level().dimension() == LOBBY_DIM)
                && !player.isCreative() && !player.isSpectator()) {
            if (player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.ADVENTURE) {
                player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            }
        }

        // 空腹状態でもダッシュを許可
        if (player.getFoodData().getFoodLevel() > GameConstants.SPRINT_MIN_FOOD_LEVEL
                && player.getFoodData().getFoodLevel() <= GameConstants.SPRINT_MAX_CHECK_FOOD) {
            if (player.isSprinting()) {
                player.getFoodData().setFoodLevel(GameConstants.SPRINT_RESTORE_FOOD);
            }
        }

        // バニラ自然回復を無効化（カスタム回復システムで置き換え）
        if ((player.level().dimension() == ROGUE_DIM || player.level().dimension() == LOBBY_DIM)
                && player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION)) {
            player.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false, player.server);
        }

        // ===== カスタム体力回復（ダメージ後5秒間は停止）=====
        if (player.level().dimension() == ROGUE_DIM && player.tickCount % 20 == 0) {
            PlayerRegenService.applyCustomHealthRegen(player);
        }
        if (player.level().dimension() == ROGUE_DIM) {
            LowHealthChallengeService.enforceCap(player);
            if (player.tickCount % 3 == 0) {
                CombatEventHandler.syncStealthTakedownHint(player);
            }
        }

        // 奈落への落下防止
        if ((player.level().dimension() == ROGUE_DIM || player.level().dimension() == LOBBY_DIM)
                && player.getY() < GameConstants.VOID_Y_THRESHOLD) {
            RunManager.returnToLobby(player);
            player.setHealth(player.getMaxHealth());
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tac_rogue.void_return"), true);
        }

        // ロビーにいてまだ装備を選んでいない場合、定期的に初期装備選択画面を開く
        if (player.level().dimension() == LOBBY_DIM && !player.getTags().contains("rogue:gear_selected")) {
            if (player.tickCount % GameConstants.GEAR_CHECK_INTERVAL == 0) {
                com.levanilla.rogue.networking.TacRogueNetworking.openStarterGear(player);
            }
        }

        // ===== インベントリ制限 + スロット制約 =====
        if (player.tickCount % GameConstants.INV_CHECK_INTERVAL == 0) {
            InventoryRuleService.enforce(player);
        }

        // ===== パーク効果の適用 =====
        if (player.tickCount % 20 == 0) {
            PlayerPerkTickService.applyPerkStats(player, perks);
            if (player.level().dimension() == ROGUE_DIM) {
                LowHealthChallengeService.enforceCap(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ROGUE_DIM && player.level().dimension() != LOBBY_DIM) return;
        StaminaManager.handleJump(player);
    }

    // ===== スタミナ / ADRENALINE =====
    
    private static float getAdrenalineBonus(PlayerPerkTickService.PerkSnapshot perks) {
        return perks.effect(PerkDefinition.Category.ADRENALINE);
    }

    // ===== ステルス =====

    private static void applyStealth(ServerPlayer player, PlayerPerkTickService.PerkSnapshot perks) {
        float stealthExtend = perks.effect(PerkDefinition.Category.STEALTH_EXTEND);
        boolean isSneaking = player.hasPose(net.minecraft.world.entity.Pose.CROUCHING);
        boolean isCrawling = CombatPostureHelper.isProne(player);
        double rangeMul = isCrawling ? GameConstants.STEALTH_CRAWL_RANGE :
                           (isSneaking ? GameConstants.STEALTH_SNEAK_RANGE : 1.0);
        
        if (stealthExtend > 0) {
            rangeMul = Math.max(0.01, rangeMul - (stealthExtend / 100.0));
        }

        AABB stealth = new AABB(player.blockPosition()).inflate(GameConstants.STEALTH_CHECK_RADIUS);
        List<Mob> nearby = player.level().getEntitiesOfClass(Mob.class, stealth, Mob::isAlive);
        for (Mob mob : nearby) {
            if (mob.getTags().contains("rogue:boss")) continue;

            AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRange != null) {
                followRange.removeModifier(GameConstants.STEALTH_MODIFIER_UUID);
                if (rangeMul < 1.0) {
                    followRange.addTransientModifier(new AttributeModifier(
                        GameConstants.STEALTH_MODIFIER_UUID, "rogue_stealth", rangeMul - 1.0,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }
        }
    }
    public static void clearMemory() {
        com.levanilla.rogue.core.service.PlayerPerkTickService.clearMemory();
        com.levanilla.rogue.core.service.InventoryRuleService.clearMemory();
    }
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        clearMemory();
    }
}
