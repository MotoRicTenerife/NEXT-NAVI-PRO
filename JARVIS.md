# Jarvis for NEXT NAVI PRO

## Current implementation

- Android-native Kotlin integration in the existing `MainActivity`; no project reset or new navigation engine.
- First-run name prompt; name is stored only in local app preferences.
- Italian greeting on launch using the existing TextToSpeech engine.
- User-initiated push-to-talk via the dashboard microphone button. There is no wake word and no background microphone service.
- Offline-first command parser: destination search, saved home, GPS fix accuracy, repeat next instruction, remaining route metrics, silence/help, and showing pending search results.
- Destination search continues to use the app's existing geocoder and route engine. Jarvis does not invent coordinates, roads, speed limits, ETA, or maneuvers.
- Voice destination results are not opened as a selection dialog while the app cannot confirm the vehicle is stopped. Free-form online AI requests are also blocked unless GPS reports speed at or below 2.5 m/s.
- The feminine Italian TTS voice is selected by known voice-name hints when the installed TTS engine exposes one. Android does not provide a portable voice-gender flag, so the actual voice still depends on installed Italian TTS voices.

## Optional online AI gateway

The offline commands work without a backend. Free-form requests can use a configured HTTPS backend; provider API keys must remain on the server and must never be embedded in the APK.

Set the GitHub Actions **repository variable** `NEXT_NAVI_AI_ENDPOINT` to an HTTPS endpoint implementing:

- Request: `POST application/json` with `{"message":"...","language":"it-IT","app":"NEXT NAVI PRO"}`
- Response: HTTP 2xx with `{"reply":"..."}`

The app sends only the recognized text, not GPS coordinates, route geometry, saved home, or the user's name. It treats the reply as conversation only; it cannot change navigation state. Requests time out quickly and fall back to offline commands. If the variable is unset, no online request is made.

## Persistent APK signing

The current workflow can use a stable keystore without committing it to Git. Configure these **Actions secrets**:

- `NEXT_NAVI_KEYSTORE_BASE64`: base64-encoded keystore file
- `NEXT_NAVI_KEYSTORE_PASSWORD`
- `NEXT_NAVI_KEY_ALIAS`
- `NEXT_NAVI_KEY_PASSWORD`

If they are absent, CI deliberately falls back to Android's generated debug signing key and warns that the APK signature can change between runs. Do not commit a keystore or its passwords to the repository. Keep a secure backup of the keystore: losing it means future APKs signed with a different key cannot update an installed copy in place.
