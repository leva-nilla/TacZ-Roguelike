package com.levanilla.rogue.networking;

import com.levanilla.rogue.core.PerkDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * クライアント → サーバー: パーク選択通知パケット。
 * プレイヤーが選択したパークのシリアライズされたタグを送信する。
 */
public class PerkActionMessage {

    private final String perkTag;

    public PerkActionMessage(String perkTag) {
        this.perkTag = perkTag;
    }

    public static void encode(PerkActionMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.perkTag);
    }

    public static PerkActionMessage decode(FriendlyByteBuf buf) {
        return new PerkActionMessage(buf.readUtf());
    }

    public static void handle(PerkActionMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            PerkDefinition perk = PerkDefinition.fromTag(msg.perkTag);

            // 累積制限チェック: OVERCLOCKED / CURSED
            if (perk.modifier == PerkDefinition.Modifier.OVERCLOCKED) {
                int count = countModifier(player, PerkDefinition.Modifier.OVERCLOCKED);
                if (count >= com.levanilla.rogue.core.GameConstants.MAX_OVERCLOCKED_PERKS) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "\u00A7c[PERK] OVERCLOCKED limit reached (" + com.levanilla.rogue.core.GameConstants.MAX_OVERCLOCKED_PERKS + "/" + com.levanilla.rogue.core.GameConstants.MAX_OVERCLOCKED_PERKS + ")"));
                    return;
                }
            }
            if (perk.modifier == PerkDefinition.Modifier.CURSED) {
                int count = countModifier(player, PerkDefinition.Modifier.CURSED);
                if (count >= com.levanilla.rogue.core.GameConstants.MAX_CURSED_PERKS) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "\u00A7c[PERK] CURSED limit reached (" + com.levanilla.rogue.core.GameConstants.MAX_CURSED_PERKS + "/" + com.levanilla.rogue.core.GameConstants.MAX_CURSED_PERKS + ")"));
                    return;
                }
            }

            // パークタグをプレイヤーに追加
            player.addTag(perk.toTag());

            // パーク効果を適用
            applyPerkEffect(player, perk);

            // 確認メッセージ
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.tac_rogue.perk_acquired", perk.getDisplayName() + " \u00A77\u2014 " + perk.getDescription()));

            // クライアントへ同期
            com.levanilla.rogue.core.RunManager.sync();
        });
        ctx.get().setPacketHandled(true);
    }

    /** 指定修飾子のパーク取得数をカウント */
    private static int countModifier(net.minecraft.server.level.ServerPlayer player, PerkDefinition.Modifier modifier) {
        int count = 0;
        for (String tag : player.getTags()) {
            if (tag.startsWith("perk:") && tag.contains(":" + modifier.name() + ":")) {
                count++;
            }
        }
        return count;
    }

    /** パーク効果をプレイヤーの属性に反映する */
    private static void applyPerkEffect(net.minecraft.server.level.ServerPlayer player, PerkDefinition perk) {
        float effect = perk.calculateEffect();
        float ratio = effect / 100.0f;

        switch (perk.category) {
            case VITALITY -> {
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (attr != null) {
                    attr.setBaseValue(attr.getBaseValue() * (1.0 + ratio));
                    player.setHealth(player.getHealth()); // 新しい上限に合わせる
                }
            }
            case ARMOR -> {
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                if (attr != null) attr.setBaseValue(attr.getBaseValue() + effect / 10.0);
            }
            case VELOCITY -> {
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (attr != null) attr.setBaseValue(attr.getBaseValue() * (1.0 + ratio * 0.5));
            }
            case DAMAGE -> {
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) attr.setBaseValue(attr.getBaseValue() * (1.0 + ratio));
            }
            case STAMINA -> {
                float maxSt = com.levanilla.rogue.core.StaminaManager.getMaxStamina(player);
                com.levanilla.rogue.core.StaminaManager.setMaxStamina(player, maxSt * (1.0f + ratio));
            }
            // GOLD_RUSH, VAMPIRE, SCAVENGER 等はイベントハンドラー側でタグを確認して処理
            default -> { /* 効果はイベントハンドラーで処理 */ }
        }

        // 修飾子のトレードオフはPlayerTickHandler.applyPerkStats()で統合管理
        // （ここで適用すると毎秒の属性再計算で上書きされるため）
    }
}
