package com.donutsmp.adblockdns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.FileInputStream

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            tearDown()
            return START_NOT_STICKY
        }
        startAsForeground()
        val ok = startVpn()
        if (!ok) {
            lastError = "VPN interface failed to start"
            tearDown()
            return START_NOT_STICKY
        }
        lastError = null
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startVpn(): Boolean {
        if (vpnInterface != null) {
            isRunning = true
            broadcast()
            return true
        }

        val established = establishWithFallback() ?: return false
        vpnInterface = established
        isRunning = true

        readerThread = Thread({
            val buffer = ByteArray(32767)
            try {
                FileInputStream(established.fileDescriptor).use { input ->
                    while (!Thread.currentThread().isInterrupted && isRunning) {
                        val n = try {
                            input.read(buffer)
                        } catch (_: Exception) {
                            break
                        }
                        if (n <= 0) Thread.sleep(200)
                    }
                }
            } catch (_: Exception) {
            }
        }, "adblock-dns-tun").also {
            it.isDaemon = true
            it.start()
        }

        broadcast()
        return true
    }

    private fun establishWithFallback(): ParcelFileDescriptor? {
        return tryEstablish(ipv6 = true) ?: tryEstablish(ipv6 = false)
    }

    private fun tryEstablish(ipv6: Boolean): ParcelFileDescriptor? {
        return try {
            val b = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addDnsServer(DNS_PRIMARY)
                .addDnsServer(DNS_SECONDARY)
                .setBlocking(false)
            if (ipv6) {
                b.addAddress("fd00:8::2", 128)
                b.addDnsServer("2a10:50c0::ad1:ff")
                b.addDnsServer("2a10:50c0::ad2:ff")
            }
            b.establish()
        } catch (_: Exception) {
            null
        }
    }

    private fun tearDown() {
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
        broadcast()
    }

    private fun broadcast() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text, DNS_PRIMARY))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    override fun onRevoke() {
        tearDown()
        super.onRevoke()
    }

    companion object {
        const val ACTION_START = "com.donutsmp.adblockdns.START"
        const val ACTION_STOP = "com.donutsmp.adblockdns.STOP"
        const val ACTION_STATE_CHANGED = "com.donutsmp.adblockdns.STATE"
        const val DNS_PRIMARY = "94.140.14.14"
        const val DNS_SECONDARY = "94.140.15.15"
        const val NOTIFICATION_ID = 41
        private const val CHANNEL_ID = "vpn"

        @Volatile var isRunning: Boolean = false
            private set

        @Volatile var lastError: String? = null
            private set
    }
}
