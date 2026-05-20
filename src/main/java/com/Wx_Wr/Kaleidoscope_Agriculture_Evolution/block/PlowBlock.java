package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

public class PlowBlock extends Block {

    public PlowBlock() {
        super(Properties.of()
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .noOcclusion());
    }
}