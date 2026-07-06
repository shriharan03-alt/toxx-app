# TOXX — AI Phone Assistant (Android)

TOXX reads incoming notifications, summarizes them via an AI backend, drafts replies to texts,
and can place calls on command. This is a **starter scaffold** — not production-hardened —
meant to be opened directly in Android Studio.

## What's included
- `app/` — Android app module (Kotlin)
  - `NotificationListener.kt` — captures all incoming notifications
  - `MainActivity.kt` — permission requests + simple dashboard UI
  - `CallManager.kt` — places outgoing calls
  - `BackendClient.kt` — talks to your backend for AI summarization/drafting
  - `AndroidManifest.xml` — declares all required permissions/services
- `backend/` — minimal Node.js server that calls the Claude API to summarize
  notifications and draft replies

## Setup
1. Open `TOXX-app/` in Android Studio (Arctic Fox or later).
2. Let Gradle sync — it will pull dependencies automatically.
3. Run `backend/` locally (`npm install && npm start`) or deploy it, then set its
   URL in `BackendClient.kt` (`BASE_URL`).
4. Install the app on a device (emulators can't fully test notification/call permissions).
5. On first launch, the app will prompt you to:
   - Enable Notification Access (Settings → Special app access → Notification access)
   - Grant `CALL_PHONE` permission
   - Optionally set TOXX as your default SMS/dialer app (required for auto-answer,
     call screening, and reading SMS content — Android restricts these to default
     handlers only)

## Important constraints (read before you build further)
- **Google Play policy**: apps requesting SMS/Call Log permissions are rejected
  unless the app is a *default handler* for SMS or Phone. If you plan to publish
  this, you'll need to implement the full default-app requirements (see
  `android.telecom` / `android.provider.Telephony` docs) or sideload for personal use.
- **iOS is not supported** — Apple does not expose notification-listener,
  call-handling, or SMS-reading APIs to third-party apps. There is no equivalent
  build possible there.
- **Auto-sending texts or auto-answering calls without confirmation** is technically
  possible here but is a policy and safety tradeoff — the scaffold defaults to
  "draft and confirm" rather than fully autonomous action. Flip `AUTO_SEND` in
  `BackendClient.kt` only if you understand the implications for your own contacts'
  privacy and consent.
- This app only reads/acts on data on **your own device, with your own permission
  grants** — it has no ability to access anyone else's phone or accounts.

## Voice control (added)
- **Wake phrase: "wake up toxx"** — `WakeWordService.kt` runs in the foreground
  and loops Android's built-in `SpeechRecognizer` to listen for it. On match,
  it speaks an acknowledgment ("Yes?"), listens once for your command, sends
  the transcript to `POST /voice-command` on the backend, and speaks back
  whatever Claude returns.
- This is a **restart-loop approximation** of always-on listening, not a true
  low-power wake-word engine. It's fine for prototyping but uses more battery
  and can occasionally miss the phrase or lag between cycles. For a
  production-grade experience, swap in **Porcupine** (or a similar wake-word
  SDK) and call the same `onWakeWordDetected()` hook in `WakeWordService.kt`.
- Push-to-talk only (no wake word) is also available via `VoiceController.kt`
  directly, if you'd rather trigger listening from a button press.
- Voice commands that resolve to `action: "call"` now resolve the spoken
  name against your device contacts via `ContactResolver.kt` (requires
  `READ_CONTACTS`, granted via the same permissions button). It tries an
  exact match first, then a partial match (so "call john" matches "John
  Smith"). If nothing matches and the spoken target looks like it's already
  a phone number, it dials that directly. If neither works, TOXX says it
  couldn't find that contact instead of guessing.
- Toggle listening from the dashboard button ("Start Listening for 'Wake up
  TOXX'"), after granting `RECORD_AUDIO` via the call-permissions button.

## Next steps you'll likely want
- Persistent foreground service so Android doesn't kill the listener
- Local Room database to store notification/message history
- Push notifications from backend → app for proactive digests
- Voice command layer (separate, larger effort)
