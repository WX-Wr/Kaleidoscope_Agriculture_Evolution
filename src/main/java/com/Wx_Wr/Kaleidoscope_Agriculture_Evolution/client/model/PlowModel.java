package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.model;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PlowModel extends GeoModel<PlowEntity> {

    @Override
    public ResourceLocation getModelResource(PlowEntity entity) {
        return new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "geo/entity/plow.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PlowEntity entity) {
        return new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "textures/entity/plow.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PlowEntity entity) {
        return new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "animations/entity/plow.animation.json");
    }
}