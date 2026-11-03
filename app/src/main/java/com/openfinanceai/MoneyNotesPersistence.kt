package com.openfinanceai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local persistence for personal borrowing/lending notes. Amounts are stored in paise. */
internal fun loadMoneyNotes(context: Context): List<MoneyNote> = runCatching {
    val raw = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        .getString("money_notes", "[]") ?: "[]"
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val paymentsArray = item.optJSONArray("payments") ?: JSONArray()
        val payments = (0 until paymentsArray.length()).mapNotNull { p ->
            val payment = paymentsArray.optJSONObject(p) ?: return@mapNotNull null
            MoneyNotePayment(
                amountPaise = payment.optLong("amountPaise", 0L),
                date = payment.optString("date", ""),
                note = payment.optString("note", ""),
                allocation = runCatching {
                    MoneyNotePaymentAllocation.valueOf(payment.optString("allocation", "BOTH"))
                }.getOrDefault(MoneyNotePaymentAllocation.BOTH)
            )
        }
        val direction = runCatching {
            MoneyNoteDirection.valueOf(item.optString("direction"))
        }.getOrNull() ?: return@mapNotNull null
        val principal = item.optLong("principalPaise", 0L)
        if (principal <= 0L) return@mapNotNull null
        MoneyNote(
            id = item.optString("id"),
            direction = direction,
            personName = item.optString("personName"),
            principalPaise = principal,
            date = item.optString("date"),
            description = item.optString("description", ""),
            paidPaise = item.optLong("paidPaise", 0L).coerceIn(0L, principal),
            payments = payments,
            interestRateBasisPoints = item.optInt("interestRateBasisPoints", 0).coerceAtLeast(0)
        )
    }
}.getOrDefault(emptyList())

internal fun saveMoneyNotes(context: Context, notes: List<MoneyNote>) {
    val array = JSONArray()
    notes.forEach { note ->
        val payments = JSONArray()
        note.payments.forEach { payment ->
            payments.put(JSONObject()
                .put("amountPaise", payment.amountPaise)
                .put("date", payment.date)
                .put("note", payment.note)
                .put("allocation", payment.allocation.name))
        }
        array.put(JSONObject()
            .put("id", note.id)
            .put("direction", note.direction.name)
            .put("personName", note.personName)
            .put("principalPaise", note.principalPaise)
            .put("date", note.date)
            .put("description", note.description)
            .put("paidPaise", note.paidPaise)
            .put("payments", payments)
            .put("interestRateBasisPoints", note.interestRateBasisPoints))
    }
    context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        .edit().putString("money_notes", array.toString()).apply()
}
