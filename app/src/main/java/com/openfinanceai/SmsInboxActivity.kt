package com.openfinanceai

import android.app.Activity
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.ViewGroup
import android.graphics.Color
import android.view.Gravity
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

class SmsInboxActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var messagesContainer: LinearLayout
    private var recipientFromIntent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipientFromIntent = intent.data?.schemeSpecificPart.orEmpty()
        showInbox()
    }

    override fun onResume() {
        super.onResume()
        if (::messagesContainer.isInitialized) loadMessages()
    }

    private fun showInbox() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(244, 250, 254))
        }
        val title = TextView(this).apply {
            text = "MoneyLog Messages"
            textSize = 25f
            setTextColor(Color.rgb(15, 41, 66))
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(title)
        val status = TextView(this).apply {
            text = if (isDefaultSmsApp()) "Default SMS app" else "Set MoneyLog as your default SMS app to manage messages"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 12)
        }
        root.addView(status)
        if (!isDefaultSmsApp()) {
            root.addView(Button(this).apply {
                text = "Set MoneyLog as default SMS app"
                setOnClickListener { requestDefaultSmsRole() }
            })
        }
        val compose = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val recipient = EditText(this).apply {
            hint = "Phone number"
            setSingleLine()
            setText(recipientFromIntent)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val body = EditText(this).apply { hint = "Message"; minLines = 1; maxLines = 3 }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage(recipient.text.toString().trim(), body.text.toString()) }
        }
        compose.addView(recipient)
        root.addView(compose)
        root.addView(body)
        root.addView(send)
        root.addView(TextView(this).apply {
            text = "Inbox"
            textSize = 18f
            setTextColor(Color.rgb(15, 41, 66))
            setPadding(0, 18, 0, 8)
        })
        messagesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(messagesContainer) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        loadMessages()
    }

    private fun isDefaultSmsApp(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) packageName == Telephony.Sms.getDefaultSmsPackage(this) else false

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS), 701)
            } else Toast.makeText(this, "SMS role is unavailable on this device", Toast.LENGTH_LONG).show()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName), 701)
        }
    }

    private fun sendMessage(number: String, message: String) {
        if (!isDefaultSmsApp()) {
            Toast.makeText(this, "Set MoneyLog as the default SMS app first", Toast.LENGTH_LONG).show()
            return
        }
        if (number.isBlank() || message.isBlank()) {
            Toast.makeText(this, "Enter a phone number and message", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val manager = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = manager.divideMessage(message)
            manager.sendMultipartTextMessage(number, null, parts, null, null)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, number)
                put(Telephony.Sms.BODY, message)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
            loadMessages()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Could not send message", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadMessages() {
        if (!::messagesContainer.isInitialized) return
        messagesContainer.removeAllViews()
        if (!isDefaultSmsApp()) {
            messagesContainer.addView(TextView(this).apply {
                text = "Choose “Set MoneyLog as default SMS app” to open your inbox."
                textSize = 14f
                setPadding(8, 18, 8, 18)
                setTextColor(Color.DKGRAY)
            })
            return
        }
        runCatching {
            val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
            contentResolver.query(Telephony.Sms.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " DESC")?.use { cursor ->
                val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                var count = 0
                while (cursor.moveToNext() && count < 250) {
                    count++
                    val sender = cursor.getString(address).orEmpty()
                    val content = cursor.getString(body).orEmpty()
                    val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(cursor.getLong(date)))
                    messagesContainer.addView(TextView(this).apply {
                        text = (if (cursor.getInt(type) == Telephony.Sms.MESSAGE_TYPE_INBOX) sender else "You → $sender") +
                            "\n" + content + "\n" + whenText
                        textSize = 14f
                        setTextColor(Color.rgb(15, 41, 66))
                        setPadding(14, 12, 14, 12)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.WHITE); cornerRadius = 24f
                        }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 5, 0, 5)
                    })
                }
                if (count == 0) messagesContainer.addView(TextView(this).apply { text = "No messages yet"; setPadding(12, 20, 12, 20) })
            }
        }.onFailure {
            messagesContainer.addView(TextView(this).apply { text = "Could not load messages: ${it.message}" })
        }
    }
}
