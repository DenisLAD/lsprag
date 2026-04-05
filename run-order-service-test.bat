@echo off
REM Run OrderServiceTest directly without Gradle instrumentation issues

set JAVA_HOME=D:\Work\Java\graalvm-jdk-21.0.1+12.1
set PATH=%JAVA_HOME%\bin;%PATH%

echo ========================================
echo Running OrderServiceTest
echo ========================================
echo.

REM Get classpath from Gradle
set CLASSPATH_CMD=gradlew.bat -q dependencies --configuration testRuntimeClasspath --no-daemon 2^>nul ^| findstr /V "^$"

REM Just compile and run with simple classpath
set BUILD_CLASSES=build\classes\java\main;build\classes\java\test
set LIBS=C:\Users\lucif\.gradle\caches\modules-2\files-2.1

REM Find JARs  
for /f "delims=" %%i in ('dir /s /b %LIBS%\junit-jupiter-api\5.10.1\*\*.jar 2^>nul') do set JUNIT_API=%%i
for /f "delims=" %%i in ('dir /s /b %LIBS%\junit-jupiter-engine\5.10.1\*\*.jar 2^>nul') do set JUNIT_ENGINE=%%i
for /f "delims=" %%i in ('dir /s /b %LIBS%\assertj-core\3.24.2\*\*.jar 2^>nul') do set ASSERTJ=%%i
for /f "delims=" %%i in ('dir /s /b %LIBS%\mockito-core\5.8.0\*\*.jar 2^>nul') do set MOCKITO=%%i
for /f "delims=" %%i in ('dir /s /b %LIBS%\byte-buddy\1.14.10\*\*.jar 2^>nul') do set BYTEBUDDY=%%i
for /f "delims=" %%i in ('dir /s /b %LIBS%\objenesis\3.3\*\*.jar 2^>nul') do set OBJENESIS=%%i

set CP=%BUILD_CLASSES%;%JUNIT_API%;%JUNIT_ENGINE%;%ASSERTJ%;%MOCKITO%;%BYTEBUDDY%;%OBJENESIS%

echo Running test...
echo.

%JAVA_HOME%\bin\java -cp "%CP%" org.junit.platform.console.ConsoleLauncher --select-class=com.reasoningtestgen.example.OrderServiceTest

echo.
echo ========================================
pause
