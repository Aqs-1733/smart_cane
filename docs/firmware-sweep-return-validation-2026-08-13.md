# Ambiguous drop sweep-return validation — 2026-08-13

## Complete snapshots

- Before: `backup/pre-sweep-return-validation-20260813`
  (`db808f14a1bc5479181fb4c2f25a4717f20b7e6a`)
- After: `backup/post-sweep-return-validation-20260813`
  (created with this commit)

Both references are complete-repository Git snapshots. Use the pre-change tag
or revert the post-change commit for rollback; do not reconstruct snippets.

## Before / after

| Location | Before | After |
| --- | --- | --- |
| Down-step candidate | After a moving sample was discarded, a stopped but raised cane could re-enter the normal `250 ms + 2 sample` down-step path. | Only a down-range change (`>=50 cm`) that began while cane motion and pose deviation were `>=8 degrees` is marked ambiguous. |
| Ambiguous path | No distinction between a real lower floor and a flat-ground raise/point-down sweep. | No motion sample is counted. If range returns before confirmation, the candidate is cancelled. If the posture naturally returns (even during the reverse swing) and range remains far, normal confirmation resumes. |
| Latency safeguard | Not applicable. | At most `600 ms` of extra waiting for an ambiguous sweep; if there is no return observation, firmware resumes the original path rather than withholding a genuine hazard. |
| Other paths | Existing behavior. | Unchanged: up steps, stable-pose down steps, thresholds, fall detection, sides/front, cue upload, and startup cue guard. |

## Unchanged thresholds and confirmation

- Up step `9 cm`; down step `50 cm`; deep drop `70 cm`.
- Existing `250 ms` normal-pose settle and two fresh same-direction samples.
- Fall candidate and formal-fall sequence.

## Expected behavior

When the user changes cane inclination to touch/raise it on flat ground, the
temporary increase in down range originated in a sweep. The firmware waits only
for that sweep to return: recovered range cancels the candidate; a persistent
lower floor resumes the original alert path. A lower floor detected directly
from a stable normal cane posture does not enter this validation window.
