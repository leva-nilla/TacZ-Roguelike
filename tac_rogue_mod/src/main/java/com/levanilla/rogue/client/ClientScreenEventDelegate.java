package com.levanilla.rogue.client;

import com.levanilla.rogue.core.RunManager;
import com.levanilla.rogue.mixin.MixinScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;

final class ClientScreenEventDelegate {
    private ClientScreenEventDelegate() {}

    static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();

        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            event.setNewScreen(new TacRogueTitleScreen());
            return;
        }
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen) {
            event.setCanceled(true);
            mc.tell(() -> RogueWorldSelectScreen.openFromVanilla(new TacRogueTitleScreen()));
            return;
        }

        if (event.getScreen() instanceof StashScreen
            || event.getScreen() instanceof RogueSupplyChestScreen
            || event.getScreen() instanceof RogueInventoryScreen
            || event.getScreen() instanceof RogueWorldSelectScreen) {
            return;
        }

        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen containerScreen) {
            net.minecraft.world.inventory.ChestMenu chestMenu = containerScreen.getMenu();
            String title = containerScreen.getTitle().getString();
            if (title.contains("STASH TERMINAL")) {
                event.setCanceled(true);
                mc.tell(() -> {
                    StashScreen stashScreen = new StashScreen(chestMenu, mc.player.getInventory(), containerScreen.getTitle());
                    mc.setScreen(stashScreen);
                });
                return;
            }
            event.setCanceled(true);
            mc.tell(() -> {
                if (mc.player != null) {
                    RogueSupplyChestScreen supplyScreen =
                        new RogueSupplyChestScreen(chestMenu, mc.player.getInventory(), containerScreen.getTitle());
                    mc.setScreen(supplyScreen);
                }
            });
            return;
        }

        if (mc.level != null && event.getScreen() instanceof InventoryScreen) {
            boolean inRogue = mc.level.dimension().location().getNamespace().equals("tac_rogue");
            if (inRogue || RunManager.isRunActive()) {
                event.setCanceled(true);
                openInventoryWithTab(RogueInventoryScreen.Tab.INVENTORY);
            }
        }
    }

    static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!TacticalScreenStyle.isWorldFlowScreen(event.getScreen())) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen) {
            TacticalScreenStyle.drawWorldSelectOverlay(graphics, mc.font, event.getScreen().width, event.getScreen().height);
        }

        for (Renderable renderable : ((MixinScreenAccessor) event.getScreen()).tacRogue$getRenderables()) {
            if (renderable instanceof Button button && button.visible) {
                TacticalScreenStyle.drawButton(graphics, mc.font, button);
            }
        }
    }

    static void openInventoryWithTab(RogueInventoryScreen.Tab tab) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null) return;
        mc.tell(() -> {
            net.minecraft.client.player.LocalPlayer p = mc.player;
            if (p != null && p.inventoryMenu != null) {
                RogueInventoryScreen screen = new RogueInventoryScreen(p.inventoryMenu, p.getInventory(), Component.literal("ROGUE INVENTORY"));
                screen.setActiveTab(tab);
                mc.setScreen(screen);
            }
        });
    }
}
