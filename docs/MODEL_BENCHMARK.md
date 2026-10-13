# On-Device Model: Specs & Benchmark Log

Reference doc for Bantai's local AI. Update the **Benchmark log** table every time we
re-run the on-device eval so we can compare across dates, devices, prompts, and models.

## Model spec — Gemma 3 1B (int4, LiteRT-LM)

| Property | Value |
|---|---|
| Model | Gemma 3 1B, instruction-tuned (`-it`) |
| Parameters | ~1B (dense, decoder-only transformer) |
| Quantization | int4 (per-channel) in `.litertlm` format |
| File on device | `gemma3-1b-it-int4.litertlm` — **584 MB** |
| Context window | 32K tokens |
| Vocab | 262K tokens, ~140 languages — Tagalog/Filipino supported natively |
| Reasoning/thinking mode | **None** — Gemma is not a reasoning model; no think/reason tokens to configure |
| Backend | LiteRT-LM `Backend.CPU()` (see `LiteRtEngine.kt`) |
| Runtime settings | `maxOutputTokens` = 48, `topK` = 10, `topP` = 0.9, `temperature` = 0.2 |
| RAM gate | App requires ≥5.5 GB total RAM before loading (`Bantai.kt`) |
| Source | `litert-community` (Apache-2.0) |

**Tunable knobs, ranked by measured impact:**
1. `maxOutputTokens` — dominant latency driver. ~5 tokens/s decode on the Infinix;
   ~35-token answers ≈ 7–8 s, ~5-token answers ≈ 2.4 s.
2. Prompt input length — matters much less than output length (prefill is fast).
3. `temperature` — keep low (0.2) for consistent `VERDICT:` formatting.

**Not available on this model:** reasoning/thinking mode (no think tokens), GPU/NPU
backend tuning (we run CPU only), fine-tuning (needs a real dataset + conversion
pipeline; not worth it with our ~38-case eval set).

## Test device

| | |
|---|---|
| Phone | Infinix X6811 |
| SoC | Helio G35 (entry-level) — mid-range phones will be faster |
| Android | 11 (API 30) |
| RAM | ~7.8 GB total |

## How to run the benchmark

Model must be on the phone: `adb push gemma3-1b-it-int4.litertlm /data/local/tmp/llm/`

```bash
# Model-only eval over TestDataset (accuracy + per-message latency):
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bantai.pipeline.ScamPipelineEvalTest#evaluateModelOnly
adb logcat -d -s BantayEval:I   # SUMMARY[llm] line + per-message ms=

# Quick 8-case latency probe (one-word verdicts):
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.bantai.probe.ModelProbe \
  -Pandroid.testInstrumentationRunnerArguments.model=/data/local/tmp/llm/gemma3-1b-it-int4.litertlm
adb logcat -d -s ModelProbe:I
```

Tip: `adb logcat -G 16M` first so the buffer doesn't wrap mid-run.

## Benchmark log (newest first)

| Timestamp | Model | Prompt | Test | Accuracy | Latency (avg / max) | Load | Notes |
|---|---|---|---|---|---|---|---|
| 2026-10-11 19:30 | Gemma 3 1B int4 | **Binary + sender + signals (current)** — 2 examples: scam (unknown number), safe (official sender receipt) | `evaluateModelOnly`, 38/38 completed | **30/38 — 18/18 scams caught, 0 FN, 8 FP** | 9,302 ms / 12,775 ms | 1.6 s (warm) | FPs: 3 are dataset artifacts (contact-money cases marked safe but eval runs `senderIsContact=false`); ~3 genuine FPs on Filipino family chat. No crash on full run |
| 2026-10-11 17:19 | Gemma 3 1B int4 | Binary + sender + signals | `evaluateModelOnly`, ~23 cases before crash | 15/15 scams, 1 FP on partial run | 13,187 ms / 22,269 ms | 7.8 s | Same prompt as above but on a hot phone (user was gaming) — thermal throttling roughly +4 s/inference. Confirms run-to-run variance |
| 2026-10-11 17:11 | Gemma 3 1B int4 | 3-example few-shot with SUSPICIOUS verdict option | `evaluateModelOnly`, 29 cases before crash | ~4/29 usable (25/29 answered SUSPICIOUS incl. 14 of 15 scams) | 11,751 ms / 17,398 ms | 9.6 s | **Regression**: the middle tier became a cop-out — model hedges everything |
| 2026-10-11 16:10 | Gemma 3 1B int4 | Scheme-list prompt (11 schemes, no examples) | `evaluateModelOnly`, 33 cases before crash | ~15/33 — **17 false positives** | 9,162 ms / 12,447 ms | — | **Regression**: primed model marked nearly everything SCAM. Process crashed at msg 33 (native LiteRT crash after ~32 inferences — watch for memory accumulation) |
| 2026-10-11 ~15:00 | Gemma 3 1B int4 | One-word verdict probe | `ModelProbe`, 8 cases | 6/8 | 2,396 ms / 3,077 ms | 7.7 s | Proves output length dominates latency |
| 2026-10-11 ~15:00 | Qwen3.5-2B int8 | One-word verdict probe | `ModelProbe`, 8 cases | **3/8** | 5,576 ms | **158.6 s** | Garbage explanations (echoed input). **Removed from app** |
| 2026-10-11 14:34 | Gemma 3 1B int4 | Old few-shot (2 examples, VERDICT+REASON) | `evaluateModelOnly`, 30 cases | **27/30** (3 FP, 0 FN) | 8,241 ms / 12,438 ms | 7.7 s | Baseline. FPs: casual family chat msgs |

## Lessons learned (do not regress)

- **Few-shot examples > instruction lists** for a 1B model. The scheme-list prompt scored
  17 false positives — listing schemes primed the model to see scams everywhere.
  Keep 2 compact examples (SCAM / SAFE).
- **Do NOT offer SUSPICIOUS as a model verdict.** Given a hedge option, Gemma 1B picks it
  for nearly everything (25/29). SUSPICIOUS stays an app-layer verdict (contact money
  requests, cautious fallback), decided by rules — never asked of the model.
- **Native crash risk**: the eval process died ~30 consecutive inferences in, twice.
  If it recurs in production use, suspect LiteRT memory accumulation — consider
  `engine.close()` + reload after N messages, or recreating the Conversation pool.
- **Scheme taxonomy lives in `ScamSchemes.kt`** but is NOT injected into the prompt —
  it documents scam patterns and can feed rules-as-retriever later.
- **Output tokens drive latency, not input.** If the 5 s target isn't met, cut
  `maxOutputTokens` or do verdict-only first pass, not a shorter prompt.
- Qwen3.5-2B int8 is unusable on this class of device (158 s load, 3/8 accuracy).
