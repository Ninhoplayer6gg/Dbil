# Próximas milestones

O personagem do jogador continua protagonista. Personagens conhecidos entram como mestres, aliados, NPCs e chefes, após a fundação ser validada.

| Etapa | Entrega | Situação |
|---|---|---|
| Fundação (0.1) | Criação, recursos, voo, combate, Ki Wave, inimigo, progressão, persistência e multiplayer | entregue |
| 0.2 | Super Saiyajin, Potencial Liberado, domínio, aura e desafios iniciais | entregue |
| **0.3 — Visual & Combat Overhaul** | Modelo DBIL próprio, aparência editável, cabelos voxel com variante SSJ, animação procedural, HUD nova, auras em camadas, Kamehameha/Galick Gun/Masenko com carga, combo/launcher/smash/perseguição/Vanish, lock-on com troca, voo rápido, terreno opcional | entregue (ver [SESSION_REPORT_03.md](SESSION_REPORT_03.md)); falta teste manual em Android e com dois clientes |
| 0.4 (proposta) | Beam Clash, mais formas (SSJ2), mais cabelos/roupas, sons próprios gravados, refinamento de animações a partir do feedback | antes: feedback de jogo real da 0.3 |
| 5 | Mestres e desafios de treinamento | recompensas únicas, requisitos e confiança persistentes |
| 6 | Namekuseijin, Majin, raça do Freeza e Androides | definições raciais isoladas, aparência por raça, testes de passivas |
| 7 | Dragon Balls, Shenlong, Scouter e detecção/ocultação de Ki | spawn controlado, cooldown e regras de multiplayer |
| 8 | Dimensões, Namek, planetas e Outro Mundo | transferência de dados e respawn independentes do vanilla |
| 9 | Fusão, ataques cooperativos e bosses | simulação compartilhada com limites de entidades/custos |

## Transformações

`TransformationDefinition` contém raças elegíveis, requisitos, multiplicadores, drain por tick, custo/tempo de ativação, referências de aparência, habilidades, regras de domínio, condição de desbloqueio e ramo. `Transformations` registra definições e rejeita IDs duplicados. `TransformationEligibility` consulta dados do servidor sem alterar estado. As versões 0.2/0.3 implementam Super Saiyajin e Potencial Despertado (a 0.3 com apresentação completa); detalhes em [TRANSFORMATIONS.md](TRANSFORMATIONS.md).

Os ramos futuros são independentes: clássico, divino, primal, instintivo e destruidor. O identificador de ramo não obriga uma árvore linear. O campo de condição de desbloqueio aponta a um avaliador de desafios, com extensões futuras para quests, treino ou mestres. Ele não concede unlock sozinho.

As formas atuais compõem modificadores temporários sobre atributos base, sem acumular multiplicadores no save. A ativação possui custo, interrupção, drain autoritativo e sincronização visual. Cabelo, olhos, textura e aura são canais distintos para permitir substituição de assets sem alterar gameplay.

## Beam Clash e destruição

A 0.3 implementou feixes reais (`KiBeamEntity`: dono, direção, comprimento, colisão no servidor) e dano ao terreno
opcional com orçamento por evento/tick e proteções. O Beam Clash ainda **não** existe: precisa detectar dois feixes
opostos que se cruzam, resolver o empate no servidor (carga, Ki, input) e sincronizar o ponto de choque.

## Fora do escopo atual

Forma personalizada, Zenkai, Oozaru, caminhos divinos, absorção, fusão, Dragon Radar, viagem espacial, timeline e Outro Mundo não são recursos concluídos. Referências na arquitetura e nos dados não substituem uma implementação jogável.
