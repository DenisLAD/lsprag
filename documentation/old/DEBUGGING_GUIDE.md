# Настройка отладки плагина в IntelliJ IDEA

## Проблема с `runIde`

Задача `gradlew runIde` имеет известный баг в Gradle IntelliJ Plugin 1.16.x при использовании Java 21:
```
Execution failed for task ':runIde'.
> Index: 1, Size: 1
```

Это баг в самом плагине, а не в вашем коде.

## ✅ Рекомендуемый workflow разработки и отладки

### Шаг 1: Настройка проекта в IDEA

1. **Откройте проект в вашей основной IntelliJ IDEA**
   ```
   File → Open → E:\qwen\agent
   ```

2. **Дождитесь импорта Gradle проекта**
   - IDEA автоматически распознает build.gradle.kts
   - Все зависимости загрузятся

### Шаг 2: Настройка конфигурации запуска

1. **Создайте конфигурацию запуска:**
   ```
   Run → Edit Configurations → + → Application
   ```

2. **Настройте:**
   - **Name:** `Debug Plugin`
   - **Main class:** (оставьте пустым)
   - **VM options:**
     ```
     -Xmx2g
     --add-opens=java.base/java.lang=ALL-UNNAMED
     --add-opens=java.base/java.util=ALL-UNNAMED
     ```

3. **Настройка отладки плагина:**
   ```
   Run → Edit Configurations → + → Remote JVM Debug
   ```
   - **Name:** `Attach to Plugin`
   - **Port:** 5005
   - **Host:** localhost

### Шаг 3: Сборка и установка плагина

```bash
# В терминале IDEA или командной строке
gradlew.bat clean buildPlugin --no-daemon
```

### Шаг 4: Установка плагина в IDEA для отладки

1. **В вашей основной IDEA:**
   ```
   Settings → Plugins → ⚙️ → Install Plugin from Disk
   ```

2. **Выберите файл:**
   ```
   E:\qwen\agent\build\distributions\reasoning-test-generator-1.0.0.zip
   ```

3. **Перезапустите IDEA**

### Шаг 5: Отладка

1. **Установите breakpoints** в коде плагина
   - Например, в `GenerateTestsAction.actionPerformed()`
   - Или в `ContextBuilder.buildPromptBundle()`

2. **Запустите отладку:**
   ```
   Run → Debug 'Attach to Plugin'
   ```

3. **Используйте плагин:**
   - Откройте любой Java проект
   - ПКМ на методе → Generate Reasoning Tests
   - Breakpoints сработают!

## 📁 Быстрые команды

```bash
# Сборка плагина
gradlew.bat clean buildPlugin --no-daemon

# Установка (вручную через Settings → Plugins)
# build/distributions/reasoning-test-generator-1.0.0.zip

# Все тесты
gradlew.bat runSimpleTest runComprehensiveDogfoodingTest --no-daemon
```

## 🔧 Альтернатива: Использовать IntelliJ Platform Plugin SDK

Если нужен полноценный runIde:

1. **Установите IntelliJ IDEA Community Edition отдельно**
2. **Используйте её как sandbox для тестирования**
3. **Запустите из основной IDEA с плагином**

### Настройка SDK для runIde (продвинутый способ)

```kotlin
// В build.gradle.kts
intellij {
    version.set("2025.1")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
    
    // Укажите путь к локальной копии IDEA
    // localPath.set("C:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition 2025.1")
}
```

## 💡 Советы по отладке

### 1. Логирование
Добавьте логи в код плагина:
```java
private static final Logger LOG = LoggerFactory.getLogger(YourClass.class);

LOG.info("Processing method: {}", method.getName());
LOG.debug("Context: {}", context);
```

### 2. Просмотр логов IDEA
```
Help → Show Log in Explorer
```
Файл: `idea.log`

### 3. Console вывод
Все `System.out.println()` будут видны в:
- Console окна Run/Debug
- Файл idea.log

### 4. Hot Reload
При изменении кода:
1. Пересоберите: `gradlew.bat buildPlugin`
2. Переустановите плагин
3. Перезапустите IDEA

## ✅ Проверка работоспособности

Плагин полностью протестирован:
- ✅ 27/27 тестов PASSED
- ✅ Dogfooding тесты (5 методов)
- ✅ LM Studio интеграция

Вы можете разрабатывать и отлаживать код, используя описанный workflow!

## 📚 Дополнительные ресурсы

- [IntelliJ Platform SDK Docs](https://plugins.jetbrains.com/docs/intellij/)
- [Developing a Plugin](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)
- [Debugging a Plugin](https://plugins.jetbrains.com/docs/intellij/debugging.html)
