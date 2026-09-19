package com.wxwr.kaleidoscopeagricultureevolution.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Renders the honey extractor speed gauge. */
public final class HoneyExtractorGaugeRenderer {

    private static final ResourceLocation GAUGE =
            new ResourceLocation("kaleidoscope_agriculture_evolution", "textures/gui/gauge/gauge.png");
    private static final ResourceLocation PIE_RED =
            new ResourceLocation("kaleidoscope_agriculture_evolution", "textures/gui/gauge/pie_red.png");
    private static final ResourceLocation PIE_GREEN =
            new ResourceLocation("kaleidoscope_agriculture_evolution", "textures/gui/gauge/pie_green.png");
    private static final ResourceLocation NEEDLE =
            new ResourceLocation("kaleidoscope_agriculture_evolution", "textures/gui/gauge/needle.png");

    private static final int TEXTURE_SIZE = 32;
    private static final int PIE_Z = -1;
    private static final float SWEEP_START = 0.25F;
    /** All three gauge textures are 32x32 and must be rendered at the same size. */
    private static final float GAUGE_OVERLAY_SCALE = 1.00F;

    private HoneyExtractorGaugeRenderer() {
    }

    public static void render(GuiGraphics graphics, int centerX, int centerY,
                              int renderSize, float normalizedOmega, boolean effective) {
        float progress = Mth.clamp(normalizedOmega, 0.0F, 1.0F);

        // pie_red/pie_green are complete recolored gauge images, including the outer frame.
        // Draw the gray gauge first, then use a clipped copy of the recolored gauge as an overlay.
        blitCentered(graphics, GAUGE, centerX, centerY, renderSize);
        if (progress > 0.0F) {
            int overlaySize = Math.round(renderSize * GAUGE_OVERLAY_SCALE);
            drawGaugeOverlaySector(graphics, centerX, centerY, overlaySize, progress,
                    effective ? PIE_GREEN : PIE_RED);
        }
        drawNeedle(graphics, centerX, centerY, renderSize, progress);
    }

    private static void blitCentered(GuiGraphics graphics, ResourceLocation texture,
                                     int centerX, int centerY, int renderSize) {
        int halfSize = renderSize / 2;
        graphics.blit(texture,
                centerX - halfSize, centerY - halfSize,
                renderSize, renderSize,
                0, 0, TEXTURE_SIZE, TEXTURE_SIZE,
                TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static void drawNeedle(GuiGraphics graphics, int centerX, int centerY,
                                   int renderSize, float normalizedOmega) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        // The texture points up by default. At 0.0 it points down; positive angles turn clockwise on screen.
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0F + normalizedOmega * 360.0F));
        graphics.blit(NEEDLE,
                -renderSize / 2, -renderSize / 2,
                renderSize, renderSize,
                0, 0, TEXTURE_SIZE, TEXTURE_SIZE,
                TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.pose().popPose();
    }

    private static void drawGaugeOverlaySector(GuiGraphics graphics, int centerX, int centerY,
                                                int renderSize, float normalizedEnd,
                                                ResourceLocation recoloredGauge) {
        // 0.25 means the sweep starts at 6 o'clock. In GUI coordinates, increasing angles move clockwise.
        float start = SWEEP_START;
        float end = start + Mth.clamp(normalizedEnd, 0.0F, 1.0F);
        /*
         * textureRadius is measured in texture space where the center is 0.5 and
         * the edge is 0.5 away from the center. Therefore the screen multiplier
         * must be renderSize, not renderSize / 2: at the horizontal edge,
         * renderSize * 0.5 reaches exactly half of the rendered texture size.
         */
        float radius = renderSize;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, recoloredGauge);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, centerX, centerY, PIE_Z)
                .uv(0.5F, 0.5F)
                .endVertex();

        for (int i = Mth.ceil(end * 4.0F + 0.5F);
             i >= Mth.floor(start * 4.0F + 0.5F);
             i--) {
            double angle = Mth.clamp(i * 0.25F - 0.125F, start, end) * 2.0D * Math.PI;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float textureRadius = Math.abs(cos) > Math.abs(sin)
                    ? Math.abs(0.5F / cos)
                    : Math.abs(0.5F / sin);

            builder.vertex(matrix,
                            centerX + radius * textureRadius * cos,
                            centerY + radius * textureRadius * sin,
                            PIE_Z)
                    .uv(0.5F + textureRadius * cos,
                            0.5F + textureRadius * sin)
                    .endVertex();
        }

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }
}
