package org.telegram.messenger.mzgram.test

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.messenger.mzgram.MZGramSettingsBackup
import org.telegram.messenger.mzgram.translate.MZGramAiService
import org.telegram.messenger.mzgram.translate.MZGramSpeechRecognizer
import org.telegram.messenger.mzgram.translate.MZGramTranslator

class MZGramTranslationAiTest {

    private var initialProvider = 0
    private var initialTargetLang = ""
    private var initialChatBar = false
    private var initialTranslateSend = false
    private var initialFallback = true
    private var initialDeeplKey = ""
    private var initialMozhiUrl = ""
    private var initialAiType = 0
    private var initialAiKey = ""
    private var initialAiEndpoint = ""
    private var initialAiModel = ""
    private var initialSpeechEnabled = false
    private var initialSpeechEngine = 0
    private var initialVoskLang = "uk"

    @Before
    fun setUp() {
        initialProvider = MZGramConfig.translationProvider
        initialTargetLang = MZGramConfig.translationTargetLang
        initialChatBar = MZGramConfig.chatTranslationBar
        initialTranslateSend = MZGramConfig.translateBeforeSend
        initialFallback = MZGramConfig.translationAutoFallback
        initialDeeplKey = MZGramConfig.deeplApiKey
        initialMozhiUrl = MZGramConfig.mozhiCustomInstance
        initialAiType = MZGramConfig.aiProviderType
        initialAiKey = MZGramConfig.aiApiKey
        initialAiEndpoint = MZGramConfig.aiCustomEndpoint
        initialAiModel = MZGramConfig.aiCustomModel
        initialSpeechEnabled = MZGramConfig.speechRecognitionEnabled
        initialSpeechEngine = MZGramConfig.speechEngine
        initialVoskLang = MZGramConfig.voskModelLang
    }

    @After
    fun tearDown() {
        MZGramConfig.translationProvider = initialProvider
        MZGramConfig.translationTargetLang = initialTargetLang
        MZGramConfig.chatTranslationBar = initialChatBar
        MZGramConfig.translateBeforeSend = initialTranslateSend
        MZGramConfig.translationAutoFallback = initialFallback
        MZGramConfig.deeplApiKey = initialDeeplKey
        MZGramConfig.mozhiCustomInstance = initialMozhiUrl
        MZGramConfig.aiProviderType = initialAiType
        MZGramConfig.aiApiKey = initialAiKey
        MZGramConfig.aiCustomEndpoint = initialAiEndpoint
        MZGramConfig.aiCustomModel = initialAiModel
        MZGramConfig.speechRecognitionEnabled = initialSpeechEnabled
        MZGramConfig.speechEngine = initialSpeechEngine
        MZGramConfig.voskModelLang = initialVoskLang
    }

    @Test
    fun testConfigDefaultsAndSetters() {
        MZGramConfig.setTranslationProvider(MZGramTranslator.PROVIDER_DEEPL)
        assertEquals(MZGramTranslator.PROVIDER_DEEPL, MZGramConfig.translationProvider)

        MZGramConfig.setDeeplApiKey("test-deepl-key")
        assertEquals("test-deepl-key", MZGramConfig.deeplApiKey)

        MZGramConfig.setTranslationTargetLang("uk")
        assertEquals("uk", MZGramConfig.translationTargetLang)

        MZGramConfig.chatTranslationBar = false
        MZGramConfig.toggleChatTranslationBar()
        assertTrue(MZGramConfig.chatTranslationBar)

        MZGramConfig.translateBeforeSend = false
        MZGramConfig.toggleTranslateBeforeSend()
        assertTrue(MZGramConfig.translateBeforeSend)

        MZGramConfig.translationAutoFallback = false
        MZGramConfig.toggleTranslationAutoFallback()
        assertTrue(MZGramConfig.translationAutoFallback)

        MZGramConfig.setMozhiCustomInstance("https://mozhi.example.com")
        assertEquals("https://mozhi.example.com", MZGramConfig.mozhiCustomInstance)

        MZGramConfig.setAiProviderType(MZGramAiService.AI_ANTHROPIC)
        assertEquals(MZGramAiService.AI_ANTHROPIC, MZGramConfig.aiProviderType)

        MZGramConfig.setAiApiKey("sk-ant-test")
        assertEquals("sk-ant-test", MZGramConfig.aiApiKey)

        MZGramConfig.setAiCustomEndpoint("http://localhost:8000/v1")
        assertEquals("http://localhost:8000/v1", MZGramConfig.aiCustomEndpoint)

        MZGramConfig.setAiCustomModel("claude-3-5-sonnet")
        assertEquals("claude-3-5-sonnet", MZGramConfig.aiCustomModel)

        MZGramConfig.speechRecognitionEnabled = false
        MZGramConfig.toggleSpeechRecognitionEnabled()
        assertTrue(MZGramConfig.speechRecognitionEnabled)

        MZGramConfig.setSpeechEngine(MZGramSpeechRecognizer.ENGINE_VOSK)
        assertEquals(MZGramSpeechRecognizer.ENGINE_VOSK, MZGramConfig.speechEngine)

        MZGramConfig.setVoskModelLang("en")
        assertEquals("en", MZGramConfig.voskModelLang)
    }

    @Test
    fun testTranslatorProviders() {
        assertEquals(6, MZGramTranslator.PROVIDERS_COUNT)
        for (i in 0 until MZGramTranslator.PROVIDERS_COUNT) {
            val name = MZGramTranslator.getProviderName(i)
            assertNotNull(name)
            assertTrue(name.isNotEmpty())
        }
    }

    @Test
    fun testAiServiceProvidersAndModels() {
        assertEquals(5, MZGramAiService.AI_PROVIDERS_COUNT)
        for (i in 0 until MZGramAiService.AI_PROVIDERS_COUNT) {
            val name = MZGramAiService.getAiProviderName(i)
            val model = MZGramAiService.getDefaultModel(i)
            assertNotNull(name)
            assertNotNull(model)
            assertTrue(name.isNotEmpty())
            assertTrue(model.isNotEmpty())
        }
    }

    @Test
    fun testSpeechRecognizer() {
        val dir = MZGramSpeechRecognizer.getVoskModelDir("uk")
        assertNotNull(dir)
        assertTrue(dir.path.contains("vosk_models"))
    }
}
