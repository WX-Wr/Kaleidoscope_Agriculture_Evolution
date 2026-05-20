package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.block.ModBlocks;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.ClientSetup;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ModEntities;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Kaleidoscope_Agriculture_Evolution.MODID)
public class Kaleidoscope_Agriculture_Evolution {

    public static final String MODID = "kaleidoscope_agriculture_evolution";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public Kaleidoscope_Agriculture_Evolution() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        Config.register(modEventBus);

        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerAttributes);
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MODID, path);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(ClientSetup::setup);
    }

    private void registerAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.PLOW_OX.get(), PlowOxEntity.createAttributes().build());
    }
}