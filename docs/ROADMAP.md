# Próximas milestones

O personagem do jogador continua protagonista. Personagens conhecidos entram como mestres, aliados, NPCs e chefes, após a fundação ser validada.

| Etapa | Entrega | Condição antes de avançar |
|---|---|---|
| Fundação atual | Criação, recursos, voo, combate, Ki Wave, inimigo, progressão, persistência e multiplayer | Build + servidor dedicado + dois clientes + teste Android |
| 2 — versão 0.2 | Super Saiyajin, Potencial Despertado, domínio, aura e desafios iniciais | Ativação e drain autoritativos, morte e reconnect corretos, orçamento de efeitos |
| 3 | Kamehameha, Galick Gun, Masenko, seleção contextual, carga variável, lock-on completo | Validação de targeting, custo/cooldown e latência |
| 4 | Mestres e desafios de treinamento | Recompensas únicas, requisitos e confiança persistentes |
| 5 | Namekuseijin, Majin, raça do Freeza e Androides | Definições raciais isoladas e testes de passivas |
| 6 | Dragon Balls, Shenlong, Scouter e detecção/ocultação de Ki | Spawn controlado, cooldown e regras de multiplayer |
| 7 | Dimensões, Namek, planetas e Outro Mundo | Transferência de dados e respawn independentes do vanilla |
| 8 | Beam Clash, fusão, ataques cooperativos e bosses | Simulação compartilhada com limites de entidades/custos |

## Transformações

`TransformationDefinition` contém raças elegíveis, requisitos, multiplicadores, drain por tick, custo/tempo de ativação, referências de aparência, habilidades, regras de domínio, condição de desbloqueio e ramo. `Transformations` registra definições e rejeita IDs duplicados. `TransformationEligibility` consulta dados do servidor sem alterar estado. A versão 0.2 implementa Super Saiyajin e Potencial Despertado; detalhes em [TRANSFORMATIONS.md](TRANSFORMATIONS.md).

Os ramos futuros são independentes: clássico, divino, primal, instintivo e destruidor. O identificador de ramo não obriga uma árvore linear. O campo de condição de desbloqueio aponta a um avaliador de desafios, com extensões futuras para quests, treino ou mestres. Ele não concede unlock sozinho.

As formas atuais compõem modificadores temporários sobre atributos base, sem acumular multiplicadores no save. A ativação possui custo, interrupção, drain autoritativo e sincronização visual. Cabelo, olhos, textura e aura são canais distintos para permitir substituição de assets sem alterar gameplay.

## Beam Clash e destruição

Os tipos de técnica distinguem beams de projéteis. A milestone de beams deve introduzir segmentos, identificação do dono e resolução de interseção no servidor antes do Beam Clash. Uma entidade pequena de Ki Wave não certifica que Beam Clash está implementado. Destruição de terreno exige orçamento configurável, proteção e execução escalonada.

## Fora do escopo atual

Modelos definitivos, customização extensa, forma personalizada, Zenkai, Oozaru, caminhos divinos, absorção, fusão, Dragon Radar, viagem espacial, timeline e Outro Mundo não são recursos concluídos. Referências na arquitetura e nos dados não substituem uma implementação jogável.
