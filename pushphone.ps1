# pushphone.ps1 - Push APK to phone via ADB
# Uncomment the next line to remove device admin first
# adb shell dpm remove-active-admin --user 0 com.bintianqi.owndroid/.Receiver

# Get the first APK file from release folder
$apkFile = Get-ChildItem "app\build\outputs\apk\release\" -Filter *.apk | Select-Object -First 1

if ($apkFile) {
    Write-Host "Pushing: $($apkFile.Name)" -ForegroundColor Cyan
    adb push $apkFile.FullName /sdcard/abc/
    Write-Host "Done!" -ForegroundColor Green
} else {
    Write-Host "No APK found in app\build\outputs\apk\release\" -ForegroundColor Red
}
