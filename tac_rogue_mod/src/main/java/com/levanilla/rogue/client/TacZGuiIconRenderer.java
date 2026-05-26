package com.levanilla.rogue.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
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
    private static final java.util.Map<String, java.util.Optional<ResourceLocation>> GUN_TEXTURE_CACHE = new java.util.HashMap<>();
    private static final java.util.Map<ResourceLocation, java.util.Optional<ResourceLocation>> AMMO_TEXTURE_CACHE = new java.util.HashMap<>();
    private static final java.util.Map<ResourceLocation, java.util.Optional<ResourceLocation>> ATTACHMENT_TEXTURE_CACHE = new java.util.HashMap<>();

    public static void clearCache() {
        GUN_TEXTURE_CACHE.clear();
        AMMO_TEXTURE_CACHE.clear();
        ATTACHMENT_TEXTURE_CACHE.clear();
    }

    public static boolean renderLightweightIcon(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        return renderLightweightIcon(graphics, font, stack, x, y, true);
    }

    public static boolean renderLightweightIcon(GuiGraphics graphics, Font font, ItemStack stack, int x, int y, boolean drawCount) {
        ResourceLocation texture = getLightweightSlotTexture(stack);
        if (texture == null) return false;

        graphics.blit(texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        if (drawCount) renderCount(graphics, font, stack, x, y);
        return true;
    }

    public static ResourceLocation getLightweightSlotTexture(ItemStack stack) {
        ResourceLocation texture = getAmmoSlotTexture(stack);
        if (texture == null) {
            texture = getAttachmentSlotTexture(stack);
        }
        return texture;
    }

    public static ResourceLocation getGunSlotTexture(ItemStack stack) {
        if (!(stack.getItem() instanceof IGun gun)) return null;
        try {
            ResourceLocation gunId = gun.getGunId(stack);
            ResourceLocation displayId = gun.getGunDisplayId(stack);
            if (gunId == null) return null;
            String key = gunId + "|" + (displayId == null ? "" : displayId.toString());
            return GUN_TEXTURE_CACHE.computeIfAbsent(key, ignored ->
                TimelessAPI.getGunDisplay(stack).map(display -> display.getSlotTexture()))
                .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ResourceLocation getAmmoSlotTexture(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains("AmmoId")) return null;
        try {
            ResourceLocation ammoId = ResourceLocation.tryParse(tag.getString("AmmoId"));
            if (ammoId == null) return null;
            return AMMO_TEXTURE_CACHE.computeIfAbsent(ammoId, id ->
                TimelessAPI.getClientAmmoIndex(id).map(index -> index.getSlotTextureLocation()))
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
            return ATTACHMENT_TEXTURE_CACHE.computeIfAbsent(attachmentId, id ->
                TimelessAPI.getClientAttachmentIndex(id).map(index -> index.getSlotTexture()))
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
