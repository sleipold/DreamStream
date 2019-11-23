package com.sleipold.dreamstream

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.nearby.connection.*

class Connection : AConnection() {

    /* member */
    override lateinit var mName: String
    override val mServiceId: String = "com.sleipold.dreamstream"
    override val mStrategy: Strategy = Strategy.P2P_POINT_TO_POINT

    private var mState = State.UNKNOWN

    /* components */
    private lateinit var cCurrentState: TextView
    private lateinit var cRoleName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        /* member */
        mName = intent.getStringExtra("role")!!

        /* components */
        cCurrentState = findViewById(R.id.txtCurrState)
        cRoleName = findViewById(R.id.txtRoleName)

        cRoleName.text = mName
    }

    override fun onStart() {
        super.onStart()

        setState(State.SEARCHING)
    }

    private fun setState(pState: State) {
        if (mState == pState) {
            println("State set to $pState but device was already in this state.")
            return
        }

        mState = pState
        println("State set to $pState")
        onStateChanged(pState)
    }

    /**
     * State has changed.
     *
     * @param newState The new state. Prepare device for this state.
     */
    private fun onStateChanged(newState: State) {
        // update nearby connections to the new state
        when (newState) {
            State.SEARCHING -> {
                disconnectFromAllEndpoints()
                startDiscovering()
                startAdvertising()
                cCurrentState.setText(R.string.state_searching)
            }
            State.CONNECTED -> {
                stopDiscovering()
                stopAdvertising()
                cCurrentState.setText(R.string.state_connected)
            }
            State.UNKNOWN -> {
                stopAllEndpoints()
                cCurrentState.setText(R.string.state_unknown)
            }
        }
    }

    override fun onEndpointDiscovered(endpoint: Endpoint) {
        stopDiscovering()
        connectToEndpoint(endpoint)
    }

    override fun onConnectionInitiated(endpoint: Endpoint, connectionInfo: ConnectionInfo) {
        acceptConnection(endpoint)
    }

    override fun onEndpointConnected(endpoint: Endpoint) {
        Toast.makeText(
            this, getString(R.string.toast_connected, endpoint.name), Toast.LENGTH_SHORT
        )
            .show()
        setState(State.CONNECTED)
    }

    override fun onEndpointDisconnected(endpoint: Endpoint) {
        Toast.makeText(
            this, getString(R.string.toast_disconnected, endpoint.name), Toast.LENGTH_SHORT
        )
            .show()
        setState(State.SEARCHING)
    }

    override fun onConnectionFailed(endpoint: Endpoint?) {
        if (mState == State.SEARCHING) {
            startDiscovering()
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
                setState(State.UNKNOWN)
                // home icon got clicked -> open welcome activity
                val homeIntent = Intent(this, Welcome::class.java)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(homeIntent)
            }

            R.id.settings -> {
                setState(State.UNKNOWN)
                // gear icon got clicked -> open settings activity
                val intent = Intent(this, Settings::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setState(State.UNKNOWN)
    }

}
