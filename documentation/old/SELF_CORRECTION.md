# Self-Correction Параметры

## Настройки количества попыток исправления

### Параметры командной строки

```bash
# По умолчанию (3 попытки)
gradlew.bat runSelfCorrectionTest --no-daemon

# Указать конкретное количество попыток
gradlew.bat runSelfCorrectionTest -Dselfcorrection.maxAttempts=5 --no-daemon

# Исправлять до полного успеха (игнорирует лимит)
gradlew.bat runSelfCorrectionTest -Dselfcorrection.untilSuccess=true --no-daemon
```

### Параметры в настройках плагина

В `Settings → Tools → Reasoning Test Generator`:

- **Max Correction Attempts**: Количество попыток (по умолчанию: 3)
- **Correct Until Success**: Исправлять до полного успеха (по умолчанию: false)

## Режимы работы

### Режим 1: Фиксированное количество попыток

```
selfcorrection.maxAttempts = 5
selfcorrection.untilSuccess = false
```

**Поведение**:
- Попытается исправить ошибки до 5 раз
- Остановится после 5 попыток даже если остались ошибки
- Вернёт лучший результат

### Режим 2: До победного

```
selfcorrection.untilSuccess = true
```

**Поведение**:
- Будет пытаться исправлять ошибки бесконечно
- Остановится только когда все ошибки исправлены
- **Внимание**: Может зациклиться если LLM не может исправить ошибку

### Режим 3: Комбинированный (рекомендуется)

```
selfcorrection.maxAttempts = 10
selfcorrection.untilSuccess = true
```

**Поведение**:
- Будет пытаться до успеха НО не более 10 раз
- Защита от зацикливания

## Примеры использования

### Быстрая проверка (default)
```bash
gradlew.bat runSelfCorrectionTest --no-daemon
# Результат: 3 попытки, ~30-60 секунд
```

### Тщательная проверка
```bash
gradlew.bat runSelfCorrectionTest -Dselfcorrection.maxAttempts=10 --no-daemon
# Результат: до 10 попыток, ~2-5 минут
```

### Полная коррекция
```bash
gradlew.bat runSelfCorrectionTest -Dselfcorrection.untilSuccess=true --no-daemon
# Результат: пока не исправит все ошибки
```

## API для программного использования

```java
// Через настройки плагина
PluginSettings settings = PluginSettings.getInstance();
settings.setMaxCorrectionAttempts(5);
settings.setCorrectUntilSuccess(true);

// Или напрямую в коде
SelfCorrectionEngine engine = new SelfCorrectionEngine(
    llmProvider,
    validator,
    promptHistoryService,
    5,  // maxAttempts
    true  // untilSuccess
);

CorrectionResult result = engine.correctCode(generatedCode, testDesign);
```

## Рекомендации

| Сценарий | maxAttempts | untilSuccess | Время |
|----------|-------------|--------------|-------|
| Быстрая проверка | 3 | false | ~30с |
| Стандартная | 5 | false | ~1мин |
| Тщательная | 10 | false | ~2мин |
| До победного | 10 | true | ~2-5мин |
| Без лимита | 999 | true | ∞ |

## Метрики качества

После каждого запуска тест показывает:

```
========================================
Self-Correction Test Results
========================================
Original errors:  4
Fixed errors:     1
Remaining:        3
Improvement:      75.0%
Attempts used:    1
========================================
✓ Partial success - 75.0% errors fixed
```

### Интерпретация результатов

- **100%** - Идеально! Все ошибки исправлены
- **75-99%** - Хорошо, но остались мелкие ошибки
- **50-74%** - Средне, нужны ещё попытки
- **<50%** - Плохо, LLM не понимает ошибки
