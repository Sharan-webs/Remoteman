# RemoteMan

A personal, AnyDesk-style remote screen share + control app for Android.
Host device shares its screen and accepts control; connector device enters a
6-digit code and controls the host.

## Already done for you

- `app/google-services.json` is included (project: `remoteman-d0215`)
- Metered TURN app name is set to `remoteman` in `TurnCredentials.kt`
- The publishable TURN key is already filled in

## Still needed before you build

1. **Set your Firebase Realtime Database rules**
   In Firebase console > Realtime Database > Rules, paste:
   ```json
   {
     "rules": {
       "sessions": {
         "$code": {
           ".read": true,
           ".write": true
         }
       }
     }
   }
   ```

2. **App icon**
   Add a launcher icon at `app/src/main/res/mipmap-*/ic_launcher.png`
   (Android Studio's Image Asset tool can generate these for you), or the
   build will fail on the missing `@mipmap/ic_launcher` reference.

## Building via Termux + GitHub

```bash
# from inside the unzipped RemoteMan folder
git init
git add .
git commit -m "initial commit"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

Then build an APK either:
- locally in Android Studio (Build > Build Bundle(s)/APK(s) > Build APK(s)), or
- via a GitHub Actions workflow that runs `./gradlew assembleDebug` and
  uploads `app/build/outputs/apk/debug/app-debug.apk` as an artifact.

## What's implemented vs what needs finishing

Implemented:
- Permission checklist screen (overlay, accessibility, notifications)
- Host flow: warning screen, code generation, Firebase session, red/green
  helper-connected indicator, stop button
- Connect flow: code entry, connecting state, remote video view, tap/swipe
  forwarding over a WebRTC data channel, fullscreen toggle, leave button
- Firebase signaling (offer/answer/ICE exchange keyed by the 6-digit code)
- WebRTC screen capture (`MediaProjection`) and remote gesture execution
  (`AccessibilityService.dispatchGesture`)

Needs your attention before real use:
- The host's capture resolution is hardcoded (720x1480) in both
  `HostActivity` and `ConnectActivity` — keep those two values in sync, or
  send the real resolution over the data channel instead.
- A launcher icon is included (simple placeholder) — swap it for a real one
  whenever you want via Android Studio's Image Asset tool.
- Testing on real devices — accessibility gesture dispatch and
  `MediaProjection` behavior vary across Android versions and OEM skins.

## What changed in this round

- **Fixed a real crash:** screen capture was starting at nearly the same
  instant as its required foreground service, which crashed on some devices
  right after the capture permission was granted. The service now starts
  first and is given a moment to settle.
- **Fixed video not streaming:** the connector was calling `createAnswer()`
  immediately after `setRemoteOffer()`, without waiting for it to actually
  finish (it's asynchronous). ICE candidates are now also queued until the
  remote description is confirmed set, instead of being dropped if they
  arrived too early.
- **Realme/Vivo/OEM reliability:** added a "Background reliability" card to
  the permission screen that requests battery-optimization exemption —
  ColorOS/FuntouchOS and similar skins aggressively kill background apps by
  default, which can cut sessions short. Also added a note to the
  Accessibility permission card about the "Allow restricted settings" step
  Android 13+ requires for sideloaded (non-Play-Store) apps.
- **New GUI:** refined dark palette, system `sans-serif-black`/`sans-serif-medium`
  typography throughout, gradient backgrounds, card ripples, smoother
  scale+fade activity transitions, a live-updating permission checklist
  (polls every second instead of only on resume), and an ambient rotating
  ring anchored to the home screen's bottom-right corner.
- **New Settings screen** (gear icon, top-right of home): Restart app (wipes
  cache, relaunches fresh), Force stop (ends any session and kills the app's
  own process — the real equivalent of Android's Force Stop), and
  Administration (opens the developer's Instagram).
- **New corner Stop button** (top-left of home) — same as Force stop, one
  tap away without opening Settings.
- **On "automatic permission granting":** Android does not allow any app to
  flip its own permission toggles — that's an OS security boundary, not a
  missing feature here. What's implemented instead is real: the permission
  screen now checks every toggle every second and routes you straight to
  the right settings page the moment something's off.
