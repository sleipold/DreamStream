package com.sleipold.dreamstream

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible

class Settings : AppCompatActivity() {

    private lateinit var mContext: Context
    private lateinit var mSharedPrefs: SharedPreferences

    private lateinit var cVibration: Switch
    private lateinit var cWarnLevel: SeekBar
    private lateinit var cThresholdText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        mContext = applicationContext

        mSharedPrefs =
            mContext.getSharedPreferences(R.string.sharedPref.toString(), Context.MODE_PRIVATE)

        // Components
        cVibration = findViewById(R.id.swVibration)
        cWarnLevel = findViewById(R.id.sbWarnLevel)
        cThresholdText = findViewById(R.id.txtThreshold)

        // Listener

        cVibration.setOnCheckedChangeListener { _, isChecked ->
            // update layout
            cWarnLevel.isVisible = isChecked
            cThresholdText.isVisible = isChecked

            // update vibration in shared preferences
            val editor = mSharedPrefs.edit()
            editor.putBoolean("vibration", isChecked)
            editor.apply()
        }

        cWarnLevel.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean
            ) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // update warnlevel in shared preferences
                val editor = mSharedPrefs.edit()
                editor.putInt("warnlevel", cWarnLevel.progress)
                editor.apply()
            }
        })
    }

    override fun onStart() {
        super.onStart()

        cVibration.isChecked = mSharedPrefs.getBoolean("vibration", false)
        cWarnLevel.progress = mSharedPrefs.getInt("warnlevel", 50)
        cWarnLevel.isVisible = false
        cThresholdText.isVisible = false

        if (cVibration.isChecked) {
            cWarnLevel.isVisible = true
            cThresholdText.isVisible = true
        }
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
        when (item.itemId) {
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
