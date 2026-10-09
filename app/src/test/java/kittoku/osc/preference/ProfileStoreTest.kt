package kittoku.osc.preference

import kittoku.osc.preference.accessor.getIntPrefValue
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class ProfileStoreTest {
    private val prefs = InMemorySharedPreferences()
    private val store = ProfileStore(prefs)

    private fun hostname() = getStringPrefValue(OscPrefKey.HOME_HOSTNAME, prefs)

    private fun configureWorking(hostname: String, username: String = "admin") {
        setStringPrefValue(hostname, OscPrefKey.HOME_HOSTNAME, prefs)
        setStringPrefValue(username, OscPrefKey.HOME_USERNAME, prefs)
    }

    @Test
    fun freshInstallHasNoProfiles() {
        store.ensureInitialized()

        assertTrue(store.names.isEmpty())
        assertNull(store.activeName)
    }

    @Test
    fun settingsOfOlderVersionsBecomeTheActiveProfile() {
        configureWorking("vpn.example.com")

        store.ensureInitialized()

        assertEquals(listOf("vpn.example.com"), store.names)
        assertEquals("vpn.example.com", store.activeName)
    }

    @Test
    fun legacyProfileMatchingTheWorkingSettingsIsAdoptedInsteadOfCopied() {
        configureWorking("vpn.example.com")
        prefs.edit().putString(PROFILE_KEY_HEADER + "Home", serializeProfile(prefs)).apply()

        store.ensureInitialized()

        assertEquals(listOf("Home"), store.names)
        assertEquals("Home", store.activeName)
    }

    @Test
    fun activationSavesEditsOfThePreviousProfileAndLoadsTheNextOne() {
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com"))
        store.save(null, ProfileForm(name = "Office", hostname = "office.example.com"))
        assertEquals("Home", store.activeName)

        setStringPrefValue("edited-user", OscPrefKey.HOME_USERNAME, prefs)
        assertTrue(store.activate("Office"))

        assertEquals("office.example.com", hostname())
        assertEquals("edited-user", store.read("Home")!!.stringSetting[OscPrefKey.HOME_USERNAME.name])
    }

    @Test
    fun deletingTheActiveProfileActivatesAnotherOne() {
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com"))
        store.save(null, ProfileForm(name = "Office", hostname = "office.example.com"))

        store.delete("Home")

        assertEquals(listOf("Office"), store.names)
        assertEquals("Office", store.activeName)
        assertEquals("office.example.com", hostname())
    }

    @Test
    fun deletingTheLastProfileClearsTheWorkingSettings() {
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com", password = "secret"))

        store.delete("Home")
        store.ensureInitialized()

        assertTrue(store.names.isEmpty())
        assertEquals("", hostname())
        assertEquals("", getStringPrefValue(OscPrefKey.HOME_PASSWORD, prefs))
    }

    @Test
    fun savingTheActiveProfileUpdatesTheWorkingSettings() {
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com"))

        store.save("Home", ProfileForm(name = "Home", hostname = "home.example.com", port = "8443", camouflageSecret = "6a828775"))

        assertEquals(8443, getIntPrefValue(OscPrefKey.SSL_PORT, prefs))
        assertEquals("6a828775", getStringPrefValue(OscPrefKey.SSL_CAMOUFLAGE_SECRET, prefs))
    }

    @Test
    fun renamingTheActiveProfileKeepsItActive() {
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com"))

        store.save("Home", ProfileForm(name = "Keenetic", hostname = "home.example.com"))

        assertEquals(listOf("Keenetic"), store.names)
        assertEquals("Keenetic", store.activeName)
    }

    @Test
    fun savingAnInactiveProfileDoesNotTouchTheWorkingSettings() {
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com"))
        store.save(null, ProfileForm(name = "Office", hostname = "office.example.com"))

        store.save("Office", ProfileForm(name = "Office", hostname = "new-office.example.com"))

        assertEquals("home.example.com", hostname())
        assertEquals("new-office.example.com", store.entries.first { it.name == "Office" }.hostname)
    }

    @Test
    fun duplicateAndImportGetUniqueNames() {
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com"))

        assertEquals("Home (2)", store.duplicate("Home"))
        assertEquals("Home (3)", store.importRaw("Home", store.readRaw("Home")!!))
        assertNull(store.importRaw("Broken", "not json"))
        assertFalse(store.exists("Broken"))
    }

    @Test
    fun importIntoAnEmptyStoreActivatesTheProfile() {
        val source = InMemorySharedPreferences()
        setStringPrefValue("vpn.example.com", OscPrefKey.HOME_HOSTNAME, source)
        val json = serializeProfile(source)

        assertEquals("Imported", store.importRaw("Imported", json))
        assertEquals("Imported", store.activeName)
        assertEquals("vpn.example.com", hostname())
    }
}
