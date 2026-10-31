# NEXT NAVI PRO — Agent Engineering Rules

## Mission
Build a reliable, motorcycle-first Android navigator with correct road context, timely guidance, conservative speed-limit handling, smooth GPS presentation, and field-testable behavior. Keep car mode compatible.

## Boundaries
- Work only in the assigned feature branch.
- Render is the backend authority. Do not add Cloudflare or restore HERE.
- Prefer configured TomTom speed-limit data. Unknown or ambiguous limits remain unknown.
- Never merge, deploy Render, publish a release, or claim an APK is ready without explicit user approval and verified evidence.
- Never force-push, rewrite shared branches, or modify approved dashboard layouts without approval.

## Engineering protocol
1. Inspect current branch, relevant source, tests, open PR dependencies, and CI before editing.
2. Record evidence, hypothesis, invariants, failure modes, and acceptance criteria.
3. Prefer small isolated changes; avoid unrelated refactors and avoid editing MainActivity unless no safer seam exists.
4. Add deterministic unit tests and regression tests for reported failures.
5. Separate raw GNSS, filtered presentation position, map matching, route progress, speed intelligence, voice scheduling, and recording.
6. Treat fix age, accuracy, monotonic time, speed, bearing, match confidence, and route continuity as first-class inputs.
7. Never let an LLM make an unverified safety-critical routing or legal-speed decision.
8. TTS requires explicit priority, freshness, cancellation, and completion semantics.
9. Preserve GPX/CSV timestamp and coordinate consistency.
10. Distinguish CI success from on-device and road validation.

## Collaboration
- Each specialist owns a bounded domain and reports evidence, assumptions, proposed changes, API/data contracts, tests, risks, and rollback.
- Do not let specialists edit the same shared file concurrently.
- Navigation Architect owns integration and resolves conflicts.
- QA/Safety can block changes for correctness or evidence gaps.
- High autonomy allows investigation, branch-local edits, and tests; it does not authorize merge, deploy, or release.

## Definition of done
Acceptance criteria met; regression and edge-case tests added; exact candidate commit CI green; no unresolved high-severity findings; claims accurately distinguish CI from physical validation.
