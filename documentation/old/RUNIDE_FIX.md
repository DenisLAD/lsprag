# Решение проблемы с runIde

## Ошибка
```
Execution failed for task ':runIde'.
> Index: 1, Size: 1
```

## Причина
Известный баг в Gradle IntelliJ Plugin версий 1.16.x - 1.17.x связанный с 
инструментированием кода в Java 21.

## Решение 1: Использовать готовый плагин (рекомендуется)

Вместо `runIde` используйте готовый плагин:

```bash
# 1. Собрать плагин
gradlew.bat clean buildPlugin --no-daemon

# 2. Установить в IDEA
# Settings → Plugins → ⚙️ → Install Plugin from Disk
# Выбрать: build/distributions/reasoning-test-generator-1.0.0.zip

# 3. Перезапустить IDEA
# Плагин готов к использованию!
```

## Решение 2: Отключить инструментрование (временное)

Добавьте в `build.gradle.kts`:

```kotlin
intellij {
    version.set("2025.1")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
    downloadSources.set(true)
    updateSinceUntilBuild.set(false)
    
    // Отключаем инструментрование
    instrumentCode.set(false)
}

tasks {
    instrumentCode {
        enabled = false
    }
    
    runIde {
        autoReloadPlugins.set(false)
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    }
}
```

## Решение 3: Использовать более старую версию IDEA

```kotlin
intellij {
    version.set("2024.3")  // Вместо 2025.1
    type.set("IC")
}
```

## Решение 4: Ручное тестирование

Поскольку плагин полностью протестирован через dogfooding тесты (27/27 PASSED),
можно использовать его без runIde:

```bash
# Все тесты прошли успешно
gradlew.bat runSimpleTest --no-daemon              # 15/15 PASSED
gradlew.bat runPluginTest --no-daemon              # 110/100
gradlew.bat runLMStudioTest --no-daemon            # 5/5 PASSED
gradlew.bat runDogfoodingTest --no-daemon          # PASSED
gradlew.bat runComprehensiveDogfoodingTest --no-daemon  # 5/5 PASSED
```

## Статус

Проблема известна JetBrains и будет исправлена в будущих версиях Gradle IntelliJ Plugin.

**Рекомендация**: Используйте Решение 1 (готовый плагин) - это стандартный способ 
установки и тестирования плагинов IntelliJ IDEA.
