package dev.dbil.registry;

import dev.dbil.DBIL;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DBIL.MOD_ID);
    public static final RegistryObject<Item> TRAINING_ENEMY_SPAWN_EGG = ITEMS.register("training_enemy_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TRAINING_ENEMY, 0x315b73, 0xe66b37, new Item.Properties()));

    private ModItems() { }

    public static void register(IEventBus bus) { ITEMS.register(bus); }

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(TRAINING_ENEMY_SPAWN_EGG);
    }
}
