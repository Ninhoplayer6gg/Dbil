# Dados de personagem, atributos e progressão

## Autoridade e armazenamento

`CharacterCapability` anexa um `ICapabilitySerializable<CompoundTag>` a cada
`Player`. O Forge persiste esse provider nos dados normais do jogador com a chave
`dbil:character`. Não há um banco paralelo, XP vanilla ou Hunger usado como Ki/
Stamina. `CharacterData` não importa classes de cliente. O servidor é o único
responsável por criar o personagem, gastar recursos, conceder experiência e
alterar atributos; a instância em `ClientState` apenas lê snapshots.

O provider invalida seu `LazyOptional` junto com a entidade e prepara uma nova
referência ao mesmo objeto de dados. A proteção de capabilities da entidade Forge
continua bloqueando acesso até `reviveCaps()`. Isso é necessário porque
`reviveCaps()` restaura a proteção da entidade, mas não revalida um `LazyOptional`
já invalidado. Referências antigas permanecem inválidas. No clone de jogador, o
lifecycle revive temporariamente o original, copia `CharacterData`, invalida-o
novamente e reaplica modificadores transitórios no novo jogador.
Dados duráveis e estado de combate/voo são separados. Assim a cópia não depende de
um modelo permanente de respawn: um futuro sistema de Outro Mundo pode reutilizar
os dados e escolher suas próprias regras de recuperação.

## Esquema atual: versão 4 (DBIL 0.3)

`save()` produz os seguintes campos NBT:

| Campo | Tipo | Regra |
| --- | --- | --- |
| `schemaVersion` | int | Atualmente `4` |
| `characterCreated` | boolean | Criação validada pelo servidor |
| `characterName` | string | Até 24 pontos de código, sem controles/cores |
| `race` | string ResourceLocation | `dbil:human` ou `dbil:saiyan` no bootstrap |
| `origin` | string ResourceLocation | Compatível com a raça |
| `combatStyle` | string | balanced, brawler, speed, ki_specialist, defensive |
| `level` | int | 1 até `maxLevel` do servidor |
| `experience` | long | Progresso dentro do nível atual |
| `basePower`, `currentPower` | long | Valores derivados, limitados a 1 bilhão |
| `attributes` | compound | Os oito atributos abaixo, como double |
| `maxKi`, `maxStamina` | double | Capacidades derivadas dos atributos; duplicadas para leitura externa |
| `currentKi`, `currentStamina` | double | Recursos entre zero e a capacidade |
| `mastery` | compound | ID → double entre 0 e 100; até 128 entradas |
| `unlockedTechniques` | lista string | Até 128 IDs |
| `equippedTechniques` | lista string | Até 6 IDs (0.3; antes 4), obrigatoriamente desbloqueados |
| `selectedTechnique` | string ResourceLocation | Técnica equipada selecionada, persistente; padrão Ki Wave |
| `unlockedTransformations` | lista string | Até 128 IDs |
| `currentTransformation` | string ResourceLocation | `dbil:base` ou ID desbloqueado |
| `trainingStats` | compound | Chave → double entre 0 e 1 milhão; até 128 entradas |
| `storyFlags` | lista string | Até 256 flags de 64 caracteres |
| `appearance` | compound | Aparência visual (0.3), descrita abaixo |

Os nomes em `attributes` são `strength`, `defense`, `speed`, `ki_power`,
`ki_control`, `max_ki`, `max_stamina`, `vitality`. Todos ficam entre 1 e
`ServerConfig.maxAttribute`. Na primeira versão, o mesmo limite também restringe
as reservas. Valores NaN/infinito e custos negativos são rejeitados/normalizados.
IDs lidos são limitados a 128 caracteres. As coleções expostas são views somente
leitura, criadas uma vez por personagem.

HP continua sendo vida real de Minecraft, determinada pelo servidor. O DBIL
acrescenta `min(80, Vitality × 0,7)` a MAX_HEALTH com UUID fixo. Speed concede
um modificador percentual moderado, limitado a 25%. Reaplicar não duplica
modificadores. Dano, defesa, voo e técnicas consultam os atributos próprios. Formas usam um UUID separado para velocidade no solo; reaplicar/reverter não acumula bônus.

### Aparência (`appearance`, schema 4)

`CharacterAppearance` é um record imutável; todo caminho de construção (NBT, rede, tela) limita os valores, então
um save ou pacote nunca produz um índice que o renderizador desconheça.

| Campo | Tipo | Regra |
| --- | --- | --- |
| `bodyType` | int | 0 = Tipo A (braços Steve), 1 = Tipo B (braços Alex) |
| `skinTone` | int RGB | 24 bits; a tela oferece 8 tons |
| `eyeStyle`, `eyeColor` | int, int RGB | 5 formatos; cor livre de 24 bits |
| `eyebrowStyle`, `mouthStyle` | int | 4 sobrancelhas, 3 bocas |
| `hairstyle` | string ResourceLocation | `dbil:short`, `spiky`, `spiky_tall`, `messy`, `medium`, `straight`, `bald`; desconhecido → `short` |
| `hairColor` | int RGB | 24 bits (o Super Saiyajin usa o dourado próprio) |
| `outfit` | string ResourceLocation | `dbil:training_gi`, `battle_armor`, `fighter_vest`, `sleeveless`; desconhecido → padrão |
| `outfitPrimary`, `outfitSecondary`, `outfitAccent` | int RGB | Três cores da roupa |
| `accessories` | int (bits) | munhequeiras, faixa, cauda; a cauda só é mantida para Saiyajins |

A aparência é sincronizada para todos que rastreiam o jogador (`AppearanceSync`) e editada pelo dono com o pacote 7,
validado no servidor e aceito só para personagens criados. Trocar de raça reaplica `forRace` (Humano perde a cauda).

## Migração e proteção de versões futuras

`CharacterDataMigrations.upgrade()` trabalha em uma cópia do NBT recebido:

1. Versão ausente/0 → 1: move atributos antigos em campos planos para o compound
   `attributes`; capacidades antigas `maxKi`/`maxStamina` também são preservadas.
2. Versão 1 → 2: acrescenta origem, estilo e forma base quando faltam.
3. Versão 2 → 3: acrescenta técnica selecionada; se inválida, usa a primeira equipada válida.
4. Versão 3 → 4 (0.3): acrescenta `appearance` com o padrão da raça; progresso, técnicas, formas e maestria
   ficam intactos.
5. `CharacterData.load()` valida os valores e aplica limites atuais do servidor.

Raça desconhecida no esquema suportado volta a Humano; origem ausente,
desconhecida ou incompatível volta a Guerreiro da Terra, que aceita ambas as
raças. O estilo inválido volta a Balanced. As técnicas equipadas são intersectadas
com as desbloqueadas. Equipar a primeira técnica válida corrige a seleção se não existia seleção equipada. Esses fallbacks são de dados; criação por packet rejeita
seleções inválidas em vez de escolher silenciosamente outra opção.

**Uma versão de esquema maior que 4 não é regravada como versão 4.** O NBT
original fica preservado integralmente pelo provider, `compatibleSchema()` passa
a ser false e `created()` fica false para impedir ações DBIL. Os mutadores e o
reset ficam bloqueados. `CharacterService.create()` também rejeita criação sobre
esses dados. É necessário carregar uma versão do mod que reconheça esse esquema.
O log informa a proteção uma vez por versão observada naquela instância; snapshots
repetidos não geram mensagens a cada tick.

## Sincronização de limites

`saveSnapshot()` acrescenta `snapshotAttributeLimit` e `snapshotLevelLimit` ao
NBT enviado ao cliente. `loadSnapshot()` usa esses limites para renderizar
corretamente valores de um servidor remoto, mesmo que a configuração local seja
diferente. A persistência usa `save()`/`load()` e **não** confia em limites trazidos
pelo NBT salvo: sempre considera a configuração efetiva do servidor. Esses campos
de transporte não são persistidos no esquema atual.

## Criação e diferenças iniciais

`CharacterService.create()` valida nome, raça registrada, origem compatível,
estilo conhecido, estado vivo e personagem ainda não criado. O servidor calcula
os valores; o cliente não envia atributos. A criação concede e equipa
`dbil:ki_wave` e enche recursos/vida.

- Humano: Ki Control 12, custo energético × 0,92, experiência × 1,10 e crescimento
  de Ki Control × 1,15.
- Saiyajin: Strength/Vitality 12, crescimento de Strength × 1,15 e Vitality × 1,10.
- Guerreiro da Terra: Defense +1; disponível às duas raças.
- Saiyajin sobrevivente: Vitality +1; apenas Saiyajin.
- Estilos distribuem pequenos bônus/penalidades iniciais. Não restringem técnicas
  ou crescimento posterior.

`Races`/`Origins` utilizam `DefinitionRegistry`; novas definições são registradas
no bootstrap. `RaceDefinition` reúne atributos, crescimento, eficiência, passivas,
técnicas raciais e transformações permitidas. Não há comparação de raça espalhada
nos serviços. Zenkai permanece futuro. Super Saiyajin e Potencial Liberado possuem execução real descrita em TRANSFORMATIONS.md.

## Experiência e poder

`ProgressionService.award(ServerPlayer, int)` recebe recompensas calculadas no
servidor. Aplica o multiplicador de configuração e da raça, registra treino de
combate, atualiza atributos/poder e sincroniza o dono. XP necessária por nível:
`80 + 45 × nível`; o nível 1 requer 125 XP. Cada nível acrescenta 0,65 aos atributos
normais, 5 ao Max Ki e 3 ao Max Stamina, ajustados pelo crescimento racial e pelo
limite. A progressão não depende de encantamento ou orbs de XP vanilla.

`PowerLevelCalculator` calcula potencial a partir dos oito atributos e da maestria.
O bônus usa `1 + min(100, soma das maestrias válidas positivas) / 400`, limitado
portanto a 25%. Aprender ou praticar uma nova técnica com maestria baixa não reduz
o poder já adquirido: o agregado é monotônico, sem calcular média por quantidade
de técnicas. Valores não finitos ou negativos não contribuem. A saída atual
também considera proporção de Ki, Stamina, HP, carga e voo. O nível
não é convertido diretamente em poder. `registerModifier()` permite que futuras
formas, buffs e supressão alterem a saída por meio de multiplicadores, sem colocar
lógica dentro da classe Player. As formas registradas compõem multiplicadores temporários de poder, dano, defesa e velocidade, preservando atributos base.

## Validação

Os GameTests do projeto exercitam save/load, migração, limites e economia de
recursos. A execução bem-sucedida, quando disponível, deve ser registrada no
relatório de validação; a presença deste documento não significa que os testes
foram executados. Persistência entre sessões e sincronização de dois clientes
precisam também da execução dedicada e do checklist manual de multiplayer.
