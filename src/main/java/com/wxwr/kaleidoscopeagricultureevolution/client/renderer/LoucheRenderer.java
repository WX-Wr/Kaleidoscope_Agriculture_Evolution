package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.client.model.LoucheModel;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemTypeCountView;
import com.wxwr.kaleidoscopeagricultureevolution.entity.LoucheEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class LoucheRenderer extends GeoEntityRenderer<LoucheEntity> {
    private static final ResourceLocation INVENTORY_TEXTURE =
            KaleidoscopeAgricultureEvolution.rl("textures/gui/table/table1.png");
    private static final int TEXTURE_WIDTH = 158;
    private static final int TEXTURE_HEIGHT = 31;
    private static final int SLOT_COUNT = 8;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_TOP = 7;
    private static final int ICON_SIZE = 18;
    private static final int SHOW_RADIUS_SQUARE = 9;
    private static final float ITEM_LAYER_Z = -0.1F;
    private static final float TEXT_LAYER_Z = -0.15F;
    private static final float PIXEL_SCALE = 0.0125F;
    private static final float Y_OFFSET = 1.15F;

    private final Font font;

    public LoucheRenderer(EntityRendererProvider.Context context) {
        super(context, new LoucheModel());
        this.font = context.getFont();
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(LoucheEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, 0, partialTicks, poseStack, buffer, packedLight);
        if (shouldRenderInventory(entity)) {
            renderInventoryTable(entity, poseStack, buffer);
        }
    }

    @Override
    protected void applyRotations(LoucheEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTicks) {
        poseStack.mulPose(Axis.YP.rotationDegrees(-animatable.getYRot()));
    }

    private boolean shouldRenderInventory(LoucheEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (!isHoldingSeed(player)) {
            return false;
        }

        double dx = player.getX() - entity.getX();
        double dz = player.getZ() - entity.getZ();
        return dx * dx + dz * dz <= SHOW_RADIUS_SQUARE;
    }

    private static boolean isHoldingSeed(LocalPlayer player) {
        return LoucheEntity.isSeedItem(player.getMainHandItem().getItem())
                || LoucheEntity.isSeedItem(player.getOffhandItem().getItem());
    }

    private void renderInventoryTable(LoucheEntity entity, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() + Y_OFFSET, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-PIXEL_SCALE, -PIXEL_SCALE, PIXEL_SCALE);

        renderTableBackground(poseStack, buffer);
        renderTableItems(entity, poseStack, buffer);
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        poseStack.popPose();
    }

    private static void renderTableBackground(PoseStack poseStack, MultiBufferSource buffer) {
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        float left = -TEXTURE_WIDTH / 2.0F;
        float right = TEXTURE_WIDTH / 2.0F;
        float top = -TEXTURE_HEIGHT / 2.0F;
        float bottom = TEXTURE_HEIGHT / 2.0F;
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, INVENTORY_TEXTURE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, left, bottom, 0.0F).uv(0.0F, 1.0F).endVertex();
        builder.vertex(matrix, right, bottom, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(matrix, right, top, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(matrix, left, top, 0.0F).uv(0.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderTableItems(LoucheEntity entity, PoseStack poseStack, MultiBufferSource buffer) {
        List<ItemTypeCountView.ItemTypeCount> counts = entity.getItemTypeCounts();
        int rendered = Math.min(counts.size(), SLOT_COUNT);
        float firstSlotLeft = -((SLOT_COUNT * SLOT_SIZE) / 2.0F);

        for (int i = 0; i < rendered; i++) {
            ItemTypeCountView.ItemTypeCount count = counts.get(i);
            float iconX = firstSlotLeft + i * SLOT_SIZE + 1.0F;
            float iconY = -TEXTURE_HEIGHT / 2.0F + SLOT_TOP;
            ItemStack iconStack = new ItemStack(count.item());

            renderItemSprite(entity, iconStack, i, iconX, iconY, poseStack);
            renderItemCount(count.count(), iconX, iconY, poseStack, buffer);
        }
    }

    private void renderItemSprite(LoucheEntity entity, ItemStack stack, int index, float x, float y,
                                  PoseStack poseStack) {
        poseStack.pushPose();
        poseStack.translate(x + ICON_SIZE / 2.25F, y + ICON_SIZE / 2.3F, ITEM_LAYER_Z);
        poseStack.scale(ICON_SIZE, ICON_SIZE, ICON_SIZE);
        BakedModel model = Minecraft.getInstance().getItemRenderer()
                .getModel(stack, entity.level(), null, entity.getId() + index);
        TextureAtlasSprite sprite = model.getParticleIcon();
        renderSpriteQuad(poseStack, sprite);
        poseStack.popPose();
    }

    private static void renderSpriteQuad(PoseStack poseStack, TextureAtlasSprite sprite) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, -0.5F, 0.5F, 0.0F).uv(sprite.getU0(), sprite.getV1()).endVertex();
        builder.vertex(matrix, 0.5F, 0.5F, 0.0F).uv(sprite.getU1(), sprite.getV1()).endVertex();
        builder.vertex(matrix, 0.5F, -0.5F, 0.0F).uv(sprite.getU1(), sprite.getV0()).endVertex();
        builder.vertex(matrix, -0.5F, -0.5F, 0.0F).uv(sprite.getU0(), sprite.getV0()).endVertex();
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderItemCount(int count, float x, float y, PoseStack poseStack, MultiBufferSource buffer) {
        if (count <= 1) {
            return;
        }
        String text = String.valueOf(count);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, TEXT_LAYER_Z);
        Matrix4f matrix = poseStack.last().pose();
        float textX = x + 16.0F- font.width(text);
        float textY = y + 7.0F;
        font.drawInBatch(text, textX, textY, 0xFFFFFFFF, true, matrix, buffer,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
