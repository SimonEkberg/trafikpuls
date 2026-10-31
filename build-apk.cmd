@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
cd /d "%~dp0"
call gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace
endlocal
