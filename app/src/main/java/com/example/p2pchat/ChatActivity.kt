package com.example.p2pchat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException

class ChatActivity : AppCompatActivity() {

    private lateinit var messageTextView: TextView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {




        super.onCreate(savedInstanceState)
        setContentView(R.layout.test)

        messageTextView = findViewById(R.id.receivedMessagesTextView)
        messageEditText = findViewById(R.id.inputMessageEditText)
        sendButton = findViewById(R.id.sendButton)

        val outputWriter: BufferedWriter? = (applicationContext as? DeviceList)?.outputWriter
        val inputReader: BufferedReader? = (applicationContext as? DeviceList)?.inputReader


        sendButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isNotEmpty()) {
                sendMessage(message, outputWriter)
                messageEditText.text.clear()
            }
        }

        Thread {
            while (true) {
                val message = readMessage(inputReader)
                if (message.isEmpty()) {
                    // Empty message indicates the end of the input stream, so break the loop
                    break
                }
                runOnUiThread {
                    messageTextView.append("Received: $message\n")
                }
            }
        }.start()
    }

    private fun sendMessage(message: String, outputWriter: BufferedWriter?) {
        try {
            outputWriter?.run {
                write(message)
                newLine()
                flush()
            }
            runOnUiThread {
                messageTextView.append("Sent: $message\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun readMessage(inputReader: BufferedReader?): String {
        val message = StringBuilder()
        try {
            var line: String? = inputReader?.readLine()
            while (line != null) {
                message.append(line)
                if (inputReader != null) {
                    line = inputReader.readLine()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return message.toString()
    }
}
