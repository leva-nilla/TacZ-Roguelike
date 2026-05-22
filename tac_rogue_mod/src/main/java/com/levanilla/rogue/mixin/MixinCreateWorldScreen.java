package com.levanilla.rogue.mixin;

import com.levanilla.rogue.client.TacticalScreenStyle;
import com.levanilla.rogue.core.DifficultyManager;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends net.minecraft.client.gui.screens.Screen {
    @Unique
    private static final String[] TAC_ROGUE$OPERATION_NAMES = {
        "KESTREL", "IRON VEIL", "BLACKWATER", "NIGHTFALL", "FALCON", "ASHLINE",
        "SABLE GATE", "CINDER WARD", "GLASS SPEAR", "RAVEN LOCK", "DUST HARBOR", "VOID LANCE"
    };

    @Unique
    private boolean tacRogue$operationNameApplied;
    @Unique
    private static Method tacRogue$onCreateMethod;

    protected MixinCreateWorldScreen(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        tacRogue$applyDefaultOperationName();
        this.clearWidgets();

        int centerX = this.width / 2;
        int startY = clamp(this.height / 2 - 54, 78, Math.max(78, this.height - 196));
        int btnWidth = clamp(this.width / 4, 128, 210);
        int btnHeight = this.height < 340 ? 18 : 22;
        int gap = this.height < 340 ? 6 : 10;
        int btnX = centerX - btnWidth - clamp(this.width / 24, 18, 42);

        DifficultyManager.Difficulty[] diffs = DifficultyManager.Difficulty.values();
        for (int i = 0; i < diffs.length; i++) {
            DifficultyManager.Difficulty diff = diffs[i];
            boolean selected = diff == DifficultyManager.getDifficulty();
            String prefix = selected ? ">> " : "";
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.literal(prefix + diff.displayName),
                (button) -> {
                    DifficultyManager.setDifficulty(diff.ordinal());
                    this.rebuildWidgets();
                }
            ).bounds(btnX, startY + i * (btnHeight + gap), btnWidth, btnHeight).build());
        }

        int startW = clamp(this.width / 3, 180, 280);
        int startButtonY = Math.min(this.height - 46, startY + diffs.length * (btnHeight + gap) + 24);
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            Component.translatable("gui.tac_rogue.difficulty.start"),
            (button) -> tacRogue$invokeOnCreate()
        ).bounds(centerX - startW / 2, startButtonY, startW, btnHeight + 2).build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TacticalScreenStyle.renderBackground(graphics, this.width, this.height);

        int centerX = this.width / 2;
        int startY = clamp(this.height / 2 - 54, 78, Math.max(78, this.height - 196));
        int btnWidth = clamp(this.width / 4, 128, 210);
        int btnHeight = this.height < 340 ? 18 : 22;
        int gap = this.height < 340 ? 6 : 10;
        int btnX = centerX - btnWidth - clamp(this.width / 24, 18, 42);
        int descX = centerX + clamp(this.width / 36, 12, 28);
        int descW = Math.min(clamp(this.width / 3, 176, 330), this.width - descX - 24);
        int descH = this.height < 340 ? 96 : 128;

        TacticalScreenStyle.drawHeader(
            graphics,
            this.font,
            this.width,
            this.height,
            Component.translatable("gui.tac_rogue.difficulty.header"),
            Component.translatable("gui.tac_rogue.difficulty.subtitle")
        );

        int listPad = 10;
        TacticalScreenStyle.drawPanel(
            graphics,
            btnX - listPad,
            startY - listPad,
            btnX + btnWidth + listPad,
            startY + DifficultyManager.Difficulty.values().length * (btnHeight + gap) - gap + listPad
        );

        DifficultyManager.Difficulty current = DifficultyManager.getDifficulty();
        TacticalScreenStyle.drawPanel(graphics, descX - 10, startY - 10, descX + descW + 10, startY + descH);
        graphics.drawString(this.font, Component.literal(current.displayName + " // RUN PROFILE"), descX, startY, current.color, false);
        graphics.drawString(this.font, Component.translatable("gui.tac_rogue.difficulty.briefing"), descX, startY + 16, 0xFF74DDBE, false);
        String resolved = Component.translatable(current.description).getString();
        String[] descLines = resolved.split("\n");
        for (int i = 0; i < descLines.length; i++) {
            String line = TacticalScreenStyle.fitLabel(this.font, descLines[i], descW);
            graphics.drawString(this.font, line, descX, startY + 34 + i * 14, 0xFFE4FFF8, false);
        }

        int indicatorY = startY + current.ordinal() * (btnHeight + gap) + btnHeight / 2 - 4;
        graphics.drawString(this.font, ">", btnX - 18, indicatorY, 0xFFE6C76A, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Unique
    private void tacRogue$applyDefaultOperationName() {
        if (this.tacRogue$operationNameApplied) return;
        this.tacRogue$operationNameApplied = true;
        WorldCreationUiState state = ((CreateWorldScreen) (Object) this).getUiState();
        String current = state.getName();
        if (current != null && !current.isBlank()
                && !current.equals("New World")
                && !current.equals("新規ワールド")) {
            return;
        }
        state.setName(tacRogue$generateOperationName());
    }

    @Unique
    private static String tacRogue$generateOperationName() {
        long seed = System.nanoTime() ^ java.time.Instant.now().toEpochMilli();
        java.util.Random random = new java.util.Random(seed);
        String code = TAC_ROGUE$OPERATION_NAMES[random.nextInt(TAC_ROGUE$OPERATION_NAMES.length)];
        int number = random.nextInt(99) + 1;
        return String.format(java.util.Locale.ROOT, "LR-TAC OP %s-%02d", code, number);
    }

    @Unique
    private void tacRogue$invokeOnCreate() {
        if (tacRogue$onCreateMethod == null) {
            tacRogue$onCreateMethod = tacRogue$findOnCreateMethod();
        }
        if (tacRogue$onCreateMethod == null) return;
        try {
            tacRogue$onCreateMethod.invoke((CreateWorldScreen) (Object) this);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private static Method tacRogue$findOnCreateMethod() {
        for (String name : new String[] { "onCreate", "m_100972_" }) {
            Class<?> type = CreateWorldScreen.class;
            while (type != null) {
                try {
                    Method method = type.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    type = type.getSuperclass();
                }
            }
        }
        return null;
    }
}
