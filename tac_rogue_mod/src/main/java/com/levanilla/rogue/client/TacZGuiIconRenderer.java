package com.levanilla.rogue.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws cheap TacZ GUI slot icons for non-gun items. Guns intentionally keep
 * TacZ's full item renderer so their detailed preview stays intact.
 */
public final class TacZGuiIconRenderer {
    private TacZGuiIconRenderer() {}

    public static boolean renderLightweightIcon(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        return renderLightweightIcon(graphics, font, stack, x, y, true);
    }

    public static boolean renderLightweightIcon(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, boolean drawCount) {
        ResourceLocation texture = getAmmoSlotTexture(stack);
        if (texture == null) {
            texture = getAttachmentSlotTexture(stack);
        }
        if (texture == null) return false;

        graphics.blit(texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        if (drawCount) renderCount(graphics, font, stack, x, y);
        return true;
    }

    public static ResourceLocation getAmmoSlotTexture(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains("AmmoId")) return null;
        try {
            ResourceLocation ammoId = ResourceLocation.tryParse(tag.getString("AmmoId"));
            if (ammoId == null) return null;
            return TimelessAPI.getClientAmmoIndex(ammoId)
                .map(index -> index.getSlotTextureLocation())
                .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ResourceLocation getAttachmentSlotTexture(ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment == null) return null;
        try {
            ResourceLocation attachmentId = attachment.getAttachmentId(stack);
            if (attachmentId == null) return null;
            return TimelessAPI.getClientAttachmentIndex(attachmentId)
                .map(index -> index.getSlotTexture())
                .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void renderCount(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (stack.getCount() <= 1) return;
        String count = String.valueOf(stack.getCount());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.drawString(font, count, x + 19 - 2 - font.width(count), y + 6 + 3, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }
}
