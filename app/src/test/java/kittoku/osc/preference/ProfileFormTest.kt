package kittoku.osc.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class ProfileFormTest {
    private val valid = ProfileForm(name = "Home", hostname = "vitalii.netcraze.pro", port = "443")

    private fun check(form: ProfileForm, original: String? = null, existing: Collection<String> = emptyList()) =
        validateProfileForm(form, original, existing)

    @Test
    fun validFormPasses() {
        assertNull(check(valid))
        assertNull(check(valid.copy(hostname = "10.0.0.1")))
        assertNull(check(valid.copy(hostname = "2001:db8::1")))
    }

    @Test
    fun hostnameMustBeABareHost() {
        assertEquals(ProfileFormError.HOSTNAME_EMPTY, check(valid.copy(hostname = "  ")))
        assertEquals(ProfileFormError.HOSTNAME_INVALID, check(valid.copy(hostname = "https://vitalii.netcraze.pro")))
        assertEquals(ProfileFormError.HOSTNAME_INVALID, check(valid.copy(hostname = "vitalii.netcraze.pro/sstp")))
        assertEquals(ProfileFormError.HOSTNAME_INVALID, check(valid.copy(hostname = "vitalii.netcraze.pro:443")))
        assertEquals(ProfileFormError.HOSTNAME_INVALID, check(valid.copy(hostname = "vitalii netcraze.pro")))
    }

    @Test
    fun portMustBeInRange() {
        assertEquals(ProfileFormError.PORT_INVALID, check(valid.copy(port = "")))
        assertEquals(ProfileFormError.PORT_INVALID, check(valid.copy(port = "0")))
        assertEquals(ProfileFormError.PORT_INVALID, check(valid.copy(port = "65536")))
        assertNull(check(valid.copy(port = "65535")))
    }

    @Test
    fun nameMustBeUniqueExceptForTheProfileBeingEdited() {
        assertEquals(ProfileFormError.NAME_TAKEN, check(valid, existing = listOf("Home")))
        assertNull(check(valid, original = "Home", existing = listOf("Home")))
    }

    @Test
    fun emptyNameFallsBackToHostname() {
        val form = valid.copy(name = " ")

        assertEquals("vitalii.netcraze.pro", form.effectiveName())
        assertEquals(ProfileFormError.NAME_TAKEN, check(form, existing = listOf("vitalii.netcraze.pro")))
    }

    @Test
    fun formRoundTripsThroughAProfile() {
        val form = valid.copy(username = "admin", password = "p@ss", camouflageSecret = "6a828775", port = "8443")
        val profile = Profile().also { form.applyTo(it) }

        assertEquals(form, profileFormOf("Home", profile))
    }

    @Test
    fun missingValuesUseDefaults() {
        assertEquals(ProfileForm(name = "New"), profileFormOf("New", Profile()))
        assertEquals(ProfileForm(name = "New"), profileFormOf("New", null))
    }
}
