package com.levanilla.rogue.core;

import com.levanilla.rogue.core.service.RogueItemFactory;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** TacZ 1.1.8 attachment accessors. Avoids stale direct NBT layouts. */
public final class TacZAttachmentHelper {
    private TacZAttachmentHelper() {
    }

    public static boolean installAttachment(ItemStack gunStack, String attachmentId) {
        if (gunStack == null || gunStack.isEmpty() || attachmentId == null || attachmentId.isBlank()) {
            return false;
        }
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return false;

        ItemStack attachmentStack = RogueItemFactory.createAttachmentStack(attachmentId);
        if (attachmentStack.isEmpty() || IAttachment.getIAttachmentOrNull(attachmentStack) == null) {
            return false;
        }
        if (!gun.allowAttachment(gunStack, attachmentStack)) {
            return false;
        }

        gun.installAttachment(gunStack, attachmentStack);
        return true;
    }

    public static List<String> getInstalledAttachmentIds(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return List.of();

        List<String> ids = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = gun.getAttachmentId(gunStack, type);
            if (!isEmptyAttachmentId(id)) {
                ids.add(id.toString());
            }
        }
        return List.copyOf(ids);
    }

    public static String installedAttachmentSummary(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return "";

        List<String> parts = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = gun.getAttachmentId(gunStack, type);
            if (!isEmptyAttachmentId(id)) {
                parts.add(type.name().toLowerCase(Locale.ROOT) + "=" + id);
            }
        }
        return String.join(",", parts);
    }

    private static boolean isEmptyAttachmentId(ResourceLocation id) {
        return id == null || DefaultAssets.isEmptyAttachmentId(id);
    }
}
