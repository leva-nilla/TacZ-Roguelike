package com.levanilla.rogue.client;

import com.levanilla.rogue.client.hud.NotificationManager;
import com.levanilla.rogue.core.RunManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

final class ClientChatEventDelegate {
    private ClientChatEventDelegate() {}

    static void onChatReceived(ClientChatReceivedEvent event) {
        try {
            String msg = event.getMessage().getString();

            if (msg.contains("Bigger Stacks") || msg.contains("BiggerStacks") || msg.toLowerCase().contains("/biggerstacks")) {
                event.setCanceled(true);
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            boolean inRogue = mc.level.dimension().location().getNamespace().equals("tac_rogue") || RunManager.isRunActive();
            if (!inRogue) return;

            if (msg.contains("[SHOP]") || msg.contains("[ERROR]") || msg.contains("[MEDKIT]") ||
                msg.contains("LOADOUT") || msg.contains("ゴールド") || msg.contains("Gold") ||
                msg.contains("スタッシュ") || msg.contains("Stash") || msg.contains("insufficient") ||
                msg.contains("purchased") || msg.contains("upgrade") || msg.contains("SOLD") ||
                msg.contains("sold") || msg.contains("Floor") || msg.contains("FLOOR")) {
                event.setCanceled(true);
                int color = msg.contains("ERROR") ? 0xFFFF4444 :
                            msg.contains("SHOP") ? 0xFFFFD700 :
                            msg.contains("insufficient") ? 0xFFFF4444 : 0xFFFFFFFF;
                NotificationManager.add(msg, color);
            }
        } catch (Exception e) {
            // 例外時はチャットをそのまま表示させる
        }
    }
}
