# Combate e técnicas DBIL 0.2

O servidor recebe intenções, seleciona um alvo dentro de um cone e valida criação de personagem, estado, distância, visibilidade, custo e cooldown. O ID de alvo enviado pelo cliente não controla o dano. Ferramentas e armas continuam usando o combate Minecraft; mão vazia usa o combate DBIL. Uma intenção sem alvo também consome stamina e respeita cooldown, impedindo tentativas gratuitas por packet.

## Combate físico

- Leve: intervalo mínimo de 8 ticks, alcance de 3,4 blocos, custo de 2 stamina.
- Pesado: intervalo mínimo de 18 ticks, alcance de 3,8 blocos, custo de 7 stamina.
- Combo: três golpes leves e um finalizador, com janela de 24 ticks entre golpes. O pesado encerra a sequência.
- Dano: `2 + Strength * transformationMultiplier * 0.14`, com multiplicador 1,9 no pesado ou 1,35 no finalizador. A configuração global de dano é aplicada antes do processamento de dano Minecraft.
- Defesa DBIL: redução `Defense / (Defense + 80)`, limitada a 65%, usando o multiplicador de transformação. `GuardService` aplica essa redução uma única vez em `LivingHurtEvent` para ataques tangíveis contra jogadores DBIL, incluindo NPCs e ataques vanilla. Os serviços de ataque não reaplicam Defense. A armadura vanilla continua operando no processamento de dano Minecraft.
- Knockback: impulso próprio horizontal/vertical, respeitando resistência ao knockback. Jogadores recebem packet de velocidade, e o integrador de voo recebe o mesmo impulso.

Golpes DBIL aceitos usam sua própria janela de dano e não perdem os golpes de combo para a imunidade de dez ticks do combate vanilla. NPCs mantêm a cadência de ataque da IA Minecraft.

## Guarda

Segurar guarda envia apenas intenção e heartbeat; o servidor valida o estado e a direção de cada impacto. A fonte precisa estar à frente do jogador, em um cone com produto escalar mínimo de 0,15. Fogo, fome, void e fontes que ignoram armadura não podem ser bloqueados. A posição real do projétil é preservada no DamageSource das técnicas.

Guarda reduz 70% do dano já ajustado por Defense, configurável por `guardDamageReduction`. Cada impacto custa o maior valor entre 3 stamina e `dano * guardStaminaPerDamage` (padrão 2). Um pesado DBIL acrescenta 12 stamina ao custo. A cobrança é atômica: se faltar stamina, o golpe mantém seu dano após Defense, a stamina vai para zero e a guarda quebra por `guardBreakTicks` (padrão 35). A quebra é sincronizada e impede golpes/técnicas durante sua janela. Segurar guarda consome 0,08 stamina por tick, impede sprint e recebe timeout se o heartbeat parar por 40 ticks. Não há regeneração de stamina enquanto a guarda está ativa.

Bloqueios efetivos cancelam o knockback vanilla do impacto e deixam apenas 20% do impulso DBIL. Ataques por trás não ganham essa proteção. Guarda não pode ser iniciada durante carregamento de Ki, transformação ou sequência de técnica; golpes e técnicas também não podem começar durante guarda.

## Técnicas selecionáveis

`selectedTechnique` é persistido nos dados do personagem (schema 3), separado do conjunto de técnicas equipadas. `TechniqueService.select` valida definição executável, aprendizado, equipamento, requisitos e maestria no servidor; não é possível trocar durante carga ou sequência. O cliente não altera seleção diretamente. Saves anteriores usam Ki Wave ou a primeira técnica equipada válida.

| Técnica | Ki base | Carga | Cooldown após execução | Alcance | Projéteis |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ki Wave | 12 | 12 ticks | 50 ticks | 32 blocos | 1 |
| Ki Blast | 5 | 2 ticks | 12 ticks | 24 blocos | 1 |
| Ki Barrage | 24 | 8 ticks | 60 ticks | 28 blocos | 3, intervalo de 4 ticks |

Custos e cooldowns de Blast/Barrage são configuráveis individualmente. Ki Wave continua sendo a técnica inicial; Blast e Barrage são aprendidas por progressão/missões. Controle de Ki reduz até 35% do custo; multiplicadores raciais e `techniqueKiCostMultiplier` também participam. Dash e voo respeitam a eficiência energética racial, além de seus próprios custos configurados. A cobrança ocorre uma vez no início aceito, inclusive no Barrage, e não é devolvida ao cancelar, morrer ou desconectar. O conjunto da sequência impede novas ativações antes de terminar e o cooldown começa após seu último disparo.

Cada ativação concluída acrescenta 0,05 de maestria, independentemente do número de projéteis, e registra `technique_casts`. Acertos com dano real registram `technique_hits`; impactos em parede e tentativas inválidas não contam. O multiplicador de transformação de Ki Power participa do dano na criação do projétil. Golpes e acertos de técnicas podem aumentar a maestria da transformação ativa, respeitando o intervalo próprio desse serviço.

`KiWaveEntity` é uma entidade de energia própria, não uma flecha recolorida. O mesmo executor serve os três padrões definidos nos dados, sem uma classe por técnica. A colisão do servidor verifica o segmento percorrido a cada tick contra blocos e entidades; uma colisão encerra o projétil. Ele não quebra blocos, não atravessa paredes, não explode e não fica ativo mais de 100 ticks. O spawn começa na posição dos olhos, evitando disparar do outro lado de paredes próximas. O alcance usa a origem do disparo, não a posição posterior do lançador: mover-se ou tomar cobertura depois de atirar não invalida um projétil em voo. Paredes no trajeto continuam interceptando-o pelo clip do servidor. NBT de dano, alcance e origem recebe validação contra números não finitos. A identidade da técnica é sincronizada por metadata de entidade e decodificada somente quando muda, permitindo diferenciar apresentação sem alocação a cada frame.

`TechniqueDefinition` e `Techniques` separam dados de custo, carga, geometria, requisitos e apresentação da execução. Tipos adicionais já são representáveis. Definições BEAM exigem `BeamProperties` com largura, duração e grupo de clash; elas ainda não têm executor. Beam Clash e técnicas homing/piercing/explosivas não estão ativos.

## Lock-on e PvP

Seleção limitada a 32 blocos, preferência pelo centro da câmera e teste de linha de visão. O alvo é invalidado ao morrer, sair do alcance, mudar de dimensão ou perder visibilidade. Golpes físicos também exigem que o alvo esteja à frente do jogador. PvP exige configuração DBIL, PvP do servidor, personagem criado e regras de equipe compatíveis.

## Inimigo de treinamento

`NpcDefinitions` permite registrar identidades e baselines de NPCs. O inimigo atual possui 40 HP, ataque 4, velocidade 0,27 e detecção de até 20 blocos. A configuração `npcDifficulty` multiplica somente o dano dos NPCs; HP e atributos básicos permanecem estáveis. O multiplicador global de dano continua sendo aplicado. Sua IA persegue e ataca personagens criados, retalia quando atacado e patrulha quando ociosa. Não voa nem dispara Ki nesta versão. O Poder de Luta deriva dos atributos reais de HP, dano e velocidade, em vez do nível.

A morte concede 35 XP DBIL ao jogador vivo com crédito de abate, passando pelos multiplicadores da progressão, e registra `training_defeats`. Um marcador persistente impede conceder a mesma recompensa novamente. Não há XP vanilla desse inimigo.

Ele não aparece naturalmente. Pode ser criado por `/dbil spawn`, pelo ovo no modo criativo ou por receita de sobrevivência: dois trigos, dois couros e um lingote de ferro. O modelo do item usa o asset vanilla de ovo; a aparência do inimigo é isolada no renderer do cliente para substituição posterior.

## Performance e próximos sistemas

Buscas de alvo só ocorrem na intenção de atacar ou selecionar alvo, e usam caixas limitadas; manutenção de lock-on usa um ID já selecionado. Uma ativação de técnica cria um único projétil e não há mensagens por frame para carregar técnicas. IA usa os goals nativos com limites de perseguição. Não há loops de busca mundial, dano de terreno ou shaders obrigatórios.

A próxima extensão de combate deve acrescentar um executor de beam sustentado, preservando a seleção existente, a autoridade do servidor e o perfil de colisão da definição. A visualização de clash pode então ser construída sobre segmentos/volumes do servidor; não deve decidir força ou dano no cliente.
