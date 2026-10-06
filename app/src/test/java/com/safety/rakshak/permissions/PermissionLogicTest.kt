package com.safety.rakshak.permissions

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionLogicTest {

    @Test
    fun `granted wins over everything`() {
        for (asked in listOf(true, false)) for (rationale in listOf(true, false)) {
            assertEquals(GrantState.GRANTED, grantState(true, asked, rationale))
        }
    }

    @Test
    fun `never asked - can ask even though rationale is false`() {
        assertEquals(GrantState.CAN_ASK, grantState(granted = false, askedBefore = false, shouldShowRationale = false))
    }

    @Test
    fun `denied once - system dialog can still be shown`() {
        assertEquals(GrantState.CAN_ASK, grantState(granted = false, askedBefore = true, shouldShowRationale = true))
    }

    @Test
    fun `denied with don't ask again - only Settings can help`() {
        assertEquals(
            GrantState.NEEDS_SETTINGS,
            grantState(granted = false, askedBefore = true, shouldShowRationale = false)
        )
    }

    @Test
    fun `approximate location is enough, precise is reported separately`() {
        assertEquals(LocationPrecision.NONE, locationPrecision(fineGranted = false, coarseGranted = false))
        assertEquals(LocationPrecision.APPROXIMATE, locationPrecision(fineGranted = false, coarseGranted = true))
        assertEquals(LocationPrecision.PRECISE, locationPrecision(fineGranted = true, coarseGranted = true))
        assertEquals(LocationPrecision.PRECISE, locationPrecision(fineGranted = true, coarseGranted = false))
    }
}
