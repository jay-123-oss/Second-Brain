@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%

echo ======================================================================
echo Second Brain - Exporting Shareable APK
echo ======================================================================

echo [1/2] Compiling latest APK...
call gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    exit /b %ERRORLEVEL%
)

echo [2/2] Copying shareable APK to Project Root and Desktop...
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "Second_Brain.apk"
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "..\Second_Brain.apk"

echo ======================================================================
echo [SUCCESS] Shareable APK ready!
echo 1. Project Root: c:\Users\jayde\OneDrive\Desktop\Brain\Second_Brain.apk
echo 2. Desktop:      c:\Users\jayde\OneDrive\Desktop\Second_Brain.apk
echo ======================================================================
