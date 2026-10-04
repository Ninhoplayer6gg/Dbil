package dev.dbil.capability;

import dev.dbil.character.CharacterData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Forge owns persistence of this provider; transient combat/movement state lives outside it. */
public final class CharacterCapability {
    public static final Capability<CharacterData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation KEY = new ResourceLocation("dbil", "character");
    private CharacterCapability() {}

    public static void register(RegisterCapabilitiesEvent event) { event.register(CharacterData.class); }

    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;
        Provider provider = new Provider();
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    public static CharacterData get(Player player) {
        return player.getCapability(CAPABILITY).orElseThrow(() -> new IllegalStateException(
                "DBIL character capability is missing for player " + player.getUUID()));
    }

    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final CharacterData data = new CharacterData();
        private LazyOptional<CharacterData> optional = LazyOptional.of(() -> data);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
            return capability == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }
        @Override public CompoundTag serializeNBT() { return data.save(); }
        @Override public void deserializeNBT(CompoundTag tag) { data.load(tag); }
        /**
         * The entity's capability guard blocks reads while invalidated. Forge reviveCaps() only
         * restores that guard, so prepare a fresh optional for the later PlayerEvent.Clone read.
         * Cached references stay invalidated; the durable data object itself survives revival.
         */
        public void invalidate() {
            optional.invalidate();
            optional = LazyOptional.of(() -> data);
        }
    }
}
