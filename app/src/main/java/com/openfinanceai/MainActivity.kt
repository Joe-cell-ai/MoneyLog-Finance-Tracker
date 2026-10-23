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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
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

private val Green = Color(0xFF6558D3) // Premium indigo-violet primary
private val Mint = Color(0xFFF0EDFF) // Soft lavender surface tint
private val IncomeGreen = Color(0xFF168568)
private val ExpenseRed = Color(0xFFD94F69)
private val SavingsBlue = Color(0xFF3478D4)
private val Ink = Color(0xFF22243A)
private val Muted = Color(0xFF74788D)
private val Page = Color(0xFFF7F7FB)
private val IncomeTint = Color(0xFFE8F7F1)
private val ExpenseTint = Color(0xFFFFEEF1)
private val SkyTint = Color(0xFFEAF3FF)
private val PeachTint = Color(0xFFFFF1E6)
private val PinkTint = Color(0xFFFFEFF6)
private val LavenderTint = Color(0xFFF0EDFF)
private val Teal = Color(0xFF2D9A91)
private val Orange = Color(0xFFE69A45)
private val Purple = Color(0xFF8A63D2)
private val Blue = Color(0xFF3478D4)
private val Money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val CategoryPalette = listOf(Color(0xFF356AE6), Color(0xFF159765), Color(0xFF8953D8), Color(0xFFD97732), Color(0xFFDB4765), Color(0xFF148D91), Color(0xFFB18A16), Color(0xFF477DAD), Color(0xFFAF5AB5), Color(0xFF567A35), Color(0xFFCF5A36), Color(0xFF5470C6))
private fun categoryColor(category: String): Color = when (category.trim().lowercase(Locale.ROOT)) {
    "salary", "payroll", "wages" -> Color(0xFF356AE6)
    "business", "business income", "freelance" -> Color(0xFF159765)
    "gift", "gifts" -> Color(0xFF8953D8)
    "bills", "bills & utilities", "utilities", "electricity", "rent" -> Color(0xFFD97732)
    "shopping", "clothing" -> Color(0xFFDB4765)
    "food", "food & groceries", "food & drink", "groceries" -> Color(0xFF148D91)
    "transport", "fuel", "travel" -> Color(0xFFB18A16)
    "health", "medical" -> Color(0xFF477DAD)
    "education" -> Color(0xFFAF5AB5)
    "loan repayment", "emi" -> Color(0xFFCF5A36)
    "other income", "income" -> Color(0xFF5470C6)
    else -> CategoryPalette[(category.trim().lowercase(Locale.ROOT).hashCode() and 0x7fffffff) % CategoryPalette.size]
}
private fun categoryTint(category: String): Color = categoryColor(category).copy(alpha = 0.12f)

private data class Transaction(val title: String, val category: String, val date: String, val amount: Long, val income: Boolean, val source: String = "", val notificationKey: String = "", val captureStatus: String = "", val paymentMethod: String = "Not set", val notes: String = "")
private data class Loan(val lender: String, val balance: Long, val emi: Long, val due: String, val annualRate: Double = 0.0, val originalPrincipal: Long = balance, val paymentsMade: Long = 0L)

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
    var showAddAsIncome by remember { mutableStateOf(false) }
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

    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            val csv = buildString {
                appendLine("Date,Description,Category,Type,Amount,Payment Method,Notes")
                transactions.forEach { t ->
                    appendLine(listOf(t.date, t.title, t.category, if (t.income) "Income" else "Expense", t.amount.toString(), t.paymentMethod, t.notes)
                        .joinToString(",") { csvEscape(it) })
                }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                ?: error("Could not write the CSV file.")
            importMessage = "CSV export saved successfully."
        }.onFailure { importMessage = it.message ?: "CSV export failed." }
    }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            val root = JSONObject().put("format", "open-finance-backup").put("version", 1)
            val transactionArray = JSONArray()
            transactions.forEach { t -> transactionArray.put(JSONObject()
                .put("title", t.title).put("category", t.category).put("date", t.date)
                .put("amount", t.amount).put("income", t.income).put("source", t.source)
                .put("notificationKey", t.notificationKey).put("captureStatus", t.captureStatus)
                .put("paymentMethod", t.paymentMethod).put("notes", t.notes)) }
            val loanArray = JSONArray()
            loans.forEach { l -> loanArray.put(JSONObject().put("lender", l.lender).put("balance", l.balance)
                .put("emi", l.emi).put("due", l.due).put("annualRate", l.annualRate).put("originalPrincipal", l.originalPrincipal).put("paymentsMade", l.paymentsMade)) }
            val prefs = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
            val savedCategories = runCatching { JSONArray(prefs.getString("custom_categories", "[]") ?: "[]") }.getOrDefault(JSONArray())
            root.put("transactions", transactionArray)
                .put("loans", loanArray)
                .put("categories", savedCategories)
                .put("appearanceTheme", prefs.getString("appearance_theme", "Light"))
                .put("appearanceCurrency", prefs.getString("appearance_currency", "INR (₹)"))
                .put("userReportedCreditScore", prefs.getInt("user_reported_credit_score", 0))
                .put("monthlyCategoryBudgets", JSONObject(prefs.getString("monthly_category_budgets", "{}") ?: "{}"))
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString(2)) }
                ?: error("Could not write the backup file.")
        }.onFailure { importMessage = it.message ?: "Backup export failed." }
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read the selected backup.")
            val root = JSONObject(raw)
            require(root.optString("format") == "open-finance-backup") { "This file is not an Open Finance backup." }
            val restoredTransactions = root.getJSONArray("transactions").let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
                    Transaction(j.getString("title"), j.optString("category", "Other"), j.optString("date", "Today"),
                        j.getLong("amount"), j.optBoolean("income"), j.optString("source"), j.optString("notificationKey"),
                        j.optString("captureStatus"), j.optString("paymentMethod", "Not set"), j.optString("notes"))
                } }
            }
            val restoredLoans = root.optJSONArray("loans")?.let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
                    Loan(j.getString("lender"), j.getLong("balance"), j.getLong("emi"), j.optString("due", "Not set"), j.optDouble("annualRate", 0.0), j.optLong("originalPrincipal", j.getLong("balance")), j.optLong("paymentsMade", 0L))
                } }
            } ?: emptyList()
            val prefs = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
            val editor = prefs.edit()
            root.optJSONArray("categories")?.let { editor.putString("custom_categories", it.toString()) }
            if (root.has("appearanceTheme")) editor.putString("appearance_theme", root.optString("appearanceTheme", "Light"))
            if (root.has("appearanceCurrency")) editor.putString("appearance_currency", root.optString("appearanceCurrency", "INR (₹)"))
            if (root.has("userReportedCreditScore")) {
                val restoredScore = root.optInt("userReportedCreditScore", 0)
                if (restoredScore in 300..900) editor.putInt("user_reported_credit_score", restoredScore)
                else editor.remove("user_reported_credit_score")
            }
            root.optJSONObject("monthlyCategoryBudgets")?.let { editor.putString("monthly_category_budgets", it.toString()) }
            editor.apply()
            transactions = restoredTransactions
            loans = restoredLoans
            saveTransactions(context, transactions)
            saveLoans(context, loans)
            importMessage = "Backup restored: ${transactions.size} transactions, ${loans.size} loans and local settings."
        }.onFailure { importMessage = it.message ?: "Backup restore failed." }
    }
    val income = transactions.filter { it.income }.sumOf { it.amount }
    val expenses = transactions.filterNot { it.income }.sumOf { it.amount }
    val selectedMonthTransactions = transactions.filter { transactionBelongsToMonth(it, selectedOverviewMonth) }
    val selectedMonthIncome = selectedMonthTransactions.filter { it.income }.sumOf { it.amount }
    val selectedMonthExpenses = selectedMonthTransactions.filterNot { it.income }.sumOf { it.amount }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            primaryContainer = Mint,
            onPrimaryContainer = Ink,            secondary = SavingsBlue,
            onSecondary = Color.White,
            tertiary = Purple,
            onTertiary = Color.White,
            error = ExpenseRed,
            background = Page,
            surface = Color.White,
            surfaceVariant = Color(0xFFF0F1F8),
            onSurface = Ink,
            onSurfaceVariant = Muted,
            outline = Color(0xFFDADCE8)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(18.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp)
        )
    ) {
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
                        Text("MONEYLOG", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Text("Your money, clearly.", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { tab = "Settings" }) { Icon(Icons.Default.Settings, "Settings") }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 3.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
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
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Mint)
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == "Home" || tab == "Activity") {
                    FloatingActionButton(onClick = { showAddAsIncome = false; showAdd = true }, containerColor = Green, contentColor = Color.White) {
                        Icon(Icons.Default.Add, "Add transaction")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    "Home" -> HomeScreen(selectedMonthIncome, selectedMonthExpenses, transactions, selectedMonthTransactions, selectedOverviewMonth, { selectedOverviewMonth = it }, { tab = "Activity" }, { isIncome -> showAddAsIncome = isIncome; showAdd = true }, { tab = it }, { transactionForActions = it })
                    "Activity" -> ActivityScreen(transactions, { transactionForActions = it })
                    "Reports" -> ReportsScreen(selectedMonthIncome, selectedMonthExpenses, selectedMonthTransactions, selectedOverviewMonth, { selectedOverviewMonth = it }, { tab = it })
                    "Loans" -> LoansScreen(loans, { tab = "Add loan" }, { loan -> selectedLoan = loan; tab = "Loan details" }, { loanForActions = it })
                    "Coach" -> CoachScreen(income, expenses, loans.sumOf { it.emi })
                    "Calendar" -> CalendarScreen(loans, transactions)
                    "Insights" -> InsightsScreen(income, expenses, loans.sumOf { it.balance }, transactions)
                    "Budgets" -> BudgetsScreen(transactions)
                    "Credit health" -> CreditHealthScreen()
                    "Categories" -> CategoriesScreen()
                    "Backup & security" -> BackupSecurityScreen(
                        onExportBackup = { backupExportLauncher.launch("open-finance-backup.json") },
                        onRestoreBackup = { backupRestoreLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExportCsv = { csvExportLauncher.launch("open-finance-transactions.csv") },
                        message = importMessage
                    )
                    "CRED sync", "Live payment tracking" -> CredSyncScreen()
                    "Import transactions" -> ImportTransactionsScreen(importMessage, importedPreview.size, { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, {
                        transactions = (importedPreview + transactions).distinctBy { transactionKey(it) }
                        saveTransactions(context, transactions)
                        importMessage = "Imported ${importedPreview.size} transaction(s). Saved on this device."
                        importedPreview = emptyList()
                    })
                    "Appearance" -> AppearanceScreen()
                    "Loan details" -> selectedLoan?.let { loan -> LoanDetailsScreen(loan) { loanForActions = loan } } ?: loans.firstOrNull()?.let { loan -> LoanDetailsScreen(loan) { loanForActions = loan } } ?: EmptyLoansScreen { tab = "Add loan" }
                    "Add loan" -> AddLoanScreen { lender, originalPrincipal, balance, paymentsMade, rate, emi ->
                        val newLoan = Loan(lender, balance, emi, "Set due date", rate, originalPrincipal, paymentsMade)
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
                    initialIncome = showAddAsIncome,
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
    onAdd: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onTransactionLongPress: (Transaction) -> Unit
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    // Keep dashboard totals consistent with the month currently selected.
    val monthIncome = monthTransactions.filter { it.income }.sumOf { it.amount }
    val monthExpenses = monthTransactions.filterNot { it.income }.sumOf { it.amount }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportMetricCard("Income", Money.format(monthIncome), Icons.Default.AccountBalanceWallet, Modifier.weight(1f), Color(0xFFE3F9ED), Color(0xFF00A86B))
                ReportMetricCard("Expense", Money.format(monthExpenses), Icons.Default.CreditCard, Modifier.weight(1f), Color(0xFFFFEBEB), Color(0xFFFD3C65))
                ReportMetricCard("Savings", Money.format(monthIncome - monthExpenses), Icons.Default.Savings, Modifier.weight(1f), Color(0xFFE6F0FF), Color(0xFF0066FF))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                QuickAction("Add expense", Icons.Default.Remove, Modifier.weight(1f)) { onAdd(false) }
                QuickAction("Add income", Icons.Default.Add, Modifier.weight(1f)) { onAdd(true) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly overview", modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.background(Color.White, RoundedCornerShape(14.dp))
                ) {
                    IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Purple)
                    }
                    TextButton(onClick = { showMonthPicker = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                        Text(
                            selectedMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + selectedMonth.year,
                            color = Ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose month", tint = Purple)
                    }
                    IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Purple)
                    }
                }
            }
        }
        item {
            val context = LocalContext.current
            val savedBudgets = remember {
                runCatching {
                    val json = JSONObject(context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE).getString("monthly_category_budgets", "{}") ?: "{}")
                    buildMap { val keys = json.keys(); while (keys.hasNext()) { val key = keys.next(); put(key, json.optLong(key, 0L)) } }
                }.getOrDefault(emptyMap())
            }
            val budgetTotal = savedBudgets.values.sum()
            val monthSpent = monthTransactions.filterNot { it.income }.sumOf { it.amount }
            val monthExpenseCategories = monthTransactions.filterNot { it.income }.groupBy { it.category }
                .map { entry -> entry.key to entry.value.sumOf { it.amount } }.sortedByDescending { it.second }
            val categorySpend = monthExpenseCategories.sumOf { it.second }
            Card(colors = CardDefaults.cardColors(containerColor = LavenderTint), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MONTHLY BUDGET", color = Purple, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                            Text(if (budgetTotal > 0L) "${Money.format(monthSpent)} of ${Money.format(budgetTotal)}" else "Set your monthly budget", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onOpen("Budgets") }) { Text("Manage") }
                    }
                    LinearProgressIndicator(progress = { if (budgetTotal > 0L) (monthSpent.toFloat() / budgetTotal).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(7.dp), color = if (budgetTotal > 0L && monthSpent > budgetTotal) ExpenseRed else Purple, trackColor = Color(0xFFDCD5FA))
                    Text(if (budgetTotal > 0L && monthSpent <= budgetTotal) "${Money.format(budgetTotal - monthSpent)} remaining this month" else if (budgetTotal > 0L) "${Money.format(monthSpent - budgetTotal)} over budget" else "Add category limits to track your monthly budget here.", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spending by category", color = Color(0xFF173D70), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(0.86f), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(Modifier.size(126.dp)) {
                                if (categorySpend > 0L) {
                                    var start = -90f
                                    monthExpenseCategories.forEach { entry ->
                                        val sweep = entry.second.toFloat() / categorySpend * 360f
                                        drawArc(categoryColor(entry.first), start, sweep, false, style = Stroke(width = size.minDimension * .22f))
                                        start += sweep
                                    }
                                } else drawArc(Mint, 0f, 360f, false, style = Stroke(width = size.minDimension * .22f))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Expense", color = Color(0xFF8094B0), fontSize = 10.sp)
                                Text(Money.format(categorySpend).substringBeforeLast("."), color = Color(0xFF173D70), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                            }
                        }
                        Column(Modifier.weight(1.14f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (monthExpenseCategories.isEmpty()) Text("Add expenses to see categories.", color = Muted, fontSize = 12.sp)
                            monthExpenseCategories.take(7).forEach { (name, amount) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(Modifier.size(8.dp).background(categoryColor(name), CircleShape))
                                    Text(name, modifier = Modifier.weight(1f), color = Color(0xFF52627A), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                    Text(if (categorySpend > 0L) "${amount * 100 / categorySpend}%" else "0%", color = Color(0xFF52627A), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeading("Recent transactions", "See all", onAll) }
        if (monthTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Muted, modifier = Modifier.size(28.dp))
                        Text("No transactions for this month", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("Add an income or expense to see it here.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(monthTransactions.take(4)) { transaction ->
                TransactionRow(transaction, onLongPress = { onTransactionLongPress(transaction) })
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
    val tint = when (label) { "Add expense" -> ExpenseTint; "Add income" -> IncomeTint; else -> LavenderTint }
    val accent = when (label) { "Add expense" -> ExpenseRed; "Add income" -> IncomeGreen; else -> Purple }
    Card(modifier = modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = tint), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = accent)
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
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).combinedClickable(onClick = onLongPress, onLongClick = onLongPress).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(categoryColor(transaction.category)), contentAlignment = Alignment.Center) {
            Icon(
                when {
                    transaction.income -> Icons.Default.AccountBalanceWallet
                    transaction.category.contains("food", true) || transaction.category.contains("grocer", true) -> Icons.Default.Restaurant
                    transaction.category.contains("transport", true) || transaction.category.contains("travel", true) -> Icons.Default.DirectionsCar
                    transaction.category.contains("bill", true) || transaction.category.contains("utilit", true) || transaction.category.contains("home", true) -> Icons.Default.Home
                    transaction.category.contains("shop", true) -> Icons.Default.ShoppingBag
                    transaction.category.contains("entertain", true) -> Icons.Default.ConfirmationNumber
                    transaction.category.contains("health", true) || transaction.category.contains("fitness", true) -> Icons.Default.Favorite
                    else -> Icons.Default.MoreHoriz
                },
                contentDescription = transaction.category,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Text("${transaction.category} · ${transaction.date}", color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        Text((if (transaction.income) "+" else "−") + Money.format(transaction.amount), color = if (transaction.income) IncomeGreen else ExpenseRed, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ActivityScreen(transactions: List<Transaction>, onTransactionLongPress: (Transaction) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("All categories") }
    var categoryMenu by remember { mutableStateOf(false) }
    val availableCategories = listOf("All categories") + transactions.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val visible = transactions
        .filter { filter == "All" || (filter == "Income" && it.income) || (filter == "Expense" && !it.income) }
        .filter { categoryFilter == "All categories" || it.category == categoryFilter }
        .filter { query.isBlank() || listOf(it.title, it.category, it.source, it.notes, it.date).any { field -> field.contains(query.trim(), ignoreCase = true) } }
    val grouped = visible.groupBy { it.date }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Page),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Transactions", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("Every income and expense, all in one place", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search transactions") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color(0xFFE5E6F0)
                )
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(categoryFilter, maxLines = 1, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        availableCategories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                leadingIcon = { if (categoryFilter == option) Icon(Icons.Default.Check, null, tint = Green) },
                                onClick = { categoryFilter = option; categoryMenu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${visible.size} found", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(50.dp).background(LavenderTint, RoundedCornerShape(18.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Income", "Expense").forEach { choice ->
                    val selected = filter == choice
                    val tint = when (choice) { "Income" -> IncomeGreen; "Expense" -> ExpenseRed; else -> Green }
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Color.White else Color.Transparent)
                            .clickable { filter = choice },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (choice != "All") {
                            Icon(if (choice == "Income") Icons.Default.TrendingUp else Icons.Default.TrendingDown, null, tint = if (selected) tint else Muted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(choice, color = if (selected) tint else Muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ReceiptLong, null, tint = Muted, modifier = Modifier.size(34.dp))
                        Text("No transactions found", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("Try another search or change your filters.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        if (query.isNotBlank() || categoryFilter != "All categories" || filter != "All") {
                            TextButton(onClick = { query = ""; categoryFilter = "All categories"; filter = "All" }) { Text("Clear filters") }
                        }
                    }
                }
            }
        }
        grouped.forEach { (date, rows) ->
            item(key = "date-$date") {
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(date, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE7E8F1))
                }
            }
            items(rows, key = { transaction -> transactionKey(transaction) + transaction.hashCode() }) { transaction ->
                TransactionRow(transaction, onLongPress = { onTransactionLongPress(transaction) })
            }
        }
        if (visible.isNotEmpty()) {
            item { Text("Tip: tap or long-press a transaction for available actions.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}

@Composable
private fun ReportsScreen(
    income: Long,
    expenses: Long,
    transactions: List<Transaction>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpen: (String) -> Unit
) {
    var reportView by remember { mutableStateOf("By category") }
    var searchQuery by remember { mutableStateOf("") }
    var showNotifications by remember { mutableStateOf(false) }
    var showAllCategories by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }
    val categories = transactions.filterNot { it.income }.groupBy { it.category }
        .map { it.key to it.value.sumOf { transaction -> transaction.amount } }.sortedByDescending { it.second }
    val totalSpend = categories.sumOf { it.second }
    val tagTotals = transactions.flatMap { transaction ->
        Regex("#([\\p{L}\\p{N}_-]+)").findAll(transaction.notes).map { it.groupValues[1] to transaction.amount }.toList()
    }.groupBy { it.first }.mapValues { entry -> entry.value.sumOf { it.second } }.toList().sortedByDescending { it.second }
    val views = listOf("Calendar", "By category", "By tag", "Total")
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7FC)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF173D70), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Monthly Report", fontSize = 23.sp, color = Color(0xFF102D59), fontWeight = FontWeight.Bold)
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable { showMonthMenu = true }.padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(selectedMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")), color = Color(0xFF49688F), fontSize = 14.sp)
                            Icon(Icons.Default.ExpandMore, contentDescription = "Select month", tint = Color(0xFF49688F), modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showMonthMenu,
                            onDismissRequest = { showMonthMenu = false },
                            modifier = Modifier.heightIn(max = 360.dp).background(Color.White)
                        ) {
                            (0..23).map { selectedMonth.plusMonths(12L - it) }.sortedDescending().forEach { month ->
                                DropdownMenuItem(
                                    text = { Text(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")), color = if (month == selectedMonth) Green else Ink, fontWeight = if (month == selectedMonth) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { onMonthChange(month); showMonthMenu = false },
                                    leadingIcon = if (month == selectedMonth) ({ Icon(Icons.Default.Check, null, tint = Green) }) else null
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Color(0xFF49688F), modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Color(0xFF49688F), modifier = Modifier.size(16.dp)) }
                    }
                }
                IconButton(onClick = { showAllCategories = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Show all categories", tint = Color(0xFF173D70))
                }
                Box {
                    IconButton(onClick = { showNotifications = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color(0xFF173D70))
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 7.dp).size(7.dp).background(Color(0xFFEF4444), CircleShape))
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFE5EFFB), RoundedCornerShape(24.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                views.forEach { view ->
                    val label = when (view) { "By category" -> "Categories"; "By tag" -> "Tags"; else -> view }
                    val tabIcon = when (view) {
                        "Calendar" -> Icons.Default.CalendarToday
                        "By category" -> Icons.Default.Category
                        "By tag" -> Icons.Default.LocalOffer
                        else -> Icons.Default.Functions
                    }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                            .background(if (reportView == view) Color(0xFF6258F5) else Color.Transparent)
                            .clickable { reportView = view }
                            .padding(vertical = 14.dp, horizontal = 3.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(tabIcon, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (reportView == view) Color.White else Color(0xFF49688F))
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontSize = 11.sp, maxLines = 1, color = if (reportView == view) Color.White else Color(0xFF173D70), fontWeight = if (reportView == view) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
        if (reportView == "Overview" || reportView == "Total") {
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Income", Money.format(income), Modifier.weight(1f), IncomeTint, IncomeGreen)
                MetricCard("Expenses", Money.format(expenses), Modifier.weight(1f), ExpenseTint, ExpenseRed)
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Net savings", Money.format(income - expenses), Modifier.weight(1f), LavenderTint, Purple)
                MetricCard("Transactions", transactions.size.toString(), Modifier.weight(1f), SkyTint, Blue)
            } }
        }
        if (reportView == "Overview" || reportView == "Total") {
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Top spending category", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (categories.isEmpty()) Text("No expenses recorded this month.", color = Muted)
                    else {
                        val top = categories.first()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(categoryTint(top.first), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.TrendingUp, null, tint = categoryColor(top.first))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(top.first, color = Ink, fontWeight = FontWeight.Bold)
                                Text(if (totalSpend > 0L) "${top.second * 100 / totalSpend}% of expenses" else "0%", color = Color(0xFF263238), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(Money.format(top.second), color = Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } }
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Income vs expenses", fontWeight = FontWeight.Bold, color = Ink)
                    Text("Income · " + Money.format(income), color = IncomeGreen)
                    LinearProgressIndicator(progress = { if (income + expenses > 0L) income.toFloat() / (income + expenses) else 0f }, modifier = Modifier.fillMaxWidth(), color = IncomeGreen)
                    Text("Expenses · " + Money.format(expenses), color = ExpenseRed)
                    LinearProgressIndicator(progress = { if (income + expenses > 0L) expenses.toFloat() / (income + expenses) else 0f }, modifier = Modifier.fillMaxWidth(), color = ExpenseRed)
                    Text("Net cash flow · " + Money.format(income - expenses), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            } }
        }
        if (reportView == "Calendar") {
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily totals", color = Ink, fontWeight = FontWeight.Bold)
                    var any = false
                    (1..selectedMonth.lengthOfMonth()).forEach { day ->
                        val date = selectedMonth.atDay(day)
                        val daily = transactions.filter { transactionDateOrNull(it) == date }
                        if (daily.isNotEmpty()) {
                            any = true
                            Row(Modifier.fillMaxWidth().background(Page, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")), color = Ink, fontWeight = FontWeight.Medium)
                                    Text("${daily.size} transaction(s)", color = Muted, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("+ ${Money.format(daily.filter { it.income }.sumOf { it.amount })}", color = IncomeGreen, fontSize = 11.sp)
                                    Text("− ${Money.format(daily.filterNot { it.income }.sumOf { it.amount })}", color = ExpenseRed, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    if (!any) Text("No dated transactions in this month yet.", color = Muted, fontSize = 12.sp)
                }
            } }
        }
        if (reportView == "By category") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportMetricCard("Income", Money.format(income), Icons.Default.AccountBalanceWallet, Modifier.weight(1f), Color(0xFFE3F9ED), Color(0xFF00A86B))
                    ReportMetricCard("Expense", Money.format(expenses), Icons.Default.CreditCard, Modifier.weight(1f), Color(0xFFFFEBEB), Color(0xFFFD3C65))
                    ReportMetricCard("Savings", Money.format(income - expenses), Icons.Default.Savings, Modifier.weight(1f), Color(0xFFE6F0FF), Color(0xFF0066FF))
                }
            }
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spending by category", color = Color(0xFF173D70), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(0.86f), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(Modifier.size(126.dp)) {
                                if (totalSpend > 0L) {
                                    var startAngle = -90f
                                    categories.forEach { entry ->
                                        val sweep = entry.second.toFloat() / totalSpend * 360f
                                        drawArc(categoryColor(entry.first), startAngle, sweep, false, style = Stroke(width = size.minDimension * .22f))
                                        startAngle += sweep
                                    }
                                } else drawArc(Mint, 0f, 360f, false, style = Stroke(width = size.minDimension * .22f))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Expense", color = Color(0xFF8094B0), fontSize = 11.sp)
                                Text(Money.format(totalSpend), color = Color(0xFF173D70), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                        }
                        Column(Modifier.weight(1.14f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (categories.isEmpty()) Text("No category totals yet.", color = Muted, fontSize = 12.sp)
                            categories.take(7).forEach { (category, amount) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(Modifier.size(8.dp).background(categoryColor(category), CircleShape))
                                    Text(category, modifier = Modifier.weight(1f), color = Color(0xFF173D70), fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                    Text(if (totalSpend > 0L) "${amount * 100 / totalSpend}%" else "0%", color = Color(0xFF263238), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            } }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    placeholder = { Text("Search categories...", fontSize = 13.sp, color = Color(0xFF8094B0)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8094B0)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF4F8FD),
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = Color(0xFFDCE9F8),
                        focusedBorderColor = Color(0xFF6258F5)
                    )
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Top Spending Categories", modifier = Modifier.weight(1f), color = Color(0xFF173D70), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    TextButton(onClick = { showAllCategories = !showAllCategories }) {
                        Text(if (showAllCategories) "Show less  →" else "See all  →", color = Color(0xFF6258F5), fontSize = 12.sp)
                    }
                }
            }
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    val filteredCategories = categories.filter { it.first.contains(searchQuery, ignoreCase = true) }
                    val visibleCategories = if (showAllCategories) filteredCategories else filteredCategories.take(5)
                    if (visibleCategories.isEmpty()) {
                        Text(if (searchQuery.isBlank()) "No expenses recorded this month yet." else "No matching categories.", color = Muted, fontSize = 12.sp)
                    }
                    visibleCategories.forEachIndexed { index, (category, amount) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(30.dp).background(Color(0xFFEAF2FC), CircleShape), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = Color(0xFF173D70), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Box(Modifier.size(44.dp).background(categoryColor(category), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(
                                    when {
                                        category.contains("food", true) || category.contains("grocer", true) -> Icons.Default.Restaurant
                                        category.contains("transport", true) || category.contains("travel", true) -> Icons.Default.DirectionsCar
                                        category.contains("bill", true) || category.contains("utilit", true) || category.contains("home", true) -> Icons.Default.Home
                                        category.contains("shop", true) -> Icons.Default.ShoppingBag
                                        category.contains("entertain", true) -> Icons.Default.ConfirmationNumber
                                        category.contains("health", true) || category.contains("fitness", true) -> Icons.Default.Favorite
                                        else -> Icons.Default.MoreHoriz
                                    },
                                    contentDescription = category, tint = Color.White
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(category, color = Color(0xFF0E1C36), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                Text(if (totalSpend > 0L) "${amount * 100 / totalSpend}% of expenses" else "0% of expenses", color = Color(0xFF263238), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                            }
                            Text(Money.format(amount).substringBeforeLast(".").takeIf { Money.format(amount).contains(".") } ?: Money.format(amount), color = Color(0xFF0E1C36), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            Text(if (totalSpend > 0L) "${amount * 100 / totalSpend}%" else "0%", color = Color(0xFF263238), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open category", tint = Muted, modifier = Modifier.size(16.dp))
                        }
                        if (index < visibleCategories.lastIndex) HorizontalDivider(color = Page)
                    }
                }
            } }
        }
        if (reportView == "By tag") {
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Totals by tag", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (tagTotals.isEmpty()) Text("No tags yet. Add hashtags to transaction notes, for example #home, #travel or #work.", color = Muted, fontSize = 12.sp)
                    tagTotals.forEach { (tag, amount) ->
                        Row(Modifier.fillMaxWidth().background(categoryTint(tag), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tag, null, tint = categoryColor(tag))
                            Spacer(Modifier.width(8.dp))
                            Text("#$tag", Modifier.weight(1f), color = Ink)
                            Text(Money.format(amount), color = Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } }
        }
        if (reportView == "Total") {
            item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Monthly totals", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    listOf(Triple("Total income", income, IncomeGreen), Triple("Total expenses", expenses, ExpenseRed), Triple("Net savings", income - expenses, Purple)).forEach { row ->
                        Row(Modifier.fillMaxWidth().background(categoryTint(row.first), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.first, Modifier.weight(1f), color = Ink)
                            Text(Money.format(row.second), color = row.third, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } }
        }
    }
    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("Notifications") },
            text = { Text("You're all caught up. New reminders and alerts will appear here when available.") },
            confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun ReportMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color,
    accent: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = tint),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Text(title, color = accent, fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
            Text(
                value.substringBeforeLast(".").takeIf { value.contains(".") } ?: value,
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, tint: Color = Color.White, valueColor: Color = Green) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = tint), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LoansScreen(loans: List<Loan>, onAddLoan: () -> Unit, onDetails: (Loan) -> Unit, onLoanLongPress: (Loan) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Page),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Loans & EMIs", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("Track balances, monthly payments and due dates", color = Muted, fontSize = 12.sp)
                }
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(LavenderTint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalance, null, tint = Green, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAddLoan,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add a loan", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Green),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("TOTAL OUTSTANDING", color = Color.White.copy(alpha = .82f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Text(Money.format(loans.sumOf { it.balance }), fontSize = 29.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color.White.copy(alpha = .22f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Combined monthly EMIs", color = Color.White.copy(alpha = .78f), fontSize = 11.sp)
                            Text(Money.format(loans.sumOf { it.emi }), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Loans tracked", color = Color.White.copy(alpha = .78f), fontSize = 11.sp)
                            Text("${loans.size}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (loans.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(LavenderTint), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalance, null, tint = Green, modifier = Modifier.size(28.dp))
                    }
                    Text("No loans added yet", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Add your first loan to see its balance, monthly EMI and next due date here.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = onAddLoan) { Text("Add your first loan") }
                }
            }
        }
        if (loans.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your loans", fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${loans.size} tracked", fontSize = 11.sp, color = Muted)
                }
            }
        }
        items(loans) { loan ->
            val index = loans.indexOf(loan)
            val iconTint = when (index.mod(4)) {
                1 -> SavingsBlue
                2 -> IncomeGreen
                3 -> Purple
                else -> Green
            }
            val iconBackground = when (index.mod(4)) {
                1 -> SkyTint
                2 -> IncomeTint
                3 -> LavenderTint
                else -> Mint
            }
            Card(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onDetails(loan) },
                    onLongClick = { onLoanLongPress(loan) }
                ),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalance, null, tint = iconTint, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(loan.lender, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text("Loan account", color = Muted, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(Money.format(loan.balance), color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("outstanding", color = Muted, fontSize = 10.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFECECF4))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("MONTHLY EMI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                            Text(Money.format(loan.emi), color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(IncomeTint).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EventRepeat, null, tint = IncomeGreen, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Monthly", color = IncomeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().background(Page, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("NEXT DUE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, null, tint = iconTint, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(loan.due, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ANNUAL RATE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                            Text(if (loan.annualRate > 0.0) "${loan.annualRate}% p.a." else "Not entered", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val progress = if (loan.originalPrincipal > 0L) ((loan.originalPrincipal - loan.balance).toFloat() / loan.originalPrincipal.toFloat()).coerceIn(0f, 1f) else 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Principal repaid · estimate", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${(progress * 100).toInt()}%", color = iconTint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),
                        color = iconTint,
                        trackColor = iconBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Payments recorded: ${loan.paymentsMade}. Progress is estimated from entered balances, not lender-verified.", color = Muted, fontSize = 10.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text("View loan details", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, null, tint = Green, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (loans.isNotEmpty()) item {
            Text("Tap a loan to open its details. Long-press for loan actions.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
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
            Card(colors = CardDefaults.cardColors(containerColor = LavenderTint), shape = RoundedCornerShape(20.dp)) {
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
                val remaining = income - expenses - emi
                val topic = question.trim().lowercase()
                answer = when {
                    income <= 0L -> "Start by recording your monthly take-home income, essential expenses and loan EMIs. Once those are tracked, this coach can calculate a more useful monthly estimate."
                    topic.contains("loan") || topic.contains("emi") || topic.contains("interest") || topic.contains("debt") ->
                        "Your tracked monthly EMI total is ${Money.format(emi)} and your estimated amount after expenses and EMIs is ${Money.format(remaining)}. Compare each loan's APR, remaining tenure and foreclosure charges. Keep required payments current and direct extra repayment only after checking fees and maintaining an emergency buffer."
                    topic.contains("save") || topic.contains("saving") || topic.contains("budget") ->
                        "Your tracked income is ${Money.format(income)}, expenses are ${Money.format(expenses)}, and EMIs are ${Money.format(emi)}. Estimated monthly remainder: ${Money.format(remaining)}. Set a realistic savings target from this remainder, reserve essential bills first, and review the largest spending categories weekly."
                    topic.contains("expense") || topic.contains("spend") || topic.contains("category") || topic.contains("cut") ->
                        "Your tracked expenses total ${Money.format(expenses)} and EMIs total ${Money.format(emi)} against income of ${Money.format(income)}. Estimated remainder: ${Money.format(remaining)}. Review category totals for flexible spending first; avoid cutting essential healthcare, housing or minimum debt payments."
                    else ->
                        "Your tracked income is ${Money.format(income)}, expenses are ${Money.format(expenses)}, and EMIs are ${Money.format(emi)}. Estimated monthly remainder: ${Money.format(remaining)}. Record transactions consistently, review your monthly report, and keep an emergency buffer before making extra loan payments."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Get guidance") }
        }
        if (answer.isNotBlank()) item {
            Text(answer, Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp), color = Ink)
        }
        item {
            InfoCard(
                "Live AI service: Not connected",
                "These answers are generated locally from tracked totals. They are not live AI responses or current lender quotes. A future connected service must show its provider and update time, protect API keys on a trusted backend, and ask before sharing financial details.",
                Icons.Default.CloudOff
            )
        }
        item { Text("Guidance is informational, not regulated financial advice.", color = Muted, fontSize = 11.sp) }
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
    val selectedTransactions = monthTransactions.filter { transactionDateOrNull(it) == selectedDate }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {                        IconButton(onClick = {
                            val nextMonth = visibleMonth.minusMonths(1)
                            visibleMonth = nextMonth
                            selectedDate = nextMonth.atDay(1)
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Green)
                        }
                        Text(visibleMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + visibleMonth.year,
                            Modifier.weight(1f), textAlign = TextAlign.Center, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = {
                            val nextMonth = visibleMonth.plusMonths(1)
                            visibleMonth = nextMonth
                            selectedDate = nextMonth.atDay(1)
                        }) {
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
private fun BudgetsScreen(transactions: List<Transaction>) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var budgets by remember {
        mutableStateOf(runCatching {
            val json = JSONObject(preferences.getString("monthly_category_budgets", "{}") ?: "{}")
            buildMap { val keys = json.keys(); while (keys.hasNext()) { val key = keys.next(); put(key, json.optLong(key, 0L)) } }
        }.getOrDefault(emptyMap()))
    }
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val expenses = transactions.filterNot { it.income }.filter { transactionBelongsToMonth(it, YearMonth.now()) }
        .groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
    fun persist(values: Map<String, Long>) {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value) }
        preferences.edit().putString("monthly_category_budgets", json.toString()).apply()
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Monthly budgets", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { Text("Set category limits and compare them with dated expenses in the current month.", color = Muted) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit) }, label = { Text("Monthly limit (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = {
                        val name = category.trim()
                        val limit = amount.toLongOrNull()
                        if (name.isBlank() || limit == null || limit <= 0L) message = "Enter a category and a positive monthly limit."
                        else {
                            budgets = budgets + (name to limit)
                            persist(budgets)
                            message = "Budget saved for $name."
                            category = ""; amount = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save budget") }
                    if (message.isNotBlank()) Text(message, color = Green, fontSize = 12.sp)
                }
            }
        }
        if (budgets.isEmpty()) item { InfoCard("No budgets yet", "Add a category and monthly spending limit above to start tracking.", Icons.Default.Savings) }
        items(budgets.keys.sorted(), key = { it }) { name ->
            val limit = budgets[name] ?: 0L
            val spent = expenses[name] ?: 0L
            val ratio = if (limit > 0L) spent.toFloat() / limit else 0f
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                        Text("${(ratio * 100).toInt()}%", color = if (ratio >= 1f) ExpenseRed else Green, fontWeight = FontWeight.Bold)
                    }
                    Text("Spent ${Money.format(spent)} of ${Money.format(limit)}", color = Muted, fontSize = 12.sp)
                    LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (ratio >= 1f) ExpenseRed else Green, trackColor = Mint)
                    if (ratio >= 1f) Text("Budget exceeded by ${Money.format(spent - limit)}", color = ExpenseRed, fontSize = 12.sp)
                    TextButton(onClick = { budgets = budgets - name; persist(budgets) }) { Text("Remove budget", color = ExpenseRed) }
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(income: Long, expenses: Long, debt: Long, transactions: List<Transaction>) {
    val categoryTotals = transactions.filterNot { it.income }.filter { transactionBelongsToMonth(it, YearMonth.now()) }
        .groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }.take(5)
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
        item {
            Text("Top spending categories this month", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (categoryTotals.isEmpty()) Text("Add dated expenses to see category insights.", color = Muted)
            categoryTotals.forEach { (category, amount) ->
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category, Modifier.weight(1f), color = Ink)
                        Text(Money.format(amount), color = Ink, fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(progress = { (amount.toFloat() / categoryTotals.maxOf { it.second }.coerceAtLeast(1L)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Green, trackColor = Mint)
                }
            }
        }
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
        "Food", "Food/Dining out", "Healthcare", "Income", "Leisure/Travel",
        "Savings/Emergency", "Transport/Fuel", "Transport/Taxi", "Utilities/Gas",
        "Utilities/Phone", "Food/Coffee", "Food/Takeout", "Household", "Leisure",
        "Other", "Savings/Investment", "Transport", "Transport/Public transit",
        "Utilities/Electricity", "Utilities/Internet", "Utilities/Water"
    )
    val initialCategories = remember {
        runCatching {
            val raw = preferences.getString("custom_categories", null)
            val saved = if (raw.isNullOrBlank()) emptyList() else {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.getString(it).trim() }.filter { it.isNotBlank() }
            }
            (defaultCategories + saved).distinctBy { it.lowercase(Locale.ROOT) }
        }.getOrDefault(defaultCategories)
    }
    var categories by remember { mutableStateOf(initialCategories) }
    var message by remember { mutableStateOf("") }
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
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Category Management", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("Manage the categories used to organize your money", color = Muted)
                Spacer(Modifier.height(8.dp))
            }
            if (message.isNotBlank()) item { Text(message, color = Green, fontSize = 12.sp) }
            items(categories, key = { it }) { category ->
                val tint = categoryColors[categories.indexOf(category).mod(categoryColors.size)]
                Row(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp))
                        .padding(start = 13.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
            item {
                Text(
                    "Categories are saved on this device and are available in Add Transaction.",
                    color = Muted, fontSize = 11.sp
                )
            }
        }
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { draft = ""; editing = null; showAdd = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Category")
                }
                Button(
                    onClick = {
                        persistCategories(categories)
                        message = "Categories saved successfully."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) { Text("Confirm") }
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (editing == null) "Add Category" else "Edit Category") },
            text = { OutlinedTextField(draft, { draft = it }, label = { Text("Category name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = draft.trim()
                    if (name.isNotBlank()) {
                        val duplicate = categories.any { it.equals(name, ignoreCase = true) && it != editing }
                        if (duplicate) {
                            message = "That category already exists."
                        } else {
                            categories = if (editing == null) categories + name else categories.map { if (it == editing) name else it }
                                .distinctBy { it.lowercase(Locale.ROOT) }
                            persistCategories(categories)
                            message = ""
                            showAdd = false
                        }
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
            text = { Text("Remove \"$category\" from this category list? Existing transactions keep their original labels.") },
            confirmButton = {
                TextButton(onClick = {
                    categories = categories - category
                    persistCategories(categories)
                    pendingDelete = null
                }) { Text("Delete", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsScreen(onOpen: (String) -> Unit) {
    val entries = listOf(
        "Categories" to Icons.Default.Label,
        "Budgets" to Icons.Default.AccountBalanceWallet,
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
        item { InfoCard("Preferences", "Premium indigo accents, soft colourful cards and light surfaces. Transactions and loans remain saved locally on this device. Open Appearance to save display preferences.", Icons.Default.Tune) }
        item { Button(onClick = { onOpen("Categories") }, modifier = Modifier.fillMaxWidth()) { Text("Category management") } }
        item { OutlinedButton(onClick = { onOpen("Budgets") }, modifier = Modifier.fillMaxWidth()) { Text("Monthly category budgets") } }
        item { OutlinedButton(onClick = { onOpen("Appearance") }, modifier = Modifier.fillMaxWidth()) { Text("Settings appearance") } }
        item { OutlinedButton(onClick = { onOpen("Backup & security") }, modifier = Modifier.fillMaxWidth()) { Text("Backup & security") } }
        item { OutlinedButton(onClick = { onOpen("CRED sync") }, modifier = Modifier.fillMaxWidth()) { Text("Live payment tracking") } }
        item { OutlinedButton(onClick = { onOpen("Import transactions") }, modifier = Modifier.fillMaxWidth()) { Text("Import bank CSV") } }
        item { OutlinedButton(onClick = { onOpen("Credit health") }, modifier = Modifier.fillMaxWidth()) { Text("CIBIL & credit health") } }
        item { OutlinedButton(onClick = { onOpen("Loan details") }, modifier = Modifier.fillMaxWidth()) { Text("Loan details & EMI schedule") } }
        item { Button(onClick = { onOpen("Add loan") }, modifier = Modifier.fillMaxWidth()) { Text("+ Add loan") } }
        items(entries) { (label, icon) ->
            val entryAccent = when (label) { "Categories" -> Purple; "Budgets" -> IncomeGreen; "Calendar" -> SavingsBlue; "Insights" -> Orange; "Credit health" -> ExpenseRed; "Appearance" -> Purple; else -> Green }
            val entryTint = when (label) { "Categories", "Appearance" -> LavenderTint; "Budgets" -> IncomeTint; "Calendar" -> SkyTint; "Insights" -> PeachTint; "Credit health" -> ExpenseTint; else -> Color.White }
            Row(
                Modifier.fillMaxWidth().background(entryTint, RoundedCornerShape(14.dp)).clickable { onOpen(label) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = entryAccent)
                Spacer(Modifier.width(12.dp))
                Text(label, Modifier.weight(1f), color = Ink)
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        item { Text("MoneyLog · Finance tracker", color = Muted, fontSize = 11.sp) }
    }
}


@Composable
private fun CreditHealthScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var scoreText by remember { mutableStateOf(preferences.getInt("user_reported_credit_score", 0).takeIf { it in 300..900 }?.toString() ?: "") }
    var scoreDraft by remember { mutableStateOf(scoreText) }
    var showScoreEditor by remember { mutableStateOf(false) }
    var scoreMessage by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("Credit score") }
    val enteredScore = scoreText.toIntOrNull()?.takeIf { it in 300..900 }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("CIBIL & Credit Health", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item {
            InfoCard(
                "Live bureau status: Not connected",
                "No authorized live CIBIL/bureau API is configured in this build. A score entered below is user-reported, not verified. When an authorized integration is added, show its source and the successful fetch time; never substitute a generated score.",
                Icons.Default.CloudOff
            )
        }
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
                    Text("Your reported credit score", color = Muted, fontSize = 13.sp)
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(154.dp)) {
                        CircularProgressIndicator(
                            progress = { (enteredScore ?: 0).toFloat() / 900f },
                            modifier = Modifier.fillMaxSize(),
                            color = Green,
                            trackColor = Mint,
                            strokeWidth = 11.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(enteredScore?.toString() ?: "—", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("of 900", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Text(if (enteredScore == null) "No score entered" else when {
                        enteredScore >= 750 -> "Strong reported score"
                        enteredScore >= 700 -> "Good reported score"
                        enteredScore >= 650 -> "Fair reported score"
                        else -> "Needs attention"
                    }, color = Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { scoreDraft = scoreText; showScoreEditor = true }, modifier = Modifier.fillMaxWidth()) { Text(if (enteredScore == null) "Enter score from your report" else "Update reported score") }
                    Text("User-reported only. This score is entered from your own credit report and saved on this device; it is not fetched from or verified by TransUnion CIBIL.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    if (scoreMessage.isNotBlank()) Text(scoreMessage, color = Green, fontSize = 12.sp)
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
    if (showScoreEditor) {
        AlertDialog(
            onDismissRequest = { showScoreEditor = false },
            title = { Text("Enter reported credit score") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the score shown on your latest credit report (300–900).", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = scoreDraft,
                        onValueChange = { value -> scoreDraft = value.filter { it.isDigit() }.take(3) },
                        label = { Text("Credit score") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = scoreDraft.toIntOrNull()
                    if (parsed != null && parsed in 300..900) {
                        preferences.edit().putInt("user_reported_credit_score", parsed).apply()
                        scoreText = parsed.toString()
                        scoreMessage = "Score saved on this device."
                        showScoreEditor = false
                    } else {
                        scoreMessage = "Enter a whole-number score from 300 to 900."
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showScoreEditor = false }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun BackupSecurityScreen(
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onExportCsv: () -> Unit,
    message: String
) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Backup & Security", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { InfoCard("Local data", "Transactions and loans are saved in app-private storage and remain after app restarts. Keep an exported backup before changing devices or reinstalling.", Icons.Default.Lock) }
        item { Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Text("Export full backup (JSON)") } }
        item { OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup") } }
        item { OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) { Text("Export transactions (CSV)") } }
        if (message.isNotBlank()) item { InfoCard("Latest operation", message, Icons.Default.Info) }
        item { InfoCard("Cloud backup", "Google Drive / OneDrive and scheduled automatic backups are not connected. Store exported files somewhere private and trusted.", Icons.Default.CloudUpload) }
        item { InfoCard("Privacy", "Backups contain financial records. Keep them private and do not enter banking passwords, card PINs or OTPs into this app.", Icons.Default.PrivacyTip) }
    }
}
@Composable
private fun CredSyncScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var notificationConsent by remember { mutableStateOf(preferences.getBoolean("notification_capture_consent", false)) }
    fun openWeb(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Bank & Credit Integrations", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Selected sources: CRED, ICICI iMobile and CIBIL", color = Muted)
        }
        item { InfoCard("Current connection status", "CRED and ICICI iMobile can be monitored through payment notifications if those apps post readable payment alerts and you enable Android Notification access. This is not a direct bank API connection and may miss or misread alerts. Verified CIBIL reports are not connected yet.", Icons.Default.Info) }
        item {
            Text("1 · CRED + ICICI iMobile alerts", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            InfoCard("What this app can do now", "With your consent, the listener checks notifications for payment-related wording and amounts and saves matching entries locally. It does not sign in to CRED or ICICI, read account balances, fetch complete statements, or verify that a payment settled. Check each captured transaction against your bank record.", Icons.Default.NotificationsActive)
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.Top) {
                Checkbox(checked = notificationConsent, onCheckedChange = { accepted ->
                    notificationConsent = accepted
                    preferences.edit().putBoolean("notification_capture_consent", accepted).apply()
                })
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text("I consent to optional payment notification capture", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Android notification access is system-wide. This app only processes full payment-alert text when the source is identified as CRED or ICICI iMobile; other apps’ notification titles may be checked to identify the source but their full text is not saved. Captured records stay locally. Withdraw consent here and disable Notification access in Android Settings to stop capture.", color = Muted, fontSize = 12.sp)
                }
            }
            Button(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, enabled = notificationConsent, modifier = Modifier.fillMaxWidth()) {
                Text(if (notificationConsent) "Enable Android notification access" else "Agree above to continue")
            }
            OutlinedButton(onClick = {
                notificationConsent = false
                preferences.edit().putBoolean("notification_capture_consent", false).apply()
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Withdraw consent / manage Android access") }
        }
        item {
            Text("2 · Direct bank-data connection", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            InfoCard("CRED and ICICI", "CRED's own Money/Networth feature can use the Account Aggregator framework, but that does not give this app access to CRED's private integrations. ICICI's developer portal is a starting point for approved API projects, not automatic access to a personal iMobile account. A production connection needs provider approval, a compliant secure backend and a separate customer consent flow. Never collect bank passwords, UPI PINs or OTPs.", Icons.Default.AccountBalance)
            OutlinedButton(onClick = { openWeb("https://api-portal.icici.bank.in/") }, modifier = Modifier.fillMaxWidth()) { Text("Open ICICI Bank developer portal") }
            OutlinedButton(onClick = { openWeb("https://www.financialservices.gov.in/account-aggregator-framework") }, modifier = Modifier.fillMaxWidth()) { Text("Read official Account Aggregator framework") }
        }
        item {
            Text("3 · CIBIL Insights / current report", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            InfoCard("Verified report requirement", "To show a current verified CIBIL score/report inside this app, the app owner needs TransUnion CIBIL's approved consumer-report product/API access, commercial onboarding, production approval and a compliant identity/consent flow. Until that is approved and integrated, this app only stores a score you manually enter; it must not label it as a live or verified CIBIL result.", Icons.Default.VerifiedUser)
            OutlinedButton(onClick = { openWeb("https://apimarketplace.transunioncibil.com/products/credit-data/dtc-consumer-connect-services") }, modifier = Modifier.fillMaxWidth()) { Text("Open official CIBIL Consumer Connect details") }
            OutlinedButton(onClick = { openWeb("https://apimarketplace.transunioncibil.com/getting-started") }, modifier = Modifier.fillMaxWidth()) { Text("Open CIBIL API onboarding") }
        }
        item {
            Text("4 · Statement import and backup", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            InfoCard("Local file access only", "You can import a bank statement CSV or export a backup using Android's document picker. Only the file you select is granted to the app; no broad storage permission is needed. Review imported rows and keep exported financial files private.", Icons.Default.FileUpload)
        }
        item { InfoCard("Important safety rule", "Do not enter bank passwords, UPI PINs, card PINs, OTPs or bureau passwords into this app or send them in chat. Provider API credentials must be kept on a secured backend, never hard-coded into the Android app or public GitHub.", Icons.Default.Lock) }
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
        item {
            Text("Loan Details", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(loan.lender, color = Muted, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OUTSTANDING BALANCE", color = Color.White.copy(alpha = .82f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(Money.format(loan.balance), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Monthly EMI · ${Money.format(loan.emi)}", color = Color.White)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("Interest rate", "${loan.annualRate}%", Modifier.weight(1f), tint = Color(0xFFFFF0E5), valueColor = Color(0xFFB75B16))
                MetricCard("EMIs recorded", loan.paymentsMade.toString(), Modifier.weight(1f), tint = Color(0xFFE6F0FF), valueColor = SavingsBlue)
            }
        }
        item { MetricCard("Original principal", Money.format(loan.originalPrincipal), Modifier.fillMaxWidth()) }
        item {
            val progress = if (loan.originalPrincipal > 0L) ((loan.originalPrincipal - loan.balance).toFloat() / loan.originalPrincipal.toFloat()).coerceIn(0f, 1f) else 0f
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Principal reduction estimate · ${(progress * 100).toInt()}%", color = Ink, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Green, trackColor = Mint)
                Text("Estimated from original principal and current outstanding balance. Payment count is user-entered; this is not a lender-verified repayment record.", color = Muted, fontSize = 11.sp)
            }
        }
        item { InfoCard("Estimated schedule", "Based on the current outstanding balance, entered EMI and interest rate. This is an estimate, not your lender's official schedule; verify exact figures and due dates against your loan statement.", Icons.Default.CalendarMonth) }
        if (paymentTooLow) item {
            InfoCard("EMI may not cover interest", "At the entered interest rate, the EMI is not greater than the estimated monthly interest. The balance may not reduce unless the EMI or rate changes.", Icons.Default.Warning)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EMI Schedule", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("Next 6 installments · estimate", fontSize = 12.sp, color = Muted)
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(LavenderTint).padding(horizontal = 11.dp, vertical = 7.dp)) {
                    Text("ESTIMATE", color = Color(0xFF5A62B5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("Schedule status is unconfirmed until you compare each installment with your lender statement. This screen does not automatically mark EMIs as paid, due or overdue.", color = Muted, fontSize = 11.sp) }
        item {
            Row(Modifier.fillMaxWidth().background(Color(0xFFEAF0ED), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 11.dp)) {
                Text("INSTALLMENT", Modifier.weight(1.25f), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("INTEREST", Modifier.weight(1f), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("PRINCIPAL", Modifier.weight(1f), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(schedule.indices.toList()) { index ->
            val (interest, principalPart, closingBalance) = schedule[index]
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).combinedClickable(onClick = { onEmiLongPress() }, onLongClick = { onEmiLongPress() }).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (index == 0) loan.due else "Month ${index + 1}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Closing ${Money.format(closingBalance)}", color = Muted, fontSize = 10.sp)
                }
                Text(Money.format(interest), Modifier.weight(1f), color = ExpenseRed, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(Money.format(principalPart), Modifier.weight(1f), color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
private fun AddLoanScreen(onSave: (String, Long, Long, Long, Double, Long) -> Unit) {
    var lender by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var outstanding by remember { mutableStateOf("") }
    var paymentsMade by remember { mutableStateOf("0") }
    var rate by remember { mutableStateOf("12") }
    var emi by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(LavenderTint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccountBalance, null, tint = Green, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Add Loan", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("Set up your loan and EMI tracking", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("Loan details", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(lender, { lender = it; error = ""; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Lender / Bank") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(principal, { principal = it.filter(Char::isDigit); if (outstanding.isBlank()) outstanding = principal; error = ""; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Original principal (₹)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(outstanding, { outstanding = it.filter(Char::isDigit); error = ""; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Current outstanding (₹)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(paymentsMade, { paymentsMade = it.filter(Char::isDigit); error = ""; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("EMI payments made (count)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' }; error = ""; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Annual interest rate (%)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(emi, { emi = it.filter(Char::isDigit); error = ""; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Monthly EMI (₹)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Button(onClick = {
                        val original = principal.toLongOrNull() ?: 0L
                        val amount = outstanding.toLongOrNull() ?: original
                        val count = paymentsMade.toLongOrNull() ?: 0L
                        val monthly = emi.toLongOrNull() ?: 0L
                        val annualRate = rate.toDoubleOrNull() ?: -1.0
                        if (lender.isBlank() || original <= 0 || amount < 0 || amount > original || count < 0 || monthly <= 0 || annualRate < 0) {
                            error = "Check lender, principal, balance, EMI count, interest rate and monthly EMI."
                            saved = false
                        } else {
                            onSave(lender.trim(), original, amount, count, annualRate, monthly)
                            error = ""
                            saved = true
                        }
                    }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Loan", fontWeight = FontWeight.Bold)
                    }
                    if (saved) Text("Loan saved on this device.", color = IncomeGreen, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionDialog(transaction: Transaction, onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    var title by remember(transaction) { mutableStateOf(transaction.title) }
    var amount by remember(transaction) { mutableStateOf(transaction.amount.toString()) }
    var category by remember(transaction) { mutableStateOf(transaction.category) }
    var date by remember(transaction) { mutableStateOf(transaction.date) }
    var showDatePicker by remember { mutableStateOf(false) }
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
                OutlinedTextField(
                    value = date, onValueChange = {}, readOnly = true,
                    label = { Text("Transaction date") },
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, "Choose date") } },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }, singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                if (showDatePicker) {
                    val initialMillis = runCatching { transactionDateOrNull(transaction)?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis() }.getOrDefault(System.currentTimeMillis())
                    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = { TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { millis -> date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                            showDatePicker = false
                        }) { Text("Select") } },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
                    ) { DatePicker(state = pickerState) }
                }
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
    var originalPrincipal by remember(loan) { mutableStateOf(loan.originalPrincipal.toString()) }
    var paymentsMade by remember(loan) { mutableStateOf(loan.paymentsMade.toString()) }
    var due by remember(loan) { mutableStateOf(loan.due) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit loan / EMI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(lender, { lender = it }, label = { Text("Lender") }, singleLine = true)
                OutlinedTextField(originalPrincipal, { originalPrincipal = it.filter(Char::isDigit) }, label = { Text("Original principal (₹)") }, singleLine = true)
                OutlinedTextField(balance, { balance = it.filter(Char::isDigit) }, label = { Text("Outstanding balance (₹)") }, singleLine = true)
                OutlinedTextField(paymentsMade, { paymentsMade = it.filter(Char::isDigit) }, label = { Text("EMI payments made (count)") }, singleLine = true)
                OutlinedTextField(emi, { emi = it.filter(Char::isDigit) }, label = { Text("Monthly EMI (₹)") }, singleLine = true)
                OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Annual interest (%)") }, singleLine = true)
                OutlinedTextField(due, { due = it }, label = { Text("Next due date") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedBalance = balance.toLongOrNull()
                val parsedPrincipal = originalPrincipal.toLongOrNull()
                val parsedPayments = paymentsMade.toLongOrNull()
                val parsedEmi = emi.toLongOrNull()
                val parsedRate = rate.toDoubleOrNull()
                if (lender.isBlank() || parsedBalance == null || parsedBalance < 0L || parsedPrincipal == null || parsedPrincipal <= 0L || parsedBalance > parsedPrincipal || parsedPayments == null || parsedPayments < 0L || parsedEmi == null || parsedEmi <= 0L || parsedRate == null || parsedRate < 0.0) error = "Check lender, principal, outstanding balance, payment count, EMI and rate."
                else onSave(loan.copy(lender = lender.trim(), balance = parsedBalance, originalPrincipal = parsedPrincipal, paymentsMade = parsedPayments, emi = parsedEmi, annualRate = parsedRate, due = due.ifBlank { "Not set" }))
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    initialIncome: Boolean = false,
    onSave: (String, Long, Boolean, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val context = LocalContext.current
    val defaultCategories = listOf("Food & groceries", "Housing", "Transport", "Bills & utilities", "Health", "Shopping", "Entertainment", "Education", "Loan repayment", "Salary", "Other income")
    val savedCategories = remember {
        runCatching {
            val raw = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE).getString("custom_categories", null)
            if (raw.isNullOrBlank()) defaultCategories else {
                val array = JSONArray(raw)
                val saved = (0 until array.length()).map { array.getString(it).trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
                saved.ifEmpty { defaultCategories }
            }
        }.getOrDefault(defaultCategories)
    }
    var isIncome by remember { mutableStateOf(initialIncome) }
    var category by remember { mutableStateOf(savedCategories.firstOrNull { it == "Food & groceries" } ?: "Other") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Page,
        titleContentColor = Ink,
        textContentColor = Ink,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Add transaction", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Record income or an expense", fontSize = 12.sp, color = Muted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isIncome, onClick = { isIncome = false; if (category in listOf("Salary", "Other income", "Income")) category = savedCategories.firstOrNull { it !in listOf("Salary", "Other income", "Income") } ?: "Other" }, label = { Text("Expense", fontWeight = FontWeight.SemiBold) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ExpenseTint, selectedLabelColor = ExpenseRed), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                    FilterChip(selected = isIncome, onClick = { isIncome = true; if (category !in listOf("Salary", "Other income", "Income")) category = "Salary" }, label = { Text("Income", fontWeight = FontWeight.SemiBold) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IncomeTint, selectedLabelColor = IncomeGreen), modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                }
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount (₹)") }, singleLine = true, shape = RoundedCornerShape(14.dp), textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
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
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        (if (isIncome) listOf("Salary", "Other income") else savedCategories.filterNot { it in listOf("Salary", "Other income", "Income") }).distinct().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { category = option; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = date, onValueChange = {}, readOnly = true,
                    label = { Text("Transaction date") },
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.CalendarMonth, "Choose date") } },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }, singleLine = true
                )
                if (showDatePicker) {
                    val initialMillis = runCatching { java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = { TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { millis -> date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                            showDatePicker = false
                        }) { Text("Select") } },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
                    ) { DatePicker(state = pickerState) }
                }
                Text("Payment method", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("UPI", "Cash", "Card", "Bank").forEach { method ->
                        FilterChip(selected = paymentMethod == method, onClick = { paymentMethod = method }, label = { Text(method) })
                    }
                }
                OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes (optional; use #tags for reports)") }, minLines = 2, shape = RoundedCornerShape(14.dp))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
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
                    onSave(title.trim(), parsed, isIncome, category.ifBlank { "Other" }, date, paymentMethod, notes.trim())
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = if (isIncome) IncomeGreen else Green), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save transaction", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}



private fun transactionDateOrNull(transaction: Transaction, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate? {
    val raw = transaction.date.trim()
    if (raw.isBlank()) return null
    return runCatching {
        when {
            raw.equals("today", ignoreCase = true) || raw.equals("just now", ignoreCase = true) -> today
            raw.equals("yesterday", ignoreCase = true) -> today.minusDays(1)
            Regex("^\\d{4}-\\d{2}-\\d{2}(?:[T ].*)?$").matches(raw) -> java.time.LocalDate.parse(raw.take(10))
            Regex("^\\d{4}/\\d{1,2}/\\d{1,2}$").matches(raw) -> { val p = raw.split("/"); java.time.LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt()) }
            Regex("^\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}$").matches(raw) -> { val p = raw.split(Regex("[/-]")); java.time.LocalDate.of(p[2].toInt(), p[1].toInt(), p[0].toInt()) }
            Regex("^\\d{1,2} [A-Za-z]{3,9}(?: \\d{4})?$").matches(raw) -> {
                val p = raw.split(" "); val hasYear = p.size == 3; val monthPattern = if (p[1].length > 3) "MMMM" else "MMM"
                val fmt = java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d $monthPattern" + if (hasYear) " yyyy" else "").toFormatter(Locale.ENGLISH)
                val parsed = fmt.parse(raw); val year = if (hasYear) parsed.get(java.time.temporal.ChronoField.YEAR) else today.year
                java.time.LocalDate.of(year, parsed.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), parsed.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
            }
            Regex("^[A-Za-z]{3,9} \\d{1,2}(?:,? \\d{4})?$").matches(raw) -> {
                val normalized = raw.replace(",", ""); val p = normalized.split(" "); val hasYear = p.size == 3; val monthPattern = if (p[0].length > 3) "MMMM" else "MMM"
                val fmt = java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("$monthPattern d" + if (hasYear) " yyyy" else "").toFormatter(Locale.ENGLISH)
                val parsed = fmt.parse(normalized); val year = if (hasYear) parsed.get(java.time.temporal.ChronoField.YEAR) else today.year
                java.time.LocalDate.of(year, parsed.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), parsed.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
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


private fun loadLoans(context: Context): List<Loan>? = runCatching {
    val raw = context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        .getString("loans", null) ?: return null
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val balance = item.getLong("balance")
        Loan(
            lender = item.getString("lender"),
            balance = balance,
            emi = item.getLong("emi"),
            due = item.optString("due", "Not set"),
            annualRate = item.optDouble("annualRate", 0.0),
            originalPrincipal = item.optLong("originalPrincipal", balance),
            paymentsMade = item.optLong("paymentsMade", 0L)
        )
    }
}.getOrNull()

private fun saveLoans(context: Context, loans: List<Loan>) {
    val array = JSONArray()
    loans.forEach { loan ->
        array.put(JSONObject()
            .put("lender", loan.lender)
            .put("balance", loan.balance)
            .put("emi", loan.emi)
            .put("due", loan.due)
            .put("annualRate", loan.annualRate)
            .put("originalPrincipal", loan.originalPrincipal)
            .put("paymentsMade", loan.paymentsMade))
    }
    context.getSharedPreferences("open_finance_data", Context.MODE_PRIVATE)
        .edit().putString("loans", array.toString()).apply()
}

private fun csvEscape(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value

@Composable
private fun ImportTransactionsScreen(
    message: String,
    previewCount: Int,
    onChooseFile: () -> Unit,
    onImport: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Page).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Import transactions", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Choose a bank statement CSV file. Review the detected rows before importing.", color = Muted)
        }
        item {
            InfoCard(
                "CSV import",
                "Use a CSV with columns such as Date, Description/Narration, Amount, and optionally Category or separate Debit and Credit columns. Duplicate transactions are skipped.",
                Icons.Default.Info
            )
        }
        item {
            Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose CSV file")
            }
        }
        if (message.isNotBlank()) item {
            Text(message, color = Ink)
        }
        item {
            Text("New transactions ready: $previewCount", color = Ink, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onImport,
                enabled = previewCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Import transactions") }
        }
    }
}

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
