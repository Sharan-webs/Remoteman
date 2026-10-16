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
- A launcher icon (see above).
- Testing on real devices — accessibility gesture dispatch and
  `MediaProjection` behavior vary across Android versions and OEM skins.
