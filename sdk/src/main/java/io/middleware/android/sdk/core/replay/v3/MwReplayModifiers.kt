package io.middleware.android.sdk.core.replay.v3

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

/**
 * Jetpack Compose modifiers controlling masking in v3 session recording.
 * The same markers work on classic views via `android:tag` or
 * `contentDescription` set to [MW_NO_CAPTURE_LABEL] / [MW_NO_MASK_LABEL].
 */
public object MwReplayModifiers {
    public const val MW_NO_CAPTURE_LABEL: String = "mw-no-capture"
    public const val MW_NO_MASK_LABEL: String = "mw-no-mask"

    internal val MwReplayMask = SemanticsPropertyKey<Boolean>(MW_NO_CAPTURE_LABEL)
    internal val MwReplayUnmask = SemanticsPropertyKey<Boolean>(MW_NO_MASK_LABEL)

    /**
     * Masks the element (and its descendants) in session recording.
     */
    public fun Modifier.mwSessionReplayMask(isEnabled: Boolean = true): Modifier {
        return semantics(
            properties = {
                this[MwReplayMask] = isEnabled
            },
        )
    }

    /**
     * Excludes the element (and its descendants) from masking even when
     * `maskAllTextInputs` / `maskAllImages` are on. Takes precedence over
     * [mwSessionReplayMask].
     */
    public fun Modifier.mwSessionReplayUnmask(isEnabled: Boolean = true): Modifier {
        return semantics(
            properties = {
                this[MwReplayUnmask] = isEnabled
            },
        )
    }
}
