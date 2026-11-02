# NudgeLoop

NudgeLoop repeats reminders for notifications that are still waiting in Android's notification shade. It works on-device: no account, message forwarding, or network service.

## UX decision brief

- **Job:** help a person notice an important notification they did not act on.
- **User mode:** personal, occasional setup and daily background use.
- **Pattern:** guided permission setup, then a four-destination mobile navigation bar: Home, Apps, Rules, Guide.
- **Primary action:** enable a reminder rule for an app.
- **Core path:** grant notification access and reminder access -> choose Instagram or another app -> optionally match a sender -> choose interval and repeat limit -> leave the original notification pending.
- **Recovery:** each missing permission has a direct settings link; rule edits are saved immediately; reminders stop when the original notification is removed or a repeat alert is dismissed.
- **Privacy:** rules and pending reminder metadata stay in private app storage. Message previews are hidden by default.

## Task ergonomics contract

- **Core task:** set up a repeat reminder for one app or a named sender.
- **Frequency/risk:** daily utility; notification access is sensitive and is requested only after the app explains why it is needed.
- **Success:** the user can identify whether access is active and can create the first rule without leaving the app's guide.
- **Error prevention:** safe default of 5 minutes and 3 repeats; exact alarm access is requested explicitly; sender matching uses the notification title exposed by the source app.
- **Recovery:** when access is missing, show the blocked capability and a settings action; when access is revoked, stop scheduling and show the status.
- **Evidence plan:** emulator walkthrough for setup, app toggle, sender match, repeat interval, stop behavior, and notification permission denial.

## Platform behavior to know

- Android requires the user to grant notification-listener access in system settings. This is what lets the app observe other apps' posted and removed notifications.
- Android 13 and later requires notification permission before NudgeLoop can post reminders.
- NudgeLoop lists launchable apps using Android package visibility queries; it does not request blanket access to every installed package.
- Exact alarms require the separate “Alarms & reminders” access on current Android versions. Without it the app falls back to an inexact alarm; Doze, battery saver, and manufacturer battery controls can delay a repeat.
- After a device restart or app update, NudgeLoop reconnects to notification access and restores reminders for source notifications that are still active. Android may delay this reconnection; open NudgeLoop if reminders do not resume.
- Android only exposes the title and text a source app includes in its notification. Sender filtering works when Instagram includes the sender in that visible notification; message previews remain hidden in NudgeLoop by default.
- GitHub preview APKs are sideloaded debug builds and may be blocked or warned about by Google Play Protect because notification access can reveal sensitive notification content. Do not disable Play Protect to install them. A public Play Store release requires a production signing key, a privacy policy, and Google Play review for notification-listener access.
- Removing the original notification or dismissing NudgeLoop's repeat alert stops future repeats. The app never deletes the source notification.

## Build and run on a computer

1. Install a recent Android Studio with Android SDK Platform 37, Android SDK Build Tools 36, and an Android 15+ emulator image.
2. Open this folder in Android Studio and let Gradle sync.
3. Create/start an emulator in Device Manager and press **Run** for the `app` configuration.
4. In NudgeLoop, open **คู่มือ** and enable Notification access, notifications, and (for close interval timing) Alarms & reminders.
5. Build and run the `sample-sender` app configuration as a second app in the emulator. In NudgeLoop, create a rule for **NudgeLoop Test Sender** with sender `Nok`, interval 1 minute, and 2 repeats. Grant its own notification permission when asked.
6. In the test sender, tap **ส่งจาก Nok**; NudgeLoop should schedule and show a repeat after about a minute. Tap **ส่งจาก Mali**; the sender rule should ignore it. Clear the source notification and verify remaining repeats stop.
7. For an Instagram check, install Instagram in a Google Play emulator image, enable Instagram message notifications, add an Instagram rule, and leave a test DM notification in the shade. The emulator needs an Instagram account to exercise Instagram's notification format.
8. **คู่มือ → ทดลองเตือน** checks the reminder scheduler, notification channel, and stop action without an Instagram account. Enable Alarms & reminders so a one-minute test is not delayed by Android.
9. Run `gradlew.bat testDebugUnitTest` for the sender-matching unit checks. Build the main app with `gradlew.bat assembleDebug` and the test sender with `gradlew.bat :sample-sender:assembleDebug`.

For a command-line build, run `gradlew.bat testDebugUnitTest assembleDebug :sample-sender:assembleDebug` from this directory. The APKs are written to `app/build/outputs/apk/debug/app-debug.apk` and `sample-sender/build/outputs/apk/debug/sample-sender-debug.apk`.

## Google Play release

The GitHub APKs are debug-signed test builds and are not suitable for Google Play. A Play release must use a private upload key, be uploaded as an Android App Bundle, and pass Google Play's review for notification-listener access. Do not disable Play Protect to install a sideloaded APK.

1. In Android Studio, use **Build > Generate Signed Bundle / APK > Android App Bundle** to create a dedicated upload key outside this repository. Back up the key and its passwords securely; never commit them.
2. Set `NUDGELOOP_UPLOAD_STORE_FILE`, `NUDGELOOP_UPLOAD_STORE_PASSWORD`, `NUDGELOOP_UPLOAD_KEY_ALIAS`, and `NUDGELOOP_UPLOAD_KEY_PASSWORD` in the build environment. The Gradle release build refuses to produce an unsigned release.
3. Run `gradlew.bat testDebugUnitTest bundleRelease`. Upload `app/build/outputs/bundle/release/app-release.aab` to the Play Console internal testing track first.
4. Complete Play Console's notification-access declaration and provide a public privacy policy before requesting review. A signed bundle does not bypass Play Protect or guarantee approval; distribution through Google Play is the supported route for this app.
