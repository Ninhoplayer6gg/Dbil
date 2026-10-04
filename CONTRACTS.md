# Integration contracts — DBIL 0.2

Minecraft Java 1.20.1, Forge 47.3.22, official mappings, Java 17; base package `dev.dbil`. Common gameplay must not import client classes except deferred S2C reception via `DistExecutor`. This document describes current integration, not future features or test certification.

## Ownership

Root: bootstrap/config/network/server lifecycle and final integration/build.
Character/flight agent: data/capability/races/stats/power and shared flight integrator/client prediction.
Combat agent: combat/guard/targeting/techniques/NPC registries.
Client agent: client events/camera, GUI, render layers/auras/poses and localization.
Transformation/debug agent: definitions/service, commands and transformation tests.
Training agent: challenges/rewards and training tests. Session sparring integration belongs to root.

## Character data

`CharacterData` is mutable server-owned. Client mirrors are read-only by convention and replaced from server snapshots. Schema is **3**; migration 0→1→2→3 and protected future schemas are implemented.

- Identity: `created()`, `compatibleSchema()`, `name()`, `raceId()`, `originId()`, `styleId()`.
- Progress/stats: `level()`, `experience()`, `basePower()`, `currentPower()`, `stat(Stat)`.
- Resources: `ki()/maxKi()`, `stamina()/maxStamina()`, safe spend/add/set methods.
- Unlocks: `mastery()`, `unlockedTechniques()`, `equippedTechniques()`, `selectedTechnique()`, `unlockedTransformations()`, `currentTransformation()`, `trainingStats()`, `storyFlags()`.
- Persistence: `save()/load(CompoundTag)`, `copyFrom(CharacterData)`; owner synchronization uses `saveSnapshot()/loadSnapshot()` to include authoritative caps.

`Stat`: STRENGTH, DEFENSE, SPEED, KI_POWER, KI_CONTROL, MAX_KI, MAX_STAMINA, VITALITY.

`CharacterCapability.CAPABILITY` attaches serializable data to every player; `get(Player)` returns it. Lifecycle handlers revive/copy/invalidate caps, apply transient attributes and clear session states. Future-schema data is preserved without exposing creation/mutation actions.

`CharacterService.create(ServerPlayer,name,race,origin,style)` validates choices and computes initial stats server-side. Race IDs: `dbil:human`, `dbil:saiyan`; origins `earth_warrior`/`survivor`; styles `balanced`, `brawler`, `speed`, `ki_specialist`, `defensive`. New characters learn/equip/select Ki Wave. `applyAttributes`, `reset` and `respawn` handle vanilla HP/speed integration safely.

`Races`/`Origins` supply registrable definitions; `PowerLevelCalculator` calculates from stats/resources/condition/modifiers. `ProgressionService.award` handles capped moderate progression. Form multipliers are temporary, never written into base stats.

## Network: dbil:main, protocol 2

Protocol equality is required on client/server. Packets have explicit directions and run queued work on the logical thread.

| ID | Direction | Data |
|---|---|---|
| 0 | C2S | Enumerated action intent |
| 1 | C2S | Sequenced flight input; forward/strafe/ascent/descent and finite yaw/pitch |
| 2 | C2S | Bounded name/race/origin/style creation choices |
| 3 | S2C owner | Character NBT snapshot with effective caps |
| 4 | S2C tracking+self | Visual/action state and authoritative player/target power |
| 5 | S2C owner | Flight ACK with server tick/sequence/input ticks/position/velocity/speed/flying/hard reset |
| 6 | C2S | Select technique or request transformation by registered resource ID |

Public requests: `sendAction(Action)`, `sendFlightInput(int sequence,float forward,float strafe,boolean up,boolean down,float yaw,float pitch)`, `sendCreate(...)`, `sendSelectTechnique(ResourceLocation)`, `sendTransform(ResourceLocation)`. `sendTransform(CharacterData.BASE_FORM)` reverts/cancels. The server owns all resource costs, stats, XP, cooldowns, damage and rewards.

`Action`: CHARGE_START, CHARGE_STOP, FLIGHT_TOGGLE, DASH, LIGHT, HEAVY, TECHNIQUE, LOCK_ON, STOP_ALL, GUARD_START, GUARD_STOP, TRANSFORM_REVERT, SPAR_START.

Each player's `PlayerState` admits at most 30 actions and 24 movement inputs per 20 ticks. Changed inputs may be sent every gameplay tick; held input heartbeat is four ticks. No input packet is sent per render frame. Charging/guarding have server leases. `sync`, `syncState`, `syncStateTo` and periodic owner comparison reduce unnecessary full data traffic.

## Client state and movement

`ClientState.acceptData(CompoundTag)` loads a snapshot. `acceptState` receives entity ID, charging/flying/target/technique ticks/cooldown, guarding/guard-break ticks, transformation ResourceLocation/preparation ticks and player/target powers.

`VisualState` exposes `charging()`, `flying()`, `targetId()`, `techniqueTicks()`, `cooldownTicks()`, `guarding()`, `guardBreakTicks()`, `transformation()`, `transformationTicks()`, `power()`, `targetPower()`. Powers come from the server; unknown target power is -1. HUD uses vanilla synchronized living-entity HP and only formats metadata.

`ClientFlightController` owns local movement prediction and gravity based on validated ACKs, with bounded history/replay. Do not restore gravity every tick from an older visual snapshot. `ClientControls` captures launcher input and touch toggles; `touchGuarding`/`touchCharging` and directional toggles use normal action intents. Death/logout/tab changes clear held controls.

`FlightMotion` is shared deterministic math for normalized desired velocity, acceleration/braking and collision replay. `FlightService.beforeTick/tick` integrates authoritative movement and validates sequenced input via `acceptInput`. `externalMotion` handles dash/knockback; `stop` resets flight. Regular ACK is four ticks; vanilla teleport is reserved for exceptional large corrections/discrete actions. No creative-flight abilities are granted.

## Combat, targeting and techniques

`CombatService.attack(ServerPlayer,heavy)` owns melee costs, cooldown/combo and hit validation. The common AttackEntity listener redirects **server-side** bare-hand attacks; client vanilla packets still reach the server. Tools/items retain vanilla behavior. Heavy uses sneak intent, including descending-flight state. `GuardService` handles directional defense, Stamina and guard break; knockback feeds active flight velocity.

`TargetingService` has `LOCK_RANGE=32`, cone/line-of-sight/PvP validation and bounded AABB selection. `toggle`, `validate`, `target` never select across the entire world.

`Techniques` registers `ki_wave`, `ki_blast`, `ki_barrage`. `TechniqueService.select/start/tick/cancel` validates unlock/equipment/requirements/resources and persists selection. `TechniqueDefinition.ProjectilePattern` describes bounded barrages; three-shot Ki Barrage pays once. `KiWaveEntity` is custom Projectile with synchronized `techniqueId`, server collision, damage, knockback and expiry; current attacks do not destroy terrain. Beam metadata remains available without an implemented beam/clash executor.

`ModEntities` registers TRAINING_ENEMY and KI_WAVE; common entity attributes are registered on the mod bus. TrainingEnemy extends PathfinderMob, uses light native goals, exposes `getPowerLevel` and rewards one credited player once. Menu sparring is limited by owner/cooldown/density/safe placement/expiry.

## Transformations and training

`Transformations` registers/finalizes `super_saiyan` and `potential_unleashed`. `TransformationEligibility.check` is a read-only query. `TransformationService.start/tick/revert/resetSession` owns requirements, interruption, cost/drain, mastery and transient attributes/power. Session teardown returns active form to base; unlocks/mastery persist.

`TrainingChallenges.progress(data)` returns definitions. `completed`, `counter`, `available`, `ready` are read-only GUI queries; `tick(ServerPlayer,data)` alone grants rewards on the server. Challenge IDs: first_combat, ki_control, awakening. Objectives are cumulative; flight_ticks displays as seconds in the GUI. Rewards have durable completion flags and cannot be claimed through client values.

## Presentation/config/debug

All GUI/render/animation subscribers are Dist.CLIENT. R/G/X/C/V/J remain compact/remappable. Right click with empty hand and a selected target requests guard; the Actions tab offers touch equivalents. Seven paged tabs include technique selection, forms, training/sparring and settings.

`ClientConfig.lockOnCamera=true` supersedes the old camera=false key without overwriting an explicit new false preference. Special cinematic effects remain separate. `TargetCamera` runs smooth render-frame orientation; only normal gameplay/Actions allows following. HUD is scaled/normalized and shows local resources/state plus target HP/power/distance.

`SaiyanHairLayer` adds lightweight procedural golden hair to SSJ from server visual state; human Potential retains skin. Auras and poses are bounded/local presentation. Current NPC skin is a temporary isolated vanilla visual.

`/dbil info` is public for self; administrative mutations/diagnostics require permission 2. Current commands also include registered form unlock/transform/mastery. No raw NBT mutation is accepted. See DEBUG_COMMANDS.md.

GameTests run explicitly in dedicated logical-server mode. The observed 50-pass regression, including the two power tests, is recorded in VALIDATION.md; graphics, real multiplayer and Android require separate evidence.
