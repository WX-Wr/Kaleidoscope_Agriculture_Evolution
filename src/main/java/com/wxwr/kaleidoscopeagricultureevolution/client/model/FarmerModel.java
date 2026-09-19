package com.wxwr.kaleidoscopeagricultureevolution.client.model;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.entity.FarmerEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class FarmerModel extends DefaultedEntityGeoModel<FarmerEntity> {

    public FarmerModel() {
        super(KaleidoscopeAgricultureEvolution.rl("farmer"), "head");
    }
}
