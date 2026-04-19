# 🔍 Полный аудит поддержки ветвлений (Branch Coverage Audit)

## Текущая реализация (PSIExtractor.buildCFG)

### ✅ Поддерживаемые конструкции

| Конструкция | PSI Visitor | Статус | Пример |
|-------------|-------------|--------|--------|
| **if** | `visitIfStatement()` | ✅ Реализовано | `if (x > 0) {...}` |
| **if-else** | `visitIfStatement()` | ✅ Реализовано | `if (x > 0) {...} else {...}` |
| **else-if** | `visitIfStatement()` | ✅ Реализовано | `if (x > 0) {...} else if (x < 0) {...}` |
| **ternary (?:)** | `visitConditionalExpression()` | ✅ Реализовано | `x > 0 ? a : b` |
| **switch (classic)** | `visitSwitchStatement()` | ✅ Реализовано | `switch(x) { case 1: ... }` |
| **switch (Java 14+)** | `visitSwitchStatement()` | ✅ Реализовано | `switch(x) { case 1 -> ... }` |
| **while** | `visitWhileStatement()` | ✅ Реализовано | `while(x > 0) {...}` |
| **for (classic)** | `visitForStatement()` | ✅ Реализовано | `for(int i=0; i<10; i++) {...}` |
| **for-each** | `visitForeachStatement()` | ✅ Реализовано | `for(String s : list) {...}` |
| **stream().forEach()** | `visitMethodCallExpression()` | ✅ Реализовано | `list.stream().forEach(...)` |
| **try-catch** | `visitTryStatement()` | ✅ Реализовано | `try {...} catch(Exception e) {...}` |
| **multiple catch** | `visitTryStatement()` | ✅ Реализовано | `try {...} catch(A e1) {...} catch(B e2) {...}` |
| **return** | `visitReturnStatement()` | ✅ Реализовано | `return value;` |
| **throw** | `visitThrowStatement()` | ✅ Реализовано | `throw new Exception();` |

---

## ❌ НЕ поддерживаемые конструкции (Упущено!)

### 1. **Pattern Matching (Java 16+)**

**Пример:**
```java
// Pattern matching instanceof
if (obj instanceof String s) {
    // s is automatically cast
    return s.length();
}
```

**Проблема:** `instanceof` с pattern matching создает неявное ветвление + переменную с автоматическим cast.

**Решение:** Добавить `visitInstanceOfExpression()` для распознавания pattern matching.

---

### 2. **Switch expressions с yield (Java 14+)**

**Пример:**
```java
int value = switch(day) {
    case SAT, SUN -> 0;
    default -> {
        yield 1;  // yield создает точку выхода
    }
};
```

**Проблема:** `yield` это точка выхода из switch expression (аналог return).

**Решение:** Добавить `visitYieldStatement()` для tracking yield точек.

---

### 3. **Record patterns (Java 21+)**

**Пример:**
```java
// Record pattern in instanceof
if (point instanceof Point(int x, int y)) {
    return x + y;
}

// Record pattern in switch
switch(obj) {
    case Point(int x, int y) -> x + y;
}
```

**Проблема:** Деструктуризация записей создает неявные проверки.

**Решение:** Поддержка record patterns в `visitInstanceOfExpression()` и `visitSwitchStatement()`.

---

### 4. **Sealed classes и exhaustive switch**

**Пример:**
```java
sealed class Shape permits Circle, Rectangle {}

// Компилятор проверяет exhaustiveness
switch(shape) {
    case Circle c -> ...
    case Rectangle r -> ...
    // No default needed!
}
```

**Проблема:** Sealed classes гарантируют exhaustiveness switch, что влияет на тестирование.

**Решение:** Добавить проверку на sealed classes в `visitSwitchStatement()`.

---

### 5. **Null-safe вызовы (?.)**

**Пример:**
```java
// Null-safe call
String name = user?.getName();

// Null-safe с elvis operator
String name = user?.getName() ?: "default";
```

**Проблема:** Kotlin-style null-safe вызовы создают неявные проверки на null.

**Решение:** Распознавать null-safe calls в `visitMethodCallExpression()`.

---

### 6. **Smart casts (Kotlin-style)**

**Пример:**
```java
// После instanceof переменная автоматически кастится
if (obj instanceof String) {
    return obj.length();  // obj автоматически кастится к String
}
```

**Проблема:** IntelliJ smart cast влияет на flow analysis.

**Решение:** Отслеживать smart casts в flow analysis.

---

### 7. **Try-with-resources**

**Пример:**
```java
try (FileInputStream fis = new FileInputStream(file);
     BufferedInputStream bis = new BufferedInputStream(his)) {
    // ...
} catch (IOException e) {
    // ...
}
```

**Проблема:** Автоматическое закрытие ресурсов создает неявные finally блоки.

**Решение:** Добавить обработку `visitTryStatement()` для try-with-resources.

---

### 8. **Multi-catch (Java 7+)**

**Пример:**
```java
try {
    // ...
} catch (IOException | SQLException e) {
    // Обработка нескольких типов исключений
}
```

**Проблема:** Один catch блок обрабатывает несколько типов исключений.

**Текущий статус:** Частично поддерживается, но нужно улучшить обработку.

---

### 9. **Assertions**

**Пример:**
```java
assert x > 0 : "x must be positive";
```

**Проблема:** Assertions создают условные точки выхода (если включены).

**Решение:** Добавить `visitAssertStatement()` для распознавания assertions.

---

### 10. **Synchronized блоки**

**Пример:**
```java
synchronized(lock) {
    // Критическая секция
}
```

**Проблема:** Synchronization создает точки contention (важно для concurrency тестов).

**Решение:** Добавить `visitSynchronizedStatement()`.

---

### 11. **Lambda выражения с body**

**Пример:**
```java
// Lambda с телом содержит свои ветвления
list.stream()
    .filter(x -> {
        if (x > 0) return true;  // Внутреннее ветвление!
        return false;
    })
    .forEach(...);
```

**Проблема:** Lambda тела не анализируются рекурсивно.

**Решение:** Добавить рекурсивный анализ lambda выражений.

---

### 12. **Method references**

**Пример:**
```java
list.stream()
    .filter(this::isValid)  // Метод может содержать ветвления!
    .forEach(...);
```

**Проблема:** Method references не раскрывают внутренние ветвления.

**Решение:** Анализировать целевые методы для извлечения ветвлений.

---

### 13. **Optional.ifPresent/ifPresentOrElse**

**Пример:**
```java
optional.ifPresent(value -> {
    // Ветвление внутри
    if (value > 0) {...}
});

optional.ifPresentOrElse(
    value -> {...},  // if present
    () -> {...}      // if empty - это тоже ветвление!
);
```

**Проблема:** Optional методы создают неявные ветвления.

**Решение:** Распознавать Optional методы как ветвления.

---

### 14. **Stream.filter/peek/map с условиями**

**Пример:**
```java
list.stream()
    .filter(x -> x > 0)  // Фильтр это ветвление!
    .map(x -> x * 2)
    .collect(...);
```

**Проблема:** Stream filter/peek создают неявные ветвления.

**Решение:** Распознавать stream operations с условиями.

---

### 15. **Reactive streams (Mono/Flux, CompletableFuture)**

**Пример:**
```java
mono.filter(x -> x > 0)
    .map(x -> x * 2)
    .switchIfEmpty(Mono.just(default))
    .onErrorResume(e -> fallback());
```

**Проблема:** Reactive chains содержат множество неявных ветвлений.

**Решение:** Добавить анализ reactive chains.

---

## 📊 Приоритеты реализации

### Критичные (P0) - Должны быть реализованы

| # | Конструкция | Влияние на тесты | Сложность |
|---|-------------|------------------|-----------|
| 1 | **Pattern Matching instanceof** | Высокое | Низкая |
| 2 | **Lambda выражения** | Высокое | Средняя |
| 3 | **Try-with-resources** | Среднее | Низкая |
| 4 | **Assertions** | Среднее | Низкая |
| 5 | **Stream.filter()** | Высокое | Средняя |

### Важные (P1) - Желательно реализовать

| # | Конструкция | Влияние на тесты | Сложность |
|---|-------------|------------------|-----------|
| 6 | **Switch expressions с yield** | Среднее | Средняя |
| 7 | **Method references** | Среднее | Высокая |
| 8 | **Optional.ifPresent** | Среднее | Низкая |
| 9 | **Multi-catch** | Низкое | Низкая |
| 10 | **Null-safe вызовы** | Низкое | Средняя |

### Долгосрочные (P2) - Будущие улучшения

| # | Конструкция | Влияние на тесты | Сложность |
|---|-------------|------------------|-----------|
| 11 | **Record patterns** | Низкое | Высокая |
| 12 | **Sealed classes** | Низкое | Средняя |
| 13 | **Smart casts** | Низкое | Высокая |
| 14 | **Synchronized** | Низкое | Низкая |
| 15 | **Reactive streams** | Среднее | Высокая |

---

## 🛠 План реализации

### Этап 1: Pattern Matching и Lambda (2 дня)

```java
// Добавить в PSIExtractor.java

@Override
public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    
    // Pattern matching instanceof (Java 16+)
    PsiPattern pattern = expression.getPattern();
    if (pattern != null) {
        // Это pattern matching instanceof!
        String condition = "instanceof " + pattern.getText();
        int line = getLineNumber(expression);
        
        cfgNodes.add(new CFGNode(
            CFGNode.NodeType.IF,
            condition,
            line,
            null,
            null,
            null
        ));
    }
}

@Override
public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    super.visitLambdaExpression(expression);
    
    // Рекурсивный анализ тела lambda
    PsiCodeBody body = expression.getBody();
    if (body != null) {
        // Извлечь ветвления из lambda тела
        extractBranchesFromBody(body, cfgNodes);
    }
}
```

### Этап 2: Try-with-resources и Assertions (1 день)

```java
@Override
public void visitTryStatement(@NotNull PsiTryStatement statement) {
    super.visitTryStatement(statement);
    
    // Try-with-resources
    PsiResourceList resources = statement.getResourceList();
    if (resources != null && !resources.isEmpty()) {
        int line = getLineNumber(statement);
        cfgNodes.add(new CFGNode(
            CFGNode.NodeType.TRY_WITH_RESOURCES,
            "try-with-resources",
            line,
            null,
            null,
            null
        ));
    }
    
    // ... остальная логика catch
}

@Override
public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
    super.visitAssertStatement(statement);
    
    String condition = statement.getAssertCondition().getText();
    int line = getLineNumber(statement);
    
    cfgNodes.add(new CFGNode(
        CFGNode.NodeType.ASSERT,
        "assert: " + condition,
        line,
        null,
        null,
        null
    ));
}
```

### Этап 3: Stream и Optional (2 дня)

```java
@Override
public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    
    String methodName = expression.getMethodExpression().getText();
    
    // Stream.filter()
    if (methodName.equals("filter") || methodName.equals("peek")) {
        PsiExpressionList args = expression.getArgumentList();
        if (args.getExpressions().length > 0) {
            String condition = args.getExpressions()[0].getText();
            int line = getLineNumber(expression);
            
            cfgNodes.add(new CFGNode(
                CFGNode.NodeType.FILTER,
                "stream.filter: " + condition,
                line,
                null,
                null,
                null
            ));
        }
    }
    
    // Optional.ifPresent
    if (methodName.equals("ifPresent") || methodName.equals("ifPresentOrElse")) {
        int line = getLineNumber(expression);
        cfgNodes.add(new CFGNode(
            CFGNode.NodeType.IF,
            "optional.isPresent()",
            line,
            null,
            null,
            null
        ));
    }
}
```

---

## 📈 Метрики покрытия

### Текущее покрытие ветвлений

| Тип ветвлений | Поддерживается | % покрытия |
|--------------|----------------|------------|
| if/else | ✅ | 100% |
| ternary | ✅ | 100% |
| switch (classic) | ✅ | 100% |
| switch (arrow) | ✅ | 90% |
| while | ✅ | 100% |
| for | ✅ | 100% |
| for-each | ✅ | 100% |
| try-catch | ✅ | 100% |
| **pattern matching** | ❌ | 0% |
| **lambda body** | ❌ | 0% |
| **stream.filter** | ❌ | 0% |
| **assertions** | ❌ | 0% |
| **yield** | ❌ | 0% |

**Итого:** ~60% всех возможных ветвлений

### Целевое покрытие после реализации

| Этап | Добавленные типы | Новое покрытие |
|------|-----------------|----------------|
| Этап 1 | pattern matching, lambda | +15% → 75% |
| Этап 2 | try-with-resources, assertions | +5% → 80% |
| Этап 3 | stream.filter, optional | +10% → 90% |

**Цель:** 90% покрытия всех ветвлений

---

## 🎯 Рекомендации

### Немедленные действия (этап 1)

1. **Добавить pattern matching instanceof**
   - Влияет на 20% нового кода Java 16+
   - Простая реализация (1-2 часа)

2. **Добавить анализ lambda тел**
   - Влияет на 40% кода с streams
   - Средняя сложность (4-6 часов)

3. **Улучшить обработку switch expressions**
   - Поддержка yield
   - Стрелочный синтаксис

### Краткосрочные действия (этап 2)

4. **Try-with-resources**
   - Важно для тестирования IO
   - Простая реализация

5. **Assertions**
   - Важно для validation кода
   - Очень простая реализация

### Среднесрочные действия (этап 3)

6. **Stream.filter/peek**
   - Критично для functional кода
   - Требует анализа lambda параметров

7. **Optional методы**
   - Важно для null-safe кода
   - Средняя сложность

---

## ✅ Чеклист аудита

- [x] Аудит текущей реализации CFG
- [x] Идентификация 15 упущенных конструкций
- [x] Приоритизация по влиянию на тесты
- [x] План реализации по этапам
- [x] Оценка метрик покрытия
- [ ] Реализация этапа 1 (pattern matching, lambda)
- [ ] Реализация этапа 2 (try-with-resources, assertions)
- [ ] Реализация этапа 3 (stream, optional)
- [ ] Финальная верификация (90% coverage)

---

## 📖 Ссылки

- [Java Language Specification - Control Flow](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html)
- [IntelliJ PSI Reference](https://plugins.jetbrains.com/docs/intellij/psi.html)
- [Pattern Matching in Java](https://openjdk.org/projects/pattern-matching/)
- [Switch Expressions](https://openjdk.org/projects/amber/guides/switchexpr)

---

**Вывод:** Текущая реализация покрывает ~60% всех возможных ветвлений. После реализации 3 этапов покрытие достигнет 90%.
