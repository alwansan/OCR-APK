package com.alwansan.crashlogocr

import android.os.Bundle
import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alwansan.crashlogocr.ocr.EngineComparison
import com.alwansan.crashlogocr.ocr.OcrEngine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/** In-app offline A/B experiment using exactly one source image.
 * Outputs stay in RAM; a user must explicitly tap Copy/Share.
 */
class EngineLabActivity : AppCompatActivity() {
    private lateinit var pick: MaterialButton
    private lateinit var status: TextView
    private lateinit var agreement: TextView
    private lateinit var firstName: TextView
    private lateinit var secondName: TextView
    private lateinit var firstText: TextView
    private lateinit var secondText: TextView
    private lateinit var firstCopy: MaterialButton
    private lateinit var secondCopy: MaterialButton
    private var busy = false

    private val sourcePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            pick.isEnabled = false
            status.setText(R.string.engine_lab_working)
            agreement.text = ""
            firstText.text = ""
            secondText.text = ""
            OcrEngine.compareEngines(this, uri) { results ->
                busy = false
                if (isFinishing || isDestroyed) return@compareEngines
                pick.isEnabled = true
                status.setText(R.string.engine_lab_finished)
                val first = results.getOrNull(0)
                val second = results.getOrNull(1)
                firstName.text = formatName(first)
                secondName.text = formatName(second)
                firstText.text = first?.output?.rawText
                    ?: first?.error ?: getString(R.string.engine_lab_empty)
                secondText.text = second?.output?.rawText
                    ?: second?.error ?: getString(R.string.engine_lab_empty)
                firstCopy.isEnabled = first?.output != null
                secondCopy.isEnabled = second?.output != null
                if (first?.output != null && second?.output != null) {
                    val diff = EngineComparison.compare(
                        first.output.rawText, second.output.rawText)
                    agreement.text = getString(R.string.engine_lab_comparison,
                        diff.commonLines, diff.onlyFirst.size, diff.onlySecond.size)
                } else {
                    agreement.setText(R.string.engine_lab_partial)
                }
            }
        }
    }

    private fun formatName(value: OcrEngine.EngineSample?): String =
        value?.let { "${it.name} · ${it.durationMs} ms" }
            ?: getString(R.string.engine_lab_empty)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine_lab)
        findViewById<MaterialToolbar>(R.id.labToolbar).setNavigationOnClickListener { finish() }
        pick = findViewById(R.id.labPick)
        status = findViewById(R.id.labStatus)
        agreement = findViewById(R.id.labAgreement)
        firstName = findViewById(R.id.labFirstName)
        secondName = findViewById(R.id.labSecondName)
        firstText = findViewById(R.id.labFirstText)
        secondText = findViewById(R.id.labSecondText)
        firstCopy = findViewById(R.id.labCopyFirst)
        secondCopy = findViewById(R.id.labCopySecond)
        firstCopy.isEnabled = false
        secondCopy.isEnabled = false
        firstText.setTextIsSelectable(true)
        secondText.setTextIsSelectable(true)
        pick.setOnClickListener { if (!busy) sourcePicker.launch("image/*") }
        firstCopy.setOnClickListener { ClipboardUtils.copy(this, firstText.text.toString())
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show() }
        secondCopy.setOnClickListener { ClipboardUtils.copy(this, secondText.text.toString())
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show() }
    }
}
