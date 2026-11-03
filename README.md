# mobilemouse

Use an Android phone as a Bluetooth mouse for your laptop. The app sends standard Bluetooth HID mouse reports directly to the laptop, with no companion app or Wi-Fi connection required.

## Install and pair

1. Download [mobilemouse.apk](https://github.com/zaidansr/mobilemouse/releases/latest/download/mobilemouse.apk) and open it on your Android phone. Android may ask you to allow installs from the app that opened the file.
2. Open **mobilemouse**, allow **Nearby devices** if prompted, and turn on Bluetooth.
3. Tap **Pair new laptop**. In your laptop's Bluetooth settings, add a new device and select your phone. Confirm the pairing prompt.
4. If the phone is already paired, tap **Paired devices** and choose your laptop. Keep mobilemouse open during use.

## Controls

- Slide one finger to move the pointer. Tap the pad to left-click.
- Slide two fingers vertically to scroll.
- Use the left, middle, and right buttons for clicks. To drag, hold the left button while moving another finger on the pad.

## Compatibility

Requires Android 9 or later, phone firmware with the Bluetooth HID Device profile, and a laptop that accepts Bluetooth Classic HID mice. OnePlus 10T pairing has yet to be verified on hardware.

If the laptop pairs but the pointer does not move, check that the app shows **Connected**. If necessary, remove the existing pairing on both devices, then pair again while the app is open. If the app cannot start the HID Device profile, report your phone model and Android version in [Issues](https://github.com/zaidansr/mobilemouse/issues).

## Build on Windows

Install JDK 17, Android SDK Platform 35, and Android SDK Build-Tools 35.0.0. Set `JAVA_HOME` to the JDK directory and `ANDROID_HOME` to the SDK directory. From the repository root, run:

```powershell
pwsh -File ./build.ps1
```

The signed development APK is written to `build/mobilemouse.apk`. The script generates a local development key on first run. A locally signed APK requires a fresh install if you already installed the release APK, because Android checks the signing key for updates.

## License

[MIT](LICENSE)
