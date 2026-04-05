@echo off
REM Simple test runner for unit tests
REM Does not use IntelliJ instrumentation

set JAVA_HOME=D:\Work\Java\graalvm-jdk-21.0.1+12.1
set PATH=%JAVA_HOME%\bin;%PATH%

echo ========================================
echo Running Unit Tests
echo ========================================
echo.

REM Compile main sources
echo Compiling main sources...
mkdir build\classes\java\main 2>nul
%JAVA_HOME%\bin\javac.exe ^
    -d build\classes\java\main ^
    -encoding UTF-8 ^
    -source 17 -target 17 ^
    -cp "C:\Users\lucif\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.core\jackson-databind\2.16.1\*\jackson-databind-2.16.1.jar;C:\Users\lucif\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.core\jackson-core\2.16.1\*\jackson-core-2.16.1.jar;C:\Users\lucif\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.core\jackson-annotations\2.16.1\*\jackson-annotations-2.16.1.jar;C:\Users\lucif\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.datatype\jackson-datatype-jdk8\2.16.1\*\jackson-datatype-jdk8-2.16.1.jar;C:\Users\lucif\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.datatype\jackson-datatype-jsr310\2.16.1\*\jackson-datatype-jsr310-2.16.1.jar;C:\Users\lucif\.gradle\caches\modules-2\files-2.1\com.squareup.okhttp3\okhttp\4.12.0\*\okhttp-4.12.0.jar;C:\Users\lucif\.gradle\caches\modules-2\files-2.1\org.slf4j\slf4j-api\2.0.9\*\slf4j-api-2.0.9.jar" ^
    src\main\java\com\reasoningtestgen\model\*.java ^
    src\main\java\com\reasoningtestgen\builder\ContextBuilder.java ^
    src\main\java\com\reasoningtestgen\service\PromptHistoryService.java ^
    src\main\java\com\reasoningtestgen\llm\LLMProviderType.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to compile main sources
    pause
    exit /b 1
)

echo Main sources compiled successfully
echo.
pause
