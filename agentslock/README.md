# Agents Lock Protocol

`agentslock/current-task.md` — единый оперативный lock-файл для текущей активной задачи.

## Правила
- Перед началом работы каждый агент читает `agentslock/current-task.md`.
- Если берешь задачу — обнови поля `owner`, `status`, `scope`, `updated_at`.
- Если задача завершена — поставь `status: done` и кратко запиши `next`.
- Не пиши здесь планы на недели; только активный кусок работы.

## Формат
- `task_id`: короткий id (например `MOD-091`)
- `owner`: имя агента/сессии
- `status`: `in_progress | blocked | done`
- `scope`: что делается прямо сейчас
- `files`: ключевые файлы
- `updated_at`: ISO дата-время
- `next`: следующий шаг
