package github.meloweh.antigrieflever;

import github.meloweh.antigrieflever.block.AntiGriefLeverBlock;
import github.meloweh.antigrieflever.block.entity.AntiGriefLeverBlockEntity;
import github.meloweh.antigrieflever.network.ModNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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

    public static final DeferredBlock<AntiGriefLeverBlock> ANTI_GRIEF_LEVER = BLOCKS.register(
        "anti_grief_lever",
        () -> new AntiGriefLeverBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LEVER))
    );
    public static final DeferredItem<BlockItem> ANTI_GRIEF_LEVER_ITEM = ITEMS.register(
        "anti_grief_lever",
        () -> new BlockItem(ANTI_GRIEF_LEVER.get(), new Item.Properties())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AntiGriefLeverBlockEntity>> ANTI_GRIEF_LEVER_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register(
            "anti_grief_lever",
            () -> BlockEntityType.Builder.of(AntiGriefLeverBlockEntity::new, ANTI_GRIEF_LEVER.get()).build(null)
        );

    public Antigrieflever(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(ModNetwork::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ANTI_GRIEF_LEVER_ITEM);
        }
    }
}
