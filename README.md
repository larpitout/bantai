# Bantai - Hindi na pauuto

Text scams like fake "bagong number" relatives asking for money, OTP phishing, and bogus parcel fees commonly target Filipinos, especially seniors. Bantai is an Android app that screens incoming SMS and chat messages for scams and warns the user inside the chat app. The check runs on the phone with an on-device LLM (Qwen3.5-2B).

## Run It Locally (for Judges)

No servers, accounts, or API keys. The only download is the model file.

### Prerequisites
- Android Studio, or JDK 17+ (a full JDK, not just a JRE) plus Android SDK Platform 35
- An Android 8.0+ phone (arm64), recommended over an emulator for notification and accessibility features
- About 2.2 GB of free storage on the phone for the Qwen model
- **At least 6,000 MB of RAM as reported by Android** to run Qwen3.5-2B. With less, the app runs rules + link checks only, without the AI model.

### 1. Clone
```bash
git clone https://github.com/larpitout/bantai.git
cd bantai
```

### 2. Download the model
Download `Qwen3.5-2B_int8.litertlm` (2,116,592,816 bytes, Apache-2.0) from [`litert-community/Qwen3.5-2B`](https://huggingface.co/litert-community/Qwen3.5-2B) on Hugging Face.

> Phones reporting under 6,000 MB RAM won't load Qwen. They use Gemma 3 1B if its file is present, otherwise rules-only.

### 3. Put the model on the phone (debug build)
```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push Qwen3.5-2B_int8.litertlm /data/local/tmp/llm/
```

*Alternative, release build:* put the file in `models/` at the repo root and run `./gradlew assembleRelease`. The release build bundles `models/` into the APK, so the APK is about 2.1 GB larger.

### 4. Build and install
```bash
./gradlew installDebug
```

### 5. Grant permissions (the Setup screen walks you through it)
- **Notification access**: to screen incoming messages
- **Accessibility (messaging apps only)**: to show the warning when the flagged chat is opened
- **Display over other apps**: to draw the warning
- Optional: **Contacts** (marks money requests from saved contacts as Suspicious, with a Call button), **Battery optimization exemption** (keeps Bantai running on aggressive OEMs), and **Caller ID & spam app** (call warnings)

> **Android 13+ sideloaded apps:** if a permission is greyed out, go to **Settings → Apps → Bantai → ⋮ → Allow restricted settings**.

To confirm which model loaded: `adb logcat -s Bantai` should show `Model: Qwen3.5-2B_int8.litertlm`, followed by `READY`.

### 6. Try it
- **No second phone needed (debug build):**
  ```bash
  adb shell cmd notification post -t "GCash" "BantaiTest" "URGENT: GCash account locked, verify at gcash-verify.xyz"
  ```
- Or send yourself an SMS like: `Ma bagong number ko to, padala ka 5k sa gcash emergency lang`
- Then open the message in your chat app to see the warning, or paste the text in the **Check** tab.

