# STATE — текущее состояние

Last updated: 2026-07-22

## Кратко

**SkyWars v1** реализован поверх каркаса `mcmgp-template`. Соло last-man-standing
на парящих островах: разминка в лобби (урон считается, никто не умирает), десант из
стеклянных капсул, выбираемые киты, сундуки со взвешенным лутом. Package root
`ru.kiviuly.skywars`, плагин `SkyWars`, команда `/sw` (алиас `/skywars`).

Игровая логика — в одном классе `game/SkyWarsGame` (наследник `Minigame`),
подключён в `SkyWarsPlugin.onEnable`. Ядро осталось игро-независимым: единственное
обобщённое расширение — хук `Minigame.onLobbyAttack` (+ точки-сундуки в `Arena`).

## Что сделано (по вехам, все собираются зелёным)

- **M1 Каркас** — `SkyWarsGame extends Minigame` вместо `TemplateGame` (удалён);
  обобщённый хук `Minigame.onLobbyAttack(s, victim, damager, dmg)` + вызов из ядра
  `GameListener` при лоббийном PvP.
- **M2 Разминка «Избиение в лобби»** — лоббийный PvP не наносит урон, но копится в
  `session.data()["warmup-damage"]`; фейковый фидбек (hurt-анимация + звук + отдача);
  топ-3 «самых буйных» объявляется на старте (`onStart` → `announceWarmup`).
- **M3 Капсулы** — `onStart` строит стеклянный короб на каждом спавне (спавн арены =
  точка капсулы над островом), игрок в ADVENTURE; через `capsule-seconds` короб
  снимается, SURVIVAL + slow-falling + Resistance (грейс) → десант. Стекло под откат
  (`rememberBlock`).
- **M4 Киты (глобальные)** — `kit/Kit` + `kit/KitRegistry` (`kits.yml`, 3 примера);
  выбор в лобби (`KitSelectMenu`, селектор-предмет slot 0, `none`/`random`/дефолт →
  `KitRegistry.resolve`); GUI-настройка `/sw kits` → `KitsAdminMenu` → `KitEditorMenu`
  (перетаскивание предметов, захват при закрытии); применение в `giveLoadout`
  (`Kit.apply` авто-надевает броню).
- **M5 Сундуки** — `loot/WeightedItem`, `loot/LootCategory` (взвешенный пул +
  min/max-per-chest + refill-seconds), `loot/LootRegistry` (`loot.yml`, 2 примера,
  ГЛОБАЛЬНЫЕ); точки-сундуки ПЕР-АРЕНА в `Arena.chestSpots` (маркер `type=chest`,
  категория в `MARKER_EXTRA`); GUI `/sw loot` → `LootAdminMenu` → `LootEditorMenu`
  (± настройки + предметы, вес 1); точки — `ChestPointsMenu` из `ArenaPointsMenu`;
  наполнение в `onStart` (`placeChests`/`fillChest`, одиночные сундуки), рефилл по
  закрытию (`SkyWarsListener` → `onChestClosed`), откат штатным `editedBlocks`.
- **M6 Матч/итоги** — победа = последний выживший (дефолтный `checkResult`, не
  переопределяли); учёт урона в матче (`SkyWarsListener.onPvpDamage` LOW →
  `recordMatchDamage`, `session.data()["match-damage"]`); HUD-строка «Лидер урона»
  (`scoreboardLines`); итог победителя (убийства+урон) в `onEnd`; настройки
  `capsule-seconds`/`grace-seconds` в `ArenaSettingsMenu` (2-й ряд).

## Новые/изменённые файлы поверх шаблона

- Новые: `game/SkyWarsGame`, `kit/{Kit,KitRegistry}`, `loot/{WeightedItem,LootCategory,LootRegistry}`,
  `listener/SkyWarsListener`, `menu/{KitSelectMenu,KitEditorMenu,KitsAdminMenu,LootAdminMenu,LootEditorMenu,ChestPointsMenu}`,
  ресурсы `kits.yml`, `loot.yml`.
- Изменены: `game/Minigame` (+onLobbyAttack), `listener/GameListener` (вызов хука),
  `arena/Arena` (+chestSpots), `listener/SetupListener` (тип `chest`), `util/Keys`
  (+KIT_ID/CATEGORY_ID), `menu/{ArenaPointsMenu,ArenaSettingsMenu}`,
  `command/MinigameCommand` (+`kits`/`loot`), `SkyWarsPlugin` (реестры),
  `messages.yml`, `plugin.yml`. Удалён `game/TemplateGame`.

## Статус проверки

- `[DONE]` **Сборка** — `./gradlew build` ЗЕЛЁНЫЙ на каждой вехе,
  `build/libs/SkyWars-1.0.0.jar`.
- `[?]` **Смоук/плейтест на сервере** — НЕ проводился (Paper-сервер не поднимался).
  Ожидаемо: `[SkyWars] SkyWars enabled`, ноль стектрейсов. Проверять по
  `docs/04-skywars.md` (цепочка настройки + прогон матча).

## Известные ограничения v1 (осознанно)

- GUI-редактор лута пишет всем предметам **вес 1**; кастомные веса — правкой `loot.yml`.
- Точки-сундуки: клик-блок становится сундуком; ставить их с зазором (одиночные
  форсятся, но логичнее не впритык). Подсказок-блоков для сундуков нет (только спавны/лобби).
- Рефилл сундуков по умолчанию выключен (`refill-seconds: 0` в примерах) — классика SkyWars.
- Смерть в матче → спектатор (без респавна), как договорено.

## Следующие шаги

1. Плейтест по `docs/04-skywars.md`, снять `[?]`.
2. Возможные v2: сундуки-подсказки в мире, веса в GUI, командные режимы, центр-остров,
   спец-предметы (эндер-жемчуг уже в луте), таблица лидеров урона в HUD пер-игрока.
