# SkyWars — Reborn

![Paper](https://img.shields.io/badge/Paper-26.1.2-blue?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?style=flat-square)
![Status](https://img.shields.io/badge/status-v1%20%E2%80%94%20сборка%20зелёная%2C%20нужен%20плейтест-yellow?style=flat-square)

Мини-игра **SkyWars** для Paper 26.1.2 (Java 25, Gradle). Соло last-man-standing на
парящих островах: пока набирается лобби — **разминка** (урон считается, никто не
умирает, топ-3 самых буйных перед стартом), затем **десант из стеклянных капсул** на
свои острова, **киты**, **сундуки со случайным лутом** и бой до последнего живого.

Построена на переиспользуемом каркасе `mcmgp-template`: всё игро-независимое (арены,
жизненный цикл матча, сетап-GUI, снапшоты игроков, откат мира, SQLite-стата, HUD)
берётся из ядра; правила SkyWars живут в одном классе `game/SkyWarsGame`.

## Как играется

1. **Лобби.** Ждёшь игроков и разминаешься кулаками — урон не проходит, но считается.
   Тут же выбираешь стартовый **набор** (предмет-селектор). Перед десантом — топ-3.
2. **Капсулы.** На старте ты заперт в стеклянной капсуле над своим островом. Через
   пару секунд она исчезает — плавно падаешь на остров со своим набором.
3. **Бой.** Лутаешь сундуки, строишь мосты, деремся. Упал в пустоту / погиб — спектатор.
4. **Победа.** Остался один — забирает всё. В HUD видно лидера по урону.

## Быстрый старт

```bash
./gradlew build                                   # jar → build/libs/SkyWars-1.0.0.jar
./gradlew deploy -PdeployDir=<server>/plugins     # сборка + копия в тестовый сервер
```

JDK 25 скачается сам (Gradle toolchain + foojay-resolver). Успех на сервере:
строка `[SkyWars] SkyWars enabled` в логе и **ноль** стектрейсов. Дальше — арена:

```
/sw create arena1        создать арену (мир = твой текущий, лобби = здесь)
/sw gui arena1           хаб: Точки (лобби / спавны-капсулы / сундуки), Параметры
/sw kits                 глобальные стартовые наборы (GUI)
/sw loot                 глобальные категории лута сундуков (GUI)
/sw check arena1         валидатор — почини CRITICAL
/sw enable arena1        включить; игроки заходят /sw или /sw join arena1
```

Полная цепочка настройки, правила и все нюансы — **[docs/04-skywars.md](docs/04-skywars.md)**.

## Структура

Package root: `ru.kiviuly.skywars`.

| Пакет | Назначение |
|---|---|
| `game/` | **`SkyWarsGame`** (все правила), `Minigame` (точка расширения), `GameSession` (движок ядра), `MatchPlayer`, `MatchResult`, `GamePhase` |
| `kit/` | `Kit`, `KitRegistry` (глобальные киты, `kits.yml`) |
| `loot/` | `WeightedItem`, `LootCategory`, `LootRegistry` (глобальные категории, `loot.yml`) |
| `arena/` | `Arena` (+ точки-сундуки), `ArenaManager`, `ArenaCheck`, `SetupMarkers` |
| `menu/` | сетап-GUI ядра + `KitSelect/KitEditor/KitsAdmin/LootAdmin/LootEditor/ChestPoints` |
| `listener/` | `GameListener` (ядро) + `SkyWarsListener` (селектор кита, урон, рефилл) |
| `player/` · `stats/` · `ui/` · `util/` | снапшоты · SQLite · HUD · `Keys`/`Items`/`Msg`/`DebugLog` |

Архитектура ядра — [docs/01-architecture.md](docs/01-architecture.md); команды и конфиг —
[docs/03-commands-and-config.md](docs/03-commands-and-config.md).

## Требования

- **Paper 26.1.2** (Java 25). Gradle toolchain скачает JDK сам.
- Ноль внешних зависимостей, ноль NMS: публичный Paper API, Adventure/MiniMessage,
  встроенный в Paper SQLite. Все тексты игрокам — в `messages.yml` (MiniMessage).

## Для разработчиков и агентов

Маршрут: **[CLAUDE.md](CLAUDE.md)** → [`.memories/INDEX.md`](.memories/INDEX.md) →
[`.memories/STATE.md`](.memories/STATE.md). Там порядок работы, железные правила и
определение «сделано». Правила игры и настройка — [docs/04-skywars.md](docs/04-skywars.md).
