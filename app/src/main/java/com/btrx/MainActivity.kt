package com.btrx

import android.app.Activity
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val svc = Intent(this, AudioService::class.java)
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }

        fun slider(label: String, key: String, max: Int, def: Int, fmt: (Int) -> String) {
            val tv = TextView(this)
            val sb = SeekBar(this).apply {
                this.max = max
                progress = p.getInt(key, def)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                        tv.text = "$label: ${fmt(v)}"
                        p.edit().putInt(key, v).apply()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            tv.text = "$label: ${fmt(sb.progress)}"
            l.addView(tv); l.addView(sb)
        }

        l.addView(TextView(this).apply { text = "Escuchando en el puerto TCP 5000\nConecta los cascos BT al movil y pulsa Iniciar.\n" })
        slider("Buffer de salida", "buf", 5, 0) { "${it + 1}x el minimo" }
        slider("Umbral de descarte", "drop", 18, 2) { "${20 + it * 10} ms" }
        l.addView(TextView(this).apply { text = "Los cambios se aplican al reconectar el PC (reinicia send.py).\nSi oyes cortes, sube el buffer.\n" })
        l.addView(Button(this).apply { text = "Iniciar"; setOnClickListener { startForegroundService(svc) } })
        l.addView(Button(this).apply { text = "Detener"; setOnClickListener { stopService(svc) } })
        val mic = Intent(this, MicService::class.java)
        l.addView(TextView(this).apply { text = "\nMicrofono -> PC (puerto TCP 5001)" })
        l.addView(Button(this).apply {
            text = "Iniciar microfono"
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    startForegroundService(mic)
                else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
            }
        })
        l.addView(Button(this).apply { text = "Detener microfono"; setOnClickListener { stopService(mic) } })
        setContentView(l)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 2 && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED)
            startForegroundService(Intent(this, MicService::class.java))
    }
}
