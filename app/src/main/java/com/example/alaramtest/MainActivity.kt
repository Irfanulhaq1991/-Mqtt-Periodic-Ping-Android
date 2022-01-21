package com.example.alaramtest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.view.View
import androidx.core.content.ContextCompat





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
    }

    private fun stopService() {
        val serviceIntent = Intent(this, SchedulerService::class.java)
        stopService(serviceIntent)
    }
}