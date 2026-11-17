@echo off
REM Build APK release + cai dat qua adb (neu co thiet bi).
REM Yeu cau: Android SDK + JDK cua Android Studio (duong dan mac dinh duoi).

set "JAVA_HOME=E:\PROGRAM\AndroidStudio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

where adb >nul 2>nul
if errorlevel 1 (
  if exist "E:\PROGRAM\AndroidStudioSDK\platform-tools\adb.exe" (
    echo [INFO] Them adb cua SDK vao PATH
    set "PATH=E:\PROGRAM\AndroidStudioSDK\platform-tools;%PATH%"
  ) else (
    echo [WARN] Khong tim thay adb - chi build, khong cai dat
    set "NOADB=1"
  )
)

echo [1/3] Build release...
call .\gradlew.bat :app:assembleRelease
if errorlevel 1 (
  echo [FAIL] Build that bai
  exit /b 1
)

echo [2/3] Tim file APK...
set "APK="
for /f "delims=" %%f in ('dir /b /o-d "app\build\outputs\apk\release\*.apk" 2^>nul') do (
  if not defined APK set "APK=app\build\outputs\apk\release\%%f"
)
if not defined APK (
  echo [FAIL] Khong tim thay APK trong app\build\outputs\apk\release\
  exit /b 1
)
echo [OK] APK: %APK%

if defined NOADB exit /b 0

echo [3/3] Cai dat qua adb...
adb devices
adb install -r "%APK%"
if errorlevel 1 (
  echo [FAIL] adb install that bai (kiem tra dien thoai da bat USB debugging + uy quyen)
  exit /b 1
)
echo [OK] Cai dat xong.
