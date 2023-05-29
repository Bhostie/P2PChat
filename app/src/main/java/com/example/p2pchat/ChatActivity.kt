package com.example.p2pchat

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class ChatActivity : AppCompatActivity() {

    private lateinit var receivedMessagesTextView: TextView
    private lateinit var inputMessageEditText: EditText
    private lateinit var sendButton: Button

    private lateinit var clientSocket: Socket
    private lateinit var outputWriter: PrintWriter
    private lateinit var inputReader: BufferedReader

    companion object {
        private const val SERVER_PORT = 8888
        const val EXTRA_IS_GROUP_OWNER = "is_group_owner"
        const val EXTRA_HOST_ADDRESS = "host_address"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test)

        receivedMessagesTextView = findViewById(R.id.receivedMessagesTextView)
        inputMessageEditText = findViewById(R.id.inputMessageEditText)
        sendButton = findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
            val message = inputMessageEditText.text.toString().trim()

            if (message.isNotEmpty()) {
                // Send the message to the connected device
                sendMessage(message)

                // Add the sent message to the chat screen
                addMessage("Me: $message")

                // Clear the input field
                inputMessageEditText.text.clear()
            }
        }

        // Get the connection information from the intent
        val isGroupOwner = intent.getBooleanExtra(EXTRA_IS_GROUP_OWNER, false)
        val hostAddress = intent.getStringExtra(EXTRA_HOST_ADDRESS)

        // Initialize the clientSocket and perform network operations in a background thread
        Thread {
            try {
                clientSocket = Socket(hostAddress, SERVER_PORT)

                // Initialize the outputWriter and inputReader
                outputWriter = PrintWriter(clientSocket.getOutputStream(), true)
                inputReader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

                // Receive messages from the server
                var message: String?
                while (true) {
                    message = inputReader.readLine()
                    if (message != null) {
                        runOnUiThread {
                            addMessage("Server: $message")
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                closeSocketConnection()
            }
        }.start()
    }

    private fun sendMessage(message: String) {
        Thread {
            try {
                outputWriter.println(message)
                outputWriter.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        closeSocketConnection()
    }

    private fun addMessage(message: String) {
        receivedMessagesTextView.append("$message\n")
    }

    private fun closeSocketConnection() {
        try {
            if (::outputWriter.isInitialized) {
                outputWriter.close()
            }
            if (::inputReader.isInitialized) {
                inputReader.close()
            }
            if (::clientSocket.isInitialized && !clientSocket.isClosed) {
                clientSocket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
