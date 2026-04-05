## Техническое задание на разработку плагина IntelliJ IDEA  
**«Генерация модульных тестов на Java на основе рассуждений (Reasoning‑First Generation)»**  

Версия 1.0  

---

### 1. Введение и цели

**Назначение** – плагин для IntelliJ IDEA, автоматически генерирующий модульные тесты (JUnit 5 / TestNG) для Java‑методов. Ключевое отличие от существующих решений (Diffblue, Squaretest, автоматические шаблоны):  
- Явное моделирование поведения метода перед генерацией кода (анализ потока управления, контрактов, сценариев).  
- Использование LLM (как внешних, так и локальных) с многошаговым пайплайном рассуждений (CoT / ToT).  
- Адаптация под стиль и фреймворки конкретного проекта (обучение на существующих тестах).  

**Цели**  
1. Снизить ручную работу по написанию тестов для сложных методов (ветвления, исключения, состояния).  
2. Обеспечить покрытие не только happy path, но и edge cases, error paths, контрактных ограничений.  
3. Интегрироваться в стандартный процесс разработки IDEA (контекстное меню, фоновый анализ, preview, автоформатирование).  
4. Поддерживать итеративную валидацию – тест должен компилироваться и (опционально) запускаться без ошибок.  

---

### 2. Функциональные требования

| ID | Требование |
|----|-------------|
| FR1 | Плагин должен предоставлять действие (action) в контекстном меню на `PsiMethod` или в редакторе Java‑файла: «Generate Unit Tests (Reasoning)». |
| FR2 | Для выбранного метода плагин извлекает расширенный контекст через PSI (сигнатура, тело, зависимости, комментарии, существующие тесты). |
| FR3 | На основе контекста плагин строит карту сценариев: happy path, граничные условия, обработка исключений, переходы состояний. |
| FR4 | Генерация тестов выполняется в несколько шагов: анализ намерений → проектирование сценариев → выбор фреймворка и стиля → генерация кода → самопроверка и уточнение. |
| FR5 | Сгенерированный тест (класс/методы) вставляется в соответствующий пакет `src/test/java` с правильным именем (`*Test.java`), импортами, аннотациями. |
| FR6 | Плагин должен автоматически определять стек тестирования проекта: JUnit 4/5, TestNG, Mockito, AssertJ, Hamcrest, а также базовые классы (например, `BaseIntegrationTest`). |
| FR7 | Должна быть возможность ручного просмотра и редактирования сгенерированного теста перед вставкой (preview). |
| FR8 | После вставки плагин выполняет проверку компиляции (через IDEA `CompilerManager`) и при ошибках предлагает автокоррекцию или показывает diff. |
| FR9 | Плагин должен работать асинхронно с отображением прогресса (`ProgressIndicator`), не блокируя UI. |
| FR10 | При наличии существующих тестов для того же класса плагин должен учитывать их стиль, naming convention, используемые вспомогательные методы (few‑shot примеры). |

---

### 3. Нефункциональные требования

| ID | Требование | Значение |
|----|-------------|-----------|
| NFR1 | Производительность: анализ метода средней сложности (цикломатическая сложность ≤ 15) не более 5 секунд (без учёта LLM). Запрос к LLM – асинхронно с таймаутом 30 с. | |
| NFR2 | Надёжность: при сбоях LLM (timeout, ошибка API) плагин должен генерировать базовый happy‑path тест без рассуждений. | |
| NFR3 | Ресурсы: кэширование результатов PSI‑анализа на сессию (сериализация в lightweight структуры). | |
| NFR4 | Безопасность: при использовании внешних LLM (OpenAI, Anthropic) данные запросов не должны логироваться на клиенте; пользователь должен дать согласие на отправку кода. Локальные модели (Ollama) предпочтительны. | |
| NFR5 | Расширяемость: архитектура должна позволять подключать новые анализаторы (мутационные, coverage gap) и альтернативные LLM‑провайдеры. | |
| NFR6 | Совместимость: поддержка IDEA 2023.3+ (Community и Ultimate), Java 11-21, Gradle/Maven проектов. | |

---

### 4. Архитектура системы

#### 4.1. Компонентная диаграмма (высокоуровневая)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            IDEA Plugin Host                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────┐   ┌───────────────┐  │
│  │ UI Action    │→│ PSI Extractor│→│ Context    │→│ LLM Reasoning │  │
│  │ (AnAction)   │   │ (Facade)     │   │ Builder    │   │ Engine        │  │
│  └──────────────┘   └──────────────┘   └─────────────┘   └───────┬───────┘  │
│                                                                    │          │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────┐   ┌───────▼───────┐  │
│  │ Test         │←│ Validator/   │←│ Test      │←│ Plan/Scenario │  │
│  │ Writer       │   │ Refiner      │   │ Generator    │   │ Mapper        │  │
│  └──────────────┘   └──────────────┘   └─────────────┘   └───────────────┘  │
│         │                  │                                                 │
│         └────────┬─────────┘                                                 │
│                  ▼                                                           │
│          ┌───────────────┐                                                   │
│          │ IDEA          │ (CompilerManager, PsiFileFactory, CodeStyle)     │
│          │ Integration   │                                                   │
│          └───────────────┘                                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 4.2. Поток данных (Data Flow)

1. **Выбор метода** → передача `PsiMethod` в `PSI Extractor`.  
2. **Извлечение контекста** → структура `MethodContext` (JSON).  
3. **Построение контекста для LLM** → обогащение примерами тестов, CFG, зависимостями.  
4. **LLM Reasoning Engine** (4 подшага):  
   - *Intent & Contract Analysis* → `IntentOutput` (бизнес‑цель, пред/постусловия, побочные эффекты).  
   - *Scenario Mapping* → дерево сценариев (`ScenarioTree`).  
   - *Test Design* → `TestDesign` (фреймворк, стиль, моки, данные).  
   - *Code Generation* → `GeneratedCode` (Java‑код тестового класса).  
5. **Self-Validation** → LLM проверяет код на соответствие дизайну и стилю → при ошибках рекурсивный фикс (до 3 итераций).  
6. **Постобработка** → форматирование, проверка компиляции, вставка или показ preview.  

Все обмены – через типизированные DTO (Java records / JSON).

---

### 5. Детальное описание компонентов

#### 5.1. PSI Extractor

**Вход**: `PsiMethod`, `PsiClass` (владелец), проект.  
**Выход**: `MethodContext` (каноническая модель).  

| Поля `MethodContext` | Описание |
|----------------------|-----------|
| `signature` | имя, возвращаемый тип, параметры (имя+тип), модификаторы, аннотации |
| `controlFlowGraph` | упрощённое представление узлов (if/else, try/catch, switch, loop, return). Формат: список блоков с номерами строк и типами ветвлений. |
| `dependencies` | список полей класса, параметров, локальных переменных, ссылающихся на внешние сервисы. Каждый элемент: `{ name, type, isExternal, nullable }` |
| `docContract` | извлечённые из JavaDoc теги `@param`, `@return`, `@throws`, а также бизнес‑правила из описания (NLP‑минимум: regex на "must", "always", "never"). |
| `existingTests` | для того же класса – найденные тестовые методы (сигнатуры, стиль, импорты, используемые assertions). |
| `complexityMetrics` | цикломатическая сложность, глубина вложенности, количество веток, наличие циклов. |

**Алгоритм**:
- Использовать `PsiTreeUtil` для обхода AST.
- Для CFG – адаптировать `PsiControlFlowUtil` + собственный visitor для ветвлений.
- Зависимости: `PsiReferenceSearch` + проверка на `instanceof PsiField`/`PsiParameter` + аннотации `@Inject`, `@Autowired`, `@Mock`.

#### 5.2. Context Builder

**Назначение**: превратить `MethodContext` в промпт для LLM, добавив few‑shot примеры из проекта.  

**Выход**: структура `PromptBundle` (системный промпт, пользовательский промпт, примеры).  

**Системный промпт** – фиксированный, описывающий роль "Senior Test Engineer", требуемый формат вывода (JSON схема для каждого шага).  
**Пользовательский промпт** включает:  
- сигнатуру, CFG в текстовом виде (например, `if line 12-15 else 16-18`),  
- зависимости,  
- контракты из документации,  
- примеры тестов из `existingTests` (не более 3).  

**Кэширование**: контекст для одного метода переиспользуется за сессию, если PSI не изменился.

#### 5.3. LLM Reasoning Engine

**Абстракция** – позволяет подключать разных провайдеров (OpenAI, Anthropic, Ollama, vLLM).  
**Формат вызова**: `ReasoningStep` (enum: INTENT, SCENARIOS, DESIGN, CODE, VALIDATE).  

Каждый шаг имеет свою JSON‑схему валидации. При невалидном ответе – повтор с уточнением.  

**Шаг 1 – Intent & Contract**  
Запрос: "Опиши бизнес‑цель, предусловия, постусловия, побочные эффекты, возможные исключения".  
Ответ – `IntentOutput` (поля: `goal`, `preconditions`, `postconditions`, `sideEffects`, `exceptions`).

**Шаг 2 – Scenario Mapping**  
Запрос: на основе CFG и IntentOutput построить дерево сценариев.  
Ответ – `ScenarioTree` (корневой сценарий, дети: happy, error, boundary, state). Каждый сценарий: `description`, `inputConditions`, `expectedOutcome`, `shouldThrow`.  

**Шаг 3 – Test Design**  
Запрос: выбрать фреймворк, стиль именования, стратегию мокирования, нужны ли параметризованные тесты, `@Nested`.  
Ответ – `TestDesign` (поля: `framework`, `namingConvention`, `mockingStrategy`, `useParameterized`, `assertionLibrary`).  

**Шаг 4 – Code Generation**  
Запрос: сгенерировать код тестового класса, следуя дизайну и сценариям.  
Ответ – `GeneratedCode` (Java‑текст, список импортов, карта «имя метода → scenarioId»).  

**Шаг 5 – Self‑Validation**  
Запрос: проверить код на соответствие дизайну, типичным ошибкам (пустые assert, неправильные моки).  
Ответ – `ValidationResult` (список проблем, исправленный код). При наличии проблем – повтор шага 4 с комментариями.  

**Технические детали**:
- Таймаут на запрос – 30 секунд, повтор при 5xx ошибках (до 2 раз).
- Для локальных моделей (Ollama) – поддержка стриминга, fallback на шаблонный генератор при низкой уверенности.

#### 5.4. Test Generator & Validator/Refiner

**Test Generator** – превращает `GeneratedCode` в объект `PsiFile`. Использует `PsiFileFactory` для создания Java‑файла.  
**Validator** – через `CompilerManager` компилирует только сгенерированный тест (в изоляции). Собирает диагностики. При ошибках компиляции формирует `CompilationErrorReport`.  
**Refiner** – повторно вызывает LLM (шаг Validate) с ошибками компиляции, получает исправленный код. Цикл до 3 итераций.

#### 5.5. IDEA Integration

- **Действие** – `AnAction`, доступное в `EditorPopupMenu`, `ProjectViewPopupMenu` на Java‑файле или методе.  
- **Прогресс** – `Task.Backgroundable` с индикацией шагов («Анализ метода…», «Рассуждение LLM…», «Генерация теста…»).  
- **Preview** – `DialogWrapper` с редактором кода (lightweight) и кнопками «Вставить», «Отмена», «Повторить генерацию».  
- **Вставка** – определение целевого пакета `src/test/java` по `ProjectStructure`, создание недостающих директорий, запись файла.  
- **Форматирование** – `CodeStyleManager.reformat()`.  
- **Запуск тестов (опционально)** – через `ExecutionManager` запустить только что созданный тест и показать результат в tool window.

---

### 6. Модели данных (ключевые структуры)

#### `MethodContext` (JSON)
```json
{
  "className": "OrderService",
  "methodName": "calculateDiscount",
  "returnType": "double",
  "parameters": [{"name": "user", "type": "User", "nullable": true}, {"name": "items", "type": "List<Item>"}],
  "annotations": ["@Transactional(readOnly=true)"],
  "controlFlow": {
    "nodes": [
      {"type": "if", "condition": "user == null", "line": 10, "then": 11, "else": 14},
      {"type": "try", "line": 20, "catch": {"type": "IllegalArgumentException", "line": 25}}
    ]
  },
  "dependencies": [{"name": "pricingEngine", "type": "PricingEngine", "isExternal": true}],
  "docContract": {"throws": ["IllegalArgumentException if user null"], "return": "discount percentage 0-50"},
  "existingTests": [{"name": "shouldApplyFullDiscountForVIP", "assertions": ["assertEquals"]}],
  "complexity": {"cyclomatic": 4, "nestingDepth": 2}
}
```

#### `ScenarioTree`
```json
{
  "root": {"id": "S0", "description": "All scenarios"},
  "children": [
    {"id": "S1", "type": "HAPPY", "input": "valid user, non-empty items", "expected": "discount calculated"},
    {"id": "S2", "type": "ERROR", "input": "user == null", "expected": "throw IllegalArgumentException"},
    {"id": "S3", "type": "BOUNDARY", "input": "empty items list", "expected": "discount = 0"}
  ]
}
```

#### `TestDesign`
```json
{
  "framework": "JUNIT5",
  "namingConvention": "should_{expected}_when_{condition}",
  "mockingStrategy": "MOCKITO_EXTEND_WITH",
  "useParameterizedTest": true,
  "assertionLibrary": "ASSERTJ"
}
```

---

### 7. Интеграция с IDEA API (конкретные вызовы)

| Задача | API / Класс | Примечание |
|--------|-------------|-------------|
| Получить текущий редактор и `PsiMethod` | `Editor`, `PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiMethod.class)` | |
| Создать тестовый файл | `PsiFileFactory.getInstance(project).createFileFromText("OrderServiceTest.java", JavaFileType.INSTANCE, code)` | |
| Найти тестовую корневую директорию | `ProjectStructure.getTestSourceRoots()` | Использовать `ModuleRootManager` |
| Проверить компиляцию | `CompilerManager.getInstance(project).compile(psiFiles, callback)` | Асинхронно |
| Запустить тест | `ExecutionManager.getInstance(project).runConfiguration(testConfiguration, executor)` | |
| Показать прогресс | `ProgressManager.getInstance().run(task)` | С поддержкой отмены |

---

### 8. Процессы и алгоритмы (ключевые)

#### 8.1. Алгоритм построения CFG из PSI (псевдокод)
```
function buildCFG(method: PsiMethod):
    cfg = empty list
    visitor = new PsiRecursiveElementVisitor() {
        visitIfStatement(ifStmt):
            cfg.add(node(type=IF, condition=ifStmt.getCondition().getText(), 
                         thenLine=ifStmt.getThenBranch().getTextRange().getStartLine(),
                         elseLine=ifStmt.getElseBranch()?.getTextRange()?.getStartLine()))
        visitTryStatement(tryStmt):
            for each catch:
                cfg.add(node(type=CATCH, exceptionType=catch.getCatchType().getText(), line=...))
    }
    method.accept(visitor)
    return compressAndIndex(cfg)  # удалить дубликаты, добавить номера строк
```

#### 8.2. Алгоритм сценарного дерева через LLM (ToT)
```
function generateScenarios(context, intent):
    prompt = buildScenarioPrompt(context, intent)
    response = llm.chat(prompt, schema=SCENARIO_TREE_JSON)
    tree = parseAndValidate(response)
    # Дополнительно: если сложность > 10, запросить расширение граничных сценариев
    if context.complexity.cyclomatic > 10:
        expandPrompt = "Добавь сценарии для всех уникальных веток CFG: " + context.controlFlow
        tree = llm.chat(expandPrompt, baseTree=tree)
    return tree
```

#### 8.3. Цикл самокоррекции
```
function generateWithSelfCorrection(design, maxAttempts=3):
    code = llm.generateCode(design)
    for attempt in 1..maxAttempts:
        validation = llm.validate(code, design)
        if validation.isValid:
            return code
        else:
            code = llm.refine(code, validation.errors)
    return code  # fallback последняя версия
```

---

### 9. Пользовательский интерфейс

**Основное действие**:
- ПКМ по имени метода (в редакторе или структуре проекта) → «Generate Reasoning Tests».
- Открывается диалог с опциями (рис.1):
  - [ ] Включить самопроверку компиляцией
  - [ ] Показать preview перед вставкой
  - Выбор LLM провайдера (локальный/OpenAI/отключить – только шаблон)
  - Кнопка «Generate»

**Progress Tool Window**:
- Отображает текущий этап: «Извлечение контекста…», «Анализ намерений…», «Генерация…».
- Возможность отмены (Cancel).

**Preview диалог**:
- Разделённый на две панели: слева – сгенерированный код (редактируемый), справа – краткое описание сценариев.
- Кнопка «Insert» – создаёт файл в правильном пакете.
- Кнопка «Regenerate» – перезапускает генерацию с текущими настройками.

**Настройки плагина (Settings → Tools → Reasoning Test Generator)**:
- LLM API ключ, endpoint, timeout.
- Максимальное количество сценариев (по умолчанию 10).
- Всегда использовать предпросмотр (да/нет).
- Автоформатирование после вставки.

---

### 10. Этапы реализации (Roadmap)

#### **MVP (2 недели)**  
- [ ] Базовый PSI экстрактор (сигнатура, зависимости, существующие тесты).  
- [ ] Прямой промпт к LLM (один шаг) – генерация одного happy‑path теста.  
- [ ] Вставка с форматированием.  
- [ ] Действие в контекстном меню.  

#### **V2 (3 недели)**  
- [ ] Построение CFG из PSI.  
- [ ] Многошаговый LLM пайплайн (Intent, Scenarios, Design, Code) с JSON схемами.  
- [ ] Structured output и валидация.  
- [ ] Проверка компиляции через `CompilerManager`.  

#### **V3 (4 недели)**  
- [ ] Самокоррекция (self‑validation цикл).  
- [ ] Автодетект тестового стека проекта (JUnit, Mockito, базовые классы).  
- [ ] Preview диалог с редактированием.  
- [ ] Поддержка локальных LLM (Ollama).  

#### **V4 (2 недели + итерации)**  
- [ ] Интеграция с coverage tools (JaCoCo) – подсветка непокрытых веток.  
- [ ] Генерация тестовых данных (integration с javafaker).  
- [ ] Фоновый анализ и предложение «пробелов в покрытии».  
- [ ] Поддержка `@ParameterizedTest` и `@CsvSource`.  

---

### 11. Риски и меры митигации

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|------------|
| LLM галлюцинирует несуществующие методы/классы | Средняя | Высокое | Жёсткая валидация импортов, проверка компиляции, fallback на шаблонный генератор |
| Большое время ответа LLM ( >30с) | Средняя | Среднее | Асинхронный вызов с возможностью отмены, кэширование одинаковых контекстов |
| Неоднозначность в стиле проекта (нет тестов) | Высокая | Низкое | Использование стандартного стиля по умолчанию (JUnit 5 + AssertJ) |
| Изменение PSI во время анализа (пользователь правит код) | Низкая | Среднее | Захват снапшота PSI (copy PsiMethod через `copyableUserData`), проверка актуальности перед вставкой |
| Конфиденциальность кода при использовании облачных LLM | Средняя | Критическое | Предупреждение при первом запуске, поддержка локальных моделей, опция анонимизации (замена имён) |

---

### 12. Критерии приемки

1. **Функциональные**:  
   - Для метода с цикломатической сложностью ≤ 10 плагин генерирует тесты, покрывающие не менее 80% ветвей (проверка через JaCoCo).  
   - Сгенерированный тест компилируется без ошибок (при условии корректного проекта).  
   - Тест проходит успешно для корректной реализации метода (happy path).  
   - Для метода с документацией (`@throws`) генерируется тест на исключение.  

2. **UX**:  
   - Время от клика до появления preview не превышает 15 секунд (для среднего метода).  
   - Возможность отмены генерации работает корректно.  

3. **Интеграционные**:  
   - Плагин устанавливается через `Plugins` и не конфликтует с другими.  
   - Настройки сохраняются между перезапусками IDE.  

4. **Надёжность**:  
   - При отсутствии сети или ошибке LLM плагин генерирует базовый тест (без LLM) и показывает уведомление.  
   - Плагин корректно обрабатывает методы с `lambda`, анонимными классами, generics.  

---

### Заключение

Данный документ является основой для реализации плагина. Каждый компонент специфицирован на уровне, достаточном для параллельной разработки. Приоритет – MVP с последующим итеративным наращиванием сложности (V2–V4). Архитектура обеспечивает заменяемость LLM и возможность добавления новых анализаторов (мутации, покрытие). Разработка рекомендуется на Kotlin (для удобной работы с PSI) с использованием `coroutines` для асинхронности.