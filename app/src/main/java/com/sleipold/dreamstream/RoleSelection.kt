package com.sleipold.dreamstream

import android.app.ActivityManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.content.Intent
import android.view.Menu
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.activity_role_selection.*

class RoleSelection : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        if (isServiceRunning(ConnectionService::class.java)) {
            // resume connection activity
            val intent = Intent(this, Connection::class.java)
            startActivity(intent)

        }

        btnRoleReceiver.setOnClickListener {
            // open connection activity as receiver
            val intent = Intent(this, Connection::class.java).apply {
                putExtra("role", "receiver")
            }
            startActivity(intent)
        }

        btnRoleSender.setOnClickListener {
            // open connection activity as sender
            val intent = Intent(this, Connection::class.java).apply {
                putExtra("role", "sender")
            }
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Adding menu to activity
        menuInflater.inflate(R.menu.main_menu, menu)
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

            R.id.settings -> {
                // gear icon got clicked -> open settings activity
                val intent = Intent(this, Settings::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

}
