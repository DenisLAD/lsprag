# Исправление проблемы с JSON вместо Java кода

## ❌ Проблема

LLM возвращал JSON вместо Java кода:
```json
{
  "testClass": "DataStorageControllerTest",
  "package": "ru.progredis.dataserver.controller",
  "imports": [...],
  "testMethods": [
    {
      "name": "should_return_200_ok_response...",
      "codeSnippet": "@Test\nvoid should_return..."
    }
  ]
}
```

## ✅ Решение

### 1. Обновлён System Prompt

Добавлены чёткие инструкции что нужен ТОЛЬКО Java код:

```
ВАЖНО: Вы должны вернуть ТОЛЬКО Java код тестового класса. НЕ JSON, НЕ описание, НЕ markdown.

Формат вывода:
- Верните ТОЛЬКО Java код, начиная с package declaration
- НЕ включайте JSON, markdown, или текстовые описания
- НЕ включайте объяснений или комментариев о том что вы делаете
- Начните с: package ...
- Закройте последней }: класса
```

### 2. Обновлён User Prompt

Добавлены критические требования:

```
КРИТИЧЕСКИ ВАЖНО:
- Верните ТОЛЬКО Java код, БЕЗ JSON, БЕЗ markdown, БЕЗ текстовых описаний
- Начните с: package ...
- Включите все необходимые imports
- Создайте полный класс с тестовыми методами
- Каждый тест должен быть аннотирован @Test
- Используйте JUnit 5 и AssertJ для ассертов
- Закройте код последней скобкой }

НЕ возвращайте JSON! НЕ возвращайте описание тестов! ТОЛЬКО Java код!
```

### 3. Добавлена обработка JSON ответа

Если LLM всё равно вернул JSON, плагин автоматически извлечёт Java код:

```java
// Проверяем есть ли JSON с testMethods
if (response.contains("\"testMethods\"") && response.contains("\"codeSnippet\"")) {
    return extractCodeFromJson(response);
}

// Извлекаем package, imports и все codeSnippets
// Собираем в полный Java класс
```

### 4. Улучшена проверка ошибок

```java
private boolean checkForCompilationErrors(String code) {
    // Проверяем что вернулся JSON вместо Java
    if (code.trim().startsWith("{") && code.contains("\"testMethods\"")) {
        System.out.println("WARNING: LLM returned JSON instead of Java code!");
        return true;
    }
    
    // Проверяем что код похож на Java
    if (!code.contains("class ") || !code.contains("{") || !code.contains("}")) {
        return true;
    }
    
    return false;
}
```

## 📝 Результат

Теперь плагин:
1. ✅ **Требует Java код** в промпте
2. ✅ **Извлекает Java код** из JSON если LLM вернул JSON
3. ✅ **Показывает кнопку Fix Errors** когда есть проблемы
4. ✅ **Обрабатывает оба формата** - и чистый Java, и JSON

## 🔧 Файлы изменены

| Файл | Изменение |
|------|-----------|
| `ContextBuilder.java` | Обновлены System и User промпты |
| `TestGenerationPreviewDialog.java` | Добавлен `extractCodeFromJson()` |
| `TestGenerationPreviewDialog.java` | Улучшена `checkForCompilationErrors()` |

## 🚀 Использование

1. **Запустите плагин** через Run Plugin конфигурацию
2. **Выберите метод** → ПКМ → Generate Reasoning Tests
3. **Просмотрите промпт** в левой панели
4. **Нажмите Generate Test**
5. **Если LLM вернул JSON** - плагин автоматически извлечёт Java код
6. **Если есть ошибки** - кнопка Fix Errors будет активна
7. **Сохраните тест** через Save Test кнопку

## 💡 Рекомендации

Если LLM продолжает возвращать JSON:
1. Добавьте в конец промпта: "```java\npackage"
2. Уменьшите temperature (0.2-0.3)
3. Используйте более конкретную модель (GPT-4 вместо GPT-3.5)
