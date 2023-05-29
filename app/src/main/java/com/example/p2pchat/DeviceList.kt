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
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class DeviceList : AppCompatActivity() {

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
    private lateinit var chatServer: ChatServer
    private lateinit var chatClient: ChatClient

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
                showToast("Connection request sent to ${device.deviceName}. Waiting for connection to be established...")
            }

            override fun onFailure(reasonCode: Int) {
                showToast("Connection to ${device.deviceName} failed. Please try again.")
            }
        })
    }




    private fun disconnect(){
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                showToast("Disconnected")
            }

            override fun onFailure(response: Int) {
                showToast("Disconnection Failed")
            }
        })
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
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        // We are connected, get connection information
                        wifiP2pManager.requestConnectionInfo(channel, object : WifiP2pManager.ConnectionInfoListener {
                            override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
                                connectionInfo = info
                                println("\nConnection Info: ${connectionInfo.toString()}")
                                if (connectionInfo.groupFormed && connectionInfo.isGroupOwner) {
                                    // We are the group owner, start a chat server
                                    chatServer = ChatServer()
                                    chatServer.start()
                                } else if (connectionInfo.groupFormed) {
                                    // We are a client, connect to the group owner
                                    chatClient = ChatClient(connectionInfo.groupOwnerAddress)
                                    chatClient.start()
                                }

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

    private fun closeSocketConnection() {
        try {
            chatServer.interrupt()
            chatClient.interrupt()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private inner class ChatServer : Thread() {
        private var running = false

        override fun start() {
            running = true
            super.start()

        }

        fun stopServer() {
            running = false
        }

        override fun run() {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                //while (running) {
                    val socket = serverSocket.accept()
                    // Handle incoming connections
                    println("Chat Server Running with IP: ${socket.localAddress.toString()}")
                    showToast("Chat Server Running with IP: ${socket.localAddress.toString()}")
                    val intent = Intent(this@DeviceList, ChatActivity::class.java).apply {
                        putExtra(ChatActivity.EXTRA_IS_GROUP_OWNER, connectionInfo.groupFormed && connectionInfo.isGroupOwner)
                        putExtra(ChatActivity.EXTRA_HOST_ADDRESS, connectionInfo.groupOwnerAddress?.hostAddress)
                    }
                    startActivity(intent)
                //}
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                serverSocket?.close()
            }
        }
    }

    private inner class ChatClient(private val hostAddress: InetAddress) : Thread() {
        private var running = false

        override fun start() {
            running = true
            super.start()
        }

        fun stopClient() {
            running = false
        }

        override fun run() {
            var socket: Socket? = null
            try {
                socket = Socket(hostAddress, SERVER_PORT)
                // Handle the connection
                println("Client Running with local IP: ${socket.localAddress.toString()}")
                showToast("Client Running with local IP: ${socket.localAddress.toString()}")
                val intent = Intent(this@DeviceList, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_IS_GROUP_OWNER, connectionInfo.groupFormed && connectionInfo.isGroupOwner)
                    putExtra(ChatActivity.EXTRA_HOST_ADDRESS, connectionInfo.groupOwnerAddress?.hostAddress)
                }
                startActivity(intent)


            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                socket?.close()
            }
        }
    }



    // Rest of the code for sending/receiving messages, handling data exchange, etc.
}