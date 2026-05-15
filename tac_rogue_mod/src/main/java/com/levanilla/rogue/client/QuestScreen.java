package com.levanilla.rogue.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class QuestScreen extends Screen {
    private static int cachedChapter = 1;
    private static final List<String[]> cachedQuests = new ArrayList<>();

    private int scrollOffset = 0;
    private int selectedIndex = 0;

    public QuestScreen() {
        super(Component.translatable("gui.tac_rogue.quest_screen.title"));
    }

    public static void syncQuestData(int chapter, List<String[]> quests) {
        cachedChapter = chapter;
        cachedQuests.clear();
        cachedQuests.addAll(quests);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        Layout layout = layout();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.common.close"), b -> this.onClose())
            .bounds(layout.x + layout.panelW - 70, layout.y + 10, 54, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        Layout l = layout();
        clampState(l);

        graphics.fill(l.x, l.y, l.x + l.panelW, l.y + l.panelH, 0xF006101D);
        graphics.fill(l.x, l.y, l.x + l.panelW, l.y + 2, 0xFF55DDAA);
        graphics.renderOutline(l.x, l.y, l.panelW, l.panelH, 0xAA55DDAA);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.title"), l.x + 14, l.y + 12, 0xFFAAFFDD, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.chapter", cachedChapter), l.x + 14, l.y + 25, 0xFFFFD45C, false);

        if (l.summaryW > 0) {
            renderSummary(graphics, l.summaryX, l.contentY, l.summaryW, l.contentH);
        }
        renderQuestList(graphics, mouseX, mouseY, l.listX, l.contentY, l.listW, l.contentH);
        if (l.detailW > 0) {
            renderQuestDetail(graphics, l.detailX, l.contentY, l.detailW, l.contentH);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSummary(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x66000000);
        graphics.renderOutline(x, y, w, h, 0x6644AA88);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.summary"), x + 8, y + 8, 0xFF7FDDBB, false);
        long completed = cachedQuests.stream().filter(q -> q.length > 5 && "true".equals(q[5])).count();
        graphics.drawString(this.font, completed + "/" + cachedQuests.size(), x + 8, y + 27, 0xFFFFD45C, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.completed"), x + 8, y + 39, 0xFFAAFFDD, false);

        if (cachedChapter >= 1) {
            Component storyTitle = Component.translatable(com.levanilla.rogue.core.QuestManager.getChapterTitleKey(cachedChapter));
            drawWrapped(graphics, storyTitle, x + 8, y + 62, w - 16, 0xFFE6E6E6, 5);
        }

        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.commander"), x + 8, y + h - 26, 0xFF777777, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.operations"), x + 8, y + h - 14, 0xFF777777, false);
    }

    private void renderQuestList(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x66000000);
        graphics.renderOutline(x, y, w, h, 0x6644AA88);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.quests"), x + 8, y + 7, 0xFF7FDDBB, false);

        int rowH = 36;
        int listTop = y + 22;
        int visible = Math.max(1, (h - 30) / rowH);
        int maxScroll = Math.max(0, cachedQuests.size() - visible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (cachedQuests.isEmpty()) {
            drawWrapped(graphics, Component.translatable("gui.tac_rogue.quest_screen.no_data"), x + 8, listTop + 6, w - 16, 0xFF888888, 4);
            return;
        }

        for (int row = 0; row < visible; row++) {
            int idx = scrollOffset + row;
            if (idx >= cachedQuests.size()) break;
            String[] quest = cachedQuests.get(idx);
            int qy = listTop + row * rowH;
            boolean selected = idx == selectedIndex;
            boolean hovered = mouseX >= x + 4 && mouseX < x + w - 8 && mouseY >= qy && mouseY < qy + rowH - 4;
            int bg = selected ? 0x7744FFAA : hovered ? 0x4433AAFF : 0x22000000;
            graphics.fill(x + 4, qy, x + w - 8, qy + rowH - 4, bg);
            graphics.renderOutline(x + 4, qy, w - 12, rowH - 4, selected ? 0xAA55FFAA : 0x33336655);

            Component type = Component.translatable(field(quest, 1, "quest.tac_rogue.type.kill"));
            Component role = Component.translatable(field(quest, 6, "quest.tac_rogue.role.contract"));
            boolean completed = "true".equals(field(quest, 5, "false"));
            boolean rare = "true".equals(field(quest, 7, "false"));
            int target = parseInt(field(quest, 2, "0"));
            int progress = parseInt(field(quest, 3, "0"));

            graphics.drawString(this.font, completed ? "[OK]" : "[..]", x + 10, qy + 5, completed ? 0xFF77FFAA : 0xFFFFD45C, false);
            graphics.drawString(this.font, type, x + 38, qy + 5, completed ? 0xFF77FFAA : 0xFFE6E6E6, false);
            graphics.drawString(this.font, role, x + 38, qy + 17, rare ? 0xFFFF66DD : 0xFF88AAFF, false);
            if (rare && w > 160) graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.rare"), x + w - 42, qy + 5, 0xFFFF66DD, false);

            int barX = x + 38;
            int barY = qy + 28;
            int barW = Math.max(32, w - 92);
            float ratio = target > 0 ? Math.min(1.0f, progress / (float) target) : 0.0f;
            graphics.fill(barX, barY, barX + barW, barY + 3, 0xFF1A222A);
            graphics.fill(barX, barY, barX + Math.round(barW * ratio), barY + 3, completed ? 0xFF55FF88 : 0xFF55DDAA);
            if (w > 150) {
                graphics.drawString(this.font, progress + "/" + target, barX + barW + 5, qy + 23, 0xFFCCCCCC, false);
            }
        }

        if (scrollOffset > 0) graphics.drawString(this.font, "^", x + w - 16, y + 7, 0xFF88FFCC, false);
        if (scrollOffset < maxScroll) graphics.drawString(this.font, "v", x + w - 16, y + h - 14, 0xFF88FFCC, false);
    }

    private void renderQuestDetail(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x66000000);
        graphics.renderOutline(x, y, w, h, 0x6644AA88);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.detail"), x + 8, y + 7, 0xFF7FDDBB, false);

        if (cachedQuests.isEmpty() || selectedIndex < 0 || selectedIndex >= cachedQuests.size()) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.no_selected"), x + 8, y + 28, 0xFF777777, false);
            return;
        }

        String[] quest = cachedQuests.get(selectedIndex);
        int target = parseInt(field(quest, 2, "0"));
        int progress = parseInt(field(quest, 3, "0"));
        int gold = parseInt(field(quest, 4, "0"));
        boolean completed = "true".equals(field(quest, 5, "false"));
        boolean rare = "true".equals(field(quest, 7, "false"));

        int yy = y + 26;
        yy = drawWrapped(graphics, Component.translatable(field(quest, 1, "quest.tac_rogue.type.kill")), x + 8, yy, w - 16, 0xFFFFFFFF, 3);
        yy += 3;
        yy = drawWrapped(graphics, Component.translatable(field(quest, 6, "quest.tac_rogue.role.contract")), x + 8, yy, w - 16, 0xFF88AAFF, 2);
        yy += 8;
        yy = drawWrapped(graphics, Component.translatable(field(quest, 8, field(quest, 1, "quest.tac_rogue.type.kill") + ".desc"), target), x + 8, yy, w - 16, 0xFFCCCCCC, 5);
        yy += 6;

        int barW = Math.max(24, w - 16);
        float ratio = target > 0 ? Math.min(1.0f, progress / (float)target) : 0.0f;
        graphics.fill(x + 8, yy, x + 8 + barW, yy + 10, 0xFF1A222A);
        graphics.fill(x + 8, yy, x + 8 + Math.round(barW * ratio), yy + 10, completed ? 0xFF55FF88 : 0xFF55DDAA);
        graphics.renderOutline(x + 8, yy, barW, 10, 0x6644AA88);
        yy += 16;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.progress", progress, target), x + 8, yy, 0xFFCCCCCC, false);

        int rewardH = Math.max(54, Math.min(72, h - 98));
        int rewardY = y + h - rewardH - 8;
        graphics.fill(x + 8, rewardY, x + w - 8, rewardY + rewardH, 0x77241410);
        graphics.renderOutline(x + 8, rewardY, w - 16, rewardH, 0x99FFD45C);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.reward"), x + 16, rewardY + 8, 0xFFFFD45C, false);
        graphics.drawString(this.font, gold + " G", x + 16, rewardY + 25, 0xFFFFD45C, false);
        graphics.drawString(this.font, Component.translatable(rare ? "gui.tac_rogue.quest_screen.reward_rare" : "gui.tac_rogue.quest_screen.reward_standard"), x + 16, rewardY + 39,
            rare ? 0xFFFF66DD : 0xFFAAAAAA, false);
        if (rewardH >= 68) {
            graphics.drawString(this.font, Component.translatable(completed ? "gui.tac_rogue.quest_screen.status_complete" : "gui.tac_rogue.quest_screen.status_active"), x + 16, rewardY + 53,
                completed ? 0xFF77FFAA : 0xFFE6E6E6, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout l = layout();
        int rowH = 36;
        int listTop = l.contentY + 22;
        int visible = Math.max(1, (l.contentH - 30) / rowH);
        for (int row = 0; row < visible; row++) {
            int idx = scrollOffset + row;
            int qy = listTop + row * rowH;
            if (idx < cachedQuests.size() && mouseX >= l.listX + 4 && mouseX < l.listX + l.listW - 8
                && mouseY >= qy && mouseY < qy + rowH - 4) {
                selectedIndex = idx;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout l = layout();
        int visible = Math.max(1, (l.contentH - 30) / 36);
        int maxScroll = Math.max(0, cachedQuests.size() - visible);
        if (delta < 0 && scrollOffset < maxScroll) scrollOffset++;
        if (delta > 0 && scrollOffset > 0) scrollOffset--;
        return true;
    }

    private Layout layout() {
        int panelW = Math.min(760, Math.max(120, this.width - 16));
        int panelH = Math.min(306, Math.max(180, this.height - 16));
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int margin = panelW >= 420 ? 14 : 8;
        int gap = panelW >= 420 ? 12 : 8;
        int contentY = y + 42;
        int contentH = panelH - 54;
        int contentW = Math.max(80, panelW - margin * 2);
        int summaryW = panelW >= 600 ? 108 : 0;
        int remaining = contentW - summaryW - (summaryW > 0 ? gap : 0);
        int detailW = 0;
        int listW = remaining;
        if (remaining >= 330) {
            detailW = Math.min(210, Math.max(140, remaining / 3));
            listW = Math.max(150, remaining - detailW - gap);
        }
        int summaryX = x + margin;
        int listX = summaryX + (summaryW > 0 ? summaryW + gap : 0);
        int detailX = listX + listW + gap;
        return new Layout(x, y, panelW, panelH, contentY, contentH, summaryX, summaryW, listX, listW, detailX, detailW);
    }

    private void clampState(Layout layout) {
        if (cachedQuests.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, cachedQuests.size() - 1));
        int visible = Math.max(1, (layout.contentH - 30) / 36);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, cachedQuests.size() - visible)));
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxLines) {
        int lines = 0;
        for (var line : this.font.split(text, width)) {
            if (lines >= maxLines) break;
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
            lines++;
        }
        return y;
    }

    private static String field(String[] quest, int index, String fallback) {
        return quest.length > index ? quest[index] : fallback;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Layout(int x, int y, int panelW, int panelH, int contentY, int contentH,
                          int summaryX, int summaryW, int listX, int listW, int detailX, int detailW) {}
}
