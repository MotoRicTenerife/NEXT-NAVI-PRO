# Verifica ripetibile delle tracce GPS reali

## Raccolta dal telefono

1. Installa una build NEXT NAVI PRO che includa **MENU → Diagnostica GPS / esporta traccia**.
2. Prima di partire, apri il menu e scegli **Avvia registrazione diagnostica GPS**. Non usare il telefono mentre guidi.
3. Segui un percorso reale con navigazione attiva; per una prova utile includi almeno un incrocio o una rotatoria e, se possibile, un tratto con strade parallele.
4. Fermati in sicurezza, apri lo stesso menu, scegli **Ferma registrazione GPS**, quindi **Esporta CSV per analisi**.
5. Esporta anche la geometria del percorso pianificato da **MENU → Percorsi GPX → Esporta GPX attivo**. Conserva entrambi i file con lo stesso nome di prova.

Il CSV contiene timestamp, coordinate GNSS grezze accettate, accuratezza dichiarata, velocità, direzione, stato satellitare e risultato dell'aggancio al percorso. La registrazione è opt-in e limitata a 50.000 campioni.

## Analisi offline ripetibile

Con Python 3, dalla root del repository:

```sh
python roadtools/gps_trace_quality.py /percorso/trace.csv /percorso/route.gpx
```

Il report JSON mostra quota di campioni agganciati, errore laterale rispetto al GPX di riferimento, percentili dell'accuratezza, intervallo temporale e salti apparentemente impossibili. Il test automatico della utility è eseguito anche in GitHub Actions.

**Limite metodologico:** il GPX pianificato è una linea di riferimento, non una verità assoluta della posizione reale. Errori cartografici, deviazioni volontarie e percorsi differenti possono apparire come scostamenti. Per valutare una traccia bisogna controllare insieme il report, la mappa e i punti in cui il conducente ha realmente seguito una strada diversa.
