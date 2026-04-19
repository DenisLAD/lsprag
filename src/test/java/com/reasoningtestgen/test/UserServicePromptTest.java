package com.reasoningtestgen.test;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.reasoningtestgen.extractor.PSIExtractor;
import com.reasoningtestgen.builder.ContextBuilder;
import com.reasoningtestgen.model.MethodContext;
import com.reasoningtestgen.model.PromptBundle;
import com.reasoningtestgen.model.DTOInfo;
import com.reasoningtestgen.model.CalledMethodInfo;
import com.reasoningtestgen.model.DependencyInfo;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Тест для генерации и просмотра промпта для UserService
 */
public class UserServicePromptTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Генерирует полный промпт для метода createUser и сохраняет в файл
     */
    public void testGeneratePromptForCreateUser() throws IOException {
        // Конфигурируем тестовые файлы
        myFixture.configureByText("UserDTO.java", """
            package com.example.demo.model;
            
            import lombok.Data;
            import lombok.Builder;
            import lombok.AllArgsConstructor;
            import lombok.NoArgsConstructor;
            
            @Data
            @Builder
            @AllArgsConstructor
            @NoArgsConstructor
            public class UserDTO {
                private Long id;
                private String username;
                private String email;
                private boolean active;
                private String role;
            }
        """);

        myFixture.configureByText("User.java", """
            package com.example.demo.model;
            
            import lombok.Data;
            import lombok.NoArgsConstructor;
            import lombok.AllArgsConstructor;
            import javax.persistence.*;
            import java.time.LocalDateTime;
            
            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            @Entity
            @Table(name = "users")
            public class User {
                @Id
                @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;
                
                @Column(nullable = false, unique = true)
                private String username;
                
                @Column(nullable = false, unique = true)
                private String email;
                
                @Column(nullable = false)
                private boolean active;
                
                @Column(nullable = false)
                private String role;
                
                @Column(name = "created_at")
                private LocalDateTime createdAt;
            }
        """);

        myFixture.configureByText("UserRepository.java", """
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
        """);

        myFixture.configureByText("UserMapper.java", """
            package com.example.demo.mapper;
            
            import com.example.demo.model.User;
            import com.example.demo.model.UserDTO;
            import org.springframework.stereotype.Component;
            import java.util.List;
            import java.util.stream.Collectors;
            
            @Component
            public class UserMapper {
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
                
                public List<UserDTO> toDTOList(List<User> users) {
                    return users.stream()
                        .map(this::toDTO)
                        .collect(Collectors.toList());
                }
            }
        """);

        myFixture.configureByText("UserService.java", """
            package com.example.demo.service;
            
            import com.example.demo.model.User;
            import com.example.demo.model.UserDTO;
            import com.example.demo.repository.UserRepository;
            import com.example.demo.mapper.UserMapper;
            import lombok.RequiredArgsConstructor;
            import lombok.extern.slf4j.Slf4j;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            import java.time.LocalDateTime;
            
            @Slf4j
            @Service
            @RequiredArgsConstructor
            @Transactional(readOnly = true)
            public class UserService {
            
                private final UserRepository userRepository;
                private final UserMapper userMapper;
            
                /**
                 * Создать нового пользователя
                 * @param dto DTO пользователя для создания
                 * @return Созданный пользователь с ID
                 * @throws IllegalArgumentException если username или email уже существуют
                 */
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
            }
        """);

        // Получаем PSI метод createUser
        PsiFile file = myFixture.getFile();
        PsiClass serviceClass = ((PsiJavaFile) file).getClasses()[0];
        PsiMethod createUserMethod = serviceClass.findMethodsByName("createUser", false)[0];
        assertNotNull("createUser method should exist", createUserMethod);

        // Извлекаем контекст
        PSIExtractor extractor = new PSIExtractor();
        MethodContext context = ReadAction.compute(() -> extractor.extractWithSource(
            createUserMethod, 
            true,   // include source code
            2000,   // max code length
            2       // analysis depth
        ));

        // Печатаем информацию о контексте
        System.out.println("\n===== МЕТАДАННЫЕ МЕТОДА =====");
        System.out.println("Класс: " + context.className());
        System.out.println("Метод: " + context.methodName());
        System.out.println("Return type: " + context.returnType());
        System.out.println("Параметры: " + context.parameters());
        System.out.println("Аннотации: " + context.annotations());
        
        System.out.println("\n===== ЗАВИСИМОСТИ =====");
        for (var dep : context.dependencies()) {
            System.out.println("  - " + dep.name() + ": " + dep.type());
        }
        
        System.out.println("\n===== ДЕТАЛИ ЗАВИСИМОСТЕЙ (с исходным кодом) =====");
        for (var depInfo : context.dependenciesInfo()) {
            System.out.println("\n  Зависимость: " + depInfo.name());
            System.out.println("  Тип: " + depInfo.type());
            System.out.println("  Spring реализации: " + (depInfo.springImplementations() != null ? depInfo.springImplementations().size() : 0));
            System.out.println("  Lombok методы: " + (depInfo.lombokGeneratedMethods() != null ? depInfo.lombokGeneratedMethods().size() : 0));
            
            if (depInfo.sourceCode() != null && !depInfo.sourceCode().isEmpty()) {
                System.out.println("  Исходный код (первые 500 символов):");
                String codePreview = depInfo.sourceCode().length() > 500 ? 
                    depInfo.sourceCode().substring(0, 500) + "..." : depInfo.sourceCode();
                System.out.println("  ```java");
                System.out.println("  " + codePreview.replace("\n", "\n  "));
                System.out.println("  ```");
            }
        }
        
        System.out.println("\n===== DTO СТРУКТУРЫ =====");
        for (var dto : context.dtoStructures()) {
            System.out.println("\n  DTO: " + dto.className());
            System.out.println("  Package: " + dto.packageName());
            System.out.println("  Поля:");
            for (var field : dto.fields()) {
                System.out.println("    - " + field.name() + ": " + field.type());
                System.out.println("      Annotations: " + field.annotations());
                System.out.println("      Getter: " + field.getterSignature());
                System.out.println("      Setter: " + field.getterSignature());
            }
        }
        
        System.out.println("\n===== ВЫЗЫВАЕМЫЕ МЕТОДЫ =====");
        for (var method : context.calledMethods()) {
            System.out.println("  - " + method.methodName() + "() в " + method.className());
            System.out.println("    Return: " + method.returnType());
            if (method.sourceCodeSnippet() != null && !method.sourceCodeSnippet().isEmpty()) {
                String snippet = method.sourceCodeSnippet().length() > 200 ? 
                    method.sourceCodeSnippet().substring(0, 200) + "..." : method.sourceCodeSnippet();
                System.out.println("    Код: " + snippet.replace("\n", " "));
            }
        }
        
        System.out.println("\n===== ТРАНСФОРМАЦИИ ДАННЫХ =====");
        for (var transform : context.dataTransformations()) {
            System.out.println("\n  Параметр: " + transform.parameterName());
            System.out.println("  Изменен: " + transform.isModified());
            System.out.println("  Шаги:");
            for (var step : transform.transformations()) {
                System.out.println("    - Строка " + step.lineNumber() + ": " + step.operation());
                System.out.println("      Описание: " + step.description());
            }
        }
        
        System.out.println("\n===== CFG (Control Flow Graph) =====");
        System.out.println("Всего узлов: " + context.controlFlow().nodes().size());
        for (var node : context.controlFlow().nodes()) {
            System.out.println("  [" + node.type() + "] Line " + node.line() + ": " + node.condition());
        }
        
        System.out.println("\n===== МЕТРИКИ СЛОЖНОСТИ =====");
        System.out.println("Цикломатическая сложность: " + context.complexity().cyclomatic());
        System.out.println("Глубина вложенности: " + context.complexity().nestingDepth());
        System.out.println("Количество веток: " + context.complexity().branchCount());
        System.out.println("Количество циклов: " + context.complexity().loopCount());
        
        // Строим промпт
        ContextBuilder builder = new ContextBuilder();
        PromptBundle promptBundle = builder.buildPromptBundle(context);
        
        // Сохраняем полный промпт в файл
        String fullPrompt = promptBundle.systemPrompt() + "\n\n=== USER PROMPT ===\n\n" +
                           promptBundle.userPrompt() +
                           (!promptBundle.examples().isEmpty() ?
                               "\n\n=== EXAMPLES ===\n" + String.join("\n", promptBundle.examples()) : "");
        
        try (FileWriter writer = new FileWriter("test-sample/generated-prompt.txt")) {
            writer.write(fullPrompt);
        }
        
        System.out.println("\n===== ПРОМПТ СОХРАНЕН =====");
        System.out.println("Файл: test-sample/generated-prompt.txt");
        System.out.println("Длина system prompt: " + promptBundle.systemPrompt().length() + " символов");
        System.out.println("Длина user prompt: " + promptBundle.userPrompt().length() + " символов");
        System.out.println("Длина examples: " + (promptBundle.examples().isEmpty() ? 0 : 
            promptBundle.examples().stream().mapToInt(String::length).sum()) + " символов");
        
        // Печатаем первые 2000 символов промпта для предпросмотра
        System.out.println("\n===== ПРЕДПРОСМОТР ПРОМПТА (первые 2000 символов) =====");
        String preview = fullPrompt.length() > 2000 ? fullPrompt.substring(0, 2000) + "..." : fullPrompt;
        System.out.println(preview);
    }
}
