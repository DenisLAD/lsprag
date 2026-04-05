@echo off
REM =============================================
REM Simple Test Runner - No IntelliJ dependencies
REM =============================================

set JAVA_HOME=D:\Work\Java\graalvm-jdk-21.0.1+12.1
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_CACHE=C:\Users\lucif\.gradle\caches\modules-2\files-2.1

echo ========================================
echo Running Standalone Tests
echo ========================================
echo.

REM Find JAR files
set JACKSON_DATABIND=%GRADLE_CACHE%\com.fasterxml.jackson.core\jackson-databind\2.16.1\*\jackson-databind-2.16.1.jar
set JACKSON_CORE=%GRADLE_CACHE%\com.fasterxml.jackson.core\jackson-core\2.16.1\*\jackson-core-2.16.1.jar
set JACKSON_ANNOT=%GRADLE_CACHE%\com.fasterxml.jackson.core\jackson-annotations\2.16.1\*\jackson-annotations-2.16.1.jar
set JACKSON_JDK8=%GRADLE_CACHE%\com.fasterxml.jackson.datatype\jackson-datatype-jdk8\2.16.1\*\jackson-datatype-jdk8-2.16.1.jar
set JACKSON_JSR310=%GRADLE_CACHE%\com.fasterxml.jackson.datatype\jackson-datatype-jsr310\2.16.1\*\jackson-datatype-jsr310-2.16.1.jar
set SLF4J=%GRADLE_CACHE%\org.slf4j\slf4j-api\2.0.9\*\slf4j-api-2.0.9.jar

set CLASSPATH=%JACKSON_DATABIND%;%JACKSON_CORE%;%JACKSON_ANNOT%;%JACKSON_JDK8%;%JACKSON_JSR310%;%SLF4J%

REM Clean
rmdir /s /q build\test-standalone 2>nul
mkdir build\test-standalone\classes 2>nul
mkdir build\test-standalone\main 2>nul

echo Step 1: Compiling main sources...
%JAVA_HOME%\bin\javac -d build\test-standalone\main -encoding UTF-8 -source 17 -target 17 -cp "%CLASSPATH%" ^
    src\main\java\com\reasoningtestgen\model\*.java ^
    src\main\java\com\reasoningtestgen\builder\ContextBuilder.java ^
    src\main\java\com\reasoningtestgen\service\PromptHistoryService.java ^
    src\main\java\com\reasoningtestgen\llm\LLMProviderType.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to compile main sources
    pause
    exit /b 1
)
echo OK
echo.

echo Step 2: Compiling test sources...
%JAVA_HOME%\bin\javac -d build\test-standalone\classes -encoding UTF-8 -source 17 -target 17 -cp "%CLASSPATH%;build\test-standalone\main" ^
    src\test\java\com\reasoningtestgen\SimpleTestRunner.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to compile test sources
    pause
    exit /b 1
)
echo OK
echo.

echo Step 3: Running tests...
echo.
%JAVA_HOME%\bin\java -cp "build\test-standalone\main;build\test-standalone\classes;%CLASSPATH%" com.reasoningtestgen.SimpleTestRunner

set TEST_RESULT=%ERRORLEVEL%
echo.

if %TEST_RESULT% NEQ 0 (
    echo TESTS FAILED
    pause
    exit /b 1
)

echo.
echo ========================================
echo ALL TESTS PASSED
echo ========================================
pause
