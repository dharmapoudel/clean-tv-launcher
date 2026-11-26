# Installing Clean TV Launcher on your TV

A complete install, start to finish. Written so a person — or an AI agent
setting the TV up for someone — can follow it in order. **Do the steps in
order and run each check before moving on.** Nothing here uninstalls
anything; every change can be undone (see the end).

## What you need

- An Android TV / Google TV set (Android 11 or newer) on your network.
- A computer on the same network with `adb` (Android platform-tools).
- The APK from the
  [Releases page](https://github.com/dharmapoudel/clean-tv-launcher/releases),
  or a build from source (`./gradlew assembleRelease` →
  `app/build/outputs/apk/release/app-release.apk`).

## 1. Turn on debugging on the TV

1. Settings → Device Preferences → About → tap **Build** 7 times to unlock
   Developer options.
2. Settings → Device Preferences → Developer options → turn on
   **Wireless debugging** (or **Network debugging** / **ADB debugging**,
   depending on the set).
3. If the set offers **Pair device with pairing code**, note the pairing
   IP:port and the 6-digit code — you need them once, in the next step.

## 2. Connect from the computer

```sh
adb pair <tv-ip>:<pairing-port>      # enter the code when asked (skip if no pairing)
adb connect <tv-ip>:5555             # or the port Wireless debugging shows
adb devices                          # the TV must show as "device", not "offline"
```

If it shows `offline`, the TV is probably in standby — wake it with the
remote and retry. A sleeping TV drops ADB; that is normal.

## 3. Back up the TV's state first

Before changing anything, save what would be needed to put it back:

```sh
adb shell pm list packages            > packages.txt
adb shell pm list packages -d         > packages-disabled.txt
adb shell getprop                     > getprop.txt
adb shell settings list global        > settings-global.txt
adb shell settings list secure        > settings-secure.txt
```

## 4. Check who owns the Wi-Fi connection

On Google TV the setup wizard sometimes owns the saved Wi-Fi network. If it
does, disabling it later **deletes the network and kills Wi-Fi (and ADB)**.

```sh
adb shell dumpsys wifi | grep -i cname
```

- If the saved network's owner is the TV Settings app — fine, continue.
- If it names a setup/wizard package (e.g. `...setupwraith`) — **stop**.
  On the TV, forget the network and rejoin it from Settings first, then
  check again.

## 5. Install the APK

```sh
adb install clean-tv-launcher-3.6.apk
```

## 6. Make it the home screen

Easiest is the install script from a source checkout, which does steps 6–8
and verifies each one:

```sh
TV_HOST=<tv-ip> INPUT_KEY_SERVICE=1 ./build-and-install.sh
```

Or by hand:

```sh
# point Home at the new launcher
adb shell cmd role add-role-holder android.app.role.HOME com.gathin.tvhome

# disable (never uninstall) the stock launcher — find yours first:
adb shell pm list packages | grep -i launcher
adb shell pm disable-user --user 0 <stock-launcher-package>
```

On Google TV also disable the setup wizard
(`com.google.android.tungsten.setupwraith`) — **only after step 4 says it's
safe**.

Check: press **Home** on the remote. The new home screen should appear, and

```sh
adb shell dumpsys activity activities | grep mFocusedApp
```

should name `com.gathin.tvhome/.MainActivity`. If Home rolls back to a
setup/recovery screen instead, the wizard is still claiming Home — disable
it (step 4 permitting) and retry.

## 7. Enable the key helper (exactly one)

The launcher ships two accessibility helpers that make the remote's
extra buttons work. Enable **one**:

- **Google TV → `InputKeyService`.** Use this one. (On Google TV the other
  helper can make a single D-pad press jump several tiles.)
- Android TV (non-Google) → `HomeKeyService`.

```sh
# Google TV:
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/.InputKeyService
adb shell settings put secure accessibility_enabled 1
```

Then, on the remote itself (injected keys are not proof for these):
press the **gear/Settings** button — Settings should open; the **YouTube**
button should open SmartTube if installed; hold **Volume Up** — it should
step once, then ramp.

## 8. Re-grant the settings permission

The install script revokes this grant as a safety measure; the launcher's
Private DNS setting needs it back:

```sh
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
```

## 9. Make it yours

- **Apps**: long-press OK on a tile to replace/move it; the App library is
  in the menu you get by long-pressing the Settings pill (top right).
- **Wallpapers**: drop photos into
  `/sdcard/Android/data/com.gathin.tvhome/files/wallpapers/`, live videos
  (plus same-name audio files) into `.../files/videos/`, and pre-generated
  thumbnails into `.../files/thumbs/` — then pick in Launcher settings →
  Wallpaper. Details in the [README](README.md#wallpapers).
- **Private DNS, idle fade, sleep timer**: Launcher settings — see the
  README.

## Undo everything

```sh
adb shell pm enable --user 0 <stock-launcher-package>
adb shell pm enable --user 0 com.google.android.tungsten.setupwraith   # if you disabled it
adb shell cmd role add-role-holder android.app.role.HOME <stock-launcher-package>
adb uninstall com.gathin.tvhome
adb shell settings put global private_dns_mode opportunistic          # if you set a DNS
```

## Troubleshooting

- **`device offline`** — the TV is asleep or debugging turned off. Wake it,
  check Developer options, reconnect.
- **Private DNS row says "not available"** — redo step 8.
- **One remote press moves several tiles** — the wrong key helper is on.
  Redo step 7 with `InputKeyService` and make sure it is the *only* service
  listed.
- **Home opens Live TV on power-up** — some firmware does this; it's a TV
  setting, not the launcher. Don't fight it with both `power_on_home`-style
  settings at once.
- **The screen goes black and ADB dies a few minutes after setup** — that's
  the sleep timer working (fade → screen off). Press any remote key to wake.
