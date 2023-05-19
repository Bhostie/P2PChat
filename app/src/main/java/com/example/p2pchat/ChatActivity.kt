package com.example.p2pchat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ChatActivity : AppCompatActivity() {

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private var isWifiP2pEnabled = false

    private lateinit var deviceListView: ListView
    private lateinit var discoverButton: Button

    private lateinit var devices: MutableList<WifiP2pDevice>
    private lateinit var deviceArrayAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        receiver = WiFiDirectBroadcastReceiver()

        deviceListView = findViewById(R.id.deviceListView)
        discoverButton = findViewById(R.id.discoverButton)

        devices = mutableListOf()
        deviceArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        deviceListView.adapter = deviceArrayAdapter

        deviceListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedDevice = devices[position]
            // Connect to the selected device
            // Implement the connection logic here
        }

        discoverButton.setOnClickListener {
            if (isWifiP2pEnabled) {
                discoverPeers()
            } else {
                showToast("Please enable Wi-Fi and try again.")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION))
        registerReceiver(receiver, IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                showToast("Peer discovery initiated.")
            }

            override fun onFailure(reasonCode: Int) {
                showToast("Peer discovery failed. Please try again.")
            }
        })
    }

    private inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isWifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (isWifiP2pEnabled) {
                        wifiP2pManager.requestPeers(channel, object : WifiP2pManager.PeerListListener {
                            override fun onPeersAvailable(peerList: WifiP2pDeviceList) {
                                devices.clear()
                                devices.addAll(peerList.deviceList)
                                deviceArrayAdapter.clear()
                                for (device in devices) {
                                    deviceArrayAdapter.add(device.deviceName)
                                }
                                deviceArrayAdapter.notifyDataSetChanged()
                            }
                        })
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Rest of the code for sending/receiving messages, connecting to other devices, etc.
}
