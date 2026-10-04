# Changelog

## 0.3.0 — Visual & Combat Overhaul

### Personagem e aparência
- Modelo DBIL próprio com proporções Minecraft (base `PlayerModel`), renderizado no lugar da skin do jogador.
- Pele pintada por aparência: rosto estilizado (5 formatos de olhos, cor, 4 sobrancelhas, 3 bocas), expressões
  (neutra/focada/grito) e variantes Super Saiyajin / Potencial Liberado.
- 7 cabelos voxel 3D, cada um com variante Super Saiyajin própria; brilho e balanço das pontas.
- 4 roupas em camadas (gi, armadura, colete, regata) com 3 cores editáveis, peças 3D, munhequeiras, faixa e cauda.
- Corpo Tipo A/Tipo B (Steve/Alex) e 8 tons de pele.
- Tela de criação com prévia 3D ao vivo, prévia SSJ e edição posterior em J → Personagem → Editar aparência.
- Aparência sincronizada para todos os jogadores que rastreiam o personagem (schema de dados 4, migração 3→4).
- Rival de treino usa o modelo DBIL com aparência derivada do UUID.

### Animação
- Camada procedural própria sobre a animação vanilla: idle, postura de combate, caminhada/corrida/sprint, voo
  parado/lento/rápido/de combate, carga de Ki, transformação, guarda, combo de 4 golpes, pesado, launcher, smash,
  dash, Vanish, poses de técnica, impacto, knockback, queda e pouso.

### HUD e câmera
- HUD nova com barras inclinadas, rastro de dano, Ki animado, Stamina segmentada, emblema, painel de técnica,
  forma/maestria/drain, painel do alvo, retículo 3D, medidor de carga, banner de transformação, contador de combo e
  prompt de perseguição. Modo AUTO compacto/expandido e escala automática para telas pequenas.
- Tremor de tela e efeitos de FOV moderados e configuráveis.

### Ki, técnicas e transformações
- Técnicas carregáveis (segurar C): carga altera dano, Ki gasto, knockback e tamanho; marcos visuais 30/60/90/100%.
- Novas técnicas: Kamehameha, Galick Gun e Masenko (feixes contínuos com colisão no servidor, explosão final).
- Ki Blast, Ki Wave e Ki Barrage (agora 6 disparos menores alternando as mãos) com visual novo.
- Auras em camadas (chamas aditivas, brilho, faixas, descargas, partículas), aura de carga e aura SSJ.
- Transformação com sequência de pose, aura crescente, cabelo piscando, som, câmera e explosão de poder.
- Maestria baixa: aura instável, flashes, técnicas até 25% mais caras; maestria alta: estável e eficiente.
- Novos desafios de treino desbloqueiam Kamehameha, Masenko e Galick Gun.

### Combate e movimento
- Combo leve (jab, cruzado, chute, chute giratório), pesado, launcher (Espaço+ataque), smash (Shift+ataque após combo).
- Perseguição (dash após launcher/smash/final), Vanish (Z) para trás/lado do alvo, dash direcional.
- Lock-on com troca de alvo (B), seleção que prefere quem está lutando com você, retículo 3D.
- Voo rápido (Ctrl/sprint durante o voo): segue o pitch, consome 3× Ki, FOV, linhas de velocidade, vento.
- Carga de Ki também durante o voo parado.
- Dano ao terreno opcional (desligado por padrão) com proteções e limites.

### Técnico
- Protocolo de rede 3 (novos pacotes: aparência, eventos de efeito, estado visual ampliado).
- Loadout de 6 técnicas (antes 4), com equipar/desequipar no menu.
- Sons nomeados DBIL (sounds.json) e 7 partículas próprias.
- Novos comandos: `/dbil learnall`, `/dbil appearance default`.
- CI: build, GameTests, servidor dedicado e teste visual automatizado de cliente.
