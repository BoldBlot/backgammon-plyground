package com.boldblot.backgammonplyground.data

import com.boldblot.backgammonplyground.core.PureTdNet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuCheckKeyTest {
    private val net = PureTdNet.entries.first()
    private val rest = gpuCheckKey(net).substringAfter('/')

    @Test fun currentKeyIsValid() = assertTrue(gpuCheckValid(gpuCheckKey(net), net))

    @Test fun buildNumberKeysOf102To104CarryOverWhileTheGpuCodeIsTheSame() {
        assertTrue(gpuCheckValid("103/$rest", net))
        assertTrue(gpuCheckValid("104/$rest", net))
        assertFalse(gpuCheckValid("101/$rest", net))
        assertFalse(gpuCheckValid("103/OTHER/x/y", net))
    }

    @Test fun settingFromAnOldCheck() {
        val s = Settings(gpuGames = true, gpuCheck = "1 48|103/$rest", puretdNet = net)
        assertEquals("48", s.gpuSetting()?.tile)
        assertNull(s.gpuOffReason())
        val stale = s.copy(gpuCheck = "1 48|g000000000000/$rest")
        assertNull(stale.gpuSetting())
        assertTrue(stale.gpuOffReason()!!.startsWith("the last check was for other GPU code"))
    }
}
