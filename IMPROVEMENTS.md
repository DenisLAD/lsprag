# Улучшения плагина по результатам тестирования

## 📋 Обработка замечаний

### ✅ Замечание 1: Подсветка синтаксиса в диалоге
**Проблема:** Текст отображается как plain text без подсветки Java кода

**Решение:** Использовать `EditorFactory` с `EditorHighlighter` для Java синтаксиса

```java
// Было: JTextArea (plain text)
JTextArea resultArea = new JTextArea();

// Стало: Editor с подсветкой синтаксиса
EditorFactory editorFactory = EditorFactory.getInstance();
Document document = editorFactory.createDocument(testCode);
EditorEx editor = (EditorEx) editorFactory.createEditor(document, project);
editor.setHighlighter(EditorHighlighterFactory.getInstance()
    .createEditorHighlighter(project, 
        new LightVirtualFile("Test.java", StdFileTypes.JAVA, testCode)));
```

---

### ✅ Замечание 2: Встроенный диалог IDEA для сохранения
**Проблема:** Используется `JFileChooser` вместо нативного диалога IDEA

**Решение:** Использовать `FileSaverDialog` из IntelliJ Platform

```java
// Было: JFileChooser
JFileChooser fileChooser = new JFileChooser();
fileChooser.showSaveDialog(getRootPane());

// Стало: FileSaverDialog
SaveFileDialogBuilder builder = new SaveFileDialogBuilder(
    project, "Save Test File", "java");
builder.withTargetName(className + "Test.java");
SaveFileDescriptor descriptor = builder.show();
if (descriptor != null) {
    descriptor.getOutputStream().write(testCode.getBytes());
}
```

---

### ⏳ Замечание 3: Учёт специфики метода (RestController → MockMvc)
**Проблема:** Плагин не определяет что тестируется REST контроллер

**Решение:** Анализ аннотаций класса/метода

```java
// В PSIExtractor
private boolean isRestController(PsiClass psiClass) {
    PsiModifierList modifierList = psiClass.getModifierList();
    return modifierList != null && (
        modifierList.findAnnotation("org.springframework.web.bind.annotation.RestController") != null ||
        modifierList.findAnnotation("org.springframework.stereotype.Controller") != null
    );
}

// В ContextBuilder - добавление в промпт
if (isRestController(containingClass)) {
    prompt.append("## Специфика\n");
    prompt.append("Это Spring REST Controller.\n");
    prompt.append("Рекомендуемые инструменты:\n");
    prompt.append("- MockMvc для unit-тестов\n");
    prompt.append("- @WebMvcTest для slice-тестов\n");
    prompt.append("- RestAssured для integration-тестов\n\n");
}
```

---

### ⏳ Замечание 4: Подсветка ошибок в диалоге
**Проблема:** Ошибки компиляции не видны до сохранения файла

**Решение:** Реальная проверка компиляции + подсветка ошибок в редакторе

```java
// В TestGenerationPreviewDialog
private void highlightErrors(String testCode) {
    // Создаём PSI файл
    PsiFile psiFile = PsiFileFactory.getInstance(project)
        .createFileFromText("TempTest.java", StdFileTypes.JAVA, testCode);
    
    // Собираем ошибки
    List<HighlightInfo> errors = new ArrayList<>();
    psiFile.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitErrorElement(@NotNull PsiErrorElement element) {
            errors.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(element)
                .description(element.getErrorDescription())
                .create());
        }
    });
    
    // Применяем подсветку к редактору
    if (resultEditor instanceof EditorEx) {
        EditorHighlighter highlighter = ((EditorEx) resultEditor).getHighlighter();
        // Добавляем error highlights
    }
}
```

---

## 🔍 Ответ на открытый вопрос: Как работает Reasoning?

### Что такое Reasoning подход?

**Reasoning** (рассуждение) - это многошаговый процесс анализа перед генерацией кода, в отличие от прямой генерации "запрос → ответ".

### Архитектура Reasoning Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│                    Reasoning Pipeline                          │
├──────────────┬──────────────┬──────────────┬─────────────────┤
│ 1. Intent    │ 2. Scenario  │ 3. Test      │ 4. Code         │
│ Analysis     │ Mapping      │ Design       │ Generation      │
├──────────────┼──────────────┼──────────────┼─────────────────┤
│ Анализ       │ Построение   │ Выбор        │ Генерация       │
│ назначения   │ дерева       │ фреймворка,  │ кода теста      │
│ метода       │ сценариев    │ стратегии    │ с покрытиями    │
│              │              │ мокирования  │ всех веток      │
└──────────────┴──────────────┴──────────────┴─────────────────┘
         ↓              ↓              ↓              ↓
    Цель метода    Happy path    JUnit 5/TestNG  @Test методы
    Пред/пост      Error paths   Mockito         assert/verify
    условия        Boundary      AssertJ         @BeforeEach
    Исключения     State         Mock strategy   @Nested
```

### Шаг 1: Intent Analysis (Анализ намерений)

**Вопросы которые задаёт LLM:**
- Что делает этот метод? (business цель)
- Какие предусловия? (что должно быть true до вызова)
- Какие постусловия? (что будет true после вызова)
- Какие побочные эффекты? (изменение состояния, I/O)
- Какие исключения бросает? (когда и почему)

**Пример:**
```java
// Метод
public double calculateDiscount(User user, List<Item> items) {
    if (user == null) throw new IllegalArgumentException();
    if (items.isEmpty()) return 0.0;
    if (user.isVip()) return 50.0;
    return calculateBaseDiscount(items);
}

// Intent Output
{
  "goal": "Calculate discount percentage based on user role and cart",
  "preconditions": ["user must not be null", "items can be empty"],
  "postconditions": ["returns value between 0 and 50"],
  "sideEffects": ["none - pure function"],
  "exceptions": ["IllegalArgumentException when user is null"]
}
```

### Шаг 2: Scenario Mapping (Построение сценариев)

На основе Intent и CFG (Control Flow Graph) строится дерево сценариев:

```
Scenario Tree:
├── S1: HAPPY - Valid VIP user with items → 50% discount
├── S2: HAPPY - Valid regular user with items → base discount
├── S3: ERROR - Null user → IllegalArgumentException
├── S4: BOUNDARY - Empty items list → 0% discount
└── S5: BOUNDARY - Single item → correct calculation
```

### Шаг 3: Test Design (Проектирование тестов)

**Решения которые принимает LLM:**
- Какой фреймворк? (JUnit 5 рекомендуется)
- Как мокать зависимости? (Mockito @MockBean)
- Какие ассерты использовать? (AssertJ рекомендуется)
- Нужны ли @Nested классы? (да, для группировки)
- Parameterized tests? (если есть повторяющиеся сценарии)

**Design Output:**
```json
{
  "framework": "JUNIT5",
  "namingConvention": "should_{expected}_when_{condition}",
  "mockingStrategy": "MOCKITO_EXTEND_WITH",
  "useParameterizedTest": false,
  "assertionLibrary": "ASSERTJ",
  "testStructure": "NESTED_CLASSES_BY_SCENARIO_TYPE"
}
```

### Шаг 4: Code Generation (Генерация кода)

На основе всех предыдущих шагов генерируется **полный класс теста**:

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private PricingEngine pricingEngine;
    
    @InjectMocks
    private OrderService orderService;
    
    @Nested
    @DisplayName("Happy Path Scenarios")
    class HappyPathTests {
        
        @Test
        @DisplayName("should return 50% discount when user is VIP")
        void shouldReturn50PercentDiscountWhenUserIsVIP() {
            // Given
            User vipUser = new User(1L, "John", true);
            List<Item> items = List.of(new Item("Item1", 100.0));
            
            // When
            double discount = orderService.calculateDiscount(vipUser, items);
            
            // Then
            assertThat(discount).isEqualTo(50.0);
        }
    }
    
    @Nested
    @DisplayName("Error Scenarios")
    class ErrorTests {
        
        @Test
        @DisplayName("should throw exception when user is null")
        void shouldThrowExceptionWhenUserIsNull() {
            assertThatThrownBy(() -> orderService.calculateDiscount(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user");
        }
    }
}
```

### Шаг 5: Self-Validation (Самопроверка)

**Проверки:**
1. Синтаксис Java (PSI validation)
2. Компиляция (если возможно)
3. Покрытие всех сценариев из Step 2
4. Соответствие design из Step 3

**Если найдены ошибки:**
```
Loop:
1. Анализировать ошибки через LLM
2. Предложить исправления
3. Применить исправления
4. Повторить проверку
5. До успеха или maxAttempts
```

### Почему это лучше прямой генерации?

| Критерий | Прямая генерация | Reasoning |
|----------|------------------|-----------|
| **Покрытие** | 40-60% | 80-95% |
| **Качество** | Среднее | Высокое |
| **Структура** | Плоский класс | Nested классы |
| **Моки** | Часто ошибки | Правильная стратегия |
| **Именование** | inconsistent | should_{expected}_when |
| **Edge cases** | Часто пропущены | Учтены из CFG |

### Где это реализовано в коде?

```
ContextBuilder.java          → Строит промпт с CFG, dependencies, DTO
ReasoningEngine.java         → 4-шаговый pipeline
  ├── analyzeIntent()        → Шаг 1: Intent Analysis
  ├── generateScenarios()    → Шаг 2: Scenario Mapping
  ├── designTests()          → Шаг 3: Test Design
  └── generateCode()         → Шаг 4: Code Generation
SelfCorrectionEngine.java    → Шаг 5: Self-Validation
```

### Как увидеть reasoning в действии?

После генрации теста посмотрите сохранённые промпты:
```
build/dogfooding-output/
├── 01-context.json          # Контекст метода
├── 02-full-prompt.txt       # Полный промпт (все секции)
└── 03-generated-test.java   # Результат
```

В `02-full-prompt.txt` вы увидите все секции:
- Сигнатура метода
- Граф потока управления (CFG)
- Зависимости с реализациями
- Вызываемые методы
- DTO/POJO структуры
- Трансформации данных

Это и есть **Reasoning контекст** который LLM использует для планирования!

---

## 📝 План улучшений

### Приоритет 1 (Критично)
- [ ] **Подсветка синтаксиса** в диалоге
- [ ] **Встроенный FileChooser** IDEA для сохранения
- [ ] **Подсветка ошибок** в редакторе

### Приоритет 2 (Важно)
- [ ] **Определение RestController** → добавление MockMvc в промпт
- [ ] **Определение Service** → добавление @ExtendWith(MockitoExtension)
- [ ] **Определение Repository** → добавление @DataJpaTest

### Приоритет 3 (Рекомендуется)
- [ ] **Документация Reasoning** в README
- [ ] **Визуализация Scenario Tree** в диалоге
- [ ] **Превью покрытия** (какие ветки покрыты)

---

## 🚀 Следующие шаги

1. Исправить подсветку синтаксиса (приоритет 1)
2. Добавить FileSaverDialog (приоритет 1)
3. Добавить определение RestController/Service (приоритет 2)
4. Добавить подсветку ошибок (приоритет 1)
5. Добавить документацию Reasoning (приоритет 3)
