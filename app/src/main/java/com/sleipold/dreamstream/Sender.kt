package com.sleipold.dreamstream

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class Sender : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        // Get the Intent that started this activity and extract the string
        val connectionSecret = intent.getStringExtra(CONNECTION_SECRET)

        // Capture the layout's TextView and set the string as its text
        findViewById<TextView>(R.id.txtConnectionSecret).apply {
            text = connectionSecret
        }
    }

}
