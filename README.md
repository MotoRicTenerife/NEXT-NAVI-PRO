# NEXT NAVI PRO — 0.2.0-alpha01

Nuovo progetto Android per prove controllate. Package: `com.nextnavipro.app`.

## Funzioni incluse in questa alpha

- Immagine di apertura originale fornita dall'utente.
- Temi POSITIVO (sfondo scuro) e NEGATIVO (sfondo chiaro), persistenti nelle impostazioni.
- Display senza mappa nella schermata principale; layout portrait/landscape secondo la specifica.
- Velocità da GPS reale con esclusione di fix obsoleti o poco accurati.
- Schermata NAVIGATORE separata con mappa vettoriale MapLibre/OpenFreeMap e attribuzione cartografica.
- Ricerca destinazioni tramite Nominatim, calcolo percorso con istruzioni OSRM, indicazioni vocali italiane, ricalcolo in caso di deviazione ed ETA stimata.
- Dati stradali OpenStreetMap persistiti in SQLite, limiti di velocità quando presenti e correttamente associati dal motore; se sconosciuti mostra `—`.
- Rilevamento preliminare delle curve da geometria OSM/percorso, avviso grafico sulla schermata principale e beep graduati.
- Meteo attuale e previsione oraria da Open-Meteo.
- Diario viaggi persistente con distanza, durata, velocità e punti GPS; esportazione GPX tramite il selettore file Android.
- Nessuna modalità DEMO e nessun dato GPS simulato.
- GitHub Actions per build Debug, test e pubblicazione dell'APK come artifact.

## Limiti importanti di questa alpha

- **Non è ancora dichiarata stabile né validata su dispositivo.** Il codice è stato aggiornato e ha superato i controlli statici elencati in `roadtools/foundation_smoke_test.py`, ma in questo ambiente non sono disponibili Android SDK/Gradle e non è stato possibile eseguire la build Android.
- La navigazione usa servizi pubblici di routing/geocodifica senza SLA. Disponibilità, copertura e limiti possono cambiare; non è equivalente a un servizio commerciale di navigazione premium.
- I limiti OSM possono mancare o essere errati. Seguire sempre la segnaletica reale; non usare l'app come unica fonte di sicurezza.
- La cache SQLite delle strade si aggiorna progressivamente. La pre-cache cartografica garantita di 50–100 km con aggiornamento esatto ogni 25 km non è ancora implementata e va completata dopo la verifica della compatibilità del provider e delle sue condizioni d'uso.
- Non è implementata una modalità offline completa. La mappa e il routing richiedono rete; la cache nativa del renderer non garantisce una copertura geografica predeterminata.
- Il diario registra viaggi automaticamente quando rileva movimento GPS sufficiente; il primo test va fatto con il telefono fissato in sicurezza e senza interagire durante la guida.
- Le curve sono una previsione assistiva basata su geometria disponibile, non una garanzia sulla sicurezza della strada.

## Compilazione

Richiede JDK 17, Gradle 8.7 e Android SDK Platform 35 / Build Tools 35.0.0. Il progetto non include il Gradle Wrapper perché non era disponibile nell'ambiente di preparazione.

```bash
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest
python3 roadtools/foundation_smoke_test.py
```

### Ottenere l'APK tramite GitHub Actions

1. Crea un repository GitHub vuoto chiamato `NEXT_NAVI_PRO`.
2. Carica **il contenuto** della cartella `nextnavi/` nella radice del repository (non la cartella ZIP come singolo file).
3. Attendi che l'azione `Android CI` termini.
4. Apri la relativa esecuzione in **Actions** e scarica l'artifact `next-navi-pro-debug-apk`.
5. L'APK è una build Debug non firmata per distribuzione pubblica; Android potrebbe richiedere l'autorizzazione all'installazione da quella sorgente.

## Prima prova sul campo

Prima di usare la navigazione reale, verificare in ambiente controllato: installazione e avvio, temi, orientamento, permessi GPS, mappa, ricerca, percorso, audio, limiti, meteo e GPX. Le prime prove su strada devono essere effettuate da un passeggero o in condizioni sicure. Non interagire con il telefono mentre si guida.
