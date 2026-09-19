package com.wxwr.kaleidoscopeagricultureevolution.client.gui;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class KaeConfigScreen extends Screen {
    private static final Component TITLE = Component.translatable("config.kaleidoscope_agriculture_evolution.title");
    private static final Component GENETICS_ENABLED = Component.translatable("config.kaleidoscope_agriculture_evolution.genetics.enabled");
    private static final Component GENETICS_ENABLED_DESC =
        Component.translatable("config.kaleidoscope_agriculture_evolution.genetics.enabled.desc");
    private static final Component ANALYZER_BOOK_MODE =
        Component.translatable("config.kaleidoscope_agriculture_evolution.genetics.analyzer_book_mode");
    private static final Component ANALYZER_BOOK_MODE_DESC =
        Component.translatable("config.kaleidoscope_agriculture_evolution.genetics.analyzer_book_mode.desc");
    private static final Component SERVER_CONFIG_UNAVAILABLE =
        Component.translatable("config.kaleidoscope_agriculture_evolution.server_config_unavailable");

    private final Screen parent;
    private boolean geneticsEnabled;
    private boolean analyzerBookMode;
    private Button geneticsButton;
    private Button analyzerBookButton;
    private Component statusMessage = Component.empty();

    public KaeConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
        this.geneticsEnabled = Config.isGeneticsEnabled();
        this.analyzerBookMode = Config.isAnalyzerBookMode();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.geneticsButton = addRenderableWidget(Button.builder(geneticsButtonText(), button -> {
            geneticsEnabled = !geneticsEnabled;
            button.setMessage(geneticsButtonText());
        }).bounds(centerX - 100, 62, 200, 20).build());

        this.analyzerBookButton = addRenderableWidget(Button.builder(analyzerBookButtonText(), button -> {
            analyzerBookMode = !analyzerBookMode;
            button.setMessage(analyzerBookButtonText());
        }).bounds(centerX - 100, 122, 200, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            if (Config.setGeneticsEnabled(geneticsEnabled)
                && Config.setAnalyzerBookMode(analyzerBookMode)) {
                closeToParent();
            } else {
                statusMessage = SERVER_CONFIG_UNAVAILABLE;
            }
        }).bounds(centerX - 155, this.height - 32, 150, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> closeToParent())
            .bounds(centerX + 5, this.height - 32, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font, GENETICS_ENABLED_DESC, this.width / 2, 90, 0xA0A0A0);
        graphics.drawCenteredString(this.font, ANALYZER_BOOK_MODE_DESC, this.width / 2, 150, 0xA0A0A0);
        graphics.drawCenteredString(this.font, statusMessage, this.width / 2, 174, 0xFF5555);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private Component geneticsButtonText() {
        return GENETICS_ENABLED.copy()
            .append(": ")
            .append(Component.translatable(geneticsEnabled ? "options.on" : "options.off"));
    }

    private Component analyzerBookButtonText() {
        return ANALYZER_BOOK_MODE.copy()
            .append(": ")
            .append(Component.translatable(analyzerBookMode ? "options.on" : "options.off"));
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
