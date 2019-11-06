package com.sleipold.dreamstream

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.content.Intent
import android.view.Menu
import kotlinx.android.synthetic.main.activity_role_selection.*

class RoleSelection : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        btnRoleReceiver.setOnClickListener {
            // role receiver button got clicked -> open receiver connection activity
            val intent = Intent(this, ReceiverConnection::class.java)
            startActivity(intent)
        }

        btnRoleSender.setOnClickListener {
            // role sender button got clicked -> open sender connection activity
            val intent = Intent(this, SenderConnection::class.java)
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
        when(item.itemId) {
            R.id.home -> {
                // home icon got clicked -> open welcome activity
                val homeIntent = Intent(this, Welcome::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // TODO: reset running connection
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
}
