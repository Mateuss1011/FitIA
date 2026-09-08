package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthAndUserIsolationTest {

    @Test
    fun `test user data isolation between different user IDs in SharedPreferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val user1Uid = "uid_usuario_alpha_111"
        val user2Uid = "uid_usuario_beta_222"

        // User 1 saves a custom ficha name
        val prefsUser1 = context.getSharedPreferences("fitai_ficha_names_$user1Uid", Context.MODE_PRIVATE)
        prefsUser1.edit().putString("ficha_name_A", "Peito e Tríceps Insano").commit()

        // User 2 saves a different custom ficha name for sheet A
        val prefsUser2 = context.getSharedPreferences("fitai_ficha_names_$user2Uid", Context.MODE_PRIVATE)
        prefsUser2.edit().putString("ficha_name_A", "Superiores Foco Ombros").commit()

        // Verify that user 1's data does not overwrite or leak into user 2's data
        val user1Name = prefsUser1.getString("ficha_name_A", null)
        val user2Name = prefsUser2.getString("ficha_name_A", null)

        assertEquals("Peito e Tríceps Insano", user1Name)
        assertEquals("Superiores Foco Ombros", user2Name)
        assertNotEquals(user1Name, user2Name)

        // Clear user 1 data (simulate logout cleanup for user 1)
        prefsUser1.edit().clear().commit()

        // Verify user 2 data remains intact and unaffected
        val user2NameAfterUser1Cleanup = prefsUser2.getString("ficha_name_A", null)
        assertEquals("Superiores Foco Ombros", user2NameAfterUser1Cleanup)
        assertTrue(prefsUser1.all.isEmpty())
    }

    @Test
    fun `test logout cleanup purges temporary draft caches`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Simulate draft onboarding data
        val draftPrefs = context.getSharedPreferences("fitai_onboarding_draft", Context.MODE_PRIVATE)
        draftPrefs.edit()
            .putString("draft_name", "Carlos")
            .putString("draft_weight", "80")
            .commit()

        assertEquals("Carlos", draftPrefs.getString("draft_name", null))

        // Perform cleanup
        draftPrefs.edit().clear().commit()

        assertTrue(draftPrefs.all.isEmpty())
    }

    @Test
    fun `test ficha name validation limits max length and ignores whitespace`() {
        val rawInput = "   Braços Fortes Hipertrofia Extrema 2026   "
        val trimmed = rawInput.trim()
        val validName = if (trimmed.length > 40) trimmed.substring(0, 40) else trimmed

        assertEquals("Braços Fortes Hipertrofia Extrema 2026", validName)
        assertTrue(validName.length <= 40)

        val overlyLongInput = "A".repeat(60)
        val trimmedLong = overlyLongInput.trim()
        val cappedName = if (trimmedLong.length > 40) trimmedLong.substring(0, 40) else trimmedLong

        assertEquals(40, cappedName.length)
    }
}
