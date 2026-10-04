# Efeitos, auras, câmera e HUD (0.3.0)

Tudo neste documento é apresentação no cliente. O servidor só envia estado (`StateSnapshot`) e eventos discretos
(`FxEvent`); nenhum valor visual volta para o servidor.

## Eventos de apresentação (`FxEvent`, pacote 9)

| Tipo | Origem no servidor | No cliente |
|---|---|---|
| `MELEE_SWING` | golpe validado (variante = jab, cruzado, chute, final, pesado, launcher, smash) | animação |
| `HIT` | dano confirmado | reação do alvo, flash, faíscas, anel, poeira em golpes fortes, tremor para atacante/alvo, contador de combo |
| `DASH`, `CHASE`, `VANISH` | movimento validado | inclinação, rastro, imagem residual, flash na saída/chegada |
| `TECHNIQUE_FIRE` | disparo | pose de disparo, flash nas mãos, reação de câmera em feixes |
| `KI_IMPACT`, `EXPLOSION` | impacto/explosão | flash, anel, onda de choque no chão, faíscas, poeira, fumaça, tremor por distância |
| `TRANSFORM_COMPLETE` / `TRANSFORM_REVERT` | forma ativada/desfeita | explosão de poder, onda de choque, câmera |
| `GUARD_BLOCK` / `GUARD_BREAK` | defesa/quebra | faíscas azuladas, anel |
| `TERRAIN_DEBRIS` | cratera opcional | nuvens de poeira |

Eventos de entidade vão para quem rastreia a entidade; eventos de posição vão para jogadores a até 64 blocos.

## Auras (`WorldEffectsRenderer`, `AuraStyles`)

Camadas, todas com render types vanilla aditivos (`RenderType.eyes`, `RenderType.lightning`):

1. **Casca de chamas**: 6/10/14 línguas (baixa/média/alta) em anel, viradas para a câmera, altura oscilando.
2. **Núcleo quente**: casca interna mais estreita e clara.
3. **Brilho aparente**: billboard suave em volta do corpo.
4. **Faixas ascendentes**: riscos de energia subindo.
5. **Descargas**: arcos elétricos (Super Saiyajin; mais frequentes com maestria baixa).
6. **Anel no chão** e poeira ao carregar no solo.
7. **Partículas** (`aura_mote`) emitidas pelo tick, não pelo FPS.

Estilos iniciais: `dbil:base` (técnica/voo rápido), `dbil:charging` (carga de Ki), `dbil:super_saiyan` (dourada com
descargas) e `dbil:potential_unleashed` (branca). Novas formas só registram um `AuraStyle`.

Maestria: com maestria baixa a aura oscila, falha e solta flashes; com maestria alta fica estável e controlada.
Em primeira pessoa a casca é omitida para não bloquear a visão (restam faixas e partículas).

## Técnicas

- **Orbe de carga** nas mãos (posição por pose): 0–30% pequeno; 30–60% halo; 60–90% partículas convergindo;
  90–100% anel duplo e descargas.
- **Ki Blast/Ki Wave** (`KiWaveRenderer`): núcleo branco, camada colorida, anel pulsante, rastro das últimas posições,
  partículas moderadas; impacto com flash/anel/faíscas; Ki Wave carregada ≥60% explode.
- **Feixes** (`KiBeamRenderer`): origem presa às mãos (posição interpolada do dono), tubo hexagonal externo colorido
  com energia rolando, núcleo branco, anéis viajando até a ponta, cabeça bulbosa, faíscas, explosão final.

## Câmera (`CameraEffects`)

- Tremor por "trauma" acumulado (impactos, explosões, transformação), escalado por `screenShake`.
- FOV: voo rápido (+14 × intensidade), pulsos de transformação/feixe, respiração leve ao carregar Ki.
- Sem pós-processamento; tudo desligável.

## Velocidade

Voo rápido: FOV, linhas de velocidade em volta da câmera, vento (som em loop), pose aerodinâmica e partículas.

## HUD (`DBILHud`)

- **Barras inclinadas**: HP com rastro de dano e alerta de vida baixa; Ki com brilho em movimento (acelera e ganha
  contorno pulsante ao carregar); Stamina segmentada (azulada ao defender).
- **Emblema** hexagonal na cor da aura (S/H, estrela quando transformado).
- **Painel de técnica**: ícone, nome, custo real ou recarga; aparece ao trocar de técnica mesmo fora de combate.
- **Forma ativa**: nome, barra de maestria e drain de Ki por segundo.
- **Alvo**: nome, HP, PL e distância; retículo 3D girando em volta do alvo.
- **Centro**: medidor de carga em anel com marcas de 30/60/90%, barra de transformação, banner da forma,
  aviso de guarda quebrada, prompt "Perseguir" depois de launcher/smash/final, contador de golpes.
- **Modo AUTO**: compacta fora de combate e expande em combate, carga, transformação ou guarda.
- Corações vanilla podem ser ocultados (padrão: ocultos com personagem DBIL).

## Android / telas pequenas

- Escala automática (0.62–1.0) multiplicada por `hudScale` (0.5–2.0).
- Menu J com botões de toque para todas as ações novas (Launcher, Smash, Vanish, segurar/disparar técnica, trocar
  técnica/alvo, voo rápido e dashes direcionais) em duas páginas.
- Recomendado: aura baixa, partículas poucas, distância de efeitos 32.

## Opções do cliente (`config/dbil-client.toml`)

`hud`, `hudMode`, `hudScale`, `hudX`, `hudY`, `powerLevel`, `hideVanillaHealth`, `targetReticle`, `lockOnCamera`,
`specialCamera`, `dbilCharacterModel`, `particles`, `auraQuality` (OFF/LOW/MEDIUM/HIGH), `particleDensity`
(LOW/MEDIUM/HIGH), `auraIntensity`, `effectDistance`, `screenShake`, `fovEffects`, `fovIntensity`, `impactEffects`,
`animationExtras`, `terrainDebris`, `speedLines`, `windSound`, `transformationEffects` (FULL/REDUCED).
A maioria também pode ser alterada em **J → Opções**.
