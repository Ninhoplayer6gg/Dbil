package dev.dbil.network;

import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterService;
import dev.dbil.client.ClientState;
import dev.dbil.client.ClientFlightController;
import dev.dbil.flight.FlightService;
import dev.dbil.technique.TechniqueService;
import dev.dbil.transformation.TransformationService;
import dev.dbil.character.CharacterData;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.targeting.TargetingService;
import net.minecraft.world.phys.Vec3;
import dev.dbil.server.Action;
import dev.dbil.server.ServerActions;
import dev.dbil.server.ServerRuntime;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Intent-only C2S messages. No incoming packet carries stats, damage, XP or costs. */
public final class Network {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(DBIL.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    public static void register() {
        CHANNEL.registerMessage(0, ActionPacket.class, ActionPacket::encode, ActionPacket::decode,
                ActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, FlightInput.class, FlightInput::encode, FlightInput::decode,
                FlightInput::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, CreateCharacter.class, CreateCharacter::encode, CreateCharacter::decode,
                CreateCharacter::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, DataSnapshot.class, DataSnapshot::encode, DataSnapshot::decode,
                DataSnapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, StateSnapshot.class, StateSnapshot::encode, StateSnapshot::decode,
                StateSnapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, FlightAck.class, FlightAck::encode, FlightAck::decode,
                FlightAck::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6, DefinitionAction.class, DefinitionAction::encode, DefinitionAction::decode,
                DefinitionAction::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void sendAction(Action action) { CHANNEL.sendToServer(new ActionPacket(action)); }
    public static void sendFlightInput(int sequence, float f, float s, boolean up, boolean down, float yaw, float pitch) {
        CHANNEL.sendToServer(new FlightInput(f, s, up, down, yaw, pitch, sequence));
    }
    public static void sendSelectTechnique(ResourceLocation id) {
        CHANNEL.sendToServer(new DefinitionAction(DefinitionAction.Kind.TECHNIQUE, id));
    }
    public static void sendTransform(ResourceLocation id) {
        CHANNEL.sendToServer(new DefinitionAction(DefinitionAction.Kind.TRANSFORMATION, id));
    }
    public static void sendFlightAck(ServerPlayer p, boolean hardReset) {
        var s = ServerRuntime.state(p);
        Vec3 position = s.flightPosition == null ? p.position() : s.flightPosition;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new FlightAck(p.serverLevel().getGameTime(),
                s.flightInputSequence, s.flightInputTicks, position, s.flightVelocity,
                s.flightMaximumSpeed, s.flying, hardReset));
        s.lastFlightAckTick = p.serverLevel().getGameTime();
        s.flightAckRequired = s.flightHardReset = false;
    }
    public static void sendCreate(String name, ResourceLocation race, ResourceLocation origin, String style) {
        CHANNEL.sendToServer(new CreateCharacter(name, race, origin, style));
    }
    public static void sync(ServerPlayer p) {
        if (!p.isAlive() || p.isRemoved()) return;
        var tag = CharacterCapability.get(p).saveSnapshot();
        ServerRuntime.state(p).lastDataSnapshot = tag;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new DataSnapshot(tag));
        syncState(p);
    }
    public static void syncPeriodic(ServerPlayer p) {
        var s = ServerRuntime.state(p);
        var tag = CharacterCapability.get(p).saveSnapshot();
        if (!tag.equals(s.lastDataSnapshot)) {
            s.lastDataSnapshot = tag;
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new DataSnapshot(tag));
        }
        syncState(p);
    }
    public static void syncState(ServerPlayer p) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), snapshot(p));
    }
    public static void syncStateTo(ServerPlayer subject, ServerPlayer observer) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), snapshot(subject));
    }
    private static StateSnapshot snapshot(ServerPlayer p) {
        var s = ServerRuntime.state(p);
        long now = p.serverLevel().getGameTime();
        int cooldown = (int)Math.max(0, Math.min(12000, s.nextTechniqueTick - now));
        var data = p.getCapability(CharacterCapability.CAPABILITY).orElse(null);
        long power = data == null ? 0 : data.currentPower();
        ResourceLocation form = data == null ? CharacterData.BASE_FORM : data.currentTransformation();
        long targetPower = -1;
        var target = p.level().getEntity(s.targetId);
        if (target instanceof TrainingEnemy enemy) targetPower = enemy.getPowerLevel();
        else if (target instanceof ServerPlayer other) {
            targetPower = other.getCapability(CharacterCapability.CAPABILITY)
                    .map(CharacterData::currentPower).orElse(-1L);
        }
        int guardBreak = (int)Math.max(0, Math.min(200, s.guardBreakUntil - now));
        return new StateSnapshot(p.getId(), s.charging, s.flying, s.targetId, s.techniqueChargeTicks, cooldown,
                s.guarding, guardBreak, form, s.transformationChargeTicks, power, targetPower);
    }
    private record ActionPacket(Action action) {
        static void encode(ActionPacket p, FriendlyByteBuf b) { b.writeEnum(p.action); }
        static ActionPacket decode(FriendlyByteBuf b) { return new ActionPacket(b.readEnum(Action.class)); }
        static void handle(ActionPacket p, Supplier<NetworkEvent.Context> context) {
            var c = context.get(); c.enqueueWork(() -> {
                var sender = c.getSender(); if (sender != null) ServerActions.handle(sender, p.action);
            }); c.setPacketHandled(true);
        }
    }
    private record FlightInput(float forward, float strafe, boolean up, boolean down, float yaw, float pitch, int sequence) {
        static void encode(FlightInput p, FriendlyByteBuf b) {
            b.writeFloat(p.forward); b.writeFloat(p.strafe); b.writeBoolean(p.up); b.writeBoolean(p.down);
            b.writeFloat(p.yaw); b.writeFloat(p.pitch); b.writeVarInt(p.sequence);
        }
        static FlightInput decode(FriendlyByteBuf b) {
            return new FlightInput(b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readVarInt());
        }
        static void handle(FlightInput p, Supplier<NetworkEvent.Context> context) {
            var c = context.get(); c.enqueueWork(() -> {
                var player = c.getSender();
                if (player == null || !player.isAlive() || player.isRemoved() || !CharacterCapability.get(player).created()) return;
                var s = ServerRuntime.state(player); long now = player.serverLevel().getGameTime();
                if (!s.admitMovement(now)) return;
                FlightService.acceptInput(player, s, p.sequence, p.forward, p.strafe, p.up, p.down, p.yaw, p.pitch);
            }); c.setPacketHandled(true);
        }
    }
    private record CreateCharacter(String name, ResourceLocation race, ResourceLocation origin, String style) {
        static void encode(CreateCharacter p, FriendlyByteBuf b) {
            b.writeUtf(p.name, 24); b.writeUtf(p.race.toString(), 64); b.writeUtf(p.origin.toString(), 64); b.writeUtf(p.style, 24);
        }
        static CreateCharacter decode(FriendlyByteBuf b) {
            return new CreateCharacter(b.readUtf(24), ResourceLocation.tryParse(b.readUtf(64)),
                    ResourceLocation.tryParse(b.readUtf(64)), b.readUtf(24));
        }
        static void handle(CreateCharacter p, Supplier<NetworkEvent.Context> context) {
            var c = context.get(); c.enqueueWork(() -> {
                var player = c.getSender(); if (player == null || !player.isAlive() || player.isRemoved()) return;
                var s = ServerRuntime.state(player);
                if (!s.admitAction(player.serverLevel().getGameTime())) return;
                if (p.race == null || p.origin == null || !CharacterService.create(player, p.name, p.race, p.origin, p.style)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbil.creation_rejected"), false);
                }
                sync(player);
            }); c.setPacketHandled(true);
        }
    }
    private record DataSnapshot(CompoundTag tag) {
        static void encode(DataSnapshot p, FriendlyByteBuf b) { b.writeNbt(p.tag); }
        static DataSnapshot decode(FriendlyByteBuf b) { var nbt = b.readNbt(); return new DataSnapshot(nbt == null ? new CompoundTag() : nbt); }
        static void handle(DataSnapshot p, Supplier<NetworkEvent.Context> context) {
            var c = context.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientState.acceptData(p.tag))); c.setPacketHandled(true);
        }
    }
    private record StateSnapshot(int id, boolean charging, boolean flying, int target, int charge, int cooldown,
                                 boolean guarding, int guardBreak, ResourceLocation form, int transformTicks,
                                 long power, long targetPower) {
        static void encode(StateSnapshot p, FriendlyByteBuf b) {
            b.writeVarInt(p.id); b.writeBoolean(p.charging); b.writeBoolean(p.flying); b.writeInt(p.target);
            b.writeVarInt(p.charge); b.writeVarInt(p.cooldown); b.writeBoolean(p.guarding); b.writeVarInt(p.guardBreak);
            b.writeUtf(p.form.toString(), 64); b.writeVarInt(p.transformTicks);
            b.writeVarLong(p.power); b.writeLong(p.targetPower);
        }
        static StateSnapshot decode(FriendlyByteBuf b) {
            int id = b.readVarInt(); boolean charging = b.readBoolean(), flying = b.readBoolean();
            int target = b.readInt(), charge = b.readVarInt(), cooldown = b.readVarInt();
            boolean guarding = b.readBoolean(); int guardBreak = b.readVarInt();
            ResourceLocation form = ResourceLocation.tryParse(b.readUtf(64));
            return new StateSnapshot(id, charging, flying, target, charge, cooldown, guarding, guardBreak,
                    form == null ? CharacterData.BASE_FORM : form, b.readVarInt(), b.readVarLong(), b.readLong());
        }
        static void handle(StateSnapshot p, Supplier<NetworkEvent.Context> context) {
            var c = context.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientState.acceptState(p.id, p.charging, p.flying, p.target, p.charge, p.cooldown,
                            p.guarding, p.guardBreak, p.form, p.transformTicks, p.power, p.targetPower)));
            c.setPacketHandled(true);
        }
    }
    private record FlightAck(long tick, int sequence, int inputTicks, Vec3 position, Vec3 velocity,
                             double maximumSpeed, boolean flying, boolean hardReset) {
        static void encode(FlightAck p, FriendlyByteBuf b) {
            b.writeLong(p.tick); b.writeVarInt(p.sequence); b.writeVarInt(p.inputTicks);
            b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z);
            b.writeDouble(p.velocity.x); b.writeDouble(p.velocity.y); b.writeDouble(p.velocity.z);
            b.writeDouble(p.maximumSpeed); b.writeBoolean(p.flying); b.writeBoolean(p.hardReset);
        }
        static FlightAck decode(FriendlyByteBuf b) {
            return new FlightAck(b.readLong(), b.readVarInt(), b.readVarInt(),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readDouble(), b.readBoolean(), b.readBoolean());
        }
        static void handle(FlightAck p, Supplier<NetworkEvent.Context> context) {
            var c = context.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientFlightController.acceptAck(p.tick, p.sequence, p.inputTicks,
                            p.position, p.velocity, p.maximumSpeed, p.flying, p.hardReset)));
            c.setPacketHandled(true);
        }
    }
    private record DefinitionAction(Kind kind, ResourceLocation id) {
        enum Kind { TECHNIQUE, TRANSFORMATION }
        static void encode(DefinitionAction p, FriendlyByteBuf b) { b.writeEnum(p.kind); b.writeUtf(p.id.toString(), 64); }
        static DefinitionAction decode(FriendlyByteBuf b) {
            return new DefinitionAction(b.readEnum(Kind.class), ResourceLocation.tryParse(b.readUtf(64)));
        }
        static void handle(DefinitionAction p, Supplier<NetworkEvent.Context> context) {
            var c = context.get();
            c.enqueueWork(() -> {
                var player = c.getSender();
                if (player == null || p.id == null || !player.isAlive() || player.isRemoved() || player.isSpectator()) return;
                var data = CharacterCapability.get(player);
                var state = ServerRuntime.state(player);
                if (!data.created() || !state.admitAction(player.serverLevel().getGameTime())) return;
                FlightService.restorePosition(player, state);
                if (p.kind == Kind.TECHNIQUE) TechniqueService.select(player, p.id);
                else TransformationService.start(player, p.id);
                sync(player);
            });
            c.setPacketHandled(true);
        }
    }
    private Network() {}
}
