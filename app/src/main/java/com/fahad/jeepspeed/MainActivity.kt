package com.fahad.jeepspeed

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
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
 *
 * 90m Sand Drag ET estimate (physics simulation):
 * Rather than scaling a pavement drag-strip formula, this steps Newton's
 * second law forward in small time increments:
 *   F_drive  = min(Power / v, mu_traction * m * g)   -- traction-limited at
 *              launch (low v), power-limited once P/v drops below the max
 *              tractive force the sand can support
 *   F_resist = Crr * m * g                            -- rolling resistance
 *   a        = (F_drive - F_resist) / m
 *   v(t+dt)  = v(t) + a*dt ;  d(t+dt) = d(t) + v*dt
 * until d reaches 90m (or the vehicle can't out-accelerate resistance, in
 * which case there's no result).
 *
 * Rolling resistance coefficients (Crr) for compacted sand are documented in
 * terramechanics literature at roughly 0.10-0.155 (e.g. Coutermarsh,
 * "Velocity effect of vehicle rolling resistance in sand", J. Terramechanics).
 * Looser/softer sand increases both rolling resistance and wheel slip
 * (reducing usable traction) well beyond that range; the values below extend
 * that published range using standard off-road engineering assumptions for
 * looser conditions -- they are estimates, not measured constants for any
 * specific vehicle/tire combination. Real ETs vary with tire choice
 * (paddle vs all-terrain), moisture, tire pressure, and launch technique.
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

    private lateinit var editWeight: EditText
    private lateinit var editHorsepower: EditText
    private lateinit var spinnerSandCondition: Spinner
    private lateinit var cardSandDrag: androidx.cardview.widget.CardView
    private lateinit var textSandDragResult: TextView
    private lateinit var textSandDragSpeed: TextView

    private data class SandCondition(val label: String, val crr: Double, val muTraction: Double)

    companion object {
        private const val MPH_CONSTANT = 336.0
        private const val KMH_PER_MPH = 1.60934
        private val TABLE_RPMS = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000)

        private const val GRAVITY = 9.81
        private const val HP_TO_WATTS = 745.7
        private const val MPS_TO_MPH = 2.23694
        private const val SAND_DRAG_METERS = 90.0
        private const val SIM_DT = 0.02
        private const val SIM_MAX_TIME = 60.0

        private val SAND_CONDITIONS = listOf(
            SandCondition("Hard-packed / wet sand", crr = 0.06, muTraction = 0.60),
            SandCondition("Medium / groomed sand", crr = 0.11, muTraction = 0.42),
            SandCondition("Soft sand", crr = 0.18, muTraction = 0.30),
            SandCondition("Loose / deep sand", crr = 0.25, muTraction = 0.30)
        )
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

        editWeight = findViewById(R.id.editWeight)
        editHorsepower = findViewById(R.id.editHorsepower)
        spinnerSandCondition = findViewById(R.id.spinnerSandCondition)
        cardSandDrag = findViewById(R.id.cardSandDrag)
        textSandDragResult = findViewById(R.id.textSandDragResult)
        textSandDragSpeed = findViewById(R.id.textSandDragSpeed)

        spinnerSandCondition.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            SAND_CONDITIONS.map { it.label }
        )

        findViewById<android.widget.Button>(R.id.buttonCalculate).setOnClickListener {
            calculate()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { calculate() }
        }
        listOf(
            editTireDiameter, editAxleRatio, editTransferCase, editGearRatio, editRpm,
            editWeight, editHorsepower
        ).forEach { it.addTextChangedListener(watcher) }

        spinnerSandCondition.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculate()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
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

        calculateSandDrag()
    }

    private fun speedMph(rpm: Double, tireDiameter: Double, totalRatio: Double): Double {
        return (rpm * tireDiameter) / (totalRatio * MPH_CONSTANT)
    }

    private fun calculateSandDrag() {
        val weightKg = editWeight.valueOrNull()
        val horsepower = editHorsepower.valueOrNull()

        if (weightKg == null || weightKg <= 0 || horsepower == null || horsepower <= 0) {
            cardSandDrag.visibility = View.GONE
            return
        }

        val condition = SAND_CONDITIONS[spinnerSandCondition.selectedItemPosition.coerceIn(0, SAND_CONDITIONS.size - 1)]
        val result = simulateSandDrag(weightKg, horsepower, condition)

        if (result == null) {
            textSandDragResult.text = "90m ET: not enough traction/power"
            textSandDragSpeed.text = "Try a lower weight, more HP, or a firmer sand condition."
            cardSandDrag.visibility = View.VISIBLE
            return
        }

        val (etSeconds, speedMps) = result
        val speedMph = speedMps * MPS_TO_MPH

        textSandDragResult.text = String.format(Locale.US, "90m ET: %.2f sec (%s)", etSeconds, condition.label)
        textSandDragSpeed.text = String.format(Locale.US, "Estimated speed at 90m: %.1f mph", speedMph)
        cardSandDrag.visibility = View.VISIBLE
    }

    /**
     * Steps Newton's second law forward at SIM_DT increments until the
     * vehicle covers SAND_DRAG_METERS. Returns (elapsed seconds, speed m/s)
     * at that point, or null if the vehicle can't out-accelerate rolling
     * resistance (or doesn't reach 90m within SIM_MAX_TIME).
     */
    private fun simulateSandDrag(weightKg: Double, horsepower: Double, condition: SandCondition): Pair<Double, Double>? {
        val powerWatts = horsepower * HP_TO_WATTS
        val resistForce = condition.crr * weightKg * GRAVITY
        val maxTractionForce = condition.muTraction * weightKg * GRAVITY

        var t = 0.0
        var v = 0.0
        var d = 0.0

        while (t < SIM_MAX_TIME) {
            val driveForce = if (v < 0.5) maxTractionForce else minOf(powerWatts / v, maxTractionForce)
            val netForce = driveForce - resistForce
            if (netForce <= 0.0) return null

            val acceleration = netForce / weightKg
            v += acceleration * SIM_DT
            d += v * SIM_DT
            t += SIM_DT

            if (d >= SAND_DRAG_METERS) return Pair(t, v)
        }
        return null
    }
}
