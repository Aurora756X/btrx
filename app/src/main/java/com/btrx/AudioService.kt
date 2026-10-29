package com.btrx

import android.app.*
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class AudioService : Service() {
    @Volatile private var running = false
    private var server: ServerSocket? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rx", "Receptor", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "rx").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("BT Receiver activo").build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1, n)
        if (!running) {
            running = true
            thread {
                try {
                    server = ServerSocket(5000)
                    while (running) try { server!!.accept().use { serve(it) } } catch (e: Exception) { }
                } catch (e: Exception) { }
            }
        }
        return START_STICKY
    }

    private fun serve(s: Socket) {
        s.tcpNoDelay = true
        val inp = DataInputStream(s.getInputStream())
        val rate = inp.readInt()
        val ch = inp.readInt().coerceIn(1, 2)
        val mask = if (ch == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val cfg = getSharedPreferences("cfg", MODE_PRIVATE)
        val mult = cfg.getInt("buf", 0) + 1
        val dropBytes = (20 + cfg.getInt("drop", 2) * 10) * rate / 1000 * ch * 2
        val minBuf = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(mask).build())
            .setBufferSizeInBytes(minBuf * mult)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val buf = ByteArray(rate / 200 * ch * 2) // 5 ms
        track.play()
        try {
            while (running) {
                inp.readFully(buf)
                // si se acumula retraso (> umbral configurado) en el socket, descartar para recuperar latencia
                while (inp.available() > dropBytes) inp.readFully(buf)
                track.write(buf, 0, buf.size)
            }
        } finally { track.stop(); track.release() }
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (e: Exception) { }
        super.onDestroy()
    }
}
