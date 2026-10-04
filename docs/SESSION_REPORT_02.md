# DBIL 0.2.0 — entrega da sessão

## Implementado nesta atualização

Voo com aceleração/desaceleração, predição local, reconciliação com servidor e colisão; entradas sequenciadas e sincronização limitada. Câmera de lock-on acompanha o alvo por frame, ativada por padrão mesmo com a configuração antiga camera=false. Guarda frontal com stamina e quebra; Ki Blast e Ki Barrage, seleção persistente de técnica; três desafios de treino com recompensas únicas; sparring pelo menu sem OP; Super Saiyajin e Potencial Liberado com requisitos, consumo de Ki, maestria e retorno à base. Auras moderadas e cabelo SSJ procedural. Dados migrados para schema 3.

## Arquivos principais

Criados: FlightMotion, ClientFlightController, TargetCamera, GuardService, TrainingSessionService, desafios e serviços de transformação, GameTests novos, FLIGHT.md, TRANSFORMATIONS.md, TRAINING.md e documentação 0.2.

Modificados: FlightService, CharacterData, CharacterService, PowerLevelCalculator, ClientControls, ClientState, ServerEvents, networking/protocolo 2, técnicas/projéteis, menu/HUD/renderers, configs, comandos, traduções e README. Fontes completas em src/main/java/dev/dbil.

## Controles

J: menu DBIL; R segurado: carregar Ki; G: alternar voo; WASD: movimento; Espaço/Shift: subir/descer; Z: dash; V: lock-on; C: técnica selecionada; clique esquerdo: ataque leve; Shift + clique esquerdo: pesado; botão direito com mão vazia e alvo: guarda. A aba Ações oferece botões de toque, incluindo guarda e movimentação. Técnicas, Formas e Treino têm suas próprias abas.

## Comandos

/dbil info; setrace; setki; addxp; heal; power; learn; reset; debug; spawn; unlockform; transform; mastery. Mutações exigem permissão administrativa nível 2. Sintaxe completa em docs/DEBUG_COMMANDS.md.

## Build

Comando: ./gradlew build runGameTestServer, com Java 17, Gradle Wrapper e proxy de dependências configurado no ambiente.
Resultado: BUILD SUCCESSFUL in 2m 3s. All 50 required tests passed.
JAR: build/libs/dbil-0.2.0.jar, copiado para /workspace/artifacts/dbil/dbil-0.2.0.jar.

## Testes realizados

50 GameTests em servidor dedicado de desenvolvimento: dados/migração, atributos/poder, networking, voo, guarda, técnicas, transformações e desafios. Servidor Forge dedicado com JAR empacotado e dois clientes reais conectados; migração dos dois personagens 0.1 preservou identidade, raça, origem, atributos, nível/XP e técnicas. Câmera acompanhou alvo movido administrativamente, com jogo normal e aba Ações aberta. Voo misto por 31 segundos (avançar/subir/descer/frear) registrou zero correções fortes do controlador. Sparring solicitado por jogador sem OP criou rival que perseguiu e atacou. Guarda e consumo de stamina observados. Rival derrotado por ataques DBIL; XP 70→153, primeiro desafio concluído e Ki Blast desbloqueada/equipada. Para encurtar esse combate, o teste ajustou administrativamente posição e HP do rival para 10; a morte foi causada pelo jogador. Requisitos de nível e desbloqueios das duas formas também foram preparados administrativamente; ativação visual final não foi concluída nesta sessão.

A execução gráfica utilizou o primeiro JAR 0.2.0 da sessão. O JAR final acrescenta a correção do bônus de maestria do Poder Base e passou novamente nos 50 testes; não houve novo reinício gráfico após essa alteração. Não se afirma teste completo em jogo de todas as novas técnicas/formas.

## Problemas encontrados

Erros iniciais de compilação e três fixtures de teste foram corrigidos; build final e testes passaram. Houve avisos de deslocamento vanilla após teletransportes administrativos de longa distância. O ambiente offline registrou erro de consulta das chaves públicas Yggdrasil, sem impedir as conexões locais. Avisos de depreciação Gradle/Forge também aparecem. Nenhum crash DBIL observado nesses testes.

## Limitações e próximo passo

Android físico/Battly não foi testado neste ambiente. Os clientes usaram Xvfb/Mesa software em Linux; isso não comprova FPS ou suavidade no celular. A atualização é incremental, com assets temporários/procedurais e sistemas iniciais. O fechamento da validação visual de Ki Blast/Barrage e das duas transformações ficou pendente quando o usuário pediu entrega imediata. A rodada seguinte deve começar testando voo e câmera no Battly solo, depois concluir essas verificações e ajustar balanceamento.

## Instalação

Minecraft Java 1.20.1, Forge 47.x e Java 17. Remova o JAR DBIL antigo da pasta mods e coloque somente dbil-0.2.0.jar. Faça uma cópia do mundo antes de atualizar. Em multiplayer ambos precisam da mesma versão. Os personagens anteriores migram ao carregar, sem exigir recriação.
