# SkyWars — читай и работай

**SkyWars** — соло last-man-standing на парящих островах (Paper 26.1.2, Java 25,
Gradle), построен на переиспользуемом каркасе `mcmgp-template` (арены, жизненный цикл
матча, сетап-GUI, снапшоты, откат мира, SQLite-стата, HUD). Вся игровая логика — в
`game/SkyWarsGame` (наследник `Minigame`); ядро осталось игро-независимым.
Package root: `ru.kiviuly.skywars`. Правила игры — `docs/04-skywars.md`.

## Порядок действий — без самодеятельности

1. **`.memories/INDEX.md`** — прочитал. Карта базы знаний.
2. **`.memories/STATE.md`** — узнал, что развёрнуто, что не проверено, где фронт.
3. Меняешь код? Сначала **`.memories/CONVENTIONS.md`** — стиль и железные правила.
4. Как устроено ядро — **`docs/01-architecture.md`**. Правила SkyWars и настройка —
   **`docs/04-skywars.md`**. Команды и конфиг — **`docs/03-commands-and-config.md`**.
   Как вообще делают игру на каркасе — `docs/02-making-a-game.md`.

## Каркас держи чистым (он игро-независим)

- Не тащи в ядро игровую специфику. Правила SkyWars живут **только** в
  `game/SkyWarsGame` (наследник `Minigame`) + `listener/SkyWarsListener` + свои меню/
  реестры, а не в `arena/`/`game/`-движке/`listener/GameListener` и т.п.
- Точка расширения одна: `Minigame` + регистрация в `SkyWarsPlugin.onEnable`
  (`new SkyWarsGame(this)`). `Minigame` — логика без состояния; состояние матча живёт
  в `GameSession` (`data()` + список `MatchPlayer`). Расширил ядро (напр. `onLobbyAttack`,
  `Arena.chestSpots`) — сделай это обобщённо, чтобы годилось любой игре.
- Обобщённые числа игры — в `Arena` через `getSetting/setSetting`, не новые поля
  в `Arena`. Расширяешь ядро — расширяй его обобщённо, чтобы годилось любой игре.

## Команды — всё, что нужно

```bash
./gradlew build                                   # сборка → build/libs/SkyWars-1.0.0.jar
./gradlew deploy -PdeployDir=<server>/plugins     # сборка + jar в тестовый сервер
```

JDK 25 скачается сам (toolchain + foojay-resolver). Успех: строка
`[SkyWars] SkyWars enabled` в логе и **ноль** стектрейсов. Не запустил и не проверил —
значит не сделал.

## Железные правила (инварианты)

1. **Тексты игрокам — только `messages.yml`** (MiniMessage) через `util/Msg`.
   Захардкодил строку для игрока — переделывай.
2. **Логи сервера — только ASCII.** Windows-консоль коверкает не-латиницу.
   Игрокам — MiniMessage; в консоль — ASCII (`DebugLog` уже фильтрует).
3. **Данные на предметах/сущностях — только PDC** (`util/Keys`). Парсить имя/лор
   запрещено — лор это отображение.
4. **Изменил блок в матче** → сначала `session.rememberBlock(block)`.
   **Заспавнил сущность** → `session.trackEntity(...)`. После матча мир = как до него.
5. **Вход в сессию** → `PlayerSnapshot.save` + `clear`. **Любой выход** →
   `restore`. Игрок никогда не уносит игровые предметы и не теряет свои.
6. **Игровая логика — main thread.** Async — только SQLite.
7. **GUI — только через `menu/Menu`** (InventoryHolder). Заголовки инвентарей не сравниваем.
8. **MiniMessage-плейсхолдеры не должны совпадать с именами тегов** (число — `<n>`,
   не `<gold>`); ключи YAML не должны быть голыми `on/off/yes/no` (парсятся как
   boolean) — пиши `state-on`/`state-off`.

Подробно с обоснованиями — `.memories/CONVENTIONS.md`.

## Что считается «сделано»

1. `./gradlew build` (или `deploy`) прошёл — сборка зелёная.
2. Сервер поднялся, лог чистый (`[SkyWars] SkyWars enabled`, ноль стектрейсов), фича
   проверена руками (смоук в игре).
3. `.memories/STATE.md` обновлён; при накоплении истории — запись в
   `.memories/JOURNAL/YYYY-MM-DD.md`.

Пропустил пункт 3 — работа не сделана: база знаний сгнила, следующий агент работает
вслепую. Не будь этим агентом. Память противоречит коду → прав код, чини память.
