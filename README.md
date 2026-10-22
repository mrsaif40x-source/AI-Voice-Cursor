# AI Voice Cursor

**An offline, on-device, voice-controlled AI agent for Android.**

Speak in Bengali or English; the app understands the command locally, reads the current screen through the
Accessibility tree, picks a target, and performs the action through an `AccessibilityService` — after validation
and safety checks.

```
Voice → Offline STT (Vosk) → Local Gemma (MediaPipe LLM Inference) → JSON action plan
      → Parser → Schema validator → Safety checker → Accessibility tree → AccessibilityService action
```

## Features
- Bengali/English voice commands, fully offline when models are on the device (the app declares **no INTERNET permission**).
- Controlled output: the LLM may only emit the JSON schema below; anything invalid is discarded.
- Node-based actions first (click, long click, set text, scroll, focus); coordinate gestures only as fallback.
- Vision fallback abstraction (`ScreenVisionEngine`) when the target is not in the accessibility tree.
- Highlight overlay on the selected target; on-screen confirmation for sensitive actions.
- Local command history (Room). Nothing is sent to any server.
- Rule-based Bengali/English parser as a deterministic fallback when the model is missing or its output is rejected.

Supported actions: `CLICK, LONG_CLICK, TYPE, SCROLL_UP, SCROLL_DOWN, SWIPE, BACK, HOME, OPEN_APP, OPEN_URL, FOCUS, WAIT, READ_SCREEN, SCREENSHOT, SEARCH_UI, CONFIRM`

```json
{ "actions": [ {"type": "CLICK", "target": "Search"}, {"type": "TYPE", "text": "Python tutorial"} ] }
```
Extra fields: `SWIPE` uses `direction` (UP/DOWN/LEFT/RIGHT), `WAIT` uses `ms` (≤ 5000), `OPEN_URL` uses `text` (http/https only), `OPEN_APP` uses `target`.

## Architecture
Single Gradle module (`app`), organised by package under `com.aivoicecursor`:

| Package | Responsibility |
|---|---|
| `core` | Action models, `ActionJsonParser`, `ActionValidator`, `SafetyChecker`, `TargetMatcher`, `ScreenSummarizer`, `VoiceCommandParser`, `AgentPipeline` |
| `ai` | `LocalLLMEngine` interface, `MediaPipeGemmaEngine`, `PromptBuilder`, `ActionPlanner` |
| `voice` | `OfflineSpeechRecognizer` interface, `VoskSpeechRecognizer`, `VoiceAgentService` (foreground, microphone) |
| `accessibility` | `VoiceCursorService`, `ScreenReader`, `ActionExecutor`, `OverlayController` |
| `vision` | `ScreenVisionEngine` interface, `NoVisionEngine` (default, see Limitations) |
| `data` | Room database + `HistoryRepository` |
| `ui` | Jetpack Compose main screen, ViewModel |

Models/runtimes are behind interfaces so the backend can be swapped.

## Requirements
- Android 8.0+ (API 26). Screenshots need Android 11+ (API 30).
- Android Studio (recent), JDK 17, Gradle 8.10+ (or Android Studio's bundled Gradle).
- A device with enough free RAM/storage for the model (see below).

## Model setup (Gemma)
Model files are **never committed** (`.gitignore` excludes `*.gguf`, `*.bin`, `*.safetensors`, `*.task`, `*.litertlm`).

1. Download Gemma 4 E2B-it only from an official source (Google / Kaggle / Hugging Face model page) after accepting its license.
2. Required format: a file loadable by the **MediaPipe LLM Inference** runtime (typically a `.task` or `.litertlm` bundle, also `.bin`). The generic `.gguf` / `.safetensors` files are **not** loadable by this runtime; add another `LocalLLMEngine` implementation if you need them. Check the model page for the compatible format.
3. Copy the file to the device folder shown in the app ("Model folder"), normally:
   `/sdcard/Android/data/com.aivoicecursor/files/models/`
   ```
   adb push gemma-e2b-it.task /sdcard/Android/data/com.aivoicecursor/files/models/
   ```
4. Open the app and tap **Load model**. Loading is lazy, runs off the UI thread, and the model is unloaded automatically when the system signals memory pressure (or via **Unload model**).
5. Without a model file the screen shows **Model status: Not installed**. Simple commands still work through the rule-based parser.

Rough requirements (estimates, check the model card): ~2–4 GB free storage and ~3–4 GB free RAM for a ~2B-effective-parameter quantised model.

## Offline setup (speech)
Download a Vosk model for your language from the Vosk model list, unzip it so its contents sit directly in
`/sdcard/Android/data/com.aivoicecursor/files/vosk-model/` (use `adb push`). Without it you can still use the in-app text command box.
Note: Vosk's model availability for Bengali is limited; check the list. The recogniser is behind `OfflineSpeechRecognizer`, so Whisper or another engine can be added.

## Accessibility setup
Settings → Accessibility → **AI Voice Cursor** → enable. On Android 13+ for sideloaded APKs you may first need
App info → ⋮ → *Allow restricted settings*. The main screen shows whether the service is connected.

## Build instructions
```
gradle wrapper          # once, if you want gradlew
./gradlew testDebugUnitTest assembleDebug
```
The APK is in `app/build/outputs/apk/debug/`. Opening the folder in Android Studio also works.

## GitHub Actions
`.github/workflows/android.yml` runs on every push and pull request: checkout, JDK 17, Gradle setup, unit tests, debug build, and uploads the debug APK (and test reports) as artifacts.

## Permissions
| Permission | Why |
|---|---|
| `RECORD_AUDIO` | microphone for offline speech recognition |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | listening while another app is in front, with a visible notification |
| `POST_NOTIFICATIONS` | show that notification (Android 13+) |
| Accessibility service (user-enabled) | read UI tree, perform actions, gestures, screenshots |
| *(no INTERNET)* | the app cannot reach the network |

## Security
- Pipeline: AI output → JSON parser → schema validator → safety checker → executor. Invalid JSON, unknown action types, malformed fields or non-http(s) URLs are never executed.
- Sensitive labels (pay, buy, delete, send, uninstall, transfer, logout, … and Bengali equivalents) require on-screen confirmation, checked both on the requested target and on the element actually resolved on screen. Screenshots require confirmation. Confirmation times out as "denied".
- Typing into password fields or into targets named password/PIN/OTP/CVV is refused.
- Listening starts only when you press Start and shows an ongoing notification with a Stop button. Commands run only from that session or from the in-app text box.
- History stays in the local Room database; the app has no network permission.

## Limitations
- The Accessibility service must be enabled manually; Google Play restricts apps that use it, so a Play release needs a policy review.
- Apps can hide content (`FLAG_SECURE`, password fields, banking apps) and some UIs (games, canvas/Flutter/WebView) expose poor accessibility trees.
- `NoVisionEngine` is the default: **no vision model is bundled**, so the vision fallback reports "not found" until you provide a `ScreenVisionEngine` implementation.
- Whether Gemma 4 E2B-it runs depends on your file being compatible with the bundled MediaPipe runtime version; this repo does not ship or verify any model.
- Bengali recognition quality depends on the speech model you install. Small on-device LLMs make mistakes; that is why validation and confirmation exist.
- The Gradle wrapper jar is not included; use `gradle wrapper` or Android Studio.
- Acting on a screen while this app is in the foreground acts on this app itself.

## Troubleshooting
- *"Accessibility service is not enabled"* → enable it, then retry.
- *"Model not installed"* → check the exact folder path shown in the app and the file extension.
- *Model fails to load* → the file is not compatible with the MediaPipe version, or RAM is too low.
- *"Speech model not installed"* → unzip the Vosk model contents directly into `vosk-model/`.
- Click does nothing → try `read screen` to see what the app exposes; the target label must match text, hint, description or id.

## Future roadmap
Whisper engine, real vision model for the fallback, richer app-name matching (Bengali names), per-app allow lists, instrumented UI tests, a floating always-on cursor.

## License
MIT — see `LICENSE`.
