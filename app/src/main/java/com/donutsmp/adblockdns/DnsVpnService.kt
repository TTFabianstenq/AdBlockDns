package com.donutsmp.adblockdns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("AdBlock DNS")
            .setMtu(1500)
            .addAddress("10.8.0.2", 32)
            .addDnsServer(DNS_PRIMARY)
            .addDnsServer(DNS_SECONDARY)
            .addAddress("fd00:8::2", 128)
            .addDnsServer("2a10:50c0::ad1:ff")
            .addDnsServer("2a10:50c0::ad2:ff")
            .setBlocking(false)

        vpnInterface = builder.establish()
        isRunning = vpnInterface != null

        val pfd = vpnInterface
        if (pfd != null) {
            readerThread = Thread {
                val buffer = ByteArray(32767)
                val input = FileInputStream(pfd.fileDescriptor)
                try {
                    while (!Thread.interrupted() && isRunning) {
                        if (input.read(buffer) <= 0) {
                            Thread.sleep(250)
                        }
                    }
                } catch (_: Exception) {
                }
            }.also { it.start() }
        }

        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun stopVpn() {
        isRunning = false
        readerThread?.interrupt()
        readerThread = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun buildNotification(): Notification {
        val channelId = "vpn"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "AdBlock DNS", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AdBlock DNS")
            .setContentText("DNS $DNS_PRIMARY (AdGuard)")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    companion object {
        const val ACTION_START = "com.donutsmp.adblockdns.START"
        const val ACTION_STOP = "com.donutsmp.adblockdns.STOP"
        const val ACTION_STATE_CHANGED = "com.donutsmp.adblockdns.STATE"
        const val DNS_PRIMARY = "94.140.14.14"
        const val DNS_SECONDARY = "94.140.15.15"
        const val NOTIFICATION_ID = 41

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
