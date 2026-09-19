package com.wxwr.kaleidoscopeagricultureevolution.client.model;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PlowModel extends GeoModel<PlowEntity> {

    @Override
    public ResourceLocation getModelResource(PlowEntity entity) {
        return KaleidoscopeAgricultureEvolution.rl("geo/entity/plow.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PlowEntity entity) {
        return KaleidoscopeAgricultureEvolution.rl("textures/entity/plow.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PlowEntity entity) {
        return KaleidoscopeAgricultureEvolution.rl("animations/entity/plow.animation.json");
    }
}
