package com.donutsmp.adblockdns

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            status.text = getString(R.string.status_denied)
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle)
        findViewById<TextView>(R.id.dnsValue).text = DnsVpnService.DNS_PRIMARY

        toggle.setOnClickListener {
            if (DnsVpnService.isRunning) stopVpnService() else requestAndStart()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(DnsVpnService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
        render()
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    private fun requestAndStart() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnPermission.launch(prepare) else startVpnService()
    }

    private fun startVpnService() {
        val intent = Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        status.postDelayed({ render() }, 400)
    }

    private fun stopVpnService() {
        startService(Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP))
        status.postDelayed({ render() }, 400)
    }

    private fun render() {
        val on = DnsVpnService.isRunning
        status.text = getString(if (on) R.string.status_on else R.string.status_off)
        toggle.text = getString(if (on) R.string.stop else R.string.start)
    }
}
