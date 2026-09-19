package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * 底层轮廓线渲染原语。
 *
 * <p>受 Create 模组的 {@code AABBOutline} 和 {@code Outline} 基类启发。
 * 相比原版 {@code DEBUG_LINES} 的关键改进是，边框被渲染为
 * <b>细长的轴对齐立方体</b>，而非 1px 的 GL 线段。
 * 这保证了无论 GPU/驱动程序对线宽的支持如何，以及无论观看距离多远，
 * 都能获得一致的视觉宽度。
 *
 * <h3>用法</h3>
 * <pre>{@code
 *   OutlineStyle style = OutlineStyle.PREVIEW;
 *   OutlineRenderer.renderCuboid(poseStack, camera, c1, c2, style);
 * }</pre>
 */
public final class OutlineRenderer {

    private OutlineRenderer() {}

    // ========================================================================
    //  公共 API
    // ========================================================================

    /**
     * 渲染完整的立方体轮廓线（边框线框 + 可选的半透明面）。
     */
    public static void renderCuboid(PoseStack poseStack, Camera camera,
                                    BlockPos c1, BlockPos c2,
                                    OutlineStyle style) {
        if (c1 == null || c2 == null) return;
        renderCuboidFloat(poseStack, camera,
                Math.min(c1.getX(), c2.getX()),
                Math.min(c1.getY(), c2.getY()),
                Math.min(c1.getZ(), c2.getZ()),
                Math.max(c1.getX(), c2.getX()) + 1f,
                Math.max(c1.getY(), c2.getY()) + 1f,
                Math.max(c1.getZ(), c2.getZ()) + 1f,
                style);
    }

    /**
     * 从浮点数 AABB 坐标渲染完整的立方体轮廓线。
     *
     * <p>此变体支持亚方块精度 — 对于 AABB 以分数块移动的平滑追踪动画
     * 来说至关重要。
     */
    public static void renderCuboidFloat(PoseStack poseStack, Camera camera,
                                          float minX, float minY, float minZ,
                                          float maxX, float maxY, float maxZ,
                                          OutlineStyle style) {
        Vec3 cam = camera.getPosition();
        Matrix4f mat = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 强制正确的剔除状态（之前的渲染如箭头可能已禁用它，
        // 导致双面渲染 → 更亮的画面）
        if (style.disableCull()) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }

        // 1. 半透明面（在边框后面，不进行深度写入以避免遮挡
        //    其他世界内叠加层，如方向箭头）
        if (style.faceAlpha() > 0f) {
            RenderSystem.depthMask(false);
            renderCuboidFaces(mat, cam, minX, minY, minZ, maxX, maxY, maxZ,
                    style.red(), style.green(), style.blue(),
                    style.faceAlpha(), style.highlightedFace());
            RenderSystem.depthMask(true);
        }

        // 2. 实心边框
        if (style.lineWidth() > 0f) {
            renderCuboidEdges(mat, cam, minX, minY, minZ, maxX, maxY, maxZ,
                    style.red(), style.green(), style.blue(), style.alpha(),
                    style.lineWidth());
        }

        RenderSystem.disableBlend();
    }

    /**
     * 仅渲染立方体的 12 条边框为细长立方体（无面填充）。
     */
    public static void renderCuboidEdges(Matrix4f mat, Vec3 cam,
                                          float minX, float minY, float minZ,
                                          float maxX, float maxY, float maxZ,
                                          float r, float g, float b, float a,
                                          float lineWidth) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        float lineLengthX = maxX - minX;
        float lineLengthY = maxY - minY;
        float lineLengthZ = maxZ - minZ;
        float hw = lineWidth * 0.5f;

        // 12 条边框：从 8 个角中的每一个出发，3 条边框沿正轴方向延伸。
        // 我们只需要每条边发射一次，因此选取 12 条唯一的边。

        // 底面边框 (y = minY)
        bufferCuboidLine(builder, mat, cam, minX, minY, minZ, Direction.EAST,  lineLengthX, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, maxX, minY, minZ, Direction.SOUTH, lineLengthZ, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, maxX, minY, maxZ, Direction.WEST,  lineLengthX, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, minX, minY, maxZ, Direction.NORTH, lineLengthZ, hw, r, g, b, a);

        // 顶面边框 (y = maxY)
        bufferCuboidLine(builder, mat, cam, minX, maxY, minZ, Direction.EAST,  lineLengthX, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, maxX, maxY, minZ, Direction.SOUTH, lineLengthZ, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, maxX, maxY, maxZ, Direction.WEST,  lineLengthX, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, minX, maxY, maxZ, Direction.NORTH, lineLengthZ, hw, r, g, b, a);

        // 垂直边框
        bufferCuboidLine(builder, mat, cam, minX, minY, minZ, Direction.UP, lineLengthY, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, maxX, minY, minZ, Direction.UP, lineLengthY, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, maxX, minY, maxZ, Direction.UP, lineLengthY, hw, r, g, b, a);
        bufferCuboidLine(builder, mat, cam, minX, minY, maxZ, Direction.UP, lineLengthY, hw, r, g, b, a);

        BufferUploader.drawWithShader(builder.end());
    }

    /**
     * 渲染立方体的六个半透明面。
     *
     * <p>{@code highlightedFace}（如有）以双倍透明度渲染；
     * 所有其他面使用基础 {@code alpha} 值。
     */
    public static void renderCuboidFaces(Matrix4f mat, Vec3 cam,
                                          float minX, float minY, float minZ,
                                          float maxX, float maxY, float maxZ,
                                          float r, float g, float b, float alpha,
                                          @Nullable Direction highlightedFace) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Direction face : Direction.values()) {
            // 跳过底面 — 它在地下，永远不可见
            if (face == Direction.DOWN) continue;

            boolean highlighted = face == highlightedFace;
            float faceAlpha = highlighted ? Math.min(1f, alpha * 2f) : alpha;

            emitFaceQuad(builder, mat, cam, minX, minY, minZ, maxX, maxY, maxZ,
                    face, r, g, b, faceAlpha);
        }

        BufferUploader.drawWithShader(builder.end());
    }

    // ========================================================================
    //  边框渲染 — 细长立方体线段
    // ========================================================================

    /**
     * 发射一个代表单条边框的细长轴对齐立方体。
     *
     * <p>该立方体具有边长为 {@code lineWidth} 的正方形截面，
     * 在给定的 {@code direction} 方向上延伸 {@code length} 个方块。
     * 它以数学边框为中心，使厚度的一半位于每侧。
     *
     * @param builder   目标顶点缓冲区（必须处于 {@code TRIANGLES} 模式）
     * @param origin    世界空间中的起始角
     * @param direction 边框延伸的轴线（必须是基本方向）
     * @param length    以方块为单位的边框长度
     * @param halfWidth 期望线宽的一半
     */
    private static void bufferCuboidLine(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                         float ox, float oy, float oz,
                                         Direction direction, float length,
                                         float halfWidth,
                                         float r, float g, float b, float a) {
        // 截面的垂直轴
        float p1x, p1y, p1z; // 第一垂直方向
        float p2x, p2y, p2z; // 第二垂直方向

        switch (direction) {
            case EAST, WEST -> {
                p1x = 0; p1y = 1; p1z = 0;   // Y 轴
                p2x = 0; p2y = 0; p2z = 1;   // Z 轴
            }
            case UP, DOWN -> {
                p1x = 1; p1y = 0; p1z = 0;   // X 轴
                p2x = 0; p2y = 0; p2z = 1;   // Z 轴
            }
            default -> { // NORTH, SOUTH
                p1x = 1; p1y = 0; p1z = 0;   // X 轴
                p2x = 0; p2y = 1; p2z = 0;   // Y 轴
            }
        }

        float dx = direction.getStepX() * length;
        float dy = direction.getStepY() * length;
        float dz = direction.getStepZ() * length;

        float hw = halfWidth;

        // 立方体的 8 个角
        // v0..v3 = 近端面（原点处），v4..v7 = 远端面（原点 + dir*length）
        float v0x = ox - p1x * hw - p2x * hw;
        float v0y = oy - p1y * hw - p2y * hw;
        float v0z = oz - p1z * hw - p2z * hw;

        float v1x = ox + p1x * hw - p2x * hw;
        float v1y = oy + p1y * hw - p2y * hw;
        float v1z = oz + p1z * hw - p2z * hw;

        float v2x = ox + p1x * hw + p2x * hw;
        float v2y = oy + p1y * hw + p2y * hw;
        float v2z = oz + p1z * hw + p2z * hw;

        float v3x = ox - p1x * hw + p2x * hw;
        float v3y = oy - p1y * hw + p2y * hw;
        float v3z = oz - p1z * hw + p2z * hw;

        float v4x = v0x + dx; float v4y = v0y + dy; float v4z = v0z + dz;
        float v5x = v1x + dx; float v5y = v1y + dy; float v5z = v1z + dz;
        float v6x = v2x + dx; float v6y = v2y + dy; float v6z = v2z + dz;
        float v7x = v3x + dx; float v7y = v3y + dy; float v7z = v3z + dz;

        // 6 个面 × 2 个三角形 = 12 个三角形 = 36 个顶点
        // 面 0：近端 (-dir) — v3, v2, v1, v0（面向 -dir 逆时针）
        emitTriangle(builder, mat, cam, v3x, v3y, v3z, v2x, v2y, v2z, v1x, v1y, v1z, r, g, b, a);
        emitTriangle(builder, mat, cam, v3x, v3y, v3z, v1x, v1y, v1z, v0x, v0y, v0z, r, g, b, a);

        // 面 1：远端 (+dir) — v4, v5, v6, v7
        emitTriangle(builder, mat, cam, v4x, v4y, v4z, v5x, v5y, v5z, v6x, v6y, v6z, r, g, b, a);
        emitTriangle(builder, mat, cam, v4x, v4y, v4z, v6x, v6y, v6z, v7x, v7y, v7z, r, g, b, a);

        // 面 2：-p2 侧 — v0, v1, v5, v4
        emitTriangle(builder, mat, cam, v0x, v0y, v0z, v1x, v1y, v1z, v5x, v5y, v5z, r, g, b, a);
        emitTriangle(builder, mat, cam, v0x, v0y, v0z, v5x, v5y, v5z, v4x, v4y, v4z, r, g, b, a);

        // 面 3：+p2 侧 — v2, v3, v7, v6
        emitTriangle(builder, mat, cam, v2x, v2y, v2z, v3x, v3y, v3z, v7x, v7y, v7z, r, g, b, a);
        emitTriangle(builder, mat, cam, v2x, v2y, v2z, v7x, v7y, v7z, v6x, v6y, v6z, r, g, b, a);

        // 面 4：-p1 侧 — v0, v4, v7, v3
        emitTriangle(builder, mat, cam, v0x, v0y, v0z, v4x, v4y, v4z, v7x, v7y, v7z, r, g, b, a);
        emitTriangle(builder, mat, cam, v0x, v0y, v0z, v7x, v7y, v7z, v3x, v3y, v3z, r, g, b, a);

        // 面 5：+p1 侧 — v1, v2, v6, v5
        emitTriangle(builder, mat, cam, v1x, v1y, v1z, v2x, v2y, v2z, v6x, v6y, v6z, r, g, b, a);
        emitTriangle(builder, mat, cam, v1x, v1y, v1z, v6x, v6y, v6z, v5x, v5y, v5z, r, g, b, a);
    }

    // ========================================================================
    //  顶点 / 三角形辅助方法
    // ========================================================================

    /** 在摄像机相对坐标中发射单个顶点。 */
    private static void emitVertex(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                   float x, float y, float z,
                                   float r, float g, float b, float a) {
        builder.vertex(mat, x - (float) cam.x, y - (float) cam.y, z - (float) cam.z)
                .color(r, g, b, a).endVertex();
    }

    /** 发射构成一个三角形的三个顶点。 */
    private static void emitTriangle(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                     float x0, float y0, float z0,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float r, float g, float b, float a) {
        emitVertex(builder, mat, cam, x0, y0, z0, r, g, b, a);
        emitVertex(builder, mat, cam, x1, y1, z1, r, g, b, a);
        emitVertex(builder, mat, cam, x2, y2, z2, r, g, b, a);
    }

    /** 发射立方体的单个四边形面。 */
    private static void emitFaceQuad(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ,
                                     Direction face,
                                     float r, float g, float b, float a) {
        switch (face) {
            case DOWN -> {
                emitVertex(builder, mat, cam, minX, minY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, minY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, minY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, minY, minZ, r, g, b, a);
            }
            case UP -> {
                emitVertex(builder, mat, cam, minX, maxY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, maxY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, maxY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, maxY, maxZ, r, g, b, a);
            }
            case NORTH -> {
                emitVertex(builder, mat, cam, maxX, maxY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, minY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, minY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, maxY, minZ, r, g, b, a);
            }
            case SOUTH -> {
                emitVertex(builder, mat, cam, minX, maxY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, minY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, minY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, maxY, maxZ, r, g, b, a);
            }
            case WEST -> {
                emitVertex(builder, mat, cam, minX, maxY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, minY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, minY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, minX, maxY, maxZ, r, g, b, a);
            }
            case EAST -> {
                emitVertex(builder, mat, cam, maxX, maxY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, minY, maxZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, minY, minZ, r, g, b, a);
                emitVertex(builder, mat, cam, maxX, maxY, minZ, r, g, b, a);
            }
        }
    }

    // ========================================================================
    //  方块精度的圆形 / 椭圆渲染
    //
    //  我们绘制实际的方块边界 — "切掉四角的方形"（阶梯状轮廓线），
    //  而非平滑的数学曲线。这让玩家能精确看到圆形区域中包含哪些方块。
    // ========================================================================

    /**
     * 渲染方块精度的椭圆轮廓线。
     *
     * <p>顶面使用每行连续选中方块的一个水平四边形填充（半透明）。
     * 方块边界边框在内部方块与外部邻居相交处绘制为细长立方体。
     *
     * <p><b>状态：待定。</b>目前<b>没有调用点</b>——圆形编辑模式处于禁用状态
     * （{@code RegionMode.CIRCLE} 的 {@code scrollSelectable = false}）。圆形区域的几何、
     * 序列化与语言键都还保留着，本方法连同其私有辅助 {@code circleContains} /
     * {@code drawBlockFaceEdges} 只服务于圆形路径，请勿删除。
     */
    public static void renderCircleOutline(PoseStack poseStack, Camera camera,
                                           BlockPos center, double radiusX, double radiusZ,
                                           int minY, int maxY,
                                           OutlineStyle style) {
        Vec3 cam = camera.getPosition();
        Matrix4f mat = poseStack.last().pose();

        double cx = center.getX();
        double cz = center.getZ();
        int iMinX = (int) Math.floor(cx - radiusX);
        int iMaxX = (int) Math.ceil(cx + radiusX);
        int iMinZ = (int) Math.floor(cz - radiusZ);
        int iMaxZ = (int) Math.ceil(cz + radiusZ);

        float r = style.red(), g = style.green(), b = style.blue(), a = style.alpha();
        float hw = style.lineWidth() * 0.5f;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (style.disableCull()) RenderSystem.disableCull();

        // ---- 1. 半透明顶面（方块精度的行四边形） -----------
        if (style.faceAlpha() > 0f) {
            float fa = style.faceAlpha();
            BufferBuilder faceBuilder = Tesselator.getInstance().getBuilder();
            faceBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            float topY = maxY + 1f;
            for (int z = iMinZ; z <= iMaxZ; z++) {
                int x = iMinX;
                while (x <= iMaxX) {
                    if (!circleContains(cx, cz, radiusX, radiusZ, x, z)) {
                        x++;
                        continue;
                    }
                    int runStart = x;
                    while (x <= iMaxX && circleContains(cx, cz, radiusX, radiusZ, x, z)) {
                        x++;
                    }
                    int runEnd = x; // 不含

                    // 为此连续段生成单个四边形
                    emitVertex(faceBuilder, mat, cam, runStart,       topY, z,     r, g, b, fa);
                    emitVertex(faceBuilder, mat, cam, runEnd,         topY, z,     r, g, b, fa);
                    emitVertex(faceBuilder, mat, cam, runEnd,         topY, z + 1, r, g, b, fa);
                    emitVertex(faceBuilder, mat, cam, runStart,       topY, z + 1, r, g, b, fa);
                }
            }
            BufferUploader.drawWithShader(faceBuilder.end());
        }

        // ---- 2. 方块边界边框（细长立方体） -----------------------
        if (style.lineWidth() > 0f) {
            BufferBuilder edgeBuilder = Tesselator.getInstance().getBuilder();
            edgeBuilder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

            for (int y = minY; y <= maxY; y++) {
                for (int bx = iMinX; bx <= iMaxX; bx++) {
                    for (int bz = iMinZ; bz <= iMaxZ; bz++) {
                        if (!circleContains(cx, cz, radiusX, radiusZ, bx, bz)) continue;

                        // EAST 面 (+X)：方块 (bx, y, bz) → 边界在 x = bx+1
                        if (!circleContains(cx, cz, radiusX, radiusZ, bx + 1, bz)) {
                            drawBlockFaceEdges(edgeBuilder, mat, cam,
                                    bx + 1, y, bz,  bx + 1, y + 1, bz + 1,
                                    hw, r, g, b, a, /*isVerticalNS=*/true);
                        }
                        // WEST 面 (-X)：边界在 x = bx
                        if (!circleContains(cx, cz, radiusX, radiusZ, bx - 1, bz)) {
                            drawBlockFaceEdges(edgeBuilder, mat, cam,
                                    bx, y, bz,  bx, y + 1, bz + 1,
                                    hw, r, g, b, a, true);
                        }
                        // SOUTH 面 (+Z)：边界在 z = bz+1
                        if (!circleContains(cx, cz, radiusX, radiusZ, bx, bz + 1)) {
                            drawBlockFaceEdges(edgeBuilder, mat, cam,
                                    bx, y, bz + 1,  bx + 1, y + 1, bz + 1,
                                    hw, r, g, b, a, /*isVerticalNS=*/false);
                        }
                        // NORTH 面 (-Z)：边界在 z = bz
                        if (!circleContains(cx, cz, radiusX, radiusZ, bx, bz - 1)) {
                            drawBlockFaceEdges(edgeBuilder, mat, cam,
                                    bx, y, bz,  bx + 1, y + 1, bz,
                                    hw, r, g, b, a, false);
                        }
                    }
                }
            }

            // 同时绘制顶面的水平边框以清晰勾勒出
            // 阶梯形状（这些是顶层面的顶部边框）
            int topY = maxY + 1;
            for (int bx = iMinX; bx <= iMaxX; bx++) {
                for (int bz = iMinZ; bz <= iMaxZ; bz++) {
                    if (!circleContains(cx, cz, radiusX, radiusZ, bx, bz)) continue;

                    // SOUTH 边框 (Z+) 在顶面
                    if (!circleContains(cx, cz, radiusX, radiusZ, bx, bz + 1)) {
                        bufferCuboidLine(edgeBuilder, mat, cam,
                                bx, topY, bz + 1, Direction.EAST, 1f, hw, r, g, b, a);
                    }
                    // EAST 边框 (X+) 在顶面
                    if (!circleContains(cx, cz, radiusX, radiusZ, bx + 1, bz)) {
                        bufferCuboidLine(edgeBuilder, mat, cam,
                                bx + 1, topY, bz, Direction.SOUTH, 1f, hw, r, g, b, a);
                    }
                    // NORTH 边框 (Z-) 在顶面 — 仅在网格边界处
                    if (bz == iMinZ || !circleContains(cx, cz, radiusX, radiusZ, bx, bz - 1)) {
                        if (!circleContains(cx, cz, radiusX, radiusZ, bx, bz - 1)) {
                            bufferCuboidLine(edgeBuilder, mat, cam,
                                    bx, topY, bz, Direction.EAST, 1f, hw, r, g, b, a);
                        }
                    }
                    // WEST 边框 (X-) 在顶面
                    if (bx == iMinX || !circleContains(cx, cz, radiusX, radiusZ, bx - 1, bz)) {
                        if (!circleContains(cx, cz, radiusX, radiusZ, bx - 1, bz)) {
                            bufferCuboidLine(edgeBuilder, mat, cam,
                                    bx, topY, bz, Direction.SOUTH, 1f, hw, r, g, b, a);
                        }
                    }
                }
            }

            BufferUploader.drawWithShader(edgeBuilder.end());
        }

        if (style.disableCull()) RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 将垂直方块面的四条边框发射为细长立方体。
     *
     * <p>该面是世界空间中跨越 {@code (x0, y0, z0) → (x1, y1, z1)} 的矩形。
     * 它必须是轴对齐且垂直的 — 位于 XZ 平面（{@code isVerticalNS = true}，
     * X 为常数）或 YZ 平面（{@code isVerticalNS = false}，Z 为常数）。
     */
    private static void drawBlockFaceEdges(BufferBuilder builder, Matrix4f mat, Vec3 cam,
                                           float x0, float y0, float z0,
                                           float x1, float y1, float z1,
                                           float halfWidth,
                                           float r, float g, float b, float a,
                                           boolean isVerticalNS) {
        float height = y1 - y0;
        float width  = isVerticalNS ? (z1 - z0) : (x1 - x0);

        if (isVerticalNS) {
            // 面位于 YZ 平面（X 为常数）
            // 4 条边框：底 (Z), 顶 (Z), 左 (Y), 右 (Y)
            bufferCuboidLine(builder, mat, cam, x0, y0, z0, Direction.SOUTH, width, halfWidth, r, g, b, a);
            bufferCuboidLine(builder, mat, cam, x0, y1, z0, Direction.SOUTH, width, halfWidth, r, g, b, a);
            bufferCuboidLine(builder, mat, cam, x0, y0, z0, Direction.UP,    height, halfWidth, r, g, b, a);
            bufferCuboidLine(builder, mat, cam, x0, y0, z1, Direction.UP,    height, halfWidth, r, g, b, a);
        } else {
            // 面位于 XY 平面（Z 为常数）
            // 4 条边框：底 (X), 顶 (X), 左 (Y), 右 (Y)
            bufferCuboidLine(builder, mat, cam, x0, y0, z0, Direction.EAST, width,  halfWidth, r, g, b, a);
            bufferCuboidLine(builder, mat, cam, x0, y1, z0, Direction.EAST, width,  halfWidth, r, g, b, a);
            bufferCuboidLine(builder, mat, cam, x0, y0, z0, Direction.UP,   height, halfWidth, r, g, b, a);
            bufferCuboidLine(builder, mat, cam, x1, y0, z0, Direction.UP,   height, halfWidth, r, g, b, a);
        }
    }

    // ---- 圆形几何辅助方法 -------------------------------------------

    /**
     * 测试 ({@code bx}, {@code bz}) 处的方块是否位于椭圆内。
     * 使用与 {@link com.wxwr.kaleidoscopeagricultureevolution.region.CircleRegion#contains}
     * 相同的整数角约定 — 圆心位于作为 {@code center} 传入的 {@link BlockPos}
     * 的角上，方块位置在其最小角处进行测试。这保证了渲染的边界与服务端
     * 认为选中的方块完全匹配。
     */
    private static boolean circleContains(double cx, double cz,
                                          double radiusX, double radiusZ,
                                          int bx, int bz) {
        double nx = (bx - cx) / radiusX;
        double nz = (bz - cz) / radiusZ;
        return nx * nx + nz * nz <= 1.0;
    }
}