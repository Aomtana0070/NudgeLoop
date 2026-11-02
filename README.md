# NudgeLoop

NudgeLoop repeats reminders for notifications that are still waiting in Android's notification shade. It works on-device: no account, message forwarding, or network service.

## UX decision brief

- **Job:** help a person notice an important notification they did not act on.
- **User mode:** personal, occasional setup and daily background use.
- **Pattern:** guided permission setup, then a four-destination mobile navigation bar: Home, Apps, Rules, Guide.
- **Primary action:** enable a reminder rule for an app.
- **Core path:** grant notification access and reminder access -> choose Instagram or another app -> select one or more senders from on-device title suggestions -> choose an interval and optional repeat limit -> leave the original notification pending.
- **Recovery:** each missing permission has a direct settings link; rule edits are saved immediately; reminders stop when the original notification is removed or a repeat alert is dismissed.
- **Privacy:** rules, pending reminder metadata, and a capped per-app list of notification titles used for sender suggestions stay in private app storage. Message previews are hidden by default.

## Task ergonomics contract

- **Core task:** set up a repeat reminder for one app or a named sender.
- **Frequency/risk:** daily utility; notification access is sensitive and is requested only after the app explains why it is needed.
- **Success:** the user can identify whether access is active and can create the first rule without leaving the app's guide.
- **Error prevention:** safe default of 5 minutes and 3 repeats; unlimited reminders are opt-in and stop when the source notification is removed or the user taps stop; exact alarm access is requested explicitly; sender matching uses the notification title exposed by the source app.
- **Recovery:** when access is missing, show the blocked capability and a settings action; when access is revoked, stop scheduling and show the status.
- **Evidence plan:** emulator walkthrough for setup, app toggle, sender match, repeat interval, stop behavior, and notification permission denial.

## Platform behavior to know

- Android requires the user to grant notification-listener access in system settings. This is what lets the app observe other apps' posted and removed notifications.
- Android 13 and later requires notification permission before NudgeLoop can post reminders.
- NudgeLoop lists launchable apps using Android package visibility queries; it does not request blanket access to every installed package.
- Exact alarms require the separate “Alarms & reminders” access on current Android versions. Without it the app falls back to an inexact alarm; on Android 12+, inexact alarms may be delayed by up to an hour, and Doze, battery saver, or manufacturer battery controls can delay them further.
- AlarmManager is intended to run while the app UI is closed or swiped away. Android force-stop blocks alarms until the app is opened again; some manufacturers also require setting NudgeLoop battery use to unrestricted/background allowed. The Guide links to app settings for these controls.
- When notification access and NudgeLoop notifications are allowed, opening the app starts an ongoing foreground keep-alive service. It shows a persistent low-priority notification, keeps the app process at foreground-service importance, and retries the notification-listener rebind if disconnected. On Redmi/Xiaomi devices, also set NudgeLoop battery use to No restrictions and enable Background autostart when those options are available. Android force-stop and manufacturer policies can still stop services; no app can guarantee an uninterrupted listener connection.
- The Guide's diagnostics section shows listener/permission state, listener connect/disconnect times, the latest source notification and rule-check result, alarm scheduling and receipt, reminder posting, pending items, and the latest delivery problem. Diagnostic history stores timestamps, app package names, and rule outcomes only, not notification text.
- After a device restart or app update, NudgeLoop reconnects to notification access and restores reminders for source notifications that are still active. Open NudgeLoop after restarting the device to start the foreground keep-alive service; Android may delay listener reconnection.
- Saving an enabled app rule refreshes already-active notifications, so notifications that arrived before the rule was enabled can be picked up without waiting for another message.
- Opening or returning to NudgeLoop rechecks the notification listener and scans currently active notifications. Diagnostic logs identify listener connections and rule-match outcomes without logging notification content.
- Active-notification refresh updates the same source-key state used by alarm delivery, preventing reminders from being rejected because the listener's cached key set was stale.
- If Android temporarily blocks posting a reminder, NudgeLoop keeps the item pending and retries after 15 minutes instead of leaving it permanently unscheduled.
- Check that Android allows notifications for NudgeLoop and that at least one NudgeLoop reminder channel is enabled. The Guide screen reports blocked app notifications and links directly to the system settings.
- Android only exposes the title and text a source app includes in its notification. Sender suggestions are learned from notification titles observed while access is enabled, stored on-device (up to 100 titles for each of the 40 most recently seen apps), and can be selected as multiple OR-matched filters. The editor shows recently observed suggestions and searches within the full title. Sender filtering works when Instagram includes the sender in that visible notification; message previews remain hidden in NudgeLoop by default.
- Unlimited repeat rules keep scheduling one reminder per interval while the original notification remains active. Stop the repeat from its notification or dismiss the source notification; Android battery policies may delay reminders.
- Reminder notifications are posted by NudgeLoop (Android does not let one app impersonate another app's package). NudgeLoop uses the sender as the title and names the source app in the notification text so it is easier to recognize.
- GitHub release APKs are sideloaded outside Google Play and may be warned about by Google Play Protect because notification access can reveal sensitive notification content. Do not disable Play Protect to install them. A Play Store release requires a production signing key, a privacy policy, and Google Play review for notification-listener access.
- Removing the original notification or dismissing NudgeLoop's repeat alert stops future repeats. The app never deletes the source notification.

## Build and run without Android Studio

1. Install JDK 17, Android SDK Platform 37, Android SDK Build Tools 36, and an Android 15+ emulator image.
2. From this folder, run `gradlew.bat testDebugUnitTest assembleDebug :sample-sender:assembleDebug`.
3. Install `app/build/outputs/apk/debug/app-debug.apk` and `sample-sender/build/outputs/apk/debug/sample-sender-debug.apk` on an emulator or device. Start an emulator from the command line with `emulator -avd <avd-name>` if needed.
4. In NudgeLoop, open **คู่มือ** and enable Notification access, notifications, and (for close interval timing) Alarms & reminders.
5. In NudgeLoop, create a rule for **NudgeLoop Test Sender** with sender `Nok` or `Mali`, interval 1 minute, and 2 repeats. Grant the sender app its notification permission when asked.
6. In the test sender, tap **ส่งจาก Nok**; NudgeLoop should learn `Nok` as a sender suggestion and schedule a repeat after about a minute. Add both `Nok` and `Mali` to try multiple senders, then enable unlimited repeats to verify it continues until stopped. Clear the source notification or tap stop to cancel the reminder.
7. For an Instagram check, install Instagram in a Google Play emulator image, enable Instagram message notifications, add an Instagram rule, and leave a test DM notification in the shade. The emulator needs an Instagram account to exercise Instagram's notification format.
8. **คู่มือ → ทดลองเตือน** checks the reminder scheduler, notification channel, and stop action without an Instagram account. Enable Alarms & reminders so a one-minute test is not delayed by Android.

For a command-line build, run `gradlew.bat testDebugUnitTest assembleDebug :sample-sender:assembleDebug` from this directory. The APKs are written to `app/build/outputs/apk/debug/app-debug.apk` and `sample-sender/build/outputs/apk/debug/sample-sender-debug.apk`.

## Signed GitHub or Google Play release

Release builds use a private upload key. Keep the keystore and passwords outside this repository; never commit them. Do not disable Play Protect to install a sideloaded APK.

1. Set `NUDGELOOP_UPLOAD_STORE_FILE`, `NUDGELOOP_UPLOAD_STORE_PASSWORD`, `NUDGELOOP_UPLOAD_KEY_ALIAS`, and `NUDGELOOP_UPLOAD_KEY_PASSWORD` in the build environment. Gradle refuses to produce an unsigned release.
2. Run `gradlew.bat testDebugUnitTest assembleRelease` for a signed APK at `app/build/outputs/apk/release/app-release.apk` to attach to a GitHub Release.
3. For Google Play, run `gradlew.bat testDebugUnitTest bundleRelease` and upload `app/build/outputs/bundle/release/app-release.aab` to an internal testing track.
4. Complete Play Console's notification-access declaration and provide a public privacy policy before requesting review. Signing alone does not bypass Play Protect or guarantee approval.
