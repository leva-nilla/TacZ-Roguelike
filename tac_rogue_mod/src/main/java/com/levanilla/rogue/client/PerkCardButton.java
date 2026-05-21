package com.levanilla.rogue.client;

import com.levanilla.rogue.core.PerkDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class PerkCardButton extends Button {
    private final PerkDefinition perk;

    PerkCardButton(int x, int y, int w, int h, PerkDefinition perk, OnPress press) {
        super(x, y, w, h, Component.empty(), press, DEFAULT_NARRATION);
        this.perk = perk;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int rarityColor = perk.getRarityColor();
        int bgColor = this.isHoveredOrFocused() ? 0xCC002244 : 0xAA001122;

        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
        graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, rarityColor | 0xFF000000);
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 2, rarityColor | 0xFF000000);

        Minecraft mc = Minecraft.getInstance();
        int textX = this.getX() + 5;
        int textY = this.getY() + 8;

        graphics.drawString(mc.font, perk.getDisplayName(), textX, textY, rarityColor | 0xFF000000, false);
        textY += 14;

        boolean hasModifier = perk.modifier != PerkDefinition.Modifier.NONE;
        int actionReserve = this.isHoveredOrFocused() ? 20 : 4;
        int modifierPanelHeight = hasModifier ? Math.min(78, Math.max(58, this.height / 3)) : 0;
        int contentBottom = this.getY() + this.height - actionReserve - modifierPanelHeight - 8;

        graphics.drawString(mc.font, Component.translatable("gui.tac_rogue.perk_screen.effect"), textX, textY, 0xFF8DEFFF, false);
        textY += 11;
        Component pureDesc = perk.getDescriptionComponent();
        java.util.List<net.minecraft.util.FormattedCharSequence> wrappedDesc =
            splitJapaneseFriendly(mc, pureDesc, this.width - 12);
        int descLines = 0;
        int maxDescLines = Math.max(1, (contentBottom - textY) / 10);
        for (net.minecraft.util.FormattedCharSequence line : wrappedDesc) {
            if (descLines >= maxDescLines) break;
            graphics.drawString(mc.font, line, textX, textY, 0xFFCCCCCC, false);
            textY += 10;
            descLines++;
        }
        if (this.isHoveredOrFocused() && descLines < wrappedDesc.size()) {
            graphics.renderComponentTooltip(mc.font, java.util.List.of(pureDesc), mouseX, mouseY);
        }

        if (hasModifier) {
            int modX = this.getX() + 5;
            int modY = this.getY() + this.height - actionReserve - modifierPanelHeight;
            int modW = this.width - 10;
            int modBottom = this.getY() + this.height - actionReserve - 4;
            graphics.fill(modX - 2, modY - 2, modX + modW + 2, modBottom + 2, 0x66000000);
            graphics.renderOutline(modX - 2, modY - 2, modW + 4, Math.max(12, modBottom - modY + 4), perk.modifier.color);

            Component modifierLabel = Component.literal("§7[" + perk.modifier.prefix + "] ")
                .append(Component.translatable("gui.tac_rogue.perk_screen.power",
                    String.format(java.util.Locale.ROOT, "%.1f", perk.modifier.multiplier)));
            graphics.drawString(mc.font, modifierLabel, modX, modY, perk.modifier.color, false);

            Component tradeoffComp = perk.getModifierDescriptionComponent();
            java.util.List<net.minecraft.util.FormattedCharSequence> wrapped =
                splitJapaneseFriendly(mc, tradeoffComp, modW);
            int lineY = modY + 12;
            int lines = 0;
            int maxLines = Math.max(1, (modBottom - lineY) / 10);
            for (net.minecraft.util.FormattedCharSequence line : wrapped) {
                if (lines >= maxLines) break;
                graphics.drawString(mc.font, line, modX, lineY, perk.modifier.color, false);
                lineY += 10;
                lines++;
            }
            if (this.isHoveredOrFocused() && lines < wrapped.size()) {
                graphics.renderComponentTooltip(mc.font,
                    java.util.List.of(perk.getDescriptionComponent(), tradeoffComp), mouseX, mouseY);
            }
        }

        if (this.isHoveredOrFocused()) {
            graphics.drawCenteredString(mc.font, Component.translatable("gui.tac_rogue.perk_screen.click_select"),
                this.getX() + this.width / 2, this.getY() + this.height - 14, 0xFF00FF00);
        }
    }

    private static java.util.List<net.minecraft.util.FormattedCharSequence> splitJapaneseFriendly(
            Minecraft mc, Component component, int width) {
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        String text = component.getString()
            .replace("。", "。\n")
            .replace(". ", ".\n")
            .replace("; ", ";\n");
        for (String paragraph : text.split("\\n")) {
            if (paragraph.isBlank()) continue;
            lines.addAll(mc.font.split(Component.literal(paragraph), width));
        }
        return lines;
    }
}
