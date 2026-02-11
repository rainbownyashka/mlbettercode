# rag_export_search (Ollama)

CLI для семантического поиска по `regallactions_export.txt`.

## Что делает
- Парсит `# record` блоки (`path/category/subitem/gui/sign*/item`).
- Считает лексический скор.
- Через **Ollama embeddings** считает семантический скор.
- Выводит топ релевантных действий.

## Требования
- Python 3.10+
- Ollama запущен локально (`http://127.0.0.1:11434`)
- Модель эмбеддингов, например:
  - `ollama pull nomic-embed-text`

## Примеры
```bash
python tools/rag_export_search/search.py "C:\Users\ASUS\AppData\Roaming\.minecraft\regallactions_export.txt" "анимация урона руки" --top 8
```

С явной моделью:
```bash
python tools/rag_export_search/search.py "C:\Users\ASUS\AppData\Roaming\.minecraft\regallactions_export.txt" "получить расстояние между местоположениями" --model nomic-embed-text
```

Только лексика (без Ollama):
```bash
python tools/rag_export_search/search.py "C:\Users\ASUS\AppData\Roaming\.minecraft\regallactions_export.txt" "выбрать мобов по условию" --no-ollama
```

## Векторный индекс (быстрый повторный поиск)
Построить индекс один раз:
```bash
python tools/rag_export_search/search.py "C:\Users\ASUS\AppData\Roaming\.minecraft\regallactions_export.txt" "init" --index tools/rag_export_search/index_regallactions.json --build-index --model nomic-embed-text
```

Искать с использованием индекса:
```bash
python tools/rag_export_search/search.py "C:\Users\ASUS\AppData\Roaming\.minecraft\regallactions_export.txt" "выбрать случайного моба/сущность" --index tools/rag_export_search/index_regallactions.json --model nomic-embed-text --top 10
```
