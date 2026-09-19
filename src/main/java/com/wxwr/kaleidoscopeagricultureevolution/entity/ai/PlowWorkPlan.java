package com.wxwr.kaleidoscopeagricultureevolution.entity.ai;

import com.wxwr.kaleidoscopeagricultureevolution.region.CuboidRegion;
import com.wxwr.kaleidoscopeagricultureevolution.region.Region;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkPathRule;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRuleRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkZoneSerializer;
import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Immutable description of one plowing request.
 *
 * <p>The entity owns the request lifecycle, while the planner owns the
 * generated route. Keeping these values together makes it possible to add
 * non-rectangular work regions later without growing the entity API.</p>
 */
public final class PlowWorkPlan {
    private final Region region;
    private final BlockPos startPos;
    private final PlowAI.Direction direction;
    private final WorkFieldType fieldType;
    private final WorkAction action;
    private final String pathRuleId;
    private final WorkPathRule pathRule;

    public PlowWorkPlan(Region region, BlockPos startPos, PlowAI.Direction direction) {
        this(region, startPos, direction, WorkZoneSerializer.DEFAULT_FIELD_TYPE, WorkZoneSerializer.DEFAULT_ACTION);
    }

    public PlowWorkPlan(Region region, BlockPos startPos, PlowAI.Direction direction,
                        WorkFieldType fieldType, WorkAction action) {
        this.region = Objects.requireNonNull(region, "region");
        this.startPos = Objects.requireNonNull(startPos, "startPos");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.fieldType = Objects.requireNonNull(fieldType, "fieldType");
        this.action = Objects.requireNonNull(action, "action");
        this.pathRuleId = resolvePathRuleId(fieldType, action);
        this.pathRule = WorkRuleRegistry.pathRuleFor(fieldType, action);
    }

    public static PlowWorkPlan rectangular(BlockPos cornerA, BlockPos cornerB, PlowAI.Direction direction) {
        return rectangular(cornerA, cornerB, direction, WorkZoneSerializer.DEFAULT_FIELD_TYPE, WorkZoneSerializer.DEFAULT_ACTION);
    }

    public static PlowWorkPlan rectangular(BlockPos cornerA, BlockPos cornerB, PlowAI.Direction direction,
                                          WorkFieldType fieldType, WorkAction action) {
        return new PlowWorkPlan(new CuboidRegion(cornerA, cornerB), cornerA, direction, fieldType, action);
    }

    private static String resolvePathRuleId(WorkFieldType fieldType, WorkAction action) {
        return WorkRuleRegistry.findRule(fieldType, action)
                .map(rule -> rule.pathRule())
                .orElse(fieldType.key());
    }

    public Region region() {
        return region;
    }

    public BlockPos startPos() {
        return startPos;
    }

    public PlowAI.Direction direction() {
        return direction;
    }

    public WorkFieldType fieldType() {
        return fieldType;
    }

    public WorkAction action() {
        return action;
    }

    public String pathRuleId() {
        return pathRuleId;
    }

    public WorkPathRule pathRule() {
        return pathRule;
    }
}
