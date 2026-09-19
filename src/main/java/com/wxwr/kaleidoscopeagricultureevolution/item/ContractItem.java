package com.wxwr.kaleidoscopeagricultureevolution.item;

import com.wxwr.kaleidoscopeagricultureevolution.entity.FarmerEntity;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

@SuppressWarnings({"deprecation", "removal"})
public class ContractItem extends Item {

    private static final String MSG = "message.kaleidoscope_agriculture_evolution.";
    private static final String KEY_SELECTED = "KAE_SelectedVillager";

    // ---- NBT 键名 ------------------------------------------------------------
    static final String KEY_BOUND_OX      = "BoundOx";
    static final String KEY_CONTRACT_NUM  = "ContractNumber";
    static final String KEY_LAST_POS      = "LastOxPos";       // ox最后已知坐标
    static final String KEY_OX_DEAD_NOTIFIED = "OxDeadNotified"; // 死亡提示已发送

    // 被绑定实体（村民等）persistentData中标记所属耕牛
    static final String KEY_BOUND_OX_PD = "KAE_BoundOx";

    // 玩家持久化数据：下一个可用的契约编号
    private static final String KEY_NEXT_NUM = "KAE_NextContractNumber";

    public ContractItem(Properties properties) {
        super(properties);
    }

    // =========================================================================
    //  显示 — 金色名称 + 附魔光效
    // =========================================================================

    @Override
    public Component getName(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(KEY_CONTRACT_NUM)) {
            return Component.translatable(MSG + "contract_bound_name")
                    .withStyle(ChatFormatting.GOLD);
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(KEY_CONTRACT_NUM)) {
            return true;
        }
        return super.isFoil(stack);
    }

    // =========================================================================
    //  inventoryTick — 手持时发光效果
    // =========================================================================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity,
                              int slotId, boolean isSelected) {
        if (level.isClientSide) return;

        // 主手或副手
        boolean isHeld = isSelected || slotId == Inventory.SLOT_OFFHAND;
        if (!isHeld) return;
        if (!stack.hasTag()) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(KEY_BOUND_OX)) return;

        if (level instanceof ServerLevel serverLevel) {
            Entity oe = serverLevel.getEntity(tag.getUUID(KEY_BOUND_OX));
            if (oe instanceof PlowOxEntity ox && ox.isAlive()) {
                // 更新最后已知位置
                tag.putLong(KEY_LAST_POS, ox.blockPosition().asLong());
                // 清除死亡通知标记（牛重新出现）
                tag.remove(KEY_OX_DEAD_NOTIFIED);

                // 耕牛发光
                ox.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING, 10, 0, false, false));

                // 牛当前绑定的实体（Farmer 工作中 / 已还原的村民）也发光
                UUID boundId = ox.getBoundFarmerUUID();
                if (boundId != null) {
                    Entity be = serverLevel.getEntity(boundId);
                    if (be instanceof LivingEntity living && living.isAlive()) {
                        living.addEffect(new MobEffectInstance(
                                MobEffects.GLOWING, 10, 0, false, false));
                    }
                }
                return;
            }

            // ox未找到 → 检查是否真的死了（区块已加载但实体不存在）
            if (tag.contains(KEY_LAST_POS)) {
                BlockPos lastPos = BlockPos.of(tag.getLong(KEY_LAST_POS));
                if (level.hasChunkAt(lastPos)) {
                    // 区块已加载但牛不在 → 牛真的死了，发一次性提示
                    if (!tag.contains(KEY_OX_DEAD_NOTIFIED)) {
                        tag.putBoolean(KEY_OX_DEAD_NOTIFIED, true);
                        if (entity instanceof Player player) {
                            player.displayClientMessage(
                                    Component.translatable(MSG + "contract_ox_died"), true);
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    //  右键空气 — 检查耕牛是否仍然存活
    // =========================================================================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        // 只有已绑定的契约才能操作
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(KEY_BOUND_OX)) {
            return InteractionResultHolder.pass(stack);
        }

        // 检查耕牛是否还存在
        if (level instanceof ServerLevel serverLevel) {
            Entity e = serverLevel.getEntity(tag.getUUID(KEY_BOUND_OX));
            if (e instanceof PlowOxEntity ox && ox.isAlive()) {
                // 牛还在 → 契约有效，提示去潜行右键牛解绑
                player.displayClientMessage(
                        Component.translatable(MSG + "contract_still_bound"), true);
                return InteractionResultHolder.success(stack);
            }

            // 牛未加载 → 检查最后已知位置的区块状态
            if (tag.contains(KEY_LAST_POS)) {
                BlockPos lastPos = BlockPos.of(tag.getLong(KEY_LAST_POS));
                if (!level.hasChunkAt(lastPos)) {
                    // 区块未加载 → 无法确认生死，不消耗契约
                    player.displayClientMessage(
                            Component.translatable(MSG + "contract_chunk_unloaded"), true);
                    return InteractionResultHolder.success(stack);
                }
            }
        }

        // 区块已加载但牛不存在（或没有位置记录）→ 确认死亡，消耗契约
        stack.shrink(1);
        player.displayClientMessage(
                Component.translatable(MSG + "contract_consumed_gone"), true);
        return InteractionResultHolder.success(stack);
    }

    // =========================================================================
    //  interactLivingEntity — 选中 / 绑定 / 解绑
    // =========================================================================

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        ServerLevel serverLevel = (ServerLevel) level;

        // ===== Shift+右键耕牛 → 解绑 =====
        if (target instanceof PlowOxEntity ox && player.isShiftKeyDown()) {
            CompoundTag tag = stack.getTag();

            // 空白契约不能解绑
            if (tag == null || !tag.hasUUID(KEY_BOUND_OX)) {
                player.displayClientMessage(
                        Component.translatable(MSG + "contract_not_bound"), true);
                return InteractionResult.SUCCESS;
            }

            // 必须匹配此契约记录的耕牛 UUID
            if (!ox.getUUID().equals(tag.getUUID(KEY_BOUND_OX))) {
                player.displayClientMessage(
                        Component.translatable(MSG + "contract_wrong_ox"), true);
                return InteractionResult.SUCCESS;
            }

            unbindAndConsume(ox, stack, player, hand);
            return InteractionResult.SUCCESS;
        }

        // ===== Shift+右键村民/流浪商人 → 选中 =====
        if ((target instanceof Villager || target instanceof WanderingTrader)
                && !(target instanceof FarmerEntity)) {
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            // 已绑定的契约不能再用于新绑定
            if (stack.hasTag() && stack.getTag().hasUUID(KEY_BOUND_OX)) {
                player.displayClientMessage(
                        Component.translatable(MSG + "contract_already_bound"), true);
                return InteractionResult.SUCCESS;
            }

            // 检查该实体是否已被其他耕牛绑定
            if (target.getPersistentData().hasUUID(KEY_BOUND_OX_PD)) {
                player.displayClientMessage(
                        Component.translatable(MSG + "contract_villager_already_bound"), true);
                return InteractionResult.SUCCESS;
            }

            // 清理旧选中UUID，写入新选中（Bug #5 修复）
            player.getPersistentData().remove(KEY_SELECTED);
            player.getPersistentData().putUUID(KEY_SELECTED, target.getUUID());
            player.displayClientMessage(
                    Component.translatable(MSG + "contract_selected"), true);
            return InteractionResult.SUCCESS;
        }

        // ===== 右键耕牛（非潜行）→ 绑定或提示 =====
        if (target instanceof PlowOxEntity ox) {
            if (player.isShiftKeyDown()) return InteractionResult.PASS;

            // 已绑定的契约 → 区分"同一头牛"与"另一头牛"的消息
            if (stack.hasTag() && stack.getTag().hasUUID(KEY_BOUND_OX)) {
                if (ox.getUUID().equals(stack.getTag().getUUID(KEY_BOUND_OX))) {
                    player.displayClientMessage(
                            Component.translatable(MSG + "contract_still_bound"), true);
                } else {
                    player.displayClientMessage(
                            Component.translatable(MSG + "contract_already_bound"), true);
                }
                return InteractionResult.SUCCESS;
            }

            // 空白契约 → 尝试绑定选中的村民
            CompoundTag pdata = player.getPersistentData();
            if (pdata.hasUUID(KEY_SELECTED)) {
                UUID selectedId = pdata.getUUID(KEY_SELECTED);
                Entity selected = serverLevel.getEntity(selectedId);

                if (selected instanceof LivingEntity living && living.isAlive()
                        && (living instanceof Villager || living instanceof WanderingTrader)) {
                    // Bug #2 修复：再次检查村民是否已被其他牛绑定
                    if (living.getPersistentData().hasUUID(KEY_BOUND_OX_PD)) {
                        pdata.remove(KEY_SELECTED);
                        player.displayClientMessage(
                                Component.translatable(MSG + "contract_villager_already_bound"), true);
                        return InteractionResult.SUCCESS;
                    }

                    // 检查目标耕牛是否已有绑定的Farmer
                    if (ox.getBoundFarmerUUID() != null) {
                        player.displayClientMessage(
                                Component.translatable(MSG + "contract_ox_already_bound"), true);
                        return InteractionResult.SUCCESS;
                    }

                    ox.bindFarmer(living);
                    pdata.remove(KEY_SELECTED);

                    // 创建单个绑定契约，只消耗手持堆叠中的1个
                    ItemStack boundStack = new ItemStack(this);
                    CompoundTag contractTag = boundStack.getOrCreateTag();
                    contractTag.putUUID(KEY_BOUND_OX, ox.getUUID());
                    contractTag.putLong(KEY_LAST_POS, ox.blockPosition().asLong());

                    int nextNum = pdata.getInt(KEY_NEXT_NUM);
                    if (nextNum <= 0) nextNum = 1;
                    contractTag.putInt(KEY_CONTRACT_NUM, nextNum);
                    pdata.putInt(KEY_NEXT_NUM, nextNum + 1);

                    // 先添加绑定契约再消耗原始契约，防止 shrink 后 addItem 失败导致契约消失
                    if (!player.addItem(boundStack)) {
                        player.drop(boundStack, false);
                    }
                    stack.shrink(1);
                    if (stack.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);

                    player.displayClientMessage(
                            Component.translatable(MSG + "contract_bound"), true);
                } else {
                    pdata.remove(KEY_SELECTED);
                    player.displayClientMessage(
                            Component.translatable(MSG + "contract_entity_gone"), true);
                }
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(
                    Component.translatable(MSG + "contract_select_first"), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // =========================================================================
    //  辅助方法
    // =========================================================================

    /**
     * 解绑耕牛与农民/村民的绑定关系，同时消耗契约。
     */
    private void unbindAndConsume(PlowOxEntity ox, ItemStack stack, Player player, InteractionHand hand) {
        // 还原活跃 Farmer → 原村民/流浪商人（可能抛异常）
        // 用 try-finally 保证清理和消耗一定执行（Bug #6 修复）
        try {
            ox.tryRevertFarmerToOriginal();
        } finally {
            // 清除耕牛绑定数据（即使在还原过程中抛异常也要清理）
            ox.clearBoundFarmer();
            // 消耗契约（包括创造模式，避免留下指向已解绑牛的僵尸契约）
            stack.shrink(1);
            if (stack.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
            player.displayClientMessage(
                    Component.translatable(MSG + "contract_unbound"), true);
        }
    }
}
