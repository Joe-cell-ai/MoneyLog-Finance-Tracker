package com.openfinanceai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

internal object CreditCardReminderScheduler {
 fun schedule(context:Context) {
  val request=PeriodicWorkRequestBuilder<CreditCardReminderWorker>(24,TimeUnit.HOURS)
   .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()).build()
  WorkManager.getInstance(context).enqueueUniquePeriodicWork("moneylog-credit-card-due-reminders",ExistingPeriodicWorkPolicy.UPDATE,request)
 }
}
class CreditCardReminderWorker(context:Context,params:WorkerParameters):Worker(context,params) {
 override fun doWork():Result {
  val prefs=applicationContext.getSharedPreferences("open_finance_data",Context.MODE_PRIVATE)
  val enabled7=prefs.getBoolean("cc_reminder_7",true)
  val enabled3=prefs.getBoolean("cc_reminder_3",true)
  val enabled1=prefs.getBoolean("cc_reminder_1",true)
  if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return Result.success()
  val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel("credit_card_due","Credit card due reminders",NotificationManager.IMPORTANCE_DEFAULT))
  val data=CreditCardBills.read(applicationContext)
  val today=LocalDate.now()
  data.bills.filter{it.total>it.paid}.forEach{bill->
   val due=runCatching{LocalDate.parse(bill.due)}.getOrNull()?:return@forEach
   val days=java.time.temporal.ChronoUnit.DAYS.between(today,due).toInt()
   val should=when(days){7->enabled7;3->enabled3;1->enabled1;0->true;else->days<0}
   if(!should)return@forEach
   val onceKey="cc_reminder_sent_"+bill.id+"_"+today
   if(prefs.getBoolean(onceKey,false))return@forEach
   val card=data.cards.firstOrNull{it.id==bill.cardId}
   val title=if(days<0)"Credit card bill overdue" else if(days==0)"Credit card bill due today" else "Credit card bill due in "+days+" days"
   val body=(card?.bank?: "Credit card")+" •••• "+(card?.last4?: "")+" · Outstanding ₹"+(bill.total-bill.paid)
   val notification=NotificationCompat.Builder(applicationContext,"credit_card_due")
    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
    .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).build()
   manager.notify(bill.id.hashCode(),notification)
   prefs.edit().putBoolean(onceKey,true).apply()
  }
  return Result.success()
 }
}
