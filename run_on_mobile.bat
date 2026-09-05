@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set DEVICE=ZD222J45KF

echo [1/5] Compiling APK...
call gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    exit /b %ERRORLEVEL%
)

echo [2/5] Installing APK on mobile device %DEVICE%...
"%ADB%" -s %DEVICE% install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Installation failed!
    exit /b %ERRORLEVEL%
)

echo [3/5] Waking screen and unlocking...
"%ADB%" -s %DEVICE% shell input keyevent KEYCODE_WAKEUP
"%ADB%" -s %DEVICE% shell wm dismiss-keyguard

echo [4/5] Launching Second Brain...
"%ADB%" -s %DEVICE% shell am start -n com.example.brain/.MainActivity

echo [5/5] Capturing verification screenshot...
timeout /t 3 /nobreak >nul
"%ADB%" -s %DEVICE% shell screencap -p /sdcard/screen.png
"%ADB%" -s %DEVICE% pull /sdcard/screen.png "c:\Users\jayde\.gemini\antigravity-ide\brain\1065597f-a2e4-44b7-87d2-5e6e8ed3bd8a\screen.png"

echo [SUCCESS] App is running live on your mobile device!
