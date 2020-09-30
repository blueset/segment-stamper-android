package com.studio1a23.segmentstamper

import android.content.Context
import android.icu.text.SimpleDateFormat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import com.studio1a23.segmentstamper.BuildConfig.APPLICATION_ID
import org.json.JSONObject
import java.util.*

val API_PREFRERENCE_KEY = "$APPLICATION_ID.ApiPreference"

enum class Type(val type: String) {
    ON("on"),
    OFF("off");
}

data class Stamp(val type: Type, val date: Date) {
    constructor(type: String, date: String): this(
        Type.valueOf(type.toUpperCase(Locale.ROOT)),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).parse(date)
    )
}

data class StampUpdate(val previous: Stamp, val current: Stamp)

fun getApiPath(context: Context): String {
    val sharedPref = context.getSharedPreferences(API_PREFRERENCE_KEY, Context.MODE_PRIVATE)
    val baseApiPath = sharedPref.getString("BaseApiPath", BuildConfig.BASE_API_PATH)
    return "$baseApiPath/api/stamps"
}

fun getStampsRequest(context: Context,
                     listener: Response.Listener<List<Stamp>>,
                     errorListener: Response.ErrorListener?
): JsonArrayRequest {
    val url = getApiPath(context)
    return JsonArrayRequest(
        Request.Method.GET, url, null,
        { response ->
            val stamps = mutableListOf<Stamp>()
            for (i in 0 until response.length()) {
                val item = response.getJSONObject(i)
                val type = item.getString("type")
                val date = item.getString("date")
                stamps.add(Stamp(type, date))
            }
            listener.onResponse(stamps)
        },
        errorListener
    )
}

fun newStampsRequest(context: Context,
                     type: Type,
                     listener: Response.Listener<StampUpdate>,
                     errorListener: Response.ErrorListener?
): JsonObjectRequest {
    val url = getApiPath(context)
    val payload = JSONObject()
    payload.put("type", type.type)
    return JsonObjectRequest(
        Request.Method.POST, url, payload,
        { response ->
            if (response.getBoolean("ok")) {
                val prevType = if (type == Type.ON) "off" else "on"
                val outcome = StampUpdate(
                    Stamp(prevType, response.getString("previousDate")),
                    Stamp(type.type, response.getString("currentDate"))
                )
                listener.onResponse(outcome)
            } else {
                errorListener?.onErrorResponse(null)
            }
        },
        errorListener
    )
}