# Combate e técnicas — DBIL 0.3.0

O servidor recebe intenções, escolhe o alvo (lock-on ou cone à frente) e valida personagem, estado, distância,
visibilidade, custo e cooldown. Nenhum ID de alvo, dano, custo ou posição enviado pelo cliente decide o resultado.
Ferramentas e armas continuam no combate Minecraft; a mão vazia usa o combate DBIL.

## Entrada de ataque (cliente)

`InputEvent.InteractionKeyMappingTriggered` intercepta o ataque com mão vazia quando o cursor está numa entidade viva,
no ar, ou quando há um alvo travado a até 6 blocos; nesses casos o cliente envia `LIGHT`, `HEAVY`, `LAUNCHER` ou
`SMASH` e anima o golpe imediatamente. Com o cursor num bloco e sem alvo travado próximo, a mineração vanilla segue
normal. O evento vanilla `AttackEntityEvent` continua redirecionado no servidor como antes.

| Entrada | Ação |
|---|---|
| ataque | `LIGHT` (combo) |
| Shift + ataque | `HEAVY`; com 2+ golpes leves nos últimos 24 ticks vira `SMASH` |
| Espaço + ataque | `LAUNCHER` |
| botões em J → Ações | qualquer uma das quatro |

## Combate físico (servidor, `CombatService`)

| Golpe | Stamina | Intervalo | Dano × | Empurrão horiz./vert. | Observação |
|---|---:|---:|---:|---|---|
| Jab / cruzado / chute | 2 | 7 ticks | 1,0 / 1,05 / 1,2 | 0,12–0,22 / 0,04–0,12 | combo com janela de 24 ticks |
| Final (4º leve) | 2 | 7 | 1,45 | 0,85 / 0,30 | abre janela de perseguição (20 ticks) |
| Pesado | 7 | 18 | 1,9 | 0,95 / 0,32 | pressiona guarda (+12 stamina no bloqueio) |
| Launcher | 9 | 18 | 1,4 | 0,15 / 1,05 | lança para cima; janela de perseguição 30 ticks |
| Smash | 10 | 20 | 1,7 | 1,8 / 0,22 | joga longe; de cima num alvo no ar vira golpe meteoro (vert. −1,1) |

- Dano base `2 + Strength × multiplicadorDaForma × 0,14`, × multiplicador global; Defense/guarda aplicadas uma vez em
  `GuardService` (inalterado da 0.2).
- Alcance 3,4 (leve) / 3,8 (pesados); com alvo travado, +0,8 (a animação cobre o avanço curto).
- **Aéreo**: tudo funciona voando; golpes leves de um atacante em voo mantêm NPCs suspensos para continuar o combo.
- **Hit-stun**: o rival de treino fica atordoado 6–14 ticks (sem navegar nem atacar), deixando o combo legível.
- Cada golpe aceito envia `MELEE_SWING`; cada acerto envia `HIT` (reação, partículas, flash, tremor, contador).

## Perseguição (`ChaseService`)

Combo → launcher/smash/final → **dash dentro da janela** → o atacante é levado (movimento com colisão) a 1,7 bloco do
alvo, mirando a posição prevista. Valida janela, alvo vivo e elegível, alcance (`chaseRange`, 18) e linha de visão;
custa `chaseStaminaCost` (10) e consome a janela. A HUD mostra "[X] Perseguir!".

## Vanish (`VanishService`)

Exige alvo travado a até `vanishRange` (12). O servidor testa poucos pontos fixos (atrás, lados e — se alguém estiver
no ar — acima do alvo) e aceita o primeiro livre de blocos/líquidos, carregado, dentro da borda e com visão do alvo.
Custa `vanishStaminaCost` (20) + 4 Ki, recarga `vanishCooldownTicks` (50). Não existe destino arbitrário.

## Dash direcional

`DASH`, `DASH_LEFT`, `DASH_RIGHT`, `DASH_BACK`: o cliente informa só a direção (teclas de movimento); distância (2,0–2,3),
colisão e custos são do servidor.

## Guarda

Inalterada da 0.2 (frontal, stamina por dano, quebra de guarda). Agora com eventos `GUARD_BLOCK`/`GUARD_BREAK`,
som e partículas, e pose de antebraços cruzados.

## Lock-on (`TargetingService`)

- Alcance 32, cone e linha de visão, como antes.
- Seleção prefere quem está atacando o jogador (alvo do mob, último agressor) e rivais de treino.
- **Troca de alvo** (`TARGET_NEXT`, tecla B): próximo oponente no sentido horário, mesma caixa limitada.
- Retículo 3D girando em volta do alvo e painel na HUD (nome, HP, PL, distância).
- Com alvo, a câmera acompanha (opção) e a movimentação vira combate: strafe circula o alvo, dash lateral,
  subir/descer no voo, perseguição.

## Técnicas

### Definições

`TechniqueDefinition` (0.2) continua; o novo `TechniqueProfile` acrescenta carga, escalas e apresentação sem alterar
o record antigo.

| Técnica | Tipo | Ki base | Preparo | Carga máx. | Ki total no máx. | Dano × no máx. | Recarga | Alcance | Diferencial |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| Ki Wave | projétil | 12 | 12 | 30 | ×2,0 | ×2,2 | 50 | 32 | esfera cresce ×2,2; ≥60% explode |
| Ki Blast | projétil | 5 | 2 | — | — | — | 12 | 24 | rápido, barato |
| Ki Barrage | rajada | 24 | 8 | — | — | — | 60 | 28 | 6 disparos menores, mãos alternadas, paga uma vez |
| Kamehameha | feixe | 30 | 10 | 40 | ×2,2 | ×2,4 | 120 | 40 | equilibrado, largura 0,9, 30 ticks |
| Galick Gun | feixe | 34 | 8 | 32 | ×2,3 | ×2,6 | 130 | 36 | mais dano e empurrão, mais lento, estreito, 24 ticks |
| Masenko | feixe | 22 | 6 | 18 | ×1,8 | ×2,0 | 90 | 34 | carga e viagem rápidas, largo, mais fraco, 18 ticks |

Custos de Blast/Barrage seguem configuráveis. Controle de Ki, raça e `techniqueKiCostMultiplier` participam como na 0.2;
na forma transformada com maestria baixa o custo sobe até +25%.

### Carga (segurar/soltar)

- `TECHNIQUE` (toque rápido, testes, compatível com 0.2): paga o custo base, prepara e dispara com carga mínima.
- `TECHNIQUE_HOLD`: paga o custo base e prepara; enquanto segura, cada tick de carga paga
  `custoBase × (fatorKi − 1) / cargaMáx` (para quando o Ki acaba, sem falhar). `TECHNIQUE_RELEASE` dispara com a carga
  atingida; segurar 40 ticks além do máximo dispara sozinho.
- A carga escala dano, knockback e tamanho (colisão e visual); marcos visuais 30/60/90/100%.
- Durante a carga e o feixe o lançador fica lento (−60% no chão, 35% da velocidade de voo).

### Feixes (`KiBeamEntity`)

- Origem nas mãos do lançador, atualizada a cada tick; direção vira até `turnRate` graus por tick em direção à mira ou
  ao alvo travado.
- A cabeça cresce à velocidade da técnica até um bloco (clip do servidor) ou o alcance; depois sustenta pela duração.
- Entidades vivas dentro do volume levam impacto (55% do dano + empurrão) e acertos a cada 6 ticks (45% divididos).
- Fim: explosão (dano em área 30%, raio por largura e carga, até 12 alvos) e cratera opcional se terminou num bloco.
- Não é salvo em disco; o renderizador recebe apenas dono, técnica, direção, comprimento e carga.

### Projéteis (`KiWaveEntity`)

Mesma autoridade da 0.2 (spawn nos olhos, clip contra paredes, alcance pela origem, até 100 ticks), agora com carga
sincronizada, raio de colisão proporcional, impacto com evento visual e explosão da Ki Wave carregada.

## Terreno (opcional)

`TerrainDamageService.crater`: desligado por padrão (`terrainDamage`). Quando ligado, só impactos com carga ≥
`terrainMinCharge` (0,6), raio ≤ 3, até `terrainMaxBlocks` (24) por evento e 96 por tick no servidor inteiro.
Mantém: blocos inquebráveis, tag `dbil:terrain_immune`, block entities (baús etc., configurável), resistência acima de
`terrainMaxResistance` (6 = pedra), proteção de spawn, borda do mundo, modo de jogo e `BlockEvent.BreakEvent` do Forge
(mods de proteção podem cancelar). Drops opcionais.

## Rival de treino

Como na 0.2 (HP 40, IA nativa, recompensa única), agora com hit-stun, eventos de golpe/acerto e o modelo DBIL com
aparência estável por UUID e postura de combate quando agressivo.
