package com.wxwr.kaleidoscopeagricultureevolution.client.model;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.entity.LoucheEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LoucheModel extends GeoModel<LoucheEntity> {

    @Override
    public ResourceLocation getModelResource(LoucheEntity entity) {
        return KaleidoscopeAgricultureEvolution.rl("geo/entity/louche.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LoucheEntity entity) {
        return KaleidoscopeAgricultureEvolution.rl("textures/block/farm_tools/louche.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LoucheEntity entity) {
        return KaleidoscopeAgricultureEvolution.rl("animations/entity/louche.animation.json");
    }
}
