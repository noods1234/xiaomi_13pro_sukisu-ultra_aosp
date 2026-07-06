package com.oiw.camera.thermal

import android.os.PowerManager
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalMonitorTest {

    @Test
    fun `long take safe mode force-stops earlier than max quality mode`() {
        val status = PowerManager.THERMAL_STATUS_MODERATE

        val longTakeAction = ThermalMonitor.decide(ThermalMonitor.Mode.LONG_TAKE_SAFE, status)
        val maxQualityAction = ThermalMonitor.decide(ThermalMonitor.Mode.MAX_QUALITY_SHORT_TAKE, status)

        assertTrue(
            "long_take_safe should force-stop at MODERATE",
            longTakeAction is ThermalMonitor.RecordingAction.ForceStop,
        )
        assertTrue(
            "max_quality_short_take should only warn at MODERATE",
            maxQualityAction is ThermalMonitor.RecordingAction.Warn,
        )
    }

    @Test
    fun `below warning threshold continues recording`() {
        val action = ThermalMonitor.decide(ThermalMonitor.Mode.BALANCED_FIELD, PowerManager.THERMAL_STATUS_NONE)
        assertTrue(action is ThermalMonitor.RecordingAction.Continue)
    }
}
