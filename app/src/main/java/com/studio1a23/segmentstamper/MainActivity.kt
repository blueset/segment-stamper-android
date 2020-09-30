package com.studio1a23.segmentstamper

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils.*
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.volley.toolbox.Volley
import com.studio1a23.segmentstamper.BuildConfig.BASE_API_PATH
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private val model: MainViewModel by viewModels()

    private val sharedPref: SharedPreferences by lazy {
        getSharedPreferences(API_PREFRERENCE_KEY, Context.MODE_PRIVATE)
    }

    private var baseApiPathFromPrefs: String
        get() {
            val baseApiPath = sharedPref.getString("BaseApiPath", BASE_API_PATH)
            return "$baseApiPath"
        }
        set(value) {
            sharedPref.edit {
                putString("BaseApiPath", value)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        model.apiUrl.observe(this, { apiUrl ->
            baseApiField.setText(apiUrl);
        })

        model.recentStamps.observe(this, { recentStamp ->
            if (recentStamp.isEmpty()) {
                statusTextView.text = "???"
                statusTextView.setTextColor(getColor(R.color.material_on_background_emphasis_medium))
                stampDetails.text = "Last stamp was an ... stamp made ... on ... at ..."
                stampToggle.text = "Toggle stamp"
                stampToggle.backgroundTintList = getColorStateList(R.color.colorAccent)
            } else {
                val stamp = recentStamp.last()
                val relativeDateString = getRelativeTimeSpanString(
                    stamp.date.time,
                    System.currentTimeMillis(),
                    MINUTE_IN_MILLIS,
                    0
                )
                val timeString = SimpleDateFormat("HH:mm").format(stamp.date)
                val dateTimeString = "$relativeDateString, $timeString"
                when (stamp.type) {
                    Type.ON -> {
                        statusTextView.text = "ON"
                        statusTextView.setTextColor(getColor(R.color.onColor))
                        stampDetails.text = "Last stamp was an on stamp made $dateTimeString."
                        stampToggle.text = "Stamp off"
                        stampToggle.backgroundTintList = getColorStateList(R.color.offColor)
                    }
                    Type.OFF -> {
                        statusTextView.text = "OFF"
                        statusTextView.setTextColor(getColor(R.color.offColor))
                        stampDetails.text = "Last stamp was an off stamp made $dateTimeString."
                        stampToggle.text = "Stamp on"
                        stampToggle.backgroundTintList = getColorStateList(R.color.onColor)
                    }
                }
            }
        })

        model.isLoading.observe(this, { isLoading ->
            stampToggle.isEnabled = !isLoading
        })

        model.apiUrl.value = baseApiPathFromPrefs;
    }

    fun resetApiPath(view: View) {
        baseApiPathFromPrefs = BASE_API_PATH
        model.apiUrl.value = BASE_API_PATH
    }

    fun setApiPath(view: View) {
        val path = baseApiField.text.toString()
        baseApiPathFromPrefs = path
        model.apiUrl.value = path
    }

    fun viewStats(view: View) {
        var url = baseApiPathFromPrefs
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    fun refresh(view: View) {
        if (model.recentStamps.value?.isNotEmpty() == true) {
            val stamp = model.recentStamps.value!!.last()
            val relativeDateString = getRelativeTimeSpanString(
                stamp.date.time,
                System.currentTimeMillis(),
                MINUTE_IN_MILLIS,
                0
            )
            val timeString = SimpleDateFormat("HH:mm", Locale.US).format(stamp.date)
            val dateTimeString = "$relativeDateString, $timeString"
            stampDetails.text = "Last stamp was an on stamp made $dateTimeString."
        }
        model.loadStatus()
    }

    fun stamp(view: View) {
        model.newStamp(if (model.recentStamps.value?.last()?.type === Type.ON) Type.OFF else Type.ON)
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val apiUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    val isLoading: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    val recentStamps: MutableLiveData<List<Stamp>> by lazy {
        MutableLiveData<List<Stamp>>().also {
            loadStatus()
        }
    }

    val volleyQueue by lazy {
        Volley.newRequestQueue(getApplication())
    }

    fun loadStatus() {
        isLoading.value = true
        viewModelScope.launch {
            volleyQueue.add(getStampsRequest(getApplication(), { response ->
                recentStamps.postValue(response.sortedBy { it.date })
                isLoading.postValue(false)
            }, { error ->
                Log.e("STAMPER", "Error while loading status: $error")
                isLoading.postValue(false)

            }))
        }
    }

    fun newStamp(type: Type) {
        isLoading.postValue(true)
        viewModelScope.launch {
            volleyQueue.add(
                newStampsRequest(
                    getApplication(),
                    type,
                    { response ->
                        isLoading.postValue(false)
                        recentStamps.postValue(recentStamps.value?.plus(response.current))
                    }, { error ->
                        Log.e("STAMPER", "Error while stamping: $error")
                        isLoading.postValue(false)
                    }
                )
            )
        }
    }
}