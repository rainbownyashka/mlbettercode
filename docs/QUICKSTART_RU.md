# MLBetterCode — быстрый старт (RU)

## Требования

- Minecraft 1.12.2
- Forge 1.12.2-14.23.5.2859
- Java 8

## Установка

1) Скачай `bettercode-*.jar` из Releases: https://github.com/rainbownyashka/mlbettercode/releases
2) Положи jar в `.minecraft/mods/`
3) Запусти игру

## MLDSL печать (самое важное)

MLDSL‑компилятор генерирует `plan.json`, а мод исполняет его в игре.

- Компилятор: https://github.com/rainbownyashka/mldsl
- В игре: `/mldsl run "%APPDATA%\\.minecraft\\plan.json"`

Пример workflow:

1) На ПК: `python tools/mldsl_compile.py my.mldsl --plan "%APPDATA%\\.minecraft\\plan.json"`
2) В игре: `/mldsl run "%APPDATA%\\.minecraft\\plan.json"`

## Минимальные команды

- `/mldsl run [path] [--start N]` — запустить план
- `/mldsl check [path] [--start N]` — проверить, что уже напечатано (без печати)
- `/placeadvanced ...` — ручной плейсер (низкоуровневый)
