# 📝 Пример сгенерированного промпта для UserService.createUser()

Этот документ показывает пример промпта который генерируется для метода `createUser()` из `UserService`.

---

## 📊 Контекст метода

### Метод
```java
@Transactional
public UserDTO createUser(UserDTO dto) {
    log.debug("Creating new user with username: {}", dto.getUsername());
    
    // Валидация входных данных
    if (dto == null) {
        throw new IllegalArgumentException("User DTO cannot be null");
    }
    
    if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
        throw new IllegalArgumentException("Username is required");
    }
    
    if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
        throw new IllegalArgumentException("Email is required");
    }
    
    // Проверка на существование
    if (userRepository.existsByUsername(dto.getUsername())) {
        throw new IllegalArgumentException("Username already exists: " + dto.getUsername());
    }
    
    if (userRepository.existsByEmail(dto.getEmail())) {
        throw new IllegalArgumentException("Email already exists: " + dto.getEmail());
    }
    
    // Создание нового пользователя
    User user = userMapper.toEntity(dto);
    user.setActive(dto.isActive());
    user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
    user.setCreatedAt(LocalDateTime.now());
    
    User savedUser = userRepository.save(user);
    log.info("Created new user with ID: {}", savedUser.getId());
    
    return userMapper.toDTO(savedUser);
}
```

---

## 🔌 Зависимости

### Поля класса (Dependency)
```
1. userRepository : UserRepository (final)
2. userMapper : UserMapper (final)
```

### Детали зависимостей (DependencyInfo)

#### 1. UserRepository (Spring Repository)

**Тип:** Interface, Spring Data JPA Repository  
**Аннотации:** `@Repository`  
**Родитель:** `JpaRepository<User, Long>`

**Методы которые вызываются:**
```java
// existsByUsername(String username) -> boolean
boolean existsByUsername(String username);

// existsByEmail(String email) -> boolean
boolean existsByEmail(String email);

// save(User user) -> User (от JpaRepository)
<S extends User> S save(S entity);
```

**Исходный код (включается в промпт):**
```java
package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByActive(boolean active);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

---

#### 2. UserMapper (Spring Component)

**Тип:** Class, Spring Component  
**Аннотации:** `@Component`

**Методы которые вызываются:**
```java
// toEntity(UserDTO dto) -> User
public User toEntity(UserDTO dto) {
    if (dto == null) return null;
    User user = new User();
    user.setId(dto.getId());
    user.setUsername(dto.getUsername());
    user.setEmail(dto.getEmail());
    user.setActive(dto.isActive());
    user.setRole(dto.getRole());
    return user;
}

// toDTO(User user) -> UserDTO
public UserDTO toDTO(User user) {
    if (user == null) return null;
    return UserDTO.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .active(user.isActive())
        .role(user.getRole())
        .build();
}
```

**Исходный код (включается в промпт):**
```java
package com.example.demo.mapper;

import com.example.demo.model.User;
import com.example.demo.model.UserDTO;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserMapper {
    public UserDTO toDTO(User user) { ... }
    public User toEntity(UserDTO dto) { ... }
    public List<UserDTO> toDTOList(List<User> users) { ... }
}
```

---

## 📦 DTO Структуры

### UserDTO

**Package:** `com.example.demo.model`  
**Аннотации:** `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`

**Поля:**
```java
// Поле: id
private Long id;
  - Type: Long
  - Annotations: none
  - Getter: Long getId()
  - Setter: void setId(Long id)

// Поле: username
private String username;
  - Type: String
  - Annotations: none
  - Getter: String getUsername()
  - Setter: void setUsername(String username)

// Поле: email
private String email;
  - Type: String
  - Annotations: none
  - Getter: String getEmail()
  - Setter: void setEmail(String email)

// Поле: active
private boolean active;
  - Type: boolean (primitive)
  - Annotations: none
  - Getter: boolean isActive()
  - Setter: void setActive(boolean active)

// Поле: role
private String role;
  - Type: String
  - Annotations: none
  - Getter: String getRole()
  - Setter: void setRole(String role)
```

**Lombok генерируемые методы:**
- `getId()`, `setId(Long)`
- `getUsername()`, `setUsername(String)`
- `getEmail()`, `setEmail(String)`
- `isActive()`, `setActive(boolean)`
- `getRole()`, `setRole(String)`
- `equals(Object)`, `hashCode()`
- `toString()`
- `builder()` (Builder pattern)

---

### User (Entity)

**Package:** `com.example.demo.model`  
**Аннотации:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Entity`, `@Table(name = "users")`

**Поля:**
```java
// Поле: id
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

// Поле: username
@Column(nullable = false, unique = true)
private String username;

// Поле: email
@Column(nullable = false, unique = true)
private String email;

// Поле: active
@Column(nullable = false)
private boolean active;

// Поле: role
@Column(nullable = false)
private String role;

// Поле: createdAt
@Column(name = "created_at")
private LocalDateTime createdAt;
```

**Lombok генерируемые методы:**
- Все getters/setters
- `equals(Object)`, `hashCode()`
- `toString()`

---

## 🔄 Трансформации данных

### Параметр: dto

**Тип:** `UserDTO`  
**Изменен:** `true` (используется для создания User)

**Шаги трансформации:**
```
1. Строка 24: dto.getUsername() → String
   Описание: Извлечение username для валидации и логирования

2. Строка 30: dto.getEmail() → String
   Описание: Извлечение email для валидации

3. Строка 39: dto.getUsername() → String
   Описание: Повторное использование для проверки existsByUsername

4. Строка 43: dto.getEmail() → String
   Описание: Повторное использование для проверки existsByEmail

5. Строка 48: userMapper.toEntity(dto) → User
   Описание: Конвертация DTO в Entity

6. Строка 49: dto.isActive() → boolean
   Описание: Извлечение active флага для установки в User

7. Строка 50: dto.getRole() → String
   Описание: Извлечение role с fallback на "USER"
```

---

## 🗺️ Control Flow Graph (CFG)

**Всего узлов:** 9

```
1. [IF] Line 17: dto == null
   - Then branch: line 18 (throw exception)
   
2. [IF] Line 21: dto.getUsername() == null || dto.getUsername().trim().isEmpty()
   - Then branch: line 22 (throw exception)
   
3. [IF] Line 26: dto.getEmail() == null || dto.getEmail().trim().isEmpty()
   - Then branch: line 27 (throw exception)
   
4. [IF] Line 32: userRepository.existsByUsername(dto.getUsername())
   - Then branch: line 33 (throw exception)
   
5. [IF] Line 37: userRepository.existsByEmail(dto.getEmail())
   - Then branch: line 38 (throw exception)
   
6. [IF] Line 50: dto.getRole() != null
   - Then branch: line 50 (use dto.getRole())
   - Else branch: line 50 (use "USER")
```

---

## ⚠️ ТРЕБОВАНИЕ: Покрытие всех веток

**Вы ОБЯЗАНЫ создать тесты для КАЖДОЙ ветки CFG:**

1. **Тест:** `dto == null` → TRUE (exception)
2. **Тест:** `dto == null` → FALSE (normal flow)

3. **Тест:** `username == null` → TRUE (exception)
4. **Тест:** `username == null` → FALSE (normal flow)
5. **Тест:** `username.isEmpty()` → TRUE (exception)
6. **Тест:** `username.isEmpty()` → FALSE (normal flow)

7. **Тест:** `email == null` → TRUE (exception)
8. **Тест:** `email == null` → FALSE (normal flow)
9. **Тест:** `email.isEmpty()` → TRUE (exception)
10. **Тест:** `email.isEmpty()` → FALSE (normal flow)

11. **Тест:** `existsByUsername == true` → TRUE (exception)
12. **Тест:** `existsByUsername == false` → FALSE (normal flow)

13. **Тест:** `existsByEmail == true` → TRUE (exception)
14. **Тест:** `existsByEmail == false` → FALSE (normal flow)

15. **Тест:** `dto.getRole() != null` → TRUE (use role from DTO)
16. **Тест:** `dto.getRole() != null` → FALSE (use default "USER")

---

## 📋 Метрики сложности

```
Цикломатическая сложность: 7
Глубина вложенности: 1 (плоская структура)
Количество веток: 6 (все IF)
Количество циклов: 0
```

---

## 🎯 Специфика класса

**Класс:** UserService  
**Аннотации:** `@Service`, `@Slf4j`, `@RequiredArgsConstructor`, `@Transactional(readOnly = true)`

**Рекомендации для тестирования:**
- Использовать `@ExtendWith(MockitoExtension.class)` для JUnit 5
- Моки для `UserRepository` и `UserMapper` через `@Mock`
- Реальный объект UserService создавать в `@BeforeEach`
- Использовать `assertThrows()` для проверки исключений
- Проверять вызовы моков через `verify()`
- Для `@Transactional` тестов использовать `@TransactionalTest` или отключать транзакции

---

## 📝 Примерная структура тестов

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private UserMapper userMapper;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void should_throwException_when_dtoIsNull() {
        // Arrange
        UserDTO dto = null;
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.createUser(dto)
        );
        assertThat(exception.getMessage()).contains("User DTO cannot be null");
    }
    
    @Test
    void should_throwException_when_usernameIsNull() {
        // Arrange
        UserDTO dto = UserDTO.builder()
            .email("test@example.com")
            .build();
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.createUser(dto)
        );
        assertThat(exception.getMessage()).contains("Username is required");
    }
    
    @Test
    void should_throwException_when_usernameExists() {
        // Arrange
        UserDTO dto = UserDTO.builder()
            .username("existing")
            .email("test@example.com")
            .build();
        when(userRepository.existsByUsername("existing")).thenReturn(true);
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> userService.createUser(dto)
        );
        assertThat(exception.getMessage()).contains("Username already exists");
        verify(userRepository).existsByUsername("existing");
        verify(userRepository, never()).existsByEmail(any());
    }
    
    @Test
    void should_createUser_when_allValid() {
        // Arrange
        UserDTO dto = UserDTO.builder()
            .username("newuser")
            .email("new@example.com")
            .active(true)
            .build();
        
        User user = new User();
        user.setId(1L);
        user.setUsername("newuser");
        
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDTO(user)).thenReturn(dto);
        
        // Act
        UserDTO result = userService.createUser(dto);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("newuser");
        verify(userMapper).toEntity(dto);
        verify(userRepository).save(user);
        verify(userMapper).toDTO(user);
    }
}
```

---

## 🔍 Что включает промпт

### System Prompt (~500 строк)
- Роль: Senior Test Engineer
- Инструкции по генерации качественных тестов
- Требования к фреймворкам (JUnit 5, Mockito, AssertJ)
- Best practices для тестирования

### User Prompt (~1000-2000 строк)
1. **Сигнатура метода** - класс, метод, параметры, аннотации
2. **Исходный код метода** - полный текст
3. **CFG** - все 6 веток с номерами строк
4. **Требование покрытия** - явный список из 16 тестов
5. **Зависимости** - userRepository, userMapper
6. **Детали зависимостей** - полные исходники UserRepository и UserMapper
7. **Spring реализации** - @Repository, @Component
8. **Lombok методы** - все getters/setters для UserDTO и User
9. **Документация** - @param, @return, @throws
10. **Метрики сложности** - cyclomatic=7, nesting=1, branches=6
11. **DTO структуры** - UserDTO и User со всеми полями
12. **Трансформации данных** - 7 шагов трансформации dto
13. **Специфика класса** - @Service рекомендации

---

## 📊 Размер промпта

| Секция | Строк | Символов |
|--------|-------|----------|
| System Prompt | ~500 | ~3000 |
| User Prompt | ~1500 | ~10000 |
| **Итого** | **~2000** | **~13000** |

---

**Этот промпт обеспечивает 100% покрытие всех веток метода!** ✅
