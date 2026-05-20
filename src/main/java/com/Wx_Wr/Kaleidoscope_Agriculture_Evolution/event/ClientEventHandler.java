package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.event;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer.*;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowEntity;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.item.WhipItem;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.rope.math.CatenaryMath;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "kaleidoscope_agriculture_evolution", value = Dist.CLIENT)
public class ClientEventHandler {

    private static final double RENDER_DISTANCE = 32.0;

    // 绳子条纹颜色 #9b7b59 和 #6A5332
    private static final int ROPE_COLOR_A = 0xFF9b7b59;
    private static final int ROPE_COLOR_B = 0xFF6A5332;
    private static final float STRIPE_WIDTH = 0.08f; // 每个条纹宽度

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var bufferSource = mc.renderBuffers().bufferSource();
        var camera = event.getCamera();
        var poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        renderRopes(player.level(), poseStack, bufferSource, camera, partialTick);

        renderWhipSelections(player, poseStack, bufferSource, camera);
        renderOxRelated(player, mc, poseStack, bufferSource, camera, partialTick);

        bufferSource.endBatch();
    }

    private static void renderRopes(Level level, PoseStack poseStack, MultiBufferSource bufferSource, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AABB range = mc.player.getBoundingBox().inflate(RENDER_DISTANCE);
        List<PlowEntity> plows = level.getEntitiesOfClass(PlowEntity.class, range);

        if (plows.isEmpty()) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Matrix4f mat = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (PlowEntity plow : plows) {
            if (!plow.isAlive()) continue;

            UUID oxId = plow.getOxUUID();
            if (oxId == null) continue;

            PlowOxEntity ox = findOxEntity(level, oxId);
            if (ox == null || !ox.isAlive()) continue;

            Vec3 oxLeft = ox.getLeftRopeAttachPoint(partialTick);
            Vec3 oxRight = ox.getRightRopeAttachPoint(partialTick);
            Vec3 plowLeft = plow.getLeftRopeAttachPoint(partialTick);
            Vec3 plowRight = plow.getRightRopeAttachPoint(partialTick);

            drawRopeSegment(builder, mat, cam, oxLeft, plowLeft);
            drawRopeSegment(builder, mat, cam, oxRight, plowRight);
        }

        BufferUploader.drawWithShader(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static PlowOxEntity findOxEntity(Level level, UUID uuid) {
        if (!(level instanceof ClientLevel clientLevel)) return null;
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (entity instanceof PlowOxEntity ox && ox.getUUID().equals(uuid)) {
                return ox;
            }
        }
        return null;
    }

    private static void drawRopeSegment(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                        Vec3 start, Vec3 end) {
        double dist = start.distanceTo(end);
        if (dist < 0.01) return;

        int segments = Math.max(8, (int) (dist * 8));
        segments = Math.min(segments, 64);

        Vec3[] points = CatenaryMath.computeCurvePoints(start, end, 0.12, segments);

        float width = 0.04f;

        for (int i = 0; i < points.length - 1; i++) {
            Vec3 p0 = points[i];
            Vec3 p1 = points[i + 1];

            // 根据沿绳子长度决定颜色（条纹效果）
            float t0 = (float) i / (points.length - 1);
            float t1 = (float) (i + 1) / (points.length - 1);
            int color0 = ((int) (t0 * dist / STRIPE_WIDTH) % 2 == 0) ? ROPE_COLOR_A : ROPE_COLOR_B;
            int color1 = ((int) (t1 * dist / STRIPE_WIDTH) % 2 == 0) ? ROPE_COLOR_A : ROPE_COLOR_B;

            Vec3 tangent = p1.subtract(p0).normalize();

            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = up.cross(tangent).normalize();
            if (right.lengthSqr() < 0.0001) {
                right = new Vec3(1, 0, 0).cross(tangent).normalize();
            }
            Vec3 normal = tangent.cross(right).normalize();

            // 四个角点（菱形截面）
            Vec3[] offsets = {
                normal.scale(width),           // 上
                right.scale(width),            // 右
                normal.scale(-width),          // 下
                right.scale(-width)            // 左
            };

            Vec3[] p0Corners = new Vec3[4];
            Vec3[] p1Corners = new Vec3[4];
            for (int j = 0; j < 4; j++) {
                p0Corners[j] = p0.add(offsets[j]);
                p1Corners[j] = p1.add(offsets[j]);
            }

            // 每个段画8个三角形（4个侧面，每个侧面2个三角形）
            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;

                // 侧面四边形分成两个三角形
                addTriangle(builder, mat, cam, p0Corners[j], p0Corners[next], p1Corners[next], color0, color1);
                addTriangle(builder, mat, cam, p0Corners[j], p1Corners[next], p1Corners[j], color0, color1);
            }
        }
    }

    private static void addTriangle(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                     Vec3 a, Vec3 b, Vec3 c,
                                     int colorAB, int colorC) {
        int rAB = (colorAB >> 16) & 0xFF;
        int gAB = (colorAB >> 8) & 0xFF;
        int bAB = colorAB & 0xFF;
        int rC = (colorC >> 16) & 0xFF;
        int gC = (colorC >> 8) & 0xFF;
        int bC = colorC & 0xFF;

        builder.vertex(mat, (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z))
                .color(rAB, gAB, bAB, 255)
                .endVertex();
        builder.vertex(mat, (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z))
                .color(rAB, gAB, bAB, 255)
                .endVertex();
        builder.vertex(mat, (float) (c.x - cam.x), (float) (c.y - cam.y), (float) (c.z - cam.z))
                .color(rC, gC, bC, 255)
                .endVertex();
    }

    private static void renderWhipSelections(net.minecraft.world.entity.player.Player player,
                                             PoseStack poseStack,
                                             MultiBufferSource bufferSource,
                                             Camera camera) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;

        CompoundTag tag = stack.getOrCreateTag();

        if (tag.getBoolean("editing") && tag.contains("corner1")) {
            BlockPos c1 = BlockPos.of(tag.getLong("corner1"));
            BlockPos c2 = tag.contains("corner2")
                    ? BlockPos.of(tag.getLong("corner2"))
                    : player.blockPosition();
            RangeHighlightRenderer.render(poseStack, bufferSource, camera, c1, c2);
        }

        List<BlockPos[]> ranges = WhipItem.getRanges(stack);
        for (BlockPos[] range : ranges) {
            RangeHighlightRenderer.render(poseStack, bufferSource, camera, range[0], range[1]);
        }
    }

    private static void renderOxRelated(net.minecraft.world.entity.player.Player player,
                                        Minecraft mc,
                                        PoseStack poseStack,
                                        MultiBufferSource bufferSource,
                                        Camera camera,
                                        float partialTick) {
        var entities = player.level().getEntitiesOfClass(PlowOxEntity.class,
                player.getBoundingBox().inflate(RENDER_DISTANCE));

        for (PlowOxEntity ox : entities) {
            if (ox.getOxState() == PlowOxEntity.OxState.SELECT_DIRECTION
                    && ox.getTargetCorner() != null && ox.getOtherCorner() != null) {
                renderDirectionArrows(ox, poseStack, camera);
            }

            if (ox.getOxState() == PlowOxEntity.OxState.PLOWING) {
                String pathData = ox.getPlowPathData();
                if (!pathData.isEmpty()) {
                    List<BlockPos[]> rows = PlowOxEntity.parsePlowPathData(pathData);
                    PlowPathRenderer.renderPath(poseStack, camera, rows);
                }
            }
        }
    }

    private static void renderDirectionArrows(PlowOxEntity ox,
                                              PoseStack poseStack,
                                              Camera camera) {
        BlockPos corner = ox.getTargetCorner();
        BlockPos other = ox.getOtherCorner();

        int minX = Math.min(corner.getX(), other.getX());
        int maxX = Math.max(corner.getX(), other.getX());
        int minZ = Math.min(corner.getZ(), other.getZ());
        int maxZ = Math.max(corner.getZ(), other.getZ());

        if (minX != maxX) {
            int xDir = Integer.compare(other.getX(), corner.getX());
            DirectionArrowRenderer.renderArrow(poseStack, camera,
                    corner.offset(xDir, 0, 0), true, xDir);
        }
        if (minZ != maxZ) {
            int zDir = Integer.compare(other.getZ(), corner.getZ());
            DirectionArrowRenderer.renderArrow(poseStack, camera,
                    corner.offset(0, 0, zDir), false, zDir);
        }
    }
}
