# Interface e controles — DBIL 0.3

Código de cliente (GUI, renderização, animação, efeitos) só é carregado no cliente. As telas leem snapshots do
servidor; dano, custos, cooldowns, desbloqueios e recompensas continuam autoritativos.

## Teclas (remapeáveis em Opções → Controles → DBIL)

| Tecla | Ação |
|---|---|
| R (segurar) | Carregar Ki (no chão ou em voo parado) |
| G | Ligar/desligar voo; Espaço/Shift sobem/descem; Ctrl/sprint = voo rápido |
| X | Dash; com A/D/S lateral ou para trás; logo após launcher/smash/final = perseguição |
| C | Técnica selecionada: toque = disparo imediato; segurar = carregar, soltar = disparar |
| N | Próxima técnica equipada |
| V / B | Travar/soltar alvo · trocar alvo |
| Z | Vanish (precisa de alvo travado) |
| J | Menu DBIL |
| Ataque (mão vazia) | Combo leve; Shift = pesado/smash; Espaço = launcher |
| Botão direito (mão vazia, com alvo) | Guarda |

Mão vazia só intercepta o ataque quando há entidade no cursor, alvo travado próximo ou o jogador está no ar;
caso contrário a mineração vanilla funciona normalmente. Itens e ferramentas mantêm o comportamento vanilla.

## Criação e edição de aparência (`CharacterCreationScreen`)

- **Criação**: páginas Identidade (nome, raça, origem, estilo), Corpo, Rosto, Cabelo e Roupa, com prévia 3D ao vivo
  à esquerda (arrastar ou « » giram; na página Cabelo, Saiyajins alternam a prévia Super Saiyajin). Trocar de raça mantém o visual
  personalizado e só aplica o padrão da raça se o jogador ainda não mexeu na aparência.
- **Edição** (J → Personagem → Editar aparência): mesmas páginas sem Identidade; "Salvar aparência" envia o pacote 7.
  O servidor valida e sincroniza para todos que veem o personagem.
- Somente a confirmação do servidor fecha a criação; saves de versão mais nova continuam protegidos.

## Menu J (`DBILMenuScreen`)

Duas páginas de abas (setas ao lado do título):

1. **Personagem** (dados, nível, poder, botão Editar aparência e resumo dos controles), **Atributos**, **Ações**,
   **Técnicas**.
2. **Formas**, **Treino**, **Opções**.

- **Ações** (toque/Android): duas páginas de botões grandes — combate (leve, pesado, launcher, smash, guarda,
  técnica segurar/disparar, próxima técnica, alvo/trocar alvo, Vanish, carga de Ki) e movimento (voo, voo rápido,
  dashes direcionais, frente/trás/esquerda/direita/subir/descer). Controles mantidos param ao sair da aba, morrer ou
  desconectar.
- **Técnicas**: lista paginada das 6 técnicas com custo, alcance, recarga e perfil; selecionar, equipar e desequipar
  (até 6 slots).
- **Formas**: requisitos, custo, maestria; ativar ou voltar à base.
- **Treino**: desafios (agora seis) com objetivos e recompensas; iniciar sparring.
- **Opções**: duas páginas com HUD, câmera, qualidade de aura, partículas, distância de efeitos, tremor, FOV,
  impactos, animações extras, linhas de velocidade, vento, efeitos de transformação e modelo DBIL.

## HUD, câmera e efeitos

Descritos em [EFFECTS_HUD.md](EFFECTS_HUD.md). Resumo: HUD AUTO compacta fora de combate e expandida em combate,
com escala automática para telas pequenas × `hudScale`; painel do alvo e retículo 3D; medidor de carga; banner de
transformação. A câmera de lock-on (`lockOnCamera`, padrão ligado) suaviza yaw/pitch por frame como na 0.2.

## Personagem e animação

Modelo próprio estilo Minecraft, cabelos voxel e roupas 3D: [VISUAL_CHARACTER.md](VISUAL_CHARACTER.md).
Animações procedurais: [ANIMATION.md](ANIMATION.md).

## Validação

PT-BR e EN-US têm as mesmas chaves (verificado no commit). O CI executa um cliente real (Xvfb, renderização por
software) que percorre criação, HUD, lock-on, aura, transformação, Kamehameha, combo, voo rápido, editor de
aparência, menu e primeira pessoa, com screenshots, e um teste multiplayer com servidor dedicado e dois clientes;
veja [VALIDATION.md](VALIDATION.md). Android real ainda precisa de teste manual.
