package ru.sbrf.uddk.ai.testing.lsprag.extraction;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.sbrf.uddk.ai.testing.lsprag.analysis.DTOAnalyzer;
import ru.sbrf.uddk.ai.testing.lsprag.context.MethodContext;
import ru.sbrf.uddk.ai.testing.lsprag.utils.PsiUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DTOContextExtractor {

    private final DTOAnalyzer dtoAnalyzer;
    private final Set<String> processedTypes = new HashSet<>();
    private final Map<String, MethodContext.DTOInfo> uniqueDTOs = new HashMap<>(); // Хранилище уникальных DTO

    // Типы коллекций для разворачивания
    private static final Set<String> COLLECTION_TYPES = Set.of(
            "java.util.Collection",
            "java.util.List",
            "java.util.Set",
            "java.util.ArrayList",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.TreeSet",
            "java.util.Stream"
    );

    // Типы обёрток для разворачивания
    private static final Set<String> WRAPPER_TYPES = Set.of(
            "java.util.Optional",
            "org.springframework.http.ResponseEntity",
            "reactor.core.publisher.Mono",
            "reactor.core.publisher.Flux"
    );

    private static final Set<String> SKIP_CLASSES = Set.of(
            "string",
            "byte",
            "short",
            "boolean",
            "integer",
            "long",
            "biginteger",
            "bigdecimal",
            "float",
            "double",
            "localdate",
            "localdatetime",
            "offsetdate",
            "offsetdatetime",
            "uuid"
    );

    public DTOContextExtractor() {
        this.dtoAnalyzer = new DTOAnalyzer();
    }

    /**
     * Извлекает все DTO из метода (уникальные)
     */
    @NotNull
    public DTOExtractionResult extractDTOs(@NotNull PsiMethod method) {
        return ReadAction.compute(() -> {
            List<MethodContext.DTOInfo> requestDTOs = new ArrayList<>();
            List<MethodContext.DTOInfo> responseDTOs = new ArrayList<>();

            processedTypes.clear();
            uniqueDTOs.clear();

            // 1. DTO из параметров метода
            extractFromParameters(method, requestDTOs);

            // 2. DTO из возвращаемого типа
            extractFromReturnType(method, responseDTOs);

            // 3. DTO из вызываемых методов
            extractFromCalledMethods(method, requestDTOs, responseDTOs);

            // 4. Добавляем все вложенные DTO к каждому найденному DTO
            enrichWithNestedDTOs(requestDTOs);
            enrichWithNestedDTOs(responseDTOs);

            return new DTOExtractionResult(requestDTOs, responseDTOs);
        });
    }

    /**
     * Обогащает список DTO всеми вложенными DTO (уникальные)
     */
    private void enrichWithNestedDTOs(@NotNull List<MethodContext.DTOInfo> dtos) {
        List<MethodContext.DTOInfo> allDTOs = new ArrayList<>(dtos);

        // Собираем все вложенные DTO
        for (MethodContext.DTOInfo dto : dtos) {
            collectNestedDTOsRecursively(dto, allDTOs);
        }

        // Очищаем список и добавляем уникальные DTO
        dtos.clear();

        // Добавляем уникальные DTO (сохраняя порядок первого появления)
        Set<String> addedNames = new HashSet<>();
        for (MethodContext.DTOInfo dto : allDTOs) {
            if (addedNames.add(dto.getName())) {
                dtos.add(dto);
            }
        }
    }

    /**
     * Рекурсивно собирает все вложенные DTO
     */
    private void collectNestedDTOsRecursively(@NotNull MethodContext.DTOInfo dto,
                                              @NotNull List<MethodContext.DTOInfo> collector) {
        // Проверяем поля на наличие вложенных DTO
        for (MethodContext.DTOInfo.FieldInfo field : dto.getFields()) {
            // Поиск вложенного DTO по имени поля, если имя соответствует имени DTO
            // Или можно хранить ссылку на вложенный DTO напрямую, но для этого нужно расширить FieldInfo

            // Альтернативный подход: ищем среди уже проанализированных DTO
            String possibleNestedName = field.getTypeName();

            // Проверяем, есть ли DTO с таким именем в хранилище уникальных DTO
            if (uniqueDTOs.containsKey(possibleNestedName)) {
                MethodContext.DTOInfo nestedDTO = uniqueDTOs.get(possibleNestedName);
                if (!collector.contains(nestedDTO)) {
                    collector.add(nestedDTO);
                    // Рекурсивно обрабатываем вложенный DTO
                    collectNestedDTOsRecursively(nestedDTO, collector);
                }
            }
        }
    }

    /**
     * Извлечение DTO из параметров метода
     */
    private void extractFromParameters(@NotNull PsiMethod method,
                                       @NotNull List<MethodContext.DTOInfo> requestDTOs) {
        for (PsiParameter param : method.getParameterList().getParameters()) {
            PsiType type = param.getType();

            // Пропускаем примитивы и JDK типы
            if (PsiUtils.isPrimitiveType(type) || isJdkSimpleType(type)) {
                continue;
            }

            // Проверяем аннотации
            boolean isRequestBody = PsiUtils.hasAnnotation(param,
                    "org.springframework.web.bind.annotation.RequestBody");
            boolean isModelAttribute = PsiUtils.hasAnnotation(param,
                    "org.springframework.web.bind.annotation.ModelAttribute");
            boolean isPathVariable = PsiUtils.hasAnnotation(param,
                    "org.springframework.web.bind.annotation.PathVariable");
            boolean isRequestParam = PsiUtils.hasAnnotation(param,
                    "org.springframework.web.bind.annotation.RequestParam");

            // Для REST API нас интересуют в первую очередь @RequestBody
            if (isRequestBody || isModelAttribute) {
                // Разворачиваем тип (вдруг там Optional или что-то ещё)
                List<PsiType> unwrappedTypes = unwrapType(type);
                for (PsiType unwrapped : unwrappedTypes) {
                    analyzeAndAddDTO(unwrapped, requestDTOs, "request");
                }
            }
            // PathVariable и RequestParam обычно примитивы, но могут быть и объекты
            else if (isPathVariable || isRequestParam) {
                if (!PsiUtils.isPrimitiveType(type) && !PsiUtils.isCollection(type)) {
                    analyzeAndAddDTO(type, requestDTOs, "request");
                }
            }
        }
    }

    /**
     * Извлечение DTO из возвращаемого типа
     */
    private void extractFromReturnType(@NotNull PsiMethod method,
                                       @NotNull List<MethodContext.DTOInfo> responseDTOs) {
        PsiType returnType = method.getReturnType();

        if (returnType == null || returnType.equals(PsiTypes.voidType())) {
            return;
        }

        // Рекурсивно разворачиваем все обёртки и коллекции
        List<PsiType> unwrappedTypes = unwrapType(returnType);

        for (PsiType unwrapped : unwrappedTypes) {
            // Добавляем только если это не примитив и не JDK тип
            if (!PsiUtils.isPrimitiveType(unwrapped) && !isJdkSimpleType(unwrapped)) {
                analyzeAndAddDTO(unwrapped, responseDTOs, "response");
            }
        }
    }

    /**
     * Рекурсивно разворачивает обёртки и коллекции до базового типа
     */
    @NotNull
    private List<PsiType> unwrapType(@Nullable PsiType type) {
        return ReadAction.compute(() -> {
            List<PsiType> result = new ArrayList<>();

            if (type == null) {
                return result;
            }

            // Избегаем бесконечной рекурсии
            String typeKey = type.getCanonicalText();
            if (processedTypes.contains(typeKey + "_unwrap")) {
                return result;
            }
            processedTypes.add(typeKey + "_unwrap");

            // 1. Проверяем, является ли тип коллекцией
            if (isCollectionType(type)) {
                PsiType[] typeParams = getTypeParameters(type);
                if (typeParams.length > 0) {
                    // Рекурсивно разворачиваем элемент коллекции
                    for (PsiType param : typeParams) {
                        result.addAll(unwrapType(param));
                    }
                }
                return result;
            }

            // 2. Проверяем, является ли тип обёрткой
            if (isWrapperType(type)) {
                PsiType[] typeParams = getTypeParameters(type);
                if (typeParams.length > 0) {
                    // Рекурсивно разворачиваем содержимое обёртки
                    for (PsiType param : typeParams) {
                        result.addAll(unwrapType(param));
                    }
                }
                return result;
            }

            // 3. Базовый случай: добавляем тип как есть
            result.add(type);
            return result;
        });
    }

    /**
     * Проверяет, является ли тип коллекцией
     */
    private boolean isCollectionType(@NotNull PsiType type) {
        String typeName = type.getPresentableText();

        // Быстрая проверка по имени
        for (String collectionType : COLLECTION_TYPES) {
            if (typeName.startsWith(collectionType.substring(collectionType.lastIndexOf('.') + 1))) {
                return true;
            }
        }

        // Проверка через PSI
        return PsiUtils.isTypeOf(type, "java.util.Collection") ||
                PsiUtils.isTypeOf(type, "java.util.List") ||
                PsiUtils.isTypeOf(type, "java.util.Set") ||
                PsiUtils.isTypeOf(type, "java.util.Stream");
    }

    /**
     * Проверяет, является ли тип обёрткой
     */
    private boolean isWrapperType(@NotNull PsiType type) {
        String typeName = type.getPresentableText();

        // Быстрая проверка по имени
        for (String wrapperType : WRAPPER_TYPES) {
            if (typeName.startsWith(wrapperType.substring(wrapperType.lastIndexOf('.') + 1))) {
                return true;
            }
        }

        // Проверка через PSI
        return PsiUtils.isTypeOf(type, "java.util.Optional") ||
                PsiUtils.isTypeOf(type, "org.springframework.http.ResponseEntity") ||
                PsiUtils.isTypeOf(type, "reactor.core.publisher.Mono") ||
                PsiUtils.isTypeOf(type, "reactor.core.publisher.Flux");
    }

    /**
     * Извлекает параметры обобщённого типа
     */
    @NotNull
    private PsiType[] getTypeParameters(@NotNull PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiType[] parameters = classType.getParameters();
            return parameters != null ? parameters : new PsiType[0];
        }
        return new PsiType[0];
    }

    /**
     * Проверяет, является ли тип простым JDK типом (не DTO)
     */
    private boolean isJdkSimpleType(@Nullable PsiType type) {
        if (type == null) return true;

        // Примитивы и обёртки
        if (PsiUtils.isPrimitiveType(type) || PsiUtils.isPrimitiveOrWrapper(type)) {
            return true;
        }

        // String, Object, Date, etc.
        String typeName = type.getPresentableText();
        return switch (typeName) {
            case "String", "Object", "Class", "Void" -> true;
            default -> PsiUtils.isJdkType(type);
        };
    }

    /**
     * Анализирует тип и добавляет DTO в список (с учётом уникальности)
     */
    private void analyzeAndAddDTO(@NotNull PsiType type,
                                  @NotNull List<MethodContext.DTOInfo> dtos,
                                  @NotNull String category) {
        if (type == null) return;

        String typeKey = type.getCanonicalText();

        // Избегаем дубликатов
        if (processedTypes.contains(typeKey)) {
            return;
        }
        processedTypes.add(typeKey);

        // Пропускаем примитивы и простые JDK типы
        if (PsiUtils.isPrimitiveType(type) || isJdkSimpleType(type)) {
            return;
        }

        // Анализируем через DTOAnalyzer
        DTOAnalyzer.DTOInfo dtoInfo = dtoAnalyzer.analyze(type);

        // Генерируем JSON пример
        String jsonExample = dtoAnalyzer.generateJsonExample(dtoInfo, 3);

        // Конвертируем в MethodContext.DTOInfo
        MethodContext.DTOInfo contextDTO = convertToContextDTO(dtoInfo, jsonExample);

        // Сохраняем в уникальное хранилище
        if (!uniqueDTOs.containsKey(contextDTO.getName())) {
            uniqueDTOs.put(contextDTO.getName(), contextDTO);
        }

        // Добавляем в результирующий список (если ещё не добавлен)
        if (!containsDTO(dtos, contextDTO.getName())) {
            dtos.add(contextDTO);
        }

        // Добавляем все вложенные DTO из анализатора
        addNestedDTOsFromAnalyzer(dtoInfo, dtos);
    }

    /**
     * Добавляет все вложенные DTO из DTOAnalyzer
     */
    private void addNestedDTOsFromAnalyzer(@NotNull DTOAnalyzer.DTOInfo dtoInfo,
                                           @NotNull List<MethodContext.DTOInfo> dtos) {
        // Получаем все вложенные DTO из анализатора
        List<DTOAnalyzer.DTOInfo> nestedDTOs = dtoInfo.getAllNestedDTOs();

        for (DTOAnalyzer.DTOInfo nestedDTO : nestedDTOs) {
            // Пропускаем, если это примитив или JDK класс
            if (SKIP_CLASSES.contains(nestedDTO.getName().toLowerCase())) {
                continue;
            }

            // Генерируем JSON пример для вложенного DTO
            String jsonExample = dtoAnalyzer.generateJsonExample(nestedDTO, 3);

            // Конвертируем в MethodContext.DTOInfo
            MethodContext.DTOInfo contextDTO = convertToContextDTO(nestedDTO, jsonExample);

            // Сохраняем в уникальное хранилище
            if (!uniqueDTOs.containsKey(contextDTO.getName())) {
                uniqueDTOs.put(contextDTO.getName(), contextDTO);
            }

            // Добавляем в результирующий список, если ещё не добавлен
            if (!containsDTO(dtos, contextDTO.getName())) {
                dtos.add(contextDTO);
            }
        }
    }

    /**
     * Проверяет, содержит ли список DTO с указанным именем
     */
    private boolean containsDTO(@NotNull List<MethodContext.DTOInfo> dtos, @NotNull String name) {
        return dtos.stream().anyMatch(dto -> dto.getName().equals(name));
    }

    /**
     * Конвертирует DTOAnalyzer.DTOInfo в MethodContext.DTOInfo
     */
    @NotNull
    private MethodContext.DTOInfo convertToContextDTO(@NotNull DTOAnalyzer.DTOInfo dtoInfo,
                                                      @NotNull String jsonExample) {
        // Проверяем, нужно ли пропустить этот класс
        if (SKIP_CLASSES.contains(dtoInfo.getName().toLowerCase())) {
            // Возвращаем пустой DTOInfo, который будет проигнорирован
            return new MethodContext.DTOInfo(
                    dtoInfo.getName(),
                    dtoInfo.getCategory(),
                    new ArrayList<>(),
                    jsonExample
            );
        }

        return new MethodContext.DTOInfo(
                dtoInfo.getName(),
                dtoInfo.getCategory(),
                dtoInfo.getFields().stream()
                        .map(f -> new MethodContext.DTOInfo.FieldInfo(
                                f.getName(),
                                f.getTypeName(),
                                f.getAnnotations(),
                                f.isNullable()
                        ))
                        .collect(Collectors.toList()),
                jsonExample
        );
    }

    /**
     * Извлечение DTO из вызываемых методов
     */
    private void extractFromCalledMethods(@NotNull PsiMethod method,
                                          @NotNull List<MethodContext.DTOInfo> requestDTOs,
                                          @NotNull List<MethodContext.DTOInfo> responseDTOs) {
        for (PsiMethodCallExpression call :
                com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression.class)) {

            PsiElement resolved = call.getMethodExpression().resolve();
            if (resolved instanceof PsiMethod calledMethod) {
                // Параметры вызываемого метода
                for (PsiParameter param : calledMethod.getParameterList().getParameters()) {
                    List<PsiType> unwrapped = unwrapType(param.getType());
                    for (PsiType type : unwrapped) {
                        analyzeAndAddDTO(type, requestDTOs, "internal");
                    }
                }
                // Возвращаемый тип
                List<PsiType> unwrapped = unwrapType(calledMethod.getReturnType());
                for (PsiType type : unwrapped) {
                    analyzeAndAddDTO(type, responseDTOs, "internal");
                }
            }
        }
    }

    /**
     * Результат извлечения DTO
     */
    public static class DTOExtractionResult {
        private final List<MethodContext.DTOInfo> requestDTOs;
        private final List<MethodContext.DTOInfo> responseDTOs;

        public DTOExtractionResult(List<MethodContext.DTOInfo> requestDTOs,
                                   List<MethodContext.DTOInfo> responseDTOs) {
            this.requestDTOs = requestDTOs;
            this.responseDTOs = responseDTOs;
        }

        public List<MethodContext.DTOInfo> getRequestDTOs() {
            return requestDTOs;
        }

        public List<MethodContext.DTOInfo> getResponseDTOs() {
            return responseDTOs;
        }

        /**
         * Получить все уникальные DTO (объединение request и response)
         */
        public List<MethodContext.DTOInfo> getAllUniqueDTOs() {
            Set<String> names = new HashSet<>();
            List<MethodContext.DTOInfo> all = new ArrayList<>();

            for (MethodContext.DTOInfo dto : requestDTOs) {
                if (names.add(dto.getName())) {
                    all.add(dto);
                }
            }

            for (MethodContext.DTOInfo dto : responseDTOs) {
                if (names.add(dto.getName())) {
                    all.add(dto);
                }
            }

            return all;
        }
    }
}