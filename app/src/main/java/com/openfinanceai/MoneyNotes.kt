package com.openfinanceai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.delay

private val NotesBlue = Color(0xFF0879E8)
private val NotesCyan = Color(0xFF20C4D8)
private val NotesPurple = Color(0xFF9A55E8)
private val NotesInk = Color(0xFF17213B)
private val NotesMuted = Color(0xFF7B879C)
private val NotesLine = Color(0xFFE4ECF5)
private val NotesCanvas = Color(0xFFF3F8FF)
private val NotesMint = Color(0xFFE4F9FA)
private val NotesPink = Color(0xFFFFE5F5)

enum class MoneyNoteLoanType { GOLD_LOAN, LOCAL_LOAN, FAMILY_FRIENDS, OTHER }

/** A local-first borrowing/lending record. All monetary values are stored in paise. */
data class MoneyNote(
    val id: String,
    val direction: MoneyNoteDirection,
    val personName: String,
    val principalPaise: Long,
    val date: String,
    val description: String = "",
    val paidPaise: Long = 0L,
    val payments: List<MoneyNotePayment> = emptyList(),
    /** Monthly rate in basis points: 150 means 1.50% per month. Zero means no interest. */
    val interestRateBasisPoints: Int = 0,
    val loanType: MoneyNoteLoanType = MoneyNoteLoanType.FAMILY_FRIENDS
) {
    val balance: MoneyNoteBalance get() = calculateMoneyNoteBalance(this)
    val outstandingPaise: Long get() = balance.totalPaise
    val status: MoneyNoteStatus get() = when {
        outstandingPaise == 0L -> MoneyNoteStatus.SETTLED
        paidPaise > 0L || payments.isNotEmpty() -> MoneyNoteStatus.PARTIALLY_PAID
        else -> MoneyNoteStatus.PENDING
    }
}
enum class MoneyNoteDirection { I_NEED_TO_PAY, I_NEED_TO_COLLECT }
enum class MoneyNoteStatus { PENDING, PARTIALLY_PAID, SETTLED }
enum class MoneyNotePaymentAllocation { INTEREST_ONLY, PRINCIPAL_ONLY, BOTH }
data class MoneyNotePayment(
    val amountPaise: Long,
    val date: String,
    val note: String = "",
    val allocation: MoneyNotePaymentAllocation = MoneyNotePaymentAllocation.BOTH
)

private fun moneyNotesCurrency(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(paise / 100.0)

private fun MoneyNoteLoanType.label() = when (this) {
    MoneyNoteLoanType.GOLD_LOAN -> "Gold Loan"
    MoneyNoteLoanType.LOCAL_LOAN -> "Local Loan"
    MoneyNoteLoanType.FAMILY_FRIENDS -> "Family / Friends"
    MoneyNoteLoanType.OTHER -> "Other"
}

private fun MoneyNoteLoanType.icon() = when (this) {
    MoneyNoteLoanType.GOLD_LOAN -> Icons.Default.AccountBalance
    MoneyNoteLoanType.LOCAL_LOAN -> Icons.Default.AccountBalance
    MoneyNoteLoanType.FAMILY_FRIENDS -> Icons.Default.Groups
    MoneyNoteLoanType.OTHER -> Icons.Default.Description
}

private fun MoneyNoteStatus.label() = when (this) {
    MoneyNoteStatus.PENDING -> "Pending"
    MoneyNoteStatus.PARTIALLY_PAID -> "Partially paid"
    MoneyNoteStatus.SETTLED -> "Paid / settled"
}

@Composable
fun MoneyNotesScreen(
    notes: List<MoneyNote>,
    onNotesChanged: (List<MoneyNote>) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    var direction by remember { mutableStateOf(MoneyNoteDirection.I_NEED_TO_PAY) }
    var filter by remember { mutableStateOf("All") }
    var addDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MoneyNote?>(null) }
    var detailTargetId by remember { mutableStateOf<String?>(null) }
    var paymentTargetId by remember { mutableStateOf<String?>(null) }
    var lastPayment by remember { mutableStateOf<Pair<String, MoneyNotePayment>?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L))
            today = LocalDate.now()
        }
    }

    val payTotal = notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_PAY }.sumOf { it.outstandingPaise }
    val collectTotal = notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_COLLECT }.sumOf { it.outstandingPaise }
    val selectedNote = notes.firstOrNull { it.id == detailTargetId }
    val paymentNote = notes.firstOrNull { it.id == paymentTargetId }
    val visible = when (filter) {
        "Pay" -> notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_PAY }
        "Collect" -> notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_COLLECT }
        "History" -> notes.filter { it.payments.isNotEmpty() || it.status == MoneyNoteStatus.SETTLED }
        else -> notes
    }.sortedByDescending { it.date }

    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF7FAFF), Color(0xFFEAF8FF), Color(0xFFF7F3FF)))).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = NotesInk) }
            Column(Modifier.weight(1f)) {
                Text("Money Notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = NotesInk)
                Text("Borrowed and lent money, clearly tracked", style = MaterialTheme.typography.bodySmall, color = NotesMuted)
            }
            IconButton(onClick = { addDialog = true }, modifier = Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(NotesPurple, NotesBlue)))) {
                Icon(Icons.Default.Add, "Add money note", tint = Color.White)
            }
        }

        if (lastPayment != null) {
            val (noteId, payment) = lastPayment!!
            val note = notes.firstOrNull { it.id == noteId }
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16B66A), modifier = Modifier.size(58.dp))
                    Text("Payment Successful!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF16854D))
                    Text("${moneyNotesCurrency(payment.amountPaise)} recorded on ${payment.date}", color = NotesInk)
                    HorizontalDivider(color = NotesLine)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Updated balance", color = NotesMuted)
                        Text(moneyNotesCurrency(note?.outstandingPaise ?: 0L), color = NotesBlue, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { lastPayment = null; detailTargetId = noteId }, shape = RoundedCornerShape(16.dp)) { Text("View Details") }
                        Button(onClick = { lastPayment = null; filter = "History" }, colors = ButtonDefaults.buttonColors(containerColor = NotesBlue), shape = RoundedCornerShape(16.dp)) { Text("Done") }
                    }
                }
            }
        } else if (selectedNote != null) {
            MoneyNoteDetails(
                note = selectedNote,
                onBack = { detailTargetId = null },
                onEdit = { editTarget = selectedNote },
                onPayment = { paymentTargetId = selectedNote.id }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NotesSummaryTile("I Need to Pay", payTotal, "2 Active", listOf(NotesBlue, Color(0xFF65B7FF)), Modifier.weight(1f))
                NotesSummaryTile("I Need to Collect", collectTotal, "Money owed to you", listOf(Color(0xFF18A96D), NotesCyan), Modifier.weight(1f))
            }

            if (notes.any { it.interestRateBasisPoints > 0 }) {
                Surface(shape = RoundedCornerShape(15.dp), color = Color(0xFFE1F2FF)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = NotesBlue)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Interest updates automatically every day", color = NotesInk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                            Text("Balance calculated as of $today", color = NotesMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFE7EEF9)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("All", "Pay", "Collect", "History").forEach { item ->
                    val selected = filter == item
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(if (selected) Brush.linearGradient(listOf(NotesBlue, NotesPurple)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))).clickable { filter = item }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(item, color = if (selected) Color.White else NotesMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (filter == "History") "Payment History" else "My Money Notes", color = NotesInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { addDialog = true }) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Text(" Add note") }
            }

            if (visible.isEmpty()) {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Person, null, tint = NotesBlue, modifier = Modifier.size(36.dp))
                        Text("No money notes yet", color = NotesInk, fontWeight = FontWeight.Bold)
                        Text("Add a loan or money you are waiting to collect.", color = NotesMuted)
                        Button(onClick = { addDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = NotesBlue)) { Text("Add Money Note") }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(visible, key = { it.id }) { note ->
                        MoneyNoteCard(note, onClick = { detailTargetId = note.id }, onPayment = { paymentTargetId = note.id })
                    }
                }
            }
        }
    }

    if (addDialog) MoneyNoteEditorDialog(
        initial = null,
        onDismiss = { addDialog = false },
        onSave = { note -> onNotesChanged(notes + note); direction = note.direction; filter = if (note.direction == MoneyNoteDirection.I_NEED_TO_PAY) "Pay" else "Collect"; addDialog = false }
    )
    editTarget?.let { target ->
        MoneyNoteEditorDialog(
            initial = target,
            onDismiss = { editTarget = null },
            onSave = { updated -> onNotesChanged(notes.map { if (it.id == target.id) updated else it }); editTarget = null }
        )
    }
    paymentNote?.let { target ->
        RecordMoneyNotePaymentDialog(target, onDismiss = { paymentTargetId = null }) { payment ->
            onNotesChanged(notes.map { old ->
                if (old.id == target.id) old.copy(
                    paidPaise = old.paidPaise + payment.amountPaise,
                    payments = old.payments + payment
                ) else old
            })
            paymentTargetId = null
            lastPayment = target.id to payment
        }
    }
}

@Composable
private fun NotesSummaryTile(title: String, amount: Long, subtitle: String, colors: List<Color>, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(23.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
                Icon(if (title.contains("Pay")) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = Color.White)
            }
            Text(title, color = NotesInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(moneyNotesCurrency(amount), color = NotesBlue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = NotesMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MoneyNoteCard(note: MoneyNote, onClick: () -> Unit, onPayment: () -> Unit) {
    val owes = note.direction == MoneyNoteDirection.I_NEED_TO_PAY
    val accent = if (owes) NotesBlue else Color(0xFF168D69)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(if (owes) listOf(Color(0xFFD9ECFF), Color(0xFFE9DFFF)) else listOf(NotesMint, Color(0xFFD8F8E9))), contentAlignment = Alignment.Center) {
                    Icon(note.loanType.icon(), null, tint = accent, modifier = Modifier.size(25.dp))
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(note.personName, color = NotesInk, fontWeight = FontWeight.Bold)
                    Text(note.loanType.label() + " · " + note.date, color = NotesMuted, style = MaterialTheme.typography.labelSmall)
                    Text(note.interestRateBasisPoints.takeIf { it > 0 }?.let { "${it / 100.0}% per month" } ?: "No interest", color = NotesMuted, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(moneyNotesCurrency(note.outstandingPaise), color = accent, fontWeight = FontWeight.ExtraBold)
                    Surface(color = when (note.status) { MoneyNoteStatus.PENDING -> Color(0xFFFFF1D7); MoneyNoteStatus.PARTIALLY_PAID -> Color(0xFFE4F2FF); MoneyNoteStatus.SETTLED -> Color(0xFFE1F8EA) }, shape = RoundedCornerShape(20.dp)) {
                        Text(note.status.label(), color = accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            if (note.description.isNotBlank()) Text(note.description, color = NotesMuted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Paid: ${moneyNotesCurrency(note.payments.sumOf { it.amountPaise }.coerceAtMost(note.principalPaise))}", color = NotesMuted, style = MaterialTheme.typography.labelSmall)
                if (note.outstandingPaise > 0L) TextButton(onClick = onPayment) { Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp)); Text(" Record payment") }
                else Text("Settled", color = Color(0xFF168D69), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MoneyNoteDetails(note: MoneyNote, onBack: () -> Unit, onEdit: () -> Unit, onPayment: () -> Unit) {
    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = NotesInk) }
            Text("Money Note Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NotesInk)
        }
        Card(shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(NotesPurple, NotesCyan))), contentAlignment = Alignment.Center) {
                        Icon(note.loanType.icon(), null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(note.loanType.label(), color = NotesInk, fontWeight = FontWeight.Bold)
                        Text(note.personName, color = NotesMuted)
                        Text(note.status.label(), color = NotesBlue, style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalDivider(color = NotesLine)
                Text("Total Due", color = NotesMuted)
                Text(moneyNotesCurrency(note.outstandingPaise), color = NotesBlue, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceMiniCard("Principal", moneyNotesCurrency(note.balance.principalPaise), Modifier.weight(1f))
                    BalanceMiniCard("Interest", moneyNotesCurrency(note.balance.unpaidInterestPaise), Modifier.weight(1f))
                    BalanceMiniCard("Paid", moneyNotesCurrency(note.payments.sumOf { it.amountPaise }), Modifier.weight(1f))
                }
                Text("Start date: ${note.date}", color = NotesMuted, style = MaterialTheme.typography.bodySmall)
                if (note.description.isNotBlank()) Text(note.description, color = NotesInk)
                if (note.interestRateBasisPoints > 0) {
                    Surface(color = Color(0xFFE3F4FF), shape = RoundedCornerShape(14.dp)) {
                        Text("Interest updates automatically each day at ${note.interestRateBasisPoints / 100.0}% per month.", color = NotesBlue, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(5.dp)); Text("Edit Note") }
            Button(onClick = onPayment, modifier = Modifier.weight(1f), enabled = note.outstandingPaise > 0L, colors = ButtonDefaults.buttonColors(containerColor = NotesBlue), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Payments, null); Spacer(Modifier.width(5.dp)); Text("Record Payment") }
        }
        Text("Payment History", color = NotesInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (note.payments.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("No payments yet", modifier = Modifier.padding(18.dp), color = NotesMuted)
            }
        } else {
            note.payments.sortedByDescending { it.date }.forEach { payment ->
                Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A765))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(payment.note.ifBlank { payment.allocation.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } }, color = NotesInk, fontWeight = FontWeight.SemiBold)
                            Text(payment.date, color = NotesMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("-${moneyNotesCurrency(payment.amountPaise)}", color = NotesBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceMiniCard(title: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(15.dp), color = NotesCanvas) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = NotesMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, color = NotesInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MoneyNoteEditorDialog(initial: MoneyNote?, onDismiss: () -> Unit, onSave: (MoneyNote) -> Unit) {
    var direction by remember(initial?.id) { mutableStateOf(initial?.direction ?: MoneyNoteDirection.I_NEED_TO_PAY) }
    var type by remember(initial?.id) { mutableStateOf(initial?.loanType ?: MoneyNoteLoanType.GOLD_LOAN) }
    var person by remember(initial?.id) { mutableStateOf(initial?.personName.orEmpty()) }
    var amount by remember(initial?.id) { mutableStateOf(initial?.principalPaise?.let { (it / 100.0).toString() }.orEmpty()) }
    var date by remember(initial?.id) { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var monthlyRate by remember(initial?.id) { mutableStateOf(initial?.interestRateBasisPoints?.takeIf { it > 0 }?.let { (it / 100.0).toString() }.orEmpty()) }
    var error by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Money Note" else "Edit Money Note", color = NotesInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Money direction", color = NotesMuted, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MoneyNoteDirection.I_NEED_TO_PAY to "I Need to Pay", MoneyNoteDirection.I_NEED_TO_COLLECT to "I Need to Collect").forEach { (value, label) ->
                        val selected = direction == value
                        FilterChip(selected, { direction = value }, label = { Text(label) })
                    }
                }
                Text("Loan type", color = NotesMuted, style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MoneyNoteLoanType.GOLD_LOAN, MoneyNoteLoanType.LOCAL_LOAN, MoneyNoteLoanType.FAMILY_FRIENDS, MoneyNoteLoanType.OTHER).chunked(2).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            rowTypes.forEach { option ->
                                val selected = type == option
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { type = option },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) Color(0xFFE5F0FF) else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) NotesBlue else NotesLine)
                                ) {
                                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(option.icon(), null, tint = if (selected) NotesBlue else NotesMuted)
                                        Text(option.label(), color = if (selected) NotesBlue else NotesInk, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(person, { person = it; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Person / Lender *") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Principal Amount (₹) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(monthlyRate, { monthlyRate = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Interest Rate (% per month)") }, placeholder = { Text("0 for no interest, e.g. 1.50") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(date, { date = it; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Start Date (YYYY-MM-DD) *") }, singleLine = true)
                OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Remarks (optional)") })
                Text("Interest is calculated daily on the remaining principal. Payments and history are saved on this device.", color = NotesMuted, style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val paise = runCatching { amount.toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
                val validDate = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
                val rateBps = if (monthlyRate.isBlank()) 0 else runCatching { monthlyRate.toBigDecimal().movePointRight(2).intValueExact() }.getOrNull()
                when {
                    person.isBlank() -> error = "Enter the person's name."
                    paise == null || paise <= 0L -> error = "Enter a valid amount greater than zero."
                    !validDate -> error = "Enter a valid date (YYYY-MM-DD)."
                    rateBps == null || rateBps < 0 || rateBps > 100_000 -> error = "Enter a valid monthly interest rate."
                    initial != null && paise < initial.payments.sumOf { it.amountPaise } && initial.payments.isNotEmpty() -> error = "Principal cannot be less than payments already recorded."
                    else -> onSave(
                        MoneyNote(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            direction = direction,
                            personName = person.trim(),
                            principalPaise = paise,
                            date = date,
                            description = description.trim(),
                            paidPaise = initial?.paidPaise ?: 0L,
                            payments = initial?.payments ?: emptyList(),
                            interestRateBasisPoints = rateBps,
                            loanType = type
                        )
                    )
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = NotesBlue), shape = RoundedCornerShape(14.dp)) { Text(if (initial == null) "Save Money Note" else "Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun RecordMoneyNotePaymentDialog(note: MoneyNote, onDismiss: () -> Unit, onSave: (MoneyNotePayment) -> Unit) {
    var amount by remember(note.id) { mutableStateOf("") }
    var date by remember(note.id) { mutableStateOf(LocalDate.now().toString()) }
    var remarks by remember(note.id) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var allocation by remember(note.id) { mutableStateOf(MoneyNotePaymentAllocation.BOTH) }
    val liveBalance = note.balance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment", color = NotesInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = NotesCanvas) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(note.personName, color = NotesInk, fontWeight = FontWeight.Bold)
                        Text("Total due: ${moneyNotesCurrency(note.outstandingPaise)}", color = NotesBlue, fontWeight = FontWeight.ExtraBold)
                        Text("Principal ${moneyNotesCurrency(liveBalance.principalPaise)} · Interest ${moneyNotesCurrency(liveBalance.unpaidInterestPaise)}", color = NotesMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (note.interestRateBasisPoints > 0) {
                    Text("Payment Type", color = NotesInk, fontWeight = FontWeight.SemiBold)
                    listOf(
                        MoneyNotePaymentAllocation.INTEREST_ONLY to "Interest Only",
                        MoneyNotePaymentAllocation.PRINCIPAL_ONLY to "Principal Only",
                        MoneyNotePaymentAllocation.BOTH to "Both (Interest + Principal)"
                    ).forEach { (value, label) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { allocation = value },
                            shape = RoundedCornerShape(13.dp),
                            color = if (allocation == value) Color(0xFFE7F2FF) else Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (allocation == value) NotesBlue else NotesLine)
                        ) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = allocation == value, onClick = { allocation = value })
                                Text(label, color = NotesInk, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit); error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount (₹) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(date, { date = it; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Payment Date (YYYY-MM-DD) *") }, singleLine = true)
                OutlinedTextField(remarks, { remarks = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Remarks (optional)") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val paise = amount.toLongOrNull()?.let { runCatching { Math.multiplyExact(it, 100L) }.getOrNull() }
                val paymentDate = runCatching { LocalDate.parse(date) }.getOrNull()
                val balanceAtPaymentDate = paymentDate?.let { calculateMoneyNoteBalance(note, it) }
                when {
                    paise == null || paise <= 0L -> error = "Enter a payment greater than zero."
                    paymentDate == null -> error = "Enter a valid payment date."
                    paymentDate.isAfter(LocalDate.now()) -> error = "Payment date cannot be in the future."
                    paymentDate.isBefore(LocalDate.parse(note.date)) -> error = "Payment date cannot be before the loan start date."
                    balanceAtPaymentDate == null || paise > balanceAtPaymentDate.totalPaise -> error = "Payment cannot exceed the outstanding amount on that date."
                    allocation == MoneyNotePaymentAllocation.INTEREST_ONLY && paise > balanceAtPaymentDate.unpaidInterestPaise -> error = "Payment cannot exceed interest accrued by that date."
                    allocation == MoneyNotePaymentAllocation.PRINCIPAL_ONLY && paise > balanceAtPaymentDate.principalPaise -> error = "Payment cannot exceed remaining principal."
                    else -> onSave(MoneyNotePayment(paise, date, remarks.trim(), allocation))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = NotesBlue), shape = RoundedCornerShape(14.dp)) { Text("Record Payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}
