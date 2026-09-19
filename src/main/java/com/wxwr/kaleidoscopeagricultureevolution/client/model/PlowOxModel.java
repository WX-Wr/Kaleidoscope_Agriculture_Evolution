package com.wxwr.kaleidoscopeagricultureevolution.client.model;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class PlowOxModel extends DefaultedEntityGeoModel<PlowOxEntity> {

    private static final ResourceLocation DEFAULT_MODEL =
            KaleidoscopeAgricultureEvolution.rl("geo/entity/plowox.geo.json");
    private static final ResourceLocation SACK_MODEL =
            KaleidoscopeAgricultureEvolution.rl("geo/entity/plowox_sack.geo.json");
    private static final ResourceLocation YOKED_MODEL =
            KaleidoscopeAgricultureEvolution.rl("geo/entity/plowox_yoke.geo.json");
    private static final ResourceLocation TEXTURE =
            KaleidoscopeAgricultureEvolution.rl("textures/entity/plowox.png");
    private static final ResourceLocation ANIMATION =
            KaleidoscopeAgricultureEvolution.rl("animations/entity/plowox.animation.json");

    public PlowOxModel() {
        super(KaleidoscopeAgricultureEvolution.rl("plowox"), "head");
    }

    @Override
    public ResourceLocation getModelResource(PlowOxEntity entity) {
        if (entity == null) {
            return DEFAULT_MODEL;
        }
        return switch (entity.getVisualState()) {
            case RICE_SACKS -> SACK_MODEL;
            case YOKE -> YOKED_MODEL;
            default -> DEFAULT_MODEL;
        };
    }

    @Override
    public ResourceLocation getTextureResource(PlowOxEntity entity) {
        if (entity == null) {
            return TEXTURE;
        }
        return TEXTURE;
    };

    @Override
    public ResourceLocation getAnimationResource(PlowOxEntity entity) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(PlowOxEntity entity, long instanceId,
                                     AnimationState<PlowOxEntity> animationState) {
        CoreGeoBone head = getAnimationProcessor().getBone(this.headBone);
        if (head != null && entity.getOxState() != PlowOxEntity.OxState.WORKING) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
        if (entity.getOxState() == PlowOxEntity.OxState.WORKING) {
            CoreGeoBone root = getAnimationProcessor().getBone("root");
            if (root != null) {
                root.setRotY(head.getRotY());
            }
        }
    }
}
