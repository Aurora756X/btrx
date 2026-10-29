package com.btrx

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.IBinder
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class MicService : Service() {
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val rate = 48000

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rx", "Receptor", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "rx").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Microfono -> PC activo").build()
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (!running) {
            running = true
            thread {
                try {
                    server = ServerSocket(5001)
                    while (running) try { server!!.accept().use { serve(it) } } catch (e: Exception) { }
                } catch (e: Exception) { }
            }
        }
        return START_STICKY
    }

    private fun serve(s: Socket) {
        s.tcpNoDelay = true
        val out = DataOutputStream(s.getOutputStream())
        out.writeInt(rate); out.writeInt(1)
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(minBuf * 2)
            .build()
        // forzar el microfono del telefono (no el de los cascos BT)
        val am = getSystemService(AudioManager::class.java)
        am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }?.let { rec.preferredDevice = it }
        val buf = ByteArray(rate / 200 * 2) // 5 ms
        rec.startRecording()
        try {
            while (running) {
                var off = 0
                while (off < buf.size) {
                    val r = rec.read(buf, off, buf.size - off)
                    if (r < 0) return
                    off += r
                }
                out.write(buf); out.flush()
            }
        } finally { rec.stop(); rec.release() }
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (e: Exception) { }
        super.onDestroy()
    }
}
