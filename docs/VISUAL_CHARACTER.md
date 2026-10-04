# Personagem DBIL — modelo, aparência e cabelos (0.3.0)

## Direção

O personagem continua sendo um personagem de Minecraft: cabeça cúbica, tronco, braços e pernas blocados e as
mesmas proporções do Steve/Alex (escala 0.9375, malha `PlayerModel` vanilla). A melhoria está em cima dessa base:
pele pintada pelo DBIL, rosto estilizado, cabelos voxel 3D, roupas em camadas, acessórios, cauda Saiyajin e
animações próprias. Nada de anatomia realista, dedos ou músculos.

## Arquitetura

| Peça | Classe | Observação |
|---|---|---|
| Dados (servidor) | `appearance.CharacterAppearance`, `AppearanceOptions` | Record imutável, valores sempre normalizados (índices, ids desconhecidos, cor 24 bits). Cauda só para Saiyajins. |
| Persistência | `CharacterData.appearance()` | Schema 4. Migração 3→4 dá a aparência padrão da raça a personagens antigos. |
| Rede | `Network.AppearanceSync` (S2C, tracking+self), `AppearanceUpdate` (C2S), `CreateCharacter` | Enviado no login, respawn, troca de dimensão, edição e quando um jogador começa a rastrear outro. Nunca por frame. |
| Modelo | `client.render.character.DBILCharacterModel` | Estende `PlayerModel` (compatível com armaduras, itens, elytra, flechas, cabeças, papagaios, capa). |
| Renderizador | `DBILPlayerRenderer` | Instalado por `RenderPlayerEvent.Pre`; jogadores sem personagem DBIL continuam vanilla. Mantém sombra, nome, armadura e itens. |
| Primeira pessoa | `CharacterRenderers.renderArm` (`RenderArmEvent`) | Braço com pele DBIL, manga e peças 3D do braço. |
| Pele | `SkinPainter` + `CharacterTextures` | Pinta um skin 64×64 (layout Steve) por aparência/variante/expressão; cache LRU com expiração. |
| Cabelo | `HairModels` | 7 estilos voxel, cada um com variante Super Saiyajin própria. |
| Roupas 3D | `OutfitModels` | Ombreiras, faixa, gola, munhequeiras, laço da faixa de cabeça e cauda segmentada. |
| Camadas | `CharacterLayer` | Cabelo (+ passe de brilho no SSJ), peças de roupa coloridas por slot, cauda animada. |
| NPC | `client.render.TrainingEnemyRenderer` | O rival de treino usa o mesmo modelo, com aparência estável derivada do UUID. |

## Pele, rosto e roupas

O `SkinPainter` usa as áreas normais da skin do Minecraft:

- **Camada base**: pele com leve ruído, rosto 8×8 (olhos, íris, brilho, sobrancelhas, boca e uma sombra de nariz quase
  invisível), cabelo por baixo do cabelo 3D, orelhas simples marcadas na lateral.
- **Camada externa (overlay +0.25 px)**: roupa com espessura real — gi, armadura, colete, botas e luvas ficam
  ligeiramente para fora do corpo, como a jaqueta/calça do skin vanilla.
- **Variantes**: base, Super Saiyajin (íris verde-água, sobrancelhas douradas) e Potencial Liberado.
- **Expressões**: neutra, focada (combate/lock-on/guarda) e grito (carga de Ki, transformação, técnica).

Roupas iniciais (cores primária/secundária/destaque editáveis):

| Roupa | Camadas |
|---|---|
| Gi de treino | Camiseta, gi com gola em V, faixa com laço 3D, calça larga, botas, emblema original nas costas |
| Armadura de batalha | Malha, peitoral com acabamento, ombreiras 3D, abas da cintura, luvas e botas 3D |
| Colete de lutador | Camisa, colete aberto, gola 3D, fivela, luvas sem dedos, botas |
| Regata de treino | Regata, faixa, calça larga, botas |

Acessórios: cauda (Saiyajin), munhequeiras e faixa na cabeça.

## Cabelos 3D

Os penteados são geometria (não textura): uma calota cobre o topo da cabeça e os volumes são caixas e pontas
voxel afuniladas (`PartBuilder.spike`, 2–4 segmentos que diminuem). A textura `hair.png` é tons de cinza com mechas,
colorida pelo cabelo escolhido.

| Estilo | Base | Variante Super Saiyajin |
|---|---|---|
| Curto | calota, franja curta, tufos | pontas curtas para cima |
| Espetado | 8 pontas laterais/traseiras + franja | pontas sobem e crescem 30%, franja única, 2 pontas extras |
| Espetado alto | chama vertical com entrada na testa | mais alta e aberta |
| Bagunçado | tufos em várias direções | tufos viram uma chama para cima |
| Médio | volume até o queixo e nuca | médio espetado (cascata para trás) |
| Liso | longo até os ombros, repartido | longo espetado em cascata |
| Careca | — | — |

Durante a transformação o cabelo pisca entre base e SSJ (40–85% do progresso) e fixa o visual dourado no fim.
No SSJ um segundo passe aditivo (`RenderType.eyes`) dá brilho aparente. Com carga, transformação ou voo, as pontas
balançam levemente (opção "Animações extras").

## Desempenho

- Peças baked uma vez por estilo/variante/roupa; nada é reconstruído por frame.
- Skins: no máximo 48 texturas 64×64 em cache, liberadas após 90 s sem uso.
- Nenhum shader obrigatório; render types vanilla.

## Compatibilidade e limites

- Itens, ferramentas, armaduras, elytra, flechas presas, cabeças, papagaios e capas continuam renderizando.
- Com capacete equipado o cabelo 3D é ocultado para não atravessar a armadura.
- Camadas adicionadas por outros mods ao `PlayerRenderer` vanilla não aparecem em personagens DBIL.
- Opção `dbilCharacterModel=false` volta à skin vanilla somente no cliente local.
