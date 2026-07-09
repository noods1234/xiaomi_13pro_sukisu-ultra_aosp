package com.oiw.camera.thermal

import android.content.Context
import android.os.PowerManager
import java.io.File

/**
 * Thermal awareness (docs/THERMAL_POWER.md). Primary signal is the public
 * PowerManager thermal-status API; raw /sys/class/thermal reads (via root, already granted by
 * SukiSU Ultra) are used only for finer-grained trend detection, never as the sole gate for the
 * forced-stop decision, since the public API is guaranteed to exist across HyperOS versions.
 */
class ThermalMonitor(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun currentStatus(): Int = powerManager.currentThermalStatus

    /** Raw sysfs zone read, root-gated. Used only for trend detection, never the sole forced-stop signal. */
    fun readRawZonesViaRoot(): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        val thermalDir = File("/sys/class/thermal")
        val zones = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyList()
        zones.forEach { zoneDir ->
            val typeFile = File(zoneDir, "type")
            val tempFile = File(zoneDir, "temp")
            val type = runCatching { typeFile.readText().trim() }.getOrNull() ?: return@forEach
            val temp = runCatching { tempFile.readText().trim().toInt() }.getOrNull() ?: return@forEach
            results += type to temp
        }
        return results
    }

    enum class Mode(
        val label: String,
        val warningStatus: Int,
        val forcedStopStatus: Int,
    ) {
        MAX_QUALITY_SHORT_TAKE("max_quality_short_take", PowerManager.THERMAL_STATUS_MODERATE, PowerManager.THERMAL_STATUS_SEVERE),
        BALANCED_FIELD("balanced_field", PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_SEVERE),
        LONG_TAKE_SAFE("long_take_safe", PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE),
        EXTERNAL_COOLING("external_cooling", PowerManager.THERMAL_STATUS_SEVERE, PowerManager.THERMAL_STATUS_CRITICAL),
        CHARGING_SAFE("charging_safe", PowerManager.THERMAL_STATUS_MODERATE, PowerManager.THERMAL_STATUS_SEVERE),
        STEALTH_LOW_HEAT("stealth_low_heat", PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE),
        DEBUG_UNRESTRICTED("debug_unrestricted", PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_EMERGENCY),
    }

    sealed interface RecordingAction {
        object Continue : RecordingAction
        data class Warn(val message: String) : RecordingAction
        data class ForceStop(val reason: String) : RecordingAction
    }

    companion object {
        /** Maps a capture profile's thermalProfileId to a Mode (stub-audit wiring aid). */
        fun modeForLabel(label: String): Mode =
            Mode.values().firstOrNull { it.label == label } ?: Mode.BALANCED_FIELD

        /** Pure function, unit-testable without a real device or Context (docs/TEST_PLAN.md #1). */
        fun decide(mode: Mode, status: Int): RecordingAction = when {
            status >= mode.forcedStopStatus -> RecordingAction.ForceStop(
                "Thermal status reached ${statusName(status)} under ${mode.label}. Finalizing current segment " +
                    "and stopping recording — footage safety takes priority over runtime."
            )
            status >= mode.warningStatus -> RecordingAction.Warn(
                "Thermal headroom low (status: ${statusName(status)}). Consider switching to a cooler profile " +
                    "or enabling external cooling."
            )
            else -> RecordingAction.Continue
        }

        fun statusName(status: Int): String = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "NONE"
            PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "UNKNOWN($status)"
        }
    }
}
