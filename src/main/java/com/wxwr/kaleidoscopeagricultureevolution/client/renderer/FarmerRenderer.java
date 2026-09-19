package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.client.model.FarmerModel;
import com.wxwr.kaleidoscopeagricultureevolution.entity.FarmerEntity;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkIssueState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FarmerRenderer extends GeoEntityRenderer<FarmerEntity> {

    public FarmerRenderer(EntityRendererProvider.Context context) {
        super(context, new FarmerModel());
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(FarmerEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        WorkIssueState state = entity.getWorkIssueState();
        if (state == WorkIssueState.ERROR) {
            IssueIconRenderer.renderError(entity, this.entityRenderDispatcher.cameraOrientation(),
                    poseStack, buffer, 0.45F, 0.5F);
        } else if (state == WorkIssueState.WARN) {
            IssueIconRenderer.renderWarn(entity, this.entityRenderDispatcher.cameraOrientation(),
                    poseStack, buffer, 0.45F, 0.5F);
        }
    }
}
