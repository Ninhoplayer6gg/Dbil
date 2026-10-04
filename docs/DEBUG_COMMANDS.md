# Comandos DBIL

`/dbil info` mostra os dados do próprio personagem e pode ser usado sem OP. Todos os demais comandos exigem permissão administrativa de nível 2. Eles alteram dados no servidor e enviam a atualização ao jogador afetado.

| Comando | Resultado |
|---|---|
| `/dbil info` | Nome, criação, raça, nível, XP, Ki, Stamina e poder |
| `/dbil setrace <raça> [jogador]` | Troca para uma raça registrada; reinicia estados transitórios e a forma atual |
| `/dbil setki <quantidade> [jogador]` | Ajusta Ki dentro de zero e capacidade atual |
| `/dbil addxp <quantidade> [jogador]` | Concede XP através do serviço normal de progressão e seus multiplicadores/limites |
| `/dbil heal [jogador]` | Restaura HP, Ki e Stamina |
| `/dbil power [jogador]` | Recalcula e mostra poder base e atual |
| `/dbil learn <técnica> [jogador]` | Aprende e equipa uma técnica registrada, respeitando o limite de slots |
| `/dbil reset [jogador]` | Reinicia dados de personagem e encerra voo, carga e técnica; a criação será solicitada novamente |
| `/dbil debug [jogador]` | Mostra origem, estilo, técnicas, forma, estado de voo, carga, combo, alvo e versão do save |
| `/dbil spawn [quantidade]` | Cria de 1 a 8 inimigos de treinamento em posições livres perto da origem do comando |
| `/dbil transform <forma> [jogador]` | Solicita uma ativação com validação de unlock, raça, nível, Ki, estado e cooldown; `dbil:base` reverte |
| `/dbil unlockform <forma> [jogador]` | Desbloqueia administrativamente uma forma registrada e compatível com a raça; não ignora seu requisito de nível |
| `/dbil mastery <forma> <valor> [jogador]` | Ajusta o domínio de uma forma desbloqueada dentro de 0 a 100 |

Sem o argumento opcional de jogador, a ação usa quem executou o comando. Pelo console, informar o jogador nos comandos que afetam personagens. `/dbil spawn` também funciona no console, utilizando a posição do command source; é mais prático usá-lo dentro do mundo.

Raças disponíveis: `dbil:human`, `dbil:saiyan`. Os argumentos de técnicas e formas oferecem sugestões das definições registradas. Formas jogáveis: `dbil:super_saiyan` e `dbil:potential_unleashed`. Não há comando de edição de NBT, de forma não implementada ou de atributos arbitrários pelo cliente.

Exemplos:

```text
/dbil setrace dbil:saiyan
/dbil setki 0
/dbil spawn 2
/dbil addxp 125 Jogador2
/dbil learn dbil:ki_wave
/dbil unlockform dbil:super_saiyan
/dbil transform dbil:super_saiyan
/dbil mastery dbil:super_saiyan 50
/dbil transform dbil:base
```

`reset` é uma operação explícita de administração e descarta a progressão DBIL do jogador indicado. Use apenas em personagem de teste ou quando quiser reiniciá-lo.
