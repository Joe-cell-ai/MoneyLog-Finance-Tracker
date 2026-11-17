package com.openfinanceai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsAutoImportReceiver : BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent?) {
  if(intent?.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION)return
  val messages=Telephony.Sms.Intents.getMessagesFromIntent(intent)
  if(messages.isNullOrEmpty())return
  val groups=messages.groupBy{it.originatingAddress.orEmpty().trim() to it.timestampMillis}
  var changed=false
  groups.forEach{(key,parts)->
   val body=parts.sortedBy{it.indexOnIcc}.joinToString(""){it.messageBody.orEmpty()}
   if(CreditCardBills.ingestSms(context,body))changed=true
   else if(SmsTransactionImporter.import(context,key.first,key.second,body))changed=true
  }
  if(changed)context.sendBroadcast(Intent(ACTION_IMPORTED).setPackage(context.packageName))
 }
 companion object{const val ACTION_IMPORTED="com.openfinanceai.ACTION_SMS_TRANSACTION_IMPORTED"}
}
