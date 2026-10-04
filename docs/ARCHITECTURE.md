# Arquitetura DBIL 0.2.0

## Responsabilidades

`DBIL` registra configs, capability, entidades, itens e protocolos. Definições de raça, origem, técnica e NPC são registráveis e usadas pelo gameplay. Não existem diretórios vazios para sistemas ainda não implementados.

| Módulo | Implementação |
|---|---|
| api/race/character/stats | Definições, escolhas iniciais, atributos e NBT versionado |
| capability | Provider Forge serializável por jogador, invalidação e clone |
| power/training | Cálculo base/estado, progressão e três desafios com recompensas persistentes |
| ki/stamina | Consumo seguro, regeneração, carga e restrições de recuperação |
| flight/movement | Aceleração, desaceleração, colisão, Ki, dash e autoridade aérea |
| combat/targeting | Combo, pesado, guarda frontal/quebra, defesa única, knockback e seleção limitada |
| technique | Ki Wave, Ki Blast e Ki Barrage por definições/padrões, entidade com colisão server-side |
| npc/registry | Oponente ativo, AI nativa, atributos, recompensa e convocador |
| transformation | Super Saiyajin/Potencial Liberado: carga, drain, mastery, multiplicadores temporários e aparência |
| network/server | Intenções, validação, lifecycle e estado transitório de sessão |
| client/gui/rendering/animation | HUD, telas, input/toque, modelos e efeitos baratos |
| debug/gametest | Ferramentas administrativas e invariantes de integração |

Vida permanece no sistema de atributos/vida Minecraft controlado pelo servidor; Vitality aplica um modificador limitado e reaplicável sem duplicação. Ki, Stamina, XP e técnicas têm dados independentes. O respawn copia a capability e não pressupõe que futuras dimensões de morte usarão sempre respawn vanilla.

## Rede

Canal `dbil:main`, protocolo `2`; versões incompatíveis recusam conexão. Sete tipos registrados com direção explícita:

| ID | Sentido | Conteúdo |
|---|---|---|
| 0 | Cliente → Servidor | Uma ação enumerada |
| 1 | Cliente → Servidor | Input sequenciado de voo, subir/descer, yaw/pitch finitos |
| 2 | Cliente → Servidor | Nome e IDs raça/origem/estilo limitados |
| 3 | Servidor → dono | Snapshot NBT validado com limites reais do servidor |
| 4 | Servidor → tracking + dono | Carga, voo, alvo, técnicas, guarda/quebra, forma/carga e poderes autoritativos |
| 5 | Servidor → dono | ACK de voo: posição/velocidade, sequência/ticks, velocidade máxima e reset |
| 6 | Cliente → Servidor | Selecionar técnica ou solicitar forma registrada por ID limitado |

Os handlers enfileiram trabalho no thread lógico correto. Pacotes de cliente não fornecem vida, dano, Ki, XP, cooldown, atributos ou recompensa. As ações verificam personagem criado, vida, estado, requisitos, alcance, linha de visão e regras PvP. Custos são calculados e consumidos pelo servidor; dash verifica as duas reservas antes de deduzir qualquer uma.

Cada jogador possui orçamento independente de 30 ações e 24 inputs por 20 ticks. Inputs mantidos usam heartbeat a cada quatro ticks; mudanças enviam imediatamente, permitindo joystick analógico em 20 ticks/s. O movimento para gradualmente após 20 ticks sem input. Carga/guarda têm heartbeats de um segundo e leases no servidor. Nenhum packet por frame de renderização.

Dados do dono são comparados antes do envio periódico; padrão máximo dois snapshots por segundo. Visual remoto recebe estados pequenos, nunca todo o histórico do personagem. Minecraft sincroniza as entidades customizadas com spawn packets Forge e tracking limitado.

## Voo e knockback

O servidor mantém posição e velocidade próprias, integra input com aceleração/deceleração e usa `Entity.move` para colisões de blocos. O cliente prevê somente movimento, com a mesma aceleração e colisões locais. Confirmações próprias cinco vezes por segundo reconcilia a previsão; não há teleports periódicos. `connection.resetPosition()` conserva o resultado no ciclo vanilla. Veja FLIGHT.md. Ações restauram a posição aérea autoritativa antes de avaliar hits. Impulsos de combate alimentam o integrador de voo, permitindo recuperação gradual em vez de ignorar knockback.

O Access Transformer abre somente os campos vanilla `clientIsFloating` e `aboveGroundTickCount`. Esses contadores são limpos durante voo DBIL validado e nas transições; `abilities.mayfly` e `abilities.flying` não são concedidos. Assim o servidor pode conservar `allow-flight=false` para os demais jogadores.

O integrador mantém custo previsível e autoridade com histórico local limitado a 64 frames. Testar suavidade em redes reais e launchers Android faz parte do refinamento; não presume mouse perfeito nem uma conexão sem latência.

## Performance e extensão

Sem buscas em toda a dimensão. Lock-on limita AABB a 32 blocos e visão/cone; melee usa menos de quatro blocos. NPC usa goals Minecraft; temporários Ki Wave duram no máximo100 ticks e range limitado e cooldown; Ki Barrage cria somente três projéteis por ativação. Partículas de aura são limitadas por jogador, distância, qualidade e cadência4ticks. Nenhuma técnica atual quebra blocos.

Client-only subscribers usam `Dist.CLIENT`; pacotes do servidor entregam snapshots ao cliente por callbacks `DistExecutor` adiados. Nenhum renderer é registrado no servidor dedicado.

Beams possuem volume, duração, grupo de colisão e compatibilidade de clash em dados separados do renderer, sem executor de beam nesta versão. Transformações possuem requisitos, multipliers, drain, aparência, mastery e ramo; novas formas usam definições e execução server-side, fora de Player. Sessões de sparring são limitadas por dono, cooldown, densidade e tempo, sem comandos administrativos.
