package com.example.alaramtest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class SchedulerService : Service(), MqttCallbackExtended, IMqttActionListener {

    private val userName = "2000"
    private val password = "oa4kgnrtse3pzdooi0kg"
    private val url = "tcp://192.168.0.197:1883"
    private val clientId = "Irfan Khan"
    //  private val credential  = Triple(url,userName,password)

    private val lteUserName = "emqx"
    private val ltePassword = "12345"
    private val lteUrl = "tcp://10.150.127.114:1883"
    private val credential = Triple(lteUrl, lteUserName, ltePassword)

    private var count = 0
    private val notifId = 1101
    private val timeInterval = 30L
    private val channelId = "service_channel"
    private lateinit var mqttClient: MqttAsyncClient

    private val TAG = "MqttConnection"

    private val scheduledExecutorService: ScheduledExecutorService by lazy {
        Executors.newScheduledThreadPool(5);
    }

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val wakeLock: PowerManager.WakeLock by lazy {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "testAPP:LocationManagerService")
    }


    // variable ends


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onCreate() {
        super.onCreate()
        startInForeGround()
        setupMqtt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connectToMqtt()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mqttClient.isConnected)
            mqttClient.disconnect()
        if (!scheduledExecutorService.isShutdown)
            scheduledExecutorService.shutdownNow()
    }

    // helpers
    private fun startInForeGround() {
        createChannel()
        startForeground(notifId, getNotification("Foreground notif running"))
        // schedule ping

    }


    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val notificationChannel =
                NotificationChannel(
                    channelId, "Service Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            notificationChannel.enableLights(false)
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    private fun getNotification(text: String): Notification {
        // The PendingIntent to launch our activity if the user selects
        // this notification
        val title = "Ping tester is running"

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
        return builder.build()
    }


    private fun showLog(message: String) {
        Log.d("Foreground Mqtt", message)
    }


    // mqtt setup


    private fun connectToMqtt() {
        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.userName = credential.second
        mqttConnectOptions.password = (credential.third).toCharArray()
        mqttConnectOptions.isAutomaticReconnect = true
        mqttConnectOptions.connectionTimeout = 60
        mqttConnectOptions.isHttpsHostnameVerificationEnabled = false
        mqttConnectOptions.keepAliveInterval = timeInterval.toInt()
        mqttClient.connect(mqttConnectOptions, this)
        showLog("Mqtt connection request sent")
    }

    private fun setupMqtt() {
        val persistanceDir = getDirForMqtt()
            ?: throw NullPointerException("No Persistence Directory Available for Mqtt ")

        mqttClient =
            MqttAsyncClient(
                credential.first,
                clientId,
                MqttDefaultFilePersistence(persistanceDir.absolutePath)
            )
        mqttClient.setCallback(this)
        showLog("Mqtt is setup")
    }

    private fun getDirForMqtt(): File? {
        // ask Android where we can put files// No external storage, use internal storage instead.
        val myDir: File? = getExternalFilesDir(TAG) ?: getDir(TAG, MODE_PRIVATE)
        if (myDir == null)
            showLog("Error! No external and internal storage available")
        return myDir
    }


    // mqtt setup end


    private fun schedule() {
        showLog("Ping is schedule")

        scheduledExecutorService.scheduleAtFixedRate(
            { sendPing() },
            0,
            timeInterval,
            TimeUnit.SECONDS
        )
    }


    private fun sendPing() {
        count += 1
        showLog("Ping is sent, Ping count: $count")
        if (!wakeLock.isHeld)
            wakeLock.acquire(timeInterval * 1000)
        val token = mqttClient.checkPing(this, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                if (wakeLock.isHeld)
                    wakeLock.release()
                showLog("ping is sent successfully_______Token: $asyncActionToken")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                if (wakeLock.isHeld)
                    wakeLock.release()
                showLog("ping is not sent successfully_______Token: $asyncActionToken")
                exception?.printStackTrace()
            }

        })
        if (token == null && wakeLock.isHeld)
            wakeLock.release()
    }

    //Mqtt Message Callbacks
    override fun connectComplete(b: Boolean, s: String) {
        showLog("connection to the host  is successful_______Token: $s")
        if (scheduledExecutorService.isShutdown)
            schedule()
    }

    override fun connectionLost(cause: Throwable?) {
        showLog("connection to the host is lost")
        cause?.printStackTrace()
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        showLog("A new message received ________ Message Content:${message.toString()} ")
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        showLog("Message is delivered successfully")
    }


    override fun onSuccess(asyncActionToken: IMqttToken?) {
        showLog("connected successfully_______Token: $asyncActionToken")
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        showLog("connection is failed_______Token: $asyncActionToken")
        exception?.printStackTrace()
    }
}

