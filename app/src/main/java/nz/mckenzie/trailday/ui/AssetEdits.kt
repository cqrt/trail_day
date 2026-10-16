package nz.mckenzie.trailday.ui

import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind

/** What the edit form made of what was typed into it. */
sealed interface AssetEditResult {

    /** The asset to store. */
    data class Ok(val asset: AssetEntity) : AssetEditResult

    /** What to tell the walker, in words that say what to fix. */
    data class Invalid(val message: String) : AssetEditResult
}

/**
 * Everything the edit form was holding, as typed or as chosen.
 *
 * Gathered into one value rather than passed as loose arguments: a form is exactly where
 * two neighbouring names can be swapped without the compiler noticing.
 */
data class AssetEditFields(
    val name: String,
    val kind: AssetKind,
    val notes: String
)

/**
 * The editable fields of an asset.
 *
 * The form is text, so every field needs a decision about what a blank or a stray space means.
 * Those decisions are worth testing on their own, which is why this is pure and lives outside
 * the view model: the screen only has to show the message.
 *
 * The kind is the exception: it is a choice between named states rather than something typed,
 * so it arrives already decided. The shape is not a field at all - it is decided by the kind
 * (see [nz.mckenzie.trailday.domain.asset.AssetKind.shape]).
 */
object AssetEdits {

    fun apply(asset: AssetEntity, fields: AssetEditFields): AssetEditResult {
        val cleanName = fields.name.trim()
        if (cleanName.isEmpty()) {
            return AssetEditResult.Invalid("Give it a name so it can be found later")
        }

        return AssetEditResult.Ok(
            asset = asset.copy(
                name = cleanName,
                kind = fields.kind.name,
                // Written from the kind rather than from a second answer, so a stored row can only
                // say one thing: a picnic table is a place because a picnic table is a place.
                shape = fields.kind.shape.name,
                notes = fields.notes.trim().ifBlank { null }
            )
        )
    }
}
