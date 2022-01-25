package com.example.alaramtest

import android.content.BroadcastReceiver
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import android.app.AlarmManager.AlarmClockInfo

import android.app.PendingIntent
import android.app.AlarmManager











class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
       val btnStartService = findViewById<View>(R.id.buttonStartService)
       val btnStopService = findViewById<View>(R.id.buttonStopService)
        btnStartService.setOnClickListener {
                startService()
        }
        btnStopService.setOnClickListener {
            stopService()
        }

    }


    private fun startService() {
        val serviceIntent = Intent(this, SchedulerService::class.java)
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android")
        ContextCompat.startForegroundService(applicationContext, serviceIntent)
     //   startScheduling()
    }

    private fun stopService() {
        val serviceIntent = Intent(this, SchedulerService::class.java)
        stopService(serviceIntent)
     //   stopScheduling()
    }
//    val reciever = receiver()
//    val mgr:AlarmManager by lazy { getSystemService(ALARM_SERVICE) as AlarmManager}
//    val  operation:PendingIntent by lazy {   PendingIntent.getBroadcast(this, 0, Intent("Hello"), 0)}
//    fun stopScheduling(){
//        unregisterReceiver(reciever)
//        mgr.cancel(operation)
//    }
//
//
//    fun startScheduling(){
//        registerReceiver(reciever, IntentFilter("Hello"))
//        schedule()
//    }
//
//
//    fun schedule(){
//        val info = AlarmClockInfo(System.currentTimeMillis()+1000*5, operation)
//        mgr.setAlarmClock(info, operation)
//    }
//    fun showLog(log: String){
//        Log.d("MainActivity",log)
//    }
//
//    fun sendPing(){
//        showLog("On Received Called")
//    }
//
//    inner class receiver:BroadcastReceiver(){
//        override fun onReceive(context: Context?, intent: Intent?) {
//            sendPing()
//            schedule()
//        }
//    }

}