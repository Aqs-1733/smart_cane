# Firmware tuning snapshot — 2026-08-13

> Superseded for fall-candidate sensitivity by
> `backup/pre-restore-fall-candidate-20260813`: the verified original fall
> candidate values (`>1.22g`, `<0.85g`, jerk `>2.2g/s`) were restored in a
> follow-up commit.  The step sweep filter and startup cue guard below remain.

## Complete pre-change backup

- Git tag: `backup/pre-fall-step-startup-20260813`
- Commit: `ae92ef8624573403dc90e37c55bf47c4890decbb`
- Scope: the complete repository, including the firmware, tests, backend, and
  front end.  This is a full Git snapshot, not only a copy of edited lines.

## Complete post-change backup

- Git tag: `backup/post-fall-step-startup-20260813`
- Scope: the complete repository at the committed tuned state.

Reverting must use one of these complete Git snapshots; do not manually
reconstruct individual thresholds.

## Intent and exact changes

| Location | Before | After | Reason |
| --- | --- | --- | --- |
| `config.h` fall candidate acceleration | vertical `>1.22g`, `<0.85g`; jerk `>2.2g/s` | vertical `>1.32g`, `<0.75g`; jerk `>3.0g/s` | Light hand waves no longer start a fall candidate. |
| `imu_fall.cpp` jerk condition | literal `2.2f` | named `SMARTCANE_FALL_VERTICAL_JERK_TRIGGER_G_PER_S` | Keeps the exact tuned value visible and testable. |
| `risk_logic.cpp` motion branch | Moving ToF samples were stored and could confirm while `caneMotion` was true. | Movement clears all directional evidence; it retains only a temporary front-beam suppression state, then requires a stable normal-use hold and two new same-direction samples. | A flat 10 cm cane lift cannot become a delayed down-step/drop or a false front obstacle. |
| `smartcane_arduino.ino` ordinary cue gate | The first changing ToF readings after setup could repeatedly restart a 120 ms normal tone. | Ordinary distance/ground cues are muted for 2500 ms after the initial ToF read, then the cue gate is rearmed. | Stops the boot/Wi-Fi first-alert long tone without affecting detection or formal fall alerting. |

## Explicitly unchanged

- Step thresholds: up `9 cm`, down `50 cm`, deep drop `70 cm`.
- Step confirmation: normal-use settle `250 ms`, then two samples.
- Formal fall: entry `58 degrees`, hold `40 degrees`, gyro `<22 dps`, stable
  for `2000 ms`, then one 2-second buzzer/vibration event.
- Front and side thresholds, backend schema, phone cue identifiers, and all
  fall lock/recovery rules.

## Rollback

Use Git, rather than copying snippets: either revert the post-change commit,
or restore the complete snapshot from
`backup/pre-fall-step-startup-20260813`.  The tuned snapshot is preserved as
`backup/post-fall-step-startup-20260813`.
