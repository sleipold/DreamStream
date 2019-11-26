package com.sleipold.dreamstream

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.content.Context
import android.content.SharedPreferences
import android.widget.Button
import android.widget.Switch

class Settings : AppCompatActivity() {

    private lateinit var mContext: Context

    private lateinit var mSharedPrefs: SharedPreferences

    private lateinit var cVibration: Switch

    private lateinit var cWarnLevel: SeekBar

    private lateinit var cSaveSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        mContext = applicationContext

        mSharedPrefs = mContext.getSharedPreferences(R.string.sharedPref.toString(), Context.MODE_PRIVATE)

        /* Components */
        cVibration = findViewById(R.id.swVibration)
        cWarnLevel = findViewById(R.id.sbWarnLevel)
        cSaveSettings = findViewById(R.id.btnSaveSettings)

        /* Listener */
        cSaveSettings.setOnClickListener{
            val editor = mSharedPrefs.edit()
            editor.putBoolean("vibration", cVibration.isChecked)
            editor.putInt("warnlevel", cWarnLevel.progress)
            editor.apply()
        }
    }

    override fun onStart() {
        super.onStart()

        cVibration.isChecked = mSharedPrefs.getBoolean("vibration", false)
        cWarnLevel.progress = mSharedPrefs.getInt("warnlevel", 0)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Adding menu to activity
        menuInflater.inflate(R.menu.main_menu, menu)
        // hide settings button in action bar
        menu!!.findItem(R.id.settings).isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // onClick listener for menu
        when(item.itemId) {
            R.id.home -> {
                // home icon got clicked -> open welcome activity
                val homeIntent = Intent(this, Welcome::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(homeIntent)
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
