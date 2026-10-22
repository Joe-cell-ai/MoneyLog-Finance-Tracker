@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.openfinanceai

import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val Green = Color(0xFF087F5B)
private val Mint = Color(0xFFE0F5EC)
private val IncomeGreen = Color(0xFF159B72)
private val ExpenseRed = Color(0xFFE85D68)
private val SavingsBlue = Color(0xFF2699D6)
private val Ink = Color(0xFF172A33)
private val Muted = Color(0xFF71818A)
private val Page = Color(0xFFF3F7F8)
private val Money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

private data class Transaction(val title: String, val category: String, val date: String, val amount: Long, val income: Boolean, val source: String = "", val notificationKey: String = "", val captureStatus: String = "", val paymentMethod: String = "Not set", val notes: String = "")
private data class Loan(val lender: String, val balance: Long, val emi: Long, val due: String, val annualRate: Double = 0.0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        EmiReminderReceiver.scheduleMonthly(this, "Fibe", 6200L, 5, 1005)
        EmiReminderReceiver.scheduleMonthly(this, "KreditBee", 4800L, 8, 1008)
        EmiReminderReceiver.scheduleMonthly(this, "Pocketly", 3200L, 10, 1010)
        setContent { FinanceApp() }
    }
}

@Composable
private fun FinanceApp() {
    var tab by remember { mutableStateOf("Home") }
    var selectedOverviewMonth by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var transactionForActions by remember { mutableStateOf<Transaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionTips by remember { mutableStateOf<Transaction?>(null) }
    var loanForActions by remember { mutableStateOf<Loan?>(null) }
    var loanToEdit by remember { mutableStateOf<Loan?>(null) }
    val context = LocalContext.current
    var transactions by remember {
        mutableStateOf(loadTransactions(context) ?: listOf(
            Transaction("Monthly salary", "Income", "Today", 65000, true),
            Transaction("Grocery shopping", "Food & groceries", "Today", 1840, false),
            Transaction("Electricity bill", "Bills", "Yesterday", 1250, false),
            Transaction("Fuel", "Transport", "Yesterday", 1800, false),
            Transaction("Coffee shop", "Food & drink", "20 Oct", 240, false)
        ).also { saveTransactions(context, it) })
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                loadTransactions(context ?: return)?.let { transactions = it }
            }
        }
        val filter = IntentFilter(PaymentNotificationListener.ACTION_TRANSACTION_CAPTURED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    var selectedLoan by remember { mutableStateOf<Loan?>(null) }
    var importMessage by remember { mutableStateOf("") }
    var importedPreview by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val csvText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read the selected file.")
                val parsed = parseTransactionCsv(csvText)
                val existingKeys = transactions.map { transactionKey(it) }.toSet()
                val unique = parsed.distinctBy { transactionKey(it) }
                    .filterNot { transactionKey(it) in existingKeys }
                importedPreview = unique
                importMessage = "Found ${parsed.size} valid row(s). ${parsed.size - unique.size} duplicate row(s) will be skipped."
            }.onFailure {
                importedPreview = emptyList()
                importMessage = it.message ?: "Could not import this CSV file."
            }
        }
    }
    var loans by remember {
        mutableStateOf(loadLoans(context) ?: listOf(
            Loan("Fibe", 31000, 6200, "5 Nov"),
            Loan("KreditBee", 24000, 4800, "8 Nov"),
            Loan("Pocketly", 12800, 3200, "10 Nov")
        ).also { saveLoans(context, it) })
    }
    val income = transactions.filter { it.income }.sumOf { it.amount }
    val expenses = transactions.filterNot { it.income }.sumOf { it.amount }
    val selectedMonthTransactions = transactions.filter { transactionBelongsToMonth(it, selectedOverviewMonth) }
    val selectedMonthIncome = selectedMonthTransactions.filter { it.income }.sumOf { it.amount }
    val selectedMonthExpenses = selectedMonthTransactions.filterNot { it.income }.sumOf { it.amount }

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Page, surface = Color.White, onSurface = Ink)) {
        Scaffold(
            containerColor = Page,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Green, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OPEN FINANCE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Text("Your money, clearly.", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { tab = "Settings" }) { Icon(Icons.Default.Settings, "Settings") }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf("Home", "Activity", "Reports", "Loans", "Coach").forEach { name ->
                        val icon = when (name) {
                            "Home" -> Icons.Default.Home
                            "Activity" -> Icons.Default.ReceiptLong
                            "Reports" -> Icons.Default.BarChart
                            "Loans" -> Icons.Default.AccountBalance
                            else -> Icons.Default.AutoAwesome
                        }
                        NavigationBarItem(
                            selected = tab == name,
                            onClick = { tab = name },
                            icon = { Icon(icon, name) },
                            label = { Text(name, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, indicatorColor = Mint)
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == "Home" || tab == "Activity") {
                    FloatingActionButton(onClick = { showAdd = true }, containerColor = Green, contentColor = Color.White) {
                        Icon(Icons.Default.Add, "Add transaction")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    "Home" -> HomeScreen(selectedMonthIncome, selectedMonthExpenses, transactions, selectedMonthTransactions, selectedOverviewMonth, { selectedOverviewMonth = it }, { tab = "Activity" }, { showAdd = true }, { tab = it }, { transactionForActions = it })
                    "Activity" -> ActivityScreen(transactions, { transactionForActions = it })
                    "Reports" -> ReportsScreen(selectedMonthIncome, selectedMonthExpenses, selectedMonthTransactions, selectedOverviewMonth, { selectedOverviewMonth = it })
                    "Loans" -> LoansScreen(loans, { tab = "Add loan" }, { loan -> selectedLoan = loan; tab = "Loan details" }, { loanForActions = it })
                    "Coach" -> CoachScreen(income, expenses, loans.sumOf { it.emi })
                    "Calendar" -> CalendarScreen(loans, transactions)
                    "Insights" -> InsightsScreen(income, expenses, loans.sumOf { it.balance })
                    "Credit health" -> CreditHealthScreen()
                    "Categories" -> CategoriesScreen()
                    "Backup & security" -> BackupSecurityScreen()
                    "CRED sync", "Live payment tracking" -> CredSyncScreen()
                    "Import transactions" -> ImportTransactionsScreen(importMessage, importedPreview.size, { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, {
                        transactions = (importedPreview + transactions).distinctBy { transactionKey(it) }
                        saveTransactions(context, transactions)
                        importMessage = "Imported ${importedPreview.size} transaction(s). Saved on this device."
                        importedPreview = emptyList()
                    })
                    "Appearance" -> AppearanceScreen()
                    "Loan details" -> selectedLoan?.let { loan -> LoanDetailsScreen(loan) { loanForActions = loan } } ?: loans.firstOrNull()?.let { loan -> LoanDetailsScreen(loan) { loanForActions = loan } } ?: EmptyLoansScreen { tab = "Add loan" }
                    "Add loan" -> AddLoanScreen { lender, principal, rate, emi ->
                        val newLoan = Loan(lender, principal, emi, "Set due date", rate)
                        loans = listOf(newLoan) + loans
                        saveLoans(context, loans)
                        tab = "Loans"
                    }
                    else -> SettingsScreen { tab = it }
                }
            }
            transactionForActions?.let { transaction ->
                AlertDialog(
                    onDismissRequest = { transactionForActions = null },
                    title = { Text(transaction.title) },
                    text = { Text("What would you like to do with this transaction?") },
                    confirmButton = { TextButton(onClick = { transactionToEdit = transaction; transactionForActions = null }) { Text("Edit") } },
                    dismissButton = { Row { TextButton(onClick = { transactionTips = transaction; transactionForActions = null }) { Text("Tips") }; TextButton(onClick = {
                        val index = transactions.indexOf(transaction)
                        if (index >= 0) { transactions = transactions.toMutableList().also { it.removeAt(index) }; saveTransactions(context, transactions) }
                        transactionForActions = null
                    }) { Text("Delete", color = ExpenseRed) }; TextButton(onClick = { transactionForActions = null }) { Text("Cancel") } } }
                )
            }
            transactionTips?.let { transaction ->
                AlertDialog(
                    onDismissRequest = { transactionTips = null },
                    title = { Text("MoneyLog suggestion") },
                    text = { Text(if (transaction.income) "Suggestion: confirm this income against your bank statement and keep recurring income dates consistent. Categorize it as salary, freelance income or another suitable source." else "Suggestion: review whether this expense is essential or recurring, confirm the amount against your statement, and use a consistent category to improve your monthly reports.") },
                    confirmButton = { TextButton(onClick = { transactionTips = null }) { Text("Got it") } }
                )
            }
            transactionToEdit?.let { transaction ->
                EditTransactionDialog(transaction, onDismiss = { transactionToEdit = null }) { edited ->
                    val index = transactions.indexOf(transaction)
                    if (index >= 0) { transactions = transactions.toMutableList().also { it[index] = edited }; saveTransactions(context, transactions) }
                    transactionToEdit = null
                }
            }
            loanForActions?.let { loan ->
                AlertDialog(
                    onDismissRequest = { loanForActions = null },
                    title = { Text(loan.lender) },
                    text = { Text("Edit this loan or delete it and its EMI tracking entry?") },
                    confirmButton = { TextButton(onClick = { loanToEdit = loan; loanForActions = null }) { Text("Edit") } },
                    dismissButton = { Row { TextButton(onClick = {
                        loans = loans.toMutableList().also { list -> val index = list.indexOf(loan); if (index >= 0) list.removeAt(index) }
                        saveLoans(context, loans)
                        loanForActions = null
                    }) { Text("Delete", color = ExpenseRed) }; TextButton(onClick = { loanForActions = null }) { Text("Cancel") } } }
                )
            }
            loanToEdit?.let { loan ->
                EditLoanDialog(loan, onDismiss = { loanToEdit = null }) { edited ->
                    loans = loans.toMutableList().also { list -> val index = list.indexOf(loan); if (index >= 0) list[index] = edited }
                    saveLoans(context, loans)
                    loanToEdit = null
                }
            }
            if (showAdd) {
                AddTransactionDialog(
                    onDismiss = { showAdd = false },
                    onSave = { title, amount, isIncome, category, date, paymentMethod, notes ->
                        transactions = listOf(Transaction(title, category, date, amount, isIncome, paymentMethod = paymentMethod, notes = notes)) + transactions
                        saveTransactions(context, transactions)
                        showAdd = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    income: Long,
    expenses: Long,
    transactions: List<Transaction>,
    monthTransactions: List<Transaction>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onAll: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onTransactionLongPress: (Transaction) -> Unit
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Text(selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH) + " " + selectedMonth.year, color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Text("Good morning 👋", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen("Calendar") }) { Icon(Icons.Default.CalendarMonth, null); Text(" Calendar") }
                OutlinedButton(onClick = { onOpen("Insights") }) { Icon(Icons.Default.Insights, null); Text(" Insights") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("TOTAL BALANCE", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(Money.format(income - expenses), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Across your tracked finances", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    HorizontalDivider(color = Color.White.copy(alpha = .25f))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("↓ INCOME", color = Color.White.copy(alpha = .8f))
                            Text(Money.format(income), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("↑ EXPENSES", color = Color(0xFFFFC1C6))
                            Text(Money.format(expenses), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("✦ SAVINGS", color = Color(0xFFB7E5FF))
                            Text(Money.format(income - expenses), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                QuickAction("Add expense", Icons.Default.Remove, Modifier.weight(1f), onAdd)
                QuickAction("Add income", Icons.Default.Add, Modifier.weight(1f), onAdd)
                QuickAction("AI coach", Icons.Default.AutoAwesome, Modifier.weight(1f)) { onOpen("Coach") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly overview", modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                TextButton(onClick = { showMonthPicker = true }) {
                    Text(selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + selectedMonth.year)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose month")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Spending in " + selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), color = Muted)
                    Text(Money.format(expenses), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    LinearProgressIndicator(
                        progress = { if (income > 0) (expenses.toFloat() / income).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Green,
                        trackColor = Mint
                    )
                    Text(if (income > 0) "${expenses * 100 / income}% of tracked income spent" else "Add income to see spending ratio", color = Muted, fontSize = 12.sp)
                    Text("Open monthly report →", Modifier.clickable { onOpen("Reports") }, color = Green)
                }
            }
        }
        item { SectionHeading("Recent transactions", "See all", onAll) }
        items((monthTransactions.ifEmpty { transactions }).take(4)) { transaction -> TransactionRow(transaction, onLongPress = { onTransactionLongPress(transaction) }) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.clickable { onOpen("Coach") }
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF5A62B5))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Your AI finance coach", fontWeight = FontWeight.Bold, color = Ink)
                        Text("Get a clearer view of your money habits.", fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { onOpen("Credit health") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Speed, null)
                Text(" Credit health")
            }
        }
    }

    if (showMonthPicker) {
        AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text("Choose month") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select a month to view", color = Muted, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(selectedMonth.year.toString(), color = Muted)
                        }
                        IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMonthChange(YearMonth.now()) }) { Text("This month") }
                        TextButton(onClick = { showMonthPicker = false }) { Text("Done") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMonthPicker = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Green)
            Text(label, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text(action, Modifier.clickable { onClick() }, color = Green, fontSize = 12.sp)
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, onLongPress: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).combinedClickable(onClick = {}, onLongClick = onLongPress).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(if (transaction.income) Mint else Page, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(if (transaction.income) Icons.Default.Payments else Icons.Default.ShoppingBag, null, tint = Green)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text("${transaction.category} · ${transaction.date}", color = Muted, fontSize = 11.sp)
        }
        Text((if (transaction.income) "+" else "−") + Money.format(transaction.amount), color = if (transaction.income) Green else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ActivityScreen(transactions: List<Transaction>, onTransactionLongPress: (Transaction) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    val visible = transactions.filter { filter == "All" || (filter == "Income" && it.income) || (filter == "Expense" && !it.income) }
    val grouped = visible.groupBy { it.date }
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Transactions", fontSize = 25.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Your tracked money activity", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("All", "Income", "Expense").forEach { choice ->
                    FilterChip(selected = filter == choice, onClick = { filter = choice }, label = { Text(choice) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Green, selectedLabelColor = Color.White))
                }
            }
        }
        if (visible.isEmpty()) item { Text("No transactions in this filter yet.", color = Muted, modifier = Modifier.padding(18.dp)) }
        grouped.forEach { (date, rows) ->
            item { Text(date, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp)) }
            items(rows) { transaction -> TransactionRow(transaction, onLongPress = { onTransactionLongPress(transaction) }) }
        }
    }
}

@Composable
private fun ReportsScreen(
    income: Long,
    expenses: Long,
    transactions: List<Transaction>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    val categories = transactions.filterNot { it.income }.groupBy { it.category }.map { entry -> entry.key to entry.value.sumOf { it.amount } }.sortedByDescending { it.second }
    val chartColors = listOf(Green, ExpenseRed, SavingsBlue, Color(0xFF9B6AD6), Color(0xFFE99B51), Color(0xFF4E9A8A))
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Monthly reports", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Income, spending and cash flow", color = Muted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), color = Ink, fontWeight = FontWeight.Bold)
                    Text(selectedMonth.year.toString(), color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, "Next month") }
            }
            Text("Showing transactions dated within the selected month. Older entries without a recognizable date are excluded.", color = Muted, fontSize = 11.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Income", Money.format(income), Modifier.weight(1f))
                MetricCard("Expenses", Money.format(expenses), Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Income vs expenses", fontWeight = FontWeight.Bold, color = Ink)
                    Text("Income  " + Money.format(income), color = Green)
                    LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth(), color = Green)
                    Text("Expenses  " + Money.format(expenses), color = Color(0xFFB87532))
                    LinearProgressIndicator(progress = { if (income > 0) (expenses.toFloat() / income).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFE99B51))
                    Text("Net cash flow: " + Money.format(income - expenses), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }
        if (categories.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spending by category", Modifier.fillMaxWidth(), color = Ink, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.Canvas(Modifier.size(178.dp)) {
                        val total = categories.sumOf { it.second }.coerceAtLeast(1L).toFloat()
                        var start = -90f
                        categories.forEachIndexed { index, entry ->
                            val sweep = entry.second.toFloat() / total * 360f
                            drawArc(
                                color = chartColors[index % chartColors.size],
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = size.minDimension * 0.19f, cap = StrokeCap.Butt)
                            )
                            start += sweep
                        }
                    }
                    Text(Money.format(categories.sumOf { it.second }), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { SectionHeading("Category breakdown", "Tracked spending") }
        items(categories) { (category, amount) ->
            val colorIndex = categories.indexOfFirst { it.first == category }.coerceAtLeast(0)
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(chartColors[colorIndex % chartColors.size], CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(category, Modifier.weight(1f), color = Ink)
                    Text(Money.format(amount), color = Ink, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { (amount.toFloat() / categories.sumOf { it.second }.coerceAtLeast(1L)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = chartColors[colorIndex % chartColors.size], trackColor = Mint)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted)
            Text(value, color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LoansScreen(loans: List<Loan>, onAddLoan: () -> Unit, onDetails: (Loan) -> Unit, onLoanLongPress: (Loan) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Text("Loans & EMIs", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Keep every repayment in view", color = Muted)
            Button(onClick = onAddLoan, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add loan") }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("TOTAL OUTSTANDING", color = Color.White.copy(alpha = .8f))
                    Text(Money.format(loans.sumOf { it.balance }), fontSize = 29.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Monthly EMIs · " + Money.format(loans.sumOf { it.emi }), color = Color.White)
                }
            }
        }
        if (loans.isEmpty()) item {
            InfoCard("No loans added yet", "Add a loan to track its outstanding balance, monthly EMI and next due date.", Icons.Default.AccountBalance)
        }
        items(loans) { loan ->
            Card(modifier = Modifier.combinedClickable(onClick = { onDetails(loan) }, onLongClick = { onLoanLongPress(loan) }), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, null, tint = Green)
                        Spacer(Modifier.width(10.dp))
                        Text(loan.lender, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                        Text(Money.format(loan.balance), color = Ink, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Mint)
                    Text("Monthly EMI", color = Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Money.format(loan.emi), Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Monthly", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Mint, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Outstanding balance", color = Muted, fontSize = 11.sp)
                            Text(Money.format(loan.balance), color = Ink, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Next due", color = Muted, fontSize = 11.sp)
                            Text(loan.due, color = Ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Repayment progress isn't shown because the original principal and payments made haven't been recorded.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        item { Text("Tap a loan to open details and repayment information.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun CoachScreen(income: Long, expenses: Long, emi: Long) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("AI finance coach", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Guidance based on the numbers you track", color = Muted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF5A62B5))
                    Text("Your money snapshot", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Income: ${Money.format(income)}\nExpenses: ${Money.format(expenses)}\nMonthly EMIs: ${Money.format(emi)}", color = Ink)
                    Text(
                        if (income > 0) "Tracked expenses are ${expenses * 100 / income}% of income. Review recurring costs and keep an emergency buffer before making extra loan payments."
                        else "Record your income and expenses for more useful guidance.",
                        color = Muted
                    )
                }
            }
        }
        item {
            OutlinedTextField(question, { question = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ask about saving, budget or loans") }, minLines = 2)
        }
        item {
            Button(onClick = {
                answer = if (income == 0L) {
                    "Start by recording take-home income, essential bills and loan EMIs."
                } else {
                    "After tracked expenses and EMIs, your estimated remaining amount is ${Money.format(income - expenses - emi)}. Review recurring categories, keep minimum payments current, and compare loan APRs and foreclosure charges before extra repayment."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Get guidance") }
        }
        if (answer.isNotBlank()) item {
            Text(answer, Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp), color = Ink)
        }
        item { Text("This starter coach uses local rules, not a live AI service or regulated financial advice.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun CalendarScreen(loans: List<Loan>, transactions: List<Transaction>) {
    val today = java.time.LocalDate.now()
    var visibleMonth by remember { mutableStateOf(java.time.YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value % 7
    val totalCells = ((firstDayOffset + visibleMonth.lengthOfMonth() + 6) / 7) * 7
    val cells = (0 until totalCells).map { index ->
        val day = index - firstDayOffset + 1
        if (day in 1..visibleMonth.lengthOfMonth()) day else null
    }
    val monthTransactions = transactions.filter { transactionBelongsToMonth(it, visibleMonth) }
    val selectedTransactions = transactions.filter { transactionDateOrNull(it) == selectedDate }
    val selectedIncome = selectedTransactions.filter { it.income }.sumOf { it.amount }
    val selectedExpenses = selectedTransactions.filterNot { it.income }.sumOf { it.amount }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Financial calendar", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Tap a date to review transactions and EMI reminders", color = Muted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1); selectedDate = visibleMonth.atDay(1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Green)
                        }
                        Text(visibleMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + visibleMonth.year,
                            Modifier.weight(1f), textAlign = TextAlign.Center, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1); selectedDate = visibleMonth.atDay(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Green)
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { name ->
                            Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                                Text(name, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                if (day == null) {
                                    Box(Modifier.weight(1f).height(48.dp))
                                } else {
                                    val date = visibleMonth.atDay(day)
                                    val dayTransactions = monthTransactions.filter { transactionDateOrNull(it) == date }
                                    val isSelected = selectedDate == date
                                    Column(
                                        Modifier.weight(1f).height(48.dp).padding(2.dp)
                                            .background(if (isSelected) Green else if (date == today) Mint else Page, RoundedCornerShape(9.dp))
                                            .clickable { selectedDate = date }
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(day.toString(), color = if (isSelected) Color.White else Ink,
                                            fontSize = 13.sp, fontWeight = if (isSelected || date == today) FontWeight.Bold else FontWeight.Normal)
                                        if (dayTransactions.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                if (dayTransactions.any { it.income }) Box(Modifier.size(4.dp).background(if (isSelected) Color.White else IncomeGreen, CircleShape))
                                                if (dayTransactions.any { !it.income }) Box(Modifier.size(4.dp).background(if (isSelected) Color.White else ExpenseRed, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(7.dp).background(IncomeGreen, CircleShape)); Text("Income", color = Muted, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(7.dp).background(ExpenseRed, CircleShape)); Text("Expense", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Income", Money.format(selectedIncome), Modifier.weight(1f))
                        MetricCard("Expenses", Money.format(selectedExpenses), Modifier.weight(1f))
                    }
                    Text("Net for day: " + Money.format(selectedIncome - selectedExpenses), color = if (selectedIncome >= selectedExpenses) Green else ExpenseRed, fontWeight = FontWeight.Bold)
                    if (selectedTransactions.isEmpty()) Text("No dated transactions for this day.", color = Muted, fontSize = 12.sp)
                    else selectedTransactions.forEach { TransactionRow(it) }
                }
            }
        }
        item { SectionHeading("Upcoming EMI reminders", "Loans") }
        if (loans.isEmpty()) {
            item { Text("No loans added yet. Add a loan to see EMI reminders here.", color = Muted) }
        } else {
            items(loans) { loan ->
                Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(loan.lender, color = Ink, fontWeight = FontWeight.Bold)
                        Text("Due ${loan.due}", color = Muted, fontSize = 12.sp)
                    }
                    Text(Money.format(loan.emi), color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(income: Long, expenses: Long, debt: Long) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Financial insights", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { MetricCard("Net tracked cash flow", Money.format(income - expenses), Modifier.fillMaxWidth()) }
        item { MetricCard("Tracked loan balance", Money.format(debt), Modifier.fillMaxWidth()) }
        item {
            InfoCard(
                "Spending check",
                if (income > 0) "You have tracked ${expenses * 100 / income}% of income as expenses. Check that cash, card and recurring payments are recorded."
                else "Add income and expenses to calculate a spending ratio.",
                Icons.Default.PieChart
            )
        }
        item { InfoCard("Debt overview", "Track each balance, APR, fee, minimum payment and due date. Compare total costs before refinancing.", Icons.Default.AccountBalance) }
        item { InfoCard("Emergency buffer", "Build savings gradually for essential expenses, at a pace that fits your income and obligations.", Icons.Default.Savings) }
    }
}

@Composable
private fun InfoCard(title: String, body: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = Green)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(body, color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun InfoScreen(title: String, body: String, icon: ImageVector) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(title, fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            InfoCard(title, body, icon)
        }
    }
}

@Composable
private fun CategoriesScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    val defaultCategories = listOf(
        "Food & groceries", "Housing", "Transport", "Bills & utilities", "Health",
        "Shopping", "Entertainment", "Education", "Loan repayment", "Salary", "Other income"
    )
    val savedCategories = remember {
        runCatching {
            val raw = preferences.getString("custom_categories", null)
            if (raw.isNullOrBlank()) defaultCategories else {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.getString(it) }.ifEmpty { defaultCategories }
            }
        }.getOrDefault(defaultCategories)
    }
    var categories by remember { mutableStateOf(savedCategories) }
    fun persistCategories(values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        preferences.edit().putString("custom_categories", array.toString()).apply()
    }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val categoryColors = listOf(Green, ExpenseRed, SavingsBlue, Color(0xFF9B6AD6), Color(0xFFE99B51))
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Categories", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Manage the labels used to organize your money", color = Muted)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { draft = ""; editing = null; showAdd = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add category")
            }
        }
        item { InfoCard("Keep reports organized", "Use consistent category names so spending patterns and recommendations are easier to understand.", Icons.Default.Insights) }
        items(categories, key = { it }) { category ->
            val tint = categoryColors[(categories.indexOf(category)).mod(categoryColors.size)]
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).padding(start = 13.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Label, null, tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Text(category, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Medium)
                IconButton(onClick = { draft = category; editing = category; showAdd = true }) {
                    Icon(Icons.Default.Edit, "Edit category", tint = Green)
                }
                IconButton(onClick = { pendingDelete = category }) {
                    Icon(Icons.Default.DeleteOutline, "Delete category", tint = ExpenseRed)
                }
            }
        }
        item { Text("Your custom category list is saved on this device and remains after restarting the app. Existing transactions keep their original category labels.", color = Muted, fontSize = 11.sp) }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (editing == null) "Add category" else "Edit category") },
            text = { OutlinedTextField(draft, { draft = it }, label = { Text("Category name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = draft.trim()
                    if (name.isNotBlank()) {
                        categories = if (editing == null) {
                            if (categories.any { it.equals(name, ignoreCase = true) }) categories else categories + name
                        } else categories.map { if (it == editing) name else it }.distinctBy { it.lowercase(Locale.ROOT) }
                        persistCategories(categories)
                        showAdd = false
                    }
                }) { Text("Save", color = Green) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete category?") },
            text = { Text("Remove \"$category\" from this category list? Existing transactions are not changed.") },
            confirmButton = { TextButton(onClick = { categories = categories - category; persistCategories(categories); pendingDelete = null }) { Text("Delete", color = ExpenseRed) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsScreen(onOpen: (String) -> Unit) {
    val entries = listOf(
        "Categories" to Icons.Default.Label,
        "Calendar" to Icons.Default.CalendarMonth,
        "Insights" to Icons.Default.Insights,
        "Credit health" to Icons.Default.Speed,
        "Appearance" to Icons.Default.Palette,
        "Backup & security" to Icons.Default.Security,
        "Live payment tracking" to Icons.Default.Sync,
        "Import transactions" to Icons.Default.FileUpload
    )
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Settings", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Personalize your workspace", color = Muted)
        }
        item { InfoCard("Preferences", "Reference palette: forest green, mint, white and slate. Transactions and loans are saved locally on this device. Open Appearance to save display preferences.", Icons.Default.Tune) }
        item { Button(onClick = { onOpen("Categories") }, modifier = Modifier.fillMaxWidth()) { Text("Category management") } }
        item { OutlinedButton(onClick = { onOpen("Appearance") }, modifier = Modifier.fillMaxWidth()) { Text("Settings appearance") } }
        item { OutlinedButton(onClick = { onOpen("Backup & security") }, modifier = Modifier.fillMaxWidth()) { Text("Backup & security") } }
        item { OutlinedButton(onClick = { onOpen("CRED sync") }, modifier = Modifier.fillMaxWidth()) { Text("Live payment tracking") } }
        item { OutlinedButton(onClick = { onOpen("Import transactions") }, modifier = Modifier.fillMaxWidth()) { Text("Import bank CSV") } }
        item { OutlinedButton(onClick = { onOpen("Credit health") }, modifier = Modifier.fillMaxWidth()) { Text("CIBIL & credit health") } }
        item { OutlinedButton(onClick = { onOpen("Loan details") }, modifier = Modifier.fillMaxWidth()) { Text("Loan details & EMI schedule") } }
        item { Button(onClick = { onOpen("Add loan") }, modifier = Modifier.fillMaxWidth()) { Text("+ Add loan") } }
        items(entries) { (label, icon) ->
            Row(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).clickable { onOpen(label) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Green)
                Spacer(Modifier.width(12.dp))
                Text(label, Modifier.weight(1f), color = Ink)
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        item { Text("Open Finance AI · Prototype 1.0.0", color = Muted, fontSize = 11.sp) }
    }
}


@Composable
private fun CreditHealthScreen() {
    var selected by remember { mutableStateOf("Credit score") }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("CIBIL & Credit Health", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Credit score", "Recommendations").forEach { option ->
                    FilterChip(selected = selected == option, onClick = { selected = option }, label = { Text(option) }, leadingIcon = { Icon(if (option == "Credit score") Icons.Default.VerifiedUser else Icons.Default.Lightbulb, null) })
                }
            }
        }
        if (selected == "Credit score") {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Illustrative score preview", color = Muted, fontSize = 13.sp)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(154.dp)) {
                        CircularProgressIndicator(
                            progress = { 720f / 900f },
                            modifier = Modifier.fillMaxSize(),
                            color = Green,
                            trackColor = Mint,
                            strokeWidth = 11.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("720", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("of 900", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Text("Good · Sample only", color = Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("This is a demonstration value, not your actual CIBIL score. No credit bureau is connected.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            item { InfoCard("What affects your score?", "Repayment history, credit utilisation, length of credit history, credit mix and recent enquiries can influence a credit score. This app cannot assess your actual bureau factors without a verified report.", Icons.Default.Insights) }
            item { InfoCard("Get your verified score", "Check your score and report through the official TransUnion CIBIL website or an authorized credit bureau service. Review account balances and late-payment records for errors. Never share your bureau password or OTP with this app.", Icons.Default.OpenInNew) }
        } else {
            item { InfoCard("Protect your payment history", "Pay every EMI by its due date and keep enough balance before the auto-debit date. Set reminders a few days early.", Icons.Default.EventAvailable) }
            item { InfoCard("Manage credit utilisation", "Try to keep revolving credit-card utilisation low, ideally below 30%, and pay the full statement balance when possible.", Icons.Default.PieChart) }
            item { InfoCard("Avoid unnecessary applications", "Multiple loan or card applications in a short period can create hard enquiries. Compare total cost and fees before applying.", Icons.Default.Rule) }
            item { InfoCard("Review your report", "Check the official report for accounts you do not recognize, incorrect late payments, and outdated balances. Raise a dispute with the bureau/lender if needed.", Icons.Default.FactCheck) }
        }
    }
}
@Composable
private fun BackupSecurityScreen() {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Backup & Security", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { InfoCard("Local data", "Transactions and loans are saved in app-private local preferences and remain after app restarts on this device. Uninstalling the app or clearing its data can remove them; cloud backup is not enabled.", Icons.Default.Lock) }
        item { InfoCard("Cloud backup", "Google Drive / OneDrive backup is not connected in this build.", Icons.Default.CloudUpload) }
        item { InfoCard("Restore & export", "Restore, CSV export and scheduled backups are not implemented yet.", Icons.Default.Restore) }
        item { InfoCard("Privacy", "Do not enter banking passwords, card PINs or other secrets into this prototype.", Icons.Default.PrivacyTip) }
    }
}
@Composable
private fun CredSyncScreen() {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Live payment tracking", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("CRED · Google Pay · PhonePe · Paytm · bank alerts", color = Muted)
        }
        item {
            InfoCard("Stage 1 · Notification capture",
                "MoneyLog can read payment notifications only after you explicitly enable Notification access in Android settings. It extracts amount, app/source, recipient text when available, and notification time, then saves locally. Some apps hide details or do not post notifications, so this is best-effort and not a verified bank ledger.",
                Icons.Default.NotificationsActive)
        }
        item {
            Button(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Enable payment notification access") }
        }
        item {
            InfoCard("Stage 2 · Bank statement / Account Aggregator",
                "Import statement CSV files to fill gaps and reconcile notification captures. A live Account Aggregator connection requires an eligible regulated Financial Information User/provider, user consent, and a real integration; it is not connected in this build.",
                Icons.Default.VerifiedUser)
        }
        item {
            InfoCard("Stage 3 · Official CRED integration",
                "Connect directly to CRED only if an official API or partner integration is approved and credentials are securely configured. No private API, password scraping, UPI PIN, OTP, or SMS scraping is used.",
                Icons.Default.Sync)
        }
        item {
            InfoCard("Transaction details & safety",
                "Captured entries include available recipient/merchant text, amount, and timestamp. Notification text can be incomplete or misleading; verify important payments against your bank/UPI history. Duplicates are filtered using notification identifiers where available.",
                Icons.Default.ReceiptLong)
        }
        item {
            InfoCard("Privacy",
                "Captured transactions are stored in app-private local storage. Android notification access can expose other app notifications, so enable it only if you accept that access; you can revoke it in Android settings at any time.",
                Icons.Default.Lock)
        }
    }
}
@Composable
private fun AppearanceScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(preferences.getString("appearance_theme", "Light") ?: "Light") }
    var currency by remember { mutableStateOf(preferences.getString("appearance_currency", "INR (₹)") ?: "INR (₹)") }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings · Appearance", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", color = Ink, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Light", "Dark", "System").forEach { value -> FilterChip(selected = theme == value, onClick = { theme = value; preferences.edit().putString("appearance_theme", value).apply() }, label = { Text(value) }) }
                    }
                    Text("Currency", color = Ink, fontWeight = FontWeight.Bold)
                    listOf("INR (₹)", "USD ($)", "EUR (€)").forEach { value -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currency == value, onClick = { currency = value; preferences.edit().putString("appearance_currency", value).apply() }); Text(value, color = Ink) } }
                    Text("Your theme and currency selections are saved on this device. The reference palette remains forest green, mint, white and slate; dark-theme rendering and currency-wide amount conversion are not yet applied across every screen.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
@Composable
private fun LoanDetailsScreen(loan: Loan, onEmiLongPress: () -> Unit) {
    var scheduleBalance = loan.balance.toDouble().coerceAtLeast(0.0)
    val monthlyRate = (loan.annualRate.coerceAtLeast(0.0) / 1200.0)
    val schedule = (0 until 6).map { index ->
        val interest = scheduleBalance * monthlyRate
        val principalPart = (loan.emi.toDouble() - interest).coerceAtLeast(0.0).coerceAtMost(scheduleBalance)
        val closingBalance = (scheduleBalance - principalPart).coerceAtLeast(0.0)
        val row = Triple(interest.toLong(), principalPart.toLong(), closingBalance.toLong())
        scheduleBalance = closingBalance
        row
    }
    val paymentTooLow = monthlyRate > 0.0 && loan.emi.toDouble() <= loan.balance.toDouble() * monthlyRate
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Loan Details & EMI Schedule", fontSize = 25.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { MetricCard("Outstanding · ${loan.lender}", Money.format(loan.balance), Modifier.fillMaxWidth()) }
        item { MetricCard("Monthly EMI", Money.format(loan.emi), Modifier.fillMaxWidth()) }
        item { MetricCard("Annual interest rate", "${loan.annualRate}%", Modifier.fillMaxWidth()) }
        item { InfoCard("Estimated schedule", "Based on the current outstanding balance, entered EMI and interest rate. This is an estimate, not your lender's official schedule; verify exact figures and due dates against your loan statement.", Icons.Default.CalendarMonth) }
        if (paymentTooLow) item {
            InfoCard("EMI may not cover interest", "At the entered interest rate, the EMI is not greater than the estimated monthly interest. The balance may not reduce unless the EMI or rate changes.", Icons.Default.Warning)
        }
        item { SectionHeading("Estimated EMI breakdown", "Next 6 months") }
        item {
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("Payment", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
                Text("Interest", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
                Text("Principal", Modifier.weight(1f), color = Muted, fontSize = 11.sp)
            }
        }
        items(schedule.indices.toList()) { index ->
            val (interest, principalPart, closingBalance) = schedule[index]
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(13.dp)).combinedClickable(onClick = { onEmiLongPress() }, onLongClick = { onEmiLongPress() }).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (index == 0) loan.due else "Month ${index + 1}", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Balance ${Money.format(closingBalance)}", color = Muted, fontSize = 10.sp)
                }
                Text(Money.format(interest), Modifier.weight(1f), color = ExpenseRed, fontSize = 12.sp)
                Text(Money.format(principalPart), Modifier.weight(1f), color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
@Composable
private fun EmptyLoansScreen(onAddLoan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No loans tracked yet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text("Add a loan to start tracking outstanding balance and monthly EMIs.", color = Muted)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddLoan) { Text("Add loan") }
    }
}

@Composable
private fun AddLoanScreen(onSave: (String, Long, Double, Long) -> Unit) {
    var lender by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("12") }
    var emi by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Add Loan", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(lender, { lender = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Lender / Bank") }, singleLine = true)
                    OutlinedTextField(principal, { principal = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Principal amount (₹)") }, singleLine = true)
                    OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, modifier = Modifier.fillMaxWidth(), label = { Text("Annual interest rate (%)") }, singleLine = true)
                    OutlinedTextField(emi, { emi = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Monthly EMI (₹)") }, singleLine = true)
                    Button(onClick = {
                        val amount = principal.toLongOrNull() ?: 0L
                        val monthly = emi.toLongOrNull() ?: 0L
                        val annualRate = rate.toDoubleOrNull() ?: 0.0
                        if (lender.isNotBlank() && amount > 0 && monthly > 0 && annualRate >= 0) {
                            onSave(lender.trim(), amount, annualRate, monthly)
                            saved = true
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save Loan") }
                    if (saved) Text("Loan saved on this device.", color = IncomeGreen)
                }
            }
        }
    }
}
@Composable
private fun EditTransactionDialog(transaction: Transaction, onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    var title by remember(transaction) { mutableStateOf(transaction.title) }
    var amount by remember(transaction) { mutableStateOf(transaction.amount.toString()) }
    var category by remember(transaction) { mutableStateOf(transaction.category) }
    var date by remember(transaction) { mutableStateOf(transaction.date) }
    var isIncome by remember(transaction) { mutableStateOf(transaction.income) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("Expense") })
                    FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("Income") })
                }
                OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount (₹)") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Date") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = amount.toLongOrNull()
                if (title.isBlank() || parsed == null || parsed <= 0L) error = "Enter a description and valid amount."
                else onSave(transaction.copy(title = title.trim(), amount = parsed, category = if (isIncome) "Income" else category.ifBlank { "Other" }, date = date.ifBlank { "Date not set" }, income = isIncome))
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditLoanDialog(loan: Loan, onDismiss: () -> Unit, onSave: (Loan) -> Unit) {
    var lender by remember(loan) { mutableStateOf(loan.lender) }
    var balance by remember(loan) { mutableStateOf(loan.balance.toString()) }
    var emi by remember(loan) { mutableStateOf(loan.emi.toString()) }
    var rate by remember(loan) { mutableStateOf(loan.annualRate.toString()) }
    var due by remember(loan) { mutableStateOf(loan.due) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit loan / EMI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(lender, { lender = it }, label = { Text("Lender") }, singleLine = true)
                OutlinedTextField(balance, { balance = it.filter(Char::isDigit) }, label = { Text("Outstanding balance (₹)") }, singleLine = true)
                OutlinedTextField(emi, { emi = it.filter(Char::isDigit) }, label = { Text("Monthly EMI (₹)") }, singleLine = true)
                OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Annual interest (%)") }, singleLine = true)
                OutlinedTextField(due, { due = it }, label = { Text("Next due date") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedBalance = balance.toLongOrNull()
                val parsedEmi = emi.toLongOrNull()
                val parsedRate = rate.toDoubleOrNull()
                if (lender.isBlank() || parsedBalance == null || parsedBalance < 0L || parsedEmi == null || parsedEmi <= 0L || parsedRate == null || parsedRate < 0.0) error = "Check lender, balance, EMI and interest rate."
                else onSave(loan.copy(lender = lender.trim(), balance = parsedBalance, emi = parsedEmi, annualRate = parsedRate, due = due.ifBlank { "Not set" }))
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, Boolean, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val context = LocalContext.current
    val savedCategories = remember {
        runCatching {
            val raw = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE).getString("custom_categories", null)
            if (raw.isNullOrBlank()) listOf("Food & groceries", "Housing", "Transport", "Bills & utilities", "Health", "Shopping", "Entertainment", "Education", "Loan repayment", "Salary", "Other income")
            else (0 until JSONArray(raw).length()).map { JSONArray(raw).getString(it) }
        }.getOrDefault(listOf("Food & groceries", "Housing", "Transport", "Bills & utilities", "Health", "Shopping", "Entertainment", "Education", "Loan repayment", "Salary", "Other income"))
    }
    var category by remember { mutableStateOf("Food & groceries") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !isIncome, onClick = { isIncome = false }, label = { Text("Expense") })
                    FilterChip(selected = isIncome, onClick = { isIncome = true }, label = { Text("Income") })
                }
                OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true)
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount (₹)") }, singleLine = true)
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        savedCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { category = option; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                Text("Payment method", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("UPI", "Cash", "Card", "Bank").forEach { method ->
                        FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method }, label = { Text(method) })
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = amount.toLongOrNull()
                val validDate = runCatching { java.time.LocalDate.parse(date); true }.getOrDefault(false)
                if (title.isBlank() || parsed == null || parsed <= 0) {
                    error = "Enter a description and valid amount."
                } else if (!validDate) {
                    error = "Enter the date as YYYY-MM-DD."
                } else {
                    onSave(title.trim(), parsed, isIncome, if (isIncome) "Income" else category.ifBlank { "Other" }, date, paymentMethod, notes.trim())
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Green), modifier = Modifier.fillMaxWidth()) { Text("Save transaction") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}



private fun transactionDateOrNull(transaction: Transaction, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate? {
    val raw = transaction.date.trim()
    return runCatching {
        when {
            raw.equals("today", ignoreCase = true) || raw.equals("just now", ignoreCase = true) -> today
            raw.equals("yesterday", ignoreCase = true) -> today.minusDays(1)
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw) -> java.time.LocalDate.parse(raw)
            Regex("^\\d{1,2} [A-Za-z]{3,9}$").matches(raw) -> {
                val parsed = java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM").toFormatter(Locale.ENGLISH).parse(raw)
                java.time.LocalDate.of(today.year, parsed.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), parsed.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
            }
            Regex("^[A-Za-z]{3,9} \\d{1,2}$").matches(raw) -> {
                val parsed = java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM d").toFormatter(Locale.ENGLISH).parse(raw)
                java.time.LocalDate.of(today.year, parsed.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), parsed.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
            }
            else -> null
        }
    }.getOrNull()
}

private fun transactionBelongsToMonth(transaction: Transaction, month: YearMonth, today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
    transactionDateOrNull(transaction, today)?.let { YearMonth.from(it) == month } ?: false

private fun transactionKey(transaction: Transaction): String {
    fun normalize(value: String): String = value.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
    return "${normalize(transaction.title)}|${normalize(transaction.date)}|${transaction.amount}|${transaction.income}"
}

private fun saveTransactions(context: android.content.Context, transactions: List<Transaction>) {
    val array = JSONArray()
    transactions.forEach { transaction ->
        array.put(JSONObject().apply {
            put("title", transaction.title)
            put("category", transaction.category)
            put("date", transaction.date)
            put("amount", transaction.amount)
            put("income", transaction.income)
            put("source", transaction.source)
            put("notificationKey", transaction.notificationKey)
            put("captureStatus", transaction.captureStatus)
            put("paymentMethod", transaction.paymentMethod)
            put("notes", transaction.notes)
        })
    }
    context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
        .edit().putString("transactions", array.toString()).apply()
}

private fun loadTransactions(context: android.content.Context): List<Transaction>? = runCatching {
    val raw = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
        .getString("transactions", null) ?: return null
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        Transaction(
            item.getString("title"),
            item.optString("category", "Other"),
            item.optString("date", ""),
            item.getLong("amount"),
            item.optBoolean("income", false),
            item.optString("source", ""),
            item.optString("notificationKey", ""),
            item.optString("captureStatus", ""),
            item.optString("paymentMethod", "Not set"),
            item.optString("notes", "")
        )
    }
}.getOrNull()

private fun parseTransactionCsv(csv: String): List<Transaction> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    var cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < csv.length) {
        val ch = csv[i]
        when {
            ch == '"' && quoted && i + 1 < csv.length && csv[i + 1] == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            (ch == ',' || ch == ';' || ch == '\t') && !quoted -> { row.add(cell.toString().trim()); cell = StringBuilder() }
            (ch == '\n' || ch == '\r') && !quoted -> {
                if (ch == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                row.add(cell.toString().trim())
                if (row.any { it.isNotBlank() }) rows.add(row)
                row = mutableListOf()
                cell = StringBuilder()
            }
            else -> cell.append(ch)
        }
        i++
    }
    row.add(cell.toString().trim())
    if (row.any { it.isNotBlank() }) rows.add(row)
    require(rows.size >= 2) { "CSV must contain a header row and at least one transaction." }
    val header = rows.first().map { it.trim().lowercase(Locale.ROOT).replace("_", " ") }
    fun findHeader(vararg names: String) = names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } }
    val dateIndex = findHeader("date", "transaction date", "txn date", "value date")
    val titleIndex = findHeader("description", "narration", "details", "merchant", "particulars", "transaction details", "title")
    val amountIndex = findHeader("amount", "transaction amount", "txn amount")
    val debitIndex = findHeader("debit", "withdrawal", "withdrawals", "money out")
    val creditIndex = findHeader("credit", "deposit", "deposits", "money in")
    val typeIndex = findHeader("type", "transaction type", "dr/cr")
    val categoryIndex = findHeader("category")
    require(titleIndex != null && (amountIndex != null || debitIndex != null || creditIndex != null)) {
        "Could not identify columns. Include Description/Narration and Amount, or separate Debit and Credit columns."
    }
    fun amountAt(values: List<String>, index: Int?): Long {
        if (index == null || index !in values.indices) return 0L
        val cleaned = values[index].replace(",", "").replace("₹", "").replace("INR", "", ignoreCase = true).replace(" ", "").trim()
        return cleaned.replace(Regex("[^0-9.()-]"), "").replace("(", "-").replace(")", "").toDoubleOrNull()?.let { kotlin.math.abs(it).toLong() } ?: 0L
    }
    return rows.drop(1).mapNotNull { values ->
        val title = values.getOrNull(titleIndex)?.trim().orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val debit = amountAt(values, debitIndex)
        val credit = amountAt(values, creditIndex)
        val rawAmount = if (amountIndex != null) amountAt(values, amountIndex) else maxOf(debit, credit)
        if (rawAmount <= 0L) return@mapNotNull null
        val type = values.getOrNull(typeIndex ?: -1).orEmpty().lowercase(Locale.ROOT)
        val isIncome = if (creditIndex != null || debitIndex != null) credit > 0L && debit == 0L
            else type.contains("cr") || type.contains("credit") || (amountIndex != null && values.getOrNull(amountIndex)?.trim()?.startsWith("+") == true)
        Transaction(
            title = title,
            category = values.getOrNull(categoryIndex ?: -1)?.takeIf { it.isNotBlank() } ?: if (isIncome) "Income" else "Imported",
            date = values.getOrNull(dateIndex ?: -1)?.takeIf { it.isNotBlank() } ?: "Imported",
            amount = rawAmount,
            income = isIncome
        )
    }.also { require(it.isNotEmpty()) { "No valid transaction rows found. Check the CSV column names and amounts." } }
}

@Composable
private fun ImportTransactionsScreen(
    message: String,
    previewCount: Int,
    onChooseFile: () -> Unit,
    onImport: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Import transactions", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Import a bank statement CSV saved on your device.", color = Muted)
        }
        item { InfoCard("Supported columns", "Description / Narration and Amount, or Description with separate Debit and Credit columns. Date, Type and Category are optional.", Icons.Default.FileUpload) }
        item { Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) { Text("Choose CSV file") } }
        if (message.isNotBlank()) item { InfoCard("Import check", message, Icons.Default.Info) }
        if (previewCount > 0) item {
            InfoCard("Ready to import", "$previewCount new transaction(s) found. Existing matching rows will be skipped.", Icons.Default.CheckCircle)
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import $previewCount transactions") }
        }
        item { Text("Privacy: the selected file is read on this device. Imported transactions are stored locally in app preferences. Review your statement before importing; bank CSV formats can vary.", color = Muted, fontSize = 12.sp) }
    }
}


private fun saveLoans(context: android.content.Context, loans: List<Loan>) {
    val array = JSONArray()
    loans.forEach { loan ->
        array.put(JSONObject().apply {
            put("lender", loan.lender)
            put("balance", loan.balance)
            put("emi", loan.emi)
            put("due", loan.due)
            put("annualRate", loan.annualRate)
        })
    }
    context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
        .edit().putString("loans", array.toString()).apply()
}

private fun loadLoans(context: android.content.Context): List<Loan>? = runCatching {
    val raw = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
        .getString("loans", null) ?: return null
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        Loan(item.getString("lender"), item.getLong("balance"), item.getLong("emi"), item.optString("due", "Not set"), item.optDouble("annualRate", 0.0))
    }
}.getOrNull()
