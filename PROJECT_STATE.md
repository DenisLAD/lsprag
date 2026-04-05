# Состояние проекта - Reasoning Test Generator

## 📅 Дата последнего обновления
5 апреля 2026 г.

---

## ✅ Завершённые версии

### V1 - MVP (Завершено)
- [x] 5-шаговый Reasoning Pipeline
- [x] PSI Extraction (CFG, DTO, зависимости)
- [x] Non-blocking Preview Dialog (4 вкладки)
- [x] Self-Correction Engine
- [x] 5 LLM провайдеров (OpenAI, GigaChat, LM Studio, Ollama, Custom)
- [x] Подсветка синтаксиса Java
- [x] Spring-aware detection (Controller/Service/Repository)

### V2 - Улучшения (Завершено)
- [x] ModuleRootManager integration
- [x] Test File Writer с правильными путями
- [x] ParameterizedTest рекомендации
- [x] Threading fixes (ReadAction/WriteCommandAction)
- [x] Auto-save & Open file после генерации
- [x] Error Highlights в редакторе

### V3 - Анализ покрытия (Завершено)
- [x] Background Coverage Analysis
- [x] Explicit branch coverage requirement в промпте
- [x] Method source code в промпте
- [x] @DisplayName requirement
- [x] Coverage panel с реальными данными

### V4 - Качество генерации (Завершено)
- [x] Few-shot примеры в промпте
- [x] Улучшенная валидация кода (PSI Analysis)
- [x] Поддержка @ParameterizedTest генерации
- [x] Улучшенная детекция missing imports

### V5 - Planning и SSL (Завершено)
- [x] Промежуточный Planning Step через LLM
- [x] TestPlanningService для структурирования тестов
- [x] Настройки SSL/JKS в диалоге настроек
- [x] File chooser для выбора JKS файла
- [x] SSL параметры передаются во все LLM клиенты
- [x] Исправлена кнопка Apply в настройках
- [x] Предустановленные параметры в настройках

---

## 📁 Структура проекта

```
src/main/java/com/reasoningtestgen/
├── action/
│   ├── GenerateTestsAction.java           # Главное действие
│   └── TestGenerationPreviewDialog.java   # Диалог предпросмотра (4 вкладки)
├── builder/
│   └── ContextBuilder.java                # Построение промпта с Few-shot
├── extractor/
│   └── PSIExtractor.java                  # PSI анализ метода
├── generator/
│   └── TestFileWriter.java                # Запись тестовых файлов
├── llm/
│   ├── LLMProvider.java                   # Интерфейс провайдера
│   ├── LLMProviderFactory.java            # Фабрика с SSL поддержкой
│   ├── OpenAIProvider.java                # OpenAI API
│   ├── GigaChatProvider.java              # GigaChat API
│   ├── LMStudioProvider.java              # LM Studio (localhost)
│   ├── OllamaProvider.java                # Ollama (local models)
│   └── ReasoningEngine.java               # 4-шаговый pipeline
├── model/
│   ├── MethodContext.java                 # Контекст метода
│   ├── TestPlan.java                      # План тестирования (V5)
│   ├── CoverageInfo.java                  # Информация о покрытии (V3)
│   └── ... (другие модели)
├── refiner/
│   └── SelfCorrectionEngine.java          # Самокоррекция ошибок
├── service/
│   ├── CoverageAnalysisService.java       # Фоновой анализ покрытия
│   └── TestPlanningService.java           # Планирование через LLM (V5)
├── settings/
│   ├── PluginSettings.java                # Настройки плагина
│   └── PluginSettingsConfigurable.java    # UI настроек с SSL
└── validator/
    └── CompilationValidator.java          # Проверка компиляции
```

---

## 🔧 Как запустить

```bash
# Сборка
gradlew.bat clean buildPlugin --no-daemon

# Запуск IDE с плагином
gradlew.bat runIde --no-daemon

# Тесты
gradlew.bat test --no-daemon
```

**Java:** 21 для сборки, 17 для компиляции
**Gradle:** 8.6
**IDEA:** 2024.1+

---

## 📊 Текущие метрики

| Метрика | Значение |
|---------|----------|
| **Java файлов** | 48+ |
| **Моделей данных** | 19 |
| **LLM провайдеров** | 5 |
| **Сервисов** | 3 |
| **Вкладок в диалоге** | 4 |
| **Настроек** | 25+ |

---

## 🚧 Что осталось сделать

### Приоритет 2 (Средний)
- [ ] Интеграция с JaCoCo для реального покрытия
- [ ] Фоновой анализ непокрытых методов
- [ ] Поддержка TestNG
- [ ] Интеграция с CI/CD

### Приоритет 3 (Низкий)
- [ ] Визуальный редактор Scenario Tree
- [ ] Diff view перед сохранением
- [ ] Пользовательские шаблоны
- [ ] Пакетная генерация тестов

---

## 🐛 Известные проблемы

- Coverage анализ использует эвристику (не реальное выполнение тестов)
- Planning Step делает дополнительный вызов LLM (увеличивает время)
- SSL работает только если JKS файл существует и доступен

---

## 📝 Последние изменения

| Дата | Версия | Изменения |
|------|--------|-----------|
| 05.04.2026 | V5 | Planning Step, SSL/JKS настройки, исправлен диалог настроек |
| 05.04.2026 | V4 | Few-shot примеры, улучшенная валидация, ParameterizedTest |
| 05.04.2026 | V3 | Background Coverage Analysis, Source code в промпте |
| 05.04.2026 | V2 | ModuleRootManager, TestFileWriter, Threading fixes |
| 05.04.2026 | V1 | MVP - Reasoning Pipeline, 5 провайдеров |

---

## 🔑 Ключевые файлы для продолжения

1. `TestPlanningService.java` - Планирование тестов
2. `CoverageAnalysisService.java` - Анализ покрытия
3. `LLMProviderFactory.java` - Создание провайдеров с SSL
4. `PluginSettingsConfigurable.java` - Диалог настроек
5. `TestGenerationPreviewDialog.java` - Главный диалог

---

## 💡 Рекомендации для продолжения

1. **Прочтите** `DEVELOPER_GUIDE.md` для понимания архитектуры
2. **Проверьте** `req2.txt` для исходных требований
3. **Запустите** `gradlew.bat runIde` для тестирования
4. **Начните с** Приоритет 2 если продолжаете разработку
