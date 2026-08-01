package life.furrer.l9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SpeedDialRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `call permission is denied by default and reflects a grant`() {
        assertFalse(SpeedDialRepository.hasCallPermission(context))
        shadowOf(context).grantPermissions(android.Manifest.permission.CALL_PHONE)
        assertTrue(SpeedDialRepository.hasCallPermission(context))
    }

    @Test
    fun `contacts are empty by default`() {
        assertTrue(SpeedDialRepository.loadContacts(context).isEmpty())
    }

    @Test
    fun `setting a contact round trips and other digits are unaffected`() {
        val alice = SpeedDialContact("Alice", "12345")
        SpeedDialRepository.setContact(context, 2, alice)

        assertEquals(mapOf(2 to alice), SpeedDialRepository.loadContacts(context))
    }

    @Test
    fun `digit 0 is a valid key, distinct from 10`() {
        val bob = SpeedDialContact("Bob", "999")
        SpeedDialRepository.setContact(context, 0, bob)

        assertEquals(bob, SpeedDialRepository.loadContacts(context)[0])
    }

    @Test
    fun `multiple contacts coexist and can be updated independently`() {
        val alice = SpeedDialContact("Alice", "1")
        val bob = SpeedDialContact("Bob", "2")
        SpeedDialRepository.setContact(context, 1, alice)
        SpeedDialRepository.setContact(context, 2, bob)

        val aliceUpdated = SpeedDialContact("Alice Updated", "3")
        SpeedDialRepository.setContact(context, 1, aliceUpdated)

        assertEquals(
            mapOf(1 to aliceUpdated, 2 to bob),
            SpeedDialRepository.loadContacts(context),
        )
    }

    @Test
    fun `setting a null contact clears that digit only`() {
        val alice = SpeedDialContact("Alice", "1")
        val bob = SpeedDialContact("Bob", "2")
        SpeedDialRepository.setContact(context, 1, alice)
        SpeedDialRepository.setContact(context, 2, bob)

        SpeedDialRepository.setContact(context, 1, null)

        assertEquals(mapOf(2 to bob), SpeedDialRepository.loadContacts(context))
    }
}
