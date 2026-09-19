package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.client.gui.RegionModeOverlay;
import com.wxwr.kaleidoscopeagricultureevolution.client.HoneyExtractorClientInteraction;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.*;
import com.wxwr.kaleidoscopeagricultureevolution.entity.AbstractDraggableEntity;
import com.wxwr.kaleidoscopeagricultureevolution.rope.math.CatenaryMath;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowPathData;
import com.wxwr.kaleidoscopeagricultureevolution.network.HoneyExtractorScrollPacket;
import com.wxwr.kaleidoscopeagricultureevolution.item.WhipItem;
import com.wxwr.kaleidoscopeagricultureevolution.network.KaeNetwork;
import com.wxwr.kaleidoscopeagricultureevolution.network.ModeScrollPacket;
import com.wxwr.kaleidoscopeagricultureevolution.network.WhipMenuPacket;
import com.wxwr.kaleidoscopeagricultureevolution.region.*;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = KaleidoscopeAgricultureEvolution.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    private static final double RENDER_DISTANCE = 32.0;

    // 绳子条纹颜色
    private static final int ROPE_COLOR_A = 0xFF9b7b59;
    private static final int ROPE_COLOR_B = 0xFF6A5332;
    private static final float STRIPE_WIDTH = 0.08f;

    /** 追踪当前帧中是否已渲染模式叠加层。 */
    private static boolean overlayRenderedThisFrame = false;

    // ========================================================================
    //  Outliner 槽位键（不透明对象，每个独立的轮廓线一个键）
    // ========================================================================

    /** 使用鞭子编辑时的预览 AABB。 */
    private static final Object SLOT_PREVIEW = new Object();
    /** 方向选择时的工作区域边框（一次一头耕牛）。 */
    private static final Object SLOT_WORK_AREA = new Object();
    /** 已保存区域槽位的前缀：{@code "saved:" + index}. */
    private static final String SAVED_PREFIX = "saved:";

    /** 上一次的选择状态；用于检测进入/退出编辑状态的转换。 */
    private static RegionMode.SelectState lastSelectState = RegionMode.SelectState.IDLE;
    private static final Map<UUID, CachedPathData> PLOW_PATH_CACHE = new HashMap<>();
    private static long lastHoneyExtractorScrollNanos;

    // ========================================================================
    //  鼠标滚轮 — 模式切换
    // ========================================================================

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        if (HoneyExtractorClientInteraction.isActive()) {
            // 摇蜜 Screen 会在 mouseScrolled() 中处理滚轮，避免这里重复发送数据包。
            return;
        }
        if (tryScrollHoneyExtractor(mc, event)) return;
        if (!player.isShiftKeyDown()) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;

        RegionMode.SelectState state = WhipItem.getSelectState(stack);
        if (state != RegionMode.SelectState.IDLE) return;

        boolean forward = event.getScrollDelta() < 0;
        KaeNetwork.CHANNEL.sendToServer(new ModeScrollPacket(forward));

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        if (HoneyExtractorClientInteraction.isActive()) return;
        if (!player.isShiftKeyDown()) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;
        if (WhipItem.getSelectState(stack) != RegionMode.SelectState.IDLE) return;
        if (!WhipItem.canOpenWorkZoneMenu(stack)) return;

        KaeNetwork.CHANNEL.sendToServer(new WhipMenuPacket());
        event.setCanceled(true);
    }

    private static boolean tryScrollHoneyExtractor(Minecraft mc, InputEvent.MouseScrollingEvent event) {
        if (!HoneyExtractorClientInteraction.isActive()) {
            lastHoneyExtractorScrollNanos = 0L;
            return false;
        }
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || !HoneyExtractorClientInteraction.isActiveAt(hit.getBlockPos())) {
            event.setCanceled(true);
            return true;
        }
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof HoneyExtractorBlockEntity extractor)) {
            event.setCanceled(true);
            return true;
        }
        long now = System.nanoTime();
        double deltaSeconds = lastHoneyExtractorScrollNanos == 0L
                ? 1.0D / 20.0D
                : Math.max((now - lastHoneyExtractorScrollNanos) / 1_000_000_000.0D, 1.0D / 60.0D);
        lastHoneyExtractorScrollNanos = now;

        double scrollDelta = event.getScrollDelta();
        extractor.applyWheelInput(scrollDelta, deltaSeconds);
        KaeNetwork.CHANNEL.sendToServer(new HoneyExtractorScrollPacket(hit.getBlockPos(), scrollDelta, deltaSeconds));
        event.setCanceled(true);
        return true;
    }

    // ========================================================================
    //  HUD 叠加层 — 模式列表
    // ========================================================================

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (overlayRenderedThisFrame) return;
        overlayRenderedThisFrame = true;
        RegionModeOverlay.render(event.getGuiGraphics(), event.getPartialTick());
    }

    // ========================================================================
    //  世界渲染 — 刻更新 + 渲染轮廓线（可变 fps）
    // ========================================================================

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        overlayRenderedThisFrame = false;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var bufferSource = mc.renderBuffers().bufferSource();
        var camera = event.getCamera();
        var poseStack = event.getPoseStack();
        float partialTick = event.getPartialTick();

        Outliner outliner = Outliner.getInstance();

        // 1. 推送目标 + 推进追踪（针对渲染帧率缩放）
        // saved 必须先于 work-area：work-area 会清除与之重叠的 saved slot
        tickPreviewOutline(player, outliner);
        tickSavedRegionOutlines(player, outliner);
        tickWorkAreaOutline(player, outliner);
        outliner.tickOutlines();

        // 2. 渲染绳子
        renderRopes(player.level(), poseStack, bufferSource, camera, partialTick);

        // 3. 渲染轮廓线（通过 partialTick 进行帧插值）
        outliner.renderOutlines(poseStack, camera, partialTick);

        // 4. 渲染箭头 + 路径信息
        renderOxRelated(player, mc, poseStack, bufferSource, camera, partialTick);

        bufferSource.endBatch();
    }

    // ========================================================================
    //  预览轮廓线 tick — 鞭子编辑 AABB
    // ========================================================================

    /**
     * 将鞭子当前的编辑 AABB 推送到预览槽位。
     *
     * <p>每帧调用。当玩家进入编辑模式时，AABB 会立即定位
     * （通过 {@link Outliner#showAABB}），使其立刻出现。
     * 后续帧调用 {@link Outliner#chaseAABB} 平滑追踪移动中的目标。
     */
    private static void tickPreviewOutline(net.minecraft.world.entity.player.Player player,
                                           Outliner outliner) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) {
            // 未手持鞭子 → 让槽位淡出
            outliner.chaseAABB(SLOT_PREVIEW, null, OutlineStyle.PREVIEW);
            lastSelectState = RegionMode.SelectState.IDLE;
            return;
        }

        // 只读：绝不创建空 tag
        CompoundTag tag = stack.getTag();
        RegionMode.SelectState state = WhipItem.getSelectState(stack);

        AABB target = computeTargetAABB(player, tag, state);

        if (target != null && state != RegionMode.SelectState.IDLE) {
            if (lastSelectState == RegionMode.SelectState.IDLE) {
                // 进入编辑 — 立即定位，无追踪动画
                outliner.showAABB(SLOT_PREVIEW, target, OutlineStyle.PREVIEW);
            } else {
                // 后续帧 — 平滑追踪
                outliner.chaseAABB(SLOT_PREVIEW, target, OutlineStyle.PREVIEW);
            }
        } else {
            // 未在编辑 → 让槽位淡出
            outliner.chaseAABB(SLOT_PREVIEW, null, OutlineStyle.PREVIEW);
        }

        lastSelectState = state;
    }

    // ========================================================================
    //  工作区域轮廓线 tick — 耕牛方向选择 AABB
    // ========================================================================

    private static void tickWorkAreaOutline(net.minecraft.world.entity.player.Player player,
                                            Outliner outliner) {
        boolean holdingWhip = player.getMainHandItem().getItem() instanceof WhipItem;

        var entities = player.level().getEntitiesOfClass(PlowOxEntity.class,
                player.getBoundingBox().inflate(RENDER_DISTANCE));

        PlowOxEntity found = null;
        for (PlowOxEntity ox : entities) {
            if ((ox.getOxState() == PlowOxEntity.OxState.SELECT_CORNER
                    || ox.getOxState() == PlowOxEntity.OxState.SELECT_DIRECTION)
                    && ox.getTargetCorner() != null && ox.getOtherCorner() != null) {
                found = ox;
                break;
            }
        }

        if (found != null && holdingWhip) {
            // 仅在手持鞭子时显示工作区域边界框
            BlockPos c1 = found.getTargetCorner();
            BlockPos c2 = found.getOtherCorner();
            AABB bb = cornersToAABB(c1, c2);
            outliner.chaseAABB(SLOT_WORK_AREA, bb, OutlineStyle.WORK_AREA);

            // 清除与 work area 完全重叠的 saved slot，防止双重渲染
            List<Region> regions = WhipItem.getRegions(player.getMainHandItem());
            for (int i = 0; i < regions.size(); i++) {
                AABB savedBB = regionToAABB(regions.get(i));
                if (bb.equals(savedBB)) {
                    outliner.remove(SAVED_PREFIX + i);
                }
            }
        } else {
            outliner.chaseAABB(SLOT_WORK_AREA, null, OutlineStyle.WORK_AREA);
        }
    }

    // ========================================================================
    //  已保存区域轮廓线 tick
    // ========================================================================

    private static void tickSavedRegionOutlines(net.minecraft.world.entity.player.Player player,
                                                Outliner outliner) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) {
            // 未手持鞭子时清除所有已保存区域槽位
            // （它们会通过 Outliner 自然淡出）
            List<Region> oldRegions = WhipItem.getRegions(stack);
            // 实际上我们无法在没有鞭子的情况下访问区域。只需保留上次推送的内容
            // — 如果不刷新它们会自然淡出。
            return;
        }

        List<Region> regions = WhipItem.getRegions(stack);
        // 刷新现有的已保存槽位
        int i = 0;
        for (; i < regions.size(); i++) {
            Region r = regions.get(i);
            String key = SAVED_PREFIX + i;
            AABB bb = regionToAABB(r);
            outliner.chaseAABB(key, bb, OutlineStyle.SAVED);
        }
        // 淡出之前较大区域列表中多余的槽位
        String key = SAVED_PREFIX + i;
        outliner.chaseAABB(key, null, OutlineStyle.SAVED); // 消除槽位 i
        // （如果存在超过 i+1 个槽位，它们会自然淡出，因为没有被刷新。
        //  我们只需要显式消除第一个超出范围的槽位来启动级联。）
    }

    // ========================================================================
    //  AABB 辅助方法
    // ========================================================================

    private static AABB computeTargetAABB(net.minecraft.world.entity.player.Player player,
                                          @Nullable CompoundTag tag,
                                          RegionMode.SelectState state) {
        if (state == RegionMode.SelectState.IDLE) return null;
        if (tag == null || !tag.contains("corner1")) return null;

        BlockPos c1 = BlockPos.of(tag.getLong("corner1"));
        BlockPos c2;
        if (state == RegionMode.SelectState.PREVIEW && tag.contains("corner2")) {
            c2 = BlockPos.of(tag.getLong("corner2"));
        } else {
            c2 = player.blockPosition();
        }

        return cornersToAABB(c1, c2);
    }

    /** 将两个角 BlockPos 转换为覆盖完整方块的 AABB。 */
    private static AABB cornersToAABB(BlockPos c1, BlockPos c2) {
        return new AABB(
                Math.min(c1.getX(), c2.getX()),
                Math.min(c1.getY(), c2.getY()),
                Math.min(c1.getZ(), c2.getZ()),
                Math.max(c1.getX(), c2.getX()) + 1,
                Math.max(c1.getY(), c2.getY()) + 1,
                Math.max(c1.getZ(), c2.getZ()) + 1
        );
    }

    /** 将任意区域转换为其包围 AABB。 */
    private static AABB regionToAABB(Region region) {
        if (region instanceof CuboidRegion cr) {
            return cornersToAABB(cr.getMinimumPoint(), cr.getMaximumPoint());
        }
        // 通用区域的回退方案
        return cornersToAABB(region.getMinimumPoint(), region.getMaximumPoint());
    }

    // ========================================================================
    //  绳子渲染（未修改）
    // ========================================================================

    private static void renderRopes(Level level, PoseStack poseStack, MultiBufferSource bufferSource,
                                     Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AABB range = mc.player.getBoundingBox().inflate(RENDER_DISTANCE);
        List<AbstractDraggableEntity> tools = level.getEntitiesOfClass(AbstractDraggableEntity.class, range);
        if (tools.isEmpty()) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Matrix4f mat = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (AbstractDraggableEntity tool : tools) {
            if (!tool.isAlive()) continue;
            if (!tool.shouldRenderRope()) continue;
            UUID oxId = tool.getOxUUID();
            if (oxId == null) continue;
            PlowOxEntity ox = findOxEntity(level, oxId);
            if (ox == null || !ox.isAlive()) continue;

            Vec3 oxLeft = ox.getLeftRopeAttachPoint(partialTick);
            Vec3 oxRight = ox.getRightRopeAttachPoint(partialTick);
            Vec3 toolLeft = tool.getLeftRopeAttachPoint(partialTick);
            Vec3 toolRight = tool.getRightRopeAttachPoint(partialTick);

            drawRopeSegment(builder, mat, cam, oxLeft, toolLeft);
            drawRopeSegment(builder, mat, cam, oxRight, toolRight);
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

            Vec3[] offsets = {
                    normal.scale(width),
                    right.scale(width),
                    normal.scale(-width),
                    right.scale(-width)
            };

            Vec3[] p0Corners = new Vec3[4];
            Vec3[] p1Corners = new Vec3[4];
            for (int j = 0; j < 4; j++) {
                p0Corners[j] = p0.add(offsets[j]);
                p1Corners[j] = p1.add(offsets[j]);
            }

            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;
                addTriangle(builder, mat, cam, p0Corners[j], p0Corners[next], p1Corners[next], color0, color1);
                addTriangle(builder, mat, cam, p0Corners[j], p1Corners[next], p1Corners[j], color0, color1);
            }
        }
    }

    private static void addTriangle(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                     Vec3 a, Vec3 b, Vec3 c, int colorAB, int colorC) {
        int rAB = (colorAB >> 16) & 0xFF, gAB = (colorAB >> 8) & 0xFF, bAB = colorAB & 0xFF;
        int rC  = (colorC  >> 16) & 0xFF, gC  = (colorC  >> 8) & 0xFF, bC  = colorC  & 0xFF;

        builder.vertex(mat, (float)(a.x - cam.x), (float)(a.y - cam.y), (float)(a.z - cam.z))
                .color(rAB, gAB, bAB, 255).endVertex();
        builder.vertex(mat, (float)(b.x - cam.x), (float)(b.y - cam.y), (float)(b.z - cam.z))
                .color(rAB, gAB, bAB, 255).endVertex();
        builder.vertex(mat, (float)(c.x - cam.x), (float)(c.y - cam.y), (float)(c.z - cam.z))
                .color(rC, gC, bC, 255).endVertex();
    }

    // ========================================================================
    //  耕牛相关渲染（WORKING 状态下的箭头 + 路径）
    // ========================================================================

    private static void renderOxRelated(net.minecraft.world.entity.player.Player player,
                                         Minecraft mc, PoseStack poseStack,
                                         MultiBufferSource bufferSource, Camera camera,
                                         float partialTick) {
        var entities = player.level().getEntitiesOfClass(PlowOxEntity.class,
                player.getBoundingBox().inflate(RENDER_DISTANCE));

        boolean holdingWhip = player.getMainHandItem().getItem() instanceof WhipItem;
        if (PLOW_PATH_CACHE.size() > 128) {
            PLOW_PATH_CACHE.clear();
        }

        for (PlowOxEntity ox : entities) {
            if (holdingWhip
                    && (ox.getOxState() == PlowOxEntity.OxState.SELECT_CORNER
                    || ox.getOxState() == PlowOxEntity.OxState.SELECT_DIRECTION)
                    && ox.getTargetCorner() != null && ox.getOtherCorner() != null) {
                renderDirectionArrows(ox, poseStack, camera);
            }

            if (ox.getOxState() == PlowOxEntity.OxState.WORKING
                    && player.getMainHandItem().getItem() instanceof WhipItem) {
                String pathData = ox.getPlowPathData();
                if (!pathData.isEmpty()) {
                    List<BlockPos> points = getCachedPathPoints(ox, pathData);
                    PlowPathRenderer.renderPath(poseStack, camera, points);
                    if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
                        PlowPathRenderer.renderRemainingCount(poseStack, camera, bs, ox, points.size());
                    }
                } else {
                    PLOW_PATH_CACHE.remove(ox.getUUID());
                }
            } else {
                PLOW_PATH_CACHE.remove(ox.getUUID());
            }
        }
    }

    private static List<BlockPos> getCachedPathPoints(PlowOxEntity ox, String pathData) {
        UUID id = ox.getUUID();
        CachedPathData cached = PLOW_PATH_CACHE.get(id);
        if (cached != null && cached.raw.equals(pathData)) {
            return cached.points;
        }
        List<BlockPos> points = PlowPathData.parse(pathData);
        PLOW_PATH_CACHE.put(id, new CachedPathData(pathData, points));
        return points;
    }

    private record CachedPathData(String raw, List<BlockPos> points) {}

    private static void renderDirectionArrows(PlowOxEntity ox, PoseStack poseStack, Camera camera) {
        BlockPos corner = ox.getTargetCorner();
        BlockPos other = ox.getOtherCorner();

        CuboidRegion region = new CuboidRegion(corner, other);

        if (region.getMinimumPoint().getX() != region.getMaximumPoint().getX()) {
            int xDir = Integer.compare(other.getX(), corner.getX());
            DirectionArrowRenderer.renderArrow(poseStack, camera, corner.offset(xDir, 0, 0), true, xDir);
        }
        if (region.getMinimumPoint().getZ() != region.getMaximumPoint().getZ()) {
            int zDir = Integer.compare(other.getZ(), corner.getZ());
            DirectionArrowRenderer.renderArrow(poseStack, camera, corner.offset(0, 0, zDir), false, zDir);
        }
    }
}
