package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.client.model.PlowModel;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class PlowRenderer extends GeoEntityRenderer<PlowEntity> {

    public PlowRenderer(EntityRendererProvider.Context context) {
        super(context, new PlowModel());
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(PlowEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, 0, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void applyRotations(PlowEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        poseStack.mulPose(Axis.YP.rotationDegrees(-animatable.getYRot()));
    }
}