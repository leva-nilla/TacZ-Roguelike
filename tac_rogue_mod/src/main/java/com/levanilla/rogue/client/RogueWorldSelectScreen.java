package com.levanilla.rogue.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RogueWorldSelectScreen extends Screen {
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final List<LevelSummary> worlds = new ArrayList<>();
    private Button playButton;
    private int selectedIndex = -1;
    private int scroll;
    private boolean loading = true;
    private String loadError = "";

    public RogueWorldSelectScreen(Screen parent) {
        super(Component.translatable("gui.tac_rogue.world_select.header"));
        this.parent = parent;
    }

    public static void openFromVanilla(Screen parent) {
        Minecraft.getInstance().setScreen(new RogueWorldSelectScreen(parent));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        ScreenLayout layout = screenLayout();

        this.playButton = addRenderableWidget(Button.builder(
            Component.translatable("selectWorld.select"),
            button -> playSelected()
        ).bounds(layout.playX, layout.buttonY, layout.primaryW, layout.buttonH).build());
        addRenderableWidget(Button.builder(
            Component.translatable("selectWorld.create"),
            button -> CreateWorldScreen.openFresh(this.minecraft, this)
        ).bounds(layout.createX, layout.buttonY, layout.primaryW, layout.buttonH).build());
        addRenderableWidget(Button.builder(
            Component.translatable("gui.cancel"),
            button -> onClose()
        ).bounds(layout.cancelX, layout.buttonY, layout.cancelW, layout.buttonH).build());

        updateButtonState();
        loadWorlds();
    }

    private void loadWorlds() {
        this.loading = true;
        this.loadError = "";
        try {
            LevelStorageSource source = this.minecraft.getLevelSource();
            source.loadLevelSummaries(source.findLevelCandidates()).thenAccept(list -> {
                List<LevelSummary> sorted = new ArrayList<>(list);
                sorted.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
                this.minecraft.execute(() -> {
                    this.worlds.clear();
                    this.worlds.addAll(sorted);
                    this.loading = false;
                    if (!this.worlds.isEmpty() && this.selectedIndex < 0) {
                        this.selectedIndex = 0;
                    }
                    this.scroll = clamp(this.scroll, 0, maxScroll());
                    updateButtonState();
                });
            }).exceptionally(error -> {
                this.minecraft.execute(() -> {
                    this.loading = false;
                    this.loadError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    updateButtonState();
                });
                return null;
            });
        } catch (LevelStorageException e) {
            this.loading = false;
            this.loadError = e.getMessage();
            updateButtonState();
        }
    }

    private void updateButtonState() {
        if (this.playButton != null) {
            this.playButton.active = selected() != null && !this.loading;
        }
    }

    private LevelSummary selected() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.worlds.size()) return null;
        return this.worlds.get(this.selectedIndex);
    }

    private void playSelected() {
        LevelSummary summary = selected();
        if (summary == null || this.minecraft == null) return;
        this.minecraft.createWorldOpenFlows().loadLevel(this, summary.getLevelId());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenLayout layout = screenLayout();
        TacticalScreenStyle.renderBackground(graphics, this.width, this.height);
        renderWorldList(graphics, mouseX, mouseY, layout.list);
        renderHeader(graphics, layout.header);
        renderSelectedBriefing(graphics, layout.briefing);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderables.forEach(renderable -> {
            if (renderable instanceof Button button && button.visible) {
                TacticalScreenStyle.drawButton(graphics, this.font, button);
            }
        });
    }

    private void renderWorldList(GuiGraphics graphics, int mouseX, int mouseY, ListBounds bounds) {
        TacticalScreenStyle.drawPanel(graphics, bounds.x - 12, bounds.y - 28, bounds.x + bounds.w + 12, bounds.bottom + 12);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.world_select.archive"),
            bounds.x, bounds.y - 18, 0xFF74DDBE, false);

        if (this.loading) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.world_select.loading"),
                bounds.x + bounds.w / 2, bounds.y + bounds.h / 2 - 4, 0xFF8A98A0);
            return;
        }
        if (!this.loadError.isEmpty()) {
            String message = TacticalScreenStyle.fitLabel(this.font, this.loadError, bounds.w - 20);
            graphics.drawCenteredString(this.font, Component.literal(message), bounds.x + bounds.w / 2, bounds.y + bounds.h / 2 - 4, 0xFFFF7777);
            return;
        }
        if (this.worlds.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.tac_rogue.world_select.empty"),
                bounds.x + bounds.w / 2, bounds.y + bounds.h / 2 - 4, 0xFF8A98A0);
            return;
        }

        graphics.enableScissor(bounds.x - 2, bounds.y, bounds.x + bounds.w + 2, bounds.bottom);
        int y = bounds.y - this.scroll;
        for (int i = 0; i < this.worlds.size(); i++) {
            LevelSummary summary = this.worlds.get(i);
            if (y + bounds.rowH >= bounds.y && y <= bounds.bottom) {
                drawWorldRow(graphics, summary, i, bounds.x, y, bounds.w, bounds.rowH,
                    mouseX >= bounds.x && mouseX <= bounds.x + bounds.w && mouseY >= y && mouseY <= y + bounds.rowH);
            }
            y += bounds.rowH + 6;
        }
        graphics.disableScissor();
    }

    private void drawWorldRow(GuiGraphics graphics, LevelSummary summary, int index, int x, int y, int w, int h, boolean hover) {
        boolean selected = index == this.selectedIndex;
        int border = selected ? 0xFFE6C76A : hover ? 0xDD8FFFF0 : 0x8854E7C4;
        int fill = selected ? 0xD20E2020 : hover ? 0xC60A181A : 0xB805090B;
        graphics.fill(x, y, x + w, y + h, 0xE0010304);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        graphics.fill(x, y, x + w, y + 2, border);
        graphics.fill(x, y + h - 2, x + w, y + h, selected ? 0xAAE6C76A : 0x6654E7C4);
        graphics.fill(x, y, x + 2, y + h, border);

        int thumb = Math.min(h - 12, 48);
        int thumbX = x + 10;
        int thumbY = y + (h - thumb) / 2;
        graphics.fill(thumbX, thumbY, thumbX + thumb, thumbY + thumb, selected ? 0xFF133634 : 0xFF10191A);
        graphics.fill(thumbX + 3, thumbY + 3, thumbX + thumb - 3, thumbY + thumb - 3, 0xFF071016);
        graphics.fill(thumbX + 7, thumbY + thumb / 2, thumbX + thumb - 7, thumbY + thumb / 2 + 2, 0xAA54E7C4);
        graphics.fill(thumbX + thumb / 2, thumbY + 7, thumbX + thumb / 2 + 2, thumbY + thumb - 7, 0x6654E7C4);

        int textX = thumbX + thumb + 12;
        int textW = Math.max(40, w - (textX - x) - 12);
        String name = summary.getLevelName().isBlank() ? summary.getLevelId() : summary.getLevelName();
        graphics.drawString(this.font, Component.literal(TacticalScreenStyle.fitLabel(this.font, name, textW)), textX, y + 12, 0xFFE8FFF8, false);
        graphics.drawString(this.font, Component.literal(TacticalScreenStyle.fitLabel(this.font, summary.getLevelId(), textW)), textX, y + 30, 0xFF74DDBE, false);
        String info = summary.getInfo().getString();
        graphics.drawString(this.font, Component.literal(TacticalScreenStyle.fitLabel(this.font, info, textW)), textX, y + 50, 0xFF8A98A0, false);
        graphics.drawString(this.font, Component.literal(formatTime(summary.getLastPlayed()).toUpperCase(Locale.ROOT)), textX, y + h - 16, 0xFF708087, false);
    }

    private void renderHeader(GuiGraphics graphics, Rect header) {
        if (!header.visible()) return;
        int x = header.x;
        int y = header.y;
        int panelW = header.w;
        int panelH = header.h;

        TacticalScreenStyle.drawPanel(graphics, x, y, x + panelW, y + panelH);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.world_select.header"), x + 12, y + 12, 0xFFE8FFF8, false);
        if (panelH > 44) {
            String subtitle = Component.translatable("gui.tac_rogue.world_select.subtitle").getString();
            graphics.drawString(this.font, Component.literal(TacticalScreenStyle.fitLabel(this.font, subtitle, panelW - 24)), x + 12, y + 32, 0xFF74DDBE, false);
        }
    }

    private void renderSelectedBriefing(GuiGraphics graphics, Rect briefing) {
        LevelSummary summary = selected();
        if (summary == null) return;
        if (!briefing.visible()) return;
        int x = briefing.x;
        int y = briefing.y;
        int w = briefing.w;
        int h = briefing.h;

        TacticalScreenStyle.drawPanel(graphics, x, y, x + w, y + h);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.world_select.selected"), x + 12, y + 12, 0xFF74DDBE, false);
        String name = summary.getLevelName().isBlank() ? summary.getLevelId() : summary.getLevelName();
        graphics.drawString(this.font, Component.literal(TacticalScreenStyle.fitLabel(this.font, name, w - 24)), x + 12, y + 30, 0xFFE8FFF8, false);
        graphics.drawString(this.font, Component.literal(TacticalScreenStyle.fitLabel(this.font, summary.getInfo().getString(), w - 24)), x + 12, y + 48, 0xFF8A98A0, false);
        graphics.drawString(this.font, Component.literal(formatTime(summary.getLastPlayed()).toUpperCase(Locale.ROOT)), x + 12, y + 66, 0xFF708087, false);
        if (h > 90) {
            graphics.drawString(this.font, Component.literal(TacticalScreenStyle.fitLabel(this.font, summary.getLevelId(), w - 24)), x + 12, y + 84, 0xFFE6C76A, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                if (row == this.selectedIndex) {
                    playSelected();
                } else {
                    this.selectedIndex = row;
                    updateButtonState();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ListBounds bounds = listBounds();
        if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.w && mouseY >= bounds.y && mouseY <= bounds.bottom) {
            this.scroll = clamp(this.scroll - (int) (delta * 24), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int rowAt(double mouseX, double mouseY) {
        ListBounds bounds = listBounds();
        if (mouseX < bounds.x || mouseX > bounds.x + bounds.w || mouseY < bounds.y || mouseY > bounds.bottom) return -1;
        int local = (int) mouseY - bounds.y + this.scroll;
        int stride = bounds.rowH + 6;
        int row = local / stride;
        int yInRow = local % stride;
        if (row >= 0 && row < this.worlds.size() && yInRow <= bounds.rowH) return row;
        return -1;
    }

    private int maxScroll() {
        ListBounds bounds = listBounds();
        int content = this.worlds.size() * (bounds.rowH + 6) - 6;
        return Math.max(0, content - bounds.h);
    }

    private ListBounds listBounds() {
        return screenLayout().list;
    }

    private ScreenLayout screenLayout() {
        int margin = clamp(this.width / 36, 8, 34);
        int bottomSpace = this.height < 340 ? 50 : 62;
        int top = clamp(this.height / 7, 54, 96);
        int bottom = this.height - bottomSpace;
        int center = this.width / 2;
        int gap = clamp(this.width / 32, 18, 32);
        int leftX = margin + clamp(this.width / 72, 4, 14);
        int leftRight = center - gap;
        int rightX = center + gap;
        int rightRight = this.width - margin - clamp(this.width / 72, 4, 14);
        int w = Math.max(170, leftRight - leftX);
        int h = Math.max(80, bottom - top);
        int rowH = this.height < 360 ? 74 : 88;
        ListBounds list = new ListBounds(leftX, top, w, h, top + h, rowH);

        int rightW = Math.max(0, rightRight - rightX);
        Rect header = rightW >= 150
            ? new Rect(rightX, top, rightW, this.height < 420 ? 42 : 58)
            : Rect.hidden();
        int briefingY = header.visible() ? header.y + header.h + clamp(this.height / 26, 10, 18) : top;
        int briefingH = Math.min(this.height < 420 ? 84 : 122, Math.max(0, bottom - briefingY - 12));
        Rect briefing = rightW >= 150 && briefingH >= 64
            ? new Rect(rightX, briefingY, rightW, briefingH)
            : Rect.hidden();

        int buttonH = this.height < 340 ? 18 : 22;
        int buttonY = this.height - buttonH - 18;
        int buttonGap = clamp(this.width / 80, 6, 12);
        int cancelW = Math.max(88, Math.min(150, rightW));
        int primaryW;
        int playX;
        int createX;
        int cancelX;
        if (this.width >= 520) {
            primaryW = Math.max(82, Math.min(190, (w - buttonGap) / 2));
            playX = leftX;
            createX = playX + primaryW + buttonGap;
            cancelX = rightW > 0 ? rightX + rightW - cancelW : this.width - margin - cancelW;
        } else {
            int usable = this.width - margin * 2;
            primaryW = Math.max(96, Math.min(180, (usable - buttonGap * 2) / 3));
            playX = margin;
            createX = playX + primaryW + buttonGap;
            cancelX = createX + primaryW + buttonGap;
            cancelW = Math.min(cancelW, Math.max(88, this.width - cancelX - margin));
        }

        return new ScreenLayout(list, header, briefing, buttonY, buttonH, playX, createX, primaryW, cancelX, cancelW);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static String formatTime(long millis) {
        if (millis <= 0L) return "UNKNOWN TIME";
        return DATE_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ListBounds(int x, int y, int w, int h, int bottom, int rowH) {}

    private record Rect(int x, int y, int w, int h) {
        static Rect hidden() {
            return new Rect(0, 0, 0, 0);
        }

        boolean visible() {
            return this.w > 0 && this.h > 0;
        }
    }

    private record ScreenLayout(
        ListBounds list,
        Rect header,
        Rect briefing,
        int buttonY,
        int buttonH,
        int playX,
        int createX,
        int primaryW,
        int cancelX,
        int cancelW
    ) {}
}
