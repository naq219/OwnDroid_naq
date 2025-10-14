adb shell dpm remove-active-admin --user 0 com.bintianqi.owndroid/.Receiver
adb push (Get-ChildItem "app\build\outputs\apk\release\" -Filter *.apk | Select-Object -First 1).FullName /sdcard/abc/