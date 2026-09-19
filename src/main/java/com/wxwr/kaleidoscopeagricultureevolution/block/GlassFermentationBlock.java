package com.wxwr.kaleidoscopeagricultureevolution.block;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.FermentationContainerBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.ModBlockEntities;
import com.wxwr.kaleidoscopeagricultureevolution.recipe.ContainerRecipeConfig;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GlassFermentationBlock extends BaseEntityBlock {
    public static final EnumProperty<FermentationContent> CONTENT = EnumProperty.create("content", FermentationContent.class);
    public static final IntegerProperty FILL_LEVEL = IntegerProperty.create("fill_level", 0, 3);
    public static final EnumProperty<FermentationTapState> TAP = EnumProperty.create("tap", FermentationTapState.class);
    public static final EnumProperty<GlassFermentationBottleState> BOTTLE =
            EnumProperty.create("bottle", GlassFermentationBottleState.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int MAX_FILL_LEVEL = 3;
    public static final int MAX_NON_LIQUID_ITEMS = 5;
    public static final int APPLE_VINEGAR_BOTTLES_PER_STAGE = 1;
    private static final String TAVERN_MOD_ID = "kaleidoscope_tavern";
    private static final String TAVERN_TAP_ID = "tap";
    private static final VoxelShape BODY_SHAPE = Block.box(3.5D, 0.0D, 3.5D, 12.5D, 16.0D, 12.5D);
    private static final VoxelShape TAP_SHAPE = Block.box(6.5D, 3.75D, -1.25D, 9.5D, 7.25D, 3.5D);
    private static final VoxelShape BOTTLE_SHAPE = Block.box(5.2D, 0.0D, -1.5D, 9.0D, 3.05D, 0.5D);

    public GlassFermentationBlock() {
        super(Properties.of()
                .strength(1.0F)
                .sound(SoundType.GLASS)
                .noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(CONTENT, FermentationContent.EMPTY)
                .setValue(FILL_LEVEL, 0)
                .setValue(TAP, FermentationTapState.NONE)
                .setValue(BOTTLE, GlassFermentationBottleState.NONE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONTENT, FILL_LEVEL, TAP, BOTTLE, FACING);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                 @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand,
                                          @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        Vec3 hitLocation = hit.getLocation();
        boolean bodyTarget = isBodyTarget(state, pos, hitLocation);
        boolean tapTarget = isTapTarget(state, pos, hitLocation);
        if (!bodyTarget && !tapTarget) {
            return InteractionResult.PASS;
        }

        InteractionResult bottleResult = tryUseBottleUnderTap(state, level, pos, player, hand, held, tapTarget);
        if (bottleResult != InteractionResult.PASS) {
            return bottleResult;
        }

        InteractionResult glassPreservingResult = tryUseGlassPreserving(state, level, pos, player, hand, held);
        if (glassPreservingResult != InteractionResult.PASS) {
            return glassPreservingResult;
        }

        InteractionResult appleVinegarResult = tryUseAppleVinegarBrewing(state, level, pos, player, hand, held);
        if (appleVinegarResult != InteractionResult.PASS) {
            return appleVinegarResult;
        }

        InteractionResult liquidResult = FermentationLiquidInteractions.tryUseLiquid(state, level, pos, player, hand,
                CONTENT, FILL_LEVEL, MAX_FILL_LEVEL);
        if (liquidResult != InteractionResult.PASS) {
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container) {
                BlockState nextState = level.getBlockState(pos);
                tryStartAppleVinegarBrewing(level, nextState, container);
                nextState = level.getBlockState(pos);
                tryStartGlassPreserving(level, nextState, container);
            }
            return liquidResult;
        }

        InteractionResult statusResult = tryShowFermentationStatus(state, level, pos, player, held);
        if (statusResult != InteractionResult.PASS) {
            return statusResult;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult tryUseGlassPreserving(BlockState state, Level level, BlockPos pos,
                                                           Player player, InteractionHand hand, ItemStack held) {
        if (player.isShiftKeyDown() || !isGlassPreservingIngredient(held)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container)) {
            return InteractionResult.PASS;
        }
        if (container.isGlassPreserving()) {
            ContainerRecipeConfig.GlassRecipeDefinition active = glassRecipe(container.getGlassPreservingRecipeId());
            return active != null && active.ingredientFor(held).isPresent()
                    ? InteractionResult.sidedSuccess(level.isClientSide)
                    : InteractionResult.PASS;
        }
        FermentationContent content = state.getValue(CONTENT);
        if (content != FermentationContent.EMPTY && content != FermentationContent.HONEY) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            ContainerRecipeConfig.GlassRecipeDefinition recipe = matchingGlassPreservingRecipe(state, container, held);
            if (recipe == null) {
                return InteractionResult.PASS;
            }
            ItemStack inserted = normalizeIngredient(recipe, held);
            ItemStack remaining = container.addNonLiquidItem(inserted);
            if (remaining.isEmpty()) {
                if (!player.getAbilities().instabuild) {
                    consumeGlassPreservingIngredient(player, hand, held);
                }
                tryStartGlassPreserving(level, state, container);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void tryStartGlassPreserving(Level level, BlockState state,
                                                FermentationContainerBlockEntity container) {
        for (ContainerRecipeConfig.GlassRecipeDefinition recipe : glassPreservingRecipes()) {
            if (container.canStartGlassPreserving(recipe.id(), state)) {
                container.startGlassPreserving(level, recipe.id(), recipe.durationTicks());
                return;
            }
        }
    }

    private static boolean isGlassPreservingIngredient(ItemStack stack) {
        return glassPreservingRecipes().stream().anyMatch(recipe -> recipe.ingredientFor(stack).isPresent());
    }

    private static boolean isSugarIngredient(ItemStack stack) {
        return stack.is(ModItems.PORTION_SUGAR.get()) || stack.is(Items.SUGAR);
    }

    private static ItemStack oneSugarPortion() {
        return ((DurablePortionItem) ModItems.PORTION_SUGAR.get()).withPortions(1);
    }

    private static void consumeGlassPreservingIngredient(Player player, InteractionHand hand, ItemStack held) {
        if (held.getItem() instanceof DurablePortionItem) {
            player.setItemInHand(hand, DurablePortionItem.consume(held, 1));
            return;
        }
        held.shrink(1);
    }

    private static InteractionResult tryUseBottleUnderTap(BlockState state, Level level, BlockPos pos,
                                                          Player player, InteractionHand hand, ItemStack held,
                                                          boolean tapTarget) {
        if (player.isShiftKeyDown() || state.getValue(TAP) == FermentationTapState.NONE) {
            return InteractionResult.PASS;
        }

        GlassFermentationBottleState bottle = state.getValue(BOTTLE);
        if (bottle == GlassFermentationBottleState.NONE) {
            if (tapTarget || !held.is(Items.GLASS_BOTTLE)) {
                return InteractionResult.PASS;
            }
            if (!held.is(Items.GLASS_BOTTLE)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state
                        .setValue(TAP, FermentationTapState.CLOSE)
                        .setValue(BOTTLE, GlassFermentationBottleState.EMPTY_CLOSE), Block.UPDATE_ALL);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!held.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (bottle.isClosed()) {
            if (tapTarget) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(BOTTLE, bottle.open()), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (tapTarget && state.getValue(TAP) == FermentationTapState.OPEN && bottle.isFilled()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(TAP, FermentationTapState.CLOSE), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        FermentationContent content = state.getValue(CONTENT);
        if (tapTarget && bottle == GlassFermentationBottleState.EMPTY_OPEN
                && content != FermentationContent.EMPTY
                && state.getValue(FILL_LEVEL) > 0) {
            if (!level.isClientSide) {
                BlockState drainedState = drainOneLiquidIntoBottle(state, level, pos);
                level.setBlock(pos, drainedState
                        .setValue(TAP, FermentationTapState.OPEN)
                        .setValue(BOTTLE, GlassFermentationBottleState.fromContent(content, true)), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!tapTarget && state.getValue(TAP) == FermentationTapState.CLOSE && bottle.isOpen()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(BOTTLE, bottle.close()), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return bottle.hasBottle() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    public static InteractionResult tryTakeBottleUnderTap(BlockState state, Level level, BlockPos pos,
                                                          Player player, InteractionHand hand, Vec3 hitLocation) {
        if (!player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!isBodyTarget(state, pos, hitLocation)) {
            return InteractionResult.PASS;
        }

        GlassFermentationBottleState bottle = state.getValue(BOTTLE);
        if (!bottle.hasBottle()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            ItemStack bottleStack = filledBottleItem(bottle);
            if (bottleStack.isEmpty() && bottle.toContent() == FermentationContent.EMPTY) {
                bottleStack = new ItemStack(Items.GLASS_BOTTLE);
            }
            giveItem(player, bottleStack);
            level.setBlock(pos, state
                    .setValue(TAP, state.getValue(TAP) == FermentationTapState.NONE
                            ? FermentationTapState.NONE
                            : FermentationTapState.CLOSE)
                    .setValue(BOTTLE, GlassFermentationBottleState.NONE), Block.UPDATE_ALL);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static BlockState drainOneLiquidIntoBottle(BlockState state, Level level, BlockPos pos) {
        FermentationContent content = state.getValue(CONTENT);
        int fillLevel = state.getValue(FILL_LEVEL);
        int nextFillLevel;

        if (content == FermentationContent.APPLE_VINEGAR) {
            nextFillLevel = drainAppleVinegarLevel(state, level, pos);
        } else {
            nextFillLevel = fillLevel - 1;
        }

        return nextFillLevel <= 0
                ? state.setValue(CONTENT, FermentationContent.EMPTY).setValue(FILL_LEVEL, 0)
                : state.setValue(FILL_LEVEL, nextFillLevel);
    }

    private static int drainAppleVinegarLevel(BlockState state, Level level, BlockPos pos) {
        int remainingBottles;
        boolean usesStoredBottleCount = false;
        if (level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container
                && container.getAppleVinegarBottles() > 0) {
            remainingBottles = container.takeAppleVinegarBottle();
            usesStoredBottleCount = true;
        } else {
            remainingBottles = Math.max(0, state.getValue(FILL_LEVEL) - 1);
        }

        return usesStoredBottleCount
                ? Math.min(MAX_FILL_LEVEL, (remainingBottles + APPLE_VINEGAR_BOTTLES_PER_STAGE - 1)
                        / APPLE_VINEGAR_BOTTLES_PER_STAGE)
                : remainingBottles;
    }

    private static ItemStack filledBottleItem(GlassFermentationBottleState bottle) {
        return switch (bottle.toContent()) {
            case WATER -> ((DurablePortionItem) ModItems.PORTION_WATER_BOTTLE.get()).withPortions(1);
            case VINEGAR -> ((DurablePortionItem) ModItems.PORTION_VINEGAR.get()).withPortions(1);
            case HONEY -> ((DurablePortionItem) ModItems.PORTION_HONEY_BOTTLE.get()).withPortions(1);
            case YEAST -> ((DurablePortionItem) ModItems.PORTION_YEAST.get()).withPortions(1);
            case APPLE_VINEGAR -> ((DurablePortionItem) ModItems.PORTION_APPLE_VINEGAR.get()).withPortions(1);
            case EMPTY, RICE -> ItemStack.EMPTY;
        };
    }

    private static InteractionResult tryUseAppleVinegarBrewing(BlockState state, Level level, BlockPos pos,
                                                               Player player, InteractionHand hand, ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container)) {
            return InteractionResult.PASS;
        }

        if (container.isAppleVinegarFermenting()) {
            return isAppleVinegarInteractionItem(held)
                    ? InteractionResult.sidedSuccess(level.isClientSide)
                    : InteractionResult.PASS;
        }

        if (!player.isShiftKeyDown() && canInsertAppleVinegarIngredient(state, container, held)) {
            if (!level.isClientSide) {
                ItemStack inserted = normalizeAppleVinegarIngredient(held);
                ItemStack remaining = container.addNonLiquidItem(inserted);
                if (remaining.isEmpty() && !player.getAbilities().instabuild) {
                    consumeAppleVinegarIngredient(player, hand, held);
                }
                if (remaining.isEmpty()) {
                    tryStartAppleVinegarBrewing(level, state, container);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return shouldHoldAppleVinegarInteraction(state, container, held)
                ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    private static boolean canInsertAppleVinegarIngredient(BlockState state, FermentationContainerBlockEntity container,
                                                           ItemStack stack) {
        ContainerRecipeConfig.GlassRecipeDefinition recipe = appleVinegarRecipe();
        if (recipe == null || state.getValue(CONTENT) == FermentationContent.APPLE_VINEGAR) {
            return false;
        }
        return recipe.canAccept(stack, container);
    }

    private static void tryStartAppleVinegarBrewing(Level level, BlockState state,
                                                    FermentationContainerBlockEntity container) {
        ContainerRecipeConfig.GlassRecipeDefinition recipe = appleVinegarRecipe();
        if (recipe == null || !canStartAppleVinegar(state, container)) {
            return;
        }
        container.startAppleVinegarFermentation(level, recipe.maxFillLevel());
    }

    private static boolean canStartAppleVinegar(BlockState state, FermentationContainerBlockEntity container) {
        ContainerRecipeConfig.GlassRecipeDefinition recipe = appleVinegarRecipe();
        return recipe != null
                && recipe.matchesState(state.getValue(CONTENT), state.getValue(FILL_LEVEL))
                && recipe.hasIngredients(container);
    }

    private static boolean isAppleVinegarInteractionItem(ItemStack stack) {
        ContainerRecipeConfig.GlassRecipeDefinition recipe = appleVinegarRecipe();
        return recipe != null && recipe.ingredientFor(stack).isPresent();
    }

    private static boolean shouldHoldAppleVinegarInteraction(BlockState state, FermentationContainerBlockEntity container,
                                                             ItemStack stack) {
        return isAppleVinegarInteractionItem(stack);
    }

    private static ItemStack normalizeIngredient(ContainerRecipeConfig.GlassRecipeDefinition recipe, ItemStack held) {
        return recipe.ingredientFor(held).map(ingredient -> ingredient.normalizeOne(held)).orElse(held.copyWithCount(1));
    }

    private static ItemStack normalizeAppleVinegarIngredient(ItemStack held) {
        ContainerRecipeConfig.GlassRecipeDefinition recipe = appleVinegarRecipe();
        return recipe == null
                ? held.copyWithCount(1)
                : recipe.ingredientFor(held).map(ingredient -> ingredient.normalizeOne(held)).orElse(held.copyWithCount(1));
    }

    private static void consumeAppleVinegarIngredient(Player player, InteractionHand hand, ItemStack held) {
        if (held.getItem() instanceof DurablePortionItem) {
            player.setItemInHand(hand, DurablePortionItem.consume(held, 1));
            return;
        }
        held.shrink(1);
    }

    private static InteractionResult tryShowFermentationStatus(BlockState state, Level level, BlockPos pos,
                                                               Player player, ItemStack held) {
        if (player.isShiftKeyDown() || !held.isEmpty() || state.getValue(BOTTLE) != GlassFermentationBottleState.NONE) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container)) {
            return InteractionResult.PASS;
        }

        StatusPreview status = statusForGlassFermentation(state, level, container);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!status.hasProducts()) {
            player.displayClientMessage(Component.translatable(
                    "message.kaleidoscope_agriculture_evolution.pickling_status_none"), false);
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(Component.translatable(
                "message.kaleidoscope_agriculture_evolution.pickling_status",
                formatProducts(status.products()), formatRemainingTime(status.remainingTicks())), false);
        return InteractionResult.SUCCESS;
    }

    private static StatusPreview statusForGlassFermentation(BlockState state, Level level,
                                                            FermentationContainerBlockEntity container) {
        if (container.isAppleVinegarFermenting()) {
            ContainerRecipeConfig.GlassRecipeDefinition recipe = appleVinegarRecipe();
            int count = recipe == null ? 1 : Math.max(1, container.getAppleVinegarBatches() * recipe.outputCount());
            return new StatusPreview(singleProduct(ModItems.PORTION_APPLE_VINEGAR.get(), count),
                    container.getRemainingAppleVinegarTicks(level));
        }
        if (container.isGlassPreserving()) {
            ContainerRecipeConfig.GlassRecipeDefinition recipe = glassRecipe(container.getGlassPreservingRecipeId());
            return recipe == null ? new StatusPreview(NonNullList.create(), 0L)
                    : new StatusPreview(singleProduct(recipe.outputItem(), recipe.outputCount()),
                    container.getRemainingGlassPreservingTicks(level));
        }
        if (canStartAppleVinegar(state, container)) {
            ContainerRecipeConfig.GlassRecipeDefinition recipe = appleVinegarRecipe();
            return recipe == null ? new StatusPreview(NonNullList.create(), 0L)
                    : new StatusPreview(singleProduct(ModItems.PORTION_APPLE_VINEGAR.get(),
                    recipe.maxFillLevel() * recipe.outputCount()),
                    recipe.durationTicks());
        }
        String preservingRecipe = matchingGlassPreservingRecipeId(state, container);
        if (!preservingRecipe.isEmpty()) {
            ContainerRecipeConfig.GlassRecipeDefinition recipe = glassRecipe(preservingRecipe);
            return recipe == null ? new StatusPreview(NonNullList.create(), 0L)
                    : new StatusPreview(singleProduct(recipe.outputItem(), recipe.outputCount()),
                    recipe.durationTicks());
        }
        return new StatusPreview(NonNullList.create(), 0L);
    }

    private static ContainerRecipeConfig.GlassRecipeDefinition matchingGlassPreservingRecipe(BlockState state,
                                                                                             FermentationContainerBlockEntity container,
                                                                                             ItemStack held) {
        ContainerRecipeConfig.GlassRecipeDefinition fallback = null;
        for (ContainerRecipeConfig.GlassRecipeDefinition recipe : glassPreservingRecipes()) {
            if (!recipe.canAccept(held, container)) {
                continue;
            }
            if (recipe.matchesState(state.getValue(CONTENT), state.getValue(FILL_LEVEL))) {
                return recipe;
            }
            if (fallback == null) {
                fallback = recipe;
            }
        }
        return fallback;
    }

    private static String matchingGlassPreservingRecipeId(BlockState state, FermentationContainerBlockEntity container) {
        for (ContainerRecipeConfig.GlassRecipeDefinition recipe : glassPreservingRecipes()) {
            if (container.canStartGlassPreserving(recipe.id(), state)) {
                return recipe.id();
            }
        }
        return "";
    }

    private static NonNullList<ItemStack> singleProduct(Item item, int count) {
        NonNullList<ItemStack> products = NonNullList.create();
        products.add(new ItemStack(item, count));
        return products;
    }

    private static ContainerRecipeConfig.GlassRecipeDefinition appleVinegarRecipe() {
        return glassRecipe(ContainerRecipeConfig.APPLE_VINEGAR_ID);
    }

    private static ContainerRecipeConfig.GlassRecipeDefinition glassRecipe(String id) {
        return ContainerRecipeConfig.glassRecipe(id);
    }

    private static java.util.List<ContainerRecipeConfig.GlassRecipeDefinition> glassPreservingRecipes() {
        return ContainerRecipeConfig.glassPreservingRecipes();
    }

    private static Component formatProducts(NonNullList<ItemStack> products) {
        Component result = Component.empty();
        boolean first = true;
        for (ItemStack stack : products) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!first) {
                result = result.copy().append(Component.literal(", "));
            }
            result = result.copy()
                    .append(stack.getHoverName())
                    .append(Component.literal(" x" + stack.getCount()));
            first = false;
        }
        return result;
    }

    private static Component formatRemainingTime(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds <= 0L) {
            return Component.translatable("message.kaleidoscope_agriculture_evolution.time_done");
        }
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return Component.translatable("message.kaleidoscope_agriculture_evolution.time_seconds",
                    remainingSeconds);
        }
        return Component.translatable("message.kaleidoscope_agriculture_evolution.time_minutes_seconds",
                minutes, remainingSeconds);
    }

    private record StatusPreview(NonNullList<ItemStack> products, long remainingTicks) {
        private boolean hasProducts() {
            return products.stream().anyMatch(stack -> !stack.isEmpty());
        }
    }

    private static boolean isRegisteredItem(ItemStack stack, String namespace, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(KaleidoscopeAgricultureEvolution.rl(namespace, path));
        return item != null && stack.is(item);
    }

    public static InteractionResult tryInstallTap(BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, Direction clickedFace,
                                                  Vec3 hitLocation) {
        ItemStack held = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || state.getValue(TAP) != FermentationTapState.NONE || !isTavernTap(held)
                || !isTapTarget(state, pos, hitLocation)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            Direction facing = clickedFace.getAxis().isHorizontal() ? clickedFace : player.getDirection();
            level.setBlock(pos, state
                    .setValue(TAP, FermentationTapState.CLOSE)
                    .setValue(FACING, facing), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static InteractionResult tryRemoveTap(BlockState state, Level level, BlockPos pos,
                                                 Player player, InteractionHand hand, Vec3 hitLocation) {
        if (!player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()
                || state.getValue(TAP) == FermentationTapState.NONE
                || state.getValue(BOTTLE) != GlassFermentationBottleState.NONE
                || !isTapTarget(state, pos, hitLocation)) {
            return InteractionResult.PASS;
        }

        Item tap = tavernTapItem();
        if (tap == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(TAP, FermentationTapState.NONE), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                giveItem(player, new ItemStack(tap));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static InteractionResult tryExtractLiquid(BlockState state, Level level, BlockPos pos,
                                                     Player player, InteractionHand hand) {
        return FermentationLiquidInteractions.tryExtractLiquid(state, level, pos, player, hand,
                CONTENT, FILL_LEVEL);
    }

    public static InteractionResult tryExtractNonLiquidItem(Level level, BlockPos pos, Player player,
                                                            InteractionHand hand) {
        if (!player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container)) {
            return InteractionResult.PASS;
        }
        if (!container.hasNonLiquidItems()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            ItemStack removed = container.removeOneNonLiquidItem();
            if (removed.isEmpty()) {
                return InteractionResult.PASS;
            }
            giveItem(player, removed);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean isTavernTap(ItemStack stack) {
        Item tap = tavernTapItem();
        return tap != null && stack.is(tap);
    }

    private static Item tavernTapItem() {
        return ForgeRegistries.ITEMS.getValue(KaleidoscopeAgricultureEvolution.rl(TAVERN_MOD_ID, TAVERN_TAP_ID));
    }

    private static void giveItem(Player player, ItemStack stack) {
        if (player.getAbilities().instabuild || stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void consumeItemsAndGive(Player player, InteractionHand hand, int count, ItemStack replacement) {
        ItemStack held = player.getItemInHand(hand);
        held.shrink(count);
        if (held.isEmpty()) {
            player.setItemInHand(hand, replacement);
        } else if (!replacement.isEmpty() && !player.getInventory().add(replacement)) {
            player.drop(replacement, false);
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container) {
                container.dropNonLiquidItems(level, pos);
            }
            if (state.getValue(TAP) != FermentationTapState.NONE) {
                Item tap = tavernTapItem();
                if (tap != null) {
                    Block.popResource(level, pos, new ItemStack(tap));
                }
            }
            ItemStack bottle = filledBottleItem(state.getValue(BOTTLE));
            if (bottle.isEmpty() && state.getValue(BOTTLE).toContent() == FermentationContent.EMPTY
                    && state.getValue(BOTTLE).hasBottle()) {
                bottle = new ItemStack(Items.GLASS_BOTTLE);
            }
            if (!bottle.isEmpty()) {
                Block.popResource(level, pos, bottle);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new FermentationContainerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                            @NotNull BlockState state,
                                                                            @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.FERMENTATION_CONTAINER.get(),
                FermentationContainerBlockEntity::tick);
    }

    private static VoxelShape shapeFor(BlockState state) {
        Direction facing = state.getValue(FACING);
        VoxelShape shape = boxForFacing(facing, 3.5D, 0.0D, 3.5D, 12.5D, 16.0D, 12.5D);
        if (state.getValue(TAP) != FermentationTapState.NONE) {
            shape = Shapes.or(shape, boxForFacing(facing, 6.5D, 3.75D, -1.25D, 9.5D, 7.25D, 3.5D));
        }
        if (state.getValue(BOTTLE).hasBottle()) {
            shape = Shapes.or(shape, boxForFacing(facing, 5.2D, 0.0D, -1.5D, 9.0D, 3.05D, 0.5D));
        }
        return shape;
    }

    private static VoxelShape boxForFacing(Direction facing, double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ) {
        return switch (facing) {
            case NORTH -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
            case EAST -> Block.box(16.0D - maxZ, minY, minX, 16.0D - minZ, maxY, maxX);
            case SOUTH -> Block.box(16.0D - maxX, minY, 16.0D - maxZ, 16.0D - minX, maxY, 16.0D - minZ);
            case WEST -> Block.box(minZ, minY, 16.0D - maxX, maxZ, maxY, 16.0D - minX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }

    public static boolean isBodyTarget(BlockState state, BlockPos pos, Vec3 hitLocation) {
        return isWithinFacingBox(state.getValue(FACING), hitLocation, pos,
                3.5D, 0.0D, 3.5D, 12.5D, 16.0D, 12.5D);
    }

    public static boolean isTapTarget(BlockState state, BlockPos pos, Vec3 hitLocation) {
        return isWithinFacingBox(state.getValue(FACING), hitLocation, pos,
                6.5D, 3.75D, -1.25D, 9.5D, 7.25D, 3.5D);
    }

    private static boolean isWithinFacingBox(Direction facing, Vec3 hitLocation, BlockPos pos,
                                             double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ) {
        Vec3 local = hitLocation.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0D);
        Vec3 rotated = toNorthLocal(facing, local);
        return rotated.x >= minX && rotated.x <= maxX
                && rotated.y >= minY && rotated.y <= maxY
                && rotated.z >= minZ && rotated.z <= maxZ;
    }

    private static Vec3 toNorthLocal(Direction facing, Vec3 local) {
        return switch (facing) {
            case NORTH -> local;
            case EAST -> new Vec3(local.z, local.y, 16.0D - local.x);
            case SOUTH -> new Vec3(16.0D - local.x, local.y, 16.0D - local.z);
            case WEST -> new Vec3(16.0D - local.z, local.y, local.x);
            default -> local;
        };
    }
}
