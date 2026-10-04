# Validação de DBIL 0.2.0

Build, GameTests, servidor dedicado, cliente gráfico e Android são evidências distintas. Um JAR compilado não comprova suavidade de voo, câmera ou controles em um launcher móvel.

## Estado observado nesta revisão

- `./gradlew build runGameTestServer`: **BUILD SUCCESSFUL em 2m 3s**.
- **Todos os 50 GameTests obrigatórios passaram**, incluindo os dois testes adicionais de Poder de Luta. Confirmação no log `/tmp/dbil-v02-final-build-tests.log`, 2026-10-04 às 14:12:33 UTC.
- JAR: `build/libs/dbil-0.2.0.jar`; SHA-256 `421e7f6b7868678804ff26eada06cc968f07d1e661578fef12b0e5840009e802`.
- Cliente gráfico real confirmou migração da opção de câmera, acompanhamento do alvo no jogo e com Ações aberta, voo contínuo, sparring sem OP com perseguição/ataque e guarda ativa. Técnicas/formas/persistência gráficas ainda estão em andamento; resultados detalhados em `CLIENT_TEST_02.md`.
- Android físico, Battly e redes com latência real **não foram testados**. Renderização de software/Xvfb não constitui benchmark móvel.

A sessão 0.1 tem registro histórico separado em `SESSION_REPORT.md` e `CLIENT_TEST.md`; seus resultados não certificam as mudanças da 0.2.

## Comandos reproduzíveis

Requisitos: Java 17, Minecraft 1.20.1, Forge 47.3.22, acesso aos repositórios oficiais e memória suficiente para a preparação inicial do ambiente ForgeGradle.

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew runServer
```

`build` compila/reobfusca o mod para `build/libs/dbil-0.2.0.jar`. Os GameTests não são executados por `build` sozinho: `runGameTestServer` precisa ser solicitado explicitamente. O comando carrega Forge/DBIL no servidor lógico, executa as classes de `dev.dbil.gametest` e encerra. Não abre uma GPU nem testa teclado/câmera.

`runServer` inicia um servidor dedicado de desenvolvimento. EULA depende da concordância de quem executa. Encerrar com `stop` depois de verificar o carregamento/salvamento. Inspecionar erros de classes client-only, registries, capabilities, pacotes e gravação de jogador.

Os dois clientes e o servidor devem usar DBIL 0.2/protocolo **2**. Não manter dois JARs DBIL em `mods`; a versão 0.1/protocolo 1 não é compatível com essa conexão.

## Cobertura automatizada

As suítes verificam persistência/migração/schema futuro, limites reais do servidor, recursos/custos inválidos, raça/criação/capability/lifecycle, progressão, admissão de packets, seleção de técnica, projéteis/cooldown/barrage, guarda/quebra, movimento/replay/custos de voo, desafios/recompensa única, transformações/requisitos/drain/maestria e atualização da versão 0.1 para schema 3.

São testes sobre comportamentos no servidor Forge, usando estrutura `dbil:empty` e jogadores de teste quando necessário. Não comprovam renderização, interação com botões, autenticação online, fluidez em um aparelho físico ou simultaneidade visual de dois clientes. Os dois testes adicionais de poder confirmaram os invariantes das transformações na regressão de 50 aprovados.

## Roteiro manual: criação e atualização

1. Em uma cópia do mundo 0.1, substituir o JAR e entrar com os personagens existentes. Confirmar migração 2→3, identidade, raça/origem/estilo, atributos, XP, Ki/Stamina, flags e maestria preservados; técnica selecionada válida e criação sem reabrir.
2. Em mundo/conta de teste novo, criar Humano e Saiyajin com origens/estilos válidos. Verificar nome vazio/longo e envio duplicado sem duplicar personagem ou conceder atributos do cliente.
3. Carregar um cliente com `camera=false` antigo e sem `lockOnCamera`: o novo campo deve ser true. Em outro cliente com `lockOnCamera=false` explícito, a escolha deve permanecer false.
4. Testar todas as sete abas e botões em resolução GUI compacta de 320×240 ou equivalente. A HUD padrão deve preservar hotbar; configurações de escala/posição devem ficar dentro da tela.
5. Salvar/sair/reentrar e morrer/respawn. Confirmar dados persistentes e limpeza de carga, guarda, voo, alvo, combo e preparação. A forma ativa volta à base ao encerrar a sessão; desbloqueios e maestria permanecem.

## Roteiro manual: voo e lock-on

1. Ativar voo no solo, manter movimento por mais de 30 segundos, subir/descer e soltar o controle. Verificar aceleração, frenagem, colisão, esgotamento de Ki e aterrissagem sem creative flight.
2. Comparar deslocamento no cliente e dados do servidor. ACK regular não deve provocar teleports periódicos. Guardar logs de correções grandes, expulsão por floating, movimento rápido ou posição divergente.
3. Repetir com controles do launcher e botões da aba Ações; diagonais e descida não devem herdar slowdown vanilla de sneak. Dash/knockback devem persistir no integrador aéreo.
4. Fixar um rival dentro de 32 blocos e com visão. Reposicioná-lo/movê-lo ao redor do jogador: câmera deve acompanhar por padrão, inclusive com Ações aberta. Repetir em primeira pessoa e terceira pessoa traseira.
5. Desligar Seguir alvo com câmera nas opções, confirmar câmera livre e reativar. Chat/outras abas, montagem e morte suspendem acompanhamento. A perspectiva frontal vanilla olha para o próprio jogador.
6. Conferir nome, HP, distância e Poder de Luta do alvo. Metadata ausente deve aparecer como `?`, nunca como um atributo remoto presumido.

## Roteiro manual: treino, combate e formas

1. Sem OP, usar J→Treino→Iniciar sparring em piso livre. Conferir perseguição/ataque do rival e rejeições por voo, rival ativo, densidade, cooldown ou falta de chão seguro.
2. Com mão vazia, executar combo leve de quatro etapas e pesado; verificar custo de Stamina, janela/cooldown, defesa e knockback. Distância, parede e regras PvP devem impedir dano inválido.
3. Usar guarda com alvo e pelo botão de toque. Conferir direção frontal, custo de Stamina, pressão do pesado, quebra e recuperação. Soltar/sair/morrer deve encerrar pedidos mantidos.
4. Usar Ki Wave; desbloquear e selecionar Ki Blast/Rajada no menu. Confirmar custo único da rajada, três projéteis, cooldown, alcance, colisão de bloco/inimigo e ausência de destruição de terreno.
5. Completar os três desafios por ações reais. Acertos no vazio não contam como hits; recompensas devem ser individuais e concedidas uma única vez, inclusive depois de relogar. Voo aparece em segundos na GUI.
6. Desbloquear forma racial no Despertar, ativar, interromper por dano/movimento e tentar sem Ki. Confirmar validação, preparação, drain, retorno à base, maestria e crescimento moderado do poder/atributos.
7. Observar cabelo SSJ dourado e aura amarela no Saiyajin, aura branca do Potencial Liberado no Humano e poses. A aparência segue snapshots do servidor e respeita distância/qualidade/partículas.

## Dois clientes e servidor dedicado

1. Entrar simultaneamente com usuários distintos e progressões próprias; verificar ausência de vazamento de HUD/unlocks/counters.
2. Usar carga, voo, guarda e técnicas simultaneamente. Observar os estados visuais remotos, cabelo/auras e alterações do alvo no cliente parceiro.
3. Usar sparring e derrotar rival: recompensa única ao jogador creditado; a progressão do parceiro não deve mudar por acidente.
4. Testar `pvp=false`: leves, pesado e as três técnicas não devem causar dano DBIL ao parceiro. Repetir PvP em mundo de teste quando ativado.
5. Sem OP, `/dbil info` próprio funciona; mutações administrativas são rejeitadas. Sparring/seleção/formas no menu continuam disponíveis quando requisitos são cumpridos.
6. Desconectar durante guarda/carga/voo/preparação, reconectar e reiniciar o servidor. Dados persistentes continuam individuais e estados transitórios permanecem limpos.
7. Inspecionar os logs dos dois clientes e do servidor. Separar avisos de autenticação offline/software GL de erros de gameplay, sem ocultar falhas.

## Android e limites de desempenho

Testar o dispositivo e launcher usados pelos jogadores com Java 17/Forge 1.20.1. Registrar aparelho, RAM/heap, backend gráfico, resolução GUI, distância de renderização, FPS e memória antes/depois no mesmo local. Experimentar joystick, jump/sneak, ações por toque, câmera ligada/desligada, deslocamento contínuo e rede entre os dois aparelhos.

A implementação limita partículas, distância, projéteis e histórico de previsão; não requer shader ou biblioteca de animação. Isso não substitui medição. Replay local aproxima colisões de blocos; colisões de entidades, mudanças no mundo e latência alta podem exigir reconciliação. NPC ainda é terrestre com aparência vanilla temporária. Animações corporais completas, beams/clash, outras raças, mestres, dimensões e destruição não são funcionalidades validadas desta versão.


## Resultado final da sessão 0.2

`./gradlew build runGameTestServer`: BUILD SUCCESSFUL, todos os 50 testes obrigatórios aprovados. O relatório definitivo em SESSION_REPORT_02.md distingue testes automatizados, gráficos e verificações ainda pendentes.
