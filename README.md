# MLBetterCode (BetterCode)

Клиентский мод под **Minecraft 1.12.2 (Forge)** для Mineland Creative+/K+: ускоряет “печать” кода, кеширует GUI/сундуки и даёт команды-утилиты для разработчика (включая запуск `plan.json` из компилятора MLDSL).

## Требования

- Minecraft `1.12.2`
- Forge `1.12.2-14.23.5.2859+`
- Java `8`

## Установка

1) Скачай `bettercode-*.jar` из **Releases** этого репозитория.
2) Положи в `.minecraft/mods/`
3) Перезапусти игру.

## MLDSL (язык/компилятор)

MLDSL компилирует `.mldsl` → `plan.json`, который этот мод умеет печатать.

- Репозиторий: https://github.com/rainbownyashka/mldsl
- В игре: `/mldsl run "%APPDATA%\\.minecraft\\plan.json"`

## Команды (основное)

- `/mldsl run [path] [--start N]` — печать `plan.json` через текущий механизм `/placeadvanced`.
- `/placeadvanced ...` — печать блоков/действий + заполнение сундука параметров.
- `/loadmodule <postId> [file]` + `/confirmload` — загрузка и печать модулей с MLDSL Hub.
- `/exportcode [floorsCSV] [name]` — экспорт кода в `exportcode_*.json` (только блоки+таблички). Если есть выделение Code Selector — экспортирует выделенное.
- `/codeselector` — выдать Code Selector (RMB toggle строку, LMB очистить, F закончить).
- `/selectfloor <1..20>` — выбрать этаж по номеру для `/exportcode` (Y = N*10-10), если не указан `floorsCSV`.
- `/copycode ...` + `/cancelcopy` — перенос кода между участками.
- `/regallactions [stop]` — автокеширование GUI действий, экспорт в `.minecraft/regallactions_export.txt`.

## Лицензия

См. `LICENSE`.
