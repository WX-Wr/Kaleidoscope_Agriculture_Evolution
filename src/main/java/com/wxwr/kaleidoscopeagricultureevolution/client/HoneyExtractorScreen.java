package com.wxwr.kaleidoscopeagricultureevolution.client;

import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.client.gui.HoneyExtractorGaugeRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.network.HoneyExtractorScrollPacket;
import com.wxwr.kaleidoscopeagricultureevolution.network.HoneyExtractorStopPacket;
import com.wxwr.kaleidoscopeagricultureevolution.network.KaeNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Transparent input screen for honey extractor interaction.
 */
public final class HoneyExtractorScreen extends Screen {
    private static final int GAUGE_RENDER_SIZE = 32;
    // Tune this offset after testing the in-game position. The gauge is left of the crosshair.
    private static final int GAUGE_CENTER_X_OFFSET = -40;
    private static final int GAUGE_CENTER_Y_OFFSET = 0;
    private static final int MOUSE_MID_RENDER_SIZE = 20;
    private static final ResourceLocation MOUSE_MID_TEXTURE =
            new ResourceLocation("kaleidoscope_agriculture_evolution", "textures/gui/mouse/mouse_mid.png");
    // The gauge deliberately uses a coarser speed value than the physics simulation.
    private static final double GAUGE_OMEGA_QUANTUM = 1.0D;
    private static final float GAUGE_SMOOTH_FACTOR = 0.20F;

    private final BlockPos extractorPos;
    private long lastScrollNanos;
    private boolean closing;
    private float previousGaugeOmega;
    private float currentGaugeOmega;
    private boolean gaugeInitialized;
    private boolean gaugeEffective;
    private boolean ignoreShiftUntilRelease;

    public HoneyExtractorScreen(BlockPos extractorPos) {
        super(Component.literal("Honey Extractor"));
        this.extractorPos = extractorPos.immutable();
    }

    public BlockPos getExtractorPos() {
        return extractorPos;
    }

    @Override
    protected void init() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUse.setDown(false);

        // The Shift key used to enter the mode may still be held when this screen opens.
        // Ignore that continuous press once; a later release and press will still exit normally.
        long window = minecraft.getWindow().getWindow();
        ignoreShiftUntilRelease = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()
                || !(minecraft.level.getBlockEntity(extractorPos) instanceof HoneyExtractorBlockEntity extractor)) {
            closeFromInteraction(true);
            return;
        }

        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.input.leftImpulse = 0.0F;
        minecraft.player.input.forwardImpulse = 0.0F;
        minecraft.player.input.jumping = false;
        minecraft.player.input.shiftKeyDown = false;
        minecraft.options.keyUse.setDown(false);

        if (ignoreShiftUntilRelease) {
            long window = minecraft.getWindow().getWindow();
            boolean shiftHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (!shiftHeld) {
                ignoreShiftUntilRelease = false;
            }
        }

        updateGaugeState(extractor);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level != null
                && minecraft.level.getBlockEntity(extractorPos) instanceof HoneyExtractorBlockEntity extractor)) {
            closeFromInteraction(true);
            return true;
        }

        long now = System.nanoTime();
        double deltaSeconds = lastScrollNanos == 0L
                ? 1.0D / 20.0D
                : Math.max((now - lastScrollNanos) / 1_000_000_000.0D, 1.0D / 60.0D);
        lastScrollNanos = now;

        extractor.applyWheelInput(delta, deltaSeconds);
        // Play the main-hand swing animation only; this does not invoke item or block use.
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        KaeNetwork.CHANNEL.sendToServer(new HoneyExtractorScrollPacket(extractorPos, delta, deltaSeconds));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (ignoreShiftUntilRelease) {
                return true;
            }
            closeFromInteraction(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.level == null) {
            return;
        }
        if (!(minecraft.level.getBlockEntity(extractorPos) instanceof HoneyExtractorBlockEntity extractor)) {
            return;
        }

        if (!gaugeInitialized) {
            updateGaugeState(extractor);
        }

        // Interpolate between the values sampled on the previous and current game tick.
        float renderOmega = Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F),
                previousGaugeOmega, currentGaugeOmega);

        int centerX = width / 2 + GAUGE_CENTER_X_OFFSET;
        int centerY = height / 2 + GAUGE_CENTER_Y_OFFSET;
        HoneyExtractorGaugeRenderer.render(guiGraphics, centerX, centerY,
                GAUGE_RENDER_SIZE, renderOmega, gaugeEffective);

        int crosshairX = width / 2;
        int gaugeRight = centerX + GAUGE_RENDER_SIZE / 2;
        int mouseMidCenterX = (gaugeRight + crosshairX) / 2;
        int mouseMidLeft = mouseMidCenterX - MOUSE_MID_RENDER_SIZE / 2;
        guiGraphics.blit(MOUSE_MID_TEXTURE,
                mouseMidLeft, centerY - MOUSE_MID_RENDER_SIZE / 2,
                MOUSE_MID_RENDER_SIZE, MOUSE_MID_RENDER_SIZE,
                0, 0, MOUSE_MID_RENDER_SIZE, MOUSE_MID_RENDER_SIZE,
                MOUSE_MID_RENDER_SIZE, MOUSE_MID_RENDER_SIZE);
    }

    private void updateGaugeState(HoneyExtractorBlockEntity extractor) {
        double maxOmega = HoneyExtractorBlockEntity.getGaugeDisplayMaxOmega();
        double measuredSpeed = Math.abs(extractor.getCurrentOmega());
        double quantizedSpeed = Math.round(measuredSpeed / GAUGE_OMEGA_QUANTUM)
                * GAUGE_OMEGA_QUANTUM;
        float targetNormalizedOmega = (float) Mth.clamp(quantizedSpeed / maxOmega, 0.0D, 1.0D);

        if (!gaugeInitialized) {
            previousGaugeOmega = targetNormalizedOmega;
            currentGaugeOmega = targetNormalizedOmega;
            gaugeInitialized = true;
        } else {
            previousGaugeOmega = currentGaugeOmega;
            currentGaugeOmega = Mth.lerp(GAUGE_SMOOTH_FACTOR,
                    currentGaugeOmega, targetNormalizedOmega);
        }
        gaugeEffective = quantizedSpeed >= HoneyExtractorBlockEntity.getMinHoneyProducingOmega();
    }

    public void closeFromInteraction(boolean notifyServer) {
        if (closing) {
            return;
        }
        closing = true;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUse.setDown(false);
        if (notifyServer) {
            KaeNetwork.CHANNEL.sendToServer(new HoneyExtractorStopPacket(extractorPos));
        }
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        closeFromInteraction(true);
    }
}
