package com.nextnavipro.app

import java.util.Locale

/**
 * Small, dependency-free localization layer for runtime-built Android views.
 * Android's system locale remains the source of truth; unsupported languages fall back to English.
 * Keep keys in Italian to minimize churn while migrating the legacy UI to resource-backed strings.
 */
object NextNaviStrings {
    private val translations: Map<String, Map<String, String>> = mapOf(
        "es" to mapOf(
            "Avvia Navigazione" to "Iniciar navegación",
            "Cerca indirizzo o località" to "Buscar dirección o localidad",
            "Indirizzi preferiti" to "Direcciones favoritas",
            "Salva destinazione come preferita" to "Guardar destino como favorito",
            "Elimina preferiti" to "Eliminar favoritos",
            "Prova voce navigatore" to "Probar voz del navegador",
            "Modalità schermo" to "Modo de pantalla",
            "Ricerche recenti" to "Búsquedas recientes",
            "Soglie tachimetro e avvisi" to "Límites del velocímetro y alertas",
            "Previsioni meteo" to "Previsión meteorológica",
            "Percorsi GPX" to "Rutas GPX",
            "Debug • CSV GPS: esporta / elimina" to "Depuración • CSV GPS: exportar / borrar",
            "Profilo veicolo: " to "Perfil del vehículo: ",
            "MOTO" to "MOTO", "AUTO" to "COCHE",
            "Nome assistente vocale" to "Nombre del asistente de voz",
            "Voce Jarvis" to "Voz de Jarvis",
            "NEXT NAVI PRO • Menu" to "NEXT NAVI PRO • Menú",
            "Automatica (voce predefinita del dispositivo)" to "Automática (voz predeterminada del dispositivo)",
            "Femminile" to "Femenina", "Maschile" to "Masculina",
            "Chiudi" to "Cerrar",
            "Ciao! Questa è la voce selezionata per Jarvis." to "¡Hola! Esta es la voz seleccionada para Jarvis.",
            "Personalizza Jarvis" to "Personalizar Jarvis",
            "Il tuo nome (facoltativo)" to "Tu nombre (opcional)",
            "Come vuoi che ti chiami? È facoltativo e puoi saltare." to "¿Cómo quieres que te llame? Es opcional.",
            "Salva" to "Guardar", "Salta" to "Omitir", "Annulla" to "Cancelar"
        ),
        "en" to mapOf(
            "Avvia Navigazione" to "Start navigation",
            "Cerca indirizzo o località" to "Search address or place",
            "Indirizzi preferiti" to "Favorite addresses",
            "Salva destinazione come preferita" to "Save destination as favorite",
            "Elimina preferiti" to "Delete favorites",
            "Prova voce navigatore" to "Test navigation voice",
            "Modalità schermo" to "Screen mode",
            "Ricerche recenti" to "Recent searches",
            "Soglie tachimetro e avvisi" to "Speedometer thresholds and alerts",
            "Previsioni meteo" to "Weather forecast",
            "Percorsi GPX" to "GPX routes",
            "Debug • CSV GPS: esporta / elimina" to "Debug • GPS CSV: export / delete",
            "Profilo veicolo: " to "Vehicle profile: ",
            "MOTO" to "MOTORCYCLE", "AUTO" to "CAR",
            "Nome assistente vocale" to "Voice assistant name",
            "Voce Jarvis" to "Jarvis voice",
            "NEXT NAVI PRO • Menu" to "NEXT NAVI PRO • Menu",
            "Automatica (voce predefinita del dispositivo)" to "Automatic (device default voice)",
            "Femminile" to "Female", "Maschile" to "Male",
            "Chiudi" to "Close",
            "Ciao! Questa è la voce selezionata per Jarvis." to "Hello! This is the voice selected for Jarvis.",
            "Personalizza Jarvis" to "Customize Jarvis",
            "Il tuo nome (facoltativo)" to "Your name (optional)",
            "Come vuoi che ti chiami? È facoltativo e puoi saltare." to "What should I call you? This is optional.",
            "Salva" to "Save", "Salta" to "Skip", "Annulla" to "Cancel"
        ),
        "fr" to mapOf(
            "Avvia Navigazione" to "Démarrer la navigation",
            "Cerca indirizzo o località" to "Rechercher une adresse ou un lieu",
            "Indirizzi preferiti" to "Adresses favorites",
            "Salva destinazione come preferita" to "Enregistrer comme favori",
            "Elimina preferiti" to "Supprimer les favoris",
            "Prova voce navigatore" to "Tester la voix du navigateur",
            "Modalità schermo" to "Mode d’écran",
            "Ricerche recenti" to "Recherches récentes",
            "Soglie tachimetro e avvisi" to "Seuils du compteur et alertes",
            "Previsioni meteo" to "Prévisions météo",
            "Percorsi GPX" to "Itinéraires GPX",
            "Profilo veicolo: " to "Profil du véhicule : ",
            "MOTO" to "MOTO", "AUTO" to "VOITURE",
            "Nome assistente vocale" to "Nom de l’assistant vocal",
            "Voce Jarvis" to "Voix de Jarvis",
            "Automatica (voce predefinita del dispositivo)" to "Automatique (voix par défaut de l’appareil)",
            "Femminile" to "Féminine", "Maschile" to "Masculine",
            "Chiudi" to "Fermer", "Salva" to "Enregistrer", "Salta" to "Ignorer", "Annulla" to "Annuler",
            "Personalizza Jarvis" to "Personnaliser Jarvis",
            "Il tuo nome (facoltativo)" to "Votre nom (facultatif)",
            "Come vuoi che ti chiami? È facoltativo e puoi saltare." to "Comment souhaitez-vous que je vous appelle ? C’est facultatif.",
            "Ciao! Questa è la voce selezionata per Jarvis." to "Bonjour ! Voici la voix sélectionnée pour Jarvis."
        ),
        "de" to mapOf(
            "Avvia Navigazione" to "Navigation starten",
            "Cerca indirizzo o località" to "Adresse oder Ort suchen",
            "Indirizzi preferiti" to "Favorisierte Adressen",
            "Salva destinazione come preferita" to "Ziel als Favorit speichern",
            "Elimina preferiti" to "Favoriten löschen",
            "Prova voce navigatore" to "Navigationsstimme testen",
            "Modalità schermo" to "Bildschirmmodus",
            "Ricerche recenti" to "Letzte Suchanfragen",
            "Soglie tachimetro e avvisi" to "Tacho-Grenzwerte und Warnungen",
            "Previsioni meteo" to "Wettervorhersage",
            "Percorsi GPX" to "GPX-Routen",
            "Profilo veicolo: " to "Fahrzeugprofil: ",
            "MOTO" to "MOTORRAD", "AUTO" to "AUTO",
            "Nome assistente vocale" to "Name des Sprachassistenten",
            "Voce Jarvis" to "Jarvis-Stimme",
            "Automatica (voce predefinita del dispositivo)" to "Automatisch (Gerätestimme)",
            "Femminile" to "Weiblich", "Maschile" to "Männlich",
            "Chiudi" to "Schließen", "Salva" to "Speichern", "Salta" to "Überspringen", "Annulla" to "Abbrechen",
            "Personalizza Jarvis" to "Jarvis anpassen",
            "Il tuo nome (facoltativo)" to "Dein Name (optional)",
            "Come vuoi che ti chiami? È facoltativo e puoi saltare." to "Wie soll ich dich nennen? Diese Angabe ist optional.",
            "Ciao! Questa è la voce selezionata per Jarvis." to "Hallo! Das ist die ausgewählte Stimme für Jarvis."
        ),
        "pt" to mapOf(
            "Avvia Navigazione" to "Iniciar navegação",
            "Cerca indirizzo o località" to "Pesquisar endereço ou localidade",
            "Indirizzi preferiti" to "Endereços favoritos",
            "Salva destinazione come preferita" to "Guardar destino como favorito",
            "Elimina preferiti" to "Eliminar favoritos",
            "Prova voce navigatore" to "Testar voz da navegação",
            "Modalità schermo" to "Modo de ecrã",
            "Ricerche recenti" to "Pesquisas recentes",
            "Soglie tachimetro e avvisi" to "Limites do velocímetro e alertas",
            "Previsioni meteo" to "Previsão meteorológica",
            "Percorsi GPX" to "Percursos GPX",
            "Profilo veicolo: " to "Perfil do veículo: ",
            "MOTO" to "MOTA", "AUTO" to "CARRO",
            "Nome assistente vocale" to "Nome do assistente de voz",
            "Voce Jarvis" to "Voz do Jarvis",
            "Automatica (voce predefinita del dispositivo)" to "Automática (voz predefinida do dispositivo)",
            "Femminile" to "Feminina", "Maschile" to "Masculina",
            "Chiudi" to "Fechar", "Salva" to "Guardar", "Salta" to "Ignorar", "Annulla" to "Cancelar",
            "Personalizza Jarvis" to "Personalizar Jarvis",
            "Il tuo nome (facoltativo)" to "O teu nome (opcional)",
            "Come vuoi che ti chiami? È facoltativo e puoi saltare." to "Como queres que te chame? É opcional.",
            "Ciao! Questa è la voce selezionata per Jarvis." to "Olá! Esta é a voz selecionada para Jarvis."
        ),
        "nl" to mapOf(
            "Avvia Navigazione" to "Navigatie starten",
            "Cerca indirizzo o località" to "Adres of plaats zoeken",
            "Indirizzi preferiti" to "Favoriete adressen",
            "Salva destinazione come preferita" to "Bestemming als favoriet opslaan",
            "Elimina preferiti" to "Favorieten verwijderen",
            "Prova voce navigatore" to "Navigatiestem testen",
            "Modalità schermo" to "Schermmodus",
            "Ricerche recenti" to "Recente zoekopdrachten",
            "Soglie tachimetro e avvisi" to "Snelheidsmetergrenzen en waarschuwingen",
            "Previsioni meteo" to "Weersverwachting",
            "Percorsi GPX" to "GPX-routes",
            "Profilo veicolo: " to "Voertuigprofiel: ",
            "MOTO" to "MOTOR", "AUTO" to "AUTO",
            "Nome assistente vocale" to "Naam spraakassistent",
            "Voce Jarvis" to "Jarvis-stem",
            "Automatica (voce predefinita del dispositivo)" to "Automatisch (standaardstem van apparaat)",
            "Femminile" to "Vrouwelijk", "Maschile" to "Mannelijk",
            "Chiudi" to "Sluiten", "Salva" to "Opslaan", "Salta" to "Overslaan", "Annulla" to "Annuleren",
            "Personalizza Jarvis" to "Jarvis aanpassen",
            "Il tuo nome (facoltativo)" to "Je naam (optioneel)",
            "Come vuoi che ti chiami? È facoltativo e puoi saltare." to "Hoe zal ik je noemen? Dit is optioneel.",
            "Ciao! Questa è la voce selezionata per Jarvis." to "Hallo! Dit is de geselecteerde stem voor Jarvis."
        )
    )


    private val coreTranslations: Map<String, Map<String, String>> = mapOf(
        "es" to mapOf(
            "CERCA" to "BUSCAR", "CASA" to "CASA", "MENU" to "MENÚ", "Premi il microfono e parla" to "Pulsa el micrófono y habla",
            "🌤️ METEO in attesa GPS" to "🌤️ METEO esperando GPS", "RICERCA GPS…" to "BUSCANDO GPS…",
            "Nessun percorso attivo" to "Sin ruta activa", "RADAR OSM" to "RADARES OSM",
            "AVVIA PERCORSO" to "INICIAR RUTA", "⌖ POSIZIONE" to "⌖ POSICIÓN",
            "ANNULLA PERCORSO" to "CANCELAR RUTA", "ANNULLA TRACCIA GPX" to "CANCELAR TRAZA GPX",
            "ANNULLA PERCORSO GPX" to "CANCELAR RUTA GPX", "DESTINAZIONE: " to "DESTINO: ",
            "Premi AVVIA PERCORSO" to "Pulsa INICIAR RUTA", "pronto • premi per parlare" to "listo • pulsa para hablar"
        ),
        "en" to mapOf(
            "CERCA" to "SEARCH", "CASA" to "HOME", "MENU" to "MENU", "Premi il microfono e parla" to "Tap the microphone and speak",
            "🌤️ METEO in attesa GPS" to "🌤️ WEATHER waiting for GPS", "RICERCA GPS…" to "SEARCHING GPS…",
            "Nessun percorso attivo" to "No active route", "RADAR OSM" to "OSM CAMERAS",
            "AVVIA PERCORSO" to "START ROUTE", "⌖ POSIZIONE" to "⌖ RECENTER",
            "ANNULLA PERCORSO" to "CANCEL ROUTE", "ANNULLA TRACCIA GPX" to "CANCEL GPX TRACK",
            "ANNULLA PERCORSO GPX" to "CANCEL GPX ROUTE", "DESTINAZIONE: " to "DESTINATION: ",
            "Premi AVVIA PERCORSO" to "Tap START ROUTE", "pronto • premi per parlare" to "ready • tap to speak"
        ),
        "fr" to mapOf(
            "CERCA" to "CHERCHER", "CASA" to "MAISON", "MENU" to "MENU", "Premi il microfono e parla" to "Appuyez sur le micro et parlez",
            "🌤️ METEO in attesa GPS" to "🌤️ MÉTÉO en attente du GPS", "RICERCA GPS…" to "RECHERCHE GPS…",
            "Nessun percorso attivo" to "Aucun itinéraire actif", "RADAR OSM" to "RADARS OSM",
            "AVVIA PERCORSO" to "DÉMARRER", "⌖ POSIZIONE" to "⌖ RECENTRER",
            "ANNULLA PERCORSO" to "ANNULER", "ANNULLA TRACCIA GPX" to "ANNULER LA TRACE GPX",
            "ANNULLA PERCORSO GPX" to "ANNULER L’ITINÉRAIRE GPX", "DESTINAZIONE: " to "DESTINATION : ",
            "Premi AVVIA PERCORSO" to "Appuyez sur DÉMARRER", "pronto • premi per parlare" to "prêt • appuyez pour parler"
        ),
        "de" to mapOf(
            "CERCA" to "SUCHEN", "CASA" to "ZUHAUSE", "MENU" to "MENÜ", "Premi il microfono e parla" to "Mikrofon antippen und sprechen",
            "🌤️ METEO in attesa GPS" to "🌤️ WETTER wartet auf GPS", "RICERCA GPS…" to "GPS-SUCHE…",
            "Nessun percorso attivo" to "Keine aktive Route", "RADAR OSM" to "OSM-BLITZER",
            "AVVIA PERCORSO" to "ROUTE STARTEN", "⌖ POSIZIONE" to "⌖ ZENTRIEREN",
            "ANNULLA PERCORSO" to "ROUTE ABBRECHEN", "ANNULLA TRACCIA GPX" to "GPX-TRACK ABBRECHEN",
            "ANNULLA PERCORSO GPX" to "GPX-ROUTE ABBRECHEN", "DESTINAZIONE: " to "ZIEL: ",
            "Premi AVVIA PERCORSO" to "ROUTE STARTEN antippen", "pronto • premi per parlare" to "bereit • zum Sprechen tippen"
        ),
        "pt" to mapOf(
            "CERCA" to "PESQUISAR", "CASA" to "CASA", "MENU" to "MENU", "Premi il microfono e parla" to "Toca no microfone e fala",
            "🌤️ METEO in attesa GPS" to "🌤️ METEOROLOGIA à espera do GPS", "RICERCA GPS…" to "A PROCURAR GPS…",
            "Nessun percorso attivo" to "Sem percurso ativo", "RADAR OSM" to "RADARES OSM",
            "AVVIA PERCORSO" to "INICIAR PERCURSO", "⌖ POSIZIONE" to "⌖ RECENTRAR",
            "ANNULLA PERCORSO" to "CANCELAR PERCURSO", "ANNULLA TRACCIA GPX" to "CANCELAR TRAJETO GPX",
            "ANNULLA PERCORSO GPX" to "CANCELAR PERCURSO GPX", "DESTINAZIONE: " to "DESTINO: ",
            "Premi AVVIA PERCORSO" to "Toca em INICIAR PERCURSO", "pronto • premi per parlare" to "pronto • toca para falar"
        ),
        "nl" to mapOf(
            "CERCA" to "ZOEKEN", "CASA" to "THUIS", "MENU" to "MENU", "Premi il microfono e parla" to "Tik op de microfoon en spreek",
            "🌤️ METEO in attesa GPS" to "🌤️ WEER wacht op GPS", "RICERCA GPS…" to "GPS ZOEKEN…",
            "Nessun percorso attivo" to "Geen actieve route", "RADAR OSM" to "OSM-CAMERA’S",
            "AVVIA PERCORSO" to "ROUTE STARTEN", "⌖ POSIZIONE" to "⌖ HER-CENTREREN",
            "ANNULLA PERCORSO" to "ROUTE ANNULEREN", "ANNULLA TRACCIA GPX" to "GPX-TRACK ANNULEREN",
            "ANNULLA PERCORSO GPX" to "GPX-ROUTE ANNULEREN", "DESTINAZIONE: " to "BESTEMMING: ",
            "Premi AVVIA PERCORSO" to "Tik op ROUTE STARTEN", "pronto • premi per parlare" to "klaar • tik om te spreken"
        )
    )


    private val statusTranslations: Map<String, Map<String, String>> = mapOf(
        "es" to mapOf(
            "Ascolto annullato" to "Escucha cancelada", "Ti ascolto…" to "Te escucho…", "Sto ascoltando…" to "Estoy escuchando…",
            "Elaboro…" to "Procesando…", "Non ho capito • riprova" to "No te he entendido • inténtalo de nuevo",
            "Permesso microfono mancante" to "Falta permiso del micrófono", "Rete non disponibile • riprova" to "Red no disponible • inténtalo de nuevo",
            "Ascolto terminato • riprova" to "Escucha finalizada • inténtalo de nuevo",
            "Non ho ricevuto parole • riprova" to "No he recibido palabras • inténtalo de nuevo",
            "Riconoscimento vocale non disponibile" to "Reconocimiento de voz no disponible",
            "Voce non pronta o lingua TTS non disponibile." to "La voz no está lista o el idioma TTS no está disponible."
        ),
        "en" to mapOf(
            "Ascolto annullato" to "Listening cancelled", "Ti ascolto…" to "I'm listening…", "Sto ascoltando…" to "Listening…",
            "Elaboro…" to "Processing…", "Non ho capito • riprova" to "I didn't understand • try again",
            "Permesso microfono mancante" to "Microphone permission missing", "Rete non disponibile • riprova" to "Network unavailable • try again",
            "Ascolto terminato • riprova" to "Listening ended • try again",
            "Non ho ricevuto parole • riprova" to "I didn't receive any words • try again",
            "Riconoscimento vocale non disponibile" to "Speech recognition unavailable",
            "Voce non pronta o lingua TTS non disponibile." to "Voice not ready or system TTS language unavailable."
        ),
        "fr" to mapOf(
            "Ascolto annullato" to "Écoute annulée", "Ti ascolto…" to "Je vous écoute…", "Sto ascoltando…" to "Écoute en cours…",
            "Elaboro…" to "Traitement…", "Non ho capito • riprova" to "Je n’ai pas compris • réessayez",
            "Permesso microfono mancante" to "Autorisation du micro manquante", "Rete non disponibile • riprova" to "Réseau indisponible • réessayez",
            "Ascolto terminato • riprova" to "Écoute terminée • réessayez",
            "Non ho ricevuto parole • riprova" to "Aucune parole reçue • réessayez",
            "Riconoscimento vocale non disponibile" to "Reconnaissance vocale indisponible",
            "Voce non pronta o lingua TTS non disponibile." to "Voix non prête ou langue TTS indisponible."
        ),
        "de" to mapOf(
            "Ascolto annullato" to "Abhören abgebrochen", "Ti ascolto…" to "Ich höre zu…", "Sto ascoltando…" to "Ich höre zu…",
            "Elaboro…" to "Verarbeitung…", "Non ho capito • riprova" to "Nicht verstanden • bitte erneut versuchen",
            "Permesso microfono mancante" to "Mikrofonberechtigung fehlt", "Rete non disponibile • riprova" to "Netz nicht verfügbar • erneut versuchen",
            "Ascolto terminato • riprova" to "Abhören beendet • erneut versuchen",
            "Non ho ricevuto parole • riprova" to "Keine Sprache empfangen • erneut versuchen",
            "Riconoscimento vocale non disponibile" to "Spracherkennung nicht verfügbar",
            "Voce non pronta o lingua TTS non disponibile." to "Stimme nicht bereit oder TTS-Sprache nicht verfügbar."
        ),
        "pt" to mapOf(
            "Ascolto annullato" to "Escuta cancelada", "Ti ascolto…" to "Estou a ouvir…", "Sto ascoltando…" to "A ouvir…",
            "Elaboro…" to "A processar…", "Non ho capito • riprova" to "Não percebi • tenta novamente",
            "Permesso microfono mancante" to "Falta a permissão do microfone", "Rete non disponibile • riprova" to "Rede indisponível • tenta novamente",
            "Ascolto terminato • riprova" to "Escuta terminada • tenta novamente",
            "Non ho ricevuto parole • riprova" to "Não recebi palavras • tenta novamente",
            "Riconoscimento vocale non disponibile" to "Reconhecimento de voz indisponível",
            "Voce non pronta o lingua TTS non disponibile." to "Voz não pronta ou idioma TTS indisponível."
        ),
        "nl" to mapOf(
            "Ascolto annullato" to "Luisteren geannuleerd", "Ti ascolto…" to "Ik luister…", "Sto ascoltando…" to "Aan het luisteren…",
            "Elaboro…" to "Verwerken…", "Non ho capito • riprova" to "Niet begrepen • probeer opnieuw",
            "Permesso microfono mancante" to "Microfoontoestemming ontbreekt", "Rete non disponibile • riprova" to "Netwerk niet beschikbaar • probeer opnieuw",
            "Ascolto terminato • riprova" to "Luisteren beëindigd • probeer opnieuw",
            "Non ho ricevuto parole • riprova" to "Geen spraak ontvangen • probeer opnieuw",
            "Riconoscimento vocale non disponibile" to "Spraakherkenning niet beschikbaar",
            "Voce non pronta o lingua TTS non disponibile." to "Stem niet gereed of TTS-taal niet beschikbaar."
        )
    )


    private val runtimeTranslations: Map<String, Map<String, String>> = mapOf(
        "en" to mapOf(
            "Voce di Jarvis" to "Jarvis voice",
            "Scegli come vuoi chiamare l'assistente di navigazione." to "Choose a name for your navigation assistant.",
            "Nome dell'assistente" to "Assistant name",
            "Ripristina Jarvis" to "Restore Jarvis",
            "Assistente rinominato in " to "Assistant renamed to ",
            "Nome ripristinato: Jarvis" to "Name restored: Jarvis",
            "Il riconoscimento vocale non è disponibile su questo dispositivo." to "Speech recognition is unavailable on this device.",
            "Non riesco ad avviare il microfono. Puoi riprovare." to "I can't start the microphone. Please try again.",
            "Avvio microfono…" to "Starting microphone…",
            "Impossibile avviare il microfono" to "Unable to start the microphone",
            "Hai detto: " to "You said: ",
            "■ STOP" to "■ STOP"
        ),
        "es" to mapOf(
            "Voce di Jarvis" to "Voz de Jarvis",
            "Scegli come vuoi chiamare l'assistente di navigazione." to "Elige un nombre para tu asistente de navegación.",
            "Nome dell'assistente" to "Nombre del asistente",
            "Ripristina Jarvis" to "Restaurar Jarvis",
            "Assistente rinominato in " to "Asistente renombrado a ",
            "Nome ripristinato: Jarvis" to "Nombre restaurado: Jarvis",
            "Il riconoscimento vocale non è disponibile su questo dispositivo." to "El reconocimiento de voz no está disponible en este dispositivo.",
            "Non riesco ad avviare il microfono. Puoi riprovare." to "No puedo iniciar el micrófono. Inténtalo de nuevo.",
            "Avvio microfono…" to "Iniciando micrófono…",
            "Impossibile avviare il microfono" to "No se puede iniciar el micrófono",
            "Hai detto: " to "Has dicho: ",
            "■ STOP" to "■ PARAR"
        ),
        "fr" to mapOf(
            "Voce di Jarvis" to "Voix de Jarvis",
            "Scegli come vuoi chiamare l'assistente di navigazione." to "Choisissez un nom pour votre assistant de navigation.",
            "Nome dell'assistente" to "Nom de l’assistant",
            "Ripristina Jarvis" to "Rétablir Jarvis",
            "Assistente rinominato in " to "Assistant renommé en ",
            "Nome ripristinato: Jarvis" to "Nom rétabli : Jarvis",
            "Il riconoscimento vocale non è disponibile su questo dispositivo." to "La reconnaissance vocale n’est pas disponible sur cet appareil.",
            "Non riesco ad avviare il microfono. Puoi riprovare." to "Impossible de démarrer le micro. Veuillez réessayer.",
            "Avvio microfono…" to "Démarrage du micro…",
            "Impossibile avviare il microfono" to "Impossible de démarrer le micro",
            "Hai detto: " to "Vous avez dit : ",
            "■ STOP" to "■ ARRÊTER"
        ),
        "de" to mapOf(
            "Voce di Jarvis" to "Jarvis-Stimme",
            "Scegli come vuoi chiamare l'assistente di navigazione." to "Wähle einen Namen für deinen Navigationsassistenten.",
            "Nome dell'assistente" to "Assistentenname",
            "Ripristina Jarvis" to "Jarvis wiederherstellen",
            "Assistente rinominato in " to "Assistent umbenannt in ",
            "Nome ripristinato: Jarvis" to "Name zurückgesetzt: Jarvis",
            "Il riconoscimento vocale non è disponibile su questo dispositivo." to "Spracherkennung ist auf diesem Gerät nicht verfügbar.",
            "Non riesco ad avviare il microfono. Puoi riprovare." to "Das Mikrofon kann nicht gestartet werden. Bitte erneut versuchen.",
            "Avvio microfono…" to "Mikrofon wird gestartet…",
            "Impossibile avviare il microfono" to "Mikrofon kann nicht gestartet werden",
            "Hai detto: " to "Du hast gesagt: ",
            "■ STOP" to "■ STOPP"
        ),
        "pt" to mapOf(
            "Voce di Jarvis" to "Voz do Jarvis",
            "Scegli come vuoi chiamare l'assistente di navigazione." to "Escolhe um nome para o teu assistente de navegação.",
            "Nome dell'assistente" to "Nome do assistente",
            "Ripristina Jarvis" to "Restaurar Jarvis",
            "Assistente rinominato in " to "Assistente renomeado para ",
            "Nome ripristinato: Jarvis" to "Nome restaurado: Jarvis",
            "Il riconoscimento vocale non è disponibile su questo dispositivo." to "O reconhecimento de voz não está disponível neste dispositivo.",
            "Non riesco ad avviare il microfono. Puoi riprovare." to "Não consigo iniciar o microfone. Tenta novamente.",
            "Avvio microfono…" to "A iniciar o microfone…",
            "Impossibile avviare il microfono" to "Não foi possível iniciar o microfone",
            "Hai detto: " to "Disseste: ",
            "■ STOP" to "■ PARAR"
        ),
        "nl" to mapOf(
            "Voce di Jarvis" to "Jarvis-stem",
            "Scegli come vuoi chiamare l'assistente di navigazione." to "Kies een naam voor je navigatieassistent.",
            "Nome dell'assistente" to "Naam van de assistent",
            "Ripristina Jarvis" to "Jarvis herstellen",
            "Assistente rinominato in " to "Assistent hernoemd naar ",
            "Nome ripristinato: Jarvis" to "Naam hersteld: Jarvis",
            "Il riconoscimento vocale non è disponibile su questo dispositivo." to "Spraakherkenning is niet beschikbaar op dit apparaat.",
            "Non riesco ad avviare il microfono. Puoi riprovare." to "Ik kan de microfoon niet starten. Probeer het opnieuw.",
            "Avvio microfono…" to "Microfoon starten…",
            "Impossibile avviare il microfono" to "Kan de microfoon niet starten",
            "Hai detto: " to "Je zei: ",
            "■ STOP" to "■ STOPPEN"
        )
    )

    fun translate(text: String, locale: Locale = Locale.getDefault()): String {
        if (locale.language.equals("it", ignoreCase = true)) return text
        val language = locale.language.lowercase(Locale.ROOT)
        return translations[language]?.get(text)
            ?: coreTranslations[language]?.get(text)
            ?: statusTranslations[language]?.get(text)
            ?: runtimeTranslations[language]?.get(text)
            ?: translations["en"]?.get(text)
            ?: coreTranslations["en"]?.get(text)
            ?: statusTranslations["en"]?.get(text)
            ?: runtimeTranslations["en"]?.get(text)
            ?: text
    }
}
