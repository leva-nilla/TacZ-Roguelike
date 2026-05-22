package com.levanilla.rogue.client;

import com.levanilla.rogue.core.QuestManager;
import com.levanilla.rogue.networking.QuestSelectionMessage;
import com.levanilla.rogue.networking.TacRogueNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class QuestScreen extends Screen {
    private static int cachedChapter = 1;
    private static final List<String[]> cachedQuests = new ArrayList<>();
    private static final Set<String> candidateQuestIds = new LinkedHashSet<>();
    private static final Set<String> selectedQuestIds = new LinkedHashSet<>();
    private static boolean selectionLocked = false;

    private int scrollOffset = 0;
    private int selectedIndex = 0;
    private Button confirmButton;

    public QuestScreen() {
        super(Component.translatable("gui.tac_rogue.quest_screen.title"));
    }

    public static void syncQuestData(int chapter, List<String[]> quests) {
        syncQuestData(chapter, quests, Set.of(), Set.of(), true);
    }

    public static void syncQuestData(int chapter, List<String[]> quests, Set<String> candidateIds, Set<String> selectedIds, boolean locked) {
        cachedChapter = chapter;
        cachedQuests.clear();
        cachedQuests.addAll(quests);
        candidateQuestIds.clear();
        candidateQuestIds.addAll(candidateIds);
        selectedQuestIds.clear();
        selectedQuestIds.addAll(selectedIds);
        selectionLocked = locked;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        Layout layout = layout();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.common.close"), b -> this.onClose())
            .bounds(layout.x + layout.panelW - 70, layout.y + 10, 54, 18).build());
        confirmButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.tac_rogue.quest_screen.confirm"), b -> confirmSelection())
            .bounds(layout.x + layout.panelW - 194, layout.y + 10, 112, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        Layout l = layout();
        clampState(l);
        if (confirmButton != null) {
            confirmButton.visible = !selectionLocked;
            confirmButton.active = selectedQuestIds.size() == QuestManager.REQUIRED_SIDE_QUESTS;
        }

        graphics.fill(l.x, l.y, l.x + l.panelW, l.y + l.panelH, 0xF006101D);
        graphics.fill(l.x, l.y, l.x + l.panelW, l.y + 2, 0xFF55DDAA);
        graphics.renderOutline(l.x, l.y, l.panelW, l.panelH, 0xAA55DDAA);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.title"), l.x + 14, l.y + 12, 0xFFAAFFDD, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.chapter", cachedChapter), l.x + 14, l.y + 25, 0xFFFFD45C, false);

        if (l.summaryW > 0) {
            renderSummary(graphics, l.summaryX, l.summaryY, l.summaryW, l.summaryH);
        }
        renderQuestList(graphics, mouseX, mouseY, l.listX, l.listY, l.listW, l.listH);
        if (l.detailW > 0) {
            renderQuestDetail(graphics, l.detailX, l.detailY, l.detailW, l.detailH);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSummary(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x66000000);
        graphics.renderOutline(x, y, w, h, 0x6644AA88);
        graphics.enableScissor(x, y, x + w, y + h);
        boolean compact = h < 118;
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.summary"), x + 8, y + 8, 0xFF7FDDBB, false);
        long completed = cachedQuests.stream().filter(this::isActiveQuest).filter(q -> "true".equals(field(q, 5, "false"))).count();
        long required = cachedQuests.stream().filter(this::isActiveQuest).count();
        graphics.drawString(this.font, completed + "/" + required, x + w - 42, y + 8, 0xFFFFD45C, false);
        if (!compact) {
            graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.completed"), x + 8, y + 25, 0xFFAAFFDD, false);
        }

        if (cachedChapter >= 1) {
            Component storyTitle = Component.translatable(com.levanilla.rogue.core.QuestManager.getChapterTitleKey(cachedChapter));
            Component storySummary = Component.translatable(com.levanilla.rogue.core.QuestManager.getChapterSummaryKey(cachedChapter));
            Component storyBackground = Component.translatable(com.levanilla.rogue.core.QuestManager.getChapterBackgroundKey(cachedChapter));
            int yy = y + (compact ? 24 : 42);
            yy = drawWrapped(graphics, storyTitle, x + 8, yy, w - 16, 0xFFE6E6E6, compact ? 1 : 2);
            yy += compact ? 2 : 4;
            yy = drawWrapped(graphics, storySummary, x + 8, yy, w - 16, 0xFFBFD7D7, compact ? 2 : 4);
            yy += compact ? 2 : 4;
            int maxBackgroundLines = Math.max(0, (y + h - (compact ? 26 : 40) - yy) / 10);
            if (maxBackgroundLines > 0) {
                drawWrapped(graphics, storyBackground, x + 8, yy, w - 16, 0xFF8FA5AA, maxBackgroundLines);
            }
        }

        Component status = selectionLocked
            ? Component.translatable("gui.tac_rogue.quest_screen.selection_locked")
            : Component.translatable("gui.tac_rogue.quest_screen.select_count", selectedQuestIds.size(), QuestManager.REQUIRED_SIDE_QUESTS);
        drawWrapped(graphics, status, x + 8, y + h - (compact ? 18 : 32), w - 16, selectionLocked ? 0xFF77FFAA : 0xFFFFD45C, compact ? 1 : 3);
        graphics.disableScissor();
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
            boolean questSelected = isSelectedQuest(quest);
            boolean active = isActiveQuest(quest);
            int bg = questSelected ? 0x7755DDAA : selected ? 0x7744FFAA : hovered ? 0x4433AAFF : 0x22000000;
            graphics.fill(x + 4, qy, x + w - 8, qy + rowH - 4, bg);
            graphics.renderOutline(x + 4, qy, w - 12, rowH - 4, questSelected ? 0xCCFFD45C : selected ? 0xAA55FFAA : 0x33336655);

            Component type = Component.translatable(field(quest, 1, "quest.tac_rogue.type.kill"));
            Component role = Component.translatable(field(quest, 6, "quest.tac_rogue.role.contract"));
            boolean completed = "true".equals(field(quest, 5, "false"));
            boolean rare = "true".equals(field(quest, 7, "false"));
            int target = parseInt(field(quest, 2, "0"));
            int progress = parseInt(field(quest, 3, "0"));

            String marker = completed ? "[OK]" : active ? "[..]" : questSelected ? "[+]" : "[ ]";
            graphics.drawString(this.font, marker, x + 10, qy + 5, completed ? 0xFF77FFAA : questSelected ? 0xFFFFD45C : 0xFF777777, false);
            graphics.drawString(this.font, type, x + 38, qy + 5, completed ? 0xFF77FFAA : active ? 0xFFE6E6E6 : 0xFF999999, false);
            Component roleLabel = rare
                ? Component.translatable("gui.tac_rogue.quest_screen.rare_contract")
                : isStoryQuest(quest) ? Component.translatable("gui.tac_rogue.quest_screen.story_required") : role;
            graphics.drawString(this.font, roleLabel, x + 38, qy + 17, rare ? 0xFFFF66DD : isStoryQuest(quest) ? 0xFFFFD45C : 0xFF88AAFF, false);
            if (questSelected && w > 166) graphics.drawString(this.font, Component.translatable("gui.tac_rogue.quest_screen.selected"), x + w - 58, qy + 5, 0xFFFFD45C, false);

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
        int listTop = l.listY + 22;
        int visible = Math.max(1, (l.listH - 30) / rowH);
        for (int row = 0; row < visible; row++) {
            int idx = scrollOffset + row;
            int qy = listTop + row * rowH;
            if (idx < cachedQuests.size() && mouseX >= l.listX + 4 && mouseX < l.listX + l.listW - 8
                && mouseY >= qy && mouseY < qy + rowH - 4) {
                selectedIndex = idx;
                if (!selectionLocked) {
                    toggleQuestSelection(cachedQuests.get(idx));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout l = layout();
        int visible = Math.max(1, (l.listH - 30) / 36);
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
        boolean stacked = panelW < 600 || panelH < 242;
        int summaryW = stacked ? contentW : panelW >= 680 ? 160 : 138;
        int summaryH = stacked ? Math.min(108, Math.max(74, contentH / 3)) : contentH;
        int summaryY = contentY;
        int listY = stacked ? contentY + summaryH + gap : contentY;
        int listH = stacked ? Math.max(54, contentH - summaryH - gap) : contentH;
        int remaining = stacked ? contentW : contentW - summaryW - gap;
        int detailW = 0;
        int listW = remaining;
        if (!stacked && remaining >= 330) {
            detailW = Math.min(210, Math.max(140, remaining / 3));
            listW = Math.max(150, remaining - detailW - gap);
        }
        int summaryX = x + margin;
        int listX = stacked ? summaryX : summaryX + summaryW + gap;
        int detailX = listX + listW + gap;
        int detailY = listY;
        int detailH = listH;
        return new Layout(x, y, panelW, panelH, contentY, contentH,
            summaryX, summaryY, summaryW, summaryH,
            listX, listY, listW, listH,
            detailX, detailY, detailW, detailH);
    }

    private void clampState(Layout layout) {
        if (cachedQuests.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, cachedQuests.size() - 1));
        int visible = Math.max(1, (layout.listH - 30) / 36);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, cachedQuests.size() - visible)));
    }

    private void toggleQuestSelection(String[] quest) {
        if (isStoryQuest(quest) || !isCandidateQuest(quest)) return;
        String id = field(quest, 0, "");
        if (selectedQuestIds.contains(id)) {
            selectedQuestIds.remove(id);
            return;
        }
        if (selectedQuestIds.size() < QuestManager.REQUIRED_SIDE_QUESTS) {
            selectedQuestIds.add(id);
        }
    }

    private void confirmSelection() {
        if (selectionLocked || selectedQuestIds.size() != QuestManager.REQUIRED_SIDE_QUESTS) return;
        TacRogueNetworking.CHANNEL.sendToServer(new QuestSelectionMessage(new ArrayList<>(selectedQuestIds)));
    }

    private boolean isStoryQuest(String[] quest) {
        return "true".equals(field(quest, 12, "false")) || field(quest, 0, "").startsWith("story_");
    }

    private boolean isCandidateQuest(String[] quest) {
        return "true".equals(field(quest, 11, "false")) || candidateQuestIds.contains(field(quest, 0, ""));
    }

    private boolean isSelectedQuest(String[] quest) {
        return "true".equals(field(quest, 10, "false")) || selectedQuestIds.contains(field(quest, 0, ""));
    }

    private boolean isActiveQuest(String[] quest) {
        return "true".equals(field(quest, 9, "true")) || isStoryQuest(quest) || isSelectedQuest(quest);
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
        int summaryX, int summaryY, int summaryW, int summaryH,
        int listX, int listY, int listW, int listH,
        int detailX, int detailY, int detailW, int detailH) {}
}
