package com.openfinanceai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** Phase 1 records: no-interest borrowing/lending between family and friends. */
data class MoneyNote(
    val id: String,
    val direction: MoneyNoteDirection,
    val personName: String,
    val principalPaise: Long,
    val date: String,
    val description: String = "",
    val paidPaise: Long = 0L,
    val payments: List<MoneyNotePayment> = emptyList()
) {
    val outstandingPaise: Long get() = (principalPaise - paidPaise).coerceAtLeast(0L)
    val status: MoneyNoteStatus get() = when {
        outstandingPaise == 0L -> MoneyNoteStatus.SETTLED
        paidPaise > 0L -> MoneyNoteStatus.PARTIALLY_PAID
        else -> MoneyNoteStatus.PENDING
    }
}
enum class MoneyNoteDirection { I_NEED_TO_PAY, I_NEED_TO_COLLECT }
enum class MoneyNoteStatus { PENDING, PARTIALLY_PAID, SETTLED }
data class MoneyNotePayment(val amountPaise: Long, val date: String, val note: String = "")

private fun moneyNotesCurrency(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(paise / 100.0)

@Composable
fun MoneyNotesScreen(
    notes: List<MoneyNote>,
    onNotesChanged: (List<MoneyNote>) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    var direction by remember { mutableStateOf(MoneyNoteDirection.I_NEED_TO_PAY) }
    var addDialog by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<MoneyNote?>(null) }
    val payTotal = notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_PAY }.sumOf { it.outstandingPaise }
    val collectTotal = notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_COLLECT }.sumOf { it.outstandingPaise }
    val visible = notes.filter { it.direction == direction }.sortedByDescending { it.date }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Money Notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Personal money borrowed and lent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { addDialog = true }) { Icon(Icons.Default.Add, "Add money note") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("I Need to Pay", payTotal, Color(0xFFFFF0E7), Color(0xFFB65C27), Modifier.weight(1f))
            SummaryTile("I Need to Collect", collectTotal, Color(0xFFE7F4EC), Color(0xFF287A4B), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(direction == MoneyNoteDirection.I_NEED_TO_PAY, { direction = MoneyNoteDirection.I_NEED_TO_PAY }, label = { Text("I Need to Pay") })
            FilterChip(direction == MoneyNoteDirection.I_NEED_TO_COLLECT, { direction = MoneyNoteDirection.I_NEED_TO_COLLECT }, label = { Text("I Need to Collect") })
        }
        if (visible.isEmpty()) {
            Card {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Text("No records yet", fontWeight = FontWeight.SemiBold)
                    Text("Add a person and amount to start your personal money register.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { addDialog = true }) { Text("Add Money Note") }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { note -> MoneyNoteCard(note) { paymentTarget = note } }
            }
        }
    }
    if (addDialog) AddMoneyNoteDialog(
        onDismiss = { addDialog = false },
        onSave = { note -> onNotesChanged(notes + note); direction = note.direction; addDialog = false }
    )
    paymentTarget?.let { target ->
        RecordMoneyNotePaymentDialog(target, { paymentTarget = null }) { payment ->
            onNotesChanged(notes.map { old ->
                if (old.id == target.id) old.copy(
                    paidPaise = old.paidPaise + payment.amountPaise,
                    payments = old.payments + payment
                ) else old
            })
            paymentTarget = null
        }
    }
}

@Composable
private fun SummaryTile(title: String, amount: Long, bg: Color, fg: Color, modifier: Modifier) {
    Surface(modifier, color = bg, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = fg, style = MaterialTheme.typography.labelMedium)
            Text(moneyNotesCurrency(amount), color = Color(0xFF20242B), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MoneyNoteCard(note: MoneyNote, onPayment: () -> Unit) {
    val owes = note.direction == MoneyNoteDirection.I_NEED_TO_PAY
    val accent = if (owes) Color(0xFFB65C27) else Color(0xFF287A4B)
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (owes) Color(0xFFFFF0E7) else Color(0xFFE7F4EC), shape = RoundedCornerShape(12.dp)) {
                    Icon(if (owes) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = accent, modifier = Modifier.padding(10.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(note.personName, fontWeight = FontWeight.SemiBold)
                    Text(note.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(moneyNotesCurrency(note.outstandingPaise), fontWeight = FontWeight.Bold)
                    Text(note.status.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, color = accent, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (note.description.isNotBlank()) Text(note.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Original: " + moneyNotesCurrency(note.principalPaise), style = MaterialTheme.typography.labelSmall)
                Text("Paid: " + moneyNotesCurrency(note.paidPaise), style = MaterialTheme.typography.labelSmall)
            }
            if (note.outstandingPaise > 0L) {
                HorizontalDivider()
                TextButton(onClick = onPayment, modifier = Modifier.align(Alignment.End)) { Text("Record payment") }
            }
        }
    }
}

@Composable
private fun AddMoneyNoteDialog(onDismiss: () -> Unit, onSave: (MoneyNote) -> Unit) {
    var direction by remember { mutableStateOf(MoneyNoteDirection.I_NEED_TO_PAY) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Money Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(direction == MoneyNoteDirection.I_NEED_TO_PAY, { direction = MoneyNoteDirection.I_NEED_TO_PAY }, label = { Text("I Need to Pay") })
                    FilterChip(direction == MoneyNoteDirection.I_NEED_TO_COLLECT, { direction = MoneyNoteDirection.I_NEED_TO_COLLECT }, label = { Text("I Need to Collect") })
                }
                OutlinedTextField(person, { person = it; error = "" }, label = { Text("Person name") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(date, { date = it; error = "" }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") })
                Text("Interest: No interest (Family & Friends)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val paise = runCatching { amount.toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
                val validDate = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
                when {
                    person.isBlank() -> error = "Enter the person's name."
                    paise == null || paise <= 0L -> error = "Enter a valid amount greater than zero."
                    !validDate -> error = "Enter a valid date (YYYY-MM-DD)."
                    else -> onSave(MoneyNote(UUID.randomUUID().toString(), direction, person.trim(), paise, date, description.trim()))
                }
            }) { Text("Save record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecordMoneyNotePaymentDialog(note: MoneyNote, onDismiss: () -> Unit, onSave: (MoneyNotePayment) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding: " + moneyNotesCurrency(note.outstandingPaise))
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Payment amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Payment date (YYYY-MM-DD)") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val paise = amount.toLongOrNull()?.let { it * 100L }
                val validDate = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
                when {
                    paise == null || paise <= 0L -> error = "Enter a payment greater than zero."
                    paise > note.outstandingPaise -> error = "Payment cannot exceed the outstanding amount."
                    !validDate -> error = "Enter a valid payment date."
                    else -> onSave(MoneyNotePayment(paise, date))
                }
            }) { Text("Save payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
