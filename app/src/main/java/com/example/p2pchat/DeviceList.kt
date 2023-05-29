package com.example.p2pchat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class DeviceList : AppCompatActivity() {


    var outputWriter: BufferedWriter? = null
    var inputReader: BufferedReader? = null

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter
    private var isWifiP2pEnabled = false

    private lateinit var deviceListView: ListView
    private lateinit var discoverButton: Button

    private lateinit var devices: MutableList<WifiP2pDevice>
    private lateinit var deviceArrayAdapter: ArrayAdapter<String>

    private lateinit var connectionInfo: WifiP2pInfo

    companion object {
        private const val SERVER_PORT = 8888
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.device_list)

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        receiver = WiFiDirectBroadcastReceiver()
        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        deviceListView = findViewById(R.id.deviceListView)
        discoverButton = findViewById(R.id.discoverButton)

        devices = mutableListOf()
        deviceArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        deviceListView.adapter = deviceArrayAdapter

        deviceListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedDevice = devices[position]
            connectToDevice(selectedDevice)
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
        registerReceiver(receiver, intentFilter)
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

    private fun connectToDevice(device: WifiP2pDevice) {

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0  // Set the group owner intent to 0 for now
        }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                showToast("Connection to ${device.deviceName} initiated.")
            }

            override fun onFailure(reasonCode: Int) {
                showToast("Connection failed. Please try again.")
            }
        })
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isWifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager.requestPeers(channel) { peerList: WifiP2pDeviceList ->
                        devices.clear()
                        devices.addAll(peerList.deviceList)

                        deviceArrayAdapter.clear()
                        devices.forEach { device ->
                            deviceArrayAdapter.add(device.deviceName + "\n" + device.deviceAddress)
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        wifiP2pManager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                            connectionInfo = info
                            if (connectionInfo.groupFormed && connectionInfo.isGroupOwner) {
                                Thread {
                                    // Start server socket to listen for incoming connections
                                    val serverSocket = ServerSocket(SERVER_PORT)
                                    val clientSocket = serverSocket.accept()

                                    outputWriter = clientSocket.getOutputStream().bufferedWriter()
                                    inputReader = clientSocket.getInputStream().bufferedReader()

                                    // Start activity to handle the chat
                                    val intent = Intent(context, ChatActivity::class.java)
                                    startActivity(intent)
                                }.start()
                            } else if (connectionInfo.groupFormed) {
                                Thread {
                                    // Connect as a client to the group owner
                                    val hostAddress = connectionInfo.groupOwnerAddress.hostAddress
                                    val clientSocket = Socket(hostAddress, SERVER_PORT)

                                    outputWriter = clientSocket.getOutputStream().bufferedWriter()
                                    inputReader = clientSocket.getInputStream().bufferedReader()

                                    // Start activity to handle the chat
                                    val intent = Intent(context, ChatActivity::class.java)
                                    startActivity(intent)
                                }.start()
                            }
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // TODO: Handle this action if needed
                }
            }
        }
    }
}
