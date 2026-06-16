package github.meloweh.antigrieflever;

import github.meloweh.antigrieflever.block.AntiGriefLeverBlock;
import github.meloweh.antigrieflever.block.AcardeWarpStoneBlock;
import github.meloweh.antigrieflever.block.RestoratorLeverBlock;
import github.meloweh.antigrieflever.block.entity.AcardeWarpStoneBlockEntity;
import github.meloweh.antigrieflever.block.entity.AntiGriefLeverBlockEntity;
import github.meloweh.antigrieflever.block.entity.RestoratorLeverBlockEntity;
import github.meloweh.antigrieflever.item.CopperCompassItem;
import github.meloweh.antigrieflever.item.DesertSandwichItem;
import github.meloweh.antigrieflever.item.PlayerFinderCompassItem;
import github.meloweh.antigrieflever.network.ModNetwork;
import github.meloweh.antigrieflever.recipe.PlayerFinderCompassRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Antigrieflever.MODID)
public class Antigrieflever {
    public static final String MODID = "antigrieflever";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, MODID);

    public static final DeferredBlock<AntiGriefLeverBlock> ANTI_GRIEF_LEVER = BLOCKS.register(
        "anti_grief_lever",
        () -> new AntiGriefLeverBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LEVER))
    );
    public static final DeferredBlock<RestoratorLeverBlock> RESTORATOR_LEVER = BLOCKS.register(
        "restorator_lever",
        () -> new RestoratorLeverBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LEVER))
    );
    public static final DeferredBlock<AcardeWarpStoneBlock> ACARDE_WARP_STONE = BLOCKS.register(
        "acarde_warp_stone",
        () -> new AcardeWarpStoneBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE))
    );
    public static final DeferredItem<BlockItem> ANTI_GRIEF_LEVER_ITEM = ITEMS.register(
        "anti_grief_lever",
        () -> new BlockItem(ANTI_GRIEF_LEVER.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> RESTORATOR_LEVER_ITEM = ITEMS.register(
        "restorator_lever",
        () -> new BlockItem(RESTORATOR_LEVER.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> ACARDE_WARP_STONE_ITEM = ITEMS.register(
        "acarde_warp_stone",
        () -> new BlockItem(ACARDE_WARP_STONE.get(), new Item.Properties())
    );
    public static final DeferredItem<CopperCompassItem> COPPER_COMPASS = ITEMS.register(
        "copper_compass",
        () -> new CopperCompassItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<PlayerFinderCompassItem> PLAYER_FINDER_COMPASS = ITEMS.register(
        "player_finder_compass",
        () -> new PlayerFinderCompassItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<DesertSandwichItem> DESERT_SANDWICH = ITEMS.register(
        "desert_sandwich",
        () -> new DesertSandwichItem(new Item.Properties().food(DesertSandwichItem.foodProperties()))
    );
    public static final DeferredHolder<MobEffect, MobEffect> DEMORALIZATION_BOOST = MOB_EFFECTS.register(
        "demoralization_boost",
        () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x7D6F8C) {
        }
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AntiGriefLeverBlockEntity>> ANTI_GRIEF_LEVER_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register(
            "anti_grief_lever",
            () -> BlockEntityType.Builder.of(AntiGriefLeverBlockEntity::new, ANTI_GRIEF_LEVER.get()).build(null)
        );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RestoratorLeverBlockEntity>> RESTORATOR_LEVER_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register(
            "restorator_lever",
            () -> BlockEntityType.Builder.of(RestoratorLeverBlockEntity::new, RESTORATOR_LEVER.get()).build(null)
        );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AcardeWarpStoneBlockEntity>> ACARDE_WARP_STONE_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register(
            "acarde_warp_stone",
            () -> BlockEntityType.Builder.of(AcardeWarpStoneBlockEntity::new, ACARDE_WARP_STONE.get()).build(null)
        );
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PlayerFinderCompassRecipe>>
        PLAYER_FINDER_COMPASS_RECIPE = RECIPE_SERIALIZERS.register(
            "player_finder_compass",
            () -> new SimpleCraftingRecipeSerializer<>(PlayerFinderCompassRecipe::new)
        );

    public Antigrieflever(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(ModNetwork::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ANTI_GRIEF_LEVER_ITEM);
            event.accept(RESTORATOR_LEVER_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ACARDE_WARP_STONE_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(COPPER_COMPASS);
            event.accept(PLAYER_FINDER_COMPASS);
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(DESERT_SANDWICH);
        }
    }
}
