package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisCommandParserTest {
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

    @Test fun parsesOfflineStatusCommands() {
        assertEquals(JarvisCommand.WhereAmI, JarvisCommandParser.parse("Dove sono?"))
        assertEquals(JarvisCommand.Remaining, JarvisCommandParser.parse("Quanto manca all'arrivo?"))
        assertEquals(JarvisCommand.Repeat, JarvisCommandParser.parse("Ripeti l'indicazione"))
        assertEquals(JarvisCommand.Silence, JarvisCommandParser.parse("Silenzio"))
        assertEquals(JarvisCommand.Help, JarvisCommandParser.parse("Cosa puoi fare?"))
        assertEquals(JarvisCommand.ShowResults, JarvisCommandParser.parse("Mostra risultati"))
    }

    @Test fun parsesRoutePreviewRequestsWithoutStartingNavigation() {
        assertEquals(JarvisCommand.PreviewRoute("Guaza"),
            JarvisCommandParser.parse("Voglio andare a Guaza, cosa mi aspetta per la strada?"))
        assertEquals(JarvisCommand.PreviewRoute("Adeje"),
            JarvisCommandParser.parse("Vorrei andare a Adeje, fammi un briefing del percorso"))
        assertEquals(JarvisCommand.BriefRoute, JarvisCommandParser.parse("Cosa mi aspetta"))
    }

    @Test fun unknownRequestsDoNotBecomeInventedDestinations() {
        assertEquals(JarvisCommand.Unknown, JarvisCommandParser.parse("Che tempo farà tra tre giorni?"))
        assertEquals(JarvisCommand.Unknown, JarvisCommandParser.parse(""))
    }
}
