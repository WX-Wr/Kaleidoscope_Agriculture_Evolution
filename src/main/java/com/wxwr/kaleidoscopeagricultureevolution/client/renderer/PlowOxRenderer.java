package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.client.model.PlowOxModel;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PlowOxRenderer extends GeoEntityRenderer<PlowOxEntity> {
    private static final String LEASH_LOCATOR_NAME = "locator";
    private static final Map<ResourceLocation, Optional<Vec3>> LEASH_LOCATORS = new HashMap<>();

    private PlowOxEntity locatorEntity;
    private Vec3 locatorWorldPosition;

    public PlowOxRenderer(EntityRendererProvider.Context context) {
        super(context, new PlowOxModel());
        this.shadowRadius = 0.7F;
    }

    @Override
    public float getMotionAnimThreshold(PlowOxEntity animatable) {
        return 0.000001f;
    }

    @Override
    public void render(PlowOxEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        locatorEntity = null;
        locatorWorldPosition = null;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        if (entity.getOxState() == PlowOxEntity.OxState.ERROR) {
            IssueIconRenderer.renderError(entity, this.entityRenderDispatcher.cameraOrientation(),
                    poseStack, buffer, 0.55F, 0.65F);
        }
    }

    @Override
    public void renderRecursively(PoseStack poseStack, PlowOxEntity entity, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green,
                                  float blue, float alpha) {
        if (!isReRender && "head".equals(bone.getName())) {
            getLeashLocator(entity).ifPresent(locator -> {
                locatorEntity = entity;
                locatorWorldPosition = calculateLocatorWorldPosition(
                        poseStack, bone, entity, locator, partialTick);
            });
        }

        super.renderRecursively(poseStack, entity, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public <E extends Entity, M extends Mob> void renderLeash(M mob, float partialTick,
                                                               PoseStack poseStack,
                                                               MultiBufferSource bufferSource,
                                                               E leashHolder) {
        if (!(mob instanceof PlowOxEntity ox)
                || ox != locatorEntity
                || locatorWorldPosition == null) {
            super.renderLeash(mob, partialTick, poseStack, bufferSource, leashHolder);
            return;
        }

        renderLocatorLeash(ox, partialTick, poseStack, bufferSource, leashHolder,
                locatorWorldPosition);
    }

    @Override
    public void doPostRenderCleanup() {
        locatorEntity = null;
        locatorWorldPosition = null;
        super.doPostRenderCleanup();
    }

    private Vec3 calculateLocatorWorldPosition(PoseStack poseStack, GeoBone bone,
                                                PlowOxEntity entity, Vec3 locator,
                                                float partialTick) {
        poseStack.pushPose();
        RenderUtils.translateMatrixToBone(poseStack, bone);
        RenderUtils.translateToPivotPoint(poseStack, bone);
        RenderUtils.rotateMatrixAroundBone(poseStack, bone);
        RenderUtils.scaleMatrixForBone(poseStack, bone);
        RenderUtils.translateAwayFromPivotPoint(poseStack, bone);

        Matrix4f entityLocalMatrix = RenderUtils.invertAndMultiplyMatrices(
                poseStack.last().pose(), this.entityRenderTranslations);
        Vector4f locatorPosition = new Vector4f(
                (float) (-locator.x / 16.0D),
                (float) (locator.y / 16.0D),
                (float) (locator.z / 16.0D),
                1.0F);
        entityLocalMatrix.transform(locatorPosition);
        poseStack.popPose();

        double entityX = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double entityY = entity.yOld + (entity.getY() - entity.yOld) * partialTick;
        double entityZ = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        return new Vec3(entityX + locatorPosition.x(), entityY + locatorPosition.y(),
                entityZ + locatorPosition.z());
    }

    private Optional<Vec3> getLeashLocator(PlowOxEntity entity) {
        ResourceLocation modelResource = getGeoModel().getModelResource(entity);
        Optional<Vec3> locator = LEASH_LOCATORS.get(modelResource);
        if (locator == null) {
            locator = loadLeashLocator(modelResource);
            LEASH_LOCATORS.put(modelResource, locator);
        }
        return locator;
    }

    private Optional<Vec3> loadLeashLocator(ResourceLocation modelResource) {
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resource =
                    Minecraft.getInstance().getResourceManager().getResource(modelResource);
            if (resource.isEmpty()) {
                return Optional.empty();
            }

            try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
                if (geometries == null || geometries.isEmpty()) {
                    return Optional.empty();
                }

                JsonArray bones = geometries.get(0).getAsJsonObject().getAsJsonArray("bones");
                if (bones == null) {
                    return Optional.empty();
                }

                for (int i = 0; i < bones.size(); i++) {
                    JsonObject bone = bones.get(i).getAsJsonObject();
                    if (!"head".equals(bone.get("name").getAsString())) {
                        continue;
                    }
                    JsonObject locators = bone.getAsJsonObject("locators");
                    JsonArray values = locators == null
                            ? null : locators.getAsJsonArray(LEASH_LOCATOR_NAME);
                    if (values == null || values.size() < 3) {
                        return Optional.empty();
                    }
                    return Optional.of(new Vec3(values.get(0).getAsDouble(),
                            values.get(1).getAsDouble(), values.get(2).getAsDouble()));
                }
            }
        } catch (RuntimeException | IOException exception) {
            KaleidoscopeAgricultureEvolution.LOGGER.warn(
                    "Unable to read plow ox leash locator from {}", modelResource, exception);
        }
        return Optional.empty();
    }

    private void renderLocatorLeash(PlowOxEntity ox, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, Entity leashHolder,
                                    Vec3 start) {
        double oxX = ox.xOld + (ox.getX() - ox.xOld) * partialTick;
        double oxY = ox.yOld + (ox.getY() - ox.yOld) * partialTick;
        double oxZ = ox.zOld + (ox.getZ() - ox.zOld) * partialTick;
        Vec3 end = leashHolder.getRopeHoldPosition(partialTick);
        float xDif = (float) (end.x - start.x);
        float yDif = (float) (end.y - start.y);
        float zDif = (float) (end.z - start.z);
        float offsetMod = Mth.invSqrt(xDif * xDif + zDif * zDif) * 0.025f / 2f;
        float xOffset = zDif * offsetMod;
        float zOffset = xDif * offsetMod;
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.leash());
        BlockPos oxEyePos = BlockPos.containing(ox.getEyePosition(partialTick));
        BlockPos holderEyePos = BlockPos.containing(leashHolder.getEyePosition(partialTick));
        int oxBlockLight = getBlockLightLevel(ox, oxEyePos);
        int holderBlockLight = leashHolder.isOnFire()
                ? 15 : leashHolder.level().getBrightness(LightLayer.BLOCK, holderEyePos);
        int oxSkyLight = ox.level().getBrightness(LightLayer.SKY, oxEyePos);
        int holderSkyLight = ox.level().getBrightness(LightLayer.SKY, holderEyePos);

        poseStack.pushPose();
        poseStack.translate(start.x - oxX, start.y - oxY, start.z - oxZ);
        Matrix4f positionMatrix = new Matrix4f(poseStack.last().pose());
        for (int segment = 0; segment <= 24; segment++) {
            renderLeashPiece(vertexConsumer, positionMatrix, xDif, yDif, zDif,
                    oxBlockLight, holderBlockLight, oxSkyLight, holderSkyLight,
                    0.025f, 0.025f, xOffset, zOffset, segment, false);
        }
        for (int segment = 24; segment >= 0; segment--) {
            renderLeashPiece(vertexConsumer, positionMatrix, xDif, yDif, zDif,
                    oxBlockLight, holderBlockLight, oxSkyLight, holderSkyLight,
                    0.025f, 0.0f, xOffset, zOffset, segment, true);
        }
        poseStack.popPose();
    }

    private static void renderLeashPiece(VertexConsumer buffer, Matrix4f positionMatrix,
                                          float xDif, float yDif, float zDif,
                                          int oxBlockLight, int holderBlockLight,
                                          int oxSkyLight, int holderSkyLight,
                                          float width, float yOffset, float xOffset,
                                          float zOffset, int segment, boolean isLeashKnot) {
        float percent = segment / 24.0F;
        int blockLight = (int) Mth.lerp(percent, oxBlockLight, holderBlockLight);
        int skyLight = (int) Mth.lerp(percent, oxSkyLight, holderSkyLight);
        int packedLight = LightTexture.pack(blockLight, skyLight);
        float colorScale = segment % 2 == (isLeashKnot ? 1 : 0) ? 0.7F : 1.0F;
        float x = xDif * percent;
        float y = yDif > 0.0F
                ? yDif * percent * percent
                : yDif - yDif * (1.0F - percent) * (1.0F - percent);
        float z = zDif * percent;
        buffer.vertex(positionMatrix, x - xOffset, y + yOffset, z + zOffset)
                .color(0.5F * colorScale, 0.4F * colorScale, 0.3F * colorScale, 1.0F)
                .uv2(packedLight).endVertex();
        buffer.vertex(positionMatrix, x + xOffset, y + width - yOffset, z - zOffset)
                .color(0.5F * colorScale, 0.4F * colorScale, 0.3F * colorScale, 1.0F)
                .uv2(packedLight).endVertex();
    }
}
