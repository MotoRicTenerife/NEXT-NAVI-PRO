package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisCommandParserTest {
    @Test fun stripsAssistantNameBeforeParsingCommands() {
        val withDefaultName = JarvisCommandParser.stripAssistantName("Jarvis, portami a Guazza")
        assertEquals("portami a Guazza", withDefaultName)
        assertEquals(JarvisCommand.Navigate("Guazza"), JarvisCommandParser.parse(withDefaultName))

        val withCustomName = JarvisCommandParser.stripAssistantName("Alfred: annulla la navigazione", "Alfred")
        assertEquals("annulla la navigazione", withCustomName)
        assertEquals(JarvisCommand.CancelNavigation, JarvisCommandParser.parse(withCustomName))
        assertEquals("portami a Guaza", JarvisCommandParser.stripAssistantName("portami a Guaza", "Alfred"))
    }

    @Test fun placeSearchVariantsTolerateForeignPlaceNameSpeechSpelling() {
        assertEquals(listOf("Guazza", "Guaza"), NavigationServices.placeQueryVariants("Guazza"))
        assertEquals(listOf("Los Cristianos"), NavigationServices.placeQueryVariants("Los Cristianos"))
        assertEquals(listOf("Adeje"), NavigationServices.placeQueryVariants("Adeje"))
        assertEquals(listOf("Candelaría", "Candelaria"), NavigationServices.placeQueryVariants("Candelaría"))
    }

    @Test fun parsesDestinationCommands() {
        assertEquals(JarvisCommand.Navigate("Santa Cruz"), JarvisCommandParser.parse("Portami a Santa Cruz"))
        assertEquals(JarvisCommand.Navigate("Los Cristianos"), JarvisCommandParser.parse("Mi porti a Los Cristianos"))
        assertEquals(JarvisCommand.Navigate("Los Cristianos"), JarvisCommandParser.parse("Puoi portarmi a Los Cristianos"))
        assertEquals(JarvisCommand.Navigate("Los Cristianos"), JarvisCommandParser.parse("Portami a Los Cristianos"))
        assertEquals(JarvisCommand.Navigate("Scristiano"), JarvisCommandParser.parse("Portami allo Scristiano"))
        assertEquals(JarvisCommand.Navigate("Mercadona"), JarvisCommandParser.parse("Portami al Mercadona"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("naviga verso Adeje"))
        assertEquals(JarvisCommand.Navigate("casa"), JarvisCommandParser.parse("Vai a casa"))
    }

    @Test fun parsesCommonNavigationCommandsInSeveralLanguages() {
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Llévame a Adeje"))
        assertEquals(JarvisCommand.Navigate("centro de Adeje"), JarvisCommandParser.parse("Llévame al centro de Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Take me to Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Emmène-moi à Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Conduis-moi à Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Bring mich nach Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Breng me naar Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Leva-me a Adeje"))
        assertEquals(JarvisCommand.Navigate("casa"), JarvisCommandParser.parse("Take me home"))
    }

    @Test fun parsesNavigationCancellationAndDestinationChange() {
        assertEquals(JarvisCommand.CancelNavigation, JarvisCommandParser.parse("Annulla la navigazione"))
        assertEquals(JarvisCommand.CancelNavigation, JarvisCommandParser.parse("Ferma navigazione"))
        assertEquals(JarvisCommand.CancelNavigation, JarvisCommandParser.parse("Cancel navigation"))
        assertEquals(JarvisCommand.CancelNavigation, JarvisCommandParser.parse("Annulla il percorso"))
        assertEquals(JarvisCommand.CancelNavigation, JarvisCommandParser.parse("Ferma il navigatore"))
        assertEquals(JarvisCommand.CancelNavigation, JarvisCommandParser.parse("Smetti di navigare"))
        assertEquals(JarvisCommand.ChangeDestination(), JarvisCommandParser.parse("Cambia destinazione"))
        assertEquals(JarvisCommand.ChangeDestination("Santa Cruz"), JarvisCommandParser.parse("Cambia destinazione a Santa Cruz"))
        assertEquals(JarvisCommand.ChangeDestination("Adeje"), JarvisCommandParser.parse("Cambia destinazione in Adeje"))
        assertEquals(JarvisCommand.ChangeDestination("Guaza"), JarvisCommandParser.parse("Portami invece a Guaza"))
        assertEquals(JarvisCommand.ChangeDestination("Adeje"), JarvisCommandParser.parse("Change destination to Adeje"))
    }

    @Test fun parsesOfflineStatusCommands() {
        assertEquals(JarvisCommand.WhereAmI, JarvisCommandParser.parse("Dove sono?"))
        assertEquals(JarvisCommand.Remaining, JarvisCommandParser.parse("Quanto manca all'arrivo?"))
        assertEquals(JarvisCommand.Repeat, JarvisCommandParser.parse("Ripeti l'indicazione"))
        assertEquals(JarvisCommand.Silence, JarvisCommandParser.parse("Silenzio"))
        assertEquals(JarvisCommand.Help, JarvisCommandParser.parse("Cosa puoi fare?"))
        assertEquals(JarvisCommand.ShowResults, JarvisCommandParser.parse("Mostra risultati"))
    }

    @Test fun parsesAssistantNameQuestions() {
        assertEquals(JarvisCommand.AssistantName, JarvisCommandParser.parse("Come ti chiami?"))
        assertEquals(JarvisCommand.AssistantName, JarvisCommandParser.parse("Tu come ti chiami?"))
        assertEquals(JarvisCommand.AssistantName, JarvisCommandParser.parse("Qual è il tuo nome"))
        assertEquals(JarvisCommand.AssistantName, JarvisCommandParser.parse("What is your name?"))
        assertEquals(JarvisCommand.AssistantName, JarvisCommandParser.parse("¿Cómo te llamas?"))
    }

    @Test fun parsesRoutePreviewRequestsWithoutStartingNavigation() {
        assertEquals(JarvisCommand.PreviewRoute("Guaza"),
            JarvisCommandParser.parse("Voglio andare a Guaza, cosa mi aspetta per la strada?"))
        assertEquals(JarvisCommand.PreviewRoute("Guaza"),
            JarvisCommandParser.parse("Voglio andare a Guaza,   cosa mi aspetta per la strada?"))
        assertEquals(JarvisCommand.PreviewRoute("Santa Cruz"),
            JarvisCommandParser.parse("Vorrei andare a Santa Cruz, cosa mi aspetta sulla strada?"))
        assertEquals(JarvisCommand.PreviewRoute("Adeje"),
            JarvisCommandParser.parse("Vorrei andare a Adeje, fammi un briefing del percorso"))
        assertEquals(JarvisCommand.BriefRoute, JarvisCommandParser.parse("Cosa mi aspetta"))
    }

    @Test fun parsesNaturalTripTrafficQuestionsWithoutStartingNavigation() {
        assertEquals(
            JarvisCommand.TrafficPreview("Los Cristianos"),
            JarvisCommandParser.parse("Devo andare a Los Cristianos, mi dici se c'è traffico?")
        )
        assertEquals(
            JarvisCommand.TrafficPreview("Adeje"),
            JarvisCommandParser.parse("Vorrei andare ad Adeje, come è il traffico?")
        )
        assertEquals(
            JarvisCommand.TrafficPreview("Guaza"),
            JarvisCommandParser.parse("Devo raggiungere Guaza, ci sono code?")
        )
    }

    @Test fun parsesRoadWaypointFollowUpsFromConversation() {
        assertEquals(JarvisCommand.TrafficVia("TF-1"), JarvisCommandParser.parse("E passando dalla TF-1?"))
        assertEquals(JarvisCommand.TrafficVia("TF-82"), JarvisCommandParser.parse("E se prendo la TF-82?"))
        assertEquals(JarvisCommand.TrafficVia("Calle Morro Afonso Alto"), JarvisCommandParser.parse("Passando per Calle Morro Afonso Alto?"))
    }

    @Test fun stressParserWithRepeatedAndMalformedVoiceInput() {
        val inputs = listOf(
            "Voglio andare a Guaza, cosa mi aspetta per la strada?",
            "Voglio andare a Guaza,   cosa mi aspetta per la strada?",
            "Portami al Mercadona",
            "Dove sono?",
            "Cosa mi aspetta",
            "",
            "?!?!?!",
            "a".repeat(4096),
            "Portami a " + "x".repeat(4096),
            "Vorrei andare a Santa Cruz, fammi un briefing del percorso"
        )
        repeat(10_000) { index ->
            val inputIndex = index % inputs.size
            val result = JarvisCommandParser.parse(inputs[inputIndex])
            when (inputIndex) {
                0, 1 -> assertEquals(JarvisCommand.PreviewRoute("Guaza"), result)
                2 -> assertEquals(JarvisCommand.Navigate("Mercadona"), result)
                3 -> assertEquals(JarvisCommand.WhereAmI, result)
                4 -> assertEquals(JarvisCommand.BriefRoute, result)
                5, 6, 7 -> assertEquals(JarvisCommand.Unknown, result)
                8 -> assertEquals(JarvisCommand.Navigate("x".repeat(4096)), result)
                9 -> assertEquals(JarvisCommand.PreviewRoute("Santa Cruz"), result)
            }
        }
    }

    @Test fun placeNamesInOrdinaryConversationNeverBecomeNavigationCommands() {
        assertEquals(JarvisCommand.Unknown, JarvisCommandParser.parse("Parlami di Los Cristianos"))
        assertEquals(JarvisCommand.Unknown, JarvisCommandParser.parse("Che cosa sai di Adeje?"))
        assertEquals(JarvisCommand.Unknown, JarvisCommandParser.parse("Cerca informazioni su Santa Cruz"))
        assertEquals(JarvisCommand.Navigate("Guaza"), JarvisCommandParser.parse("Voglio andare a Guaza"))
        assertEquals(JarvisCommand.Navigate("Los Cristianos"), JarvisCommandParser.parse("Portami a Los Cristianos"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Vorrei andare ad Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Portami ad Adeje"))
        assertEquals(JarvisCommand.Navigate("Adeje"), JarvisCommandParser.parse("Voglio andare ad Adeje"))
    }

    @Test fun unknownRequestsDoNotBecomeInventedDestinations() {
        assertEquals(JarvisCommand.Unknown, JarvisCommandParser.parse("Che tempo farà tra tre giorni?"))
        assertEquals(JarvisCommand.Unknown, JarvisCommandParser.parse(""))
    }
}
