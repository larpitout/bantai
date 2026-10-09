# Bantay: Task List

**Oras:** Oct 9, 8:40 PM hanggang Oct 10, 5:00 AM. Deadline 10:00 AM.
**Prinsipyo:** Local AI ang una dahil ito ang puso ng system. Tatlong lane ang sabay tumatakbo sa bawat phase, at may isang gate ang bawat phase bago lumipat.

## Mga lane

| Lane | Saklaw |
|---|---|
| **A: AI core** | Gemma sa A54, engine, prompts, pipelines. Ito ang may hawak ng A54. |
| **B: Android services** | Rules, notification listener, accessibility, TTS, setup screen. |
| **C: App shell at UI** | Project, kontrata, overlay, bubble, README, at video. |

> **Isang A54 lang ang meron.** LLM lang ang nangangailangan nito, kaya nasa Lane A ang phone hanggang 10 PM. Sa ibang Android phone o emulator muna ang services at overlay, gamit ang `FakeEngine`.
>
> Kung dalawa lang kayo, pagsamahin ang B at C. Kung mag-isa, sundin ang A, tapos B, tapos C sa bawat phase.

Ang "Kailangan" ay mga task na dapat tapos muna. Kapag walang nakasulat, pwede nang simulan agad.

---

## Phase 0: Local AI, ang puso (8:40 – 10:00 PM)

**Gate, 10:00 PM:** sumasagot ang Gemma 3 1B sa A54 sa format na HATOL, DAHILAN, GAWIN, mula sa sarili ninyong app. Kapag hindi pa, lumipat sa MediaPipe `tasks-genai` gamit ang `.task` file ng parehong model.

### Lane A: AI core

- [ ] **A1. Subukan ang Gemma 3 1B sa AI Edge Gallery** (~15 min)
  I-install ang app sa A54, i-download ang model, i-paste ang scam prompt. Itala ang segundo ng sagot at kung maayos ang Tagalog.
- [ ] **A2. I-download ang `.litertlm` file sa laptop** (~10 min, isabay sa A1)
  Hugging Face account, i-accept ang Gemma license, kunin ang Gemma3-1B-IT mula sa `litert-community`.
- [ ] **A3. `LiteRtEngine`: sagot ng Gemma sa sariling app** (~40 min) · Kailangan: A2, C1
  Idagdag ang `litertlm-android`, `adb push` ang model, isang button na nagpapadala ng prompt at nagpapakita ng sagot.
- [ ] **A4. Sukatin at i-pin** (~15 min) · Kailangan: A3
  Itala ang load time at segundo kada scam check sa A54. I-pin ang version na na-resolve ng `latest.release`.

### Lane B: Android services (AI rin ang ginagawa rito sa Phase 0)

- [ ] **B1. I-tune ang scam prompt sa Ollama** (~30 min)
  Gamitin ang `gemma3:1b` sa laptop para kapareho ng model sa phone. Dapat laging lumabas ang HATOL, DAHILAN, GAWIN.
- [ ] **B2. Test set: 30 mensahe** (~20 min)
  15 scam at 15 ligtas, Tagalog at Taglish, may tamang sagot kada isa. Isang file na mababasa ng app at ng Ollama script.
- [ ] **B3. `RuleFilter` at `ScamParser`, may unit tests** (~30 min) · Kailangan: C1
  Pure Kotlin, tumatakbo sa JVM kaya hindi kailangan ng phone. Anim na signal, isang puntos kada tama.

### Lane C: App shell at UI

- [ ] **C1. Project, repo, at mga kontrata** (~20 min) · **UNAHIN, hinihintay ng A3 at B3**
  `LlmEngine` interface, tatlong data class, at `FakeEngine` na may `delay(2000)`.
- [ ] **C2. Manifest at dalawang walang-lamang service** (~25 min) · Kailangan: C1
  Notification listener at accessibility service, na-o-on sa Settings. I-check ang restricted settings sa Android 13 pataas.
- [ ] **C3. Babala card bilang overlay** (~35 min) · Kailangan: C2
  Malaking text, tatlong button, lumalabas sa ibabaw ng ibang app. `FakeEngine` muna ang pinagkukunan ng text.

---

## Phase 1: Scam Alert (10:00 PM – 1:00 AM)

**Gate, 1:00 AM:** totoong Messenger o SMS na scam, lumalabas ang babala at binabasa nang malakas, naka-off ang Wi-Fi at data. Kapag hindi pa, tapusin muna ito at paikliin ang Gabay sa tatlong quick button.

### Lane A: AI core

- [ ] **A5. `PromptBuilder` at `ScamPipeline`** (~60 min) · Kailangan: A3, B3
  Tatlong tier ayon sa score, 15 segundong timeout, at rules-only kapag `FAILED` ang engine.
- [ ] **A6. Patakbuhin ang test set sa A54** (~45 min) · Kailangan: A5, B2
  Itala ang tunay na accuracy at oras. Ayusin ang prompt kasama ang Lane B kung maraming mali.

### Lane B: Android services

- [ ] **B4. Notification listener** (~60 min) · Kailangan: C2
  Allowlist ng limang package, `EXTRA_BIG_TEXT`, dedupe sa huling 50, pasa sa `ScamPipeline`.
- [ ] **B5. `Speaker`: TextToSpeech fil-PH** (~30 min)
  I-download ang Filipino voice data habang may Wi-Fi. Idagdag ang `<queries>` entry para sa `TTS_SERVICE`.

### Lane C: App shell at UI

- [ ] **C4. Babala card, final** (~50 min) · Kailangan: C3
  Lumalabas agad mula sa rules, tapos napapalitan ang DAHILAN at GAWIN pagdating ng sagot ng LLM. Basahin, Tawagan si Apo, Sige po.
- [ ] **C5. `Prefs` at Tawagan si Apo** (~20 min)
  Pangalan at number sa `SharedPreferences`, `ACTION_DIAL` sa button.

### Lahat

- [ ] **X1. 12:30 AM: pagsamahin at i-test nang end to end** (~30 min) · Kailangan: A5, B4, B5, C4
  Totoong mensahe mula sa pangalawang phone, naka-off ang Wi-Fi at data sa A54.

---

## Phase 2: Gabay (1:00 – 3:30 AM)

**Gate, 3:30 AM:** pinindot ang bubble sa Messenger, nagtanong, at tinuro ng Bantay ang tamang pipindutin gamit ang mga label na nasa screen.

### Lane B: Android services (ito ang mauuna sa phase na ito)

- [ ] **B6. `ScreenContextReader`** (~40 min) · Kailangan: C2 · **UNAHIN, hinihintay ng A7**
  Hanggang 30 label o 600 character. I-dump sa Logcat ang mga label ng Messenger para magamit ng A7.
- [ ] **B7. Bumalik at Home** (~15 min)
  `performGlobalAction` mula sa accessibility service.

### Lane A: AI core

- [ ] **A7. I-tune ang Gabay prompt sa totoong labels** (~40 min) · Kailangan: B6
  Sa Ollama muna gamit ang dump ng B6. Hindi hihigit sa tatlong hakbang, at mga label na nasa screen lang ang babanggitin.
  *Habang hinihintay ang B6: ituloy ang A6 o ayusin ang scam prompt.*
- [ ] **A8. `GabayPipeline`** (~45 min) · Kailangan: A7
  Fixed na sagot kapag walang label. 20 segundong timeout na nag-aalok na tawagan si Apo.

### Lane C: App shell at UI

- [ ] **C6. Bubble at Gabay panel** (~60 min) · Kailangan: C3
  Apat na state. Text box at tatlong quick button. Basahin ang screen bago ipakita ang panel.
- [ ] **C7. Ikabit ang panel sa `GabayPipeline` at `Speaker`** (~30 min) · Kailangan: C6, A8, B5
  Sagot sa panel, tapos binabasa nang malakas kapag kumpleto.

### Lahat

- [ ] **X2. 3:10 AM: i-test ang Gabay sa Messenger at Gallery** (~20 min) · Kailangan: C7, B7
  Kasama ang isang app na humaharang sa pagbasa ng screen, para makita ang fallback.

---

## Phase 3: Submission (3:30 – 5:00 AM)

**Gate, 5:00 AM:** kumpleto ang submission checklist. Ang natitira hanggang 10 AM ay buffer at tulog.

### Lane A: AI core

- [ ] **A9. Huling accuracy run** (~30 min) · Kailangan: A6
  Tunay na numero lang ang isusulat sa README, kasama ang bilang ng mali.

### Lane B: Android services

- [ ] **B8. `SetupActivity`** (~50 min)
  Pangalan at number ni Apo, status ng bawat permission, at test button na tumatawag sa `ScamPipeline.check()`.

### Lane C: App shell at UI

- [ ] **C8. README at disclosures** (~30 min)
  Palitan ang MediaPipe ng LiteRT-LM sa listahan ng frameworks. Ilista ang ginamit na AI dev tools.
- [ ] **C9. Demo video at post** (~40 min) · Kailangan: X1, X2
  Mga isang minuto: kwento, offline, scam alert, gabay.

### Lahat

- [ ] **X3. 4:40 AM: buong demo run at submission checklist** (~20 min) · Kailangan: B8, C8, C9, A9
  Naka-off ang Wi-Fi at data, Unrestricted ang battery ng Bantay, at may charge ang A54.

---

## Ano ang tatanggalin kapag naipit

Sa ganitong pagkakasunod, mula sa unang isasakripisyo:

1. Boses bilang input ng Gabay (wala na ito sa listahan; text at quick buttons lang).
2. Ganda ng `SetupActivity` (B8): sapat na ang gumaganang form at test button.
3. Text box ng Gabay (C6): tatlong quick button na lang na may nakahandang tanong.
4. `Backend.GPU()` at anumang pagpapabilis: CPU lang.

**Huwag galawin:** A3, A5, B4, C4, at X1. Ito ang Scam Alert, at ito ang bida ng demo.
