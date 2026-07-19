package io.middleware.android.sdk.core.replay.v3

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Golden-JSON tests for the rrweb event shapes. These payloads are consumed by
 * the standard rrweb Replayer in bifrost — any change here is a wire-format
 * change and must stay rrweb-spec compliant.
 */
class RRWebEventsTest {

    private val gson = Gson()

    @Test
    fun metaEventShape() {
        val event = RRWebEvents.meta("android-app://io.test/MainActivity", 412, 892, 1750000000000L)
        assertEquals(4, event.type)
        assertEquals(1750000000000L, event.timestamp)
        assertEquals(
            """{"href":"android-app://io.test/MainActivity","width":412,"height":892}""",
            gson.toJson(event.data)
        )
    }

    @Test
    fun fullSnapshotShape() {
        val event = RRWebEvents.fullSnapshot("data:image/webp;base64,AAAA", 412, 892, 1750000000001L)
        assertEquals(2, event.type)
        val expected = """{"node":{"type":0,"id":1,"childNodes":[""" +
            """{"type":1,"id":2,"name":"html","publicId":"","systemId":""},""" +
            """{"type":2,"id":3,"tagName":"html","attributes":{},"childNodes":[""" +
            """{"type":2,"id":4,"tagName":"head","attributes":{},"childNodes":[]},""" +
            """{"type":2,"id":5,"tagName":"body","attributes":{"style":"margin:0;padding:0;background:#000;overflow:hidden;"},"childNodes":[""" +
            """{"type":2,"id":6,"tagName":"img","attributes":{"id":"mw-screen","src":"data:image/webp;base64,AAAA","style":"width:412px;height:892px;display:block;"},"childNodes":[]}""" +
            """]}]}]},"initialOffset":{"left":0,"top":0}}"""
        assertEquals(expected, gson.toJson(event.data))
    }

    @Test
    fun frameMutationShape() {
        val event = RRWebEvents.frameMutation("data:image/webp;base64,BBBB", 1750000001000L)
        assertEquals(3, event.type)
        assertEquals(
            """{"source":0,"texts":[],"removes":[],"adds":[],""" +
                """"attributes":[{"id":6,"attributes":{"src":"data:image/webp;base64,BBBB"}}]}""",
            gson.toJson(event.data)
        )
    }

    @Test
    fun touchEventShape() {
        val event = RRWebEvents.touch(RRWebEvents.MOUSE_INTERACTION_TOUCH_START, 210, 480, 1750000001234L)
        assertEquals(3, event.type)
        assertEquals(
            """{"source":2,"type":7,"id":6,"x":210,"y":480,"pointerType":2}""",
            gson.toJson(event.data)
        )
    }

    @Test
    fun screenCustomEventShape() {
        val event = RRWebEvents.screenCustom("CheckoutActivity", 1750000002000L)
        assertEquals(5, event.type)
        assertEquals(
            """{"tag":"screen","payload":{"name":"CheckoutActivity"}}""",
            gson.toJson(event.data)
        )
    }
}
