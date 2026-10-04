# Transformações jogáveis — DBIL 0.3

As regras de gameplay da 0.2 abaixo continuam válidas. A 0.3 acrescenta a apresentação completa e o efeito da
maestria sobre o controle de Ki (seção "Apresentação e maestria na 0.3").

O servidor controla ativação, interrupção, Ki, drain e domínio. O cliente solicita uma forma e recebe snapshots para HUD, aura e aparência. Atributos base e reservas não são multiplicados nem regravados; o combate, a defesa e o movimento consultam modificadores temporários.

| Forma | Raça | Força | Ki Power | Defesa | Velocidade | Ki de ativação | Drain/tick |
|---|---|---:|---:|---:|---:|---:|---:|
| Super Saiyajin — `dbil:super_saiyan` | Saiyajin | ×1,50 | ×1,60 | ×1,20 | ×1,20 | 25 | 0,18 |
| Potencial Despertado — `dbil:potential_unleashed` | Humano | ×1,25 | ×1,35 | ×1,15 | ×1,10 | 20 | 0,13 |

Os valores são antes dos multiplicadores de configuração. HP máximo, Max Ki e Max Stamina permanecem com suas capacidades base nesta versão. Poder atual recebe um modificador calculado a partir da distribuição de atributos do personagem; o modificador não regrava o Poder base. Aprender domínio continua contribuindo ao modelo geral de potencial.

## Desbloqueio e ativação

As duas formas exigem nível 3 e desbloqueio permanente pelo desafio **Despertar**, `dbil:awakening`. As definições usam o avaliador `dbil:training_challenge` e o parâmetro `challenge=dbil:awakening`. O serviço de desafios concede apenas a forma compatível com a raça do personagem. A leitura de uma definição ou o atendimento do nível mínimo sozinho não concede a forma.

Solicitar uma forma através do menu inicia uma preparação de 30 ticks, aproximadamente 1,5 segundo com o servidor a 20 TPS. O Ki de ativação é pago uma só vez ao aceitar a solicitação. Não é possível empilhar solicitações, técnicas, guarda ou outra forma durante a preparação. Carregamento comum de Ki é encerrado ao iniciá-la.

Dano à vida, movimento superior a 0,25 bloco desde a posição inicial, entrar em água, dormir ou ocupar um veículo interrompem a preparação. O custo já pago e o intervalo de ativação permanecem; uma interrupção não concede domínio. O intervalo impede reativações repetidas e dura 80 ticks após o tempo reservado de preparação.

`dbil:base` reverte a forma ou cancela uma preparação. Ki insuficiente para o drain reverte automaticamente. Login, logout, morte e reset de sessão também restauram a forma base, preservando unlocks e domínio. A arquitetura de dados guarda o campo da forma atual, mas sessões novas não retomam uma transformação ativa.

## Domínio

Cada forma tem domínio independente, de 0 a 100, persistido por ID em `CharacterData.mastery()`. Iniciar, cancelar ou completar a ativação não concede domínio.

- Enquanto a forma está ativa: +0,10 a cada 100 ticks, aproximadamente cinco segundos.
- Após golpes DBIL confirmados: +0,16, no máximo uma vez a cada 20 ticks. Pacotes recebidos não concedem esse ganho.
- O ganho é limitado a 100. Processar novamente a mesma janela de tempo não duplica o ganho.

O domínio interpola gradualmente a eficiência. Com domínio 100, a preparação leva 40% do tempo base; o drain de Super Saiyajin usa 55% do valor base e Potencial Despertado usa 60%. O custo inicial de Ki permanece igual. A regeneração normal de Ki e o custo de voo continuam sendo sistemas independentes; o consumo líquido depende das ações do jogador e das configurações do servidor.

## Configuração e API

`transformationCostMultiplier` ajusta o custo de ativação e `transformationDrainMultiplier` ajusta o consumo contínuo, ambos com padrão 1. Os valores pertencem à configuração de servidor.

`Transformations.bootstrap()` registra as formas, o modificador de poder e congela o registro de maneira idempotente. Cada definição contém raça, requisitos, multiplicadores, aparência, ramo e condição de desbloqueio. Não há comparações de raça espalhadas pelo serviço de combate.

`TransformationService` oferece `start`, `revert`, `tick`, `resetSession`, `multiplier`, `powerMultiplier` e `recordCombat`. O último método é chamado exclusivamente por sistemas de combate após um acerto aceito pelo servidor. Estado de preparação e cooldown ficam em `PlayerState`, fora do save permanente. `currentTransformation`, unlocks e domínio pertencem aos dados de personagem.

## Validação

`TransformationGameTests` testa rejeição por raça, bloqueio, nível e Ki; custo pago uma vez; bootstrap repetido; poder temporário; ausência de acumulação nos atributos; interrupção; drain e exaustão; domínio limitado por tempo; eficiência e preservação de progresso no reset. A existência desses testes não substitui o resultado real da execução nem o teste visual em Battly/Android.

## Apresentação e maestria na 0.3

Tudo abaixo é cliente, dirigido pelo `StateSnapshot` (forma, ticks de preparação, maestria da forma) e pelos eventos
`TRANSFORM_COMPLETE`/`TRANSFORM_REVERT` enviados pelo servidor.

| Fase | O que acontece |
|---|---|
| Preparação (0–40%) | pose de poder (`CharacterAnimator`), tremor leve do corpo, expressão de grito, aura dourada crescendo, poeira no chão |
| 40–85% | o cabelo voxel alterna entre a variante base e a Super Saiyajin, cada vez mais rápido; descargas na aura; FOV pulsando |
| 85–100% | cabelo SSJ fixo, olhos SSJ, grito no ápice, brilho máximo |
| Conclusão | explosão de poder (flash, anel, onda de choque, partículas), som `transform_complete`, tremor e pulso de FOV; banner da forma na HUD |
| Ativa | cabelo SSJ do estilo escolhido (cada um dos 7 tem variante própria), olhos/sobrancelhas SSJ, aura dourada com descargas, emblema com estrela |
| Reversão | evento `TRANSFORM_REVERT`, flash curto e volta do visual base |

`transformationEffects=REDUCED` (cliente) mantém a troca visual mas reduz explosão, tremor e FOV.
O Potencial Liberado (Humano) usa a aura branca e mantém o cabelo do personagem.

**Maestria** (0–100, já persistida desde a 0.2):

- **Baixa**: preparação mais longa e drain maior (regras da 0.2); a aura oscila, falha e solta flashes/descargas
  irregulares; técnicas custam até **+25%** de Ki (`TransformationService.controlPenalty`, aplicado em
  `TechniqueService`).
- **Alta**: preparação até 60% mais curta, drain menor, aura estável e contida, sem penalidade de custo.

Novas formas registram uma `TransformationDefinition` (gameplay) e um `AuraStyle` (cliente); cor do evento em
`TransformationService.formColor`.

## Fora do escopo

Não incluem SSJ2, SSJ3, formas divinas, forma personalizada, Oozaru ou Beam Clash.
