# Debloat — what was disabled on the reference TV, and how to undo it

The launcher is only half of a clean TV. The other half is turning off the
pre-installed apps and telemetry that ship with the set. This page records
exactly what was disabled on the reference TV (a TCL 75QM6K Pro, Google TV,
Android 14), why, and how to bring any of it back.

Nothing here is uninstalled. Everything is disabled with
`pm disable-user --user 0`, which is reversible in one command — that is the
whole point of doing it this way.

## Ground rules used

- Back up first: full package list, disabled list, `getprop`, and all
  settings, before touching anything.
- Disable in small batches, check the TV still works between batches, and
  reboot once at the end to confirm nothing on the list was load-bearing.
- Never disable: the TV/HDMI/tuner and input services, the input/source menu,
  remote and Bluetooth pairing, the on-screen keyboard, the Play Store, Play
  Services, Android core packages (settings, system UI, package installer,
  permission controller, providers), or the manufacturer's boot/config
  packages. On this TCL, `com.tcl.providers.config`, `com.tcl.initsetup` and
  `com.tcl.factory.view` stay enabled — together they hold up Wi-Fi.
- Before disabling the setup wizard, check who owns the saved Wi-Fi network
  (`dumpsys wifi`): if it belongs to the setup app, forget and rejoin the
  network in Settings first, or disabling the wizard deletes the network.
- Lists like this one are model-specific. Another TV will have a different
  set of packages — treat this as a worked example, not a script to run
  blind.

## The 31 disabled packages

| Package | What it is | Why it's off |
| --- | --- | --- |
| `com.tcl.logkit` | TCL log collection | Telemetry |
| `com.tcl.tv.plus` | TCL TV+ promo app | Pre-installed promo |
| `com.tcl.channelplus` | Channel Plus promo | Pre-installed promo |
| `com.tcl.tv.tclhome_passive` | Stock home passive layer | Promo/recommendations |
| `com.tcl.partnercustomizer` | Partner customisation hooks | Pre-installed promo |
| `com.tcl.interactive` | TCL interactive promos | Pre-installed promo |
| `com.tcl.smartlink.core` | SmartLink promo/sharing hooks | Not used |
| `com.tcl.esticker` | Sticker promo app | Pre-installed promo |
| `com.tcl.hotelmenu` | Hotel mode menu | Not used |
| `com.tcl.ocean.instructions` | Built-in instruction promos | Not used |
| `com.tcl.repairguide` | Repair guide app | Not used |
| `android.autoinstalls.config.google.gtvpai` | Partner app auto-install config | Stops bundled apps reinstalling themselves |
| `com.google.android.feedback` | Google feedback reporter | Reporting |
| `com.android.tv.feedbackconsent` | Feedback consent prompts | Reporting |
| `com.google.android.apps.tv.dreamx` | Google TV ambient promo app | Promo content |
| `com.netflix.ninja` | Netflix | Not used on this TV |
| `com.netflix.tokenmanager` | Netflix token helper | Goes with Netflix |
| `com.amazon.amazonvideo.livingroom` | Prime Video | Not used on this TV |
| `com.google.android.youtube.tv` | YouTube (TV) | Replaced by SmartTube |
| `com.android.adservices.api` | Android ad services | Ad topics/attribution |
| `com.android.ondevicepersonalization.services` | On-device personalisation | Ad personalisation |
| `com.android.federatedcompute.services` | Federated compute | Ad personalisation |
| `com.android.printspooler` | Print spooler | Nobody prints from a TV |
| `com.google.android.syncadapters.calendar` | Calendar sync adapter | No account signed in on the TV |
| `com.google.android.onetimeinitializer` | One-time setup initialiser | Already ran at first setup |
| `com.google.android.tv.remote.service` | Google TV phone-remote service | Not used |
| `com.google.android.marvin.talkback` | TalkBack | Not used |
| `com.tcl.gamebar` | TCL Game Bar overlay | Not used |
| `com.google.android.tungsten.setupwraith` | Google TV setup wizard | Setup is done; the launcher owns Home now (Wi-Fi check first — see above) |
| `com.google.android.apps.tv.launcherx` | Stock Google TV launcher | Replaced by this launcher |
| `com.tcl.ttvs` | TCL system service — also hosts Samba TV content recognition (ACR) and usage reporting | Disabled in a privacy audit: the ACR service can't be switched off separately from the shell, so the whole package went off. Side effect: firmware updates are USB-only afterwards |

Deliberately **not** disabled: the art app the wallpapers came from (left
enabled and force-stopped, so it runs nothing in the background), the media
player, Chromecast and AirPlay receivers, and everything in the
never-disable list above.

## Re-enable anything

One package, over ADB:

```
adb shell pm enable --user 0 com.netflix.ninja
```

Everything at once — this is the restore script, one line per package:

```
adb shell pm enable --user 0 com.tcl.logkit
adb shell pm enable --user 0 com.tcl.tv.plus
adb shell pm enable --user 0 com.tcl.channelplus
adb shell pm enable --user 0 com.tcl.tv.tclhome_passive
adb shell pm enable --user 0 com.tcl.partnercustomizer
adb shell pm enable --user 0 com.tcl.interactive
adb shell pm enable --user 0 com.tcl.smartlink.core
adb shell pm enable --user 0 com.tcl.esticker
adb shell pm enable --user 0 com.tcl.hotelmenu
adb shell pm enable --user 0 com.tcl.ocean.instructions
adb shell pm enable --user 0 com.tcl.repairguide
adb shell pm enable --user 0 android.autoinstalls.config.google.gtvpai
adb shell pm enable --user 0 com.google.android.feedback
adb shell pm enable --user 0 com.android.tv.feedbackconsent
adb shell pm enable --user 0 com.google.android.apps.tv.dreamx
adb shell pm enable --user 0 com.netflix.ninja
adb shell pm enable --user 0 com.netflix.tokenmanager
adb shell pm enable --user 0 com.amazon.amazonvideo.livingroom
adb shell pm enable --user 0 com.google.android.youtube.tv
adb shell pm enable --user 0 com.android.adservices.api
adb shell pm enable --user 0 com.android.ondevicepersonalization.services
adb shell pm enable --user 0 com.android.federatedcompute.services
adb shell pm enable --user 0 com.android.printspooler
adb shell pm enable --user 0 com.google.android.syncadapters.calendar
adb shell pm enable --user 0 com.google.android.onetimeinitializer
adb shell pm enable --user 0 com.google.android.tv.remote.service
adb shell pm enable --user 0 com.google.android.marvin.talkback
adb shell pm enable --user 0 com.tcl.gamebar
adb shell pm enable --user 0 com.google.android.tungsten.setupwraith
adb shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb shell pm enable --user 0 com.tcl.ttvs
```

Two things to know before restoring the last two blocks:

- Re-enabling the stock launcher (`launcherx`) doesn't make it Home again by
  itself — pick a Home app in Settings, or with
  `adb shell cmd package set-home-activity`.
- Re-enabling `com.tcl.ttvs` turns Samba TV content recognition back on.
  That was the point of disabling it.

Without ADB, most disabled apps can also be turned back on from the TV
itself: **Settings → Apps → See all apps → Disabled apps**, pick the app,
**Enable**.

To see what is currently disabled on a TV:

```
adb shell pm list packages -d
```

Note: some manufacturers quietly re-enable packages after a firmware
update. After an update, run the list command above and compare.
