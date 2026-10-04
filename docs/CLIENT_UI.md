# Interface e apresentação v0.2

Código de cliente, GUI, renderização e animação é carregado apenas pelo lado cliente. Screens e efeitos usam snapshots do servidor. Dano, custos, cooldowns, desbloqueios e recompensas continuam autoritativos.

## Controles e menu

`R` segura carregamento de Ki; `G` alterna voo; `X` faz dash; `C` usa a técnica selecionada; `V` seleciona/libera alvo; `J` abre o menu. Mão vazia: clique de ataque leve; Shift + ataque pesado; botão direito com alvo para guarda. Teclas são remapeáveis nas opções vanilla. A HUD exibe o remapeamento atual.

A criação solicita nome, raça, origem e estilo; somente a confirmação do servidor fecha o formulário. Schema mais recente permanece protegido e não abre criação nova.

O menu funciona em tempo real. As setas junto ao título alternam dois grupos de abas: Personagem/Atributos/Ações/Técnicas e Formas/Treino/Opções. Ações possui botões grandes para os ataques, técnica selecionada, guarda, carregamento, dash, alvo e voo, além de avanço/recuo/esquerda/direita/subida/descida. Movimento, carga e guarda de toque param ao sair ou trocar de aba. Morte e logout limpam os controles para evitar ações presas ao voltar.

Técnicas lista Ki Wave, Disparo de Ki e Rajada de Ki, mostra custo base, alcance e cooldown e permite solicitar seleção de técnicas desbloqueadas. Formas mostra Super Saiyajin e Potencial Liberado, requisitos, custo inicial e maestria, e oferece ativar ou voltar à forma base. Seleção e ativação aguardam snapshots; a interface não muda dados autoritativos por conta própria.

Treino pagina os três desafios e seus objetivos, mostra voo em segundos e recompensas automáticas, e oferece Iniciar sparring sem comandos administrativos. O servidor exige local seguro, intervalo entre sessões e limites de rivais; pedidos recusados exibem mensagens traduzidas. As recompensas desbloqueiam técnicas e a transformação racial pelo progresso individual.

## Câmera e HUD

A câmera de lock-on agora vem ativada por `interface.lockOnCamera=true`. A opção antiga `camera=false` de v0.1 não controla esse campo. Forge acrescenta a chave nova ao corrigir configurações antigas; um valor explícito `lockOnCamera=false` já existente é preservado. Não há sobrescrita incondicional de preferências. `specialCamera=false` permanece uma política separada para efeitos cinematográficos futuros e não desativa lock-on.

`TargetCamera` suaviza yaw/pitch por frame, usando tempo real limitado, resposta exponencial e posição interpolada do alvo. O acompanhamento funciona na primeira pessoa e na câmera traseira vanilla, no jogo solo e cooperativo, inclusive usando os botões da aba Ações. Chat, outras telas/abas, montagem, morte e câmera em outra entidade suspendem o acompanhamento. A câmera frontal vanilla continua sendo a perspectiva voltada ao próprio jogador. A opção Seguir alvo com câmera fica na aba Opções.

A HUD usa `hudX`/`hudY` normalizados e `hudScale` limitado à tela. Defaults 0,02/0,04 colocam o painel no canto superior esquerdo, preservando hotbar. Mostra HP, Ki e Stamina reais, nível, Poder de Luta opcional, técnica selecionada, cooldown, voo, guarda/quebra de guarda e forma. O cartão de alvo apresenta nome, HP, Poder de Luta enviado pelo servidor, distância e estado da câmera; tenta ocupar espaço ao lado da HUD própria. Ausência de metadata de poder mostra `?`, sem inventar atributos remotos.

## Transformações e efeitos

`SaiyanHairLayer` adiciona cabelo dourado estilizado de sete pontas ao Super Saiyajin confirmado pelo servidor, sem substituir o skin. A malha usa 136 vértices e 40 triângulos visíveis, calculados uma vez, acompanha o modelo da cabeça e funciona nos modelos de jogador normal e slim. Não exige GeckoLib, shader ou access transformer. Olhos e aparência facial do skin permanecem preservados.

Auras registráveis são azuis para carga/técnica, amarelas para Super Saiyajin e brancas para Potencial Liberado. A emissão respeita partículas, intensidade, qualidade e distância configuradas, com no máximo quatro partículas por jogador ativo a cada quatro ticks. Posturas de carga, preparação da técnica e guarda usam extensões ArmPose do Forge e os estados do servidor. Potencial Liberado conserva o modelo e o cabelo do skin.

O rival de treinamento mantém modelo/textura de zumbi vanilla temporários isolados no renderer. Ki Wave usa um billboard radial luminoso leve. Não há shaders obrigatórios ou destruição de terreno.

## Validação

PT-BR e EN-US possuem as mesmas chaves. Fontes de GUI, modelos e efeitos são compiladas com o restante do mod. Testes visuais, seguimento de câmera, Android real e multiplayer precisam ser registrados pela execução efetiva; build e GameTests de servidor isoladamente não substituem esses testes.
