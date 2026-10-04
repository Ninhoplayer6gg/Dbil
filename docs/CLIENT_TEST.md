# Testes gráficos reais e multiplayer

## Resultado após as correções

Dois clientes Minecraft Java 1.20.1 com Forge 47.3.22 foram abertos de verdade e conectaram a um servidor dedicado instalado pelo instalador oficial do Forge, executando o JAR reobfuscado de distribuição. A renderização usou Xvfb/Mesa llvmpipe nesta máquina; isso verifica execução e interfaces, sem representar desempenho de Android ou GPU móvel.

Foram verificados em jogo:

- Criação pela GUI em português a 854×480: `DBILTestOne` criou **Kairo Earth**, Humano, Guerreiro da Terra, Equilibrado; `DBILTestTwo` criou **Raku Survivor**, Saiyajin, Sobrevivente, Lutador. Nome, raça, origem, estilo e atributos são individuais.
- HUD de HP/Ki/Stamina/poder/técnica/cooldown/voo, quatro abas do menu e botões grandes de ações. O HUD corrigido fica no canto superior esquerdo e deixa a hotbar livre.
- Ki carregado por R e pelo botão de toque; ganho calculado no dedicado. Em um teste, o observador Two consultou One carregando: Ki passou de 10 para 54,8 em aproximadamente dois segundos. Two mantinha seu próprio Ki/poder. Two viu pose e partículas cianas de One.
- Voo por G, subir por Espaço, controles virtuais de subir/descer/avançar e dash X. One permaneceu voando por mais de 30 segundos no dedicado com `allow-flight=false`, com consumo de Ki e sem kick por voo. Após dash, a consulta mostrou Stamina 88/100.
- Lock-on V com indicador e distância; câmera opcional habilitada pela aba Opções. Ela vem desativada por padrão e exige escolha do jogador. Parte das tentativas iniciais de ataque não acertou porque o operador do teste presumiu orientação automática enquanto essa opção estava desligada; não foram contadas como acertos.
- NPC de treinamento invocado com 40 HP: perseguiu jogadores, atacou, sofreu knockback, recebeu dano físico e de Ki Wave e morreu. Um NPC também matou One durante um teste, comprovando que é um inimigo ativo.
- Combo de quatro ataques leves pelos botões de toque: NPC inteiro passou de 40 para 25,209997 HP, dano de 14,79 consistente com três leves e finisher. Ataque pesado e knockback foram exercitados em outros encontros.
- Ki Wave carregou, consumiu Ki, exibiu cooldown/partículas e atingiu NPC. Um primeiro acerto reduziu 40 para 31 HP. Foram usados ataques reais para as derrotas; não foi usado `/kill` em NPC DBIL nem `/dbil addxp` para a progressão observada.
- One derrotou quatro NPCs e passou a nível 2, XP 31, Max Ki 125 e Max Stamina 103. Two derrotou seu próprio NPC e recebeu XP 35, permanecendo nível 1. A progressão continuou individual.
- One carregou Ki enquanto Two lutava. Depois ambos ativaram voo, subiram e dispararam Ki Wave durante a mesma sessão; consultas do dedicado mostraram ambos em voo com recursos independentes. Não se afirma acerto aéreo em NPC: as tentativas aéreas anteriores não produziram evidência conclusiva de dano.
- PvP desativado: HP de Two foi 29,1 antes e depois de quatro ataques leves, um pesado e Ki Wave de One.
- Reentrada após desligar/reiniciar clientes e servidor preservou personagens. Reentrada com jogadores mortos foi seguida por respawn sem perder dados. Morte deliberada de One (23 segundos aguardando na tela de morte) e Two, além de morte em combate, não causou desconexão após a correção.

Setup administrativo dos testes: heal/setki e teleports reposicionaram/restauraram os jogadores entre encontros; invocação de NPC criou os oponentes. Isso é preparação do teste, não recompensa ou dano de combate. Mobs vanilla do mundo flat foram removidos e o spawning natural desativado para não interferir.

## Problemas encontrados e corrigidos

O primeiro teste revelou um erro crítico: cerca de um segundo depois de morrer, ambos os jogadores desconectavam com `Internal server error`. O dedicado tentava obter a capability já invalidada do jogador morto em `ServerEvents.tick`. A correção foi compilada e verificada com reentrada, respawn, mortes deliberadas e morte real para NPC. Não se observou novo erro DBIL no log do dedicado corrigido.

O primeiro HUD padrão inferior sobrepunha a hotbar a escala GUI 2; o padrão foi movido para cima com posição normalizada configurável e conferido por screenshot em jogo.

A ferramenta inicial de teste usou simulationDistance 3, abaixo do mínimo vanilla 5; foi corrigida para 5. Consultas vanilla de autenticação/chaves públicas/perfis falharam no ambiente de contas offline, mesmo depois de configurar o proxy. Houve avisos vanilla ocasionais de `moved too quickly` durante teleports/respawn administrativo; não houve kick nem erro DBIL associado. Avisos de shaders/sounds vanilla, recursos `union:` de desenvolvimento, versão Forge mais recente disponível e correção de novas chaves padrão de configuração foram separados de falhas do mod.

## Reteste do JAR final

O JAR de `build/libs/dbil-0.1.0.jar` e o instalado em `mods/` do dedicado possuíam o mesmo SHA-256:

```text
ad330a84acbcf8496794c5853bd8fd3fb17ff7a142c73a62f1a10bb0c162c508
```

Depois de salvar, desligar e reiniciar o dedicado com esse JAR, os dois clientes também foram reiniciados. Às 02:49:54/55 UTC de 2026-10-04, consultas em jogo confirmaram One nível 2/XP 31/Max Ki 125/Max Stamina 103 e Two nível 1/XP 35/Max Ki 120/Max Stamina 100. Nomes, raças, origens, estilos e técnicas permaneceram salvos. Ambos entraram sem tela de criação e sem estados transitórios de voo/carga/combo/alvo.

A câmera opcional foi verificada com a aba Ações aberta: um NPC com `NoAI:1b` foi usado exclusivamente para isolar a orientação de câmera. O observador Two teletransportou esse alvo da frente para a esquerda de One; a rotação de One, consultada pelo servidor, mudou de `[0.0, 12.68]` para `[89.999985, 12.68]` sem fechar a aba. Isso confirma acompanhamento de alvo enquanto os botões de toque estão acessíveis, com a opção de câmera ligada. As outras telas não foram alteradas para seguir alvo.

Ainda na aba Ações, One acionou quatro leves, pesado e Ki Wave pelos botões, derrotando esse alvo controlado. Esse teste adicional concedeu mais 39 XP; o estado final ficou One nível 2/XP 70 e Two nível 1/XP 35. O alvo sem IA não foi usado como evidência de perseguição ou ataque — esses comportamentos foram verificados nos encontros anteriores com NPCs normais.

Não se observou erro DBIL no log desse dedicado final. O erro vanilla de consulta a chaves públicas de autenticação continuou restrito ao ambiente de contas offline. Logs foram preservados em `dedicated-release-pass.log` nas ferramentas da sessão. Ambos os clientes desconectaram ao encerrar os testes; os logs registram saída normal, e os processos clientes/Xvfb foram encerrados.

Screenshots copiadas para o projeto:

- [Criação de Humano](screenshots/criacao-humano.png)
- [Criação de Saiyajin](screenshots/criacao-saiyajin.png)
- [Humano: persistência no JAR final](screenshots/humano-persistencia-final.png)
- [Saiyajin: persistência no JAR final](screenshots/saiyajin-persistencia-final.png)
- [Menu Ações no JAR final](screenshots/acoes-camera-final.png)
- [Aura vista pelo outro jogador](screenshots/aura-multiplayer.png)

## Como este ambiente foi preparado

Ambiente: Debian 13, Java 17 local, sem acesso administrativo. Xvfb, xdotool e glxinfo foram extraídos de pacotes oficiais Debian em `/workspace/.tools/client-runtime/`. Mesa/X11/ImageMagick já estavam instalados. São ferramentas efêmeras de validação e não entram no mod ou no JAR.

Xvfb mantém um display virtual 1280×720. `glxinfo` confirmou OpenGL 4.5 core via Mesa llvmpipe. O display deve ser mantido em uma sessão de terminal:

```sh
/workspace/.tools/client-runtime/scripts/start-display.sh
```

O comando `./gradlew prepareRunClient` concluiu com sucesso. Os argumentos efetivos de desenvolvimento foram registrados para esta sessão e permitem iniciar clientes independentes sem deixar Gradle aberto:

```sh
source /workspace/.tools/client-runtime/scripts/env.sh
/workspace/.tools/client-runtime/scripts/launch-client.py DBILTestOne /workspace/.tools/client-one
# Em outro terminal:
/workspace/.tools/client-runtime/scripts/launch-client.py DBILTestTwo /workspace/.tools/client-two
```

Os clientes usam 854×480, escala GUI 2, distância de renderização 3, distância de simulação 5, 30 FPS e heap máxima 1200 MiB. `env.sh` configura caminhos/display/software GL e o truststore Java do ambiente; o script usa o proxy existente. Nenhuma credencial é necessária para o servidor local de teste com `online-mode=false`.

Screenshots e logs estão em `/workspace/.tools/client-runtime/`, incluindo `dedicated-first-pass.log` (falha original) e `dedicated-fixed-pass.log` (morte/combate/progressão após a correção). O servidor dedicado usa o JAR normal; os clientes de teste usam classes/recursos de desenvolvimento equivalentes compilados pelo projeto.
