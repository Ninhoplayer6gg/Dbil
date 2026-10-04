# Desafios pessoais de treino — DBIL 0.3

O servidor avalia uma cadeia curta de desafios a cada segundo por jogador e concede, no máximo, uma recompensa por avaliação. Não há packet de resgate: a interface apenas mostra os objetivos e os valores recebidos no snapshot do personagem. Os contadores são individuais, cumulativos e persistem em `trainingStats`; os desafios concluídos persistem em `storyFlags`.

| Desafio | Requisitos cumulativos | Recompensa base |
|---|---|---|
| Primeiro Combate | Derrotar 1 rival de treinamento | Aprender/equipar Ki Blast e 40 XP DBIL |
| Controle de Ki | Concluir Primeiro Combate; derrotar 3 rivais; acertar 5 técnicas; voar por 1.200 ticks, equivalentes a 60 segundos a 20 TPS | Aprender/equipar Ki Barrage e 60 XP DBIL |
| Despertar | Concluir Controle de Ki; derrotar 6 rivais; alcançar nível 3; recuperar 250 Ki por carregamento ativo; acertar 8 técnicas | Desbloquear a transformação compatível com a raça e 80 XP DBIL |

Os requisitos contam o total do personagem, não exigem reiniciar cada objetivo após concluir o desafio anterior. O nível consulta diretamente o nível RPG atual. Os demais objetivos consultam `training_defeats`, `technique_hits`, `flight_ticks` e `ki_charged`. Carregar com a reserva cheia não aumenta `ki_charged`; regeneração passiva também não participa. Tempo de voo e acertos são registrados exclusivamente pela simulação do servidor. A recompensa de XP passa por `ProgressionService`, respeitando os multiplicadores racial e da configuração e os limites de progressão.

As formas não são escolhidas por comparações de raça dentro do serviço de treino. `TrainingChallenges` consulta as definições registradas em `Transformations`, filtra as raças permitidas e exige `UnlockCondition` com evaluator `dbil:training_challenge` e parâmetro `challenge=dbil:awakening`. As definições da versão atual associam Super Saiyajin aos Saiyajins e Potencial Desbloqueado aos Humanos. O desafio desbloqueia a forma; ativação, requisitos, custos e manutenção continuam sob responsabilidade do serviço de transformação.

## Caminho de feixes (0.3)

Três desafios foram acrescentados **depois** da cadeia da 0.2, sem alterar ordem, marcadores ou recompensas antigas.
Personagens migrados que já cumprem os requisitos recebem as novas técnicas na avaliação seguinte.

| Desafio | Pré-requisito | Requisitos cumulativos | Recompensa |
|---|---|---|---|
| Onda concentrada (`dbil:beam_training`) | Domínio de Ki | 12 acertos com técnicas; 400 Ki carregado; nível 2 | Kamehameha e 70 XP |
| Disparo relâmpago (`dbil:rapid_ki`) | Onda concentrada | 4 acertos com feixes; 20 acertos com técnicas; 40 golpes corpo a corpo | Masenko e 80 XP |
| Canhão explosivo (`dbil:explosive_wave`) | Despertar | 10 rivais derrotados; 8 acertos com feixes; nível 3 | Galick Gun e 100 XP |

Contadores novos, sempre produzidos pelo servidor: `beam_hits` (primeiro acerto de cada feixe em `KiBeamEntity`) e
`melee_hits` (golpe DBIL confirmado em `CombatService`). Com 6 slots, as recompensas equipam a técnica quando há espaço.

## Salvamento e recompensa única

Os marcadores são `challenge:dbil:first_combat`, `challenge:dbil:ki_control` e `challenge:dbil:awakening`. Esses nomes usam somente caracteres aceitos por `CharacterData` e ficam dentro do limite de 64 caracteres. Não foi acrescentado um novo campo de NBT nem alterado o esquema do personagem.

Antes de marcar um desafio, o servidor valida a recompensa em uma cópia dos dados: definição disponível, espaço para o marcador, espaço para desbloqueios e slots de técnica. Se não puder armazenar toda a recompensa, mantém o desafio pendente e não concede XP. A pré-validação só cria essa cópia no momento de uma possível conclusão, não a cada tick. A marcação persistente acontece antes de conceder XP e enviar notificações; avaliações seguintes e recargas de NBT reconhecem a conclusão e não repetem a recompensa. Todos esses passos ocorrem no mesmo thread do servidor.

Um save de esquema futuro protegido não recebe desafios nem mutações. Reentrar, morrer e reiniciar o servidor preservam os marcadores e contadores através da capability. O estado de cadência de avaliação é apenas de sessão e não serve como prova de conclusão.

## Extensão e validação

`ChallengeDefinition` mantém objetivos, predecessor e recompensa centralizados; cada `Objective` descreve uma chave de contador e um alvo. A GUI pode usar `TrainingChallenges.progress(data)`, `completed(data,id)`, `counter(data,key)` e `available(data,definition)` para mostrar estado sem conceder recompensas. Novos desafios devem continuar usando contadores produzidos pelo servidor e predecessores definidos, com recompensas registradas.

Os testes próprios em `ChallengeGameTests` verificam conclusão e desbloqueio, repetição após save/load, requisitos cumulativos e sequência, seleção racial de forma, bloqueio por flags/slots cheios e preservação de esquema futuro. A execução e o resultado efetivos devem ser registrados no relatório da sessão; a presença dos testes não significa que tenham passado.
