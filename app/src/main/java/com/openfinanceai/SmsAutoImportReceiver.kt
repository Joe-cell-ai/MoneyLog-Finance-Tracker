package com.openfinanceai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.Locale

class SmsAutoImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Android may split one long SMS into multiple PDUs. Join parts before parsing.
        val groups = messages.groupBy { it.originatingAddress.orEmpty().trim() to it.timestampMillis }
        var added = 0
        groups.forEach { (key, parts) ->
            val body = parts.sortedBy { it.indexOnIcc }.joinToString("") { it.messageBody.orEmpty() }
            if (SmsTransactionImporter.import(context, key.first, key.second, body)) added++
        }
        if (added > 0) context.sendBroadcast(Intent(ACTION_IMPORTED).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_IMPORTED = "com.openfinanceai.ACTION_SMS_TRANSACTION_IMPORTED"
    }
}
