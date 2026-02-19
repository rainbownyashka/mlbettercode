# New Agent Handoff (Strict)

Этот файл для нового агента, который продолжает миграцию `legacy 1.12.2 -> modern fabric`.

## 1) Главный приоритет
- Цель: **observable 1:1 parity** c legacy 1.12.2.
- Не важно, какие API внутри, важно чтобы поведение в игре совпадало.
- Никогда не помечай задачу как done без фактического подтверждения по логам/смоуку.

## 2) Что пользователю НЕ нравится (критично)
- Нельзя говорить "сделано", если это не подтверждено runtime.
- Нельзя оставлять "удобные" fallback-и, ломающие parity (особенно crosshair fallback).
- Нельзя переносить частично и выдавать как полный 1:1.
- Нельзя игнорировать `latest.log`; сначала смотри лог, потом код.
- Нельзя делать поведение "примерно похоже" вместо точного legacy-контракта.

## 3) Обязательный рабочий цикл на каждый фикс
1. Прочитать `docs/STATUS.md`, `docs/TODO.md`, `docs/CURRENT_TASK_1TO1_PARITY.md`, `docs/LEGACY_1TO1_EXECUTION_SPEC.md`.
2. Прочитать `C:\Users\ASUS\AppData\Roaming\.minecraft\logs\latest.log`.
3. Выписать конкретные сигнатуры бага (trace/error/reason).
4. Сопоставить с legacy-кодом 1.12.2 (поведение, не только названия методов).
5. Исправить минимальным срезом.
6. Прогнать сборку всех 3 таргетов:
   - `modern/fabric1165: ./gradlew.bat compileJava`
   - `modern/fabric120: ./gradlew.bat compileJava`
   - `modern/fabric121: ./gradlew.bat compileJava`
7. Обновить `docs/STATUS.md` и `docs/TODO.md` по факту.
8. Сделать маленький коммит (один логический срез).
9. Для 1165: собрать jar и скопировать в `.minecraft/mods`.

## 4) Технические инварианты parity (обязательно)

### `/mldsl run`
- Placement от валидного blue-glass row как в legacy, не от взгляда.
- Меню: target order `sign(z-1)` -> fallback entry block.
- Без crosshair fallback в critical path.
- Success открытия GUI = подтвержденный window/content change (не просто interact accepted).
- Bounded retries/timeouts, explicit error code, без silent cleanup.
- Cursor/item semantics как legacy (включая timeout/fail, без тихой очистки).
- Route resolver: direct/contains/click-map/scope/fallback как legacy-порядок.

### `/module publish`
- Selected row трактуется корректно: glass anchor -> entry (`y+1`) -> sign (`z-1`).
- Проверка таблички не на glass block, а на рассчитанном sign target.
- Sign cache: источник/ключи/валидация как legacy-контракт (включая persistent behavior).
- Warmup/page semantics: bounded, с явными причинами stop/fail.
- Никаких ложных `warmup.done` при недоступном TP/контексте.

## 5) Формат отчетности пользователю
- Сначала: **что НЕ сделано** и какие риск-зоны остались.
- Затем: что сделано в этом срезе.
- Затем: какие проверки реально запускались (build/runtime/log).
- Никогда не обещать 100% до прохождения лог-гейта и смоука.

## 6) Минимальный чек перед словом "готово"
- Нет `publish.sign.invalid sign_missing/sign_empty` на валидной табличке.
- Нет бесконечного `WAIT_MENU_ACK`.
- Нет ложного `NO_PATH_GUI` для legacy-валидного события.
- Нет зависимости от взгляда для постановки блока/открытия меню.
- Есть явные trace-этапы из baseline, совпадающие по семантике.

## 7) Координация между агентами
- Использовать `agentslock/current-task.md` перед началом и после завершения среза.
- Не трогать чужие незавершенные изменения.
- Коммиты маленькие, без mega-merge.
