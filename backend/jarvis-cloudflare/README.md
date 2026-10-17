# Jarvis online — setup a costo zero (Cloudflare Worker + Gemini API)

Questa cartella contiene un proxy HTTPS per il client già presente nell'app. Il proxy tiene la chiave Gemini sul server, non nell'APK.

## Prima di iniziare: niente addebiti

1. Apri Google AI Studio: https://aistudio.google.com/ e crea una Gemini API key.
2. Controlla che il modello scelto sia disponibile nel Free Tier del tuo account e **non attivare un piano a pagamento né collegare la fatturazione**. I limiti gratuiti e i modelli disponibili possono cambiare.
3. Nel piano gratuito Google può usare i contenuti inviati per migliorare i prodotti: non inviare dati sensibili o posizioni personali. Il proxy non invia coordinate GPS, ma il testo che l'utente dice a Jarvis viene inoltrato al provider.

## Pubblicare il proxy gratuito

1. Crea un account Cloudflare e apri Workers & Pages.
2. Dal tuo PC/Codespace, installa Node.js e Wrangler, poi dalla cartella `backend/jarvis-cloudflare` esegui:
   ```sh
   npm install -g wrangler
   wrangler login
   wrangler secret put GEMINI_API_KEY
   ```
   Incolla la chiave **solo nel prompt privato di Wrangler**, mai in chat, commit o file.
3. Pubblica:
   ```sh
   wrangler deploy
   ```
   L'URL sarà simile a `https://next-navi-pro-jarvis.<tuo-account>.workers.dev`.
4. Verifica aprendo `https://<URL-worker>/health`: deve rispondere con JSON e `"ok":true`.
5. L'endpoint da usare nell'app è `https://<URL-worker>/jarvis`.

Il piano gratuito di Cloudflare Workers include un limite di richieste giornaliere; il proxy applica inoltre un limite best-effort per IP. Il limite per IP non è una garanzia globale. La protezione principale contro gli addebiti è **non attivare la fatturazione Gemini** e restare su modelli/quote del Free Tier. Se la quota gratuita finisce, Jarvis online smette temporaneamente di rispondere; i comandi offline dell'app restano disponibili.

## Collegare il proxy alla build Android

Il progetto legge l'endpoint dalla variabile d'ambiente `NEXT_NAVI_AI_ENDPOINT` durante la compilazione (`app/build.gradle`). Nelle impostazioni del repository GitHub aggiungi una Actions variable chiamata `NEXT_NAVI_AI_ENDPOINT` con valore completo `https://...workers.dev/jarvis`. Poi avvia una nuova build GitHub Actions.

Se il workflow non importa automaticamente le repository Actions variables come variabili d'ambiente, occorre aggiungere al job di build:
```yaml
env:
  NEXT_NAVI_AI_ENDPOINT: ${{ vars.NEXT_NAVI_AI_ENDPOINT }}
```
Non aggiungere la Gemini API key a GitHub Actions: deve rimanere solo come secret del Worker.

## Contratto e limiti

- Il proxy accetta `POST /jarvis` con `{"message":"...","language":"it-IT","app":"NEXT NAVI PRO"}` e restituisce `{"reply":"..."}`, come si aspetta il client Android.
- `GET /health` serve solo per controllare che il Worker sia pubblicato.
- Non fornisce navigazione, GPS, meteo o limiti stradali: è solo una chat vocale.
- Il limite in memoria per IP è best-effort e può azzerarsi quando Cloudflare cambia istanza. Non è un sistema di autenticazione.
- Prima di distribuire l'app ad altri utenti servirà un controllo anti-abuso più robusto. Non usare questa prima versione per informazioni di sicurezza stradale.
