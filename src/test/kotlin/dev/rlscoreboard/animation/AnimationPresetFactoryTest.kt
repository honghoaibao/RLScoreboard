package dev.rlscoreboard.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural tests for every named preset (design spec section 14) - these check the *shape*
 * of generated frames (count, prefix/suffix, monotonic growth) rather than exact color/phase
 * values, since the exact aesthetic result isn't a correctness property, but "typing produces
 * one frame per character" or "gradient frames are actually gradient tags" are.
 */
class AnimationPresetFactoryTest {

    @Test
    fun `empty text always produces a single empty frame regardless of preset`() {
        assertEquals(listOf(""), AnimationPresetFactory.generate("gradient", "", listOf("#FFFFFF", "#000000")))
    }

    @Test
    fun `an unrecognised preset name falls back to a single static frame rather than guessing`() {
        assertEquals(listOf("&aHello"), AnimationPresetFactory.generate("not-a-real-preset", "&aHello"))
    }

    @Test
    fun `static is always exactly one frame, unaffected by frame-count`() {
        assertEquals(listOf("&aHello"), AnimationPresetFactory.generate("static", "&aHello", frameCount = 30))
    }

    @Test
    fun `typing reveals one additional character per frame, in order`() {
        val frames = AnimationPresetFactory.generate("typing", "abc")
        assertEquals(listOf("a", "ab", "abc"), frames)
    }

    @Test
    fun `typing on a single character produces exactly one frame`() {
        assertEquals(listOf("x"), AnimationPresetFactory.generate("typing", "x"))
    }

    @Test
    fun `color cycles through exactly the configured colors, one frame each, as solid legacy hex`() {
        val frames = AnimationPresetFactory.generate("color", "Hi", colors = listOf("#FF0000", "#00FF00", "#0000FF"))
        assertEquals(3, frames.size)
        assertEquals("&#FF0000Hi", frames[0])
        assertEquals("&#00FF00Hi", frames[1])
        assertEquals("&#0000FFHi", frames[2])
    }

    @Test
    fun `fade produces frame-count frames each as solid legacy hex`() {
        val frames = AnimationPresetFactory.generate("fade", "Hi", colors = listOf("#000000", "#FFFFFF"), frameCount = 10)
        assertEquals(10, frames.size)
        frames.forEach { frame ->
            assertTrue(frame.startsWith("&#"), "expected a solid legacy hex prefix, got: $frame")
            assertTrue(frame.endsWith("Hi"))
        }
        // The very first frame should be exactly the starting color, unblended.
        assertEquals("&#000000Hi", frames.first())
        // The very last frame should be exactly the ending color, unblended.
        assertEquals("&#FFFFFFHi", frames.last())
    }

    @Test
    fun `pulse oscillates rather than just fading one-way - first and last frame are near the starting color`() {
        val frames = AnimationPresetFactory.generate("pulse", "Hi", colors = listOf("#000000", "#FFFFFF"), frameCount = 20)
        assertEquals(20, frames.size)
        // A triangle wave starts and (just before looping back to) ends near the "from" color,
        // unlike fade's linear sweep from "from" all the way to "to".
        assertEquals("&#000000Hi", frames.first())
    }

    @Test
    fun `gradient produces real MiniMessage gradient tags containing every configured color`() {
        val frames = AnimationPresetFactory.generate("gradient", "Hi", colors = listOf("#00AEEF", "#7B61FF"), frameCount = 6)
        assertEquals(6, frames.size)
        frames.forEach { frame ->
            assertTrue(frame.startsWith("<gradient:#00AEEF:#7B61FF:"), "unexpected shape: $frame")
            assertTrue(frame.endsWith("Hi</gradient>"), "unexpected shape: $frame")
        }
    }

    @Test
    fun `wave with no colors configured uses a real MiniMessage rainbow tag`() {
        val frames = AnimationPresetFactory.generate("wave", "Hi", colors = emptyList(), frameCount = 5)
        assertEquals(5, frames.size)
        frames.forEachIndexed { i, frame ->
            assertEquals("<rainbow:$i>Hi</rainbow>", frame)
        }
    }

    @Test
    fun `wave with colors configured produces gradient tags, same as gradient`() {
        val frames = AnimationPresetFactory.generate("wave", "Hi", colors = listOf("#FF0000", "#0000FF"), frameCount = 4)
        assertEquals(4, frames.size)
        frames.forEach { frame -> assertTrue(frame.startsWith("<gradient:#FF0000:#0000FF:")) }
    }

    @Test
    fun `scrolling returns the text unchanged when it already fits within the width`() {
        assertEquals(listOf("short"), AnimationPresetFactory.generate("scrolling", "short", width = 20))
    }

    @Test
    fun `scrolling produces multiple frames of exactly the configured width when text is longer`() {
        val longText = "This line is definitely longer than the window width"
        val frames = AnimationPresetFactory.generate("scrolling", longText, width = 10, speed = 1)
        assertTrue(frames.size > 1, "expected more than one frame for scrolling text longer than the window")
        frames.forEach { frame -> assertEquals(10, frame.length) }
    }

    @Test
    fun `scrolling advances by the configured speed`() {
        val longText = "0123456789ABCDEFGHIJ"
        val speed1 = AnimationPresetFactory.generate("scrolling", longText, width = 5, speed = 1)
        val speed2 = AnimationPresetFactory.generate("scrolling", longText, width = 5, speed = 2)
        // Twice the speed should mean roughly half as many frames to cover the same padded text.
        assertTrue(speed2.size < speed1.size)
    }
}
