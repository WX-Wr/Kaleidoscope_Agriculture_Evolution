package com.wxwr.kaleidoscopeagricultureevolution.blockentity;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony.BeeColonyHost;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony.BeeColonyEnvironment;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony.BeeColonyState;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraitSource;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraits;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BeeColonyBlockEntity extends BaseBlockEntity implements HoneyExtractorTraitSource {
    private static final int ENVIRONMENT_REFRESH_INTERVAL = 20;

    private static final String TAG_HOST = "Host";
    private static final String TAG_STATE = "State";

    private final BeeColonyHost host = new BeeColonyHost();
    private final BeeColonyState state = new BeeColonyState();
    private BeeColonyEnvironment environment = BeeColonyEnvironment.neutral(BeePhenotype.neutral());

    public BeeColonyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BEE_COLONY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BeeColonyBlockEntity colony) {
        if (level.isClientSide) {
            return;
        }
        boolean changed = colony.host.tick(level.random);
        BeePhenotype phenotype = colony.host.getPhenotype();
        if (level.getGameTime() % ENVIRONMENT_REFRESH_INTERVAL == 0L
            || !colony.environment.speciesId().equals(phenotype.speciesId())) {
            colony.environment = BeeColonyEnvironment.evaluate(level, pos, phenotype);
        }
        if (colony.state.tick(phenotype, colony.host.getColonySize(), level.random, colony.environment)) {
            changed = true;
        }
        if (changed) {
            colony.setChanged();
        }
    }

    public BeePhenotype getPhenotype() {
        return host.getPhenotype();
    }

    public int getBeeCount() {
        return host.getColonySize();
    }

    public BeeColonyState getState() {
        return state;
    }

    public BeeColonyEnvironment getEnvironment() {
        return environment;
    }

    public List<BeeGenomeData> getBees() {
        return host.getBees();
    }

    public boolean markAllAnalyzed() {
        return host.markAllAnalyzed();
    }

    public void addBee(BeeGenomeData genome) {
        host.addBee(genome);
        state.sync(host.getPhenotype(), host.getColonySize());
        if (level != null) {
            environment = BeeColonyEnvironment.evaluate(level, worldPosition, host.getPhenotype());
        }
        setChanged();
    }

    @Override
    @NotNull
    public HoneyExtractorTraits getHoneyExtractorTraits(@NotNull ItemStack stack) {
        return host.getHoneyExtractorTraits(stack);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_HOST, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            host.fromNBT(tag.getCompound(TAG_HOST));
        } else {
            host.fromNBT(tag);
        }
        if (tag.contains(TAG_STATE, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            state.fromNBT(tag.getCompound(TAG_STATE));
        } else {
            state.sync(host.getPhenotype(), host.getColonySize());
        }
        environment = BeeColonyEnvironment.neutral(host.getPhenotype());
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_HOST, host.toNBT());
        tag.put(TAG_STATE, state.toNBT());
    }
}
