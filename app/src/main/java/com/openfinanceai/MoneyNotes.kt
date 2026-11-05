package com.openfinanceai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
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

private val MnBlue = Color(0xFF087BEA)
private val MnCyan = Color(0xFF20B8D5)
private val MnPurple = Color(0xFF9C5BEB)
private val MnInk = Color(0xFF17223B)
private val MnMuted = Color(0xFF78849A)
private val MnLine = Color(0xFFE2EAF4)
private val MnPaleBlue = Color(0xFFEAF5FF)
private val MnPaleGreen = Color(0xFFE9F9F0)
private val MnPalePink = Color(0xFFFFEAF6)
private val MnPalePurple = Color(0xFFF2EAFE)

enum class MoneyNoteLoanType(val label: String) {
    GOLD_LOAN("Gold Loan"), LOCAL_LOAN("Local Loan"), FAMILY_FRIENDS("Family / Friends"), OTHER("Other")
}

/** Personal borrowing/lending note. Amounts are stored in paise. */
data class MoneyNote(
    val id: String,
    val direction: MoneyNoteDirection,
    val personName: String,
    val principalPaise: Long,
    val date: String,
    val description: String = "",
    val paidPaise: Long = 0L,
    val payments: List<MoneyNotePayment> = emptyList(),
    /** Annual rate in basis points: 150 means 1.50% per year. Zero means no interest. */
    val interestRateBasisPoints: Int = 0,
    val loanType: MoneyNoteLoanType = MoneyNoteLoanType.OTHER
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
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { minimumFractionDigits = 0; maximumFractionDigits = 2 }.format(paise / 100.0)

private fun MoneyNoteLoanType.icon() = when (this) {
    MoneyNoteLoanType.GOLD_LOAN -> Icons.Default.Savings
    MoneyNoteLoanType.LOCAL_LOAN -> Icons.Default.AccountBalance
    MoneyNoteLoanType.FAMILY_FRIENDS -> Icons.Default.People
    MoneyNoteLoanType.OTHER -> Icons.Default.ReceiptLong
}

@Composable
fun MoneyNotesScreen(
    notes: List<MoneyNote>,
    onNotesChanged: (List<MoneyNote>) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    var filter by remember { mutableStateOf("All") }
    var addDialog by remember { mutableStateOf(false) }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<MoneyNote?>(null) }
    var paymentTarget by remember { mutableStateOf<MoneyNote?>(null) }
    var successPayment by remember { mutableStateOf<Pair<MoneyNote, MoneyNotePayment>?>(null) }
    val selectedNote = notes.firstOrNull { it.id == selectedNoteId }
    val payTotal = notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_PAY }.sumOf { it.outstandingPaise }
    val collectTotal = notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_COLLECT }.sumOf { it.outstandingPaise }
    val visible = when (filter) {
        "Pay" -> notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_PAY && it.status != MoneyNoteStatus.SETTLED }
        "Collect" -> notes.filter { it.direction == MoneyNoteDirection.I_NEED_TO_COLLECT && it.status != MoneyNoteStatus.SETTLED }
        "History" -> notes.filter { it.payments.isNotEmpty() || it.status == MoneyNoteStatus.SETTLED }
        else -> notes
    }.sortedByDescending { it.date }

    Column(
        modifier.fillMaxSize().background(appPageBrush()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = MnInk) }
            Column(Modifier.weight(1f)) {
                Text("Money Notes", style = MaterialTheme.typography.headlineSmall, color = MnInk, fontWeight = FontWeight.ExtraBold)
                Text("Borrowed and lent money, all in one place", style = MaterialTheme.typography.bodySmall, color = MnMuted)
            }
            IconButton(onClick = { addDialog = true }) {
                Icon(Icons.Default.Payments, "Add money note", tint = MnBlue)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyNotesSummary("I Need to Pay", payTotal, "${notes.count { it.direction == MoneyNoteDirection.I_NEED_TO_PAY && it.status != MoneyNoteStatus.SETTLED }} Active", MnPaleBlue, MnBlue, Modifier.weight(1f), Icons.Default.ArrowUpward)
            MoneyNotesSummary("I Need to Collect", collectTotal, "${notes.count { it.direction == MoneyNoteDirection.I_NEED_TO_COLLECT && it.status != MoneyNoteStatus.SETTLED }} Active", MnPaleGreen, Color(0xFF169B69), Modifier.weight(1f), Icons.Default.ArrowDownward)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (AppBlueGlass) Color(0xBFFFFFFF) else Color.White.copy(alpha = .88f)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("All", "Pay", "Collect", "History").forEach { item ->
                val active = filter == item
                Surface(
                    onClick = { filter = item },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) MnBlue else Color.Transparent
                ) {
                    Text(item, modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (active) Color.White else MnMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (filter == "History") "Payment History" else "My Money Notes", color = MnInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${visible.size} records", color = MnMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (visible.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), color = appGlassSurface(), shape = RoundedCornerShape(24.dp), border = if (AppBlueGlass) androidx.compose.foundation.BorderStroke(1.dp, appGlassBorder()) else null, shadowElevation = if (AppBlueGlass) 7.dp else 3.dp) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = MnPaleBlue, shape = CircleShape) { Icon(Icons.Default.Wallet, null, tint = MnBlue, modifier = Modifier.padding(16.dp).size(30.dp)) }
                    Text(if (filter == "History") "No payment history yet" else "No money notes yet", color = MnInk, fontWeight = FontWeight.Bold)
                    Text("Add a note to track a loan, repayment, or money you need to collect.", color = MnMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button(onClick = { addDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MnBlue)) { Text("Add Money Note") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(visible, key = { it.id }) { note ->
                    MoneyNoteCardV2(note, onClick = { selectedNoteId = note.id }, onPayment = { paymentTarget = note })
                }
                item {
                    Surface(Modifier.fillMaxWidth(), color = if (AppBlueGlass) Color(0xBFFFFFFF) else Color.White.copy(alpha = .8f), shape = RoundedCornerShape(18.dp), border = if (AppBlueGlass) androidx.compose.foundation.BorderStroke(1.dp, appGlassBorder()) else null) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = MnCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Your notes and payment records stay on this device and are included in app backup.", color = MnMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (addDialog) AddMoneyNoteDialogV2(onDismiss = { addDialog = false }, onSave = { note ->
        onNotesChanged(notes + note); filter = if (note.direction == MoneyNoteDirection.I_NEED_TO_PAY) "Pay" else "Collect"; addDialog = false
    })
    editTarget?.let { target ->
        AddMoneyNoteDialogV2(existing = target, onDismiss = { editTarget = null }, onSave = { updated ->
            onNotesChanged(notes.map { if (it.id == target.id) updated.copy(id = target.id, payments = target.payments, paidPaise = target.paidPaise) else it })
            editTarget = null
        })
    }
    selectedNote?.let { note ->
        MoneyNoteDetailDialogV2(note, onDismiss = { selectedNoteId = null }, onEdit = { editTarget = note; selectedNoteId = null }, onPayment = { paymentTarget = note; selectedNoteId = null }, onDelete = {
            onNotesChanged(notes.filterNot { it.id == note.id }); selectedNoteId = null
        })
    }
    paymentTarget?.let { target ->
        RecordMoneyNotePaymentDialogV2(target, onDismiss = { paymentTarget = null }, onSave = { payment ->
            val updated = target.copy(paidPaise = target.paidPaise + payment.amountPaise, payments = target.payments + payment)
            onNotesChanged(notes.map { if (it.id == target.id) updated else it })
            paymentTarget = null
            successPayment = updated to payment
        })
    }
    successPayment?.let { (updated, payment) ->
        PaymentSuccessDialogV2(updated, payment, onDismiss = { successPayment = null }, onDetails = {
            selectedNoteId = updated.id; successPayment = null
        })
    }
}

@Composable
private fun MoneyNotesSummary(title: String, amount: Long, caption: String, bg: Color, accent: Color, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(modifier, color = if (AppBlueGlass) appGlassSurface() else bg, shape = RoundedCornerShape(22.dp), border = if (AppBlueGlass) androidx.compose.foundation.BorderStroke(1.dp, appGlassBorder()) else null, shadowElevation = if (AppBlueGlass) 7.dp else 2.dp) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = if (AppBlueGlass) Color(0xBFFFFFFF) else Color.White.copy(alpha = .8f), shape = CircleShape) { Icon(icon, null, tint = accent, modifier = Modifier.padding(7.dp).size(17.dp)) }
                Text(title, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(moneyNotesCurrency(amount), color = MnInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(caption, color = accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MoneyNoteCardV2(note: MoneyNote, onClick: () -> Unit, onPayment: () -> Unit) {
    val owes = note.direction == MoneyNoteDirection.I_NEED_TO_PAY
    val accent = if (owes) MnBlue else Color(0xFF169B69)
    val statusColor = when (note.status) { MoneyNoteStatus.PENDING -> Color(0xFFB7791F); MoneyNoteStatus.PARTIALLY_PAID -> MnPurple; MoneyNoteStatus.SETTLED -> Color(0xFF15965F) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = appGlassSurface(), shape = RoundedCornerShape(20.dp), border = if (AppBlueGlass) androidx.compose.foundation.BorderStroke(1.dp, appGlassBorder()) else null, shadowElevation = if (AppBlueGlass) 7.dp else 3.dp) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = when (note.loanType) { MoneyNoteLoanType.GOLD_LOAN -> Color(0xFFFFF0C7); MoneyNoteLoanType.LOCAL_LOAN -> MnPalePurple; MoneyNoteLoanType.FAMILY_FRIENDS -> MnPaleGreen; MoneyNoteLoanType.OTHER -> MnPaleBlue }, shape = RoundedCornerShape(14.dp), border = if (AppBlueGlass) androidx.compose.foundation.BorderStroke(1.dp, appGlassBorder()) else null) {
                    Icon(note.loanType.icon(), null, tint = accent, modifier = Modifier.padding(11.dp).size(23.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(note.loanType.label, color = MnInk, fontWeight = FontWeight.Bold)
                    Text(note.personName, color = MnMuted, style = MaterialTheme.typography.bodySmall)
                    Text("Started: ${note.date}", color = MnMuted, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(moneyNotesCurrency(note.outstandingPaise), color = accent, fontWeight = FontWeight.ExtraBold)
                    Surface(color = statusColor.copy(alpha = .12f), shape = RoundedCornerShape(9.dp)) {
                        Text(note.status.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (note.interestRateBasisPoints > 0) Text("${note.interestRateBasisPoints / 100.0}% per year · interest updates daily", color = MnMuted, style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("View details", color = MnBlue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (note.outstandingPaise > 0L) {
                    TextButton(onClick = onPayment, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Record Payment", color = MnBlue) }
                }
            }
        }
    }
}

@Composable
private fun MoneyNoteDetailDialogV2(note: MoneyNote, onDismiss: () -> Unit, onEdit: () -> Unit, onPayment: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val balance = note.balance
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (AppBlueGlass) appGlassSurface() else Color(0xFFF8FBFF),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MnPaleBlue, shape = RoundedCornerShape(12.dp)) { Icon(note.loanType.icon(), null, tint = MnBlue, modifier = Modifier.padding(10.dp)) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(note.loanType.label, color = MnInk, fontWeight = FontWeight.Bold)
                    Text(note.personName, color = MnMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete money note", tint = Color(0xFFD9485F)) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = MnMuted) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = appGlassSurface(), shape = RoundedCornerShape(18.dp), border = if (AppBlueGlass) androidx.compose.foundation.BorderStroke(1.dp, appGlassBorder()) else null) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Due", color = MnMuted)
                        Text(moneyNotesCurrency(note.outstandingPaise), color = MnBlue, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DetailMetric("Principal", moneyNotesCurrency(balance.principalPaise), Modifier.weight(1f))
                            DetailMetric("Interest", moneyNotesCurrency(balance.unpaidInterestPaise), Modifier.weight(1f))
                            DetailMetric("Paid", moneyNotesCurrency(note.payments.sumOf { it.amountPaise }), Modifier.weight(1f))
                        }
                    }
                }
                Text("Payment History", color = MnInk, fontWeight = FontWeight.Bold)
                if (note.payments.isEmpty()) Text("No payments recorded yet.", color = MnMuted, style = MaterialTheme.typography.bodySmall)
                else note.payments.sortedByDescending { it.date }.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MnPaleGreen, shape = CircleShape) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF15965F), modifier = Modifier.padding(7.dp).size(17.dp)) }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(p.note.ifBlank { p.allocation.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } }, color = MnInk, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(p.date, color = MnMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("-${moneyNotesCurrency(p.amountPaise)}", color = Color(0xFF15965F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (note.description.isNotBlank()) Text(note.description, color = MnMuted, style = MaterialTheme.typography.bodySmall)
                if (confirmDelete) Text("Delete this money note and its payment history?", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(onClick = onDelete) { Text("Confirm delete", color = MaterialTheme.colorScheme.error) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEdit) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text("Edit") }
                    if (note.outstandingPaise > 0) Button(onClick = onPayment, colors = ButtonDefaults.buttonColors(containerColor = MnBlue)) { Text("Record Payment") }
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (confirmDelete) confirmDelete = false else onDismiss() }) { Text(if (confirmDelete) "Cancel" else "Close") } }
    )
}

@Composable
private fun DetailMetric(label: String, amount: String, modifier: Modifier) {
    Surface(modifier.heightIn(min = 68.dp), color = MnPaleBlue.copy(alpha = .65f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = MnMuted, style = MaterialTheme.typography.labelSmall)
            Text(amount, color = MnInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AddMoneyNoteDialogV2(existing: MoneyNote? = null, onDismiss: () -> Unit, onSave: (MoneyNote) -> Unit) {
    var direction by remember { mutableStateOf(existing?.direction ?: MoneyNoteDirection.I_NEED_TO_PAY) }
    var type by remember { mutableStateOf(existing?.loanType ?: MoneyNoteLoanType.GOLD_LOAN) }
    var person by remember { mutableStateOf(existing?.personName.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.principalPaise?.let { (it / 100.0).toString() }.orEmpty()) }
    var rate by remember { mutableStateOf(existing?.interestRateBasisPoints?.takeIf { it > 0 }?.let { (it / 100.0).toString() }.orEmpty()) }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF8FBFF),
        title = { Text(if (existing == null) "Add Money Note" else "Edit Money Note", color = MnInk, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MoneyNoteDirection.I_NEED_TO_PAY to "I Need to Pay", MoneyNoteDirection.I_NEED_TO_COLLECT to "I Need to Collect").forEach { (value, label) ->
                            FilterChip(selected = direction == value, onClick = { direction = value }, label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("Loan Type", color = MnInk, fontWeight = FontWeight.Bold)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        MoneyNoteLoanType.entries.chunked(2).forEach { rowTypes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                rowTypes.forEach { item ->
                                    val selected = type == item
                                    Surface(onClick = { type = item }, modifier = Modifier.weight(1f), color = if (selected) MnPaleBlue else appGlassSurface(), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MnBlue else if (AppBlueGlass) appGlassBorder() else MnLine)) {
                                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(item.icon(), null, tint = if (selected) MnBlue else MnMuted, modifier = Modifier.size(23.dp))
                                            Text(item.label, color = if (selected) MnBlue else MnInk, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                item { OutlinedTextField(person, { person = it; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text(if (direction == MoneyNoteDirection.I_NEED_TO_PAY) "Person / Lender *" else "Person / Borrower *") }, singleLine = true) }
                item { OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Principal Amount (₹) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
                item {
                    OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Interest Rate (% per year)") }, placeholder = { Text("0 for no interest") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Text("Interest accrues daily using the annual rate and remaining principal.", color = MnMuted, style = MaterialTheme.typography.labelSmall)
                }
                item { MoneyNoteDateField("Start Date", date) { date = it; error = "" } }
                item { OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description (optional)") }, minLines = 2) }
                if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val paise = runCatching { amount.toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
                val validDate = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
                val rateBps = runCatching { if (rate.isBlank()) 0 else rate.toBigDecimal().movePointRight(2).intValueExact() }.getOrNull()
                when {
                    person.isBlank() -> error = "Enter the person's name."
                    paise == null || paise <= 0L -> error = "Enter a valid amount greater than zero."
                    !validDate -> error = "Enter a valid date (YYYY-MM-DD)."
                    LocalDate.parse(date).isAfter(LocalDate.now()) -> error = "Start date cannot be in the future."
                    rateBps == null || rateBps < 0 || rateBps > 100_000 -> error = "Enter a valid annual interest rate."
                    else -> onSave(MoneyNote(existing?.id ?: UUID.randomUUID().toString(), direction, person.trim(), paise, date, description.trim(), existing?.paidPaise ?: 0L, existing?.payments ?: emptyList(), rateBps, type))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = MnBlue)) { Text(if (existing == null) "Save Money Note" else "Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MnBlue) } }
    )
}

@Composable
private fun RecordMoneyNotePaymentDialogV2(note: MoneyNote, onDismiss: () -> Unit, onSave: (MoneyNotePayment) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var remark by remember { mutableStateOf("") }
    var allocation by remember { mutableStateOf(MoneyNotePaymentAllocation.BOTH) }
    var error by remember { mutableStateOf("") }
    val balance = note.balance
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF8FBFF),
        title = { Text("Record Payment", color = MnInk, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(color = appGlassSurface(), shape = RoundedCornerShape(16.dp), border = if (AppBlueGlass) androidx.compose.foundation.BorderStroke(1.dp, appGlassBorder()) else null) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(note.loanType.icon(), null, tint = MnBlue, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(note.loanType.label, color = MnInk, fontWeight = FontWeight.Bold)
                            Text(note.personName, color = MnMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(moneyNotesCurrency(note.outstandingPaise), color = MnBlue, fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (note.interestRateBasisPoints > 0) {
                    Text("Payment Type", color = MnInk, fontWeight = FontWeight.Bold)
                    listOf(MoneyNotePaymentAllocation.INTEREST_ONLY to "Interest Only", MoneyNotePaymentAllocation.PRINCIPAL_ONLY to "Principal Only", MoneyNotePaymentAllocation.BOTH to "Both").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = allocation == value, onClick = { allocation = value }, colors = RadioButtonDefaults.colors(selectedColor = MnBlue))
                            Text(label, color = MnInk)
                        }
                    }
                }
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount (₹) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                MoneyNoteDateField("Payment Date", date) { date = it; error = "" }
                OutlinedTextField(remark, { remark = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Remarks (optional)") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val paise = runCatching { amount.toBigDecimal().movePointRight(2).longValueExact() }.getOrNull()
                val paymentDate = runCatching { LocalDate.parse(date) }.getOrNull()
                val balanceAtDate = paymentDate?.let { calculateMoneyNoteBalance(note, it) }
                when {
                    paise == null || paise <= 0L -> error = "Enter a valid payment amount."
                    paymentDate == null -> error = "Enter a valid date (YYYY-MM-DD)."
                    paymentDate.isAfter(LocalDate.now()) -> error = "Payment date cannot be in the future."
                    paymentDate.isBefore(LocalDate.parse(note.date)) -> error = "Payment date cannot be before the loan start date."
                    balanceAtDate == null || paise > balanceAtDate.totalPaise -> error = "Payment exceeds the outstanding amount on that date."
                    allocation == MoneyNotePaymentAllocation.INTEREST_ONLY && paise > balanceAtDate.unpaidInterestPaise -> error = "Payment exceeds accrued interest."
                    allocation == MoneyNotePaymentAllocation.PRINCIPAL_ONLY && paise > balanceAtDate.principalPaise -> error = "Payment exceeds remaining principal."
                    else -> onSave(MoneyNotePayment(paise, date, remark.trim(), allocation))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = MnBlue)) { Text("Record Payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MnBlue) } }
    )
}

@Composable
private fun PaymentSuccessDialogV2(note: MoneyNote, payment: MoneyNotePayment, onDismiss: () -> Unit, onDetails: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = appGlassSurface(),
        icon = { Surface(color = MnPaleGreen, shape = CircleShape) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A765), modifier = Modifier.padding(14.dp).size(38.dp)) } },
        title = { Text("Payment Successful!", color = Color(0xFF147443), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${moneyNotesCurrency(payment.amountPaise)} recorded on ${payment.date}", color = MnInk)
                Surface(color = MnPaleGreen, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Principal remaining", color = MnMuted); Text(moneyNotesCurrency(note.balance.principalPaise), color = MnInk, fontWeight = FontWeight.SemiBold) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Interest due", color = MnMuted); Text(moneyNotesCurrency(note.balance.unpaidInterestPaise), color = MnInk, fontWeight = FontWeight.SemiBold) }
                        HorizontalDivider(color = Color(0xFFC5EBD4))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total Due", color = MnBlue, fontWeight = FontWeight.Bold); Text(moneyNotesCurrency(note.outstandingPaise), color = MnBlue, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDetails, colors = ButtonDefaults.buttonColors(containerColor = MnBlue)) { Text("View Details") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done", color = MnBlue) } }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneyNoteDateField(label: String, date: String, onDateChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val initialMillis = remember(date) {
        runCatching { LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }
    OutlinedTextField(
        value = date,
        onValueChange = {},
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("$label (YYYY-MM-DD) *") },
        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = MnBlue) },
        trailingIcon = { IconButton(onClick = { showPicker = true }) { Icon(Icons.Default.CalendarMonth, "Choose date", tint = MnBlue) } },
        shape = RoundedCornerShape(14.dp),
        singleLine = true
    )
    if (showPicker) GlassCalendarDialog(initialDate = date, accent = MnBlue, onDismiss = { showPicker = false }, onSelect = { onDateChange(it); showPicker = false })
}
