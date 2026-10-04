package dev.dbil.registry;

import dev.dbil.DBIL;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.KiWaveEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DBIL.MOD_ID);

    public static final RegistryObject<EntityType<TrainingEnemy>> TRAINING_ENEMY = ENTITIES.register(
            "training_enemy", () -> EntityType.Builder.of(TrainingEnemy::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(8).updateInterval(3)
                    .build(DBIL.id("training_enemy").toString()));
    public static final RegistryObject<EntityType<KiWaveEntity>> KI_WAVE = ENTITIES.register(
            "ki_wave", () -> EntityType.Builder.<KiWaveEntity>of(KiWaveEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(2)
                    .build(DBIL.id("ki_wave").toString()));

    public static final RegistryObject<EntityType<KiBeamEntity>> KI_BEAM = ENTITIES.register(
            "ki_beam", () -> EntityType.Builder.<KiBeamEntity>of(KiBeamEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).noSave().fireImmune()
                    .build(DBIL.id("ki_beam").toString()));

    private ModEntities() { }

    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(TRAINING_ENEMY.get(), TrainingEnemy.attributes().build());
    }
}
