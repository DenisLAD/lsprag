# Non-Blocking Preview Dialog Feature

## Обзор

Новый подход к генерации тестов разделён на **два этапа**:

1. **Сбор данных** (быстрый, фоновый)
2. **Просмотр и редактирование промпта** (неблокирующий диалог)
3. **Генерация теста через LLM** (по кнопке пользователя)

## Преимущества

### До (старый подход)
```
Выбор метода → Сбор данных → Ожидание LLM (30-60с) → Результат
                    ↑_____________ Блокирующий процесс _____________↑
```

### После (новый подход)
```
Выбор метода → Сбор данных (5с) → Диалог с промптом → [Редактирование] → Генерация (30-60с)
                    ↑___ Быстро ___↑  ↑___ Неблокирующий ___↑  ↑___ По кнопке ___↑
```

## Как это работает

### Этап 1: Сбор данных (Background Task)
```java
ProgressManager.getInstance().run(new Task.Backgroundable(project, "Collecting Method Context") {
    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        // 1. Extract PSI context (ReadAction)
        MethodContext context = ReadAction.compute(() -> extractor.extract(method));
        
        // 2. Build prompts
        PromptBundle promptBundle = contextBuilder.buildPromptBundle(context);
        
        // 3. Build full prompt
        String fullPrompt = promptBundle.systemPrompt() + "\n\n=== USER PROMPT ===\n\n" + 
                           promptBundle.userPrompt();
        
        // 4. Show preview dialog (non-blocking)
        ApplicationManager.getApplication().invokeLater(() -> {
            TestGenerationPreviewDialog dialog = new TestGenerationPreviewDialog(
                project, className, methodName, fullPrompt
            );
            dialog.show();
        });
    }
});
```

### Этап 2: Preview Dialog (Non-blocking)
```
┌─────────────────────────────────────────────────────────────┐
│  Test Generation Preview - OrderService.calculateDiscount   │
├──────────────────────────┬──────────────────────────────────┤
│  📝 Prompt (editable)    │  ✨ Generated Test               │
│  ┌────────────────────┐  │  ┌────────────────────────────┐ │
│  │ You are a Senior   │  │  │ Click 'Generate Test' to   │ │
│  │ Test Engineer...   │  │  │ start generation...        │ │
│  │                    │  │  │                            │ │
│  │ ## Method Sig...   │  │  │                            │ │
│  │ ## CFG...          │  │  │                            │ │
│  │ ## Dependencies... │  │  │                            │ │
│  └────────────────────┘  │  └────────────────────────────┘ │
│  [💾 Save Prompt] [📋Copy]│  [🚀Generate] [💾Save Test]     │
├──────────────────────────┴──────────────────────────────────┤
│  Ready. Edit prompt if needed, then click 'Generate Test'   │
└─────────────────────────────────────────────────────────────┘
```

## Функциональность диалога

### Левая панель (Prompt)
- ✏️ **Редактируемый промпт** - можно изменить перед отправкой
- 💾 **Save Prompt** - сохранить промпт в файл для анализа
- 📋 **Copy** - копировать промпт в буфер обмена

### Правая панель (Result)
- 🚀 **Generate Test** - отправить промпт в LLM
- 💾 **Save Test** - сохранить сгенерированный тест в файл
- 📝 **Отображение результата** - показывает сгенерированный код

### Статус бар
- Индикатор прогресса
- Сообщения о статусе
- Ошибки (если есть)

## Пользовательский сценарий

### 1. Открытие диалога
```
ПКМ на методе → Generate Reasoning Tests
↓
Сбор данных (3-5 секунд)
↓
Открывается Preview Dialog
```

### 2. Редактирование промпта (опционально)
```
Пользователь видит промпт
↓
Может редактировать текст
↓
Может сохранить для анализа
```

### 3. Генерация теста
```
Нажать "🚀 Generate Test"
↓
Отправка в LLM (30-60 секунд)
↓
Прогресс бар показывает статус
↓
Результат появляется в правой панели
```

### 4. Сохранение результата
```
Нажать "💾 Save Test"
↓
Выбрать место сохранения
↓
Файл теста создан
```

## Технические детали

### Non-blocking подход
```java
// Диалог НЕ модальный
setModal(false); // Non-blocking

// Пользователь может:
// - Редактировать промпт
// - Сохранить промпт
// - Закрыть диалог
// - Продолжить работу в IDE
```

### Генерация в фоне
```java
ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Test") {
    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        // LLM вызов в фоне
        String testCode = llmProvider.chat(editedPrompt, systemPrompt);
        
        // Обновление UI на EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            generatedCode = testCode;
            updateUI();
        });
    }
});
```

## Преимущества нового подхода

### 1. Контроль над промптом
- ✅ Видишь что отправляется в LLM
- ✅ Можешь исправить ошибки
- ✅ Можешь добавить контекст
- ✅ Можешь сохранить для анализа

### 2. Non-blocking
- ✅ IDE не блокируется
- ✅ Можно продолжать работу
- ✅ Можно отменить генерацию
- ✅ Прогресс виден всегда

### 3. Гибкость
- ✅ Можно сохранить промпт
- ✅ Можно сохранить результат
- ✅ Можно скопировать в буфер
- ✅ Можно закрыть и открыть снова

### 4. Отладка
- ✅ Видишь точный промпт
- ✅ Понимаешь что пошло не так
- ✅ Можешь исправить без перегенерации
- ✅ Сохраняешь промпты для анализа

## Пример использования

### Сценарий: Добавление контекста в промпт

1. **Открываем диалог**
   ```
   ПКМ на OrderService.calculateDiscount → Generate Tests
   ```

2. **Видим промпт**
   ```
   ## Method Signature
   Class: OrderService
   Method: calculateDiscount
   ...
   ```

3. **Добавляем контекст**
   ```
   ## Additional Context
   - This method is used in checkout flow
   - VIP users have special pricing
   - Discount should never exceed 50%
   - Test should cover all user types
   ```

4. **Генерируем**
   ```
   Click "🚀 Generate Test"
   ↓
   LLM учитывает дополнительный контекст
   ↓
   Генерирует более релевантные тесты
   ```

## Настройки

В `Settings → Tools → Reasoning Test Generator`:

- ☑ **Show Preview Before Insert** - всегда показывать диалог (по умолчанию: true)
- ☑ **Auto-format After Insert** - форматировать код после вставки

## Файлы

- `TestGenerationPreviewDialog.java` - новый диалог
- `GenerateTestsAction.java` - обновлённое действие (two-phase)

## Команды

```bash
# Сборка
gradlew.bat clean buildPlugin --no-daemon

# Файл плагина
build/distributions/reasoning-test-generator-1.0.0.zip
```

## Результат

✅ **Быстрый сбор данных** - 3-5 секунд  
✅ **Неблокирующий диалог** - IDE не зависает  
✅ **Редактирование промпта** - полный контроль  
✅ **Генерация по кнопке** - пользователь решает когда  
✅ **Сохранение промптов** - для анализа и отладки  
✅ **Сохранение тестов** - сразу в нужный файл  

Это **значительно улучшает UX** и даёт пользователю **полный контроль** над процессом! 🚀
