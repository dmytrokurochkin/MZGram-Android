package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.plugins.MZGramPluginAuditJournal
import org.telegram.messenger.mzgram.plugins.MZGramPluginHookFilter
import org.telegram.messenger.mzgram.plugins.MZGramPluginHookGate

// What the hooks of plugins give back to the app, their filters and the
// crash mark of the hook engine (the parts outside the build with plugins).
class MZGramPluginHooksTest {

    private val invalid = MZGramPluginHookGate.INVALID

    @Test
    fun coerce_numbersToTheExactType() {
        assertEquals(5, MZGramPluginHookGate.coerce(5L, Int::class.javaPrimitiveType))
        assertTrue(MZGramPluginHookGate.coerce(5L, Int::class.javaPrimitiveType) is Int)
        // A colour from Python wraps like a Java cast
        assertEquals(0xFF000000.toInt(), MZGramPluginHookGate.coerce(0xFF000000L, Int::class.javaPrimitiveType))
        assertEquals(2, MZGramPluginHookGate.coerce(2.0, Int::class.javaPrimitiveType))
        assertSame(invalid, MZGramPluginHookGate.coerce(1.5, Int::class.javaPrimitiveType))
        assertEquals(1.5f, MZGramPluginHookGate.coerce(1.5, Float::class.javaPrimitiveType))
        assertEquals(7L, MZGramPluginHookGate.coerce(7, Long::class.javaObjectType))
        assertSame(invalid, MZGramPluginHookGate.coerce(true, Int::class.javaPrimitiveType))
    }

    @Test
    fun coerce_nullCharsAndObjects() {
        assertSame(invalid, MZGramPluginHookGate.coerce(null, Int::class.javaPrimitiveType))
        assertNull(MZGramPluginHookGate.coerce(null, String::class.java))
        assertEquals('x', MZGramPluginHookGate.coerce("x", Char::class.javaPrimitiveType))
        assertSame(invalid, MZGramPluginHookGate.coerce("xy", Char::class.javaPrimitiveType))
        assertEquals("text", MZGramPluginHookGate.coerce("text", CharSequence::class.java))
        assertSame(invalid, MZGramPluginHookGate.coerce(3L, String::class.java))
        assertNull(MZGramPluginHookGate.coerce("anything", Void.TYPE))
    }

    @Test
    fun coerceAll_inPlace() {
        val values = arrayOf<Any?>(1L, "a", null)
        assertTrue(MZGramPluginHookGate.coerceAll(values, arrayOf(Int::class.javaPrimitiveType, String::class.java, Any::class.java)))
        assertEquals(1, values[0])
        assertFalse(MZGramPluginHookGate.coerceAll(arrayOf<Any?>(null), arrayOf(Boolean::class.javaPrimitiveType)))
        assertFalse(MZGramPluginHookGate.coerceAll(arrayOf<Any?>(1), arrayOf<Class<*>>()))
    }

    @Test
    fun filter_argumentsAndResults() {
        val parsed = MZGramPluginHookFilter.parse("""{"before":[{"type":"argument_equal","index":0,"value":3},
            {"type":"argument_not_null","index":1}],"after":[{"type":"or","filters":[{"type":"result_is_true"},
            {"type":"result_equal","value":"7"}]}]}""")
        assertFalse(parsed.hasUnsupported())
        assertTrue(MZGramPluginHookFilter.passes(parsed.before, arrayOf<Any?>(3L, "x"), null, false))
        assertFalse(MZGramPluginHookFilter.passes(parsed.before, arrayOf<Any?>(4, "x"), null, false))
        // Argument 1 is not there
        assertFalse(MZGramPluginHookFilter.passes(parsed.before, arrayOf<Any?>(3), null, false))
        assertTrue(MZGramPluginHookFilter.passes(parsed.after, arrayOf<Any?>(), true, true))
        assertTrue(MZGramPluginHookFilter.passes(parsed.after, arrayOf<Any?>(), 7, true))
        // A result filter never passes before the method ran
        assertFalse(MZGramPluginHookFilter.passes(parsed.after, arrayOf<Any?>(), true, false))
    }

    @Test
    fun filter_expressionsNeverPass() {
        val parsed = MZGramPluginHookFilter.parse("""{"before":[{"type":"condition","expression":"true"}]}""")
        assertTrue(parsed.hasUnsupported())
        assertFalse(MZGramPluginHookFilter.passes(parsed.before, arrayOf<Any?>(), null, false))
        val broken = MZGramPluginHookFilter.parse("not json")
        assertTrue(broken.before.isEmpty() && broken.after.isEmpty())
        assertTrue(MZGramPluginHookFilter.passes(broken.before, arrayOf<Any?>(), null, false))
    }

    @Test
    fun engine_offAfterTwoCrashedStarts() {
        try {
            MZGramPluginHookGate.retryEngine()
            assertTrue(MZGramPluginHookGate.beginEngineStart())
            MZGramPluginHookGate.endEngineStart(true)
            // A start that never ended (the process died), twice
            assertTrue(MZGramPluginHookGate.beginEngineStart())
            assertTrue(MZGramPluginHookGate.beginEngineStart())
            assertFalse(MZGramPluginHookGate.beginEngineStart())
            assertTrue(MZGramPluginHookGate.isEngineBroken())
            assertFalse(MZGramPluginHookGate.beginEngineStart())
            MZGramPluginHookGate.retryEngine()
            assertFalse(MZGramPluginHookGate.isEngineBroken())
            assertTrue(MZGramPluginHookGate.beginEngineStart())
        } finally {
            MZGramPluginHookGate.retryEngine()
        }
    }

    @Test
    fun counters_followTheHooks() {
        val member = String::class.java.getMethod("length")
        val counter = MZGramPluginHookGate.counter("hooks_test_plugin", member)
        assertEquals(1, MZGramPluginHookGate.count("hooks_test_plugin"))
        MZGramPluginHookGate.release(counter)
        assertEquals(0, MZGramPluginHookGate.count("hooks_test_plugin"))
    }

    @Test
    fun hooked_isItsOwnEvent() {
        val events = listOf(MZGramPluginAuditJournal.EVENT_HOOKED, MZGramPluginAuditJournal.EVENT_HOOK_FAILED,
                MZGramPluginAuditJournal.EVENT_HOOK_DENIED, MZGramPluginAuditJournal.EVENT_HOOK_PROTECTED,
                MZGramPluginAuditJournal.EVENT_HOOK_NO_PERMISSION, MZGramPluginAuditJournal.EVENT_LOG)
        assertEquals(events.size, events.toSet().size)
    }
}
