# STATE — текущее состояние

Last updated: 2026-07-24

## Кратко

**SkyWars** — соло last-man-standing на парящих островах. С коммита `7c283ea`
(2026-07-24) репозиторий — **тонкий игровой плагин поверх платформы MgCore**, а не
самодостаточный каркас. Весь игро-независимый код (арены, движок матча, меню,
снапшоты, откат мира, стата, HUD, i18n, утилиты) вынесен во **внешний** репозиторий
`../kiviuly-mg-core`; здесь остались только правила SkyWars.

Package root `ru.kiviuly.skywars`, плагин `SkyWars`, `depend: [MgCore]`.
**Своей команды больше нет** — админка идёт через `/mg` ядра.

## Миграция на MG Core (что изменилось)

Было ~40 java-файлов каркаса + игры → стало **13 файлов**, только игра
(`-3546/+176` строк). Каркас удалён из репо, не переписан.

### Куда что уехало

| Было (`ru.kiviuly.skywars.*`) | Стало (`ru.kiviuly.mg.api.*`, внешнее) |
|---|---|
| `game/GameSession` | `game/Match` (интерфейс-контракт) |
| `game/Minigame` | `game/Minigame` (абстрактный SPI) |
| `game/{GamePhase,MatchPlayer,MatchResult}` | `game/{GamePhase,MatchPlayer,MatchResult}` |
| `arena/{Arena,ArenaManager,ArenaCheck,SetupMarkers}` | `arena/{Arena,ArenaService,SetupMarkers}` |
| `menu/{Menu,AnvilInputMenu}` + `MenuListener` | `menu/{Menu,AnvilInputMenu}` (листенер — в ядре) |
| `util/{Msg,Items,Keys,DebugLog}` | `util/{Msg,Items,Keys,DebugLog}` |
| `player/PlayerSnapshot`, `stats/StatsRepository`, `ui/*` | реализация в `mg-core`, наружу — `stats/StatsService` |
| `command/MinigameCommand`, `listener/{Game,Chat,Protection,Setup}Listener` | целиком в `mg-core` |
| `menu/Arena*Menu` (Hub/Points/Select/Settings) | целиком в `mg-core` |

### Ключевые смены API в коде игры

- `GameSession s` → **`Match s`** во всех хуках. `s.plugin()` больше нет — доступ к
  своим реестрам через поле `plugin` в `SkyWarsGame`, к платформе — через `core`.
- `Minigame` теперь принимает `MgCore` в конструкторе: `super(core)`, поле `core`
  (`core.arenas()`, `core.stats()`, `core.transport()`).
- Регистрация: не «реестр в `onEnable`», а `core.register(game)`, где `core` берётся
  из Bukkit `ServicesManager` (`load(MgCore.class)`; нет — плагин сам себя выключает).
- `Arena.chestSpots` → обобщённые **именованные группы точек**:
  `arena.spots("chest")` → `Map<Location, List<String>>` (теги = id категорий лута),
  плюс `addSpot/removeSpot/spotAt`. Числа игры по-прежнему `getSetting/setSetting`.
- `Msg.merge(this)` в `onEnable` — свой `messages.yml` домешивается в общий каталог
  ядра; ключи игры живут в пространстве `skywars.*`.
- Админ-подкоманды: `MinigameCommand` не наш — ядро делегирует в
  `Minigame.onCommand(p, sub, args)` / `tabComplete` / `helpLines` / `onReload`.
  У SkyWars это `kits`, `loot`, `chests <arena>`.

### Сборка

- `settings.gradle.kts`: `includeBuild("../kiviuly-mg-core")` — **жёсткая привязка к
  соседнему каталогу на диске**. Артефакт нигде не опубликован: без клона
  `kiviuly-mg-core` рядом со `skywars-reborn` репозиторий **не собирается**.
- `build.gradle.kts`: `compileOnly("ru.kiviuly.mg:mg-api:1.0.0")` — в рантайме классы
  `mg-api` даёт плагин `MgCore` (они вложены в его jar), поэтому только compileOnly.
- На сервер нужны **два jar-а**: `MgCore.jar` (из `kiviuly-mg-core`, задача
  `:mg-core:deploy`) и `SkyWars-1.0.0.jar`. Порядок: без MgCore SkyWars не включится.

## Что осталось в репозитории (вся игра)

- `SkyWarsPlugin` — загрузка `MgCore` из ServicesManager, `Msg.merge`, реестры китов/
  лута, `core.register(game)`, свой листенер. Аксессоры `arenas()/core()/kits()/loot()/game()`.
- `game/SkyWarsGame` (наследник `Minigame`) — **все правила**: разминка в лобби
  (`onLobbyAttack`), капсулы (`onStart`/`onTick`), лут-сундуки (`placeChests`/
  `fillChest`/`onChestClosed`), киты (`onLobbyJoin`/`giveLoadout`), итоги (`onEnd`),
  HUD-строка (`scoreboardLines`), админ-подкоманды (`onCommand`).
- `kit/{Kit,KitRegistry}` (`kits.yml`), `loot/{WeightedItem,LootCategory,LootRegistry}`
  (`loot.yml`) — глобальные, не пер-арена.
- `listener/SkyWarsListener` — селектор кита (ПКМ), закрытие сундука (рефилл), учёт
  PvP-урона в матче. Ссылку на игру держит напрямую: в контракте `Match` нет `game()`.
- `menu/{KitSelectMenu,KitEditorMenu,KitsAdminMenu,LootAdminMenu,LootEditorMenu,ChestPointsMenu}`
  — наследники `ru.kiviuly.mg.api.menu.Menu`.

## Статус проверки

- `[DONE]` **Сборка** — `./gradlew build` ЗЕЛЁНАЯ (проверено 2026-07-24 на этом
  коммите; собирается вместе с `:kiviuly-mg-core:mg-api`), `build/libs/SkyWars-1.0.0.jar`.
- `[?]` **Смоук/плейтест на сервере** — по-прежнему НЕ проводился, и теперь это
  сложнее: нужен сервер с **обоими** плагинами. Ожидаемо в логе:
  `[SkyWars] SkyWars enabled, game registered: skywars`, ноль стектрейсов.
- `[?]` **Вся админ-цепочка после смены команд** — `/mg` вместо `/sw` не прогонялась
  ни разу; делегирование `kits`/`loot`/`chests` через ядро не проверено вживую.

## Дефекты и долги, найденные при ревизии миграции

- `[BUG]` **Капсулы/грейс больше не настраиваются из GUI.** `SkyWarsGame` читает
  `arena.getSetting("capsule-seconds", 5)` и `"grace-seconds"`, но `ArenaSettingsMenu`
  уехал в ядро и содержит только ядровые настройки (min/max players, отсчёты,
  duration) — второй ряд с капсулой/грейсом, добавленный в вехе M6, потерян. Сейчас
  меняется только ручной правкой `arenas/<id>.yml`. Чинить обобщённо: дать
  `Minigame` хук на свои строки в меню настроек арены (не хардкодить SkyWars в ядро).
- `[BUG]` **Документация протухла целиком.** `docs/01-architecture.md`,
  `02-making-a-game.md`, `03-commands-and-config.md` и `README.md` описывают снесённый
  внутренний каркас: `/sw`, `GameSession`, `ArenaManager`, `StatsRepository`,
  `PlayerSnapshot`, «каркас `mcmgp-template`». `CLAUDE.md` — там же. Всё это надо
  либо переписать под MgCore, либо выкинуть, оставив `04-skywars.md` (правила игры).
- `[BUG]` **`src/main/resources/config.yml` — мёртвый дубль.** `saveDefaultConfig()`
  зовётся, но `getConfig()` не читается нигде; секции `arena-defaults`/`match`/`chat`/
  `hud`/`debug-log` теперь принадлежат конфигу MgCore. Файл вводит в заблуждение
  (в шапке ещё и ссылка на `/sw gui`) — удалить или оставить только своё.
- `[TODO]` Неиспользованные хуки новой платформы, на которые стоит посмотреть:
  `descriptor()` (карточка игры в селекторе хаба), `allowLobbyPvp()`,
  `onLethalDamage()`, `onCleanup()`, `onPlayerRemoved()`, `onArenaCreated/Removed()`.
  В частности `onArenaCreated` — естественное место проставить дефолты капсулы/грейса.

## Известные ограничения v1 (осознанно, без изменений)

- GUI-редактор лута пишет всем предметам **вес 1**; кастомные веса — правкой `loot.yml`.
- Точки-сундуки: клик-блок становится сундуком, форсится SINGLE (иначе ломается
  рефилл); подсказок-блоков для сундуков нет (только спавны/лобби).
- Рефилл сундуков по умолчанию выключен (`refill-seconds: 0`) — классика SkyWars.
- Смерть в матче → спектатор, без респавна.

## Следующие шаги

1. Поднять сервер с `MgCore.jar` + `SkyWars-1.0.0.jar`, прогнать цепочку настройки и
   матч, снять оба `[?]`.
2. Вернуть настройку `capsule-seconds`/`grace-seconds` в GUI — обобщённым хуком в
   `Minigame`, а не игро-специфичным кодом в ядре.
3. Переписать/выпилить протухшие `docs/*` + `README.md` + `CLAUDE.md` под MgCore.
4. Разобраться с мёртвым `config.yml`.
