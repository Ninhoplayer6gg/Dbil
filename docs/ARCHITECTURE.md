# Arquitetura DBIL 0.3.0

## Princípio

O servidor decide tudo que é gameplay (dano, custos, alcance, posição, desbloqueios, recompensas). O cliente envia
**intenções** e transforma **estado + eventos** em apresentação (modelo, animação, auras, HUD, câmera, som).
Nenhuma lógica visual roda no servidor dedicado; nenhum valor visual volta para o servidor.

## Módulos

| Módulo | Implementação |
|---|---|
| api/race/character/stats | Definições, escolhas iniciais, atributos e NBT versionado (schema 4) |
| appearance | `CharacterAppearance` (record validado, NBT, rede, padrões por raça) e `AppearanceOptions` (listas fixas) |
| capability | Provider Forge serializável por jogador, invalidação e clone |
| power/training | Cálculo base/estado, progressão e seis desafios com recompensas persistentes |
| ki/stamina | Consumo seguro, regeneração, carga (no chão e em voo parado) |
| flight/movement | Integrador autoritativo, voo rápido, dash direcional, perseguição (`ChaseService`) e Vanish (`VanishService`) |
| combat/targeting | Combo, pesado, launcher, smash, aéreo, hit-stun, guarda, lock-on com troca e seleção por ameaça, terreno opcional |
| technique | Seis técnicas; `TechniqueProfile` (carga e escalas); `KiWaveEntity` (projéteis) e `KiBeamEntity` (feixes) |
| transformation | Super Saiyajin/Potencial Liberado, maestria, penalidade de controle, cor de forma para eventos |
| fx | `FxType` + `FxService`: eventos discretos de apresentação gerados pelo servidor |
| npc/registry | Rival de treino, entidades, sons nomeados, partículas próprias |
| network/server | Pacotes, intenções, validação, lifecycle e estado transitório de sessão (`PlayerState`) |
| client/render/character | Modelo DBIL, pele pintada, cabelos voxel, roupas 3D, camadas, renderer substituto |
| client/anim | Camada procedural de animação (`CharacterAnimator`, `AnimState`) |
| client/fx | Auras, feixes/projéteis, partículas, câmera, sons em loop, despachante de eventos |
| gui | HUD dinâmica, criação/edição de aparência, menu J |
| debug/gametest | Comandos administrativos, GameTests de servidor e teste visual automatizado do cliente (`client/dev`) |

## Rede: canal `dbil:main`, protocolo `3`

Versões diferentes recusam a conexão.

| ID | Sentido | Conteúdo |
|---|---|---|
| 0 | C → S | Ação enumerada (`Action`, 22 valores; os novos foram acrescentados no fim) |
| 1 | C → S | Input sequenciado de voo + yaw/pitch finitos + `fast` |
| 2 | C → S | Criação: nome, raça, origem, estilo e aparência |
| 3 | S → dono | Snapshot NBT do personagem com limites efetivos |
| 4 | S → rastreadores + dono | `StateSnapshot`: carga, voo/voo rápido, alvo, técnica (preparo, carga, ticks), guarda, forma (ticks, maestria), combate, poderes |
| 5 | S → dono | ACK de voo (posição, velocidade, limites normal/rápido, reset) |
| 6 | C → S | Selecionar/equipar/desequipar técnica ou pedir forma por ID registrado |
| 7 | C → S | Atualizar aparência (validada; só personagem criado) |
| 8 | S → rastreadores + dono | `AppearanceSync`: aparência + raça de uma entidade |
| 9 | S → rastreadores/próximos | `FxEvent`: tipo, entidade/posição, variante, intensidade, cor |

Custos de banda: `AppearanceSync` só ao começar a rastrear, ao criar e ao editar; `StateSnapshot` em mudanças e
heartbeat; `FxEvent` apenas em ações aceitas (golpe, acerto, disparo, impacto, transformação, guarda, dash). Nada é
enviado por frame. Orçamento por jogador: 30 ações e 24 inputs de voo a cada 20 ticks.

## Renderização do personagem

`ClientModEvents` cria `DBILPlayerRenderer` (normal e slim) em `EntityRenderersEvent.AddLayers`. `ClientEvents`
cancela `RenderPlayerEvent.Pre` de jogadores com personagem DBIL e desenha o renderer próprio; sem personagem (ou
com `dbilCharacterModel=false`) o vanilla segue intacto. `RenderArmEvent` desenha o braço DBIL em primeira pessoa.
O modelo usa a pele/roupa pintada; `CharacterLayer` desenha roupas 3D, cabelo voxel, acessórios e cauda; as camadas
vanilla seguem presentes (armadura, item na mão, flechas, capa, cabeça customizada, elytra, papagaio, ataque giratório,
ferrão). Detalhes em [VISUAL_CHARACTER.md](VISUAL_CHARACTER.md).

## Animação

Depois de `HumanoidModel.setupAnim`, `CharacterAnimator` mistura pesos suavizados (combate, voo, carga, guarda,
técnica, transformação, golpe, impacto, dash) a partir de `ClientState.VisualState` e dos eventos. Sem GeckoLib
(avaliado e descartado: dependência extra, modelos Bedrock separados, sem ganho para um corpo cúbico procedural).
Detalhes em [ANIMATION.md](ANIMATION.md).

## Voo e knockback

Inalterado em essência da 0.2 (integrador servidor + previsão cliente com replay limitado), com o modo rápido.
Veja [FLIGHT.md](FLIGHT.md).

## Performance

- Texturas de pele pintadas uma vez por combinação (aparência × variante × expressão), cache LRU de 48 com
  expiração de 90 s; malhas de cabelo/roupa assadas uma vez por estilo.
- Auras com render types vanilla aditivos, contagem de camadas por qualidade, corte por distância; partículas por
  tick e limitadas por densidade.
- Lock-on, golpes, Vanish e perseguição usam caixas limitadas; nenhuma busca no mundo inteiro.
- Feixes: uma entidade por disparo, colisão por clip + AABB limitada, acertos a cada 6 ticks, não salva em disco.
- Terreno opcional com limite por evento e por tick.
- Assinantes de cliente usam `Dist.CLIENT`; pacotes S2C chegam ao cliente por `DistExecutor`. O servidor dedicado
  sobe sem classes de cliente (verificado no CI).
