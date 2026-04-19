Промпт:

Вы — Senior Test Engineer, специализирующийся на создании высококачественных unit-тестов для Java-приложений.

ВАЖНО: Вы должны вернуть ТОЛЬКО Java код тестового класса. НЕ JSON, НЕ описание, НЕ markdown.

Ваша задача:
1. Проанализировать назначение метода, контракт и поведение
2. Спроектировать комплексные тестовые сценарии, покрывающие все ветки
3. Сгенерировать готовый к использованию Java код теста

Формат вывода:
- Верните ТОЛЬКО Java код, начиная с package declaration
- НЕ включайте JSON, markdown, или текстовые описания
- НЕ включайте объяснений или комментариев о том что вы делаете
- ОБЯЗАТЕЛЬНО включите ВСЕ необходимые импорты (JUnit, Mockito, AssertJ, тестируемый класс и его зависимости)
- Начните с: package ...
- Закройте последней }: класса

ВАЖНО про импорты:
- ВСЕГДА добавляйте import org.junit.jupiter.api.Test;
- ВСЕГДА добавляйте import org.junit.jupiter.api.DisplayName;
- ВСЕГДА добавляйте import static org.assertj.core.api.Assertions.*;
- Если используете моки: import org.mockito.*; и import org.junit.jupiter.api.extension.ExtendWith;
- Если используете @ParameterizedTest: import org.junit.jupiter.params.ParameterizedTest;
- Добавьте импорты для ВСЕХ классов используемых в методе (User, Order, Service и т.д.)

Руководство:
- Всегда покрывайте основной сценарий (happy path), граничные случаи, обработку ошибок и краевые условия
- Используйте описательные имена тестовых методов по соглашению: should_{ожидание}_when_{условие}
- ОБЯЗАТЕЛЬНО добавляйте @DisplayName("описание на русском") к КАЖДОМУ тесту
- @DisplayName должен описывать ЧТО проверяет тест понятным языком
- Пишите изолированные, повторяемые тесты с правильной подготовкой и очисткой
- Включайте осмысленные тестовые данные, отражающие реальные сценарии
- Используйте соответствующие стратегии мокирования без избыточного мокирования
- Следуйте стилю и соглашениям существующих тестов проекта

Пример правильного формата:
```java
package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock MyRepository repo;
    @InjectMocks MyService service;

    @Nested
    @DisplayName("Happy Path Scenarios")
    class HappyPath {
        @Test
        @DisplayName("Возвращает результат при валидном вводе")
        void should_return_result_when_input_valid() {
            // Given
            when(repo.findById(1L)).thenReturn(Optional.of(new Entity()));

            // When
            Result result = service.doSomething(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
        }
    }

    @Nested
    @DisplayName("Error Scenarios")
    class Errors {
        @Test
        @DisplayName("Выбрасывает исключение когда сущность не найдена")
        void should_throw_exception_when_entity_not_found() {
            // Given
            when(repo.findById(99L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> service.doSomething(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("not found");
        }
    }
}
```


=== USER PROMPT ===

## Сигнатура метода
Класс: DataStorageController
Метод: getById
Возвращаемый тип: ResponseEntity<DataStorageResponse>
Параметры: id: UUID
Аннотации: org.springframework.web.bind.annotation.GetMapping

## Исходный код метода
```java
/**
     * Получение записи по идентификатору.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataStorageResponse> getById(@PathVariable UUID id) {
        DataStorageResponse resp = storageService.getById(id);
        if (Objects.isNull(resp)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resp);
    }
```

## Граф потока управления (CFG)
return at line 1719
if (Objects.isNull(resp)) at line 1679 [then: 1705]
return at line 1779


## ⚠️ ТРЕБОВАНИЕ: Покрытие всех веток
Вы ОБЯЗАНЫ создать тесты для КАЖДОЙ ветки CFG:

1. Тест: условие 'Objects.isNull(resp)' → TRUE (then branch)
2. Тест: условие 'Objects.isNull(resp)' → FALSE (else branch)

Каждый тест должен проверять ОДНУ конкретную ветку.
Используйте описательные имена: should_{result}_when_{condition}

## ⚠️ ВАЖНЫЕ ПРИМЕЧАНИЯ

### ✅ Код соответствует best practices
- Валидация входных параметров присутствует
- Обработка ошибок реализована
- Транзакции настроены корректно


## Зависимости
id: UUID [external]
storageService: DataStorageService

## Детали зависимостей

### storageService (ru.progredis.dataserver.services.DataStorageService)

Методы:
- ru.progredis.dataserver.model.DataStorageResponse save(ru.progredis.dataserver.model.DataStorageCreateRequest request)
- ru.progredis.dataserver.model.DataStorageResponse getById(UUID id)
- ru.progredis.dataserver.model.DataStorageResponse getByGroupAndName(String group, String name)
- List<DataStorageResponse> getByGroup(String group)
- void deleteById(UUID id)
- void retainOldest()

### id (UUID)

## Документация и контракт

## Метрики сложности
Цикломатическая сложность: 2
Максимальная глубина вложенности: 1
Количество веток: 1
Количество циклов: 0

## Вызываемые методы
Методы которые вызываются в теле анализируемого метода:

### ru.progredis.dataserver.services.DataStorageService.getById
- Возвращаемый тип: ru.progredis.dataserver.model.DataStorageResponse
- Параметры: UUID id
- Статический: нет
- Есть исходный код: да
- Фрагмент кода:
```java
public DataStorageResponse getById(UUID id) {
        return DataStorageMapper.mapResponse(dataManagementService.getById(TABLE, id));
    }
```
- Вызывает: getById, mapResponse

### org.springframework.http.ResponseEntity.notFound
- Возвращаемый тип: org.springframework.http.ResponseEntity.HeadersBuilder<?>
- Статический: да
- Есть исходный код: нет

### org.springframework.http.ResponseEntity.HeadersBuilder.build
- Возвращаемый тип: org.springframework.http.ResponseEntity<T>
- Статический: нет
- Есть исходный код: нет

### org.springframework.http.ResponseEntity.ok
- Возвращаемый тип: org.springframework.http.ResponseEntity<T>
- Параметры: T body
- Статический: да
- Есть исходный код: нет

## DTO/POJO структуры
Структуры данных используемые в параметрах и возвращаемом типе:

### ResponseEntity
Пакет: org.springframework.http.ResponseEntity
Аннотации: 
Поля:

### DataStorageResponse
Пакет: ru.progredis.dataserver.model.DataStorageResponse
Аннотации: lombok.Builder, lombok.NoArgsConstructor, lombok.AllArgsConstructor, lombok.Data
Поля:
  - UUID id
  - String group
  - String name
  - String type
  - Map<String,Object> data
  - OffsetDateTime retentionDate
  - Integer version
  - OffsetDateTime createDate
  - OffsetDateTime lastChangeDate
  - UUID createAuthor
  - UUID lastChangeAuthor

## Задача
Сгенерируйте ПРОИЗВОДСТВЕННО-ГОТОВЫЙ Java код unit-тестов для метода выше.

КРИТИЧЕСКИ ВАЖНО:
- Верните ТОЛЬКО Java код, БЕЗ JSON, БЕЗ markdown, БЕЗ текстовых описаний
- Начните с: package ...
- Включите все необходимые imports
- Создайте полный класс с тестовыми методами
- Каждый тест должен быть аннотирован @Test
- Используйте JUnit 5 и AssertJ для ассертов
- Закройте код последней скобкой }

НЕ возвращайте JSON! НЕ возвращайте описание тестов! ТОЛЬКО Java код!

## Специфика: Spring REST Controller
Это REST контроллер. Рекомендуется использовать:
- MockMvc для тестирования HTTP endpoints
- @WebMvcTest для slice-тестов
- MockHttpServletResponse для проверки ответов
- Тестирование status codes, headers, response body
- @MockBean для зависимостей (сервисы, репозитории)



## 🌟 Примеры лучших практик (Few-Shot Examples)

Используйте эти паттерны как основу для генерации:

**Пример 1: Параметризованный тест (Data Driven)**
```java
@ParameterizedTest(name = "Возвращает {1} для возраста {0}")
@CsvSource({
    "17, MINOR",
    "18, ADULT",
    "65, SENIOR"
})
@DisplayName("Определяет категорию пользователя по возрасту")
void shouldClassifyUserByAge(int age, String expectedCategory) {
    assertThat(categorizer.categorize(age)).isEqualTo(expectedCategory);
}
```

**Пример 2: Тестирование исключений (Modern Style)**
```java
@Test
@DisplayName("Выбрасывает IllegalArgumentException при пустом списке")
void shouldThrow_whenListIsEmpty() {
    assertThatThrownBy(() -> service.process(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("List cannot be empty");
}
```

**Пример 3: Мокирование зависимостей**
```java
@Test
@DisplayName("Сохраняет пользователя через репозиторий")
void shouldSaveUserViaRepository() {
    // Given
    User user = new User("test");
    when(repo.save(any())).thenReturn(user.withId(1L));

    // When
    User result = service.createUser(user);

    // Then
    assertThat(result.getId()).isEqualTo(1L);
    verify(repo, times(1)).save(user);
}
```


---

Step 1: Intent

## 🎯 Intent Analysis Results

### Goal
Test getById method

### Preconditions

• Method is called with valid parameters

### Postconditions

• Method returns correct result

### Side Effects
None

### Exceptions
None


---

Step 2: Scenarios

🌳 Scenario Tree
===============

Root: DataStorageController.getById
├── ✅ S1: Happy path - normal execution
│   Type: HAPPY
│   Conditions: Valid parameters
│   Expected: Method returns correct result



---

Step 3: Design

## 📋 Test Design Strategy

### Test Framework
**JUNIT5**

### Naming Convention
`should_{expected}_when_{condition}`

### Mocking Strategy
**MOCKITO_EXTEND_WITH**

### Parameterized Tests
No

### Assertion Library
**ASSERTJ**

### Recommended Structure
```java
@ExtendWith(MockitoExtension.class)
class DataStorageControllerTest {
    @Mock private Dependency dependency;
    private DataStorageController classUnderTest;

    @BeforeEach
    void setUp() { ... }

    @Test
    void should_expectedResult_when_condition() { ... }
}
```


---

Step 4: Code

package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.model.DataStorageResponse;
import com.example.service.DataStorageService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DataStorageControllerTest {

    @Mock
    private DataStorageService storageService;

    @InjectMocks
    private DataStorageController dataStorageController;

    @Test
    void should_return_correct_response_when_id_exists() {
        // Arrange
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        DataStorageResponse expectedResponse = new DataStorageResponse();
        expectedResponse.setId(id);
        expectedResponse.setName("Test Resource");

        when(storageService.findById(id)).thenReturn(expectedResponse);

        // Act
        ResponseEntity<DataStorageResponse> result = dataStorageController.getById(id);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getBody()).isEqualTo(expectedResponse);
    }
}

--- 

Step 5: Validation

## ❌ Pipeline Error

**Error:** IllegalFormatConversionException

**Message:** d != java.lang.String

**Check logs for details.**