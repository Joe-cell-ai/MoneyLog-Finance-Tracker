package com.openfinanceai

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Required response-via-message endpoint; interactive replies are composed in SmsInboxActivity. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
