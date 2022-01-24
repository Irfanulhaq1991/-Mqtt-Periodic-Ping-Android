package com.example.alaramtest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.internal.ClientComms
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import java.io.File
import java.lang.IllegalStateException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class SchedulerService : Service(), ServiceDataBridge, MqttCallbackExtended, MqttPingSender,
    IMqttActionListener {

    private val userName = "2000"
    private val password = "oa4kgnrtse3pzdooi0kg"
    private val url = "tcp://192.168.0.197:1883"
    private val clientId = "Irfan Khan"
    private val credential = Triple(url, userName, password)

    private val lteUserName = "emqx"
    private val ltePassword = "12345"
    private val lteUrl = "tcp://10.150.127.114:1883"
    //private val credential  = Triple(lteUrl,lteUserName,ltePassword)

    private var count = 0
    private val notifId = 1101
    private val timeInterval = 30L
    private val tcpTimout = 60
    private val channelId = "service_channel"
    private lateinit var mqttClient: MqttAsyncClient

    private val TAG = "MqttConnection"

    private val scheduledExecutorService: ScheduledExecutorService by lazy {
        Executors.newScheduledThreadPool(4)
    }

    private val subscribers = mutableListOf<ServiceEventListener>()
    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val wakeLock: PowerManager.WakeLock by lazy {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::LocationManagerService")
    }


    // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>variable ends<<<<<<<<<<<<<<<<<<<


    /// data communication
    companion object {
        private var instance: SchedulerService? = null

        //exposed for incomming data communication
        fun getServiceDataBridge(): ServiceDataBridge {
            if (instance == null) throw Exception("ServiceDataBridge is null: Service not started yet")
            return instance!!
        }

        private fun initDataBridge() {
            //  if (instance != null) throw IllegalStateException("ServiceDataBridge is not null: Service is already started")
            instance = SchedulerService()
        }

        private fun destroyDataBridge() {
            instance = null
        }
    }


    override fun onData(data: Any) {
        showLog("Data Passed: $data")
    }

    override fun subscribeForEvents(subscriber: ServiceEventListener) {
        if (!subscribers.contains(subscriber))
            subscribers.add(subscriber)
    }

    override fun unSubscribeForEvents(subscriber: ServiceEventListener) {
        if (subscribers.contains(subscriber))
            subscribers.remove(subscriber)
    }

    //data communication ends


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onCreate() {
        super.onCreate()
        startInForeGround()
        setupMqtt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initDataBridge()
        connectToMqtt()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mqttClient.isConnected)
            mqttClient.disconnect()
        if (!scheduledExecutorService.isShutdown)
            scheduledExecutorService.shutdownNow()
        destroyDataBridge()
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
        mqttConnectOptions.connectionTimeout = tcpTimout
        mqttConnectOptions.isHttpsHostnameVerificationEnabled = false
        mqttConnectOptions.keepAliveInterval = timeInterval.toInt()
        mqttClient.connect(mqttConnectOptions, this, this)
        showLog("Mqtt connection request sent")
    }

    private fun setupMqtt() {
        val persistanceDir = getDirForMqtt()
            ?: throw NullPointerException("No Persistence Directory Available for Mqtt ")

        mqttClient =
            MqttAsyncClient(
                credential.first,
                clientId,
                MqttDefaultFilePersistence(persistanceDir.absolutePath), this
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


    //Mqtt Message Callbacks

    lateinit var comms: ClientComms
    override fun init(comms: ClientComms?) {
        this.comms = comms!!
    }

    override fun start() {
        showLog("PingSender Started")
        schedule(timeInterval)
    }

    override fun stop() {

        showLog("PingSender stopped")
    }

    override fun schedule(delayInMilliseconds: Long) {
        scheduledExecutorService.schedule({ sendPing() }, timeInterval, TimeUnit.SECONDS)
    }


    private fun sendPing() {
        count += 1
        showLog("Ping is sent, Ping count: $count")
        if (!wakeLock.isHeld)
            wakeLock.acquire(timeInterval * 1000)

        val token: MqttToken? = comms.checkForActivity(object : IMqttActionListener {
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
    // mqtt setup end

    //Mqtt Message Callbacks
    // message callbacks
    override fun messageArrived(topic: String?, message: MqttMessage?) {
        showLog("A New message received ________ Message Content:${message.toString()} ")
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        showLog("Message is delivered successfully")
    }

    // connection callbacks
    override fun onSuccess(asyncActionToken: IMqttToken?) {

        showLog("connected successfully_______Token: $asyncActionToken")
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        showLog("connection is failed_______Token: $asyncActionToken")
        exception?.printStackTrace()
    }

    override fun connectionLost(cause: Throwable?) {
        count = 0
        showLog("connection to the host is lost")
        cause?.printStackTrace()
    }

    override fun connectComplete(b: Boolean, s: String) {
        showLog("connection to the host  is successful_______Token: $s")
    }


}

interface ServiceDataBridge {
    fun onData(data: Any)
    fun subscribeForEvents(subscriber: ServiceEventListener)
    fun unSubscribeForEvents(subscriber: ServiceEventListener)
}

interface ServiceEventListener {
    fun onEvent(data: Any)
}
