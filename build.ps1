$ErrorActionPreference = 'Stop'

function Run-Tool($program, $arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "$program exited with code $LASTEXITCODE" }
}

$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (!$sdk -or !$env:JAVA_HOME) { throw 'Set ANDROID_HOME and JAVA_HOME first.' }
$tools = Join-Path $sdk 'build-tools/35.0.0'
$android = Join-Path $sdk 'platforms/android-35/android.jar'
$java = Join-Path $env:JAVA_HOME 'bin'

Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force build/classes, build/dex | Out-Null
    Run-Tool "$java/javac.exe" @('--release', '8', '-encoding', 'UTF-8', '-classpath', $android, '-d', 'build/classes', 'MainActivity.java')
    Run-Tool "$java/jar.exe" @('cf', 'build/compiled.jar', '-C', 'build/classes', '.')
    Run-Tool "$tools/d8.bat" @('--release', '--min-api', '28', '--lib', $android, '--output', 'build/dex', 'build/compiled.jar')
    Run-Tool "$tools/aapt2.exe" @('link', '-I', $android, '--manifest', 'AndroidManifest.xml', '-o', 'build/unsigned.apk')
    Run-Tool "$java/jar.exe" @('uf', 'build/unsigned.apk', '-C', 'build/dex', 'classes.dex')
    Run-Tool "$tools/zipalign.exe" @('-f', '4', 'build/unsigned.apk', 'build/aligned.apk')
    if (!(Test-Path build/local.p12)) {
        Run-Tool "$java/keytool.exe" @('-genkeypair', '-keystore', 'build/local.p12', '-storetype', 'PKCS12', '-storepass', 'android', '-alias', 'local', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '3650', '-dname', 'CN=mobilemouse Local Development')
    }
    Run-Tool "$tools/apksigner.bat" @('sign', '--ks', 'build/local.p12', '--ks-pass', 'pass:android', '--v4-signing-enabled', 'false', '--out', 'build/mobilemouse.apk', 'build/aligned.apk')
    Run-Tool "$tools/apksigner.bat" @('verify', 'build/mobilemouse.apk')
} finally {
    Pop-Location
}
