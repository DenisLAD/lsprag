# Flow работы кнопки Fix Errors

## 🔄 Полный поток генерации теста

```
1. Пользователь нажимает "🚀 Generate Test"
   ↓
2. Отправка промпта в LLM
   ↓
3. Получение ответа (Java код или JSON)
   ↓
4. extractCodeFromResponse() - извлечение Java кода
   ↓
5. Создание PsiFile из строки кода
   ↓
6. CompilationValidator.validateCompilation(psiFile)
   ↓
7. Если errors.isEmpty() → "✓ Test generated successfully!"
   Если !errors.isEmpty() → "⚠ Test generated with errors!" + Fix Errors активна
```

## 🔧 Flow кнопки Fix Errors

```
1. Пользователь нажимает "🔧 Fix Errors"
   ↓
2. Показ диалога с ошибками:
   "Found compilation errors:
    Line 15: Cannot resolve symbol 'User'
    Line 23: Incompatible types
    
    Attempt to fix automatically?"
   ↓
3. Пользователь нажимает "Yes"
   ↓
4. SelfCorrectionEngine.correctCode()
   ├── 4a. Анализ ошибок через LLM
   ├── 4b. Генерация исправлений
   ├── 4c. Повторная проверка компиляции
   └── 4d. До 3 попыток (или пока не исправит)
   ↓
5. Если success → "✓ All errors fixed in X attempt(s)!"
   Если failed → "⚠ Partial fix: Y error(s) remaining"
   ↓
6. Обновление правой панели с исправленным кодом
```

## ❓ Почему кнопка была не активна

### До исправления:
```java
// БЫЛО - упрощённая проверка
hasErrors = checkForCompilationErrors(testCode);
// Проверяла только:
// - Не JSON ли это
// - Есть ли TODO/placeholder  
// - Есть ли class { }
```

**Проблема**: Когда LLM возвращал валидный Java код, проверка проходила → `hasErrors = false` → кнопка не активна

### После исправления:
```java
// СТАЛО - реальная проверка компиляции
PsiFile psiFile = PsiFileFactory.getInstance(project)
    .createFileFromText("TempTest.java", StdFileTypes.JAVA, testCode);

CompilationValidator validator = new CompilationValidator(project);
ValidationResult validationResult = validator.validateCompilation(psiFile);
hasErrors = !validationResult.isValid();
```

**Результат**: Кнопка активируется когда есть РЕАЛЬНЫЕ ошибки компиляции!

## 📊 Когда кнопка активируется

| Сценарий | Ошибки | Fix Errors активна? |
|----------|--------|---------------------|
| LLM вернул JSON → извлечён Java код | Зависит от кода | ✅ Если есть ошибки |
| LLM вернул валидный Java код | ❌ Нет | ❌ Нет |
| LLM вернул код с ошибками компиляции | ✅ Да | ✅ Да |
| LLM вернул код с TODO | ✅ Да | ✅ Да |
| Пользователь отредактировал и допустил ошибку | ✅ Да | ✅ Да |

## 🎯 Пример использования

### Сценарий 1: Ошибка в импорте
```
LLM сгенерировал:
import org.junit.Test;  // ❌ JUnit 4 вместо JUnit 5

Fix Errors активируется → Показывает ошибку:
"Line 3: Cannot resolve symbol 'Test'"

Нажимаем Fix → Исправляет на:
import org.junit.jupiter.api.Test;  // ✅ JUnit 5
```

### Сценарий 2: Неправильный метод ассерта
```
LLM сгенерировал:
assertEquals(expected, actual);  // ❌ AssertJ syntax

Fix Errors активируется → Показывает ошибку

Нажимаем Fix → Исправляет на:
assertThat(actual).isEqualTo(expected);  // ✅ AssertJ
```

### Сценарий 3: Отсутствует мок
```
LLM сгенерировал:
when(service.getById(id)).thenReturn(entity);  // ❌ service not mocked

Fix Errors активируется → Показывает ошибку

Нажимаем Fix → Добавляет:
@MockBean
private DataStorageService service;  // ✅ Mock added
```

## 📁 Файлы изменены

| Файл | Изменение |
|------|-----------|
| `TestGenerationPreviewDialog.java` | Использует CompilationValidator |
| `TestGenerationPreviewDialog.java` | Создаёт PsiFile для проверки |
| `TestGenerationPreviewDialog.java` | Показывает ошибки в диалоге |
| `TestGenerationPreviewDialog.java` | Сохраняет currentErrors |

## ✅ Итог

Теперь кнопка **Fix Errors**:
- ✅ **Активируется** когда есть реальные ошибки компиляции
- ✅ **Показывает** список ошибок пользователю
- ✅ **Спрашивает** перед исправлением
- ✅ **Исправляет** через SelfCorrectionEngine
- ✅ **Обновляет** код в правой панели

Пользователь видит что не так и может автоматически исправить ошибки! 🚀
