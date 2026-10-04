# Dragon Ball: Infinite Legacy — DBIL 0.3.0 "Visual & Combat Overhaul"

Mod original para **Minecraft Java 1.20.1 / Forge 47.3.22 / Java 17**, pensado para cooperativo privado, teclado e
controles por toque (Android). Todos os sistemas DBIL foram escritos do zero, sem código de outros mods.

A 0.3.0 transforma o protótipo funcional da 0.2 em um RPG de Dragon Ball com identidade visual própria, mantendo o
personagem com cara de Minecraft: **Steve evoluído**, não um modelo humano realista.

## O que há de novo

- **Personagem DBIL próprio**: proporções Steve/Alex, pele/rosto pintados pelo mod, **cabelos voxel 3D** (7 estilos,
  cada um com sua variante Super Saiyajin), roupas em camadas com peças 3D, acessórios e cauda Saiyajin animada.
  A skin da conta não é mais usada para personagens DBIL (continua como fallback).
- **Criação e edição de aparência** com prévia 3D: corpo A/B, pele, olhos (formato e cor), sobrancelhas, boca,
  cabelo e cor, 4 roupas com 3 cores, munhequeiras, faixa e cauda. Edite depois em **J → Personagem**.
- **Animações próprias**: postura de combate, voo parado/lento/rápido, carga de Ki, transformação, combo de 4 golpes,
  pesado, launcher, smash, guarda, dash, Vanish, poses de cada técnica, impacto, knockback, queda e pouso.
- **HUD nova** e dinâmica (compacta fora de combate, expandida em combate), pensada também para telas pequenas.
- **Auras em camadas**, Ki Charge com aura crescente, **Super Saiyajin** com sequência de transformação, cabelo
  dourado 3D, olhos alterados, aura dourada com descargas e maestria que muda a estabilidade.
- **Técnicas carregáveis** e três feixes novos: **Kamehameha**, **Galick Gun** e **Masenko**.
- **Combate corpo a corpo** com combo, launcher, smash, perseguição, **Vanish**, lock-on com troca de alvo e combate aéreo.
- **Voo rápido** com FOV, linhas de velocidade, vento e pose aerodinâmica.
- **Dano ao terreno opcional** (desligado por padrão) com proteções.

Lista completa em [CHANGELOG.md](CHANGELOG.md).

## Instalar

1. Minecraft Java 1.20.1, Forge 47.3.22+ e Java 17.
2. Remova o JAR DBIL antigo e coloque `dbil-0.3.0.jar` em `mods/`. Cliente e servidor precisam da mesma versão
   (protocolo de rede 3; versões diferentes recusam a conexão).
3. Faça backup do mundo. Personagens da 0.1/0.2 migram automaticamente (schema 4) e recebem a aparência padrão da
   raça, editável depois. Progresso, técnicas, formas e maestria são preservados.
4. Nenhuma dependência extra (sem GeckoLib, sem shaders).

## Controles (todos remapeáveis)

| Tecla | Ação |
|---|---|
| Ataque com mão vazia | Combo leve (jab → cruzado → chute → chute giratório) |
| Shift + ataque | Pesado; depois de 2+ golpes do combo vira **Smash** |
| Espaço + ataque | **Launcher** (lança o alvo para cima) |
| X | Dash (com A/D/S: lateral ou para trás). Logo após launcher/smash/final: **perseguir** |
| Z | **Vanish** para trás/lado do alvo travado |
| V / B | Travar/soltar alvo · trocar alvo |
| C (segurar) | Carregar a técnica selecionada; soltar dispara. Toque rápido = disparo imediato |
| N | Próxima técnica equipada |
| R (segurar) | Carregar Ki (no chão ou voando parado) |
| G | Ligar/desligar voo · Espaço/Shift sobem/descem · **Ctrl = voo rápido** |
| Botão direito, mão vazia, com alvo | Guarda |
| J | Menu: personagem/aparência, atributos, ações de toque, técnicas, formas, treino e opções |

Mineração com a mão vazia continua normal quando não há alvo travado por perto. Ferramentas e armas mantêm o
comportamento vanilla. No Android, **J → Ações** tem botões grandes para todas as ações (duas páginas: combate e
movimento).

## Técnicas

| Técnica | Perfil | Como obter |
|---|---|---|
| Ki Wave | esfera carregável; ≥60% explode no impacto | inicial |
| Ki Blast | rápido e barato | desafio "Primeiro combate" |
| Ki Barrage | 6 disparos menores alternando as mãos | desafio "Domínio de Ki" |
| Kamehameha | feixe azul equilibrado, carga longa | desafio "Onda concentrada" |
| Masenko | feixe amarelo largo, carga e viagem rápidas, mais fraco | desafio "Disparo relâmpago" |
| Galick Gun | feixe roxo, mais dano e empurrão, mais lento e estreito | desafio "Canhão explosivo" |

A carga altera dano, Ki gasto, knockback e tamanho (marcos visuais em 30/60/90/100%). Até 6 técnicas equipadas
(J → Técnicas).

## Transformações

Super Saiyajin (Saiyajin) e Potencial Liberado (Humano), desbloqueados no desafio "Despertar". A transformação tem
pose, aura crescente, cabelo piscando, som, reação de câmera e explosão de poder. Maestria baixa: drain maior,
ativação mais lenta, aura instável com flashes e técnicas até 25% mais caras. Maestria alta: ativação rápida,
consumo menor e aura controlada.

## Configuração

- **Cliente** `config/dbil-client.toml` (quase tudo também em J → Opções): escala/modo da HUD, qualidade da aura
  (OFF/LOW/MEDIUM/HIGH), densidade de partículas, distância de efeitos, tremor de tela, efeitos de FOV, impactos,
  animações extras, poeira/destroços, linhas de velocidade, vento, efeitos de transformação, modelo DBIL.
  **Android**: aura LOW, partículas LOW, distância 32 e escala de HUD adequada.
- **Servidor** `<mundo>/serverconfig/dbil-server.toml`: valores da 0.2 + Vanish, perseguição, voo rápido e
  terreno (`terrainDamage=false` por padrão, limite de blocos, resistência máxima, proteção de block entities,
  carga mínima). Blocos protegidos extras: tag `dbil:terrain_immune`.

## Comandos (permissão 2, exceto `info`)

`/dbil info`, `setrace`, `setki`, `addxp`, `heal`, `power`, `learn <técnica>`, **`learnall`**, `reset`, `debug`,
`spawn [n]`, `unlockform`, `transform`, `mastery`, **`appearance default [jogador]`**. Detalhes em
[docs/DEBUG_COMMANDS.md](docs/DEBUG_COMMANDS.md).

## Build

Com **JDK 17**:

```sh
./gradlew clean build          # JAR em build/libs/dbil-0.3.0.jar
./gradlew runGameTestServer    # GameTests em servidor lógico dedicado
./gradlew runClient            # cliente de desenvolvimento
```

O workflow `.github/workflows/build.yml` executa build limpo, GameTests, boot de servidor dedicado e um teste visual
automatizado de cliente (Xvfb) que percorre criação, aura, transformação, Kamehameha, combo, voo rápido e telas.

## Documentação

[Personagem e aparência](docs/VISUAL_CHARACTER.md) · [Animação](docs/ANIMATION.md) ·
[Efeitos, câmera e HUD](docs/EFFECTS_HUD.md) · [Combate e técnicas](docs/COMBAT_TECHNIQUES.md) ·
[Transformações](docs/TRANSFORMATIONS.md) · [Voo](docs/FLIGHT.md) · [Dados e migração](docs/DATA.md) ·
[Arquitetura e rede](docs/ARCHITECTURE.md) · [Contratos](CONTRACTS.md) · [Comandos](docs/DEBUG_COMMANDS.md) ·
[Validação](docs/VALIDATION.md) · [Relatório da 0.3.0](docs/SESSION_REPORT_03.md) · [Roadmap](docs/ROADMAP.md)
