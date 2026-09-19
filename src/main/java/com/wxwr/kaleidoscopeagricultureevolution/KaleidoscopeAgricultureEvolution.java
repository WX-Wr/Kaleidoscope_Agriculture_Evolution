package com.wxwr.kaleidoscopeagricultureevolution;

import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.ModBlockEntities;
import com.wxwr.kaleidoscopeagricultureevolution.client.ClientSetup;
import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.recipe.ContainerRecipeConfig;
import com.wxwr.kaleidoscopeagricultureevolution.container.ContainerWhitelist;
import com.wxwr.kaleidoscopeagricultureevolution.entity.FarmerEntity;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ModEntities;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ModMemoryModuleTypes;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import com.wxwr.kaleidoscopeagricultureevolution.effect.ModEffects;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.GeneticsSetup;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import com.wxwr.kaleidoscopeagricultureevolution.network.KaeNetwork;
import com.wxwr.kaleidoscopeagricultureevolution.recipe.ModRecipes;
import com.wxwr.kaleidoscopeagricultureevolution.particle.ModParticles;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRuleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(KaleidoscopeAgricultureEvolution.MODID)
public class KaleidoscopeAgricultureEvolution {

    public static final String MODID = "kaleidoscope_agriculture_evolution";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SuppressWarnings("removal")
    public KaleidoscopeAgricultureEvolution() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMemoryModuleTypes.register(modEventBus);
        ModEffects.register(modEventBus);
        ModParticles.register(modEventBus);
        Config.register(modEventBus);
        ContainerWhitelist.loadFromConfigFile();
        ContainerRecipeConfig.loadFromConfigFile();
        WorkRuleRegistry.loadFromConfigFiles();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSetup::registerConfigScreen);
        KaeNetwork.register();
        GeneticsSetup.init(modEventBus);

        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerAttributes);
    }

    public static ResourceLocation rl(String path) {
        return rl(MODID, path);
    }

    public static ResourceLocation rl(String namespace, String path) {
        ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
        if (location == null) {
            throw new IllegalArgumentException("Invalid resource location: " + namespace + ":" + path);
        }
        return location;
    }

    public static ResourceLocation id(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid resource location: " + id);
        }
        return location;
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(ClientSetup::setup);
    }

    private void registerAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.PLOW_OX.get(), PlowOxEntity.createAttributes().build());
        event.put(ModEntities.FARMER.get(), FarmerEntity.createAttributes().build());
    }
}
