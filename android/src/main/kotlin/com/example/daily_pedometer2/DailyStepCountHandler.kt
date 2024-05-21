package com.example.daily_pedometer2

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import android.os.Handler
import java.util.Calendar

class DailyStepCountHandler() : EventChannel.StreamHandler {
    
    private lateinit var context: Context
    private lateinit var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding
    private lateinit var sharedPrefs: SharedPreferences
    
    private var sensorEventListener: SensorEventListener? = null
    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var dailyStepCount: Int = 0
    private var initialStepCount: Int = -1
    
    constructor(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) : this() {
        this.context = flutterPluginBinding.applicationContext
        this.sharedPrefs = context.getSharedPreferences("pedometerPrefs", Context.MODE_PRIVATE)
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        this.flutterPluginBinding = flutterPluginBinding

        // Load saved step count at initialization
        dailyStepCount = sharedPrefs.getInt("dailyStepCount", 0);
        val savedDate = sharedPrefs.getLong("lastSavedDate", 0L);
        if (isDifferentDay(savedDate)) {
            // Reset step count at the start of a new day
            dailyStepCount = 0
            initialStepCount = -1
            sharedPrefs.edit().putLong("lastSavedDate", System.currentTimeMillis()).apply();
        }
    }
    private fun getDailyEventListener(events: EventChannel.EventSink): SensorEventListener? {
        return object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                    val currentStepCount = event.values[0].toInt();
                    if (initialStepCount == -1) {
                        initialStepCount = currentStepCount
                    }
                    dailyStepCount = currentStepCount - initialStepCount
                    // Save the updated step count
                    sharedPrefs.edit().putInt("dailyStepCount", dailyStepCount).apply();
                    events!!.success(dailyStepCount)
                }
            }
        }
    }
    private fun isDifferentDay(savedDate: Long): Boolean {
        val savedCalendar = Calendar.getInstance().apply { timeInMillis = savedDate }
        val currentCalendar = Calendar.getInstance()
        return savedCalendar.get(Calendar.DAY_OF_YEAR) != currentCalendar.get(Calendar.DAY_OF_YEAR) ||
                savedCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        if (stepCounterSensor == null) {
            events!!.error("1", "DailyStepCount not available", "DailyStepCount is not available on this device");
        } else {
            sensorEventListener = getDailyEventListener(events!!);
            sensorManager!!.registerListener(sensorEventListener, stepCounterSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    override fun onCancel(arguments: Any?) {
        sensorManager!!.unregisterListener(sensorEventListener);
    }

}