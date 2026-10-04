# Relatório da sessão — DBIL 0.3.0 "Visual & Combat Overhaul"

Base: DBIL 0.2.0 (importada sem alterações no commit `5a8a09a`). A 0.3.0 foi construída **sobre** ela: nenhum sistema
foi recriado do zero; os sistemas de gameplay da 0.2 continuam e foram estendidos.

## 1. Implementado

**Personagem e aparência**
- Modelo DBIL estilo Minecraft ("Steve evoluído"): base `PlayerModel` (cabeça cúbica, membros em bloco), proporções
  Tipo A (Steve) / Tipo B (Alex), renderizado no lugar da skin para quem tem personagem DBIL; a skin da conta só
  aparece sem personagem ou com `dbilCharacterModel=false`.
- Pele pintada pelo mod (`SkinPainter`, textura dinâmica em cache): rosto estilizado com 5 formatos de olho, cor
  livre, 4 sobrancelhas, 3 bocas, expressões neutra/focada/grito e olhos SSJ.
- 7 cabelos voxel 3D (curto, espetado, espetado alto, bagunçado, médio, liso, careca), cada um com variante
  Super Saiyajin própria (`HairModels`), cor editável.
- 4 roupas em camadas (gi de treino, armadura de batalha, colete, regata) com 3 cores, peças 3D (ombreiras, nó da faixa,
  golas, pontas da faixa na testa), munhequeiras, faixa na testa e cauda Saiyajin animada.
- Criação com prévia 3D ao vivo (giro, prévia SSJ) e edição posterior em J → Personagem → Editar aparência.
- Dados visuais persistidos (schema 4, migração automática 3→4) e sincronizados para todos que veem o jogador.
- Rival de treino usa o mesmo modelo com aparência derivada do UUID.

**Animação** (procedural, sem GeckoLib): idle respirando, postura de combate com lock-on, caminhada/corrida/sprint,
voo parado/lento/rápido/de combate, carga de Ki, transformação, guarda, combo de 4 golpes, pesado, launcher, smash,
dash, Vanish, poses próprias de cada técnica (Kamehameha nas mãos ao lado, Masenko com as mãos acima da cabeça,
Galick Gun com o tronco torcido e os braços para o lado), impacto, knockback, queda e pouso.

**HUD nova** (`DBILHud`): barras inclinadas de HP (rastro de dano), Ki (brilho animado, reage à carga) e Stamina
segmentada, emblema de raça/forma, PL opcional, painel de técnica (custo, recarga, carga %), forma/maestria/drain,
painel do alvo (nome, HP, PL, distância) com retículo 3D, medidor de carga em anel, banner e barra de
transformação, contador de golpes, prompt de perseguição. Modo AUTO compacto/expandido; escala automática para
telas pequenas × `hudScale`.

**Ki, auras e transformações**
- Auras em camadas com render types aditivos vanilla: chamas, núcleo, brilho, faixas ascendentes, descargas,
  anel no chão, partículas por tick. Estilos registráveis: base, carga, Super Saiyajin, Potencial Liberado.
- Sequência de transformação: pose, aura crescente, cabelo alternando base/SSJ, grito, explosão de poder, som,
  câmera, banner.
- Maestria: baixa = drain maior, ativação lenta, aura instável com flashes, técnicas até 25% mais caras;
  alta = rápida, estável, econômica.

**Técnicas**
- Carga segurar/soltar (C) com marcos 30/60/90/100% escalando dano, Ki, knockback e tamanho.
- Novos feixes **Kamehameha** (azul, equilibrado), **Galick Gun** (roxo, mais dano/empurrão, mais lento) e
  **Masenko** (amarelo, carga rápida, largo, mais fraco): entidade `KiBeamEntity` presa às mãos, colisão no
  servidor, explosão final.
- Ki Blast, Ki Wave (explode com ≥60%) e Ki Barrage (6 disparos menores alternando mãos) com visual novo.
- Três desafios novos de treino desbloqueiam os feixes.

**Combate e movimento**
- Combo leve de 4 golpes, pesado (Shift), launcher (Espaço), smash (Shift após combo), golpes aéreos, hit-stun.
- Perseguição (dash logo após launcher/smash/final), Vanish (Z) para pontos fixos ao lado/atrás do alvo,
  dash direcional, lock-on com troca (B) e seleção por ameaça.
- Voo rápido (Ctrl): segue o pitch, ×1,9 de velocidade (teto configurável), ×3 de Ki, FOV, linhas, vento.
- Carga de Ki em voo parado.
- Dano ao terreno **opcional** (desligado por padrão) com proteções.

**Infra**: protocolo de rede 3 (aparência, eventos de efeito, estado visual ampliado); 6 slots de técnica;
sons nomeados e 7 partículas próprias; comandos `/dbil learnall` e `/dbil appearance default`; CI com build,
GameTests, servidor dedicado e teste visual automatizado do cliente.

## 2. Melhorado em relação à 0.2

Auras (antes partículas simples), Ki Wave/Blast/Barrage (antes billboard), cabelo SSJ (antes malha fixa de 7 pontas
sobre a skin), HUD (antes painel de texto), lock-on (troca de alvo, seleção, retículo), combate (antes leve/pesado),
voo (modo rápido), menu (ações de toque novas, equipar/desequipar), feedback de impacto (tremor, flash, faíscas,
proporcional ao golpe).

## 3. Sistemas da 0.2 preservados

Criação (nome/raça/origem/estilo), atributos e progressão, Ki/Stamina, voo autoritativo com previsão e replay,
guarda/quebra, lock-on com câmera suave, Super Saiyajin e Potencial Liberado (regras, custo, drain, maestria),
desafios e recompensas únicas, sparring sem OP, rival de treino, comandos, migrações 0→3, proteção de schema
futuro, orçamento de pacotes, todas as suítes de GameTests da 0.2 (continuam passando).

## 4. Principais arquivos

- Novos: `appearance/*`, `client/render/character/*` (11 classes), `client/anim/*`, `client/fx/*`,
  `client/render/KiBeamRenderer`, `technique/KiBeamEntity`, `technique/TechniqueProfile`, `fx/*`,
  `movement/ChaseService`, `movement/VanishService`, `combat/TerrainDamageService`, `gui/HudState`,
  `gametest/VisualCombatGameTests`, `client/dev/ClientAutotest`, `tools/generate_textures.py`,
  `tools/preview_hair.py`, `.github/workflows/build.yml`.
- Muito alterados: `network/Network`, `server/ServerActions`, `server/PlayerState`, `combat/CombatService`,
  `technique/TechniqueService`, `technique/Techniques`, `technique/KiWaveEntity`, `targeting/TargetingService`,
  `flight/FlightService`, `flight/FlightMotion`, `transformation/TransformationService`, `character/CharacterData`,
  `gui/DBILHud`, `gui/CharacterCreationScreen`, `gui/DBILMenuScreen`, `client/ClientEvents`, `config/*`.
- Removidos (substituídos): `animation/PlayerPoses`, `animation/PlayerPresentation`, `rendering/*`.
- Em números (desde a base 0.2): 38 classes novas, 35 alteradas, 8 removidas; ~7,5 mil linhas adicionadas em `src`.

## 5. Dependências novas

Nenhuma. GeckoLib foi avaliado e descartado (dependência extra para cliente e servidor, modelos Bedrock separados,
sem ganho para um corpo cúbico com animação procedural). Sem shaders, sem mixins.

## 6. Controles

R carregar Ki · G voo (Ctrl voo rápido) · X dash/perseguir · C técnica (toque/segurar) · N próxima técnica ·
V travar alvo · B trocar alvo · Z Vanish · J menu · ataque (Shift pesado/smash, Espaço launcher) · botão direito
guarda. Tudo remapeável; todas as ações também em J → Ações (toque).

## 7. Configuração visual (`config/dbil-client.toml`, quase tudo em J → Opções)

`hudMode`, `hudScale`, `hudX/Y`, `powerLevel`, `hideVanillaHealth`, `targetReticle`, `lockOnCamera`,
`specialCamera`, `dbilCharacterModel`, `particles`, `auraQuality` (OFF/LOW/MEDIUM/HIGH), `particleDensity`
(LOW/MEDIUM/HIGH), `auraIntensity`, `effectDistance`, `screenShake`, `fovEffects`, `fovIntensity`,
`impactEffects`, `animationExtras`, `terrainDebris`, `speedLines`, `windSound`, `transformationEffects`.
Servidor (`dbil-server.toml`): `vanish*`, `chase*`, `maxFastFlightSpeed`, `fastFlightKiMultiplier`,
`terrainDamage` (false), `terrainMaxBlocks`, `terrainDropBlocks`, `terrainProtectBlockEntities`,
`terrainMaxResistance`, `terrainMinCharge`, além das chaves da 0.2.

## 8. Comandos

Os da 0.2 + `/dbil learnall [jogador]` e `/dbil appearance default [jogador]` (permissão 2).

## 9. Build e testes executados

Todos executados de verdade no GitHub Actions (o ambiente desta sessão não alcança o Maven do Forge; ver
[VALIDATION.md](VALIDATION.md)):

- `./gradlew clean build`: **BUILD SUCCESSFUL**; JAR em `build/libs/dbil-0.3.0.jar` (artefato `dbil-jar` de cada run).
- `./gradlew runGameTestServer`: **All 61 required tests passed** (50 da 0.2 + 11 novos).
- Servidor dedicado: sobe até `Done (...)` com o DBIL 0.3.0, sem erro de classe de cliente.
- Cliente visual (Xvfb, renderização por software): **PASS**, 28 screenshots cobrindo modelo, HUD, lock-on, aura,
  transformação, SSJ, os três feixes, Ki Barrage, combo, Vanish, guarda, voo rápido, editor, menu e primeira pessoa.
- Multiplayer (servidor dedicado + 2 clientes gráficos): em verificação no CI (a primeira execução mostrou toda a sincronização remota funcionando; ver VALIDATION.md).
- Problemas reais encontrados pelos testes visuais e corrigidos: reverter forma na água/montado; cabelo SSJ de
  costas; rótulo de carga na HUD; dica do menu sobre o botão; partículas da preparação humana.

## 10. Bugs conhecidos / limitações

- Sem teste em **Android físico** (escala da HUD, toque, FPS e memória precisam ser conferidos no aparelho).
- Multiplayer testado com dois clientes na mesma máquina (rede local, sem latência real).
- Camadas de jogador adicionadas por **outros mods** não aparecem em personagens DBIL (as camadas vanilla sim).
  Desligar `dbilCharacterModel` volta ao modelo vanilla.
- Sons são variações de sons vanilla nomeados como eventos DBIL (substituíveis por resource pack).
- O ambiente desta sessão não acessa o Maven do Forge (política de rede); o build local não roda aqui — a
  verificação real é o CI do GitHub Actions.
- Renderização por software no CI prova funcionamento e aparência, não desempenho.

## 11. Desempenho

Texturas e malhas assadas uma vez e reutilizadas (cache LRU, expiração); nenhum pacote por frame; FX por evento
discreto; auras com número de camadas por qualidade e corte por distância; partículas por tick limitadas por
densidade; buscas sempre em caixas limitadas; feixes não salvos em disco; terreno com orçamento por evento e por
tick; nada visual roda no servidor. Recomendado para Android: aura LOW, partículas LOW, distância 32.

## 12. Pendente / próximos passos

Beam Clash; SSJ2 e outras formas; mais estilos de cabelo/roupas; sons próprios gravados; teste e ajuste em Android
e com dois clientes; refinamento de animações com feedback de jogo.
