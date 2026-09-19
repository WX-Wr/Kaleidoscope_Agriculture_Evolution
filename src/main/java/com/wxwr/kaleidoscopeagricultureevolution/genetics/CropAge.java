package com.wxwr.kaleidoscopeagricultureevolution.genetics;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class CropAge {
    private CropAge() {}

    public static int get(BlockState state) {
        IntegerProperty property = findAgeProperty(state);
        return property != null ? state.getValue(property) : -1;
    }

    public static BlockState set(BlockState state, int age) {
        IntegerProperty property = findAgeProperty(state);
        if (property == null) return state;
        int clamped = Math.max(property.getPossibleValues().stream().min(Integer::compareTo).orElse(0),
                Math.min(property.getPossibleValues().stream().max(Integer::compareTo).orElse(age), age));
        return state.setValue(property, clamped);
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty
                    && "age".equals(integerProperty.getName())) {
                return integerProperty;
            }
        }
        return null;
    }
}
