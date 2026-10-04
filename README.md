# Dragon Ball: Infinite Legacy — DBIL 0.2.0

Mod original para **Minecraft Java 1.20.1 / Forge 47.3.22 / Java 17**, pensado para cooperativo privado e controles por toque. Os sistemas DBIL foram escritos do zero, sem código de outros mods Dragon Ball, Luanti ou Minetest.

## O que mudou

- **Voo contínuo:** movimento local em 20 ticks/s, previsão com colisões e confirmação autoritativa. Os teleports periódicos da 0.1 foram removidos. O input real do launcher é capturado antes da movimentação vanilla.
- **Lock-on:** câmera acompanha o alvo por padrão, suavizada por frame; funciona também com a aba Ações aberta. HUD do alvo mostra HP, distância e Poder de Luta autoritativo quando disponível. A opção pode ser desligada.
- **Defesa:** guarda frontal, consumo de Stamina e quebra de guarda; ataques pesados pressionam mais a reserva.
- **Técnicas:** Ki Wave, Ki Blast e Ki Barrage de três projéteis, com seleção persistente, custos e cooldowns próprios.
- **Treino:** iniciar sparring pelo menu, três desafios com objetivos e recompensas únicas; libera técnicas e a primeira forma racial.
- **Formas:** Super Saiyajin e Potencial Liberado, com preparação interruptível, consumo de Ki, maestria, bônus temporários e aura. SSJ possui uma camada leve de cabelo dourado estilizado.

Os personagens da 0.1 são migrados para esquema 3, preservando identidade, atributos, recursos, XP, maestria e flags. Não é preciso criar outro mundo. A forma ativa volta à base ao encerrar a sessão; desbloqueios e domínio permanecem.

## Instalar e jogar

1. Use Minecraft Java 1.20.1, Forge 47.3.22 e Java 17.
2. Substitua o JAR DBIL antigo por `dbil-0.2.0.jar` em `mods/`. Cliente e servidor devem usar a mesma versão; não mantenha dois JARs DBIL juntos.
3. Entre no mundo. Novos jogadores criam nome, raça, origem e estilo. Personagens existentes continuam salvos.
4. Abra **J → Treino → Iniciar sparring** em local com chão e espaço livre. Um rival ativo por solicitação/dono, cooldown padrão de 30 s, limite de densidade e duração de cinco minutos evitam acúmulo.
5. Derrote rivais, acerte técnicas, carregue Ki e pratique voo. A aba Treino mostra todos os requisitos e progresso. As recompensas são automáticas no servidor.
6. Selecione técnicas em **J → Técnicas**. Ao terminar o desafio Despertar, use **J → Formas** para ativar SSJ (Saiyajin) ou Potencial Liberado (Humano).

O menu não pausa o mundo. Carga e guarda por botão são toggles; fechar o menu encerra esses controles. Técnicas e preparação de forma têm validação do servidor. O voo não concede creative flight nem exige `allow-flight=true`.

## Controles

| Controle | Ação |
|---|---|
| R segurado | Carregar Ki no solo |
| G | Ativar/desativar voo |
| WASD / movimento do launcher | Movimento horizontal em voo |
| Espaço / Shift | Subir / descer |
| X | Dash; Ki + Stamina |
| Ataque com mão vazia | Leve; combo de quatro etapas |
| Shift + ataque com mão vazia | Pesado; também no ar |
| Botão direito, mão vazia e lock-on | Segurar guarda |
| C | Técnica selecionada |
| V | Fixar/remover alvo, até 32 blocos e com visão |
| J | Personagem, atributos, ações, técnicas, formas, treino e opções |

As ações possuem botões na GUI. Não foram adicionadas teclas obrigatórias para cada técnica/forma. As teclas existentes podem ser remapeadas no Minecraft. Ferramentas e armas mantêm interação vanilla.

## Caminho de treino

| Desafio | Requisitos acumulados | Recompensa |
|---|---|---|
| Primeiro combate | 1 rival derrotado | Ki Blast e 40 XP base |
| Controle de Ki | 3 rivais, 5 acertos energéticos, 60 s em voo | Ki Barrage e 60 XP base |
| Despertar | 6 rivais, nível 3, 250 Ki realmente carregado, 8 acertos energéticos | Forma racial e 80 XP base |

XP aplica multiplicadores da raça/servidor. Somente derrotas e acertos confirmados contam; não basta disparar no vazio. Os counters são individuais e persistentes. O jogador continua sendo o protagonista.

Também é possível fabricar o convocador de rival (`W` trigo, `L` couro, `I` ferro):

```text
 W
LIL
 W
```

## Build

Com **JDK 17** no `JAVA_HOME`:

```sh
./gradlew build
./gradlew runGameTestServer
```

No Windows: `gradlew.bat build`. O JAR reobfuscado fica em `build/libs/dbil-0.2.0.jar`. Gradle Wrapper 8.8 e ForgeGradle 6.0.54 fixados; a primeira compilação baixa dependências oficiais. A heap de build é 2 GB e não representa a memória necessária em jogo.

Desenvolvimento: `./gradlew runClient` ou `./gradlew runServer`. Aceite o EULA para o servidor normal. GameTests usam o mundo de testes próprio. Resultados efetivamente obtidos são registrados no relatório da sessão.

## Configuração e Android

- Servidor: `<mundo>/serverconfig/dbil-server.toml`: limites, custos, cooldowns, dano, XP, PvP, velocidade de voo, guarda, formas e sparring.
- Cliente: `config/dbil-client.toml`: HUD, escala/posição, partículas, intensidade/distância/qualidade de aura e `lockOnCamera` (padrão true). A chave antiga `camera=false` não desliga o novo acompanhamento. Um valor explícito `lockOnCamera=false` é preservado.
- Nenhum shader ou biblioteca de animação obrigatório. Efeitos são limitados por distância/cadência/quantidade. Explosões e destruição não estão implementadas.

Battly/Android físico ainda precisa de validação neste dispositivo. Os testes em renderização de software no ambiente de desenvolvimento não comprovam FPS, memória ou fluidez num celular específico. Se o aparelho estiver sobrecarregado, use partículas mínimas e escala GUI adequada; a câmera de lock-on tem opção independente dos efeitos especiais.

## Comandos

`/dbil info` é público para o próprio personagem. Os demais exigem permissão 2:

- `/dbil setrace <dbil:human|dbil:saiyan> [player]`
- `/dbil setki <amount> [player]`
- `/dbil addxp <amount> [player]`
- `/dbil heal [player]`
- `/dbil power [player]`
- `/dbil learn <technique_id> [player]`
- `/dbil reset [player]`
- `/dbil debug [player]`
- `/dbil spawn [count]` (máximo oito)
- `/dbil unlockform <form_id> [player]`
- `/dbil transform <form_id> [player]`
- `/dbil mastery <form_id> <amount> [player]`

Formas: `dbil:super_saiyan`, `dbil:potential_unleashed`; `dbil:base` reverte. O comando transform respeita requisitos, unlock, custo e estado. Reset remove o personagem DBIL e reabre a criação.

## Documentação

[Arquitetura e rede](docs/ARCHITECTURE.md) · [Voo](docs/FLIGHT.md) · [Dados e migração](docs/DATA.md) · [Combate e técnicas](docs/COMBAT_TECHNIQUES.md) · [Transformações](docs/TRANSFORMATIONS.md) · [Treino](docs/TRAINING.md) · [Interface](docs/CLIENT_UI.md) · [Comandos](docs/DEBUG_COMMANDS.md) · [Roadmap](docs/ROADMAP.md)

O NPC ainda usa visual humanoide vanilla temporário, isolado do gameplay. Cabelo SSJ é estilizado e não um modelo definitivo de personagem. Outras raças, mestres, história extensa, Kamehameha/beam, Beam Clash, fusão, dimensões, Dragon Balls e destruição ficam para próximas versões.
