package dev.dbil.debug;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.dbil.DBIL;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.character.CharacterService;
import dev.dbil.network.Network;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.power.PowerLevelCalculator;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.server.ServerRuntime;
import dev.dbil.server.ServerEvents;
import dev.dbil.technique.Techniques;
import dev.dbil.training.ProgressionService;
import dev.dbil.transformation.Transformations;
import dev.dbil.transformation.TransformationDefinition;
import dev.dbil.transformation.TransformationService;
import dev.dbil.transformation.TransformationEligibility;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/** Every mutation is run by the logical server and requires operator permission level two. */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DebugCommands {
    private DebugCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dbil")
                .then(Commands.literal("info").executes(context -> info(context.getSource(), context.getSource().getPlayerOrException(), false)));

        root.then(Commands.literal("setrace").requires(source -> source.hasPermission(2))
                .then(Commands.argument("race", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                Races.values().stream().map(race -> race.id()), builder))
                        .executes(context -> setRace(context, self(context)))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> setRace(context, target(context))))));
        root.then(Commands.literal("setki").requires(source -> source.hasPermission(2))
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 1_000_000))
                        .executes(context -> setKi(context, self(context)))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> setKi(context, target(context))))));
        root.then(Commands.literal("addxp").requires(source -> source.hasPermission(2))
                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1_000_000))
                        .executes(context -> addExperience(context, self(context)))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> addExperience(context, target(context))))));
        root.then(adminPlayerCommand("heal", DebugCommands::heal));
        root.then(adminPlayerCommand("power", DebugCommands::power));
        root.then(adminPlayerCommand("reset", DebugCommands::reset));
        root.then(adminPlayerCommand("debug", (context, player) -> info(context.getSource(), player, true)));
        root.then(Commands.literal("learn").requires(source -> source.hasPermission(2))
                .then(Commands.argument("technique", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                Techniques.values().stream().map(technique -> technique.id()), builder))
                        .executes(context -> learn(context, self(context)))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> learn(context, target(context))))));
        root.then(Commands.literal("spawn").requires(source -> source.hasPermission(2))
                .executes(context -> spawn(context, 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 8))
                        .executes(context -> spawn(context, IntegerArgumentType.getInteger(context, "count")))));
        root.then(Commands.literal("transform").requires(source -> source.hasPermission(2))
                .then(Commands.argument("form", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                java.util.stream.Stream.concat(java.util.stream.Stream.of(CharacterData.BASE_FORM),
                                        Transformations.values().stream().map(TransformationDefinition::id)), builder))
                        .executes(context -> transform(context, self(context)))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> transform(context, target(context))))));
        root.then(Commands.literal("unlockform").requires(source -> source.hasPermission(2))
                .then(Commands.argument("form", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                Transformations.values().stream().map(TransformationDefinition::id), builder))
                        .executes(context -> unlockForm(context, self(context)))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> unlockForm(context, target(context))))));
        root.then(Commands.literal("mastery").requires(source -> source.hasPermission(2))
                .then(Commands.argument("form", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                Transformations.values().stream().map(TransformationDefinition::id), builder))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 100))
                                .executes(context -> mastery(context, self(context)))
                                .then(Commands.argument("player", EntityArgument.player()).executes(context -> mastery(context, target(context)))))));
        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminPlayerCommand(String name, PlayerCommand action) {
        return Commands.literal(name).requires(source -> source.hasPermission(2))
                .executes(context -> action.run(context, self(context)))
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> action.run(context, target(context))));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayer(context, "player");
    }

    private static int info(CommandSourceStack source, ServerPlayer player, boolean detailed) {
        CharacterData data = CharacterCapability.get(player);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "DBIL %s | criado=%s | raça=%s | nível=%d | XP=%d | Ki=%.1f/%.1f | Stamina=%.1f/%.1f | Poder=%d/%d",
                data.name(), data.created(), data.raceId(), data.level(), data.experience(),
                data.ki(), data.maxKi(), data.stamina(), data.maxStamina(), data.currentPower(), data.basePower())), false);
        if (detailed) {
            var state = ServerRuntime.state(player);
            source.sendSuccess(() -> Component.literal("Origem=" + data.originId() + " | estilo=" + data.styleId()
                    + " | técnicas=" + data.equippedTechniques() + " | forma=" + data.currentTransformation()
                    + " | voo=" + state.flying + " | carregando=" + state.charging
                    + " | selecionada=" + data.selectedTechnique()
                    + " | guarda=" + state.guarding + " | quebra até=" + state.guardBreakUntil
                    + " | flight seq=" + state.flightInputSequence + " | correções fortes=" + state.flightCorrections
                    + " | combo=" + state.combo + " | alvo=" + state.targetId
                    + " | transformação pendente=" + state.pendingTransformation
                    + " | ticks de transformação=" + state.transformationChargeTicks
                    + " | schema=" + data.save().getInt("schemaVersion")), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static boolean requireCharacter(CommandSourceStack source, CharacterData data) {
        if (data.created()) return true;
        source.sendFailure(Component.literal("Crie o personagem DBIL antes de alterar seus dados."));
        return false;
    }

    private static int setRace(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!requireCharacter(context.getSource(), data)) return 0;
        ResourceLocation id = ResourceLocationArgument.getId(context, "race");
        if (Races.get(id) == null) {
            context.getSource().sendFailure(Component.literal("Raça DBIL desconhecida: " + id));
            return 0;
        }
        ServerEvents.resetSession(player);
        data.setRace(id);
        CharacterService.applyAttributes(player, data);
        recalculate(player, data);
        Network.sync(player);
        return feedback(context, "Raça de " + player.getScoreboardName() + " alterada para " + id);
    }

    private static int setKi(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!requireCharacter(context.getSource(), data)) return 0;
        double amount = DoubleArgumentType.getDouble(context, "amount");
        if (!Double.isFinite(amount)) {
            context.getSource().sendFailure(Component.literal("Ki deve ser um valor finito."));
            return 0;
        }
        data.setKi(amount);
        recalculate(player, data);
        Network.sync(player);
        return feedback(context, String.format(Locale.ROOT, "Ki de %s: %.1f/%.1f", player.getScoreboardName(), data.ki(), data.maxKi()));
    }

    private static int addExperience(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!requireCharacter(context.getSource(), data)) return 0;
        ProgressionService.award(player, IntegerArgumentType.getInteger(context, "amount"));
        Network.sync(player);
        return feedback(context, "Nível de " + player.getScoreboardName() + ": " + data.level() + " | XP: " + data.experience());
    }

    private static int heal(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!requireCharacter(context.getSource(), data)) return 0;
        player.setHealth(player.getMaxHealth());
        data.setKi(data.maxKi());
        data.setStamina(data.maxStamina());
        recalculate(player, data);
        Network.sync(player);
        return feedback(context, "HP, Ki e Stamina restaurados: " + player.getScoreboardName());
    }

    private static int power(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        recalculate(player, data);
        Network.sync(player);
        return feedback(context, "Poder de " + player.getScoreboardName() + ": base=" + data.basePower() + " | atual=" + data.currentPower());
    }

    private static void recalculate(ServerPlayer player, CharacterData data) {
        var state = ServerRuntime.state(player);
        PowerLevelCalculator.update(data, player.getHealth() / Math.max(1, player.getMaxHealth()), state.charging, state.flying);
    }

    private static int learn(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!requireCharacter(context.getSource(), data)) return 0;
        ResourceLocation id = ResourceLocationArgument.getId(context, "technique");
        if (Techniques.get(id) == null) {
            context.getSource().sendFailure(Component.literal("Técnica DBIL desconhecida: " + id));
            return 0;
        }
        data.learn(id);
        data.equip(id);
        Network.sync(player);
        return feedback(context, "Técnica aprendida: " + id + " por " + player.getScoreboardName());
    }

    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (!CharacterCapability.get(player).compatibleSchema()) {
            context.getSource().sendFailure(Component.literal("DBIL: save de uma versão mais nova protegido. Use a versão compatível do mod."));
            return 0;
        }
        ServerEvents.resetSession(player);
        CharacterService.reset(player);
        Network.sync(player);
        Network.syncState(player);
        return feedback(context, "Personagem DBIL reiniciado: " + player.getScoreboardName());
    }

    private static int spawn(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        Vec3 center = source.getPosition();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            TrainingEnemy enemy = ModEntities.TRAINING_ENEMY.get().create(source.getLevel());
            if (enemy == null) break;
            Vec3 position = center.add(2 + i % 4 * 1.5, 0, 2 + i / 4 * 1.5);
            enemy.moveTo(position.x, position.y, position.z, 0, 0);
            if (!source.getLevel().noCollision(enemy)) {
                enemy.discard();
                continue;
            }
            if (source.getLevel().addFreshEntity(enemy)) spawned++;
        }
        if (spawned == 0) {
            source.sendFailure(Component.literal("Sem espaço livre para o inimigo de treinamento."));
            return 0;
        }
        return feedback(context, "Inimigos de treinamento criados: " + spawned);
    }

    private static int transform(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "form");
        var result = TransformationService.start(player, id);
        if (result != TransformationEligibility.Result.READY) {
            context.getSource().sendFailure(Component.literal("Transformação recusada: " + transformationReason(result)));
            return 0;
        }
        return feedback(context, (CharacterData.BASE_FORM.equals(id) ? "Forma base restaurada: " : "Ativação iniciada: ")
                + id + " | " + player.getScoreboardName());
    }

    private static int unlockForm(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!requireCharacter(context.getSource(), data)) return 0;
        ResourceLocation id = ResourceLocationArgument.getId(context, "form");
        TransformationDefinition definition = Transformations.get(id).orElse(null);
        if (definition == null || !definition.races().contains(data.raceId())) {
            context.getSource().sendFailure(Component.literal("Forma desconhecida ou incompatível com a raça: " + id));
            return 0;
        }
        if (!data.unlockedTransformations().contains(id) && !data.unlockTransformation(id)) {
            context.getSource().sendFailure(Component.literal("Limite de transformações desbloqueadas alcançado."));
            return 0;
        }
        Network.sync(player);
        return feedback(context, "Forma desbloqueada: " + id + " | " + player.getScoreboardName());
    }

    private static int mastery(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData data = CharacterCapability.get(player);
        if (!requireCharacter(context.getSource(), data)) return 0;
        ResourceLocation id = ResourceLocationArgument.getId(context, "form");
        TransformationDefinition definition = Transformations.get(id).orElse(null);
        double amount = DoubleArgumentType.getDouble(context, "amount");
        if (definition == null || !data.unlockedTransformations().contains(id)
                || !definition.races().contains(data.raceId()) || !Double.isFinite(amount)
                || amount < 0 || amount > definition.mastery().maximum()) {
            context.getSource().sendFailure(Component.literal("Forma deve ser válida e desbloqueada; maestria deve estar entre 0 e o limite da definição."));
            return 0;
        }
        data.setMastery(id, amount);
        recalculate(player, data);
        Network.sync(player);
        return feedback(context, String.format(Locale.ROOT, "Maestria %s de %s: %.2f", id, player.getScoreboardName(), data.mastery().getOrDefault(id, 0.0)));
    }

    private static String transformationReason(TransformationEligibility.Result result) {
        return switch (result) {
            case READY -> "pronta";
            case UNKNOWN_FORM -> "forma desconhecida";
            case NO_CHARACTER -> "personagem não criado ou save incompatível";
            case WRONG_RACE -> "raça incompatível";
            case LOCKED -> "forma ainda bloqueada; conclua o desafio Despertar";
            case REQUIREMENTS -> "requisitos de nível/atributos não cumpridos";
            case INSUFFICIENT_KI -> "Ki insuficiente";
            case INVALID_STATE -> "estado atual do jogador impede a ativação";
            case BUSY -> "outra habilidade ou transformação está ativa";
            case COOLDOWN -> "aguarde o intervalo de ativação";
            case ALREADY_ACTIVE -> "forma já está ativa; use dbil:base para reverter";
        };
    }

    private static int feedback(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), true);
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface PlayerCommand {
        int run(CommandContext<CommandSourceStack> context, ServerPlayer player) throws CommandSyntaxException;
    }
}
