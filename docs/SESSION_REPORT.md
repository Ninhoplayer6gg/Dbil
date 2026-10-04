# Relatório da sessão — DBIL 0.1.0

Data: 2026-10-04 UTC. Projeto novo em `/workspace/dbil`, Minecraft Java 1.20.1, Forge 47.3.22, Java 17, Gradle 8.8, ForgeGradle 6.0.54. O workspace estava vazio. O MDK oficial foi usado somente para bootstrap; os sistemas DBIL foram escritos do zero.

## Implementado

Criação validada no servidor, Humano/Saiyajin, origens, cinco estilos, atributos próprios limitados, HP por Vitality, Ki/Stamina independentes, Poder de Luta baseado em atributos/reservas/vida/estado, carga com aura/pose, regeneração, voo customizado, dash, combo leve de quatro etapas, pesado, defesa e knockback, lock-on limitado, Ki Wave, NPC ativo e recompensa, progressão e dados persistentes versionados. HUD/menu/toque, efeitos leves, configs separadas e comandos administrativos também possuem código real.

Frameworks registráveis de raça, origem, técnica, NPC e aura são usados pelo gameplay. O framework de transformações possui definição/requisitos/drain/mastery/aparência/ramos, mas nenhuma transformação jogável. Metadados de beams não são executor de beam nem Beam Clash.

## Arquivos criados

58 classes Java em módulos concretos. Principais:

- `DBIL.java`, `config/ServerConfig.java`, `config/ClientConfig.java`.
- `character/CharacterData.java`, `CharacterDataMigrations.java`, `CharacterService.java`, `Origins.java`, `CombatStyle.java`.
- `capability/CharacterCapability.java`, `race/RaceDefinition.java`, `Races.java`, `stats/Stat.java`.
- `power/PowerLevelCalculator.java`, `training/ProgressionService.java`.
- `ki/KiService.java`, `stamina/StaminaService.java`, `flight/FlightService.java`, `movement/DashService.java`.
- `server/ServerActions.java`, `ServerEvents.java`, `PlayerState.java`, `ServerRuntime.java`, `network/Network.java`.
- `combat/CombatService.java`, `KnockbackService.java`, `targeting/TargetingService.java`.
- `technique/TechniqueDefinition.java`, `Techniques.java`, `TechniqueService.java`, `KiWaveEntity.java`.
- `npc/TrainingEnemy.java`, `NpcDefinition.java`, `NpcDefinitions.java`, `registry/ModEntities.java`, `ModItems.java`.
- `client/ClientState.java`, `ClientControls.java`, `ClientEvents.java`, `ClientModEvents.java`.
- `gui/CharacterCreationScreen.java`, `DBILMenuScreen.java`, `DBILHud.java`.
- `rendering/*`, `animation/PlayerPoses.java`, `PlayerPresentation.java`.
- `transformation/*`, `debug/DebugCommands.java`, `gametest/DBILGameTests.java`.
- Traduções PT-BR/EN-US, textura Ki Wave 32×32, receita/modelo/advancement do convocador, estrutura GameTest e AccessTransformer.
- README e documentação de arquitetura, dados, combate, interface, comandos, testes e roadmap.

## Arquivos modificados do bootstrap

`build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `META-INF/mods.toml`, `.gitignore`. Wrapper oficial preservado, checksum adicionado; projeto/versionamento/Java dependências fixados. Exemplos Java do MDK removidos. Licença/créditos do MDK preservados em `licenses/`.

## Controles e comandos

R segurar: Ki. G: voo. Espaço/Shift: subir/descer. W/A/S/D: movimento aéreo. X: dash. Clique com mão vazia: leve. Shift+clique: pesado. C: Ki Wave. V: lock-on. J: menu. Remapeáveis no Minecraft; menu fornece botões de toque e encerra controles de movimento/carga ao fechar.

`/dbil info` próprio é público. Com permissão 2: `setrace`, `setki`, `addxp`, `heal`, `power`, `learn`, `reset`, `debug`, `spawn`. Sintaxe completa em `DEBUG_COMMANDS.md` e README.

## Build

Comandos executados com Java 17 no `JAVA_HOME` e configuração de proxy/truststore local do ambiente:

```sh
./gradlew build
./gradlew build runGameTestServer
```

Resultado de regressão: **BUILD SUCCESSFUL** em 42 s; **todos os 19 GameTests obrigatórios passaram**. Artefato reobfuscado: `/workspace/dbil/build/libs/dbil-0.1.0.jar`.

Build final de entrega: `./gradlew build`, **BUILD SUCCESSFUL** em 13 s. SHA-256: `ad330a84acbcf8496794c5853bd8fd3fb17ff7a142c73a62f1a10bb0c162c508`. Após a regressão, somente o posicionamento/acompanhamento da câmera de cliente e a normalização dos timestamps/ordem do JAR foram refinados; o gameplay server-side permanece o validado pela suíte. O JAR final foi novamente implantado no servidor dedicado; a reconexão e a câmera na aba Ações foram aprovadas.

`build` não executa os GameTests por si só; eles foram executados explicitamente. Não há suíte JUnit disfarçada de teste funcional. Evidência resumida em `validation-logs/regression-gametest.txt`.

## Testes efetivamente realizados

- Compilação/reobfuscação Gradle, metadata/registry/resources e Java 17.
- 19 GameTests em servidor Forge: persistência/cópias independentes, migração, NBT inválido, proteção schema futuro, caps remotos, custos inválidos/insuficientes, progressão limitada, cálculo de poder, registries, capability, limites de packets, criação, lifecycle morte/clone, projétil/dano/expiração, NPC recompensa única e requisitos de transformação.
- Servidor dedicado instalado com installer Forge e **JAR reobfuscado**, sem classes do cliente do Minecraft. Inicialização e carregamento de mundo aprovados.
- Dois clientes Minecraft/Forge reais em processos independentes, conectados simultaneamente por rede local. Renderização Xvfb/Mesa llvmpipe; não eram somente FakePlayers nem servidor integrado.
- Criação por cliques de Kairo Earth (Humano/Terra/Equilibrado) e Raku Survivor (Saiyajin/Sobrevivente/Lutador), estados e HUDs separados.
- HUD, menu, atributos e botões de toque em 854×480 com GUI 240 unidades; HUD corrigida para não cobrir hotbar.
- Carga de Ki real, consumo de voo/dash, subida/descida e movimento por menu. Voo prolongado >30 s com `allow-flight=false`, sem expulsão por floating.
- Visual remoto de carga/aura e pose observado pelo segundo cliente.
- Morte normal inicial revelou bug; após correção, ambos reentraram, deram respawn e repetiram morte deliberada sem desconexão. Nome/raça/origem/estilo/atributos/Ki Wave preservados.
- NPC com 40 HP perseguiu/atacou, lock-on selecionou, Ki Wave causou 9 de dano observado, leve/pesado produziram knockback. Primeira derrota concedeu 39 XP ao Humano; Saiyajin manteve 0 XP.

A validação real também comprovou quatro golpes do combo completo: NPC com 40 HP passou a 25,21 HP. Quatro derrotas efetivas por ataques DBIL levaram o Humano ao nível 2, com 31 XP restantes, Max Ki 125, Max Stamina 103 e Poder de Luta base 1174. Não foi utilizado addxp para esse resultado; heal foi usado entre encontros para preparar as repetições do teste. O Saiyajin também derrotou um NPC por combate e recebeu seus próprios 35 XP. Ambos voaram, subiram e dispararam Ki Wave simultaneamente, com reservas próprias. PvP desativado: quatro leves, pesado e Ki Wave do parceiro conservaram HP 29,1 do Saiyajin. A última reconexão, às 02:49:54/55 UTC, confirmou esses valores no JAR final após reiniciar o servidor: Humano nível 2/31 XP/Max Ki 125/Max Stamina 103, Saiyajin nível 1/35 XP/Max Ki 120/Max Stamina 100. A maestria adquirida elevou os poderes base a 1175 e 1188. A criação não reabriu e os estados transitórios de voo/carga/combo/alvo foram corretamente limpos.

A câmera opcional acompanhou um alvo reposicionado de frente para esquerda, mudando a rotação real no servidor de 0° para 90° com a aba Ações aberta. Esse teste usou um NPC sem IA apenas para isolar a câmera. A derrota adicional por botões elevou o estado final do Humano para nível 2/70 XP; o Saiyajin permaneceu nível 1/35 XP. Os encontros anteriores com NPCs normais são a evidência de perseguição/ataque. Clientes encerraram normalmente e o servidor salvou o mundo. Screenshots e detalhes em `CLIENT_TEST.md`.

## Problemas encontrados e corrigidos

1. Java disponível no ambiente era 21; obtido JDK 17 e usado para desenvolvimento/build.
2. Wrapper Java não herdou proxy de shell automaticamente; configurado proxy/truststore somente nas ferramentas locais, sem embutir esse endereço no projeto.
3. Anti-floating vanilla ignora noGravity do jogador: exceção estreita via AT para voo DBIL autorizado, sem creative flight.
4. Knockback de jogador em voo era sobrescrito pelo integrador: impulso integrado ao estado de voo.
5. Reset/adminteleport precisavam encerrar estados transitórios; lifecycle ajustado.
6. Morte invalidava capability antes do tick: guarda de vida/removal antes da leitura; provider renova LazyOptional para reviveCaps/clone. Falha real reproduzida em dois clientes, corrigida, testes de regressão e respawn real aprovados.
7. HUD inferior esquerda cobria hotbar: âncora normalizada configurável, padrão superior esquerdo.

Avisos de ambiente: autenticação Yggdrasil indisponível no teste local offline, picos de carga/software GL e avisos vanilla moved-too-quickly em respawn/teleports administrativos do roteiro. Não são escondidos nem usados para afirmar teste de autenticação online/desempenho Android.

## Limitações atuais

Android físico e launcher específico e rede com latência real ainda não foram testados. PCs modestos não foram benchmarkados: software GL em servidor cloud comprova carregamento/GUI, não desempenho móvel. Configs/efeitos foram limitados para facilitar esses próximos testes.

NPC é terrestre e usa visual humanoide/zombie vanilla temporário isolado. Som usa eventos nativos; o dispositivo de áudio do teste gráfico era virtual. Voo recebe correções de posição cinco vezes por segundo e deve ser refinado por medições de suavidade/latência reais. Combate tem animação vanilla de golpes e poses DBIL de preparação; não possui ainda animações corporais completas.

Somente Ki Wave tem executor de técnica. Transformações, mestres, quests, outras raças, Beam Clash, fusão, dimensões, timeline, Dragon Balls, Scouter, destruição, cabelo/cauda e customização ampla permanecem milestones futuras.

## Próximo passo recomendado

Testar este JAR no launcher Android usado pelos dois jogadores: validar toque, legibilidade da HUD e controle/correções de voo com rede real; registrar resolução/Java/backend gráfico/FPS/memória e corrigir a base. Só então iniciar Super Saiyajin com drain/mastery server-side e camadas de aparência.
