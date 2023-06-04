package com.example.p2pchat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException

class ChatActivity : AppCompatActivity() {

    private lateinit var messageTextView: TextView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button

    private lateinit var outputWriter: BufferedWriter
    private lateinit var inputReader: BufferedReader
    private var isGroupOwner: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test)

        messageTextView = findViewById(R.id.receivedMessagesTextView)
        messageEditText = findViewById(R.id.inputMessageEditText)
        sendButton = findViewById(R.id.sendButton)

        outputWriter = DeviceList.outputWriter!!
        inputReader = DeviceList.inputReader!!

        isGroupOwner = intent.getBooleanExtra("isGroupOwner", false)

        sendButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageEditText.text.clear()
            }
        }

        Thread {
            try {
                var message: String?
                if (inputReader != null) {
                    while (inputReader.readLine().also { message = it } != null) {

                        if(message?.startsWith("ACK::") == true){
                            runOnUiThread {
                                showToast("Message has been sent succesfully")
                            }

                        }
                        else{
                            runOnUiThread {
                                messageTextView.append("Received: $message\n")
                            }
                            sendMessage("ACK::")
                        }


                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun sendMessage(message: String) {
        Thread {
            try {
                outputWriter?.write(message)
                outputWriter?.newLine()
                outputWriter?.flush()

                if(message.startsWith("ACK::")){
                    runOnUiThread{
                        showToast("ACK Sent")
                    }
                }
                else{
                    runOnUiThread {
                        messageTextView.append("Sent: $message\n")
                    }
                }


            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
