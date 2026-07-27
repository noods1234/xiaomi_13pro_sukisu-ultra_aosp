package com.oiw.camera.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.oiw.camera.R
import com.oiw.camera.metadata.Slate
import com.oiw.camera.metadata.SlateStore

/**
 * The slate entry screen (docs/CINEMA_FEATURES.md #2 "Slate metadata screen", #9 "Lens metadata
 * mode"). Scene / shot / take, the circled-take flag, and the lens and filter details an adapted
 * lens has no way to report electronically.
 *
 * Everything it writes lands in each clip's JSON sidecar and therefore in `session_index.csv` — the
 * shot list a DIT actually opens. The sidecar fields existed long before this screen did; without
 * it they were simply always null.
 *
 * Field discipline, because this gets used in the dark with cold hands:
 *  - **Nothing is mandatory.** A blank field leaves whatever the capture profile already knew,
 *    rather than overwriting it with nothing (see [Slate.applyTo]).
 *  - **Save is explicit, Next Take is one tap.** Bumping the take is the single most frequent slate
 *    action on a set, so it does not hide behind a keyboard.
 *  - Touch targets follow the 64dp cinema-control sizing in `docs/UI_UX.md`.
 */
class SlateActivity : AppCompatActivity() {

    private val store = SlateStore()

    private lateinit var projectField: EditText
    private lateinit var sceneField: EditText
    private lateinit var shotField: EditText
    private lateinit var takeField: EditText
    private lateinit var circledBox: CheckBox
    private lateinit var lensField: EditText
    private lateinit var focalField: EditText
    private lateinit var apertureField: EditText
    private lateinit var adapterField: EditText
    private lateinit var filterField: EditText
    private lateinit var ndField: EditText
    private lateinit var notesField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slate)

        projectField = findViewById(R.id.slate_project)
        sceneField = findViewById(R.id.slate_scene)
        shotField = findViewById(R.id.slate_shot)
        takeField = findViewById(R.id.slate_take)
        circledBox = findViewById(R.id.slate_circled)
        lensField = findViewById(R.id.slate_lens)
        focalField = findViewById(R.id.slate_focal)
        apertureField = findViewById(R.id.slate_aperture)
        adapterField = findViewById(R.id.slate_adapter)
        filterField = findViewById(R.id.slate_filters)
        ndField = findViewById(R.id.slate_nd)
        notesField = findViewById(R.id.slate_notes)

        render(store.load())

        findViewById<Button>(R.id.slate_save).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.slate_next_take).setOnClickListener {
            // Persist any edits first, so "next take" never silently discards a typed scene number.
            val advanced = readFields().nextTake()
            persist(advanced)
            render(advanced)
        }
        findViewById<Button>(R.id.slate_next_shot).setOnClickListener {
            val advanced = readFields().nextShot(shotField.text.toString().takeIf { it.isNotBlank() })
            persist(advanced)
            render(advanced)
        }
    }

    /** Anything typed is saved on the way out too — a back-press must not lose a slate. */
    override fun onPause() {
        super.onPause()
        if (isFinishing) persist(readFields())
    }

    private fun saveAndFinish() {
        if (persist(readFields())) finish()
    }

    private fun persist(slate: Slate): Boolean {
        val ok = store.save(slate)
        if (!ok) {
            // Specific, actionable — never "something went wrong" (docs/UI_UX.md §5).
            Toast.makeText(
                this,
                "Could not write the slate to ${com.oiw.camera.util.OiwPaths.mediaRoot()}. " +
                    "Check storage permission and free space; recording is unaffected.",
                Toast.LENGTH_LONG,
            ).show()
        }
        return ok
    }

    private fun render(slate: Slate) {
        projectField.setText(slate.project)
        sceneField.setText(slate.scene.orEmpty())
        shotField.setText(slate.shot.orEmpty())
        takeField.setText(slate.take.toString())
        circledBox.isChecked = slate.circledTake
        lensField.setText(slate.lensName.orEmpty())
        focalField.setText(slate.focalLengthMm?.toString().orEmpty())
        apertureField.setText(slate.aperture.orEmpty())
        adapterField.setText(slate.adapter.orEmpty())
        filterField.setText(slate.filterStack.joinToString(", "))
        ndField.setText(slate.ndValue.orEmpty())
        notesField.setText(slate.notes.orEmpty())
    }

    private fun readFields() = Slate(
        project = projectField.text.toString().ifBlank { "untitled" },
        scene = sceneField.text.toString().takeIf { it.isNotBlank() },
        shot = shotField.text.toString().takeIf { it.isNotBlank() },
        // A garbled take number must not crash the slate or reset the shoot to take 1 silently;
        // fall back to what is already stored.
        take = takeField.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: store.load().take,
        circledTake = circledBox.isChecked,
        lensName = lensField.text.toString().takeIf { it.isNotBlank() },
        focalLengthMm = focalField.text.toString().trim().toDoubleOrNull(),
        aperture = apertureField.text.toString().takeIf { it.isNotBlank() },
        adapter = adapterField.text.toString().takeIf { it.isNotBlank() },
        filterStack = filterField.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        ndValue = ndField.text.toString().takeIf { it.isNotBlank() },
        notes = notesField.text.toString().takeIf { it.isNotBlank() },
    )
}
