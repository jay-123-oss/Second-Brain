@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set DEVICE=ZD222J45KF

echo ======================================================================
echo Second Brain - Prompt 7: Knowledge Intelligence & Local Discovery
echo ======================================================================

echo [1/3] Running Unit Tests for Knowledge Intelligence...
call gradlew.bat testDebugUnitTest --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Unit Tests failed!
    exit /b %ERRORLEVEL%
)

echo [2/3] Compiling Debug APK...
call gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [3/3] Build & Unit Test Verification Complete!
echo [READY] When you want to run on your phone, pass the --run flag or execute adb install.
if "%1"=="--run" (
    echo Installing onto device %DEVICE%...
    "%ADB%" -s %DEVICE% install -r app\build\outputs\apk\debug\app-debug.apk
    "%ADB%" -s %DEVICE% shell input keyevent KEYCODE_WAKEUP
    "%ADB%" -s %DEVICE% shell wm dismiss-keyguard
    "%ADB%" -s %DEVICE% shell am start -n com.example.brain/.MainActivity
)
