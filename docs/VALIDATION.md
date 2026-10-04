# Validação de DBIL 0.3.0

Build, GameTests, servidor dedicado, cliente gráfico, multiplayer real e Android são evidências distintas. Este
documento registra o que foi **executado de fato** na 0.3 e o que continua pendente.

## Onde roda

O ambiente desta sessão não alcança os repositórios Maven do Forge/Mojang (política de rede: HTTP 403 em
`maven.minecraftforge.net`, `libraries.minecraft.net`, `piston-meta.mojang.com`). Por isso toda compilação e teste
real roda no GitHub Actions (`.github/workflows/build.yml`, runner `ubuntu-latest`, JDK 17 Temurin), a cada push:

| Passo | Comando | O que prova |
|---|---|---|
| Build limpo | `./gradlew clean build --no-daemon --stacktrace` | compila, reobfusca e gera `build/libs/dbil-0.3.0.jar` |
| GameTests | `./gradlew runGameTestServer` | invariantes de gameplay num servidor lógico dedicado (sem GPU) |
| Servidor dedicado | `./gradlew runServer` até `Done (`, depois `stop` | o mod carrega sem classes de cliente no servidor dedicado |
| Cliente visual | `xvfb-run ./gradlew runClient -PdbilAutotest=true` | o cliente real carrega, renderiza o modelo DBIL e percorre o roteiro sem exceções |

## Resultado registrado

RESULT_TABLE

## Teste visual automatizado do cliente

`client/dev/ClientAutotest` (ativo só com `-Ddbil.autotest=true`) entra num mundo de teste, cria o Saiyajin "Kairo"
pelo pacote real de criação, prepara o personagem no servidor integrado (XP, técnicas, SSJ desbloqueado, Ki) e
executa, com screenshots: modelo de frente, lock-on e HUD expandida, câmera lateral, carga de Ki, transformação,
Super Saiyajin de frente/costas, carga e disparo do Kamehameha (costas e lateral), impacto, combo de 4 golpes,
voo rápido, editor de aparência, menu, primeira pessoa, aparência alternativa (corpo B, cabelo liso roxo, armadura)
em SSJ e base, colete com faixa, Galick Gun e Masenko. Ao final grava `run/dbil-autotest-report.txt` com `PASS:`
só se a sequência terminar sem exceção no cliente; o passo de CI falha sem essa linha.

Renderização por software (Mesa llvmpipe) em Xvfb: prova funcionamento e aparência, **não** desempenho.

### Problemas encontrados pelo teste visual e corrigidos

- Voltar à forma base era recusado com o jogador na água/montado/dormindo (a checagem de estado vinha antes do
  pedido de reversão). Corrigido em `TransformationService.start` + GameTest
  `poweringDownWorksWhileMountedButStartingDoesNot`.
- Cabelo Super Saiyajin visto de costas: pontas traseiras apontavam para a câmera e pareciam "óculos". Agora sobem e
  abrem (prévia em `tools/preview_hair.py`).
- Painel de técnica mostrava "Recarga" durante a carga; agora mostra a porcentagem de carga.
- Dica de controles do menu invadia o botão Concluído; agora é cortada ao espaço disponível.
- Partículas da preparação usavam dourado também para Humanos (Potencial Liberado); agora brancas.

## Cobertura dos GameTests (0.3)

Além das suítes da 0.2 (persistência, migração, limites, recursos, criação, progressão, admissão de pacotes,
técnicas, guarda, voo, desafios, transformações), `VisualCombatGameTests` cobre: migração 3→4 e validação/round-trip
da aparência; edição só para personagem criado; carga segurada custando mais e escalando o disparo; feixe
Kamehameha acertando o oponente à frente; Vanish exigindo alvo e pousando ao lado dele; final do combo e launcher
abrindo a perseguição; voo rápido mais rápido, seguindo o pitch e custando mais; dano ao terreno opcional e
respeitando proteções; troca de alvo entre oponentes; loadout de 6 slots; reverter forma montado.

## Pendente (não testado nesta sessão)

- **Android físico** (PojavLauncher/Battly etc.): escala da HUD, toque, FPS e memória.
- **Dois clientes reais** no mesmo servidor: visibilidade remota de aparência, auras, feixes e animações. O código
  sincroniza por `AppearanceSync`/`StateSnapshot`/`FxEvent` para quem rastreia a entidade, mas isso não foi
  observado com duas instâncias gráficas.
- Rede com latência real; compatibilidade com outros mods que adicionam camadas ao jogador (as camadas vanilla são
  mantidas no renderer DBIL; camadas de terceiros não aparecem em personagens DBIL).

## Roteiro manual sugerido

1. Copiar um mundo da 0.2, trocar o JAR e entrar: personagem migra para schema 4 com a aparência padrão da raça;
   progresso, técnicas, formas e maestria preservados. Editar a aparência em J → Personagem.
2. Criar personagem novo passando por todas as páginas; girar a prévia; conferir cabelo base e SSJ de cada estilo.
3. Combate: combo, Shift (pesado/smash), Espaço (launcher), X logo depois (perseguição), Z (Vanish com alvo),
   B (trocar alvo), botão direito (guarda). Conferir HUD expandindo e voltando a compacta.
4. Técnicas: tocar e segurar C com cada uma das 6; observar 30/60/90/100%, custo e recarga.
5. Transformar com maestria baixa e alta (`/dbil mastery dbil:super_saiyan 0|100`): aura instável x estável, custo.
6. Voo: parado, normal, rápido (Ctrl), combate (com alvo); carga de Ki em voo parado.
7. Opções: aura OFF/LOW/MEDIUM/HIGH, partículas, distância, tremor, FOV, efeitos de transformação reduzidos.
8. Dois clientes: um observa o outro transformar, disparar feixes, voar e trocar aparência.
9. Servidor com `terrainDamage=true`: crateras só com carga ≥ 60%, sem quebrar baús, bedrock ou spawn protegido.
