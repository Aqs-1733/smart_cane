# Ground-probe pose gate — 2026-08-13

## Complete snapshots

- Before: `backup/pre-ground-probe-gate-20260813`
  (`73b27a92b8bb23a0a5109ce8e2ab6f71e0044ab8`)
- After: `backup/post-ground-probe-gate-20260813`
  (created with the commit for this change)

Both tags are full-repository Git snapshots, not partial firmware copies.

## Exact minimal change

| Location | Before | After |
| --- | --- | --- |
| `config.h` | Only broad normal-use gate: pitch/roll within `18 degrees`. | Adds a separate probe-pose gate: pitch/roll within `5 degrees` of the learned normal probing angle. |
| `risk_logic.cpp` motion/step gate | A stopped cane could meet the broad normal-use gate and begin the 250 ms + two-sample step confirmation after its angle changed. | A changed inclination clears directional evidence and cannot start/confirm a ground event until the cane returns to the 5-degree probe-pose gate. |
| `risk_logic.cpp` front beam behavior | Sweep suppression only applied while `caneMotion` was true. | A changed probe angle also keeps temporary ground suppression, preventing the front ToF from interpreting the floor as a front obstacle. |

## Explicitly unchanged

- Step thresholds: up `9 cm`, down `50 cm`, deep drop `70 cm`.
- Existing wait/confirmation: `250 ms` stable plus two fresh same-direction
  samples.
- Fall candidate and formal-fall logic, front/side thresholds, local cue
  sequencing, backend schema, and phone events.

## Expected behavior

- On flat ground, changing the cane inclination to touch down or raise it
  cannot make a delayed step/drop candidate, even if the new pose becomes
  still.
- At a real step, return the cane to its learned normal probing angle; the
  unchanged 250 ms + two-sample confirmation then produces the normal up/down
  event.

## Rollback

Restore the full pre-change tag `backup/pre-ground-probe-gate-20260813`, or
revert the post-change commit.  Do not manually edit thresholds to roll back.
