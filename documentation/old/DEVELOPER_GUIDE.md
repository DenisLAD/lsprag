# Reasoning Test Generator - Developer Guide

## 📖 Содержание

1. [Обзор проекта](#обзор-проекта)
2. [Архитектура](#архитектура)
3. [Reasoning Flow](#reasoning-flow)
4. [Ключевые компоненты](#ключевые-компоненты)
5. [Правила разработки](#правила-разработки)
6. [ReadAction/WriteAction](#readactionwriteaction)
7. [Интеграция LLM](#интеграция-llm)
8. [Тестирование](#тестирование)
9. [Сборка и запуск](#сборка-и-запуск)
10. [Что осталось реализовать](#что-осталось-реализовать)

---

## Обзор проекта

**Reasoning Test Generator** - плагин для IntelliJ IDEA, генерирующий unit-тесты с использованием LLM-powered reasoning подхода.

### Ключевые особенности

- **5-шаговый Reasoning Pipeline** - Intent → Scenarios → Design → Code → Self-Validation
- **Полный PSI анализ** - CFG, зависимости, DTO/POJO, трансформации данных
- **Spring-aware** - автоопределение Controller/Service/Repository
- **Self-Correction** - автоматическое исправление ошибок компиляции
- **Настраиваемый** - 20+ параметров конфигурации

### Технологический стек

| Технология | Версия | Назначение |
|-----------|--------|-----------|
| **Java** | 17+ | Основной язык |
| **IntelliJ Platform** | 2024.1+ | SDK плагина |
| **Gradle** | 8.6 | Система сборки |
| **OkHttp** | 4.12.0 | HTTP клиент для LLM |
| **Jackson** | 2.16.1 | JSON сериализация |
| **JUnit 5** | 5.10.1 | Тестирование |

---

## Архитектура

### Компонентная диаграмма

```
┌─────────────────────────────────────────────────────────────────┐
│                    Plugin Components                              │
├──────────────┬──────────────┬──────────────┬─────────────────────┤
│ PSI Extractor│ Context      │ LLM Reasoning│ Self-Correction     │
│              │ Builder      │ Engine       │ Engine              │
├──────────────┼──────────────┼──────────────┼─────────────────────┤
│ Test         │ Compilation  │ Prompt       │ GigaChat Provider   │
│ Generator    │ Validator    │ History      │ LM Studio Provider  │
├──────────────┴──────────────┴──────────────┴─────────────────────┤
│                    UI Components                                   │
│  - GenerateTestsAction  - TestGenerationPreviewDialog             │
│  - PluginSettings       - PluginSettingsConfigurable              │
└─────────────────────────────────────────────────────────────────┘
```

### Структура пакетов

```
com.reasoningtestgen/
├── action/                      # UI Actions
│   ├── GenerateTestsAction      # Главное действие
│   └── TestGenerationPreviewDialog  # Диалог предпросмотра
├── builder/                     # Построение промптов
│   └── ContextBuilder           # Генерация промпта из контекста
├── extractor/                   # PSI анализ
│   ├── PSIExtractor             # Основной экстрактор
│   └── DependencyInfoExtractor  # Извлечение зависимостей
├── generator/                   # Генерация файлов
│   └── TestGenerator            # Создание тестовых файлов
├── llm/                        # LLM провайдеры
│   ├── LLMProvider              # Интерфейс провайдера
│   ├── LLMProviderFactory       # Фабрика провайдеров
│   ├── LLMProviderType          # Enum типов провайдеров
│   ├── GigaChatProvider         # GigaChat API
│   ├── LMStudioProvider         # LM Studio (localhost:1234)
│   ├── OllamaProvider           # Ollama (local models)
│   ├── OpenAIProvider           # OpenAI API
│   ├── LLMProviderWithLogging   # Обёртка с логированием
│   └── ReasoningEngine          # 4-шаговый pipeline
├── model/                      # Модели данных (17 файлов)
│   ├── MethodContext            # Основной контекст метода
│   ├── CalledMethodInfo         # Вызываемые методы
│   ├── DTOInfo                  # DTO/POJO структуры
│   ├── DataTransformation       # Трансформации данных
│   ├── DependencyInfo           # Информация о зависимостях
│   └── ...                      # Остальные модели
├── refiner/                    # Самокоррекция
│   └── SelfCorrectionEngine     # Исправление ошибок
├── service/                    # Сервисы
│   └── PromptHistoryService     # История промптов
├── settings/                   # Настройки
│   ├── PluginSettings           # Хранение настроек
│   └── PluginSettingsConfigurable  # UI настроек
└── validator/                  # Валидация
    └── CompilationValidator     # Проверка компиляции
```

---

## Reasoning Flow

### Полный flow генерации теста

```
┌─────────────────────────────────────────────────────────────┐
│                    User Action                                │
│  ПКМ на методе → Generate Reasoning Tests                     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 1: PSI Extraction (ReadAction)                          │
│  - Извлечение сигнатуры метода                                │
│  - Построение CFG (Control Flow Graph)                        │
│  - Анализ зависимостей                                        │
│  - Поиск Spring реализаций интерфейсов                        │
│  - Извлечение вызываемых методов                              │
│  - Анализ DTO/POJO структур                                   │
│  - Отслеживание трансформаций данных                          │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 2: Context Building                                   │
│  - Формирование System Prompt (роль Senior Test Engineer)    │
│  - Формирование User Prompt (13 секций)                       │
│  - Определение типа класса (Controller/Service/Repository)    │
│  - Добавление контекстных рекомендаций                        │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 3: Preview Dialog (Non-blocking)                        │
│  - 📝 Prompt - редактирование промпта                        │
│  - 🌳 Scenarios - дерево сценариев из CFG                     │
│  - 📊 Coverage - метрики покрытия                             │
│  - Пользователь может редактировать промпт                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 4: LLM Generation (Background Task)                     │
│  - Отправка промпта в LLM (OpenAI/GigaChat/LM Studio/Ollama)  │
│  - Получение ответа                                           │
│  - Извлечение Java кода (из markdown или JSON)                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 5: Validation (WriteCommandAction)                      │
│  - PSI проверка синтаксиса                                    │
│  - Проверка на ошибки компиляции                              │
│  - Если ошибки → активация кнопки Fix Errors                  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 6: Self-Correction (if needed)                          │
│  - Анализ ошибок через LLM                                    │
│  - Генерация исправлений                                      │
│  - Повторная проверка                                         │
│  - До успеха или maxAttempts                                  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 7: Save (FileSaverDialog)                               │
│  - Нативный диалог сохранения IDEA                            │
│  - Запись файла                                               │
│  - Refresh VFS                                                │
└─────────────────────────────────────────────────────────────┘
```

### Структура промпта (13 секций)

1. **System Prompt** - роль и инструкции
2. **Сигнатура метода** - класс, метод, параметры
3. **Граф потока управления (CFG)** - ветвления с номерами строк
4. **Зависимости** - внешние зависимости
5. **Детали зависимостей** - контракты интерфейсов
6. **Spring реализации** - @Service/@Component/@Repository классы
7. **Lombok методы** - генерируемые геттеры/сеттеры
8. **MapStruct** - mapper конфигурация
9. **Документация и контракт** - @param, @return, @throws
10. **Метрики сложности** - цикломатическая, глубина, ветки
11. **Вызываемые методы** - с сигнатурами
12. **DTO/POJO структуры** - поля и аннотации
13. **Трансформации данных** - шаги изменений параметров
14. **Специфика класса** - Controller/Service/Repository рекомендации
15. **Задача** - критические инструкции для LLM

---

## Ключевые компоненты

### PSIExtractor

Извлекает полный контекст из PSI дерева:

```java
// Основной метод - должен вызываться в ReadAction
public MethodContext extract(@NotNull PsiMethod method) {
    return ReadAction.compute(() -> {
        // Извлечение всех данных
        return new MethodContext(...);
    });
}

// Проверка типа класса
public boolean isRestController(@Nullable PsiClass psiClass)
public boolean isSpringService(@Nullable PsiClass psiClass)
public boolean isSpringRepository(@Nullable PsiClass psiClass)
```

### ContextBuilder

Строит промпт из MethodContext:

```java
public PromptBundle buildPromptBundle(@NotNull MethodContext context) {
    String systemPrompt = buildSystemPrompt();  // Роль Senior Test Engineer
    String userPrompt = buildUserPrompt(context);  // 13 секций
    List<String> examples = extractExamples(context.existingTests());
    
    return new PromptBundle(systemPrompt, userPrompt, examples);
}
```

### SelfCorrectionEngine

Исправляет ошибки через LLM reasoning:

```java
public CorrectionResult correctCode(
    @NotNull GeneratedCode code,
    @NotNull TestDesign design,
    @NotNull String originalCode
) {
    // Цикл исправлений
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        List<String> errors = validateCode(currentCode);  // PSI проверка
        if (errors.isEmpty()) return success;
        
        String analysis = analyzeErrors(currentCode, errors, design);
        currentCode = generateFix(currentCode, errors, analysis, design);
    }
    return failure;
}
```

### TestGenerationPreviewDialog

Неблокирующий диалог с 4 вкладками:

```java
// Вкладки:
// 📝 Prompt - редактируемый промпт
// 🌳 Scenarios - дерево сценариев
// 📊 Coverage - метрики покрытия
// ✨ Generated Test - результат с подсветкой
```

---

## Правила разработки

### 1. ReadAction для PSI чтения

**ВСЕ** обращения к PSI должны быть в `ReadAction.compute()`:

```java
// ✅ ПРАВИЛЬНО
MethodContext context = ReadAction.compute(() -> {
    PsiClass containingClass = method.getContainingClass();
    String className = containingClass.getName();
    // ... другие PSI операции
    return new MethodContext(...);
});

// ❌ НЕПРАВИЛЬНО
PsiClass containingClass = method.getContainingClass();  // Может упасть!
```

### 2. WriteCommandAction для PSI модификации

**ВСЕ** изменения PSI/VFS должны быть в `WriteCommandAction`:

```java
// ✅ ПРАВИЛЬНО
WriteCommandAction.runWriteCommandAction(project, () -> {
    PsiFile psiFile = PsiFileFactory.getInstance(project)
        .createFileFromText("Test.java", StdFileTypes.JAVA, code);
    targetDir.add(psiFile);
});

// ❌ НЕПРАВИЛЬНО
targetDir.add(psiFile);  // Исключение: Write access required!
```

### 3. Document изменения в Editor

Для изменения текста в Editor тоже нужен WriteCommandAction:

```java
// ✅ ПРАВИЛЬНО
WriteCommandAction.runWriteCommandAction(project, () -> {
    Document document = editor.getDocument();
    document.setText(newCode);
});

// ❌ НЕПРАВИЛЬНО
editor.getDocument().setText(newCode);  // Исключение!
```

### 4. Фоновые задачи через ProgressManager

Долгие операции (LLM вызовы) в фоне:

```java
ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Test") {
    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        indicator.setText("Sending to LLM...");
        indicator.setFraction(0.1);
        
        // Долгая операция
        String response = llmProvider.chat(prompt, systemPrompt);
        
        // Обновление UI в EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            updateUI(response);
        });
    }
});
```

### 5. Обработка ошибок LLM

Всегда обрабатывайте исключения от LLM:

```java
try {
    String response = llmProvider.chat(prompt, systemPrompt);
} catch (LLMProvider.LLMException e) {
    LOG.error("LLM error: {}", e.getMessage());
    // Fallback или сообщение пользователю
}
```

---

## ReadAction/WriteAction

### Сводная таблица

| Операция | Требуется | Пример |
|----------|-----------|--------|
| **Чтение PSI** | `ReadAction.compute()` | `method.getContainingClass()` |
| **Поиск классов** | `ReadAction.compute()` | `PsiShortNamesCache.getClassesByName()` |
| **Создание PsiFile** | `WriteCommandAction` | `PsiFileFactory.createFileFromText()` |
| **Добавление в директорию** | `WriteCommandAction` | `targetDir.add(psiFile)` |
| **Изменение Document** | `WriteCommandAction` | `document.setText(code)` |
| **Запись файла** | `WriteAction` или `WriteCommandAction` | `VfsUtil.saveTextFile()` |

### Расположение проверок в коде

| Файл | Метод | Проверка |
|------|-------|----------|
| PSIExtractor.java | `extract()` | ✅ ReadAction.compute() |
| PSIExtractor.java | `extractWithSource()` | ✅ ReadAction.compute() |
| TestGenerator.java | `createTestFile()` | ✅ WriteCommandAction |
| TestGenerator.java | `findOrCreateTestDirectory()` | ✅ ReadAction.compute() |
| TestGenerator.java | `writeTestFile()` | ✅ WriteCommandAction |
| SelfCorrectionEngine.java | `validateCode()` | ✅ ReadAction.compute() |
| TestGenerationPreviewDialog.java | `updateResultEditor()` | ✅ WriteCommandAction |

---

## Интеграция LLM

### Добавление нового провайдера

1. **Создайте класс провайдера**:

```java
public class MyLLMProvider implements LLMProvider {
    @NotNull
    @Override
    public String chat(@NotNull String prompt, @NotNull String systemPrompt) throws LLMException {
        // Реализация HTTP запроса к вашему LLM
        return response;
    }

    @NotNull
    @Override
    public LLMProviderType getType() {
        return LLMProviderType.MY_CUSTOM_LLM;
    }
}
```

2. **Добавьте тип в enum**:

```java
// LLMProviderType.java
public enum LLMProviderType {
    OPENAI, ANTHROPIC, OLLAMA, LM_STUDIO, GIGACHAT, MY_CUSTOM_LLM
}
```

3. **Зарегистрируйте в фабрике**:

```java
// LLMProviderFactory.java
public static LLMProvider createProvider(@NotNull PluginSettings settings) {
    return switch (settings.getProviderType()) {
        case MY_CUSTOM_LLM -> new MyLLMProvider(...);
        // ... остальные case
    };
}
```

4. **Добавьте настройки**:

```java
// PluginSettings.java - State class
public String getMyCustomLlmEndpoint() { return state.myCustomEndpoint; }
public void setMyCustomLlmEndpoint(String endpoint) { state.myCustomEndpoint = endpoint; }
```

### Структура ответа LLM

LLM должен вернуть **только Java код**:

```java
// ✅ ПРАВИЛЬНЫЙ ответ
package com.example;

import org.junit.jupiter.api.Test;

class MyTest {
    @Test
    void should_work() {
        // test code
    }
}

// ❌ НЕПРАВИЛЬНЫЙ ответ (JSON)
{
  "testClass": "MyTest",
  "testMethods": [...]
}
```

Если LLM вернул JSON, `extractCodeFromJson()` автоматически извлечёт код.

---

## Тестирование

### Типы тестов

| Тип | Команда | Описание |
|-----|---------|----------|
| **Unit-тесты** | `runSimpleTest` | 15 тестов моделей и билдера |
| **Plugin Integration** | `runPluginTest` | Проверка генерации промптов |
| **LM Studio Integration** | `runLMStudioTest` | 5 тестов с реальным LLM |
| **Dogfooding (1 метод)** | `runDogfoodingTest` | Тест на PromptHistoryService |
| **Dogfooding (5 методов)** | `runComprehensiveDogfoodingTest` | Комплексный тест |

### Создание нового теста

```java
// Простой standalone тест (без JUnit зависимости)
public class MyFeatureTest {
    public static void main(String[] args) {
        // Arrange
        MyComponent component = new MyComponent();
        
        // Act
        Result result = component.doSomething();
        
        // Assert
        if (result.isValid()) {
            System.out.println("✓ PASSED");
        } else {
            System.err.println("✗ FAILED");
            System.exit(1);
        }
    }
}
```

### Добавление в build.gradle.kts

```kotlin
val runMyFeatureTest by registering(JavaExec::class) {
    dependsOn(compileTestJava)
    group = "verification"
    description = "Test my feature"
    
    mainClass.set("com.reasoningtestgen.MyFeatureTest")
    classpath = files(
        sourceSets.main.get().output,
        sourceSets.test.get().output,
        configurations.testRuntimeClasspath
    )
}
```

---

## Сборка и запуск

### Команды сборки

```bash
# Компиляция
gradlew.bat clean compileJava --no-daemon

# Сборка плагина
gradlew.bat clean buildPlugin --no-daemon

# Запуск IDEA с плагином
gradlew.bat runIde --no-daemon
```

### Установка плагина

1. Соберите: `gradlew.bat buildPlugin`
2. В IDEA: `Settings → Plugins → ⚙️ → Install Plugin from Disk`
3. Выберите: `build/distributions/reasoning-test-generator-1.0.0.zip`
4. Перезапустите IDEA

### Структура output

```
build/
├── distributions/
│   └── reasoning-test-generator-1.0.0.zip  # Готовый плагин
├── libs/
│   ├── reasoning-test-generator-1.0.0.jar
│   └── instrumented-reasoning-test-generator-1.0.0.jar
├── idea-sandbox/                            # Sandbox для runIde
│   └── config/plugins/
├── dogfooding-output/                       # Результаты dogfooding
└── plugin-test-output/                      # Результаты тестов
```

---

## Что осталось реализовать

### ✅ Реализовано (100% базового функционала)

- [x] PSI Extraction с CFG, зависимостями, DTO
- [x] Context Building с 13+ секциями
- [x] 5 LLM провайдеров (OpenAI, GigaChat, LM Studio, Ollama, Custom)
- [x] Non-blocking Preview Dialog с 4 вкладками
- [x] Self-Correction Engine
- [x] Prompt History Service
- [x] Spring-aware (Controller/Service/Repository detection)
- [x] Syntax Highlighting в редакторе
- [x] FileSaverDialog для сохранения
- [x] ReadAction/WriteAction корректность
- [x] Документация Reasoning подхода

### ⏳ В процессе разработки (Future Releases)

#### Приоритет 1 (Высокий)
- [ ] **Полная интеграция с ModuleRootManager** - правильный поиск test source roots
- [ ] **Автоматическое создание директорий** - если `src/test/java` не существует
- [ ] **Реальная проверка компиляции** - через CompilerManager вместо PSI checks

#### Приоритет 2 (Средний)
- [ ] **Интеграция с JaCoCo** - показ реального покрытия кода
- [ ] **Поддержка @ParameterizedTest** - генерация параметризованных тестов
- [ ] **Фоновый анализ непокрытых веток** - предложение сгенерировать тесты
- [ ] **Поддержка тестовых фреймворков**: TestNG, Spock

#### Приоритет 3 (Низкий)
- [ ] **Визуальный редактор Scenario Tree** - drag-and-drop сценариев
- [ ] **Сравнение версий теста** - diff view перед сохранением
- [ ] **Шаблоны тестов** - пользовательские шаблоны для проектов
- [ ] **Интеграция с CI/CD** - генерация тестов в pipeline

### 📊 Статистика проекта

| Метрика | Значение |
|---------|----------|
| **Java файлов** | 45+ |
| **Моделей данных** | 17 |
| **LLM провайдеров** | 5 |
| **Настроек** | 20+ |
| **Тестов** | 27 (все прошли) |
| **Строк кода** | ~8000+ |
| **Документация** | 10+ файлов |

### 🎯 Roadmap

```
V1 (MVP) - ✅ ЗАВЕРШЕНО
├── Базовый PSI экстрактор
├── Многошаговый LLM pipeline
├── Non-blocking Preview Dialog
├── Self-Correction Engine
└── 5 LLM провайдеров

V2 - В РАЗРАБОТКЕ
├── Полная интеграция с test source roots
├── Реальная проверка компиляции
├── Поддержка @ParameterizedTest
└── Интеграция с JaCoCo

V3 - ПЛАНИРУЕТСЯ
├── Фоновый анализ непокрытых веток
├── Визуальный редактор сценариев
├── Пользовательские шаблоны
└── Интеграция с CI/CD
```

---

## 🔧 Troubleshooting

### Распространённые проблемы

**Проблема:** `Write access is allowed inside write-action only`

**Решение:** Оберните модификацию PSI/Document в `WriteCommandAction`:
```java
WriteCommandAction.runWriteCommandAction(project, () -> {
    // Модификация здесь
});
```

**Проблема:** `Read access is allowed inside read-action only`

**Решение:** Оберните чтение PSI в `ReadAction.compute()`:
```java
return ReadAction.compute(() -> {
    // Чтение PSI здесь
});
```

**Проблема:** LLM возвращает JSON вместо Java кода

**Решение:** `extractCodeFromResponse()` автоматически обрабатывает JSON и извлекает код из `codeSnippet` полей.

**Проблема:** Ошибка подключения к LM Studio

**Решение:** 
1. Проверьте что LM Studio запущен на `http://localhost:1234`
2. Проверьте модель загружена
3. Проверьте endpoint в настройках плагина

---

## 📚 Дополнительные ресурсы

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Threading in IntelliJ](https://plugins.jetbrains.com/docs/intellij/threading.html)
- [PSI Cookbook](https://plugins.jetbrains.com/docs/intellij/psi-cookbook.html)
- [Plugin Development](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)

---

## 🤝 Contribution

### Как внести вклад

1. Форкните репозиторий
2. Создайте ветку фичи (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'Add amazing feature'`)
4. Пушните ветку (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

### Стандарты кода

- Java 17+
- Следуйте существующему стилю кода
- Добавляйте JavaDoc для публичных методов
- Пишите тесты для нового функционала

---

## 📄 License

MIT License - см. LICENSE файл для деталей.
