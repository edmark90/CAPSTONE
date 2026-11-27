package com.example.smartplantcare

import com.example.smartplantcare.iot.CloudinaryCropPhotoClient
import com.example.smartplantcare.iot.DeviceRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudinaryCropPhotoTest {

    @Test
    fun cloudinarySignature_matchesOfficialSpec() {
        // Official Cloudinary signature specification example
        val sig = CloudinaryCropPhotoClient.computeSignature(
            params = mapOf(
                "timestamp" to "1315060510",
                "public_id" to "sample_image",
                "eager" to "w_400,h_300,c_pad|w_260,h_200,c_crop"
            ),
            apiSecret = "abcd"
        )
        assertEquals("bfd09f95f331f558cbd1320e67aa8d488770583e", sig)
    }

    @Test
    fun cloudinarySignature_sortsKeysAndIgnoresBlankValues() {
        val sigA = CloudinaryCropPhotoClient.computeSignature(
            params = mapOf(
                "timestamp" to "1700000000",
                "public_id" to "smartplantcare/spc-unit-0001/tomato_1",
                "invalidate" to "true",
                "ignored_blank" to ""
            ),
            apiSecret = "secret123"
        )
        val sigB = CloudinaryCropPhotoClient.computeSignature(
            params = mapOf(
                "invalidate" to "true",
                "public_id" to "smartplantcare/spc-unit-0001/tomato_1",
                "timestamp" to "1700000000"
            ),
            apiSecret = "secret123"
        )
        assertEquals(sigA, sigB)
        assertEquals(40, sigA.length)
    }

    @Test
    fun cropPhotoRolePermissions_allowOwnerAndOperator_blockViewer() {
        assertTrue(DeviceRole.OWNER.canControl)
        assertTrue(DeviceRole.OPERATOR.canControl)
        assertFalse(DeviceRole.VIEWER.canControl)
    }
}
