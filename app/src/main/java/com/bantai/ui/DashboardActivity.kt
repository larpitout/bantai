package com.bantai.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Dashboard ni Bantai (pinupunan sa susunod na hakbang). */
class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Bantai" })
    }
}
