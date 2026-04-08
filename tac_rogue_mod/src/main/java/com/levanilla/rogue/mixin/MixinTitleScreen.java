package com.levanilla.rogue.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * タイトル画面にタクティカルHUD装飾を追加。
 * パノラマ背景はそのまま活かし、薄い暗転 + コーナーブラケット + 情報テキストのみ。
 * バニラロゴとスプラッシュはそのまま残す（ロゴ画像は assets/minecraft で上書き）。
 */
@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    @Unique
    private static final ResourceLocation CUSTOM_LOGO =
        new ResourceLocation("tac_rogue", "textures/gui/title_logo.png");

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen)(Object)this;
        int w = screen.width;
        int h = screen.height;
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;

        // ═══════════════ 薄いオーバーレイ（パノラマを暗くしすぎない） ═══════════════
        graphics.fill(0, 0, w, h, 0x40000000); // 25% 黒

        // ═══════════════ スキャンライン（微細な横線） ═══════════════
        for (int y = 0; y < h; y += 4) {
            graphics.fill(0, y, w, y + 1, 0x0C000000);
        }

        // ═══════════════ コーナーブラケット ═══════════════
        int c = 0xCC00CED1; // dark turquoise
        int len = 25;
        int th = 1; // line thickness
        // 左上
        graphics.fill(3, 3, 3 + len, 3 + th, c);
        graphics.fill(3, 3, 3 + th, 3 + len, c);
        // 右上
        graphics.fill(w - 3 - len, 3, w - 3, 3 + th, c);
        graphics.fill(w - 3 - th, 3, w - 3, 3 + len, c);
        // 左下
        graphics.fill(3, h - 3 - th, 3 + len, h - 3, c);
        graphics.fill(3, h - 3 - len, 3 + th, h - 3, c);
        // 右下
        graphics.fill(w - 3 - len, h - 3 - th, w - 3, h - 3, c);
        graphics.fill(w - 3 - th, h - 3 - len, w - 3, h - 3, c);

        // ═══════════════ 左下情報テキスト（Forge表示の上） ═══════════════
        int infoY = h - 68;
        graphics.drawString(font, "\u00A7c\u00A7lTACZ ROGUELIKE SYSTEM // INITIALIZED", 6, infoY, 0xFFFFFF, false);
        graphics.drawString(font, "\u00A78BUILD v0.1.0-beta // STANDBY", 6, infoY + 10, 0x44FFFFFF, false);
    }
}
