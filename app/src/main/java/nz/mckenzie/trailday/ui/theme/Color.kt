package nz.mckenzie.trailday.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The brand seed green: the colour of the mock screens' buttons and the app's own since it was built.
val GreenPrimary = Color(0xFF2E7D32)
val GreenPrimaryDark = Color(0xFF81C784)

/**
 * The light scheme: **the mock screens' surfaces.**
 *
 * A pale sage page with white cards on it, rather than Material's near-white page with grey-green cards:
 * the mock screens are a walker's outdoor palette, and the difference is what makes a card read as a card.
 * Text is a soft near-black rather than pure black, and the muted text is a grey-green, the way the mock
 * screens write the supporting line under a place.
 *
 * Every role is set, not just primary, secondary and tertiary - setting three of them leaves every
 * container and surface role on Material's baseline palette, and that lavender was once the legend card,
 * the rows and the body of every dialog.
 *
 * The tones are the Material 3 tonal palettes for the seed, read out of Google's own colour utilities with
 * the Tonal Spot variant, with three deliberate departures: `primary` stays the brand green rather than
 * the generated tone 40, `surfaceTint` follows it, and the tertiary family comes from a second palette
 * seeded with the old earth brown, because that role was the app's warm accent.
 */
internal val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCF0B4),
    onPrimaryContainer = Color(0xFF002204),
    inversePrimary = Color(0xFFA1D39A),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E8CE),
    onSecondaryContainer = Color(0xFF111F0F),
    tertiary = Color(0xFF695E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2E3A8),
    onTertiaryContainer = Color(0xFF211B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    // The page: the mock screens' pale sage.
    background = Color(0xFFF4F7EC),
    onBackground = Color(0xFF1B1D18),
    surface = Color(0xFFF4F7EC),
    onSurface = Color(0xFF1B1D18),
    surfaceVariant = Color(0xFFE4EADD),
    onSurfaceVariant = Color(0xFF45493F),
    surfaceTint = GreenPrimary,
    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEFF2E9),
    outline = Color(0xFF757A6D),
    outlineVariant = Color(0xFFC6CCBC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9FCF3),
    surfaceDim = Color(0xFFDCE3D2),
    // Cards and sheets are white, whichever container role a component reaches for.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFBFDF6),
    surfaceContainerHigh = Color(0xFFEDF1E4),
    surfaceContainerHighest = Color(0xFFFFFFFE)
)

/**
 * The dark scheme, **derived from the light one rather than invented separately**: the same roles, the same
 * sage family taken to a dark tone, and the same kind colours' hues at a lighter weight - see
 * [nz.mckenzie.trailday.map.AssetColors.forKindOnDark] for that half.
 */
internal val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    // Not white: on a light green the label has to go dark.
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFBCF0B4),
    inversePrimary = GreenPrimary,
    secondary = Color(0xFFB9CCB2),
    onSecondary = Color(0xFF243422),
    secondaryContainer = Color(0xFF3A4B37),
    onSecondaryContainer = Color(0xFFD6E8CE),
    tertiary = Color(0xFFD5C68E),
    onTertiary = Color(0xFF383005),
    tertiaryContainer = Color(0xFF50461A),
    onTertiaryContainer = Color(0xFFF2E3A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12150F),
    onBackground = Color(0xFFE3E7DA),
    surface = Color(0xFF12150F),
    onSurface = Color(0xFFE3E7DA),
    surfaceVariant = Color(0xFF43483E),
    onSurfaceVariant = Color(0xFFC3C9B9),
    surfaceTint = GreenPrimaryDark,
    inverseSurface = Color(0xFFE3E7DA),
    inverseOnSurface = Color(0xFF2D322C),
    outline = Color(0xFF8D9384),
    outlineVariant = Color(0xFF43483E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383D33),
    surfaceDim = Color(0xFF12150F),
    surfaceContainerLowest = Color(0xFF0D100A),
    surfaceContainerLow = Color(0xFF1D211A),
    surfaceContainer = Color(0xFF1A1E17),
    surfaceContainerHigh = Color(0xFF232A20),
    surfaceContainerHighest = Color(0xFF1D211A)
)
