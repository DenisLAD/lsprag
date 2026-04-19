# Тестирование плагина Reasoning Test Generator

## Структура тестов

### Unit-тесты
- `ContextBuilderTest` - тестирование построения промптов
- `MethodContextTest` - тестирование моделей данных
- `PromptHistoryServiceTest` - тестирование сохранения промптов

### Интеграционные тесты
- `LMStudioProviderIntegrationTest` - тестирование с LM Studio

## Запуск тестов

### Unit-тесты (без LLM)

```bash
# Через Gradle
gradlew test

# Или напрямую через Java
java -cp build/classes/java/test:build/classes/java/main:$(gradlew dependencies --configuration testRuntimeClasspath --quiet) org.junit.platform.console.ConsoleLauncher --scan-classpath
```

### Интеграционные тесты с LM Studio

#### Подготовка

1. **Установите LM Studio**
   - Скачайте с https://lmstudio.ai
   - Установите и запустите

2. **Загрузите модель**
   - Откройте LM Studio
   - Перейдите в "Search" 
   - Найдите `qwen/qwen3.5-9b`
   - Нажмите "Download"

3. **Запустите сервер**
   - Перейдите в "Local Server"
   - Выберите модель `qwen/qwen3.5-9b`
   - Нажмите "Start Server"
   - Убедитесь что порт `1234`

4. **Проверьте доступность**
   ```bash
   curl http://localhost:1234/v1/chat/completions -H "Content-Type: application/json" -d '{
     "model": "qwen/qwen3.5-9b",
     "messages": [{"role": "user", "content": "Hello"}]
   }'
   ```

#### Запуск интеграционных тестов

```bash
# С включенными интеграционными тестами
gradlew test -Dtest.lmstudio.enabled=true

# Только интеграционные тесты
gradlew test --tests "com.reasoningtestgen.llm.LMStudioProviderIntegrationTest" -Dtest.lmstudio.enabled=true
```

## Просмотр сохранённых промптов

Плагин сохраняет все промпты для анализа:

### Расположение
```
{project-base}/prompt-history/
├── 2026-04-05_12-30-45_INTENT_ANALYSIS.json
├── 2026-04-05_12-30-50_SCENARIO_MAPPING.json
├── 2026-04-05_12-30-55_TEST_DESIGN.json
└── 2026-04-05_12-31-00_CODE_GENERATION.json
```

### Формат записи
```json
{
  "id": "a1b2c3d4",
  "timestamp": "2026-04-05T12:30:45.123",
  "step": "INTENT_ANALYSIS",
  "systemPrompt": "...",
  "userPrompt": "...",
  "llmResponse": "...",
  "model": "qwen/qwen3.5-9b",
  "responseTimeMs": 1523,
  "success": true
}
```

### Анализ промптов

Можно использовать для:
- **Анализа полноты** - проверить что промпты содержат всю необходимую информацию
- **Оптимизации** - найти дублирующиеся части
- **Отладки** - понять что отправлялось в LLM
- **Документации** - примеры промптов для разных сценариев

## Настройка LM Studio в плагине

### Через UI
1. Откройте `Settings → Tools → Reasoning Test Generator`
2. Выберите:
   - **LLM Provider**: LM_STUDIO
   - **API Endpoint**: `http://localhost:1234/v1/chat/completions`
   - **Model**: `qwen/qwen3.5-9b`
   - **Timeout**: `120` секунд
3. Нажмите "Apply"

### Через конфигурацию
```xml
<!-- В config файла плагина -->
<option name="providerType" value="LM_STUDIO" />
<option name="endpoint" value="http://localhost:1234/v1/chat/completions" />
<option name="model" value="qwen/qwen3.5-9b" />
<option name="timeout" value="120" />
<option name="savePromptHistory" value="true" />
```

## Советы по использованию LM Studio

### Рекомендуемые модели
1. **qwen/qwen3.5-9b** - отличная для генерации кода
2. **deepseek-coder-33b** - хорош для сложных методов
3. **llama-3-70b** - универсальная

### Параметры модели
- **Temperature**: 0.3 (для детерминированности)
- **Max tokens**: 4096 (для полных тестов)
- **Context window**: 8192+ (для больших методов)

### Оптимизация скорости
- Используйте GPU если доступно
- Quantized модели (Q4_K_M) быстрее
- Закройте другие приложения

## Устранение проблем

### Тесты не запускаются
```bash
# Очистите кеш
gradlew clean

# Пересоберите
gradlew buildPlugin
```

### LM Studio не отвечает
```bash
# Проверьте доступность
curl -v http://localhost:1234/v1/chat/completions

# Перезапустите сервер в LM Studio
```

### Промпты не сохраняются
- Проверьте что `savePromptHistory = true` в настройках
- Проверьте права на запись в директорию проекта
- Посмотрите логи плагина
