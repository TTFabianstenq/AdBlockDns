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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var statusDot: TextView
    private lateinit var statusLabel: TextView
    private lateinit var hint: TextView
    private lateinit var toggle: MaterialButton
    private lateinit var statusCard: MaterialCardView

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpnService()
        else render(denied = true)
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
        statusDot = findViewById(R.id.statusDot)
        statusLabel = findViewById(R.id.statusLabel)
        hint = findViewById(R.id.hint)
        toggle = findViewById(R.id.toggle)
        statusCard = findViewById(R.id.statusCard)
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
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    private fun requestAndStart() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnPermission.launch(prepare) else startVpnService()
    }

    private fun startVpnService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_START)
        )
    }

    private fun stopVpnService() {
        startService(Intent(this, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP))
    }

    private fun render(denied: Boolean = false) {
        val on = DnsVpnService.isRunning
        val err = DnsVpnService.lastError
        when {
            denied -> {
                statusDot.setBackgroundResource(R.drawable.dot_off)
                statusLabel.text = getString(R.string.status_denied)
                hint.text = getString(R.string.hint_denied)
            }
            err != null && !on -> {
                statusDot.setBackgroundResource(R.drawable.dot_off)
                statusLabel.text = getString(R.string.status_error)
                hint.text = err
            }
            on -> {
                statusDot.setBackgroundResource(R.drawable.dot_on)
                statusLabel.text = getString(R.string.status_on)
                hint.text = getString(R.string.hint_on)
            }
            else -> {
                statusDot.setBackgroundResource(R.drawable.dot_off)
                statusLabel.text = getString(R.string.status_off)
                hint.text = getString(R.string.hint_off)
            }
        }
        toggle.text = getString(if (on) R.string.stop else R.string.start)
    }
}
