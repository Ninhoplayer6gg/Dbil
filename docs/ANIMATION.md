# Sistema de animação (0.3.0)

## Decisão: GeckoLib ou implementação própria?

GeckoLib 4.x existe para Forge 1.20.1, mas foi descartado para o jogador:

- Exigiria trocar o renderizador do jogador por um renderer Geo, perdendo compatibilidade direta com itens, armaduras,
  elytra, primeira pessoa e as poses vanilla de arco/besta/escudo.
- Animações seriam keyframes fixos (Blockbench), difíceis de misturar com estados contínuos (voo, carga, lock-on).
- Seria uma dependência obrigatória a mais para cliente e servidor (também no Android).

A solução adotada é uma camada procedural própria (`client.anim.CharacterAnimator`) aplicada **depois** do
`HumanoidModel.setupAnim` vanilla. Vanilla continua cuidando de itens, montaria, natação, sono e agachar; o DBIL
substitui as poses rígidas. Tudo é client-only e nunca roda no servidor dedicado.

## Estrutura

- `AnimState` por entidade (jogadores e NPCs), com blends suavizados exponencialmente por tempo de jogo.
- Estados contínuos (vêm do `StateSnapshot`): combate/lock-on, voo, voo rápido, carga de Ki, guarda, transformação,
  carga de técnica e feixe ativo, queda.
- Ações pontuais (vêm de `FxEvent`): golpes do combo, pesado, launcher, smash, disparo de técnica, dash, Vanish,
  explosão de poder da transformação e pouso.
- Reação a impacto sobreposta a tudo (`HIT`).
- Transformação raiz (`CharacterAnimator.root`): inclinação de voo, rolagem lateral, giro do chute final, balanço de
  flutuação e "tombo" após golpes fortes, aplicada em `setupRotations` do renderizador.

## Poses

| Estado | Pose |
|---|---|
| Idle | respiração leve, braços afastados do corpo, base mais aberta |
| Caminhada / corrida / sprint | torção do tronco no passo, inclinação para frente, braços bombeando no sprint |
| Postura de combate | punhos na guarda (mão da frente mais alta), pés escalonados, tronco virado, pequeno balanço |
| Voo parado | pernas dobradas assimétricas, braços relaxados, flutuação |
| Voo lento | corpo inclinado na direção do movimento |
| Voo rápido | corpo quase horizontal seguindo o pitch, um punho à frente, pernas estendidas |
| Voo de combate | guarda de combate com pernas de voo |
| Carga de Ki | punhos na cintura, base larga, tremor proporcional, grito |
| Transformação | começa contido e termina com braços abertos e cabeça para trás |
| Guarda | antebraços cruzados diante do rosto |
| Combo | jab (direita), cruzado (esquerda), chute alto, chute giratório |
| Pesado / Launcher / Smash | soco amplo com inclinação / uppercut subindo / martelo duplo |
| Kamehameha | carga com as mãos no quadril direito, disparo com as duas mãos à frente |
| Galick Gun | carga com corpo torcido e mãos ao lado esquerdo, disparo de palmas abertas |
| Masenko | mãos acima da cabeça, disparo para frente |
| Ki Blast / Barragem | estocadas rápidas, alternando braços na barragem |
| Dash / Vanish | inclinação na direção do dash / guarda ao reaparecer |
| Impacto / knockback | recuo de tronco e cabeça; golpes fortes no ar fazem o corpo girar |
| Queda / pouso | braços sobem na queda; agachamento proporcional à velocidade no pouso |

O golpe do jogador local é animado no clique (predição) e o eco do servidor é ignorado para evitar duplicidade.

## Desempenho

Somente matemática de ângulos por parte; nenhuma alocação por frame além do registro de estados (limpos a cada 5 s).
