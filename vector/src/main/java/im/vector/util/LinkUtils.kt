package im.vector.util

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.matrix.androidsdk.rest.model.Event

/*
returns null or url
 */
fun createLink(event:Event?): String?{
    if (event == null)
        return null

    val json: JsonObject? = event.contentAsJsonObject
    if (json == null)
        return null

    if (!json.has("url"))
        return null

    val jUrl = json.get("url")
    val str = jUrl.asString
    if (str.isNullOrEmpty())
        return null

    // mxc://ilfumo.ru/WdMXbkBqUiQhljuQUAALYYeh
    val id = str.split("/").last()

    return "https://matrix.ilfumo.ru/_matrix/media/v1/download/ilfumo.ru/$id"
}