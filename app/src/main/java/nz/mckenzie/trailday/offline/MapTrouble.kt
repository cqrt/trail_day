package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.domain.tiles.Basemap

/**
 * Why the map has stopped drawing, as one of five things a walker can tell apart.
 *
 * The map used to answer this question with "The map service could not be reached" and a button
 * offering OpenStreetMap, whatever had actually happened. That is wrong in the one case it matters
 * most: with no reception, OpenStreetMap is the last thing that will draw, and the walker is left
 * with a card that names a service they have not asked for and an action that cannot work.
 *
 * So the reason is a fact gathered before the notice is drawn, not a sentence guessed while it is
 * written. Each one carries what is needed to say something true and to offer the one thing that
 * helps - see [troubleWords], where the words live rather than in the card.
 *
 * **The five are told apart by two questions.** Is there a connection at all ([Offline])? And if
 * there is, is the basemap one whose tiles come from LINZ ([Basemap.usesLinz]) - because only
 * then is the key, and the Worker that holds it, any of the walker's business.
 */
sealed interface MapTrouble {

    /**
     * There is no connection: the phone cannot reach anything.
     *
     * [downloaded] is an offline area that covers where the map is looking, for a **different**
     * basemap, or null when nothing on the phone covers this spot. It is the difference between
     * "use the map you already have" and "what you have is elsewhere".
     */
    data class Offline(val downloaded: Basemap?) : MapTrouble

    /**
     * The app's own proxy answered, and [message] is its sentence about the key it holds.
     *
     * Repeated as it was given rather than turned into a sentence here: the Worker is what knows
     * whether the key expired, is being rate limited, or is missing altogether.
     */
    data class ServiceSaid(val message: String) : MapTrouble

    /** The proxy did not answer at all, and there is a connection, so the trouble is not the phone. */
    data object ServiceSilent : MapTrouble

    /**
     * The walker's own LINZ key is in force and LINZ refused it - see [LinzAccess] and [KeyCheck].
     *
     * The one case where the fix is in the app rather than on the map: the key is in Settings.
     */
    data class OwnKeyRefused(val message: String) : MapTrouble

    /**
     * A drawn map - OpenStreetMap or OpenTopoMap - stopped answering while there is a connection.
     *
     * Not LINZ's business, so nothing is asked: their hosts need no key, and the one basemap left to
     * offer is the imagery, which comes through this app instead.
     */
    data object DrawnMapSilent : MapTrouble
}

/** What the notice says, and the one thing its button does about it. */
data class MapTroubleWords(
    val title: String,
    val body: String,
    val action: MapTroubleAction
)

/** The one action a notice offers. Which it is depends on [MapTrouble]. */
sealed interface MapTroubleAction {

    /** Draw [basemap] instead: one tap, the same preference the Settings screen writes. */
    data class SwitchTo(val basemap: Basemap) : MapTroubleAction

    /** Show what is downloaded, which is the Offline tab. */
    data object OpenOfflineTab : MapTroubleAction

    /** Where a key of the walker's own is put in. */
    data object OpenSettings : MapTroubleAction
}

/**
 * The words for a notice, from the reason and the basemap the walker is looking at.
 *
 * Pure, and separate from the card for the same reason [isTrouble] is separate from its counter: a
 * new kind of trouble is a case in this table and a test, rather than another branch inside a
 * composable.
 */
internal fun troubleWords(trouble: MapTrouble, basemap: Basemap): MapTroubleWords = when (trouble) {
    is MapTrouble.Offline -> MapTroubleWords(
        title = "You're offline",
        body = when (val covering = trouble.downloaded) {
            null ->
                "There's no connection, so this map can't draw here. Nothing you've downloaded " +
                    "covers this spot."
            else ->
                "There's no connection, so this map can't draw here. " +
                    "${covering.displayName} is downloaded for this spot."
        },
        action = when (val covering = trouble.downloaded) {
            null -> MapTroubleAction.OpenOfflineTab
            else -> MapTroubleAction.SwitchTo(covering)
        }
    )

    is MapTrouble.ServiceSaid -> MapTroubleWords(
        title = "This map isn't drawing",
        // The Worker's own sentence, then the thing that explains the button under it.
        body = "${trouble.message} The drawn maps need no key, so they still work.",
        action = MapTroubleAction.SwitchTo(Basemap.OPENSTREETMAP)
    )

    MapTrouble.ServiceSilent -> MapTroubleWords(
        title = "This map isn't drawing",
        body = "The map service didn't answer, so the trouble is at our end rather than on your " +
            "phone. The drawn maps need no key, so they still work.",
        action = MapTroubleAction.SwitchTo(Basemap.OPENSTREETMAP)
    )

    is MapTrouble.OwnKeyRefused -> MapTroubleWords(
        title = "Your LINZ key isn't working",
        body = "${trouble.message} You can put a new one in Settings, under Developer options.",
        action = MapTroubleAction.OpenSettings
    )

    MapTrouble.DrawnMapSilent -> MapTroubleWords(
        title = "This map isn't drawing",
        body = "The drawn map isn't answering. Aerial imagery comes a different way, through this " +
            "app, so it may still draw.",
        action = MapTroubleAction.SwitchTo(Basemap.LINZ_AERIAL)
    )
}

/** The button's own words for a basemap, in the case a sentence needs rather than a title. */
internal fun offerLabel(basemap: Basemap): String = when (basemap) {
    Basemap.LINZ_AERIAL -> "Use aerial imagery"
    Basemap.OPENSTREETMAP -> "Use OpenStreetMap"
    Basemap.TOPOGRAPHIC -> "Use Topographic (OSM)"
    Basemap.LINZ_TOPO_LIDAR_HILLSHADE -> "Use the Topographic map"
}

/** The words on the notice's one button. */
internal fun actionLabel(action: MapTroubleAction): String = when (action) {
    is MapTroubleAction.SwitchTo -> offerLabel(action.basemap)
    MapTroubleAction.OpenOfflineTab -> "What's downloaded"
    MapTroubleAction.OpenSettings -> "Open Settings"
}

/**
 * What the app's own proxy's answer means, in the notice's terms - the whole of the mapping, and
 * so the whole of what a test has to hold.
 */
internal fun troubleFrom(answer: ProxyHealth): MapTrouble = when (answer) {
    is ProxyHealth.Answered -> MapTrouble.ServiceSaid(answer.message)
    ProxyHealth.Unreachable -> MapTrouble.ServiceSilent
}

/**
 * What the probe of the walker's own key means.
 *
 * **"Could not ask" is not "refused".** A key that LINZ turned down is the walker's to replace, and
 * sending them to Settings is the fix; a probe that never got an answer - DNS, TLS, a dropped
 * connection - says nothing about the key, and pointing at Settings over it would be the app
 * blaming the one thing it has not tested.
 */
internal fun troubleFrom(answer: KeyCheck): MapTrouble = when (answer) {
    is KeyCheck.Failed -> MapTrouble.OwnKeyRefused(answer.message)
    KeyCheck.Works, is KeyCheck.Unreachable -> MapTrouble.ServiceSilent
}
