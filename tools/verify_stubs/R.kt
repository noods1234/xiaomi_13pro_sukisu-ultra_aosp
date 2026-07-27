package com.oiw.camera

// Stand-in for the AGP-generated R class (tier 3 — the weakest evidence tier; see
// tools/verify_stubs/README.md). Every id referenced from Kotlin must appear here, or the offline
// harness cannot type-check the Activity that uses it.
//
// This does NOT prove the id exists in res/ — only `./gradlew assembleDebug` does. Adding an entry
// here for a view that is missing from the layout will compile happily and then fail at runtime on
// a null findViewById.
object R {
  object layout { const val activity_camera = 1; const val activity_slate = 2 }
  object id { const val preview_texture_view=1; const val overlay_container=2; const val status_bar=3
              const val record_button=4; const val still_button=5; const val profile_button=6
              const val slate_button=7
              const val slate_project=10; const val slate_scene=11; const val slate_shot=12
              const val slate_take=13; const val slate_circled=14; const val slate_lens=15
              const val slate_focal=16; const val slate_aperture=17; const val slate_adapter=18
              const val slate_filters=19; const val slate_nd=20; const val slate_notes=21
              const val slate_save=22; const val slate_next_take=23; const val slate_next_shot=24 }
  object string { const val app_name=1; const val record_button=2 }
  object drawable { const val ic_record_indicator=1 }
}
