package org.telegram.messenger.mzgram.test

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramSettingsBackup
import org.telegram.messenger.mzgram.plugins.MZGramPipInstaller

class MZGramPipInstallerTest {

    private var initialPipEnabled = false

    @Before
    fun setUp() {
        initialPipEnabled = MZGramConfig.pipEnabled
        MZGramConfig.pipEnabled = false
    }

    @After
    fun tearDown() {
        MZGramConfig.pipEnabled = initialPipEnabled
    }

    @Test
    fun testPipConfigurationDefaultsAndToggle() {
        assertFalse(MZGramConfig.pipEnabled)
        MZGramConfig.togglePipEnabled()
        assertTrue(MZGramConfig.pipEnabled)
        MZGramConfig.setPipEnabled(false)
        assertFalse(MZGramConfig.pipEnabled)
    }

    @Test
    fun testPipInstallerPackageParsing() {
        val json = JSONObject().apply {
            put("name", "requests")
            put("version", "2.31.0")
            put("filename", "requests-2.31.0-py3-none-any.whl")
            put("size", 62000L)
            put("installed", false)
        }
        val pkg = MZGramPipInstaller.PackageInfo(json)
        assertEquals("requests", pkg.name)
        assertEquals("2.31.0", pkg.version)
        assertEquals("requests-2.31.0-py3-none-any.whl", pkg.filename)
        assertEquals(62000L, pkg.size)
        assertFalse(pkg.installed)
    }

    @Test
    fun testPipQuerySummaryAndInstallResult() {
        val arr = JSONArray().apply {
            put(JSONObject().apply {
                put("name", "urllib3")
                put("version", "2.0.0")
                put("filename", "urllib3-2.0.0-py3-none-any.whl")
                put("size", 120000L)
                put("installed", true)
            })
        }
        val summary = MZGramPipInstaller.QuerySummary(true, "", 120000L, arr)
        assertTrue(summary.ok)
        assertEquals(1, summary.packages.size)
        assertEquals("urllib3", summary.packages[0].name)
        assertTrue(summary.packages[0].installed)

        val installArr = JSONArray().apply {
            put("urllib3==2.0.0")
        }
        val installRes = MZGramPipInstaller.InstallResult(true, "", installArr)
        assertTrue(installRes.ok)
        assertEquals(1, installRes.installed.size)
        assertEquals("urllib3==2.0.0", installRes.installed[0])
    }

    @Test
    fun testPipStringsExist() {
        val ctx = ApplicationLoader.applicationContext
        assertNotNull(ctx.getString(R.string.MZGramPipEnabled))
        assertNotNull(ctx.getString(R.string.MZGramPipEnabledInfo))
        assertNotNull(ctx.getString(R.string.MZGramPipEnabledPrompt))
        assertNotNull(ctx.getString(R.string.MZGramPipInstallPackages))
        assertNotNull(ctx.getString(R.string.MZGramPipInstallTitle))
        assertNotNull(ctx.getString(R.string.MZGramPipInstallWarning))
        assertNotNull(ctx.getString(R.string.MZGramPipInstallSuccess))
    }

    @Test
    fun testSettingsBackupIncludesPip() {
        val json = MZGramSettingsBackup.exportToJson()
        assertTrue(json.contains("\"pipEnabled\"") || json.contains("pipEnabled"))
    }
}
