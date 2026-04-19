# Deep Analysis Implementation Plan

## REQ 1: Called Methods Deep Analysis

### Что нужно:
- Пройтись по телу метода
- Найти все вызовы методов
- Включить их в промпт с сигнатурами

### Реализация:
```java
// В PSIExtractor.extract()
List<CalledMethodInfo> calledMethods = extractCalledMethods(
    method, 
    settings.getAnalysisDepth()
);
```

## REQ 2: Spring Interface Implementations

### Что нужно:
- Для каждого интерфейса в зависимостях
- Найти все классы-реализации
- Исключить java.* пакеты
- Исключить классы без исходников
- Настраиваемая глубина поиска

### Реализация:
```java
// В PSIExtractor.extractDependenciesWithSource()
for (DependencyInfo dep : dependencies) {
    if (dep.type().isInterface()) {
        List<String> implementations = findImplementations(
            dep.type(),
            settings.getImplementationSearchDepth(),
            project
        );
        // Add implementations to context
    }
}
```

## REQ 3: Data Transformation Tracking

### Что нужно:
- Отслеживать как изменяются параметры
- Фиксировать трансформации
- Включить в промпт

### Реализация:
```java
// В PSIExtractor
List<DataTransformation> transformations = extractDataTransformations(method);
```

## REQ 4: Improved Test Generation Prompts

### Что нужно:
- Более детальные инструкции для LLM
- Акцент на quality
- Примеры хороших тестов

### Обновлённый промпт:
```
Вы — Senior Test Engineer. Ваша задача — создать ПРОИЗВОДСТВЕННО-ГОТОВЫЕ тесты.

КРИТИЧЕСКИ ВАЖНО:
1. Каждый тест должен быть ИЗОЛИРОВАННЫМ
2. Используйте GIVEN-WHEN-THEN структуру
3. Тесты должны быть ЧИТАЕМЫМИ и ПОДДЕРЖИВАЕМЫМИ
4. Мокируйте только внешние зависимости
5. Используйте реалистичные тестовые данные
```

## REQ 5: POJO/DTO Structure Inclusion

### Что нужно:
- Найти все DTO/POJO используемые методом
- Включить их структуру в промпт
- Обрабатывать вложенные DTO

### Реализация:
```java
List<DTOInfo> dtos = extractDTOStructures(method, settings.getAnalysisDepth());
```

## REQ 6: Configurable Depth Settings

### Добавлены настройки:
- `analysisDepth` (1-5) - глубина анализа вызываемых методов
- `implementationSearchDepth` (1-5) - глубина поиска реализаций
- `includeCalledMethods` (boolean) - включать ли вызываемые методы
- `includeDTOStructures` (boolean) - включать ли DTO
- `includeDataTransformations` (boolean) - включать ли трансформации данных

## Формат промпта (обновлённый)

```
## Сигнатура метода
...

## Вызываемые методы
### method1() - ClassA
  - Сигнатура: String process(User user)
  - Статус: есть исходный код
  
### method2() - InterfaceB
  - Реализации:
    - ClassB1.process()
    - ClassB2.process()

## DTO/POJO структуры
### User
  - Long id
  - String name
  - boolean vip
  
### Item  
  - String name
  - double price

## Трансформации данных
- user → userProfile (через userRepository.findById)
- items → totalValue (через stream().mapToDouble().sum())

## Граф потока управления
...

## Задача
Сгенерируйте ПРОИЗВОДСТВЕННО-ГОТОВЫЕ unit-тесты...
```

## Следующие шаги

1. ✅ Модели данных созданы
2. ✅ Настройки добавлены
3. ⏳ Реализовать PSIExtractor методы
4. ⏳ Обновить ContextBuilder
5. ⏳ Добавить UI для настроек
6. ⏳ Тестирование

## Сборка

```bash
gradlew.bat clean buildPlugin --no-daemon
```

Статус: BUILD SUCCESSFUL ✅
