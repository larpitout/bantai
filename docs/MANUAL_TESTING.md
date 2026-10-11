# Manual Test Messages

Copy-paste these into the **Check tab** (Dashboard → Check → paste → Check), or send them as
real notifications from a second phone / via `adb` (see bottom). Expected results assume the
protection toggle is ON and the model is loaded (Gemma 3 1B, ~7 s cold load on first message).

Legend: **SCAM** = red warning + notification, **SUSPICIOUS** = caution warning, **SAFE** = no alert.

## Scams — should warn

```
Ma si Junjun to bagong number ko nasira phone ko padala ka 5k sa gcash emergency lang
```
→ SCAM. Hard signal (new number + money) — **instant rule warning**, AI explanation follows.

```
Your GCash account has been suspended due to unverified details. Verify immediately at http://bit.ly/gcash-verify-sec to prevent permanent deactivation.
```
→ SCAM. Dangerous link (shortener) — instant warning.

```
Congratulations! Ang iyong cellphone number ay nanalo ng Php 750,000 mula sa Philippine Charity Sweepstakes. Upang ma-claim ang premyo mag-reply ng OTP at pangalan.
```
→ SCAM. Prize + OTP signals.

```
BDO: Your account will be locked today. Verify your details at http://bdo-verify.top/secure immediately.
```
→ SCAM. **Spoofed-sender test**: sender looks official (BDO) but link is dangerous — must still warn.

```
Ma pa-load naman 500 sa number na to, emergency lang
```
→ SCAM or SUSPICIOUS depending on sender (contact → SUSPICIOUS; stranger → AI decides).

## Safe — should NOT warn (the false-positive tests)

```
8080: Supercharge your surfing experience with Go5G TURBO 50! Enjoy faster speeds and lower latency (vs all other Globe Prepaid promos) with UNLI 5G and 3GB for all apps.
```
→ SAFE. **The Globe promo case** — official sender ID, nothing asked. In the Check tab the sender
isn't set, so this may still flag; for the real sender test use a notification (below).

```
GCash: You received P500.00 from Juan D. Your new balance is P1,250.45. Ref. No. 601234567890.
```
→ SAFE. Official receipt — nothing requested.

```
Nay kumusta po kayo diyan? Dadalaw po kami sa Sabado at magluluto ako ng sinigang.
```
→ SAFE. Normal family chat.

```
Pabatid: May libreng flu vaccination sa barangay covered court sa darating na Lunes mula 8am hanggang 12nn.
```
→ SAFE. Public advisory.

## Known false positives (as of 2026-10-11 eval)

These incorrectly flag as SCAM — useful to watch if we improve the prompt:

```
Mars pwede makahingi ng talbos ng kamote sa bakuran niyo mamaya paglabas ko?
Nay nasend ko na po yung picture ng mga bata sa Messenger pacheck na lang po kapag may time ka.
```

## Full end-to-end test (real notification, from PC)

Sends a fake notification that Bantai's listener sees (debug builds only — the shell package is
allowlisted for testing). `-t` sets the title (= the sender Bantai classifies); the last arg is the message.

Scam (should warn ~2-3 s after model warm):

```bash
~/Android/Sdk/platform-tools/adb shell \
  "cmd notification post -S bigtext -t 'GCASH' 'test' 'Your GCash account has been suspended. Verify now at http://bit.ly/fake-verify'"
```

Safe promo from an official sender (should NOT warn; appears in Alerts → SAFE):

```bash
~/Android/Sdk/platform-tools/adb shell \
  "cmd notification post -S bigtext -t '8080' 'test' 'Supercharge your surfing experience with Go5G TURBO 50! Enjoy faster speeds and lower latency (vs all other Globe Prepaid promos) with UNLI 5G and 3GB for all apps.'"
```

Spoofed official sender with dangerous link (must still warn — official IDs can be spoofed):

```bash
~/Android/Sdk/platform-tools/adb shell \
  "cmd notification post -S bigtext -t 'BDO' 'test' 'Your account will be locked today. Verify your details at http://bdo-verify.top/secure immediately.'"
```

Family money request (SUSPICIOUS if sender is in contacts):

```bash
~/Android/Sdk/platform-tools/adb shell \
  "cmd notification post -S bigtext -t 'Junjun' 'test' 'Ma pautang po 2k pang-load ko lang, bayad ko sa sweldo. Padala sa gcash ko.'"
```

Syntax is `cmd notification post [flags] <tag> <text>` — `-t` sets the notification **title**
(which Bantai reads as the sender, so `-t 'GCASH'` simulates an official sender ID) and the
last argument is the message body. In the app: watch for the notification + the overlay when
you open the "chat", and check Flagged messages / history for the verdict + AI explanation.

## Quick checklist

- [ ] Scam text → red SCAM alert appears within ~10 s (model cold: add ~8 s for first load)
- [ ] 8080-style promo → no alert
- [ ] Pausing protection → confirm dialog appears, then alerts stop entirely (test with the adb notification)
- [ ] Call-warnings row on/off → opens the right settings screen
- [ ] AI-may-err disclaimer visible at the bottom of the warning overlay and notification
