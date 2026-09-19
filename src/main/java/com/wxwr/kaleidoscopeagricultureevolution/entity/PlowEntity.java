package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowableChecker;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkPathRule;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRuleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

public class PlowEntity extends AbstractDraggableEntity {

    public PlowEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ==================== 核心牵引行为 ====================

    @Override
    protected void onDraggerMove() {
        PlowOxEntity ox = getOx();
        if (ox == null) return;
        if (ox.getCurrentWorkAction() != WorkAction.TILL) return;

        WorkFieldType fieldType = ox.getCurrentWorkFieldType();
        WorkPathRule pathRule = WorkRuleRegistry.pathRuleFor(fieldType, WorkAction.TILL);

        // 计算牛后方第1个方块的XZ坐标
        float yaw = ox.getYRot();
        double rad = Math.toRadians(yaw);
        int behindX = Mth.floor(ox.getX() + Math.sin(rad) * 2.0);
        int behindZ = Mth.floor(ox.getZ() - Math.cos(rad) * 2.0);

        if (fieldType == WorkFieldType.PADDY_FIELD) {
            BlockPos waterPos = new BlockPos(behindX, ox.blockPosition().getY(), behindZ);
            if (!PlowableChecker.isWorkable(level(), waterPos, WorkAction.TILL, pathRule)) return;

            BlockPos operationPos = waterPos.below();
            BlockState operationState = level().getBlockState(operationPos);
            if (!isTillable(operationState)) return;
            level().setBlock(operationPos,
                    Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0), 3);
            level().playSound(null, operationPos,
                    net.minecraft.sounds.SoundEvents.HOE_TILL,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }

        // 从牛脚部Y坐标向下扫描，找到地表方块
        int scanY = ox.blockPosition().getY();
        while (scanY > level().getMinBuildHeight()
                && level().isEmptyBlock(new BlockPos(behindX, scanY, behindZ))) {
            scanY--;
        }

        if (scanY <= level().getMinBuildHeight()) return;

        BlockPos surfacePos = new BlockPos(behindX, scanY, behindZ);

        // 必须在工作区域内
        if (!isInWorkArea(ox, surfacePos)) return;

        BlockPos abovePos = surfacePos.above();
        BlockState surfaceState = level().getBlockState(surfacePos);
        BlockState aboveState = level().getBlockState(abovePos);

        // 上方一格不是空气则不处理（保护作物、栅栏等不被犁误伤）
        if (!aboveState.isAir()) return;

        // 翻耕泥土（仅地表）
        if (isTillable(surfaceState)) {
            level().setBlock(surfacePos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0), 3);
            if (!this.level().isClientSide) {
                this.level().playSound(null, surfacePos,
                        net.minecraft.sounds.SoundEvents.HOE_TILL,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            }
        }

        // 破坏路径上的花花草草
        if (isBreakablePlant(aboveState)) {
            level().destroyBlock(abovePos, true);
        }
        if (isBreakablePlant(surfaceState)) {
            level().destroyBlock(surfacePos, true);
        }
    }

    private boolean isBreakablePlant(BlockState state) {
        return state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    @Override
    protected ItemStack getDropItem() {
        return new ItemStack(ModBlocks.PLOW.get().asItem());
    }

    @Override
    protected double getFollowDistance() { return 1.2; }
    @Override
    protected double getMaxBreakDistance() { return 6.0; }
    @Override
    protected double getMinWorkSpeed() { return 0.02; } // 低于基础犁地速度 0.05

    private boolean isTillable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) ||
                state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT);
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::predicate));
    }

    private PlayState predicate(AnimationState<PlowEntity> state) {
        if (isWorking() && this.getDeltaMovement().horizontalDistance() > getMinWorkSpeed()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plow.plowing"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plow.idle"));
        }
        return PlayState.CONTINUE;
    }

    // ==================== 绳子连接点 ====================

    private static final float PLOW_LEFT_X = -0.5625f;  // -9/16 (left_rope_attach)
    private static final float PLOW_RIGHT_X = 0.5625f;   // 9/16 (right_rope_attach)
    private static final float PLOW_Y = 0.31875f;        // 5.1/16
    private static final float PLOW_Z = 0.60625f;        // 9.7/16 (bb_main locator)

    @Override
    public Vec3 getLeftRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, PLOW_LEFT_X, PLOW_Y, PLOW_Z, partialTick);
    }

    @Override
    public Vec3 getRightRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, PLOW_RIGHT_X, PLOW_Y, PLOW_Z, partialTick);
    }

    private static Vec3 calculateAttachPoint(Entity entity, float localX, float localY, float localZ, float partialTick) {
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float yawRad = (float) Math.toRadians(yaw);

        // 模型 root rotation = 0，无需补偿
        double worldX = x + (localX * Math.cos(yawRad) - localZ * Math.sin(yawRad));
        double worldZ = z + (localX * Math.sin(yawRad) + localZ * Math.cos(yawRad));
        return new Vec3(worldX, y + localY, worldZ);
    }
}
