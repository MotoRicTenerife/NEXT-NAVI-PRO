# Jarvis for NEXT NAVI PRO

## Current implementation

- Android-native Kotlin integration in the existing `MainActivity`; no project reset or new navigation engine.
- First-run name prompt; the chosen assistant name is stored in local app preferences and used by the microphone button, speech-recognition prompt, local identity answers, and online assistant system instructions.
- Italian greeting on launch using the existing TextToSpeech engine.
- User-initiated push-to-talk via the dashboard microphone button. There is no wake word and no background microphone service.
- Offline-first command parser: destination search, saved home, GPS fix accuracy, repeat next instruction, remaining route metrics, silence/help, and showing pending search results.
- Destination search continues to use the app's existing geocoder and route engine. Jarvis does not invent coordinates, roads, speed limits, ETA, or maneuvers.
- Spoken navigation commands are parsed locally and can search/select a destination, cancel an active route, or request a destination change. Online AI replies are conversational only and cannot alter navigation state. Ambiguous place results must be shown to the rider rather than silently selecting an unrelated destination.
- The feminine Italian TTS voice is selected by known voice-name hints when the installed TTS engine exposes one. Android does not provide a portable voice-gender flag, so the actual voice still depends on installed Italian TTS voices.

## Optional online AI gateway

The offline commands work without a backend. Free-form requests can use a configured HTTPS backend; provider API keys must remain on the server and must never be embedded in the APK.

Set the GitHub Actions **repository variable** `NEXT_NAVI_AI_ENDPOINT` to an HTTPS endpoint implementing:

- Request: `POST application/json` with `{"message":"...","language":"it-IT","app":"NEXT NAVI PRO"}`
- Response: HTTP 2xx with `{"reply":"..."}`

The production Android CI build uses the Render service `https://next-navi-pro-jarvis.onrender.com/jarvis`. The app sends recognized text, language, and assistant name for conversational requests; it includes the current GPS coordinates only for live weather/traffic questions. It does not send route geometry or saved home. Replies are conversational only and cannot change navigation state. Local builds need `NEXT_NAVI_AI_ENDPOINT` set in the build environment to enable the online layer; offline navigation commands remain available without it.

## Persistent APK signing

The current workflow can use a stable keystore without committing it to Git. Configure these **Actions secrets**:

- `NEXT_NAVI_KEYSTORE_BASE64`: base64-encoded keystore file
- `NEXT_NAVI_KEYSTORE_PASSWORD`
- `NEXT_NAVI_KEY_ALIAS`
- `NEXT_NAVI_KEY_PASSWORD`

If they are absent, CI deliberately falls back to Android's generated debug signing key and warns that the APK signature can change between runs. Do not commit a keystore or its passwords to the repository. Keep a secure backup of the keystore: losing it means future APKs signed with a different key cannot update an installed copy in place.
