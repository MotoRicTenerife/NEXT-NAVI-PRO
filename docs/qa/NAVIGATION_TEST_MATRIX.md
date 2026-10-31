# Navigation Regression Test Matrix

This matrix is the acceptance contract for the specialist team. A row without an executable fixture is a known coverage gap, not a passed test.

| Domain | Scenario / failure injection | Expected invariant | Automated evidence | Field evidence |
|---|---|---|---|---|
| GNSS | stale fix, age >10 s | reject for live guidance; never move marker backward | unit/replay | GPS loss/recovery |
| GNSS | accuracy degradation / multipath | lower confidence; no false route jump | synthetic trace | urban canyon |
| GNSS | stationary / walking speed | stable heading; no random bearing rotation | replay | stop-and-go |
| GNSS | impossible displacement / speed | reject outlier; preserve diagnostic reason | unit/replay | controlled route |
| Map match | parallel carriageways | retain ambiguity until evidence resolves it | synthetic geometry | TF-1 parallel roads |
| Map match | junction / hairpin / U-turn | topology and heading prevent premature segment jump | replay | Tenerife junctions |
| Routing | roundabout entry/circulation/exit | one stable maneuver identity; correct exit; no repeated prompts | state-machine tests | several roundabouts |
| Routing | missed turn / route rejoin | reroute once after confidence/time threshold; no oscillation | replay | deliberate deviation |
| Routing | destination passed | arrival closes guidance once, no stale next turn | replay | real destination |
| Voice | multiple updates while speaking | no sentence truncation; stale stage dropped/coalesced | scheduler tests | Cardo Freecom 4x |
| Voice | Jarvis response + navigation prompt | explicit priority; no overlapping speech | scheduler tests | intercom ride |
| Voice | TTS error/stop/lifecycle restart | queue recovers; no stuck speaking state | unit tests | app background/foreground |
| Speed | TomTom timeout/no match/stale result | display unknown, never fabricated numeric limit | mocked contract tests | coverage gaps |
| Speed | direction mismatch / road change | do not reuse opposite-direction limit | contract/replay | divided roads |
| Speed | overspeed threshold crossing | first voice warning; visual flash policy; bounded repeat | policy tests | safe controlled check |
| Telemetry | GPX/CSV same ride | monotonic timestamps and matching accepted raw fixes | parser comparison | paired ride recording |
| Telemetry | stop recording without navigation | both files close and archive correctly | unit/integration | recording-only ride |
| Telemetry | app process interruption | recoverable/valid partial files; no silent corruption | fault injection | forced app restart |
| Android | background/foreground, permission revocation | no crash; GPS/TTS resources released/restarted | instrumentation where available | S22 Ultra |
| Android | long ride / thermal / battery | bounded CPU, memory, wake locks and network | profiler/emulator | 60–120 min ride |
| Favorites | saved Casa/POI lifecycle | list, select, navigate, rename/delete, persistence are coherent | repository/UI tests | device UI |
| UI | portrait/landscape S22 Ultra | approved layout unchanged; no clipping/overlap | screenshot tests where available | actual device |
| Backend | Render health / TomTom configuration | health and configuration observable; no secrets in logs | safe GET smoke | production status |
| Security | untrusted prompts / malformed inputs | bounded input, no secret leakage, safe error handling | static/unit tests | manual review |

## Release gates
- No unresolved critical/high defect in GPS integrity, maneuver selection, legal speed-limit provenance, TTS arbitration, or recording integrity.
- All deterministic tests pass on the exact candidate commit.
- APK signature matches the already-installed debug identity before an update is offered.
- Device tests pass on the Galaxy S22 Ultra / Android 16.
- Road test checklist is completed and attached with GPX/CSV evidence.
- Known limitations are documented; a green workflow alone is not a release approval.
