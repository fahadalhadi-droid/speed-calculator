package com.fahad.jeepspeed

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Jeep Speed Calculator
 *
 * Standard drivetrain speed formula:
 *   MPH = (RPM x Tire Diameter[in]) / (Total Ratio x 336)
 *
 * Where Total Ratio = Axle Ratio x Transfer Case Ratio x Transmission Gear Ratio
 * (336 converts tire circumference/RPM into miles-per-hour).
 *
 * KM/H = MPH x 1.60934
 */
class MainActivity : AppCompatActivity() {

    private lateinit var editTireDiameter: EditText
    private lateinit var editAxleRatio: EditText
    private lateinit var editTransferCase: EditText
    private lateinit var editGearRatio: EditText
    private lateinit var editRpm: EditText
    private lateinit var textSpeedResult: TextView
    private lateinit var textTotalRatio: TextView
    private lateinit var textSpeedTable: TextView

    companion object {
        private const val MPH_CONSTANT = 336.0
        private const val KMH_PER_MPH = 1.60934
        private val TABLE_RPMS = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editTireDiameter = findViewById(R.id.editTireDiameter)
        editAxleRatio = findViewById(R.id.editAxleRatio)
        editTransferCase = findViewById(R.id.editTransferCase)
        editGearRatio = findViewById(R.id.editGearRatio)
        editRpm = findViewById(R.id.editRpm)
        textSpeedResult = findViewById(R.id.textSpeedResult)
        textTotalRatio = findViewById(R.id.textTotalRatio)
        textSpeedTable = findViewById(R.id.textSpeedTable)

        findViewById<android.widget.Button>(R.id.buttonCalculate).setOnClickListener {
            calculate()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { calculate() }
        }
        listOf(editTireDiameter, editAxleRatio, editTransferCase, editGearRatio, editRpm)
            .forEach { it.addTextChangedListener(watcher) }
    }

    private fun EditText.valueOrNull(): Double? = text.toString().trim().toDoubleOrNull()

    private fun calculate() {
        val tireDiameter = editTireDiameter.valueOrNull()
        val axleRatio = editAxleRatio.valueOrNull()
        val transferCase = editTransferCase.valueOrNull()
        val gearRatio = editGearRatio.valueOrNull()
        val rpm = editRpm.valueOrNull()

        if (tireDiameter == null || tireDiameter <= 0 ||
            axleRatio == null || axleRatio <= 0 ||
            transferCase == null || transferCase <= 0 ||
            gearRatio == null || gearRatio <= 0
        ) {
            textSpeedResult.text = "Speed: -- mph  /  -- km/h"
            textTotalRatio.text = "Total gear reduction: --:1"
            textSpeedTable.text = "Fill in tire diameter, axle ratio, transfer case ratio, and gear ratio."
            return
        }

        val totalRatio = axleRatio * transferCase * gearRatio
        textTotalRatio.text = String.format(Locale.US, "Total gear reduction: %.3f:1", totalRatio)

        if (rpm != null && rpm > 0) {
            val mph = speedMph(rpm, tireDiameter, totalRatio)
            val kmh = mph * KMH_PER_MPH
            textSpeedResult.text = String.format(Locale.US, "Speed: %.1f mph  /  %.1f km/h", mph, kmh)
        } else {
            textSpeedResult.text = "Speed: -- mph  /  -- km/h"
        }

        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "%-6s %10s %10s\n", "RPM", "MPH", "KM/H"))
        for (r in TABLE_RPMS) {
            val mph = speedMph(r.toDouble(), tireDiameter, totalRatio)
            val kmh = mph * KMH_PER_MPH
            sb.append(String.format(Locale.US, "%-6d %10.1f %10.1f\n", r, mph, kmh))
        }
        textSpeedTable.text = sb.toString()
    }

    private fun speedMph(rpm: Double, tireDiameter: Double, totalRatio: Double): Double {
        return (rpm * tireDiameter) / (totalRatio * MPH_CONSTANT)
    }
}
