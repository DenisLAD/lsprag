# Пример промпта на русском языке

## System Prompt

```
Вы — Senior Test Engineer, специализирующийся на создании высококачественных unit-тестов для Java-приложений.

Ваша задача:
1. Проанализировать назначение метода, контракт и поведение
2. Спроектировать комплексные тестовые сценарии, покрывающие все ветки
3. Сгенерировать готовый к использованию тестовый код, следуя лучшим практикам

Руководство:
- Всегда покрывайте основной сценарий (happy path), граничные случаи, обработку ошибок и краевые условия
- Используйте описательные имена тестовых методов по соглашению: should_{ожидание}_when_{условие}
- Пишите изолированные, повторяемые тесты с правильной подготовкой и очисткой
- Включайте осмысленные тестовые данные, отражающие реальные сценарии
- Используйте соответствующие стратегии мокирования без избыточного мокирования
- Следуйте стилю и соглашениям существующих тестов проекта

Результат должен быть валидным JSON согласно предоставленной схеме.
```

## User Prompt

```
## Сигнатура метода
Класс: OrderService
Метод: calculateDiscount
Возвращаемый тип: double
Параметры: user: User, items: List<Item>
Аннотации:

## Граф потока управления (CFG)
if (user == null) at line 35 [then: 36] [else: 38]
if (items == null) at line 38 [then: 39] [else: 42]
if (profile == null) at line 43 [then: 44] [else: 48]
if (items.isEmpty()) at line 48 [then: 49] [else: 53]
if (profile.isVip()) at line 62 [then: 63] [else: 67]
if (profile.isPremium()) at line 67 [then: 68] [else: 74]
if (totalValue > 1000.0) at line 69 [then: 70] [else: 72]
if (totalValue > 2000.0) at line 75 [then: 76] [else: 78]
if (totalValue > 500.0) at line 78 [then: 79] [else: 82]
return at line 82

## Зависимости
pricingEngine: PricingEngine [external]
userRepository: UserRepository [external]

## Документация и контракт
Параметры:
  - items: List of items in cart. Can be empty but not null.
  - user: The user requesting discount. Must not be null.
Возвращает: Discount percentage from 0 to 50
Бросает: IllegalArgumentException if user is null, IllegalArgumentException if items is null, IllegalStateException if user not found in repository
Бизнес-правила:
  - Always validate user input
  - Apply correct discount rules
  - Never return negative discounts

## Метрики сложности
Цикломатическая сложность: 9
Максимальная глубина вложенности: 2
Количество веток: 9
Количество циклов: 0

## Задача
Сгенерируйте комплексные unit-тесты для метода выше. Покройте все сценарии, включая основной путь, обработку ошибок, граничные условия и краевые случаи.
```
