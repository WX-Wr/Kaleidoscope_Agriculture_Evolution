package com.wxwr.kaleidoscopeagricultureevolution.client.gui;

import com.wxwr.kaleidoscopeagricultureevolution.item.WhipItem;
import com.wxwr.kaleidoscopeagricultureevolution.region.RegionMode;
import com.wxwr.kaleidoscopeagricultureevolution.work.WhipMenuLayer;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class RegionModeOverlay {
    private static final int X_OFFSET = 10;
    private static final int Y_OFFSET = 10;
    private static final int LINE_SPACING = 12;
    private static final int COLOR_SELECTED = 0xFFFFD700;
    private static final int COLOR_NORMAL = 0xFFAAAAAA;
    private static final int COLOR_DISABLED = 0xFF555555;

    private static final String KEY_MODE = "mode";
    private static final String KEY_SELECT_STATE = "selectState";

    private RegionModeOverlay() {
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof WhipItem)) return;

        RegionMode.SelectState state = getSelectStateFromStack(stack);
        if (state != RegionMode.SelectState.IDLE) return;

        WhipMenuLayer layer = WhipItem.getMenuLayer(stack);
        String titleKey = "message.kaleidoscope_agriculture_evolution.menu_layer_" + layer.key();
        graphics.drawString(mc.font, Component.translatable(titleKey).getString(), X_OFFSET, Y_OFFSET, COLOR_SELECTED);

        switch (layer) {
            case REGION_MODE -> renderRegionModes(graphics, mc, getModeFromStack(stack));
            case FIELD_TYPE -> renderFieldTypes(graphics, mc, WhipItem.getSelectedFieldType(stack));
            case WORK_ACTION -> renderWorkActions(graphics, mc, WhipItem.getSelectedWorkAction(stack));
        }
    }

    private static void renderRegionModes(GuiGraphics graphics, Minecraft mc, RegionMode selectedMode) {
        RegionMode[] values = RegionMode.values();
        for (int i = 0; i < values.length; i++) {
            RegionMode mode = values[i];
            boolean selected = mode == selectedMode;
            int color = selected ? COLOR_SELECTED : (mode.isScrollSelectable() ? COLOR_NORMAL : COLOR_DISABLED);
            String key = "message.kaleidoscope_agriculture_evolution.mode_" + mode.getKey();
            drawOption(graphics, mc, i, selected, key, color);
        }
    }

    private static void renderFieldTypes(GuiGraphics graphics, Minecraft mc, WorkFieldType selectedType) {
        WorkFieldType[] values = WorkFieldType.values();
        for (int i = 0; i < values.length; i++) {
            WorkFieldType type = values[i];
            boolean selected = type == selectedType;
            String key = "message.kaleidoscope_agriculture_evolution.field_type_" + type.key();
            drawOption(graphics, mc, i, selected, key, selected ? COLOR_SELECTED : COLOR_NORMAL);
        }
    }

    private static void renderWorkActions(GuiGraphics graphics, Minecraft mc, WorkAction selectedAction) {
        WorkAction[] values = WorkAction.values();
        for (int i = 0; i < values.length; i++) {
            WorkAction action = values[i];
            boolean selected = action == selectedAction;
            String key = "message.kaleidoscope_agriculture_evolution.work_action_" + action.key();
            drawOption(graphics, mc, i, selected, key, selected ? COLOR_SELECTED : COLOR_NORMAL);
        }
    }

    private static void drawOption(GuiGraphics graphics, Minecraft mc, int index, boolean selected, String key, int color) {
        String prefix = selected ? "> " : "  ";
        String line = prefix + Component.translatable(key).getString();
        graphics.drawString(mc.font, line, X_OFFSET, Y_OFFSET + (index + 1) * LINE_SPACING, color);
    }

    private static RegionMode getModeFromStack(ItemStack stack) {
        if (!stack.hasTag()) return RegionMode.RECTANGLE;
        CompoundTag tag = stack.getTag();
        if (!tag.contains(KEY_MODE)) return RegionMode.RECTANGLE;
        return RegionMode.fromId(tag.getInt(KEY_MODE));
    }

    private static RegionMode.SelectState getSelectStateFromStack(ItemStack stack) {
        if (!stack.hasTag()) return RegionMode.SelectState.IDLE;
        CompoundTag tag = stack.getTag();
        if (!tag.contains(KEY_SELECT_STATE)) return RegionMode.SelectState.IDLE;
        return RegionMode.SelectState.fromId(tag.getInt(KEY_SELECT_STATE));
    }
}
