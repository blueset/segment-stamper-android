package com.studio1a23.segmentstamper

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.ToggleTemplate
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.volley.toolbox.Volley
import io.reactivex.Flowable
import io.reactivex.processors.ReplayProcessor
import org.reactivestreams.FlowAdapters
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Flow
import java.util.function.Consumer


@RequiresApi(Build.VERSION_CODES.R)
class DeviceControlService : ControlsProviderService() {

    private val controlRequestCode = 1
    private val deviceId = "SegmentStamperToggle"
    private val templateId = "SegmentStamperToggleTemplate"

    private lateinit var updatePublisher: ReplayProcessor<Control>

    private fun getPendingIntent(activityClass: Class<out Activity>? = null): PendingIntent {
        val context: Context = baseContext
        val i = if (activityClass != null) {
            val intent = Intent(this, activityClass)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent
        } else Intent()
        val pi = PendingIntent.getActivity(
            context, controlRequestCode, i,
            // PendingIntent.FLAG_CANCEL_CURRENT
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return pi
    }

    val volleyQueue by lazy {
        Volley.newRequestQueue(baseContext)
    }

    private fun colorize(s: CharSequence, color: Int): CharSequence {
        val ssb = SpannableStringBuilder(s)
        ssb.setSpan(ForegroundColorSpan(color), 0, s.length, 0)
        return ssb
    }

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        val pi = getPendingIntent()
        val controls = mutableListOf<Control>()
        val control =
            Control.StatelessBuilder(deviceId, pi)
                // Required: The name of the control
                .setTitle("Segment Stamper")
                // Required: Usually the room where the control is located
                .setSubtitle("Stamp time goes here")
                // Required: Type of device, i.e., thermostat, light, switch
                .setDeviceType(DeviceTypes.TYPE_REMOTE_CONTROL) // For example, DeviceTypes.TYPE_THERMOSTAT
                .build()
        controls.add(control)
        // Create more controls here if needed and add it to the ArrayList

        // Uses the RxJava 2 library
        return FlowAdapters.toFlowPublisher(Flowable.fromIterable(controls))
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        /* Fill in details for the activity related to this device. On long press,
         * this Intent will be launched in a bottomsheet. Please design the activity
         * accordingly to fit a more limited space (about 2/3 screen height).
         */
        val pi = getPendingIntent(MainActivity::class.java)
        updatePublisher = ReplayProcessor.create()

        if (controlIds.contains(deviceId)) {
            volleyQueue.add(getStampsRequest(baseContext, { response ->

                val controlBuilder = Control.StatefulBuilder(deviceId, pi)
                    .setTitle("Segment Stamper")
                    .setDeviceType(DeviceTypes.TYPE_REMOTE_CONTROL)
                    .setCustomIcon(
                        Icon.createWithResource(this, R.drawable.ic_baseline_timer_24))

                if (response.isEmpty()) {
                    controlBuilder
                        .setSubtitle("over 7 days ago")
                        .setStatus(Control.STATUS_OK)
                        .setStatusText("Off")
                        .setControlTemplate(
                            ToggleTemplate(
                                templateId, ControlButton(
                                    false, "Off"
                                )
                            )
                        )
                } else {
                    val stamp = response.last()

                    val relativeDateString = DateUtils.getRelativeTimeSpanString(
                        stamp.date.time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        0
                    )
                    val timeString = SimpleDateFormat("HH:mm", Locale.US).format(stamp.date)
                    val dateTimeString = "$relativeDateString, $timeString"

                    val statusText = if (stamp.type === Type.ON) "On" else "Off"
                    val color: Int = getColor(if (stamp.type === Type.ON) R.color.onColor else R.color.offColor)
                    val bgColor: Int = color and 0x40FFFFFF

                    controlBuilder
                        .setStatus(Control.STATUS_OK)
                        .setStatusText(colorize(statusText, color))
                        .setTitle(colorize("Segment Stamper", color))
                        .setSubtitle(dateTimeString)
                        .setCustomColor(ColorStateList.valueOf(bgColor))
                        .setCustomIcon(
                            Icon.createWithResource(this, if (stamp.type === Type.ON) R.drawable.ic_twotone_timer_24 else R.drawable.ic_baseline_timer_24))
                        .setControlTemplate(
                            ToggleTemplate(
                                templateId, ControlButton(
                                    stamp.type === Type.ON,
                                    statusText
                                )
                            )
                        )
                }

                val control = controlBuilder.build()
                updatePublisher.onNext(control)
            }, { error ->
                Log.e("DeviceControlService", error.toString())
                val controlBuilder = Control.StatefulBuilder(deviceId, pi)
                    .setTitle("Segment Stamper")
                    .setDeviceType(DeviceTypes.TYPE_REMOTE_CONTROL)
                    .setSubtitle("Error loading state")
                    .setCustomIcon(
                        Icon.createWithResource(this, R.drawable.ic_baseline_timer_24))
                    .setStatus(Control.STATUS_ERROR)

                val control = controlBuilder.build()
                updatePublisher.onNext(control)
            }))

        }

        return FlowAdapters.toFlowPublisher(updatePublisher);
    }


    override fun performControlAction(
        controlId: String, action: ControlAction, consumer: Consumer<Int>
    ) {
        val pi = getPendingIntent(MainActivity::class.java)
        /* First, locate the control identified by the controlId. Once it is located, you can
         * interpret the action appropriately for that specific device. For instance, the following
         * assumes that the controlId is associated with a light, and the light can be turned on
         * or off.
         */
        if (action is BooleanAction) {

            // Inform SystemUI that the action has been received and is being processed
            consumer.accept(ControlAction.RESPONSE_OK)

            // In this example, action.getNewState() will have the requested action: true for “On”,
            // false for “Off”.

            /* This is where application logic/network requests would be invoked to update the state of
             * the device.
             * After updating, the application should use the publisher to update SystemUI with the new
             * state.
             */
            val type = if (action.newState) Type.ON else Type.OFF

            volleyQueue.add(newStampsRequest(baseContext, type, { response ->
                val stamp = response.current

                val timeString = SimpleDateFormat("HH:mm", Locale.US).format(stamp.date)
                val statusText = if (stamp.type === Type.ON) "On" else "Off"
                val color: Int = getColor(if (stamp.type === Type.ON) R.color.onColor else R.color.offColor)
                val bgColor: Int = color and 0x40FFFFFF

                val controlBuilder = Control.StatefulBuilder(deviceId, pi)
                    .setTitle(colorize("Segment Stamper", color))
                    .setSubtitle("just now, $timeString")
                    .setStatusText(colorize(statusText, color))
                    .setCustomColor(ColorStateList.valueOf(bgColor))
                    .setDeviceType(DeviceTypes.TYPE_REMOTE_CONTROL)
                    .setStatus(Control.STATUS_OK).setCustomIcon(
                        Icon.createWithResource(this, if (stamp.type === Type.ON) R.drawable.ic_twotone_timer_24 else R.drawable.ic_baseline_timer_24))
                    .setControlTemplate(
                        ToggleTemplate(
                            templateId, ControlButton(
                                stamp.type === Type.ON,
                                statusText
                            )
                        )
                    )

                val control = controlBuilder.build()
                updatePublisher.onNext(control)
            }, { error ->
                Log.e("DeviceControlService", error.toString())
                val controlBuilder = Control.StatefulBuilder(deviceId, pi)
                    .setTitle("Segment Stamper")
                    .setDeviceType(DeviceTypes.TYPE_REMOTE_CONTROL)
                    .setSubtitle("Error updating state")
                    .setCustomIcon(
                        Icon.createWithResource(this, R.drawable.ic_baseline_timer_24))
                    .setStatus(Control.STATUS_ERROR)

                val control = controlBuilder.build()
                updatePublisher.onNext(control)
            }))
        }
    }
}