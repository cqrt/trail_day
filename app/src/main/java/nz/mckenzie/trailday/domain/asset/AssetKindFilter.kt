package nz.mckenzie.trailday.domain.asset

/**
 * What the place list is showing: every kind of place, or one of them.
 *
 * A **question being asked now** rather than a way of working, which is why nothing stores it. The
 * map's layer switches are remembered because a walker who hides the huts wants them hidden
 * tomorrow as well; "where are my huts?" is asked, answered and put down. Nothing stored also
 * means an install that never taps a chip cannot be changed by this feature at all.
 *
 * The answer goes through [AssetKind.fromStorage] - the same reading the rows, the picker and the
 * map use.
 */
data class AssetKindFilter(val kind: AssetKind?) {

    /** True for a row stored as [storedKind], which is as the database keeps it. */
    fun keeps(storedKind: String): Boolean = kind == null ||
        AssetKind.fromStorage(storedKind) == kind

    companion object {

        /** Everything the phone has, which is what the list shows until a chip is tapped. */
        val All = AssetKindFilter(null)

        /**
         * The filter after a chip is tapped: that kind, or - the chip already showing - all of them
         * again. The chips are the only way in, so one of them has to be the way back out, and
         * "All" is both the first chip and the answer to tapping it.
         */
        fun afterTapping(kind: AssetKind?, showing: AssetKindFilter): AssetKindFilter =
            if (showing.kind == kind) All else AssetKindFilter(kind)
    }
}
