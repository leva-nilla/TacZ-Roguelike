package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;

final class ClientWelcomeScreenDelegate {
    private static boolean pendingWelcomeScreen = false;
    private static int pendingWelcomeTicks = 0;

    private ClientWelcomeScreenDelegate() {}

    static void onClientPlayerJoin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        pendingWelcomeScreen = true;
        pendingWelcomeTicks = 0;
        mc.tell(() -> {
            pendingWelcomeScreen = mc.player != null && !ClientPreferenceManager.hasSeenWelcomeForCurrentWorld();
        });
    }

    static void handlePendingWelcomeScreen(Minecraft mc) {
        if (!pendingWelcomeScreen) return;
        if (mc.player == null || mc.level == null) {
            pendingWelcomeTicks = 0;
            return;
        }
        if (ClientPreferenceManager.hasSeenWelcomeForCurrentWorld()) {
            pendingWelcomeScreen = false;
            pendingWelcomeTicks = 0;
            return;
        }
        pendingWelcomeTicks++;
        if (pendingWelcomeTicks < 10) return;
        if (mc.screen == null || mc.screen instanceof StarterGearScreen) {
            mc.setScreen(new com.levanilla.rogue.client.hud.WelcomeScreen());
            pendingWelcomeScreen = false;
            pendingWelcomeTicks = 0;
        }
    }
}
