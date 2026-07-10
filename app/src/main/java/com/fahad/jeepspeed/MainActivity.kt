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
 *
 * 90m Sand Drag ET estimate:
 * Uses the standard drag-racing quarter-mile ET/trap-speed formulas
 *   ET_quarter (sec)   = 5.825 x (Weight_lbs / HP)^(1/3)
 *   Speed_quarter (mph) = 234   x (HP / Weight_lbs)^(1/3)
 * then scales them to 90m using constant-power kinematics, where for a
 * fixed weight/power vehicle distance and time relate as d ~ t^(3/2):
 *   ET_90    = ET_quarter    x (90 / 402.336)^(2/3)
 *   Speed_90 = Speed_quarter x (90 / 402.336)^(1/3)
 * (402.336m = 1/4 mile). This is a physics-based estimate calibrated off
 * pavement drag-strip data -- real sand times depend heavily on traction,
 * sand condition, tire choice, and launch technique.
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
    private lateinit var cardSandDrag: androidx.cardview.widget.CardView
    private lateinit var textSandDragResult: TextView
    private lateinit var textSandDragSpeed: TextView

    companion object {
        private const val MPH_CONSTANT = 336.0
        private const val KMH_PER_MPH = 1.60934
        private val TABLE_RPMS = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000)

        private const val QUARTER_MILE_METERS = 402.336
        private const val SAND_DRAG_METERS = 90.0
        private const val ET_CONSTANT = 5.825
        private const val TRAP_SPEED_CONSTANT = 234.0
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
        cardSandDrag = findViewById(R.id.cardSandDrag)
        textSandDragResult = findViewById(R.id.textSandDragResult)
        textSandDragSpeed = findViewById(R.id.textSandDragSpeed)

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
        val weight = editWeight.valueOrNull()
        val horsepower = editHorsepower.valueOrNull()

        if (weight == null || weight <= 0 || horsepower == null || horsepower <= 0) {
            cardSandDrag.visibility = android.view.View.GONE
            return
        }

        val distanceScale = SAND_DRAG_METERS / QUARTER_MILE_METERS
        val etQuarterMile = ET_CONSTANT * Math.cbrt(weight / horsepower)
        val trapSpeedQuarterMile = TRAP_SPEED_CONSTANT * Math.cbrt(horsepower / weight)

        val et90m = etQuarterMile * Math.pow(distanceScale, 2.0 / 3.0)
        val speed90m = trapSpeedQuarterMile * Math.pow(distanceScale, 1.0 / 3.0)

        textSandDragResult.text = String.format(Locale.US, "90m ET: %.2f sec", et90m)
        textSandDragSpeed.text = String.format(Locale.US, "Estimated speed at 90m: %.1f mph", speed90m)
        cardSandDrag.visibility = android.view.View.VISIBLE
    }
}
