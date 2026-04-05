# Dependency Source Code Inclusion Feature

## Обзор

Новая функция плагина позволяет включать исходный код зависимостей и их сигнатуры методов в промпты для LLM. Это позволяет генерировать более точные и релевантные тесты.

## Настройки

### Расположение
`Settings → Tools → Reasoning Test Generator → Dependency Source Code Inclusion`

### Параметры

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| **Include dependency source code** | Включать полный исходный код зависимостей в промпт | `false` |
| **Include method signatures** | Включать сигнатуры методов зависимостей | `true` |
| **Max code length** | Максимальная длина исходного кода (символов) | `2000` |

## Как это работает

### Без включения исходного кода (по умолчанию)
```
## Dependencies
pricingEngine: PricingEngine [external]
userRepository: UserRepository [external]
```

### С включенными method signatures
```
## Dependencies Details

### pricingEngine (PricingEngine)

Methods:
- double calculateTax(double amount)
  Calculates tax for the given amount

### userRepository (UserRepository)

Methods:
- UserProfile findById(Long userId)
  Finds user profile by ID
- List<UserProfile> findAll()
  Returns all user profiles
```

### С включенным исходным кодом
```
### pricingEngine (PricingEngine)

Interface Contract:
```java
interface PricingEngine {
  double calculateTax(double amount);
}
```

Source Code:
```java
public interface PricingEngine {
    /**
     * Calculates tax for the given amount
     * @param amount The amount to calculate tax for
     * @return Tax amount
     */
    double calculateTax(double amount);
}
```
```

## Преимущества

### 1. Более точные моки
LLM видит реальные сигнатуры методов и создает правильные mock объекты:
```java
// Вместо
when(pricingEngine.calculate(any())).thenReturn(0.0);

// Генерирует
when(pricingEngine.calculateTax(1000.0)).thenReturn(130.0);
```

### 2. Правильные исключения
Зная какие методы выбрасывают, LLM создает тесты на исключения:
```java
assertThrows(IllegalArgumentException.class, () -> {
    userRepository.findById(null);
});
```

### 3. Релевантные тестовые данные
Понимая контракты методов, LLM подбирает осмысленные данные:
```java
// Зная что calculateTax принимает double и возвращает double
User user = new User(1L, "Test");
double tax = pricingEngine.calculateTax(1000.0);
assertThat(tax).isPositive();
```

## Пример промпта

### Полный промпт с исходным кодом зависимостей
```
## Method Signature
Class: OrderService
Method: calculateDiscount
Return Type: double
Parameters: user: User, items: List<Item>

## Dependencies
pricingEngine: PricingEngine [external]
userRepository: UserRepository [external]

## Dependencies Details

### userRepository (UserRepository)

Interface Contract:
```java
interface UserRepository {
  UserProfile findById(Long userId);
}
```

Methods:
- UserProfile findById(Long userId) throws DataAccessException
  Finds user profile by ID. Returns null if not found.

Source Code:
```java
public interface UserRepository {
    /**
     * Finds user profile by ID
     * @param userId The user ID
     * @return User profile or null if not found
     * @throws DataAccessException if database error occurs
     */
    UserProfile findById(Long userId) throws DataAccessException;
}
```

## Task
Generate comprehensive unit tests for the method above...
```

## Использование

### Быстрое включение
```bash
# Через UI
Settings → Tools → Reasoning Test Generator
☑ Include dependency source code in prompts
☑ Include method signatures
Max code length: 2000
```

### Рекомендуемые настройки

| Сценарий | Source Code | Signatures | Max Length |
|----------|-------------|------------|------------|
| Быстрая генерация | ❌ | ✅ | 1000 |
| Стандартная | ❌ | ✅ | 2000 |
| Детальная | ✅ | ✅ | 3000 |
| Полная | ✅ | ✅ | 5000 |

## Влияние на размер промпта

| Настройка | Размер промпта | Время генерации |
|-----------|----------------|-----------------|
| По умолчанию | ~2KB | 10-20с |
| Signatures only | ~4KB | 15-30с |
| Full source (2000) | ~8KB | 20-40с |
| Full source (5000) | ~15KB | 30-60с |

## Ограничения

1. **Размер промпта**: Большие исходники увеличивают время генерации
2. **Лимит символов**: Код обрезается до `maxDependencyCodeLength`
3. **Доступность**: Исходный код должен быть в проекте
4. **Производительность**: Поиск классов занимает дополнительное время

## Технические детали

### PSI Extractor
```java
List<DependencyInfo> deps = extractor.extractDependenciesWithSource(
    method, 
    containingClass, 
    includeSourceCode, 
    maxCodeLength
);
```

### DependencyInfo модель
```java
record DependencyInfo(
    String name,
    String type,
    boolean isExternal,
    boolean nullable,
    String sourceCode,
    List<MethodInfo> methods,
    String interfaceContract
)
```

### MethodInfo модель
```java
record MethodInfo(
    String name,
    String returnType,
    List<String> parameters,
    List<String> exceptions,
    String javadoc
)
```

## Результат

Благодаря включению исходного кода зависимостей, плагин генерирует:
- ✅ **Более точные моки** - правильные сигнатуры методов
- ✅ **Релевантные исключения** - знание throws clause
- ✅ **Осмысленные данные** - понимание контрактов
- ✅ **Лучшее покрытие** - знание всех методов зависимостей

## Сборка

```bash
gradlew.bat clean buildPlugin --no-daemon
```

Файл плагина: `build/distributions/reasoning-test-generator-1.0.0.zip`
