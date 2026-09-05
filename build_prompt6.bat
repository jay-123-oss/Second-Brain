@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set DEVICE=ZD222J45KF

echo [1/4] Compiling Prompt 6 Advanced Capture & Media Intelligence APK...
call gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [2/4] Installing updated Second Brain APK onto %DEVICE%...
"%ADB%" -s %DEVICE% install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Installation failed!
    exit /b %ERRORLEVEL%
)

echo [3/4] Waking device and launching Second Brain...
"%ADB%" -s %DEVICE% shell input keyevent KEYCODE_WAKEUP
"%ADB%" -s %DEVICE% shell wm dismiss-keyguard
"%ADB%" -s %DEVICE% shell am start -n com.example.brain/.MainActivity

echo [4/4] Capturing verified screenshot...
timeout /t 3 /nobreak >nul
"%ADB%" -s %DEVICE% shell screencap -p /sdcard/screen.png
"%ADB%" -s %DEVICE% pull /sdcard/screen.png "c:\Users\jayde\.gemini\antigravity-ide\brain\1065597f-a2e4-44b7-87d2-5e6e8ed3bd8a\screen.png"

echo [SUCCESS] Prompt 6 Advanced Capture & Media Intelligence System is live on your mobile device!
