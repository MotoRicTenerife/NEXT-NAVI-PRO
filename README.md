# NEXT NAVI PRO — 0.2.0-alpha02

Progetto Android nativo per la prima base installabile dell'interfaccia NEXT NAVI PRO. Package: `com.nextnavipro.app`.

## Stato reale della versione alpha02

Questa revisione introduce una struttura Android Gradle effettiva e una prima schermata di guida con velocità GPS reale. **Non è ancora un navigatore completo e non va considerata una versione stabile.**

### Implementato nel codice attuale
- Progetto Android nativo Kotlin, minSdk 26, JDK 17, Android SDK 35.
- Dashboard ad alto contrasto NEXT NAVI PRO, con tachimetro digitale.
- Lettura della velocità dal provider di localizzazione Android; esclusione dei fix più vecchi di 10 secondi o con accuratezza peggiore di 50 metri.
- Soglie cromatiche iniziali: bianco sotto 50 km/h, giallo da 50 a meno di 120 km/h, rosso da 120 km/h.
- Pulsanti iniziali per modalità Moto/Auto e tema Giorno/Notte.
- Gestione del permesso di localizzazione e layout che si adatta all'orientamento.
- Workflow GitHub Actions per compilazione, test Android e pubblicazione dell'APK Debug come artifact.

### Non ancora implementato / da verificare
- Splash screen animata e logo grafico definitivo.
- Mappa navigabile, ricerca destinazioni, routing, ricalcolo, istruzioni turn-by-turn e voce.
- Limiti di velocità provenienti da dati stradali reali.
- Mappe e routing offline, meteo, curve, registro viaggi e GPX.
- Persistenza delle impostazioni e soglie configurabili.
- Test su dispositivo fisico e verifica di fluidità, consumo e affidabilità GPS.

Queste funzioni non devono essere considerate disponibili finché non saranno aggiunte e verificate. Il tachimetro GPS è un supporto informativo e non sostituisce il tachimetro del veicolo né la segnaletica stradale.

## Compilazione locale

Richiede JDK 17 e Android SDK Platform 35 / Build Tools 35.0.0.

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest
```

## Ottenere l'APK tramite GitHub Actions

1. Apri la scheda **Actions** del repository.
2. Seleziona **Android CI** e apri l'esecuzione associata all'ultimo commit.
3. Attendi il completamento del job; se fallisce, apri il log del passaggio rosso prima di installare qualsiasi APK.
4. Se il job termina correttamente, scarica l'artifact `next-navi-pro-debug-apk`.
5. Trasferisci l'APK sul dispositivo Android e autorizza l'installazione dalla sorgente scelta, se richiesto.

L'APK Debug è destinato esclusivamente alle prove. La build non è firmata per la distribuzione pubblica.

## Prove consigliate

Prima prova da fermo: installazione, avvio, permessi, tema e orientamento. Successivamente confrontare la velocità GPS con il veicolo in condizioni sicure, con il telefono fissato e senza interagire con esso durante la guida.
