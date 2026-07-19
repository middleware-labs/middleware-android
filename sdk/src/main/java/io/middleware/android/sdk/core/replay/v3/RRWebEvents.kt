package io.middleware.android.sdk.core.replay.v3

import io.middleware.android.sdk.core.replay.RREvent

/**
 * Factory for the rrweb events emitted by v3 session recording.
 *
 * The recording is screenshot-based: the replayed "DOM" is a fixed six-node
 * document whose only visible element is a full-viewport `<img>`; every new
 * frame is an attribute mutation swapping that image's `src`. The node ids are
 * constant — rrweb rebuilds its mirror on each FullSnapshot, so reusing them
 * across snapshots is safe.
 *
 * All coordinates and sizes are density-independent px, all timestamps epoch ms.
 */
internal object RRWebEvents {

    // rrweb event types
    const val TYPE_FULL_SNAPSHOT = 2
    const val TYPE_INCREMENTAL_SNAPSHOT = 3
    const val TYPE_META = 4
    const val TYPE_CUSTOM = 5

    // rrweb incremental sources
    const val SOURCE_MUTATION = 0
    const val SOURCE_MOUSE_INTERACTION = 2

    // rrweb mouse interaction types
    const val MOUSE_INTERACTION_TOUCH_START = 7
    const val MOUSE_INTERACTION_TOUCH_END = 9

    // rrweb serialized node types
    private const val NODE_DOCUMENT = 0
    private const val NODE_DOCUMENT_TYPE = 1
    private const val NODE_ELEMENT = 2

    // Fixed node ids of the synthetic document
    private const val NODE_ID_DOCUMENT = 1
    private const val NODE_ID_DOCTYPE = 2
    private const val NODE_ID_HTML = 3
    private const val NODE_ID_HEAD = 4
    private const val NODE_ID_BODY = 5
    const val NODE_ID_SCREEN = 6

    private const val POINTER_TYPE_TOUCH = 2

    fun meta(href: String, widthDp: Int, heightDp: Int, timestampMs: Long): RREvent =
        RREvent(
            timestampMs,
            TYPE_META,
            linkedMapOf<String, Any>(
                "href" to href,
                "width" to widthDp,
                "height" to heightDp,
            )
        )

    fun fullSnapshot(frameDataUri: String, widthDp: Int, heightDp: Int, timestampMs: Long): RREvent {
        val img = element(
            NODE_ID_SCREEN, "img",
            linkedMapOf(
                "id" to "mw-screen",
                "src" to frameDataUri,
                "style" to "width:${widthDp}px;height:${heightDp}px;display:block;",
            )
        )
        val head = element(NODE_ID_HEAD, "head", linkedMapOf())
        val body = element(
            NODE_ID_BODY, "body",
            linkedMapOf("style" to "margin:0;padding:0;background:#000;overflow:hidden;"),
            mutableListOf(img)
        )
        val html = element(NODE_ID_HTML, "html", linkedMapOf(), mutableListOf(head, body))
        val doctype = linkedMapOf<String, Any>(
            "type" to NODE_DOCUMENT_TYPE,
            "id" to NODE_ID_DOCTYPE,
            "name" to "html",
            "publicId" to "",
            "systemId" to "",
        )
        val document = linkedMapOf<String, Any>(
            "type" to NODE_DOCUMENT,
            "id" to NODE_ID_DOCUMENT,
            "childNodes" to mutableListOf<Any>(doctype, html),
        )
        return RREvent(
            timestampMs,
            TYPE_FULL_SNAPSHOT,
            linkedMapOf<String, Any>(
                "node" to document,
                "initialOffset" to linkedMapOf("left" to 0, "top" to 0),
            )
        )
    }

    fun frameMutation(frameDataUri: String, timestampMs: Long): RREvent =
        RREvent(
            timestampMs,
            TYPE_INCREMENTAL_SNAPSHOT,
            linkedMapOf<String, Any>(
                "source" to SOURCE_MUTATION,
                "texts" to emptyList<Any>(),
                "removes" to emptyList<Any>(),
                "adds" to emptyList<Any>(),
                "attributes" to listOf(
                    linkedMapOf(
                        "id" to NODE_ID_SCREEN,
                        "attributes" to linkedMapOf("src" to frameDataUri),
                    )
                ),
            )
        )

    fun touch(interactionType: Int, xDp: Int, yDp: Int, timestampMs: Long): RREvent =
        RREvent(
            timestampMs,
            TYPE_INCREMENTAL_SNAPSHOT,
            linkedMapOf<String, Any>(
                "source" to SOURCE_MOUSE_INTERACTION,
                "type" to interactionType,
                "id" to NODE_ID_SCREEN,
                "x" to xDp,
                "y" to yDp,
                "pointerType" to POINTER_TYPE_TOUCH,
            )
        )

    fun screenCustom(screenName: String, timestampMs: Long): RREvent =
        RREvent(
            timestampMs,
            TYPE_CUSTOM,
            linkedMapOf<String, Any>(
                "tag" to "screen",
                "payload" to linkedMapOf("name" to screenName),
            )
        )

    private fun element(
        id: Int,
        tagName: String,
        attributes: LinkedHashMap<String, Any>,
        childNodes: MutableList<Any> = mutableListOf(),
    ): LinkedHashMap<String, Any> = linkedMapOf(
        "type" to NODE_ELEMENT,
        "id" to id,
        "tagName" to tagName,
        "attributes" to attributes,
        "childNodes" to childNodes,
    )
}
