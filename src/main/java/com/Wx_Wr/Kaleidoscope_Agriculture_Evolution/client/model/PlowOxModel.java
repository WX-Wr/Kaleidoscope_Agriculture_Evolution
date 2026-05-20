package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.model;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;  // 直接导入 entity 下的
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class PlowOxModel extends GeoModel<PlowOxEntity> {

    @Override
    public ResourceLocation getModelResource(PlowOxEntity entity) {
        return new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "geo/entity/plowox.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PlowOxEntity entity) {
        return new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "textures/entity/plowox.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PlowOxEntity entity) {
        return new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "animations/entity/plowox.animation.json");
    }
}