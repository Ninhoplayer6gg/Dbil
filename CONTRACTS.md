# Integration contracts — DBIL 0.3

Minecraft Java 1.20.1, Forge 47.3.22, official mappings, Java 17; base package `dev.dbil`. Common gameplay must not import client classes except deferred S2C reception via `DistExecutor`. This document describes current integration, not future features or test certification.

## Ownership

Root: bootstrap/config/network/server lifecycle and final integration/build.
Character/flight agent: data/capability/races/stats/power and shared flight integrator/client prediction.
Combat agent: combat/guard/targeting/techniques/NPC registries.
Client agent: client events/camera, GUI, render layers/auras/poses and localization.
Transformation/debug agent: definitions/service, commands and transformation tests.
Training agent: challenges/rewards and training tests. Session sparring integration belongs to root.

## Character data

`CharacterData` is mutable server-owned. Client mirrors are read-only by convention and replaced from server snapshots. Schema is **4**; migration 0→1→2→3→4 (4 adds `appearance`) and protected future schemas are implemented.

- Identity: `created()`, `compatibleSchema()`, `name()`, `raceId()`, `originId()`, `styleId()`.
- Progress/stats: `level()`, `experience()`, `basePower()`, `currentPower()`, `stat(Stat)`.
- Resources: `ki()/maxKi()`, `stamina()/maxStamina()`, safe spend/add/set methods.
- Unlocks: `mastery()`, `unlockedTechniques()`, `equippedTechniques()` (max `MAX_EQUIPPED` = 6), `selectedTechnique()`, `equip/unequip`, `unlockedTransformations()`, `currentTransformation()`, `trainingStats()`, `storyFlags()`.
- Appearance: `appearance()` / `setAppearance(CharacterAppearance)`; `CharacterAppearance` is an immutable, self-clamping record (NBT `save/load`, network `write/read`, `defaultFor(race)`, `forRace(race)`).
- Persistence: `save()/load(CompoundTag)`, `copyFrom(CharacterData)`; owner synchronization uses `saveSnapshot()/loadSnapshot()` to include authoritative caps.

`Stat`: STRENGTH, DEFENSE, SPEED, KI_POWER, KI_CONTROL, MAX_KI, MAX_STAMINA, VITALITY.

`CharacterCapability.CAPABILITY` attaches serializable data to every player; `get(Player)` returns it. Lifecycle handlers revive/copy/invalidate caps, apply transient attributes and clear session states. Future-schema data is preserved without exposing creation/mutation actions.

`CharacterService.create(ServerPlayer,name,race,origin,style[,appearance])` validates choices and computes initial stats server-side. Race IDs: `dbil:human`, `dbil:saiyan`; origins `earth_warrior`/`survivor`; styles `balanced`, `brawler`, `speed`, `ki_specialist`, `defensive`. New characters learn/equip/select Ki Wave. `CharacterService.updateAppearance` accepts edits only for created characters and re-syncs trackers. `applyAttributes`, `reset` and `respawn` handle vanilla HP/speed integration safely.

`Races`/`Origins` supply registrable definitions; `PowerLevelCalculator` calculates from stats/resources/condition/modifiers. `ProgressionService.award` handles capped moderate progression. Form multipliers are temporary, never written into base stats.

## Network: dbil:main, protocol 3

Protocol equality is required on client/server. Packets have explicit directions and run queued work on the logical thread.

| ID | Direction | Data |
|---|---|---|
| 0 | C2S | Enumerated action intent |
| 1 | C2S | Sequenced flight input; forward/strafe/ascent/descent, finite yaw/pitch and `fast` |
| 2 | C2S | Bounded name/race/origin/style creation choices plus appearance |
| 3 | S2C owner | Character NBT snapshot with effective caps |
| 4 | S2C tracking+self | `StateSnapshot`: visual/action state (charge, flight/fast flight, target, technique id/prep/charge/holding, guard, form/prep/total/mastery, combat) and authoritative player/target power |
| 5 | S2C owner | Flight ACK with server tick/sequence/input ticks/position/velocity/normal and fast speed caps/flying/hard reset |
| 6 | C2S | Select/equip/unequip technique or request transformation by registered resource ID |
| 7 | C2S | Appearance update (validated, created characters only) |
| 8 | S2C tracking+self | `AppearanceSync`: entity id, created flag, appearance, race |
| 9 | S2C tracking/nearby | `FxEvent`: type, variant, entity/other id, magnitude, position, color (presentation only) |

Public requests: `sendAction(Action)`, `sendFlightInput(sequence, forward, strafe, up, down, yaw, pitch[, fast])`, `sendCreate(...[, appearance])`, `sendSelectTechnique(id)`, `sendEquipTechnique(id, equip)`, `sendTransform(id)`, `sendAppearance(appearance)`. `sendTransform(CharacterData.BASE_FORM)` reverts/cancels in any living state. Server side: `Network.sendFx(entity, event)` / `sendFxNear(level, pos, radius, event)` via `FxService`. The server owns all resource costs, stats, XP, cooldowns, damage and rewards.

`Action` (new values appended, ordinals of 0.2 values unchanged): CHARGE_START, CHARGE_STOP, FLIGHT_TOGGLE, DASH, LIGHT, HEAVY, TECHNIQUE, LOCK_ON, STOP_ALL, GUARD_START, GUARD_STOP, TRANSFORM_REVERT, SPAR_START, TECHNIQUE_HOLD, TECHNIQUE_RELEASE, LAUNCHER, SMASH, VANISH, DASH_LEFT, DASH_RIGHT, DASH_BACK, TARGET_NEXT.

Each player's `PlayerState` admits at most 30 actions and 24 movement inputs per 20 ticks. Changed inputs may be sent every gameplay tick; held input heartbeat is four ticks. No input packet is sent per render frame. Charging/guarding have server leases. `sync`, `syncState`, `syncStateTo` and periodic owner comparison reduce unnecessary full data traffic.

## Client state and movement

`ClientState.acceptData(CompoundTag)` loads a snapshot. `acceptState` receives entity ID, charging/flying/target/technique ticks/cooldown, guarding/guard-break ticks, transformation ResourceLocation/preparation ticks and player/target powers.

`VisualState` exposes `charging()`, `flying()`, `targetId()`, `techniqueTicks()`, `cooldownTicks()`, `guarding()`, `guardBreakTicks()`, `transformation()`, `transformationTicks()`, `power()`, `targetPower()`, plus 0.3 `technique()`, `techniqueChargeNow(partial)`, `chargingTechnique()`, `transformationProgress()`, `fastFlight()`, `inCombat()`, `formMastery()`. `ClientState.appearance(entityId)` returns the synced look (null = vanilla rendering). Powers come from the server; unknown target power is -1. HUD uses vanilla synchronized living-entity HP and only formats metadata.

`ClientFlightController` owns local movement prediction and gravity based on validated ACKs, with bounded history/replay. Do not restore gravity every tick from an older visual snapshot. `ClientControls` captures launcher input and touch toggles; `touchGuarding`/`touchCharging` and directional toggles use normal action intents. Death/logout/tab changes clear held controls.

`FlightMotion` is shared deterministic math for normalized desired velocity, acceleration/braking and collision replay. `FlightService.beforeTick/tick` integrates authoritative movement and validates sequenced input via `acceptInput`. `externalMotion` handles dash/knockback; `stop` resets flight. Regular ACK is four ticks; vanilla teleport is reserved for exceptional large corrections/discrete actions. No creative-flight abilities are granted.

## Combat, targeting and techniques

`CombatService` owns melee (light combo of 4, heavy, launcher, smash, aerial), costs, cooldown/combo, hit-stun and hit validation; launcher/smash/finisher open a chase window used by `ChaseService`. `VanishService` repositions next to a locked target at fixed candidate points only. The common AttackEntity listener redirects **server-side** bare-hand attacks; client vanilla packets still reach the server. Tools/items retain vanilla behavior. Heavy uses sneak intent, including descending-flight state. `GuardService` handles directional defense, Stamina and guard break; knockback feeds active flight velocity.

`TargetingService` has `LOCK_RANGE=32`, cone/line-of-sight/PvP validation, threat-aware selection and bounded AABB selection. `toggle`, `cycle`, `validate`, `target` never select across the entire world.

`Techniques` registers `ki_wave`, `ki_blast`, `ki_barrage`, `kamehameha`, `galick_gun`, `masenko`; `TechniqueProfile` adds charge/scaling/presentation. `TechniqueService.select/start/startHold/release/tick/cancel` validates unlock/equipment/requirements/resources and persists selection. Ki Barrage (6 shots) pays once. `KiWaveEntity` projectiles and `KiBeamEntity` beams (not saved, owner-anchored, server collision) carry synchronized technique id and charge. Terrain damage only via opt-in `TerrainDamageService`. No Beam Clash.

`ModEntities` registers TRAINING_ENEMY, KI_WAVE and KI_BEAM; common entity attributes are registered on the mod bus. TrainingEnemy extends PathfinderMob, uses light native goals, exposes `getPowerLevel` and rewards one credited player once. Menu sparring is limited by owner/cooldown/density/safe placement/expiry.

## Transformations and training

`Transformations` registers/finalizes `super_saiyan` and `potential_unleashed`. `TransformationEligibility.check` is a read-only query. `TransformationService.start/tick/revert/resetSession` owns requirements, interruption, cost/drain, mastery and transient attributes/power; `masteryFraction`/`controlPenalty` (up to +25% technique cost at low mastery) and `formColor` serve techniques and FX. Session teardown returns active form to base; unlocks/mastery persist.

`TrainingChallenges.progress(data)` returns definitions. `completed`, `counter`, `available`, `ready` are read-only GUI queries; `tick(ServerPlayer,data)` alone grants rewards on the server. Challenge IDs: first_combat, ki_control, awakening, beam_training, rapid_ki, explosive_wave. Objectives are cumulative; flight_ticks displays as seconds in the GUI. Rewards have durable completion flags and cannot be claimed through client values.

## Presentation/config/debug

All GUI/render/animation subscribers are Dist.CLIENT. R/G/X/C/V/J remain; 0.3 adds N (next technique), Z (Vanish), B (next target); all remappable. Right click with empty hand and a selected target requests guard; the Actions tab offers touch equivalents. Seven paged tabs include technique selection, forms, training/sparring and settings.

`ClientConfig.lockOnCamera=true` supersedes the old camera=false key without overwriting an explicit new false preference. Special cinematic effects remain separate. `TargetCamera` runs smooth render-frame orientation; only normal gameplay/Actions allows following. HUD is scaled/normalized and shows local resources/state plus target HP/power/distance.

`DBILPlayerRenderer` replaces player rendering for DBIL characters (vanilla otherwise): painted skin, voxel hair with an SSJ variant per style, 3D outfit pieces and accessories in `CharacterLayer`, procedural animation in `CharacterAnimator`. Auras/beams/projectiles are drawn by `client/fx` with vanilla additive render types. The training rival uses the DBIL model with a UUID-derived look.

`/dbil info` is public for self; administrative mutations/diagnostics require permission 2. Current commands also include registered form unlock/transform/mastery. No raw NBT mutation is accepted. See DEBUG_COMMANDS.md.

GameTests run explicitly in dedicated logical-server mode; CI also boots a dedicated server and runs a scripted Xvfb client (`client/dev/ClientAutotest`, `-PdbilAutotest=true`). A second CI job runs a dedicated server with two scripted graphical clients (`client/dev/MultiplayerAutotest`). Results are recorded in docs/VALIDATION.md; Android hardware still requires separate evidence.
