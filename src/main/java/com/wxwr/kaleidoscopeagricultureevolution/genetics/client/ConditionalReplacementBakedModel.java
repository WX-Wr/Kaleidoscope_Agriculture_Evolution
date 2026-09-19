package com.wxwr.kaleidoscopeagricultureevolution.genetics.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

import java.util.List;

public abstract class ConditionalReplacementBakedModel implements BakedModel {
    private final BakedModel originalModel;
    private final ModelProperty<Boolean> replacementProperty;

    protected ConditionalReplacementBakedModel(BakedModel originalModel,
                                               ModelProperty<Boolean> replacementProperty) {
        this.originalModel = originalModel;
        this.replacementProperty = replacementProperty;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return originalModel.getQuads(state, side, rand);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                                    ModelData data, RenderType renderType) {
        if (usesReplacement(data)) {
            BakedModel replacementModel = getReplacementModel(state, data);
            if (replacementModel != null) {
                return replacementModel.getQuads(state, side, rand, replacementModelData(data), renderType);
            }
        }
        return originalModel.getQuads(state, side, rand, data, renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ModelData queryData = data == null ? ModelData.EMPTY : data;
        if (usesReplacement(queryData)) {
            BakedModel replacementModel = getReplacementModel(state, queryData);
            if (replacementModel != null) {
                return replacementModel.getRenderTypes(state, rand, queryData);
            }
        }
        return originalModel.getRenderTypes(state, rand, queryData);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData data = modelData == null ? ModelData.EMPTY : modelData;
        if (!shouldUseReplacement(level, pos, state, data)) {
            return data;
        }
        return data.derive().with(replacementProperty, true).build();
    }

    protected abstract boolean shouldUseReplacement(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                                    ModelData modelData);

    protected abstract BakedModel getReplacementModel(BlockState state, ModelData modelData);

    protected ModelData replacementModelData(ModelData modelData) {
        return ModelData.EMPTY;
    }

    private boolean usesReplacement(ModelData data) {
        return data != null && Boolean.TRUE.equals(data.get(replacementProperty));
    }

    @Override public boolean useAmbientOcclusion() { return originalModel.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return originalModel.isGui3d(); }
    @Override public boolean usesBlockLight() { return originalModel.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return originalModel.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return originalModel.getParticleIcon(); }
    @Override public ItemOverrides getOverrides() { return originalModel.getOverrides(); }
}
