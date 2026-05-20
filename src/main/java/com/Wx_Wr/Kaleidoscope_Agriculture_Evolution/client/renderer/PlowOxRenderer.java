package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.model.PlowOxModel;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PlowOxRenderer extends GeoEntityRenderer<PlowOxEntity> {

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
        // 只渲染实体模型，绳子在全局事件中渲染
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}