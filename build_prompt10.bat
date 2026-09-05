@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set DEVICE=ZD222J45KF

echo ======================================================================
echo Second Brain - Prompt 10: Personal Knowledge OS and Logo Verification
echo ======================================================================

echo [1/3] Running Unit Tests (KnowledgeIntelligence, LocalAI, KnowledgeOS)...
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

echo [3/3] Installing and Launching on Device %DEVICE%...
"%ADB%" -s %DEVICE% install -r app\build\outputs\apk\debug\app-debug.apk
"%ADB%" -s %DEVICE% shell input keyevent KEYCODE_WAKEUP
"%ADB%" -s %DEVICE% shell wm dismiss-keyguard
"%ADB%" -s %DEVICE% shell am start -n com.example.brain/.MainActivity

echo ======================================================================
echo [SUCCESS] App Successfully Deployed and Running on Mobile!
echo ======================================================================
