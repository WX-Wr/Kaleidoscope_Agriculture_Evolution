package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemContainerStorage;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemMergePolicy;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemTypeCountView;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowableChecker;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkPathRule;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRuleRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkSeedEntry;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkSeedGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.Set;

/**
 * 耧车（播种机）— 被耕牛拖动的播种农具。
 * 支持两种模式：
 * - 种子模式：存储种子，经过耕地时自动播种
 * - 肥料模式：存储腐肉+骨粉，经过耕地时转化为沃土
 */
public class LoucheEntity extends AbstractDraggableEntity implements ItemTypeCountView {

    // ==================== 库存模式 ====================

    enum InventoryMode { EMPTY, SEED, FERTILIZER }

    // ==================== 库存字段 ====================

    private InventoryMode inventoryMode = InventoryMode.EMPTY;
    private final ItemContainerStorage seedStorage = new ItemContainerStorage(
            MAX_SEED_COUNT, MAX_SEED_COUNT, 64, LoucheEntity::isSeedStack,
            ItemMergePolicy.EXACT, () -> {
    });
    private final ItemContainerStorage fertilizerStorage = new ItemContainerStorage(
            2, MAX_FERTILIZER_PER_TYPE * 2, MAX_FERTILIZER_PER_TYPE,
            LoucheEntity::isFertilizerStack, ItemMergePolicy.EXACT, () -> {
    });
    private int seedIndex = 0;

    // 实体数据：当前种子类型同步到客户端
    private static final EntityDataAccessor<String> SEED_KEY =
            SynchedEntityData.defineId(LoucheEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> INVENTORY_VIEW_KEY =
            SynchedEntityData.defineId(LoucheEntity.class, EntityDataSerializers.STRING);

    // ==================== 常量 ====================

    private static final int MAX_SEED_COUNT = 192;
    private static final int MAX_FERTILIZER_PER_TYPE = 64;
    private static final int MAX_PER_RIGHT_CLICK = 32;

    // ==================== 种子判定（数据驱动） ====================

    /**
     * 不可装入耧车的种子黑名单。
     * 西瓜种子和南瓜种子的 StemBlock 虽然在技术上继承 BushBlock，
     * 但它们成熟后只长果实不替换茎本身，不适合耧车的行播模式。
     */
    private static final Set<Item> SEED_BLACKLIST = Set.of(
            Items.MELON_SEEDS,
            Items.PUMPKIN_SEEDS
    );

    /**
     * 判断一个物品是否是有效的种子。
     * 不依赖 Config 列表 — 所有 CropBlock / StemBlock 的 BlockItem 自动兼容。
     * 黑名单内的物品（如西瓜种子、南瓜种子）会被排除。
     */
    public static boolean isSeedItem(Item item) {
        if (SEED_BLACKLIST.contains(item)) return false;
        if (isBushBlockSeed(item)) return true;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        return itemId != null && WorkRuleRegistry.seedGroups().stream()
                .anyMatch(group -> group.isExplicitlyIncluded(itemId));
    }

    private static boolean isBushBlockSeed(Item item) {
        if (SEED_BLACKLIST.contains(item)) return false;
        return item instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof net.minecraft.world.level.block.BushBlock;
    }

    // ==================== 构造函数 ====================

    public LoucheEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SEED_KEY, "");
        this.entityData.define(INVENTORY_VIEW_KEY, "");
    }

    private void syncSeedToClient() {
        if (!level().isClientSide) {
            Item cur = getCurrentSeed();
            String key = cur != null ? ForgeRegistries.ITEMS.getKey(cur).toString() : "";
            this.entityData.set(SEED_KEY, key);
        }
    }

    private void syncInventoryViewToClient() {
        if (!level().isClientSide) {
            syncSeedToClient();
            this.entityData.set(INVENTORY_VIEW_KEY, serializeItemTypeCounts(getServerItemTypeCounts()));
        }
    }

    private Item getCurrentSeed() {
        ItemStack stack = getCurrentSeedStack();
        return stack.isEmpty() ? null : stack.getItem();
    }

    private ItemStack getCurrentSeedStack() {
        List<ItemStack> seeds = getSeedStacks();
        if (seeds.isEmpty()) return ItemStack.EMPTY;
        return seeds.get(Math.floorMod(seedIndex, seeds.size()));
    }

    private int getTotalSeedCount() {
        return seedStorage.getTotalCount();
    }

    private static boolean isSeedStack(ItemStack stack) {
        return !stack.isEmpty() && isSeedItem(stack.getItem());
    }

    private static boolean isFertilizerStack(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BONE_MEAL));
    }

    private List<ItemStack> getSeedStacks() {
        List<ItemStack> seedStacks = new java.util.ArrayList<>();
        for (ItemStack stack : seedStorage.getItems()) {
            if (!stack.isEmpty()) {
                seedStacks.add(stack.copy());
            }
        }
        return seedStacks;
    }

    @Override
    public List<ItemTypeCount> getItemTypeCounts() {
        if (level().isClientSide) {
            return deserializeItemTypeCounts(this.entityData.get(INVENTORY_VIEW_KEY));
        }
        return getServerItemTypeCounts();
    }

    private List<ItemTypeCount> getServerItemTypeCounts() {
        return switch (inventoryMode) {
            case SEED -> seedStorage.getItemTypeCounts();
            case FERTILIZER -> fertilizerStorage.getItemTypeCounts();
            default -> List.of();
        };
    }

    private static String serializeItemTypeCounts(List<ItemTypeCount> counts) {
        StringBuilder builder = new StringBuilder();
        for (ItemTypeCount count : counts) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(count.item());
            if (key == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(key).append('=').append(count.count());
        }
        return builder.toString();
    }

    private static List<ItemTypeCount> deserializeItemTypeCounts(String value) {
        List<ItemTypeCount> counts = new java.util.ArrayList<>();
        if (value == null || value.isEmpty()) {
            return counts;
        }
        String[] entries = value.split(";");
        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(parts[0]);
            if (id == null) {
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                continue;
            }
            try {
                int count = Integer.parseInt(parts[1]);
                if (count > 0) {
                    counts.add(new ItemTypeCount(item, count));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return counts;
    }

    // ==================== 核心播种行为 ====================

    @Override
    protected void onDraggerMove() {
        if (level().isClientSide) return;

        PlowOxEntity ox = getOx();
        if (ox == null) return;

        WorkAction action = ox.getCurrentWorkAction();
        if (action == WorkAction.TILL) return;
        WorkFieldType fieldType = ox.getCurrentWorkFieldType();
        WorkPathRule pathRule = WorkRuleRegistry.pathRuleFor(fieldType, action);

        if (fieldType == WorkFieldType.PADDY_FIELD) {
            workPaddy(ox, action, pathRule);
            return;
        }

        float yaw = ox.getYRot();
        double rad = Math.toRadians(yaw);

        // 先试牛后方第1格，不可播种时再试第2格，每tick只工作一格
        for (int behind = 1; behind <= 2; behind++) {
            int behindX = Mth.floor(ox.getX() + Math.sin(rad) * behind);
            int behindZ = Mth.floor(ox.getZ() - Math.cos(rad) * behind);

            // 从牛脚部Y坐标向下扫描，找到地表方块
            int scanY = ox.blockPosition().getY();
            while (scanY > level().getMinBuildHeight()
                    && level().isEmptyBlock(new BlockPos(behindX, scanY, behindZ))) {
                scanY--;
            }

            if (scanY <= level().getMinBuildHeight()) continue;

            BlockPos surfacePos = new BlockPos(behindX, scanY, behindZ);

            // 必须在工作区域内
            if (!isInWorkArea(ox, surfacePos)) continue;

            BlockPos plantPos = surfacePos.above();
            BlockState surfaceState = level().getBlockState(surfacePos);
            BlockState plantState = level().getBlockState(plantPos);

            if (!PlowableChecker.isWorkable(level(), plantPos, action, pathRule)) continue;

            // 只在耕地上工作
            if (!(surfaceState.getBlock() instanceof FarmBlock)) continue;

            // 耕地上方必须是空气（已有作物则跳过）
            if (!plantState.isAir()) continue;

            // 检查光照（夜间不工作）
            if (level().getMaxLocalRawBrightness(plantPos) < 8) continue;

            switch (inventoryMode) {
                case SEED -> {
                    if (action == WorkAction.SOW
                            && !seedStorage.isEmpty()
                            && isSeedAllowedForField(getCurrentSeedStack(), fieldType)) {
                        plantSeed(plantPos);
                    }
                }
                case FERTILIZER -> {
                    if (action == WorkAction.FERTILIZE
                            && isFertilizerTarget(surfaceState)) {
                        convertToRichSoil(surfacePos, surfaceState);
                    }
                }
                default -> {}
            }
            break; // 找到并工作了一格，本tick不再处理
        }
    }

    private void workPaddy(PlowOxEntity ox, WorkAction action, WorkPathRule pathRule) {
        float yaw = ox.getYRot();
        double rad = Math.toRadians(yaw);
        for (int behind = 1; behind <= 2; behind++) {
            int behindX = Mth.floor(ox.getX() + Math.sin(rad) * behind);
            int behindZ = Mth.floor(ox.getZ() - Math.cos(rad) * behind);
            BlockPos waterPos = new BlockPos(behindX, ox.blockPosition().getY(), behindZ);
            if (!PlowableChecker.isWorkable(level(), waterPos, action, pathRule)) continue;
            if (level().getMaxLocalRawBrightness(waterPos) < 8) continue;

            if (action == WorkAction.SOW) {
                if (!seedStorage.isEmpty()
                        && isSeedAllowedForField(getCurrentSeedStack(), WorkFieldType.PADDY_FIELD)
                        && plantSeed(waterPos, WorkFieldType.PADDY_FIELD, true)) {
                    break;
                }
            } else if (action == WorkAction.FERTILIZE
                    && convertToRichSoil(waterPos.below(), level().getBlockState(waterPos.below()))) {
                break;
            }
        }
    }

    // ==================== 播种 ====================

    private void plantSeed(BlockPos pos) {
        plantSeed(pos, null, false);
    }

    private boolean plantSeed(BlockPos pos, WorkFieldType fieldType, boolean waterCrop) {
        ItemStack seedStack = getCurrentSeedStack();
        if (seedStack.isEmpty()
                || (fieldType != null && !isSeedAllowedForField(seedStack, fieldType))) {
            return false;
        }
        Item seed = seedStack.getItem();

        Block cropBlock = resolveCropBlock(seedStack, fieldType);
        if (cropBlock == null) return false;

        BlockState cropState = cropBlock.defaultBlockState();
        if (waterCrop) {
            if (!level().getBlockState(pos).getFluidState().is(FluidTags.WATER)
                    || !cropState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                return false;
            }
            cropState = cropState.setValue(BlockStateProperties.WATERLOGGED, true);
        }
        level().setBlock(pos, cropState, 3);

        level().playSound(null, pos,
                SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.6F, 1.0F);

        seedStorage.removeMatching(stack -> ItemStack.isSameItemSameTags(stack, seedStack), 1);
        if (seedStorage.getCount(stack -> ItemStack.isSameItemSameTags(stack, seedStack)) <= 0) {
            List<ItemStack> seedStacks = getSeedStacks();
            if (seedStacks.isEmpty()) {
                seedIndex = 0;
                inventoryMode = InventoryMode.EMPTY;
            } else {
                seedIndex = seedIndex % seedStacks.size();
            }
        }
        syncInventoryViewToClient();
        return true;
    }

    private static Block resolveCropBlock(ItemStack seedStack, WorkFieldType fieldType) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(seedStack.getItem());
        if (itemId != null && fieldType != null) {
            WorkSeedGroup group = WorkRuleRegistry.findSeedGroup(fieldType, WorkAction.SOW).orElse(null);
            if (group != null) {
                WorkSeedEntry entry = group.findEntry(itemId).orElse(null);
                if (entry != null && entry.crop() != null) {
                    Block configured = ForgeRegistries.BLOCKS.getValue(entry.crop());
                    if (configured != null) return configured;
                }
            }
        }
        return seedStack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    public static boolean isSeedAllowedForField(ItemStack stack, WorkFieldType fieldType) {
        if (stack.isEmpty()) return false;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return false;

        WorkSeedGroup group = WorkRuleRegistry.findSeedGroup(fieldType, WorkAction.SOW).orElse(null);
        if (group == null) return isSeedItem(stack.getItem());
        if (group.isExcluded(itemId)) return false;
        return group.isExplicitlyIncluded(itemId)
                || (group.includeBushBlockSeeds() && isBushBlockSeed(stack.getItem()));
    }

    // ==================== 沃土转化 ====================

    private static Block cachedRichSoilFarmland = null;
    private static Block cachedRichSoil = null;
    private static boolean richSoilLookupDone = false;

    private boolean convertToRichSoil(BlockPos belowPos, BlockState below) {
        // 只转化原版耕地（已是沃土则不转）
        if (!below.is(Blocks.FARMLAND)) return false;

        // 需要两种材料
        if (fertilizerStorage.getCount(Items.ROTTEN_FLESH) < 1
                || fertilizerStorage.getCount(Items.BONE_MEAL) < 1) return false;

        // 查找农夫乐事沃土耕地
        if (!below.is(Blocks.FARMLAND) && !isRichSoil(below)) return false;
        if (fertilizerStorage.getCount(Items.ROTTEN_FLESH) < 1
                || fertilizerStorage.getCount(Items.BONE_MEAL) < 1) return false;

        Block richSoilFarmland = getRichSoilFarmland();
        if (richSoilFarmland == null) return false;

        // 继承湿度
        BlockState newState = richSoilFarmland.defaultBlockState();
        if (newState.hasProperty(FarmBlock.MOISTURE) && below.hasProperty(FarmBlock.MOISTURE)) {
            int moisture = below.getValue(FarmBlock.MOISTURE);
            newState = newState.setValue(FarmBlock.MOISTURE, moisture);
        }
        level().setBlock(belowPos, newState, 3);

        level().playSound(null, belowPos,
                SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.8F, 1.0F);

        fertilizerStorage.removeMatching(stack -> stack.is(Items.ROTTEN_FLESH), 1);
        fertilizerStorage.removeMatching(stack -> stack.is(Items.BONE_MEAL), 1);

        if (fertilizerStorage.isEmpty()) {
            clearInventory();
        } else {
            syncInventoryViewToClient();
        }
        return true;
    }

    private static Block getRichSoilFarmland() {
        if (!richSoilLookupDone) {
            richSoilLookupDone = true;
            cachedRichSoilFarmland = ForgeRegistries.BLOCKS.getValue(
                    KaleidoscopeAgricultureEvolution.rl("farmersdelight", "rich_soil_farmland"));
            cachedRichSoil = ForgeRegistries.BLOCKS.getValue(
                    KaleidoscopeAgricultureEvolution.rl("farmersdelight", "rich_soil"));
            if (cachedRichSoilFarmland == null) {
                // 回退：尝试 rich_soil（基础方块）
                cachedRichSoilFarmland = ForgeRegistries.BLOCKS.getValue(
                        KaleidoscopeAgricultureEvolution.rl("farmersdelight", "rich_soil"));
            }
            if (cachedRichSoilFarmland == null) {
                KaleidoscopeAgricultureEvolution.LOGGER.warn(
                        "Farmer's Delight rich_soil_farmland not found — louche fertilizer mode disabled");
            }
        }
        return cachedRichSoilFarmland;
    }

    private static Block getRichSoil() {
        getRichSoilFarmland();
        return cachedRichSoil;
    }

    private static boolean isRichSoil(BlockState state) {
        Block richSoil = getRichSoil();
        return richSoil != null && state.is(richSoil);
    }

    private static boolean isFertilizerTarget(BlockState state) {
        return state.is(Blocks.FARMLAND) || isRichSoil(state);
    }

    // ==================== 库存管理 ====================

    private void clearInventory() {
        inventoryMode = InventoryMode.EMPTY;
        seedStorage.clear();
        fertilizerStorage.clear();
        seedIndex = 0;
        syncInventoryViewToClient();
    }

    /**
     * 取出耧车中所有物品（种子 + 肥料），返回 ItemStack 列表并清空库存。
     * 供 Farmer 在劳作结束后回收用。
     */
    public java.util.List<ItemStack> retrieveAllItems() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        if (level().isClientSide) return items;
        items.addAll(seedStorage.drainNonEmpty());
        items.addAll(fertilizerStorage.drainNonEmpty());
        clearInventory();
        return items;
    }

    /** 农夫调用：切换到下一种种子 */
    public void resetSeedType() {
        int seedStackCount = getSeedStacks().size();
        if (seedStackCount > 1) {
            seedIndex = (seedIndex + 1) % seedStackCount;
            syncInventoryViewToClient();
        }
    }

    /** 农夫调用：获取当前播种槽索引 */
    public int getSeedIndex() {
        return seedIndex;
    }

    /** 农夫调用：获取种子槽总数 */
    public int getSeedSlotCount() {
        return getSeedStacks().size();
    }

    /**
     * 农夫调用：获取指定槽的种子物品。
     * @return 该槽的种子 Item，索引无效或 slot 不存在则返回 null
     */
    @org.jetbrains.annotations.Nullable
    public Item getSeedSlotItem(int slotIndex) {
        List<ItemStack> seedStacks = getSeedStacks();
        if (slotIndex < 0 || slotIndex >= seedStacks.size()) return null;
        return seedStacks.get(slotIndex).getItem();
    }

    /**
     * 农夫调用：切换到指定种子槽。
     * 只在槽索引有效且与当前不同时才执行。
     */
    public void switchToSeedSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= getSeedStacks().size()) return;
        if (slotIndex == seedIndex) return;
        seedIndex = slotIndex;
        syncInventoryViewToClient();
    }

    // ==================== 农夫 API ====================

    public Item getSeedItem() {
        if (level().isClientSide) {
            String key = this.entityData.get(SEED_KEY);
            if (key.isEmpty()) return null;
            return ForgeRegistries.ITEMS.getValue(KaleidoscopeAgricultureEvolution.id(key));
        }
        return getCurrentSeed();
    }
    public int getSeedCount() { return getTotalSeedCount(); }
    public int getMaxSeedCount() { return MAX_SEED_COUNT; }
    public boolean isModeEmpty() { return inventoryMode == InventoryMode.EMPTY; }
    public boolean isModeSeed() { return inventoryMode == InventoryMode.SEED; }
    public boolean isModeFertilizer() { return inventoryMode == InventoryMode.FERTILIZER; }

    public int getFertilizerCount(Item item) {
        return fertilizerStorage.getCount(item);
    }

    /**
     * 核心装载逻辑：尝试将种子加入库存（不处理 ItemStack shrink）。
     * @return 实际添加的数量，0 表示已满或无效
     */
    private int addSeedsInternal(ItemStack stack, int count) {
        int total = getTotalSeedCount();
        if (total >= MAX_SEED_COUNT) return 0;
        int toAdd = Math.min(count, MAX_SEED_COUNT - total);
        ItemStack remaining = seedStorage.insert(stack.copyWithCount(toAdd));
        int added = toAdd - remaining.getCount();
        if (added > 0) {
            inventoryMode = InventoryMode.SEED;
            syncInventoryViewToClient();
        }
        return added;
    }

    /** 农夫调用：将种子装入耧车，会自动 shrink 物品 */
    public int tryLoadSeed(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item item = stack.getItem();
        if (!isSeedItem(item)) return 0;
        if (inventoryMode == InventoryMode.FERTILIZER) return 0;

        int added = addSeedsInternal(stack, Math.min(stack.getCount(), MAX_PER_RIGHT_CLICK));
        if (added > 0) stack.shrink(added);
        return added;
    }

    public int tryLoadFertilizer(ItemStack stack) {
        if (stack.isEmpty() || !isFertilizerStack(stack) || inventoryMode == InventoryMode.SEED) {
            return 0;
        }
        Item item = stack.getItem();
        int current = fertilizerStorage.getCount(item);
        int capacity = Math.max(0, MAX_FERTILIZER_PER_TYPE - current);
        int toAdd = Math.min(stack.getCount(), capacity);
        if (toAdd <= 0) return 0;

        ItemStack remaining = fertilizerStorage.insert(stack.copyWithCount(toAdd));
        int added = toAdd - remaining.getCount();
        if (added > 0) {
            inventoryMode = InventoryMode.FERTILIZER;
            stack.shrink(added);
            syncInventoryViewToClient();
        }
        return added;
    }

    // ==================== 右键交互 ====================

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack handItem = player.getItemInHand(hand);

        // 服务端处理
        if (level().isClientSide) {
            // 客户端：对所有可处理的情况返回 SUCCESS
            if (handItem.isEmpty()
                    || isSeedItem(handItem.getItem())
                    || handItem.is(Items.ROTTEN_FLESH)
                    || handItem.is(Items.BONE_MEAL)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // === 空手 → shift 取出全部 / 不 shift 显示状态 ===
        if (handItem.isEmpty()) {
            if (player.isShiftKeyDown()) {
                dropAllItems();
                return InteractionResult.SUCCESS;
            }
            showStatus(player);
            return InteractionResult.SUCCESS;
        }

        Item item = handItem.getItem();

        // === 种子 ===
        if (isSeedItem(item)) {
            if (inventoryMode == InventoryMode.FERTILIZER) {
                return InteractionResult.SUCCESS;
            }

            int added = addSeedsInternal(handItem, Math.min(handItem.getCount(), MAX_PER_RIGHT_CLICK));
            if (added <= 0) {
                player.displayClientMessage(
                        Component.translatable(
                                "message.kaleidoscope_agriculture_evolution.louche_seed_full",
                                MAX_SEED_COUNT), true);
                return InteractionResult.SUCCESS;
            }

            if (!player.isCreative()) handItem.shrink(added);
            player.displayClientMessage(
                    Component.translatable(
                            "message.kaleidoscope_agriculture_evolution.louche_seed_added",
                            item.getDescription(), getTotalSeedCount()), true);
            return InteractionResult.SUCCESS;
        }

        // === 腐肉 ===
        if (item == Items.ROTTEN_FLESH) {
            return addFertilizer(player, handItem, true);
        }

        // === 骨粉 ===
        if (item == Items.BONE_MEAL) {
            return addFertilizer(player, handItem, false);
        }

        return InteractionResult.PASS;
    }

    private InteractionResult addFertilizer(Player player, ItemStack handItem, boolean isFlesh) {
        if (inventoryMode == InventoryMode.SEED) {
            return InteractionResult.SUCCESS;
        }

        Item item = isFlesh ? Items.ROTTEN_FLESH : Items.BONE_MEAL;
        int currentCount = fertilizerStorage.getCount(item);
        int toAdd = Math.min(handItem.getCount(), MAX_PER_RIGHT_CLICK);
        int newTotal = currentCount + toAdd;
        int actuallyAdded = toAdd;
        if (newTotal > MAX_FERTILIZER_PER_TYPE) {
            actuallyAdded = MAX_FERTILIZER_PER_TYPE - currentCount;
        }

        if (actuallyAdded <= 0) {
            String typeName = isFlesh ? Items.ROTTEN_FLESH.getDescription().getString() : Items.BONE_MEAL.getDescription().getString();
            player.displayClientMessage(
                    Component.translatable(
                            "message.kaleidoscope_agriculture_evolution.louche_fertilizer_full",
                            typeName, MAX_FERTILIZER_PER_TYPE), true);
            return InteractionResult.SUCCESS;
        }

        String itemName = handItem.getItem().getDescription().getString(); // 在 shrink 前保存

        ItemStack remaining = fertilizerStorage.insert(new ItemStack(item, actuallyAdded));
        actuallyAdded -= remaining.getCount();
        if (actuallyAdded <= 0) {
            return InteractionResult.CONSUME;
        }

        inventoryMode = InventoryMode.FERTILIZER;
        if (!player.isCreative()) handItem.shrink(actuallyAdded);
        syncInventoryViewToClient();
        player.displayClientMessage(
                Component.translatable(
                        "message.kaleidoscope_agriculture_evolution.louche_fertilizer_added",
                    itemName, fertilizerStorage.getCount(Items.ROTTEN_FLESH),
                    fertilizerStorage.getCount(Items.BONE_MEAL)), true);
        return InteractionResult.SUCCESS;
    }

    private void showStatus(Player player) {
        switch (inventoryMode) {
            case SEED -> {
                StringBuilder sb = new StringBuilder();
                int total = 0;
                List<ItemStack> seedStacks = getSeedStacks();
                for (int i = 0; i < seedStacks.size(); i++) {
                    ItemStack stack = seedStacks.get(i);
                    int count = seedStorage.getCount(current -> ItemStack.isSameItemSameTags(current, stack));
                    sb.append(stack.getItem().getDescription().getString()).append(" x").append(count);
                    if (i == seedIndex) sb.append(" ◀");
                    if (i < seedStacks.size() - 1) sb.append(", ");
                    total += count;
                }
                player.displayClientMessage(Component.literal("§a耧车: " + sb + "（共" + total + "颗）"), false);
            }
            case FERTILIZER -> player.displayClientMessage(
                    Component.translatable(
                            "message.kaleidoscope_agriculture_evolution.louche_status_fertilizer",
                            fertilizerStorage.getCount(Items.ROTTEN_FLESH),
                            fertilizerStorage.getCount(Items.BONE_MEAL)), true);
            default -> player.displayClientMessage(
                    Component.translatable(
                            "message.kaleidoscope_agriculture_evolution.louche_status_empty"), true);
        }
    }

    private void dropAllItems() {
        if (level().isClientSide) return;
        for (ItemStack stack : seedStorage.drainNonEmpty()) {
            spawnAtLocation(stack);
        }
        for (ItemStack stack : fertilizerStorage.drainNonEmpty()) {
            spawnAtLocation(stack);
        }
        clearInventory();
    }

    // ==================== 可覆盖参数 ====================

    @Override
    protected ItemStack getDropItem() {
        ItemStack stack = new ItemStack(ModBlocks.LOUCHE.get().asItem());
        CompoundTag inv = new CompoundTag();
        saveInventory(inv);
        if (!inv.isEmpty()) stack.getOrCreateTag().put("LoucheInv", inv);
        return stack;
    }

    @Override
    public boolean shouldRenderRope() { return false; }

    @Override
    protected double getFollowDistance() { return 0.9; }

    @Override
    protected double getMinWorkSpeed() { return 0.02; }

    // ==================== NBT 持久化 ====================

    // 通用 key 常量 — 实体持久化与物品掉落共用
    private static final String NBT_SEED_ITEMS = "SeedItems";
    private static final String NBT_FERTILIZER_ITEMS = "FertilizerItems";
    private static final String NBT_SEED_INDEX = "SeedIndex";

    /** 写入库存到 tag，通过 key 参数区分实体持久化 / 物品掉落 */
    private void writeInventory(CompoundTag tag, String modeKey) {
        tag.putString(modeKey, inventoryMode.name());
        if (!seedStorage.isEmpty()) {
            seedStorage.save(tag, NBT_SEED_ITEMS);
            tag.putInt(NBT_SEED_INDEX, seedIndex);
        }
        if (!fertilizerStorage.isEmpty()) {
            fertilizerStorage.save(tag, NBT_FERTILIZER_ITEMS);
        }
    }

    /** 从 tag 读取库存，先完整重置再反序列化，避免残留脏数据 */
    private void readInventory(CompoundTag tag, String modeKey) {
        seedStorage.clear();
        fertilizerStorage.clear();
        seedIndex = 0;

        if (!tag.contains(modeKey)) {
            inventoryMode = InventoryMode.EMPTY;
            syncInventoryViewToClient();
            return;
        }

        try {
            inventoryMode = InventoryMode.valueOf(tag.getString(modeKey));
        } catch (IllegalArgumentException e) {
            inventoryMode = InventoryMode.EMPTY;
            syncInventoryViewToClient();
            return;
        }

        switch (inventoryMode) {
            case SEED -> {
                seedStorage.load(tag, NBT_SEED_ITEMS);
                if (seedStorage.isEmpty()) {
                    inventoryMode = InventoryMode.EMPTY;
                    seedIndex = 0;
                } else {
                    seedIndex = Math.floorMod(tag.getInt(NBT_SEED_INDEX), getSeedStacks().size());
                }
            }
            case FERTILIZER -> {
                fertilizerStorage.load(tag, NBT_FERTILIZER_ITEMS);
                if (fertilizerStorage.isEmpty()) {
                    inventoryMode = InventoryMode.EMPTY;
                }
            }
            default -> inventoryMode = InventoryMode.EMPTY;
        }
        syncInventoryViewToClient();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeInventory(tag, "InventoryMode");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readInventory(tag, "InventoryMode");
    }

    // ==================== 破坏掉落 ====================

    private void saveInventory(CompoundTag tag) {
        writeInventory(tag, "Mode");
    }

    public void restoreFromTag(CompoundTag tag) {
        readInventory(tag, "Mode");
    }

    @Override
    protected void destroy() {
        super.destroy();
    }

    // ==================== 绳子连接点 ====================

    // 模型坐标（pixel），除以 16 转换为方块坐标
    private static final float LOUCHE_LEFT_X  = -0.5f;    // -8/16
    private static final float LOUCHE_RIGHT_X =  0.5f;    //  8/16
    private static final float LOUCHE_Y       =  0.375f;  //  6/16
    private static final float LOUCHE_Z       = -0.125f;  // -2/16

    @Override
    public Vec3 getLeftRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, LOUCHE_LEFT_X, LOUCHE_Y, LOUCHE_Z, partialTick);
    }

    @Override
    public Vec3 getRightRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, LOUCHE_RIGHT_X, LOUCHE_Y, LOUCHE_Z, partialTick);
    }

    private static Vec3 calculateAttachPoint(Entity entity, float localX, float localY,
                                             float localZ, float partialTick) {
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float yawRad = (float) Math.toRadians(yaw);

        // 模型 root rotation = 0（与犁不同，耧车无需 180° 补偿）
        double worldX = x + (localX * Math.cos(yawRad) - localZ * Math.sin(yawRad));
        double worldZ = z + (localX * Math.sin(yawRad) + localZ * Math.cos(yawRad));
        return new Vec3(worldX, y + localY, worldZ);
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::predicate));
    }

    private PlayState predicate(AnimationState<LoucheEntity> state) {
        if (isWorking() && this.getDeltaMovement().horizontalDistance() > getMinWorkSpeed()) {
            state.getController().setAnimation(
                    RawAnimation.begin().thenLoop("animation.louche.working"));
        } else {
            state.getController().setAnimation(
                    RawAnimation.begin().thenLoop("animation.louche.idle"));
        }
        return PlayState.CONTINUE;
    }
}
