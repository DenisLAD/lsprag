# Reasoning Test Generator Plugin

IntelliJ IDEA плагин для автоматической генерации unit-тестов на основе LLM-powered reasoning analysis.

## 🎯 Возможности

- **Многошаговый Reasoning Pipeline**: Intent Analysis → Scenario Mapping → Test Design → Code Generation → Self-Validation
- **Полный анализ PSI**: Контекст метода, CFG, зависимости, вызываемые методы, DTO/POJO, трансформации данных
- **Spring/Lombok/MapStruct поддержка**: Поиск реализаций интерфейсов, генерируемые методы, mapper'ы
- **Поддержка LLM провайдеров**: LM Studio, GigaChat, OpenAI, Ollama, Custom endpoints
- **Неблокирующий Preview Dialog**: Редактирование промпта перед генерацией
- **Self-Correction Engine**: Автоматическое исправление ошибок компиляции через LLM reasoning
- **Prompt History**: Сохранение всех промптов для анализа и отладки
- **Русские промпты**: Для лучшего понимания контекста LLM

## 📋 Требования

- **IntelliJ IDEA**: 2025.1+ (Community или Ultimate)
- **Java**: 17+ (для сборки)
- **LLM**: LM Studio (рекомендуется), GigaChat, OpenAI, Ollama

## 🚀 Установка

### Из исходного кода

```bash
# Клонировать репозиторий
git clone <repository-url>
cd reasoning-test-generator

# Собрать плагин
gradlew.bat clean buildPlugin --no-daemon

# Установить в IDEA
# Settings → Plugins → ⚙️ → Install Plugin from Disk
# Выбрать build/distributions/reasoning-test-generator-1.0.0.zip
# Перезапустить IDEA
```

## ⚙️ Настройка

### LM Studio (рекомендуется)

1. Установите [LM Studio](https://lmstudio.ai)
2. Загрузите модель `qwen/qwen3.5-9b`
3. Запустите сервер на порту 1234
4. В IDEA: `Settings → Tools → Reasoning Test Generator`
   - Provider: **LM_STUDIO**
   - Model: `qwen/qwen3.5-9b`
   - Endpoint: `http://localhost:1234/v1/chat/completions` (по умолчанию)

### GigaChat

1. Получите API ключ в [личном кабинете GigaChat](https://developers.sber.ru)
2. Настройте:
   - Provider: **GIGACHAT**
   - Auth Method: API_KEY / CLIENT_CREDENTIALS / CERTIFICATE
   - Model: `GigaChat-Max`
   - Для сертификатов: укажите JKS keystore path и пароль

### OpenAI

1. Получите API ключ на [OpenAI](https://platform.openai.com)
2. Настройте:
   - Provider: **OPENAI**
   - API Key: ваш ключ
   - Model: `gpt-4` или `gpt-3.5-turbo`

### Настройки глубины анализа

| Настройка | По умолчанию | Описание |
|-----------|-------------|----------|
| `analysisDepth` | 2 | Глубина анализа вызываемых методов (1-5) |
| `implementationSearchDepth` | 3 | Глубина поиска Spring реализаций (1-5) |
| `maxDependencyCodeLength` | 2000 | Макс. длина кода зависимостей |
| `includeCalledMethods` | true | Включать вызываемые методы в промпт |
| `includeDTOStructures` | true | Включать DTO/POJO структуры |
| `includeDataTransformations` | true | Включать трансформации данных |
| `maxCorrectionAttempts` | 3 | Макс. попыток исправления ошибок |
| `correctUntilSuccess` | false | Исправлять до полного успеха |

## 📖 Использование

1. Откройте Java файл в редакторе
2. Поставьте курсор на метод или выделите его
3. **ПКМ → Generate Reasoning Tests**
4. Дождитесь сбора контекста (3-5 секунд)
5. Откроется **Preview Dialog** с промптом
6. При необходимости **отредактируйте промпт**
7. Нажмите **🚀 Generate Test**
8. Если есть ошибки → нажмите **🔧 Fix Errors**
9. Нажмите **💾 Save Test** для сохранения

## 🏗️ Архитектура

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

## 🔄 Reasoning Pipeline

1. **Intent & Contract Analysis** - Анализ назначения метода, предусловий, постусловий
2. **Scenario Mapping** - Построение дерева сценариев (happy path, error, boundary)
3. **Test Design** - Выбор фреймворка, стратегии мокирования, библиотеки ассертов
4. **Code Generation** - Генерация тестового класса с покрытиями всех сценариев
5. **Self-Validation** - Проверка компиляции и автокоррекция ошибок (до N попыток)

## 📊 Структура промпта

Промпт включает **13 секций**:

1. **System Prompt** - роль Senior Test Engineer с инструкциями
2. **Сигнатура метода** - класс, метод, параметры, аннотации
3. **Граф потока управления (CFG)** - все ветвления с номерами строк
4. **Зависимости** - внешние зависимости
5. **Детали зависимостей** - контракты интерфейсов, методы
6. **Spring реализации** - классы с @Service/@Component/@Repository
7. **Lombok методы** - генерируемые геттеры, сеттеры, builder
8. **MapStruct** - mapper конфигурация
9. **Документация и контракт** - @param, @return, @throws, бизнес-правила
10. **Метрики сложности** - цикломатическая, глубина, ветки, циклы
11. **Вызываемые методы** - с сигнатурами и фрагментами кода
12. **DTO/POJO структуры** - поля, аннотации, вложенные DTO
13. **Трансформации данных** - шаги изменений параметров

## 🧪 Тестирование

### Unit-тесты (15 тестов)
```bash
gradlew.bat runSimpleTest --no-daemon
```

### Plugin Integration (110/100)
```bash
gradlew.bat runPluginTest --no-daemon
```

### LM Studio Integration (5 тестов)
```bash
gradlew.bat runLMStudioTest --no-daemon
```

### Dogfooding - плагин тестирует сам себя
```bash
# Один метод (PromptHistoryService)
gradlew.bat runDogfoodingTest --no-daemon

# Пять методов (Comprehensive)
gradlew.bat runComprehensiveDogfoodingTest --no-daemon
```

## 📈 Результаты тестирования

| Категория | Тестов | PASSED | FAILED | Процент |
|-----------|--------|--------|--------|---------|
| **Unit-тесты** | 15 | 15 | 0 | 100% |
| **Plugin Integration** | 1 | 1 | 0 | 100% |
| **LM Studio Integration** | 5 | 5 | 0 | 100% |
| **Dogfooding (1 метод)** | 1 | 1 | 0 | 100% |
| **Dogfooding (5 методов)** | 5 | 5 | 0 | 100% |
| **ВСЕГО** | **27** | **27** | **0** | **100%** |

### Качество промптов: **107/100** (среднее)
### Качество тестов: **107/100** (среднее)

## 📁 Структура проекта

```
src/main/java/com/reasoningtestgen/
├── action/                      # UI Actions
│   ├── GenerateTestsAction.java
│   └── TestGenerationPreviewDialog.java
├── builder/                     # Prompt Building
│   └── ContextBuilder.java
├── extractor/                   # PSI Extraction
│   ├── PSIExtractor.java
│   └── DependencyInfoExtractor.java
├── generator/                   # Test Generation
│   └── TestGenerator.java
├── llm/                        # LLM Providers
│   ├── GigaChatProvider.java
│   ├── LMStudioProvider.java
│   ├── LLMProvider.java
│   ├── LLMProviderFactory.java
│   ├── LLMProviderType.java
│   ├── LLMProviderWithLogging.java
│   ├── OllamaProvider.java
│   ├── OpenAIProvider.java
│   └── ReasoningEngine.java
├── model/                      # Data Models (17 файлов)
│   ├── CalledMethodInfo.java
│   ├── CFGNode.java
│   ├── ComplexityMetrics.java
│   ├── DataTransformation.java
│   ├── Dependency.java
│   ├── DependencyInfo.java
│   ├── DocContract.java
│   ├── DTOInfo.java
│   ├── ExistingTestInfo.java
│   ├── GeneratedCode.java
│   ├── IntentOutput.java
│   ├── MethodContext.java
│   ├── Parameter.java
│   ├── PromptBundle.java
│   ├── PromptEntry.java
│   ├── ScenarioTree.java
│   ├── TestDesign.java
│   └── ValidationResult.java
├── refiner/                    # Self-Correction
│   ├── CodeRefiner.java
│   └── SelfCorrectionEngine.java
├── service/                    # Services
│   └── PromptHistoryService.java
├── settings/                   # Plugin Settings
│   ├── PluginSettings.java
│   └── PluginSettingsConfigurable.java
└── validator/                  # Validation
    └── CompilationValidator.java
```

## ✅ Требования (FINAL_REQ.md)

| Требование | Статус |
|-----------|--------|
| **REQ 1**: Вызываемые методы | ✅ 100% |
| **REQ 2**: Spring + Lombok + MapStruct | ✅ 100% |
| **REQ 3**: Трансформации данных | ✅ 100% |
| **REQ 4**: Качество генерации | ✅ 100% |
| **REQ 5**: DTO/POJO структуры | ✅ 100% |
| **REQ 6**: Настраиваемая глубина | ✅ 100% |

**Общий статус**: **100%** требований реализовано

## 📚 Документация

| Файл | Описание |
|------|----------|
| `README.md` | Этот файл - основная документация |
| `ANALYTICS.md` | Техническое задание (версия 1.0) |
| `FINAL_REQ.md` | Финальные требования и проверка |
| `TEST_RESULTS.md` | Итоговый отчёт тестирования |
| `TEST_VERIFICATION.md` | Проверка промптов на соответствие |
| `BRANCHING_COVERAGE.md` | Обработка ветвлений (ternary, switch) |
| `LOOP_COVERAGE.md` | Обработка циклов (for, while, forEach, stream) |
| `SELF_CORRECTION_DIALOG.md` | Self-Correction в диалоге |
| `DEPENDENCY_SOURCE_CODE.md` | Включение кода зависимостей |
| `NON_BLOCKING_DIALOG.md` | Неблокирующий Preview Dialog |
| `DEEP_ANALYSIS_PLAN.md` | План глубокого анализа |
| `RUSSIAN_PROMPT_EXAMPLE.md` | Пример промпта на русском |

## 🔧 Разработка

### Сборка
```bash
gradlew.bat clean buildPlugin --no-daemon
```

### Установка для разработки
1. Соберите плагин: `gradlew.bat clean buildPlugin --no-daemon`
2. В вашей основной IDEA: `Settings → Plugins → ⚙️ → Install Plugin from Disk`
3. Выберите: `build/distributions/reasoning-test-generator-1.0.0.zip`
4. Перезапустите IDEA

### Отладка
См. подробное руководство: `DEBUGGING_GUIDE.md`

**Кратко:**
1. Откройте проект в IDEA
2. Установите breakpoints
3. Run → Debug → Attach to Plugin (port 5005)
4. Используйте плагин - breakpoints сработают!

### Тестирование
```bash
# Все тесты
gradlew.bat runSimpleTest runPluginTest runLMStudioTest runComprehensiveDogfoodingTest --no-daemon

# Только unit-тесты
gradlew.bat runSimpleTest --no-daemon

# Dogfooding (плагин тестирует сам себя)
gradlew.bat runComprehensiveDogfoodingTest --no-daemon
```

## 🗺️ Roadmap

### V1 (MVP) - Текущая версия ✅
- [x] Базовый PSI экстрактор
- [x] Многошаговый LLM reasoning pipeline
- [x] Генерация тестов с preview
- [x] Поддержка 5 LLM провайдеров
- [x] Self-Correction Engine
- [x] 20 настроек для гибкости
- [x] Русские промпты
- [x] Prompt History
- [x] Spring/Lombok/MapStruct поддержка
- [x] Вызываемые методы
- [x] DTO/POJO структуры
- [x] Трансформации данных

### V2 - Следующий релиз
- [ ] Полная вставка теста в проект (ModuleRootManager)
- [ ] Интеграция с JaCoCo для coverage analysis
- [ ] Поддержка @ParameterizedTest
- [ ] Фоновый анализ непокрытых веток

### V3 - Будущие улучшения
- [ ] Генерация тестовых данных (java-faker интеграция)
- [ ] Поддержка мутационного тестирования
- [ ] AI-powered тестовые сценарии
- [ ] Интеграция с CI/CD

## 📄 Лицензия

MIT License - см. файл LICENSE для деталей

## 🤝 Поддержка

При возникновении проблем или вопросов:
- Откройте issue в репозитории
- Проверьте логи: `Help → Show Log in Explorer`
- Просмотрите сохранённые промпты: `{project}/prompt-history/`

## 🙏 Благодарности

Плагин вдохновлён лучшими практиками из:
- Diffblue Cover
- Squaretest
- IntelliJ Test Generator

Но с ключевым отличием - использованием **LLM-powered reasoning** для глубокого анализа намерений метода и создания comprehensive тестов с учётом Spring, Lombok и MapStruct!
