package com.oiw.camera.overlay

/**
 * The vocabulary of a capture profile's `monitoringOverlays` list (docs/CINEMA_FEATURES.md #2).
 *
 * Exists because these ids were previously bare string literals compared in `CameraActivity`, which
 * made two silent failures possible and both had actually happened:
 *
 *  - **A typo does nothing, quietly.** `"rgb-parade"` in a profile matches no branch, so the scope
 *    simply never appears and nothing reports why.
 *  - **A feature can be "wired" and still unreachable.** `waveform` and `vectorscope` were built,
 *    tested, benchmarked and hooked into the analysis path — yet no shipped profile listed them, so
 *    no user could ever turn one on. The wiring guard passed because the *code* referenced them.
 *
 * `ProfileOverlayVocabularyTest` (tier 1) pins both directions against the shipped assets: every id
 * a profile uses must be known here, and every id known here must be enabled by at least one
 * profile. See `docs/AUDIT_FINDINGS.md` findings W and X.
 */
object MonitoringOverlays {
    /** Luma histogram, top-right. */
    const val HISTOGRAM = "histogram"

    /** Over-exposure hatching above a luma threshold. */
    const val ZEBRA = "zebra"

    /** Sobel edge highlight for manual focus. */
    const val FOCUS_PEAKING = "focus_peaking"

    /** IRE-banded exposure map. Replaces the image, so it is opt-in per profile. */
    const val FALSE_COLOR = "false_color"

    /** Luma waveform, bottom-left. */
    const val WAVEFORM = "waveform"

    /** Chroma density on the U/V plane, bottom-right. */
    const val VECTORSCOPE = "vectorscope"

    /** Per-channel column histograms, bottom. */
    const val RGB_PARADE = "rgb_parade"

    /** Aspect-ratio and safe-area boxes. */
    const val FRAME_GUIDES = "frame_guides"

    /**
     * Preview-only LUT. Handled by the GL preview path, **not** by `OverlayView` — and that path is
     * not built yet (`tools/unwired_allowlist.txt`), so listing this in a profile currently has no
     * visible effect. Kept in the vocabulary so profiles can declare intent without tripping the
     * unknown-id check, and named here so the gap is visible rather than mysterious.
     */
    const val LUT_PREVIEW = "lut_preview"

    /**
     * Non-uniform preview stretch from the profile's squeeze factor. Applied to the `TextureView`
     * transform, not by `OverlayView`. Preview only — recorded geometry is never touched.
     */
    const val ANAMORPHIC_DESQUEEZE = "anamorphic_desqueeze"

    /** Every id a capture profile may legally name. */
    val ALL: Set<String> = setOf(
        HISTOGRAM,
        ZEBRA,
        FOCUS_PEAKING,
        FALSE_COLOR,
        WAVEFORM,
        VECTORSCOPE,
        RGB_PARADE,
        FRAME_GUIDES,
        LUT_PREVIEW,
        ANAMORPHIC_DESQUEEZE,
    )

    /** Ids handled outside [OverlayView]; excluded from the view-flag mapping. */
    val HANDLED_ELSEWHERE: Set<String> = setOf(LUT_PREVIEW, ANAMORPHIC_DESQUEEZE)

    /**
     * The profile's overlay list as an on/off table, one entry per [OverlayView]-rendered id.
     *
     * Lives in main code rather than in a test helper so there is exactly one mapping: a pure-JVM
     * test can assert the table, and a Robolectric test can assert the real view agrees with it,
     * without either one re-deriving what "on" means.
     */
    fun viewFlags(overlays: List<String>): Map<String, Boolean> =
        (ALL - HANDLED_ELSEWHERE).associateWith { it in overlays }

    /** Ids a profile named that this build does not understand — surfaced to the user, not swallowed. */
    fun unknownIn(overlays: List<String>): List<String> = overlays.filterNot { it in ALL }
}
