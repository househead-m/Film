package com.example.filmcamera

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val switchGrid = findViewById<Switch>(R.id.switchGrid)
        switchGrid.isChecked = SettingsPrefs.isGridEnabled(this)
        switchGrid.setOnCheckedChangeListener { _, isChecked ->
            SettingsPrefs.setGridEnabled(this, isChecked)
        }

        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }
    }
}
