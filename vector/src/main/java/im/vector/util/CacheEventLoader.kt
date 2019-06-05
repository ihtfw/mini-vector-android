package im.vector.util

import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.callback.SuccessCallback
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.MatrixError
import java.util.concurrent.ConcurrentHashMap

public class CacheEventLoader(var session: MXSession) {
    var cache: MutableMap<String, Event> = ConcurrentHashMap()

    fun getEvent(eventId: String, successCallback: SuccessCallback<Event>){
        getEvent(eventId, object : ApiCallback<Event> {
            override fun onNetworkError(e: Exception) {
            }

            override fun onMatrixError(e: MatrixError) {
            }

            override fun onUnexpectedError(e: Exception) {
            }

            override fun onSuccess(info: Event) {
                successCallback.onSuccess(info)
            }
        })
    }

    fun getEvent(eventId: String, apiCallback: ApiCallback<Event>){
        var event = cache[eventId]
        if (event != null){
            apiCallback.onSuccess(event)
            return
        }

        session.eventsApiClient.getEventFromEventId(eventId, object : ApiCallback<Event> {
            override fun onNetworkError(e: Exception) {
                apiCallback.onNetworkError(e)
            }

            override fun onMatrixError(e: MatrixError) {
                apiCallback.onMatrixError(e)
            }

            override fun onUnexpectedError(e: Exception) {
                apiCallback.onUnexpectedError(e)
            }

            override fun onSuccess(info: Event) {
                cache[info.eventId] = info
                apiCallback.onSuccess(info)
            }
        })
    }
}