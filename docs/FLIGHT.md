# Voo contínuo e autoridade

## Correção da versão 0.2

O voo anterior corrigia o dono com um teleport vanilla a cada quatro ticks. Cada
teleport abre uma confirmação de posição; um launcher Android podia receber novos
teleports antes de concluir a confirmação anterior. `setNoGravity(true)` sozinho
também não impede `travel()` de aplicar impulso e atrito vanilla.

A implementação atual integra movimento em 20 ticks por segundo, no cliente e no
servidor. O servidor continua decidindo custo de Ki, velocidade máxima, validade do
estado e colisões. O cliente prevê apenas apresentação de movimento. Não altera
Ki, Stamina, atributos, dano, cooldown ou permissões de voo.

## Ciclo de tick

- Servidor START: `FlightService.beforeTick()` restaura a posição autoritativa,
  zera o impulso consumido por `travel()`, mantém ausência de gravidade e aplica
  a exceção de anti-floating somente durante voo DBIL válido.
- Servidor END: `FlightService.tick()` integra aceleração/desaceleração e move com
  `Entity.move(MoverType.SELF, ...)`. A posição vem do estado armazenado pelo
  servidor, independentemente dos packets de posição vanilla recebidos.
- Cliente START: o controller zera movimento vanilla somente durante voo aceito.
- `MovementInputUpdateEvent`: captura input real do launcher e controles da UI;
  depois zera impulsos/jump/sneak vanilla. As teclas continuam legíveis no snapshot
  de controle. O listener roda com prioridade LOWEST para capturar ajustes prévios.
- Cliente END: aplica a mesma matemática de `FlightMotion`, com colisão local,
  antes de `LocalPlayer.tick()` enviar a posição vanilla prevista.

**Detalhe obrigatório de integração:** `ServerGamePacketListenerImpl.tick()`
chama `resetPosition()`, depois `ServerPlayer.doTick()`, e finalmente restaura
`firstGoodX/Y/Z`. O integrador DBIL chama `connection.resetPosition()` após seu
movimento para atualizar esse baseline. Isso evita que o listener desfaça o
movimento calculado; não envia packet e não abre handshake de teleport.

## Intents e confirmação

C2S envia `sequence`, forward/strafe, subir/descer, yaw e pitch. Não envia posição,
velocidade, custo ou atributos. Inputs alterados são enviados imediatamente;
inputs mantidos têm heartbeat a cada quatro ticks. O servidor rejeita valores
não finitos e sequências repetidas/negativas. Saltos de sequência são aceitos:
packets podem ter sido descartados pelo limite de admissão ou por um burst, e a
sequência não controla custo nem distância. A admissão limita bursts por jogador.
Inputs sem heartbeat por 20 ticks param o movimento gradualmente.

O S2C `FlightAck`, enviado apenas ao dono, contém:

| Campo | Finalidade |
| --- | --- |
| serverTick | Ordenar confirmações |
| sequence | Último input aceito |
| inputTicks | Ticks já integrados usando esse input |
| position / velocity | Resultado autoritativo |
| maximumSpeed | Limite efetivo já calculado no servidor |
| flying | Estado aceito |
| hardReset | Correção/evento que deve substituir a previsão |

Há ACK regular a cada quatro ticks, sem teleport. O cliente descarta frames
confirmados e repete no máximo 64 frames locais restantes, usando colisões de
blocos sem modificar o jogador durante o replay. Pequenas diferenças são
corrigidas em passos de até 0,15 bloco por tick, respeitando colisões. Diferenças
acima de cinco blocos ou eventos marcados como hardReset descartam a previsão.

O servidor usa teleport vanilla apenas para discrepâncias acima de cinco blocos
ou ações discretas que já necessitam teleport, como o dash. O dash e knockback
atualizam o estado do integrador e enviam ACK imediato; não são anulados pela
próxima etapa de voo. Desligamento, morte, respawn e mudança de dimensão precisam
encerrar/resetar o controller e enviar o estado final antes de remover a sessão.

## Desempenho e limites

`FlightMotion` normaliza diagonais, acelera em 0,18 e desacelera em 0,30 por tick.
Speed e forma ativa modificam a velocidade calculada, mas o limite absoluto da
configuração permanece. O servidor não integra em chunks ausentes. Não há
creative flight, shader ou biblioteca adicional. O histórico ocupa 64 frames
somente no cliente local; não cria entidades, buscas globais ou efeitos visuais.

O replay é uma aproximação local de colisões de blocos. Colisões de entidades,
mudanças recentes no mundo e latência elevada podem exigir correção. A autoridade
é sempre o resultado final do servidor. O relatório de validação deve distinguir
build/servidor/protocolo de um teste visual real no Android: os primeiros não
comprovam a fluidez visual de um launcher específico.
