# Bantay: Task List

**Oras:** Oct 9, 9:05 PM hanggang Oct 10, 5:00 AM. Deadline 10:00 AM.  
**Prinsipyo:** Local AI ang puso ng system. Tatlong developer ang magtutulungan sa tatlong lane, may kanya-kanyang pananagutan, at may malinaw na gate bago lumipat sa susunod na phase.

---

## Hatian ng Gawain sa Team (3 Developers)

Paalala: Pag-uusapan pa ng team ang eksaktong toka sa bawat lane.

| Lane | Saklaw | Status |
|---|---|---|
| **Lane A: AI Core** | Gemma 3 1B model, LiteRtEngine, Prompts, at Pipelines | Priority (Backend / AI) |
| **Lane B: Android Services & Rules** | RuleFilter, Notification Listener, Accessibility, Speaker (TTS) | Priority (Backend / AI) |
| **Lane C: App Shell at UI** | Overlay UI, Bubble View, SetupActivity Screen | ON HOLD (Hinihintay ang UI/UX Design) |

> Tandaan sa Device:
> Universal Android: Pwedeng Virtual Device (Android Emulator) o kahit anong Physical Android Phone (Samsung, Xiaomi, Pixel, Oppo, Vivo, Motorola, atbp.).
> Kapag Emulator ang gamit, gamitin ang `adb emu sms send` para sa mga SMS test. Kapag physical phone, parehong APK at standard Android APIs ang tatakbo.
> 
> Paalala sa Diskarte: 
> Dahil ginagawa pa ang UI/UX sa ngayon, huwag munang gumawa ng kahit anong UI views o screens (Lane C) para walang masayang na layout code.
> Ang pokus muna ay ang Lane A (Gemma model at LiteRT) at Lane B (Rules at Unit Tests) na gumagana nang walang UI.

---

## Phase 0: Foundation & Local AI Setup (9:05 – 10:15 PM)

Gate, 10:15 PM: Sumasagot ang Gemma 3 1B at gumagana ang RuleFilter unit tests.

### Bago Magsimula: Mobile at Device Setup (~15 min)

Pumili kung Virtual Device o Physical Device ang gagamitin:

#### Paraan A: Kung Virtual Device (Android Emulator) ang gamit
- [ ] **M1-V. AVD Creation at Specs Check** (~5 min)
  - Buksan ang Android Studio -> Tools -> Device Manager.
  - Pumili o gumawa ng virtual device na may Google Play Store (mahalaga para may built-in Google TTS).
  - Siguraduhing may internal storage na hindi bababa sa 4 GB para magkasya ang 550 MB model file.
- [ ] **M2-V. I-launch at i-verify ang Emulator** (~3 min)
  - I-start ang AVD mula sa Android Studio o via terminal.
  - Patunayan sa terminal: `adb devices` (dapat may lumabas na `emulator-5554 device`).
- [ ] **M3-V. Offline Filipino Voice Pack (Google TTS sa Emulator)** (~5 min)
  - Sa emulator screen: Pumunta sa Settings -> Accessibility o System -> Text-to-speech.
  - Siguraduhing Google Speech Recognition and Synthesis ang engine at i-download ang Filipino (Philippines) voice pack.
- [ ] **M4-V. SMS Simulation Verification** (~2 min)
  - Subukan magpadala ng test text sa terminal:
    `adb emu sms send 09951234567 "Test SMS"`
  - O gamitin ang Emulator Extended Controls (...) -> Phone -> Send SMS.

#### Paraan B: Kung Physical Android Phone ang gamit
- [ ] **M1-P. Developer Options at USB Debugging** (~5 min)
  - Sa phone: Pumunta sa Settings -> About phone -> Software information.
  - I-tap ang Build number nang 7 beses para lumabas ang Developer options.
  - Sa Developer options: I-ON ang USB Debugging.
  - Isaksak via USB cable sa laptop at piliin ang "Always allow from this computer".
- [ ] **M2-P. ADB Verification** (~2 min)
  - Patunayan sa terminal: `adb devices` (dapat may lumabas na alphanumeric device ID).
- [ ] **M3-P. Offline Filipino Voice Pack (Google TTS sa Phone)** (~5 min)
  - Pumunta sa Settings -> General Management o Accessibility -> Text-to-speech.
  - Piliin ang Google TTS engine at i-download ang Filipino (Philippines) voice data.

#### Karaniwang Hakbang para sa Parehong Paraan (Virtual o Physical)
- [ ] **M5. Gumawa ng Storage Folder para sa Model** (~2 min)
  - I-run sa terminal: `adb shell mkdir -p /data/local/tmp/llm/`
- [ ] **M6. Paalala sa Restricted Settings (Android 13 pataas)** (~2 min)
  - Kapag na-install na ang APK: Pumunta sa Settings -> Apps -> Bantay -> Tatlong tuldok sa taas -> Allow Restricted Settings para ma-enable ang Notification Access at Accessibility Service.

### Lane C: App Shell at UI (ON HOLD - Hinihintay ang UI/UX)
- [x] **C1. Core Contracts & Data Models** (~15 min) · Pwedeng ilatag kahit walang UI
  - LlmEngine interface, data classes (RuleResult, ScamVerdict, ScreenContext), at FakeEngine.
- [ ] *(Deferred)* **C2. Manifest at Services Setup** · Gagawin kapag may baseline layout na
- [ ] *(Deferred)* **C3. Babala Card Overlay Views** · Gagawin kapag tapos na ang Figma/UI design

### Lane A: AI Core (Backend Dev 1)
- [x] **A2. I-download ang Gemma 3 1B `.litertlm` Model** (~15 min)
  - Hugging Face: tanggapin ang Gemma license sa `litert-community/Gemma3-1B-IT`.
  - I-download ang int4 `.litertlm` file sa laptop (~550 MB).
- [x] **A3. `LiteRtEngine` Implementation** (~40 min) · Kailangan: A2, C1
  - Idagdag ang dependency `com.google.ai.edge.litertlm:litertlm-android`.
  - Ipatupad ang `LiteRtEngine : LlmEngine` na may `Mutex` at CPU backend.
  - I-push ang model file: `adb push <model>.litertlm /data/local/tmp/llm/`.
- [x] **A4. Smoke Test at Version Pinning** (~15 min) · Kailangan: A3
  - Sukatin ang load time at inference latency. I-pin ang LiteRT version sa Gradle.
  - Resulta (Oct 9, Infinix X6835B, Android 13, 8 GB RAM, CPU backend): load 15.4 s (cold); generate 7.6 s, 5.3 s, 5.2 s.
  - Naka-pin: `litertlm-android` 0.18.0 sa `gradle/libs.versions.toml`.
  - Paalala: mali ang sagot sa 2 sa 3 raw prompt (walang few-shot); kailangan ang prompt template ng B1 at ang RuleFilter.
  - Few-shot prompt (~450 token): 48–50 s kada sagot at mali ang hatol sa 2 scam (kinopya ang mga halimbawa). Kailangang maikli ang prompt sa A5 para pumasok sa 15 s timeout.

### Lane B: Android Services & Rules (Backend Dev 2)
- [ ] **B1. I-validate ang Scam Prompt Template** (~25 min)
  - Gamitin ang test prompt mula sa `SYSTEM_DESIGN.md` na may few-shot examples.
  - Siguraduhing konsistent ang format: `HATOL`, `DAHILAN`, `GAWIN`.
- [ ] **B2. Gumawa ng 30 Test Messages (Ground Truth)** (~20 min)
  - 15 scam at 15 ligtas na Tagalog/Taglish messages na may tamang sagot.
  - Ilagay sa isang JSON o Kotlin test file para magamit sa automated tests.
- [ ] **B3. `RuleFilter` at `ScamParser` na may Unit Tests** (~30 min) · Kailangan: C1
  - Pure Kotlin JVM unit tests (hindi kailangan ng emulator/phone).
  - 6 signals (hingi ng pera, bagong number, link, premyo, OTP, apura) at 3 tiers (0, 1, 2+).

---

## Phase 1: Scam Alert End-to-End (10:15 PM – 1:00 AM)

**Gate, 1:00 AM:** Kapag may pumasok na scam SMS o notification, agad lumalabas ang babala at binabasa nang malakas via TTS.

### Lane A: AI Core (Backend Dev 1)
- [x] **A5. `PromptBuilder` at `ScamPipeline`** (~60 min) · Kailangan: A3, B3
  - Tatlong tiers ayon sa score, 15-segundong timeout, at automatic fallback sa rules kapag `FAILED` ang engine.
- [ ] **A6. Patakbuhin ang Test Set** (~45 min) · Kailangan: A5, B2
  - Sukatin ang accuracy laban sa 30 test messages. Itala ang tunay na resulta para sa pitch.

### Lane B: Android Services (Backend Dev 2)
- [ ] **B4. `BantayNotificationListener` Implementation** (~60 min) · Kailangan: C2
  - Universal allowlist: Google Messages (`com.google.android.apps.messaging`), Samsung Messages (`com.samsung.android.messaging`), AOSP SMS (`com.android.mms`, `com.android.messaging`), Messenger (`com.facebook.orca`), Viber (`com.viber.voip`), WhatsApp (`com.whatsapp`).
  - Kunin ang `EXTRA_TITLE` at `EXTRA_BIG_TEXT` / `EXTRA_TEXT`.
  - Dedupe sa huling 50 notification hashes.
- [ ] **B5. `Speaker`: Offline TextToSpeech fil-PH** (~30 min)
  - I-setup ang Android `TextToSpeech` gamit ang `Locale("fil", "PH")`.
  - Idagdag ang `<queries>` intent para sa `TTS_SERVICE` sa `AndroidManifest.xml`.

### Lane C: App Shell at UI (Fullstack / Frontend Dev)
- [ ] **C4. Final Babala Card Overlay** (~50 min) · Kailangan: C3
  - Mabilis na paglabas (Score 2+ generic text), tapos asynchronous update kapag natapos ang LLM.
  - Buttons: [Basahin], [Sige po], at [Tawagan si Apo].
- [ ] **C5. `Prefs` at Direct Call Action** (~20 min)
  - SharedPreferences para sa number ni Apo; `ACTION_DIAL` intent kapag pinindot ang tawag.

### Lahat (Team Integration)
- [ ] **X1. 12:45 AM: End-to-End Test Run** (~25 min) · Kailangan: A5, B4, B5, C4
  - Mag-send ng simulated scam text via ADB:
    ```bash
    adb emu sms send 09951234567 "Ma si Junjun to bagong number ko padala ka 5k gcash"
    ```
  - I-verify: Notification -> Rule -> Overlay pop-up -> Text-to-Speech -> AI explanation update.

---

## Phase 2: Gabay sa Phone (1:00 – 3:30 AM)

**Gate, 3:30 AM:** Pag-tap sa bubble, nababasa ang kasalukuyang screen labels at nagbibigay ng maikling gabay si Bantay.

### Lane B: Android Services (Backend Dev 2)
- [ ] **B6. `ScreenContextReader`** (~40 min) · Kailangan: C2 · **UNAHIN sa Phase 2**
  - Kunin ang `rootInActiveWindow`, lakarin ang tree, limitahan sa 30 labels o 600 characters.
  - I-filter ang empty o invisible nodes.
- [ ] **B7. Bumalik at Home Actions** (~15 min)
  - `performGlobalAction(GLOBAL_ACTION_BACK)` at `GLOBAL_ACTION_HOME`.

### Lane A: AI Core (Backend Dev 1)
- [x] **A7. I-tune ang Gabay Prompt gamit ang Screen Labels** (~40 min) · Kailangan: B6
  - Limitahan sa 3 simpleng hakbang, magalang (may "po"), at bawal magbanggit ng buttons na wala sa screen.
  - Resulta (Oct 10, Infinix X6835B, `GabayPipelineEvalTest`, 6 na screen na may labels):
    - Buong Tagalog na hakbang mula sa model: 3 sa 4 ang nag-timeout sa 20 s; ang isang sumagot ay 18.5 s at mali ang buttons.
    - Hanggang 3 pangalan ng button: 9.5–17.3 s, pero laging 3 ang ibinibigay at pampuno lang ang ika-2 at ika-3.
    - Pinal, isang button lang (ang susunod na pipindutin): 6/6 ang sumagot, avg 6.1 s, max 9.6 s. Tama ang 4 (Voice call, Sound & vibration, Photo, Facebook), puwede na ang 1 (Keypad sa "tumawag sa anak ko"), mali ang 1 ("Message" sa "mag-send ng picture").
  - Ang model ay pumipili lang ng button; ang pangungusap na may "po" ay galing sa code (`GabayPipeline`), at `GabayParser` ang nagtatanggal ng button na wala sa screen.
- [x] **A8. `GabayPipeline`** (~45 min) · Kailangan: A7
  - Fixed fallback kapag walang labels (hal. banking app): *"Hindi ko po makita ang screen na ito"*.
  - 20-segundong timeout na nag-aalok na tawagan si Apo.

### Lane C: App Shell at UI (Fullstack / Frontend Dev)
- [ ] **C6. Floating Bubble at Gabay Panel** (~60 min) · Kailangan: C3
  - Floating chathead icon gamit ang `TYPE_ACCESSIBILITY_OVERLAY`.
  - Panel na may quick buttons (*"Paano mag-send ng picture?"*, *"Bumalik sa Facebook"*), Bumalik, at Home.
- [ ] **C7. Ikabit ang Panel sa `GabayPipeline` at `Speaker`** (~30 min) · Kailangan: C6, A8, B5

### Lahat (Team Integration)
- [ ] **X2. 3:15 AM: Gabay End-to-End Test** (~15 min) · Kailangan: C7, B7
  - Test sa Messenger o Settings screen gamit ang emulator.

---

## Phase 3: Polish, Submission, at Video (3:30 – 5:00 AM)

**Gate, 5:00 AM:** Kumpleto ang submission sa portal (Public GitHub repo, README, at 1-min demo video). Buffer hanggang 10:00 AM deadline.

### Lane A: AI Core (Backend Dev 1)
- [ ] **A9. Huling Accuracy Run at Benchmark Table** (~30 min) · Kailangan: A6
  - Itala ang tunay na accuracy (walang imbento) para sa README at pitch presentation.

### Lane B: Android Services (Backend Dev 2)
- [ ] **B8. `SetupActivity` (Apo Onboarding Screen)** (~45 min)
  - Simple form: Pangalan at Number ni Apo.
  - Status indicators para sa Notification Access at Accessibility Service.
  - "I-test ang Bantay" button na tumatawag sa `ScamPipeline.check()`.

### Lane C: App Shell at UI (Fullstack / Frontend Dev)
- [ ] **C8. README.md at Submission Disclosures** (~35 min)
  - Arkitektura, LiteRT-LM framework, zero internet explanation, at accuracy table.
- [ ] **C9. 1-Minute Demo Video Recording** (~40 min) · Kailangan: X1, X2
  - I-record ang screen ng emulator gamit ang simulated SMS scam at Gabay.

### Lahat (Final Check)
- [ ] **X3. 4:45 AM: Submission Checklist Verification** (~15 min)
  - GitHub repo ay Public.
  - Zero cloud API keys na naka-expose (100% local).
  - Submit sa portal bago mag-5:00 AM.
