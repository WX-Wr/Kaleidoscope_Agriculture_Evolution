package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer.PlowOxRenderer;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer.PlowRenderer;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class ClientSetup {

    public static void setup() {
        System.out.println("=== ClientSetup.setup() START ===");

        EntityRenderers.register(ModEntities.PLOW_OX.get(), PlowOxRenderer::new);
        EntityRenderers.register(ModEntities.PLOW_ENTITY.get(), PlowRenderer::new);

        System.out.println("=== ClientSetup.setup() END ===");
    }
}