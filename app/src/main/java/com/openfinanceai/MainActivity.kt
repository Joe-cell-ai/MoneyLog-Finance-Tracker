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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private data class Transaction(val title: String, val category: String, val date: String, val amount: Long, val income: Boolean, val source: String = "", val notificationKey: String = "", val captureStatus: String = "", val paymentMethod: String = "Not set", val notes: String = "", val tag: String = "")
private data class Loan(val lender: String, val balance: Long, val emi: Long, val due: String, val annualRate: Double = 0.0, val originalPrincipal: Long = balance, val paymentsMade: Long = 0L)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        listOf(1005, 1008, 1010).forEach { EmiReminderReceiver.cancelReminder(this, it) }
        setContent { FinanceApp() }
    }
}

@Composable
private fun FinanceApp() {
    var tab by remember { mutableStateOf("Home") }
    var selectedOverviewMonth by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var showAddAsIncome by remember { mutableStateOf(false) }
    var categoryRefreshToken by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(loans) {
        loans.forEach { loan ->
            if (loan.due.equals("Set due date", ignoreCase = true) || loan.due.isBlank()) return@forEach
            val dueDay = Regex("^\\s*(\\d{1,2})").find(loan.due)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 28) ?: return@forEach
            val reminderId = 3000 + (loan.lender.lowercase(Locale.ROOT).hashCode() and 0x3fff)
            EmiReminderReceiver.scheduleMonthly(context, loan.lender, loan.emi, dueDay, reminderId)
        }
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
                .put("paymentMethod", t.paymentMethod).put("notes", t.notes).put("tag", t.tag)) }
            val loanArray = JSONArray()
            loans.forEach { l -> loanArray.put(JSONObject().put("lender", l.lender).put("balance", l.balance)
                .put("emi", l.emi).put("due", l.due).put("annualRate", l.annualRate).put("originalPrincipal", l.originalPrincipal).put("paymentsMade", l.paymentsMade)) }
            val prefs = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
            val savedCategories = runCatching { JSONArray(prefs.getString("custom_categories", "[]") ?: "[]") }.getOrDefault(JSONArray())
            val savedTags = runCatching { JSONArray(prefs.getString("custom_tags", "[]") ?: "[]") }.getOrDefault(JSONArray())
            root.put("transactions", transactionArray)
                .put("loans", loanArray)
                .put("categories", savedCategories)
                .put("tags", savedTags)
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
                        j.optString("captureStatus"), j.optString("paymentMethod", "Not set"), j.optString("notes"), j.optString("tag", ""))
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
            root.optJSONArray("tags")?.let { editor.putString("custom_tags", it.toString()) }
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
            topBar = {},
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 3.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    listOf("Home", "Activity", "Reports", "Loans", "Settings").forEach { name ->
                        val icon = when (name) {
                            "Home" -> Icons.Default.Home
                            "Activity" -> Icons.Default.ReceiptLong
                            "Reports" -> Icons.Default.BarChart
                            "Loans" -> Icons.Default.AccountBalance
                            else -> Icons.Default.Settings
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
            Box(Modifier.fillMaxSize().padding(padding).padding(top = 20.dp)) {
                when (tab) {
                    "Home" -> HomeScreen(selectedMonthIncome, selectedMonthExpenses, transactions, selectedMonthTransactions, selectedOverviewMonth, { selectedOverviewMonth = it }, { tab = "Activity" }, { isIncome -> showAddAsIncome = isIncome; showAdd = true }, { tab = it }, { transactionForActions = it })
                    "Activity" -> ActivityScreen(transactions, { transactionForActions = it })
                    "Reports" -> ReportsScreen(selectedMonthIncome, selectedMonthExpenses, selectedMonthTransactions, selectedOverviewMonth, { selectedOverviewMonth = it }, { tab = it })
                    "Loans" -> LoansScreen(loans, { tab = "Add loan" }, { loan -> selectedLoan = loan; tab = "Loan details" }, { loanForActions = it })
                    "Settings" -> SettingsScreen { tab = it }
                    "Calendar" -> CalendarScreen(loans, transactions)
                    "Insights" -> InsightsScreen(income, expenses, loans.sumOf { it.balance }, transactions)
                    "Budgets" -> BudgetsScreen(transactions)
                    "Credit health" -> CreditHealthScreen()
                    "Categories" -> CategoriesScreen(onCategoriesChanged = { categoryRefreshToken++ })
                    "Tag Management" -> TagManagementScreen()
                    "Backup & Export" -> BackupSecurityScreen(
                        onExportBackup = { backupExportLauncher.launch("open-finance-backup.json") },
                        onRestoreBackup = { backupRestoreLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExportCsv = { csvExportLauncher.launch("open-finance-transactions.csv") },
                        message = importMessage
                    )
                    "Import transactions" -> ImportTransactionsScreen(importMessage, importedPreview.size, { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, {
                        transactions = (importedPreview + transactions).distinctBy { transactionKey(it) }
                        saveTransactions(context, transactions)
                        importMessage = "Imported ${importedPreview.size} transaction(s). Saved on this device."
                        importedPreview = emptyList()
                    })
                    "Appearance" -> AppearanceScreen()
                    "Privacy & Security" -> PrivacySecurityScreen { tab = "CRED sync" }
                    "Notifications" -> NotificationsSettingsScreen { tab = "CRED sync" }
                    "About" -> AboutSettingsScreen()
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
                TransactionDetailsDialog(
                    transaction = transaction,
                    allTransactions = transactions,
                    onDismiss = { transactionForActions = null },
                    onEdit = { transactionToEdit = transaction; transactionForActions = null },
                    onDelete = {
                        val index = transactions.indexOfFirst { it === transaction }
                        if (index >= 0) {
                            transactions = transactions.toMutableList().also { it.removeAt(index) }
                            saveTransactions(context, transactions)
                        }
                        transactionForActions = null
                    },
                    onDuplicate = {
                        val copy = transaction.copy(title = if (transaction.title.endsWith(" (copy)")) transaction.title else "${transaction.title} (copy)")
                        transactions = listOf(copy) + transactions
                        saveTransactions(context, transactions)
                        transactionForActions = null
                    },
                    onTips = { transactionTips = transaction; transactionForActions = null }
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
                        EmiReminderReceiver.cancelMonthly(context, loan.lender)
                        loans = loans.toMutableList().also { list -> val index = list.indexOfFirst { it === loan }; if (index >= 0) list.removeAt(index) }
                        saveLoans(context, loans)
                        loanForActions = null
                    }) { Text("Delete", color = ExpenseRed) }; TextButton(onClick = { loanForActions = null }) { Text("Cancel") } } }
                )
            }
            loanToEdit?.let { loan ->
                EditLoanDialog(loan, onDismiss = { loanToEdit = null }) { edited ->
                    if (!edited.lender.equals(loan.lender, ignoreCase = true)) EmiReminderReceiver.cancelMonthly(context, loan.lender)
                    loans = loans.toMutableList().also { list -> val index = list.indexOfFirst { it === loan }; if (index >= 0) list[index] = edited }
                    saveLoans(context, loans)
                    loanToEdit = null
                }
            }
            if (showAdd) {
                AddTransactionDialog(
                    onDismiss = { showAdd = false },
                    initialIncome = showAddAsIncome,
                    categoryRefreshToken = categoryRefreshToken,
                    onSave = { title, amount, isIncome, category, date, paymentMethod, notes, tag ->
                        transactions = listOf(Transaction(title, category, date, amount, isIncome, paymentMethod = paymentMethod, notes = notes, tag = tag)) + transactions
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
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var categoryDetailTab by remember { mutableIntStateOf(0) }
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
                            Modifier.fillMaxWidth().clickable { selectedCategory = category }.padding(vertical = 10.dp),
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
                            Icon(Icons.Default.Label, null, tint = categoryColor(tag))
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
    selectedCategory?.let { category ->
        val categoryTransactions = transactions.filter { !it.income && it.category == category }
        val categoryTotal = categoryTransactions.sumOf { it.amount }
        val categoryShare = if (totalSpend > 0L) categoryTotal * 100 / totalSpend else 0L
        val averageSpend = if (categoryTransactions.isNotEmpty()) categoryTotal / categoryTransactions.size else 0L
        val topPlaces = categoryTransactions.groupBy { it.title.ifBlank { "Other" } }
            .map { it.key to it.value.sumOf { transaction -> transaction.amount } }
            .sortedByDescending { it.second }.take(4)
        AlertDialog(
            onDismissRequest = { selectedCategory = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(44.dp).background(categoryColor(category), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Icon(
                                if (category.contains("food", true) || category.contains("dining", true) || category.contains("grocer", true)) Icons.Default.Restaurant
                                else if (category.contains("transport", true) || category.contains("travel", true)) Icons.Default.DirectionsCar
                                else Icons.Default.Category,
                                contentDescription = null, tint = Color.White
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(category, color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text("${Money.format(categoryTotal)}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                            Text("${categoryShare}% of total expenses", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F6FC)), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text("Transactions", color = Muted, fontSize = 11.sp)
                                Text("${categoryTransactions.size}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                        }
                        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F6FC)), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text("Avg. per transaction", color = Muted, fontSize = 11.sp)
                                Text(Money.format(averageSpend), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                        }
                    }
                }
            },
            text = {
                Column(Modifier.heightIn(max = 420.dp)) {
                    TabRow(
                        selectedTabIndex = categoryDetailTab,
                        containerColor = Color(0xFFEAF2F8),
                        contentColor = Color(0xFF087F68),
                        indicator = {}
                    ) {
                        Tab(selected = categoryDetailTab == 0, onClick = { categoryDetailTab = 0 }, text = { Text("Transactions", fontSize = 12.sp) })
                        Tab(selected = categoryDetailTab == 1, onClick = { categoryDetailTab = 1 }, text = { Text("Insights", fontSize = 12.sp) })
                    }
                    Spacer(Modifier.height(10.dp))
                    if (categoryDetailTab == 0) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (categoryTransactions.isEmpty()) {
                                Text("No transactions in this category for the selected month.", color = Muted, fontSize = 12.sp)
                            } else {
                                categoryTransactions.take(8).forEach { transaction ->
                                    TransactionRow(transaction, onLongPress = { onOpen("Transactions") })
                                }
                                if (categoryTransactions.size > 8) {
                                    Text("Showing 8 of ${categoryTransactions.size} transactions", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE9F8F3)), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF07866D))
                                        Text("Spending insights", color = Ink, fontWeight = FontWeight.Bold)
                                    }
                                    Text("You spent ${categoryShare}% of your recorded expenses in $category.", color = Ink, fontSize = 12.sp)
                                    Text("${categoryTransactions.size} transactions · average ${Money.format(averageSpend)} each", color = Muted, fontSize = 12.sp)
                                }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Top spending places", color = Ink, fontWeight = FontWeight.Bold)
                                    if (topPlaces.isEmpty()) {
                                        Text("Add transactions to see your top spending places.", color = Muted, fontSize = 12.sp)
                                    } else {
                                        topPlaces.forEach { place ->
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(place.first, Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1)
                                                    Text(Money.format(place.second), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                }
                                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEAF0F6))) {
                                                    Box(Modifier.fillMaxWidth(if (categoryTotal > 0L) place.second.toFloat() / categoryTotal else 0f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(categoryColor(category)))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0EEFF)), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6558D3))
                                        Text("Suggestions", color = Ink, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        if (categoryTransactions.size >= 8) "Review your most frequent purchases and set a monthly limit for $category."
                                        else "Keep recording purchases in $category to build a more useful spending pattern.",
                                        color = Ink, fontSize = 12.sp
                                    )
                                    Text(
                                        if (averageSpend > 0L) "Your average transaction is ${Money.format(averageSpend)}. Compare similar purchases and look for lower-cost alternatives."
                                        else "Once transactions are recorded, MoneyLog can suggest practical ways to save.",
                                        color = Ink, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedCategory = null; categoryDetailTab = 0 }) { Text("Done") } }
        )
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
    val canvas = Color(0xFFF4F7FC)
    val navy = Color(0xFF0E1C36)
    val slate = Color(0xFF7A869A)
    val forest = Color(0xFF006644)
    val green = Color(0xFF00A86B)
    val greenTint = Color(0xFFE3F9ED)
    val orange = Color(0xFFFF7A00)
    val orangeTint = Color(0xFFFFF3E5)
    val accents = listOf(Color(0xFFEF4444), Color(0xFFFF7A00), Color(0xFF6366F1), Color(0xFF00A86B), Color(0xFFB84DDB), Color(0xFF0891B2))
    val tints = listOf(Color(0xFFFFE8EA), Color(0xFFFFF0DF), Color(0xFFEEEEFF), Color(0xFFE3F9ED), Color(0xFFF9E8FF), Color(0xFFE2F7FA))
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(canvas),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Loan Tracker", fontSize = 26.sp, color = navy, fontWeight = FontWeight.Bold)
                    Text("Your loans, clearly under control", color = slate, fontSize = 12.sp)
                }
                Button(
                    onClick = onAddLoan,
                    colors = ButtonDefaults.buttonColors(containerColor = forest, contentColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add Loan", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("TOTAL OUTSTANDING", color = slate, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                        Text(Money.format(loans.sumOf { it.balance }), color = navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("${loans.size} loans tracked", color = slate, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("MONTHLY COMMITMENT", color = slate, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(Money.format(loans.sumOf { it.emi }), color = forest, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (loans.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(greenTint), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountBalance, null, tint = forest, modifier = Modifier.size(28.dp))
                    }
                    Text("No loans added yet", color = navy, fontWeight = FontWeight.Bold)
                    Text("Add your first loan to track its EMI, repayment progress and outstanding balance.", color = slate, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Button(onClick = onAddLoan, colors = ButtonDefaults.buttonColors(containerColor = forest)) { Text("Add Loan") }
                }
            }
        }
        if (loans.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your loans", fontSize = 18.sp, color = navy, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${loans.size} total", fontSize = 11.sp, color = slate)
                }
            }
        }
        items(loans) { loan ->
            val index = loans.indexOf(loan)
            val accent = accents[index.mod(accents.size)]
            val tint = tints[index.mod(tints.size)]
            val progress = if (loan.originalPrincipal > 0L) ((loan.originalPrincipal - loan.balance).toFloat() / loan.originalPrincipal.toFloat()).coerceIn(0f, 1f) else 0f
            val dueDate = nextLoanDueDate(loan.due)
            val isUpcoming = dueDate != null && !dueDate.isBefore(java.time.LocalDate.now()) &&
                java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), dueDate) <= 7
            Card(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onDetails(loan) },
                    onLongClick = { onLoanLongPress(loan) }
                ),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalance, null, tint = accent, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(loan.lender, color = navy, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Text("${Money.format(loan.originalPrincipal)}  •  ${if (loan.annualRate > 0.0) "${loan.annualRate}%" else "Rate not set"}", color = slate, fontSize = 11.sp, maxLines = 1)
                        }
                        IconButton(onClick = { onLoanLongPress(loan) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Loan actions", tint = slate)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(Money.format(loan.emi), color = navy, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("/month", color = slate, fontSize = 11.sp)
                        }
                        val statusColor = if (isUpcoming) orange else green
                        val statusTint = if (isUpcoming) orangeTint else greenTint
                        Box(Modifier.clip(RoundedCornerShape(50)).background(statusTint).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(if (isUpcoming) Icons.Default.Schedule else Icons.Default.CheckCircle, null, tint = statusColor, modifier = Modifier.size(14.dp))
                                Text(if (isUpcoming) "Upcoming" else "On Track", color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = Color(0xFF00C853),
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${(progress * 100).toInt()}% paid", color = forest, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${Money.format(loan.balance)} left", color = navy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Color(0xFFE9EEF5))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Next due · ${loan.due}", color = slate, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("View details", color = forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ChevronRight, null, tint = forest, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        if (loans.isNotEmpty()) item {
            Text("Tap a card to view details. Use the three-dot menu or long-press for loan actions. Progress is estimated from saved balances.", color = slate, fontSize = 10.sp, modifier = Modifier.padding(vertical = 2.dp))
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
                "Offline guidance",
                "Guidance is calculated on this device from the transactions and loans you enter. MoneyLog does not send your financial data to a live AI service or fetch live lender offers.",
                Icons.Default.Lightbulb
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

private val DEFAULT_MONEY_CATEGORIES = listOf(
    "Food", "Food/Dining out", "Healthcare", "Income", "Leisure/Travel",
    "Savings/Emergency", "Transport/Fuel", "Transport/Taxi", "Utilities/Gas",
    "Utilities/Phone", "Food/Coffee", "Food/Takeout", "Household", "Leisure",
    "Other", "Savings/Investment", "Transport", "Transport/Public transit",
    "Utilities/Electricity", "Utilities/Internet", "Utilities/Water"
)

@Composable
private fun CategoriesScreen(onCategoriesChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    val initialCategories = remember {
        runCatching {
            val raw = preferences.getString("custom_categories", null)
            if (raw.isNullOrBlank()) DEFAULT_MONEY_CATEGORIES else {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.getString(it).trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
            }
        }.getOrDefault(DEFAULT_MONEY_CATEGORIES)
    }
    var categories by remember { mutableStateOf(initialCategories) }
    var message by remember { mutableStateOf("") }
    fun persistCategories(values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        preferences.edit().putString("custom_categories", array.toString()).apply()
        onCategoriesChanged()
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
private fun TagManagementScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    val defaultTags = listOf("Credit Card", "Cash", "Apple Pay", "Amazon", "Google Play", "Debit Card", "PayPal", "Google Pay", "Online")
    val initialTags = remember {
        runCatching {
            val raw = preferences.getString("custom_tags", null)
            val saved = if (raw.isNullOrBlank()) emptyList() else {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.getString(it).trim().removePrefix("#").trim() }.filter { it.isNotBlank() }
            }
            (defaultTags + saved).distinctBy { it.lowercase(Locale.ROOT) }
        }.getOrDefault(defaultTags)
    }
    var tags by remember { mutableStateOf(initialTags) }
    var message by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    fun persistTags(values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        preferences.edit().putString("custom_tags", array.toString()).apply()
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Tag Management", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("Create and manage tags for your transactions", color = Muted)
                Spacer(Modifier.height(8.dp))
            }
            if (message.isNotBlank()) item { Text(message, color = Green, fontSize = 12.sp) }
            items(tags, key = { it }) { tag ->
                Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).padding(start = 13.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(LavenderTint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Label, null, tint = Purple)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(tag, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { draft = tag; editing = tag; showAdd = true }) { Icon(Icons.Default.Edit, "Edit tag", tint = Green) }
                    IconButton(onClick = { pendingDelete = tag }) { Icon(Icons.Default.DeleteOutline, "Delete tag", tint = ExpenseRed) }
                }
            }
            item { Text("Tags are saved on this device and are available in Add Transaction.", color = Muted, fontSize = 11.sp) }
        }
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { draft = ""; editing = null; showAdd = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Tag")
                }
                Button(onClick = { persistTags(tags); message = "Tags saved successfully." }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Confirm") }
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (editing == null) "Add Tag" else "Edit Tag") },
            text = { OutlinedTextField(draft, { draft = it }, label = { Text("Tag name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = draft.trim().removePrefix("#").trim()
                    if (name.isNotBlank()) {
                        val duplicate = tags.any { it.equals(name, ignoreCase = true) && it != editing }
                        if (duplicate) message = "That tag already exists."
                        else {
                            tags = if (editing == null) tags + name else tags.map { if (it == editing) name else it }.distinctBy { it.lowercase(Locale.ROOT) }
                            persistTags(tags)
                            message = ""
                            showAdd = false
                        }
                    }
                }) { Text("Save", color = Green) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
    pendingDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete tag?") },
            text = { Text("Remove \"$tag\" from the tag list? Existing transactions keep their saved tag.") },
            confirmButton = {
                TextButton(onClick = { tags = tags - tag; persistTags(tags); pendingDelete = null }) { Text("Delete", color = ExpenseRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    val mainItems = listOf(
        Triple("Appearance", "Theme, colors and layout", Icons.Default.Palette),
        Triple("Budgets", "Set monthly spending limits", Icons.Default.TrackChanges),
        Triple("Backup & Export", "Save, restore and export records", Icons.Default.Security),
        Triple("Privacy & Security", "Data protection and permissions", Icons.Default.Shield),
        Triple("Notifications", "Reminders and payment capture", Icons.Default.Notifications),
        Triple("Categories", "Manage transaction categories", Icons.Default.Category),
        Triple("Tag Management", "Manage transaction tags", Icons.Default.LocalOffer),
        Triple("About", "App version and information", Icons.Default.Info)
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Page),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF075E4D))
            ) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF0BAA83)), contentAlignment = Alignment.Center) {
                        Text("M", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text("MoneyLog", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Finance Tracker", color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Simple · Secure · Private", color = Color(0xFFB8F3DE), fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Text("Settings", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Make MoneyLog work for you", color = Muted, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    mainItems.forEachIndexed { index, item ->
                        val tint = when (item.first) {
                            "Appearance" -> Purple
                            "Categories" -> Blue
                            "Tag Management" -> IncomeGreen
                            "Budgets" -> Orange
                            "Backup & Export" -> Blue
                            "Privacy & Security" -> Purple
                            "Notifications" -> ExpenseRed
                            else -> Muted
                        }
                        val soft = when (item.first) {
                            "Appearance" -> LavenderTint
                            "Categories" -> SkyTint
                            "Tag Management" -> IncomeTint
                            "Budgets" -> PeachTint
                            "Backup & Export" -> SkyTint
                            "Privacy & Security" -> LavenderTint
                            "Notifications" -> ExpenseTint
                            else -> Page
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(item.first) }.padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(soft), contentAlignment = Alignment.Center) {
                                Icon(item.third, null, tint = tint, modifier = Modifier.size(21.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.first, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(item.second, color = Muted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                        if (index != mainItems.lastIndex) HorizontalDivider(color = Color(0xFFEDEFF4))
                    }
                }
            }
        }
        item {
            Text("More tools", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    listOf(
                        Triple("Import transactions", "Import a bank statement CSV", Icons.Default.FileUpload),
                        Triple("CIBIL & credit health", "View your reported score and guidance", Icons.Default.Speed),
                        Triple("Loan details & EMI schedule", "Review tracked loans and installments", Icons.Default.AccountBalance),
                        Triple("Add loan", "Track a new loan or EMI", Icons.Default.Add)
                    ).forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().clickable {
                            onOpen(when (item.first) {
                                "CIBIL & credit health" -> "Credit health"
                                "Loan details & EMI schedule" -> "Loan details"
                                else -> item.first
                            })
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.third, null, tint = Green, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.first, color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(item.second, color = Muted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                        if (index < 4) HorizontalDivider(color = Color(0xFFEDEFF4))
                    }
                }
            }
        }
        item {
            Text("Your financial records stay on this device unless you choose to export or share a file.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text("MoneyLog · Finance Tracker", color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrivacySecurityScreen(onOpenPaymentSettings: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(Page), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Privacy & Security", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold); Text("Understand how your data is handled", color = Muted, fontSize = 13.sp) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(IncomeTint), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = IncomeGreen) }
                        Spacer(Modifier.width(11.dp))
                        Column { Text("Local data storage", color = Ink, fontWeight = FontWeight.Bold); Text("Transactions and loans stay on this device", color = Muted, fontSize = 11.sp) }
                    }
                    HorizontalDivider(color = Color(0xFFEDEFF4))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(SkyTint), contentAlignment = Alignment.Center) { Icon(Icons.Default.NotificationsActive, null, tint = Blue) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) { Text("Notification access", color = Ink, fontWeight = FontWeight.SemiBold); Text("Required only for optional payment-alert capture", color = Muted, fontSize = 11.sp) }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                    OutlinedButton(onClick = onOpenPaymentSettings, modifier = Modifier.fillMaxWidth()) { Text("Manage payment capture permissions") }
                }
            }
        }
        item { InfoCard("App lock", "PIN and biometric app-lock protection are not implemented in this build. Do not treat the app as locked; protect your device with Android screen lock.", Icons.Default.Fingerprint) }
        item { InfoCard("Backups contain sensitive information", "Create backups only when needed and store exported JSON/CSV files in a trusted private location. MoneyLog does not currently sync backups to a cloud account.", Icons.Default.PrivacyTip) }
        item { InfoCard("Clear app data", "Clearing Android app storage or uninstalling may remove locally saved records. Export a backup first if you need to keep them.", Icons.Default.DeleteOutline) }
    }
}

@Composable
private fun NotificationsSettingsScreen(onOpenPaymentSettings: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var captureInfo by remember { mutableStateOf(preferences.getBoolean("notification_capture_consent", false)) }
    var emiReminder by remember { mutableStateOf(preferences.getBoolean("settings_emi_reminders", true)) }
    var budgetAlerts by remember { mutableStateOf(preferences.getBoolean("settings_budget_alerts", true)) }
    var monthlySummary by remember { mutableStateOf(preferences.getBoolean("settings_monthly_summary", false)) }
    fun persist(key: String, value: Boolean) { preferences.edit().putBoolean(key, value).apply() }
    LazyColumn(modifier = Modifier.fillMaxSize().background(Page), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Notifications", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold); Text("Choose which MoneyLog alerts you want", color = Muted, fontSize = 13.sp) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Transaction capture", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Optional payment-alert capture needs your consent and Android Notification access.", color = Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = Green)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text("Consent to payment capture", color = Ink, fontWeight = FontWeight.Medium); Text(if (captureInfo) "Consent is enabled" else "Consent is off", color = Muted, fontSize = 11.sp) }
                        Switch(checked = captureInfo, onCheckedChange = { value -> captureInfo = value; persist("notification_capture_consent", value) })
                    }
                    Button(onClick = onOpenPaymentSettings, modifier = Modifier.fillMaxWidth()) { Text("Open payment capture settings") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reminders & summaries", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    SettingToggleRow("EMI reminders", "Preference saved on this device", emiReminder) { emiReminder = it; persist("settings_emi_reminders", it) }
                    HorizontalDivider(color = Color(0xFFEDEFF4))
                    SettingToggleRow("Budget alerts", "Preference saved on this device", budgetAlerts) { budgetAlerts = it; persist("settings_budget_alerts", it) }
                    HorizontalDivider(color = Color(0xFFEDEFF4))
                    SettingToggleRow("Monthly summary", "Preference saved on this device", monthlySummary) { monthlySummary = it; persist("settings_monthly_summary", it) }
                }
            }
        }
        item { Text("Reminder toggles save your preferences. Scheduled push notifications for these three options are not yet wired up in this build.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun SettingToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AboutSettingsScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize().background(Page), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) { Text("M", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
                    Text("MoneyLog", color = Ink, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    Text("Finance Tracker", color = Muted)
                    Text("Your money, clearly.", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Text("About MoneyLog", fontSize = 25.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row { Text("App", Modifier.weight(1f), color = Muted); Text("MoneyLog: Finance Tracker", color = Ink, fontWeight = FontWeight.Medium) }
                    HorizontalDivider(color = Color(0xFFEDEFF4))
                    Row { Text("Version", Modifier.weight(1f), color = Muted); Text("1.0.0", color = Ink, fontWeight = FontWeight.Medium) }
                    HorizontalDivider(color = Color(0xFFEDEFF4))
                    Row { Text("Data storage", Modifier.weight(1f), color = Muted); Text("On device", color = Ink, fontWeight = FontWeight.Medium) }
                }
            }
        }
        item { InfoCard("Built for everyday money tracking", "Record income and expenses, manage categories and tags, track loans and EMIs, review budgets, and export your records when needed.", Icons.Default.Favorite) }
        item { InfoCard("Please verify your records", "MoneyLog is a personal tracking tool. Imported transactions and estimated loan schedules should be checked against your bank or lender statements.", Icons.Default.Info) }
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
                "Offline credit health",
                "MoneyLog does not connect to CIBIL or any credit bureau. You can manually enter the score shown on your own report; it remains user-reported and is not verified by this app.",
                Icons.Default.VerifiedUser
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
            item { InfoCard("Check your credit report safely", "Use the official TransUnion CIBIL website to view your report. MoneyLog does not request your bureau password or OTP and does not import or verify a live report.", Icons.Default.OpenInNew) }
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Page),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Backup & Export", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Keep a safe copy of your financial records", color = Muted, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(IncomeTint), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudUpload, null, tint = IncomeGreen, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Back up your data", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Export transactions, loans and settings to a file.", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                        Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Create backup (JSON)")
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(LavenderTint), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Restore, null, tint = Purple, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Restore data", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Load a previous MoneyLog JSON backup.", color = Muted, fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restore from file")
                    }
                }
            }
        }
        item {
            Text("Export data", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onExportCsv).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(SkyTint), contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = Blue) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) { Text("Export as CSV", color = Ink, fontWeight = FontWeight.SemiBold); Text("Transactions for spreadsheets", color = Muted, fontSize = 11.sp) }
                        Icon(Icons.Default.FileDownload, null, tint = Green)
                    }
                    HorizontalDivider(color = Color(0xFFEDEFF4))
                    Row(Modifier.fillMaxWidth().clickable(onClick = onExportBackup).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(IncomeTint), contentAlignment = Alignment.Center) { Icon(Icons.Default.DataObject, null, tint = IncomeGreen) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) { Text("Export as JSON", color = Ink, fontWeight = FontWeight.SemiBold); Text("Full backup · recommended", color = Muted, fontSize = 11.sp) }
                        Icon(Icons.Default.FileDownload, null, tint = Green)
                    }
                }
            }
        }
        if (message.isNotBlank()) item { InfoCard("Latest operation", message, Icons.Default.CheckCircle) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F5F3)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lock, null, tint = Teal)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Your data stays on your device", color = Teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("MoneyLog does not currently upload backups to Google Drive or another cloud service. Exported files contain sensitive financial information—store them somewhere private.", color = Ink, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
@Composable
private fun AppearanceScreen() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(preferences.getString("appearance_theme", "Light") ?: "Light") }
    var currency by remember { mutableStateOf(preferences.getString("appearance_currency", "INR (₹)") ?: "INR (₹)") }
    var accent by remember { mutableStateOf(preferences.getString("appearance_accent", "Green") ?: "Green") }
    var layout by remember { mutableStateOf(preferences.getString("appearance_layout", "Comfortable") ?: "Comfortable") }
    var fontSize by remember { mutableStateOf(preferences.getString("appearance_font_size", "Medium") ?: "Medium") }
    val accents = listOf("Green" to Color(0xFF07966F), "Blue" to Color(0xFF3478F6), "Purple" to Color(0xFF8B4DE8), "Orange" to Color(0xFFF59E0B), "Pink" to Color(0xFFEC4899))
    fun save(key: String, value: String) { preferences.edit().putString(key, value).apply() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Page),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Appearance", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Make MoneyLog comfortable to use", color = Muted, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Light", "Dark", "System").forEach { value ->
                            val selected = theme == value
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                    .background(if (selected) IncomeTint else Page)
                                    .then(if (selected) Modifier.border(1.5.dp, Green, RoundedCornerShape(15.dp)) else Modifier)
                                    .clickable { theme = value; save("appearance_theme", value) }
                                    .padding(vertical = 15.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(when (value) { "Light" -> Icons.Default.LightMode; "Dark" -> Icons.Default.DarkMode; else -> Icons.Default.SettingsBrightness }, null, tint = if (selected) Green else Muted, modifier = Modifier.size(25.dp))
                                Text(value, color = if (selected) Green else Ink, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                if (selected) Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Text("Accent color", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        accents.forEach { (name, color) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { accent = name; save("appearance_accent", name) }) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                                    if (accent == name) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                                Text(name, color = if (accent == name) Ink else Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Layout", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Compact", "Comfortable").forEach { value ->
                            val selected = layout == value
                            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected) IncomeTint else Page)
                                .then(if (selected) Modifier.border(1.dp, Green, RoundedCornerShape(14.dp)) else Modifier)
                                .clickable { layout = value; save("appearance_layout", value) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(if (value == "Compact") Icons.Default.ViewCompact else Icons.Default.ViewAgenda, null, tint = if (selected) Green else Muted)
                                Column { Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(if (value == "Compact") "More items" else "More space", color = Muted, fontSize = 9.sp) }
                            }
                        }
                    }
                    Text("Font size", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(Modifier.fillMaxWidth().background(Page, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Small", "Medium", "Large").forEach { value ->
                            val selected = fontSize == value
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (selected) IncomeTint else Color.Transparent).clickable { fontSize = value; save("appearance_font_size", value) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(value, color = if (selected) Green else Muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Preview", color = Ink, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().background(Page, RoundedCornerShape(15.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(accents.firstOrNull { it.first == accent }?.second ?: Green), contentAlignment = Alignment.Center) { Icon(Icons.Default.Restaurant, null, tint = Color.White) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text("Food & Dining", color = Ink, fontWeight = FontWeight.SemiBold); Text("Example transaction", color = Muted, fontSize = 11.sp) }
                        Text("−₹450", color = ExpenseRed, fontWeight = FontWeight.Bold)
                    }
                    Text("Preferences are saved on this device. Theme, accent, layout and font choices are currently preview preferences; they are not yet applied across every app screen. Currency selection is saved but does not convert stored transaction amounts.", color = Muted, fontSize = 11.sp)
                    Text("Currency", color = Ink, fontWeight = FontWeight.Bold)
                    listOf("INR (₹)", "USD ($)", "EUR (€)").forEach { value ->
                        Row(Modifier.fillMaxWidth().clickable { currency = value; save("appearance_currency", value) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = currency == value, onClick = { currency = value; save("appearance_currency", value) })
                            Text(value, color = Ink)
                        }
                    }
                }
            }
        }
    }
}
private fun nextLoanDueDate(due: String, today: java.time.LocalDate = java.time.LocalDate.now()): java.time.LocalDate? {
    val raw = due.trim()
    if (raw.isBlank() || raw.equals("Set due date", ignoreCase = true) || raw.equals("Not set", ignoreCase = true)) return null
    runCatching {
        val parsed = java.time.LocalDate.parse(raw)
        val candidate = runCatching { parsed.withYear(today.year) }.getOrNull() ?: return null
        return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
    }
    val dayMonth = Regex("^\\s*(\\d{1,2})\\s+([A-Za-z]{3,9})(?:\\s+(\\d{4}))?\\s*$").find(raw)
    val monthDay = Regex("^\\s*([A-Za-z]{3,9})\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?\\s*$").find(raw)
    val match = dayMonth ?: monthDay
    if (match != null) {
        val day = (if (dayMonth != null) match.groupValues[1] else match.groupValues[2]).toIntOrNull() ?: return null
        val monthText = if (dayMonth != null) match.groupValues[2] else match.groupValues[1]
        val explicitYear = (if (dayMonth != null) match.groupValues[3] else match.groupValues[3]).toIntOrNull()
        val month = runCatching {
            java.time.Month.valueOf(monthText.uppercase(Locale.ENGLISH).let { if (it.length > 3) it else it.take(3) }.let {
                when (it) {
                    "JAN" -> "JANUARY"; "FEB" -> "FEBRUARY"; "MAR" -> "MARCH"; "APR" -> "APRIL"
                    "MAY" -> "MAY"; "JUN" -> "JUNE"; "JUL" -> "JULY"; "AUG" -> "AUGUST"
                    "SEP" -> "SEPTEMBER"; "OCT" -> "OCTOBER"; "NOV" -> "NOVEMBER"; "DEC" -> "DECEMBER"
                    else -> it
                }
            })
        }.getOrNull() ?: return null
        if (explicitYear != null) return runCatching { java.time.LocalDate.of(explicitYear, month, day) }.getOrNull()
        var candidate = runCatching { java.time.LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        if (candidate.isBefore(today)) candidate = runCatching { candidate.plusYears(1) }.getOrNull() ?: return null
        return candidate
    }
    val dayOnly = raw.toIntOrNull()?.takeIf { it in 1..31 } ?: Regex("^\\s*(\\d{1,2})\\b").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (dayOnly != null && dayOnly in 1..31) {
        for (offset in 0..12) {
            val month = java.time.YearMonth.from(today).plusMonths(offset.toLong())
            if (dayOnly <= month.lengthOfMonth()) {
                val candidate = month.atDay(dayOnly)
                if (!candidate.isBefore(today)) return candidate
            }
        }
    }
    return null
}

@Composable
private fun LoanDetailsScreen(loan: Loan, onEmiLongPress: () -> Unit) {
    val firstDueDate = nextLoanDueDate(loan.due)
    var scheduleBalance = loan.balance.toDouble().coerceAtLeast(0.0)
    val monthlyRate = loan.annualRate.coerceAtLeast(0.0) / 1200.0
    val schedule = (0 until 6).map { index ->
        val interest = scheduleBalance * monthlyRate
        val principalPart = (loan.emi.toDouble() - interest).coerceAtLeast(0.0).coerceAtMost(scheduleBalance)
        val closingBalance = (scheduleBalance - principalPart).coerceAtLeast(0.0)
        val row = Triple(interest.toLong(), principalPart.toLong(), closingBalance.toLong())
        scheduleBalance = closingBalance
        row
    }
    val paymentTooLow = monthlyRate > 0.0 && loan.emi.toDouble() <= loan.balance.toDouble() * monthlyRate
    val emerald = Color(0xFF087F5B)
    val forest = Color(0xFF065F46)
    val mint = Color(0xFFE6F7F0)
    val page = Color(0xFFF5F7FA)
    val paidGreen = Color(0xFF10B981)
    val paidTint = Color(0xFFE6FBF3)
    val upcomingAmber = Color(0xFFF59E0B)
    val upcomingTint = Color(0xFFFFF7ED)
    val progress = if (loan.originalPrincipal > 0L) ((loan.originalPrincipal - loan.balance).toFloat() / loan.originalPrincipal.toFloat()).coerceIn(0f, 1f) else 0f
    val monthsRemaining = if (loan.emi > 0L) kotlin.math.ceil(loan.balance.toDouble() / loan.emi.toDouble()).toInt().coerceAtLeast(0) else 0
    var totalInterestEstimate = 0L
    var totalRepaymentEstimate = 0L
    var estimateValid = loan.balance > 0L && loan.emi > 0L && !paymentTooLow
    if (estimateValid) {
        var remaining = loan.balance.toDouble()
        var count = 0
        while (remaining > 0.0 && count < 1200) {
            val interest = kotlin.math.round(remaining * monthlyRate).toLong()
            val payment = minOf(loan.emi, kotlin.math.ceil(remaining + interest).toLong())
            val principalPart = payment - interest
            if (principalPart <= 0L) {
                estimateValid = false
                break
            }
            totalInterestEstimate += interest
            totalRepaymentEstimate += payment
            remaining = (remaining - principalPart).coerceAtLeast(0.0)
            count++
        }
        if (count >= 1200 && remaining > 0.0) estimateValid = false
    }
    var selectedSection by remember(loan.lender) { mutableStateOf("Schedule") }
    var bankEmiAmount by remember(loan.lender) { mutableStateOf("") }
    var bankEmiDate by remember(loan.lender) { mutableStateOf("") }
    var bankCheckAttempted by remember(loan.lender) { mutableStateOf(false) }
    val context = LocalContext.current
    val schedulePrefs = remember(loan.lender) { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var importedSchedule by remember(loan.lender) {
        mutableStateOf(runCatching {
            val raw = schedulePrefs.getString("bank_schedule_${loan.lender}", null) ?: ""
            if (raw.isBlank()) emptyList() else {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { n -> arr.getJSONObject(n).getString("date") to arr.getJSONObject(n).getLong("amount") }
            }
        }.getOrDefault(emptyList()))
    }
    var pdfMessage by remember(loan.lender) { mutableStateOf("") }
    var pdfBusy by remember(loan.lender) { mutableStateOf(false) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pdfBusy = true
            pdfMessage = ""
            val result = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read the selected PDF. Please choose it again.")
                if (bytes.size < 8 || !bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII).startsWith("%PDF-")) {
                    error("Invalid PDF file. Choose a readable EMI schedule PDF.")
                }
                PDFBoxResourceLoader.init(context.applicationContext)
                val extractedText = PDDocument.load(bytes.inputStream()).use { document ->
                    if (document.isEncrypted) error("Password-protected PDFs are not supported. Upload an unlocked PDF.")
                    if (document.numberOfPages !in 1..300) error("The PDF has an invalid or unsupported page count.")
                    PDFTextStripper().getText(document)
                }
                val normalized = extractedText.replace('\\u00A0', ' ').replace(Regex("[ \\t]+"), " ")
                val looksLikeSchedule = Regex("(?i)\\b(emi|installment|instalment|repayment|due date|principal|outstanding)\\b").containsMatchIn(normalized)
                if (!looksLikeSchedule) error("This PDF does not look like an EMI repayment schedule. No data was saved.")
                val datePattern = Regex("(?i)(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}[- ](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- ,]+\\d{2,4}|\\d{4}-\\d{2}-\\d{2})")
                val amountPattern = Regex("(?:₹|Rs\\.?\\s*)?([0-9]{1,3}(?:,[0-9]{2,3})+(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)")
                val rows = normalized.lines().mapNotNull { line ->
                    val dateMatch = datePattern.find(line) ?: return@mapNotNull null
                    val tail = line.substring(dateMatch.range.last + 1)
                    val amount = amountPattern.findAll(tail).mapNotNull { match ->
                        match.groupValues[1].replace(",", "").toDoubleOrNull()?.takeIf { it in 1.0..1000000000.0 }?.toLong()
                    }.firstOrNull() ?: return@mapNotNull null
                    dateMatch.value.trim() to amount
                }.distinct().take(1200)
                if (rows.size < 2) error("The PDF is readable, but I couldn't confidently identify at least two dated EMI rows. No data was saved. Use a text-based PDF with visible dates and amounts.")
                if (rows.map { it.first }.distinct().size != rows.size) error("The schedule contains repeated dates that need manual review. No data was saved.")
                rows
            }
            result.onSuccess { rows ->
                val arr = JSONArray()
                rows.forEach { (date, amount) -> arr.put(JSONObject().put("date", date).put("amount", amount)) }
                schedulePrefs.edit().putString("bank_schedule_${loan.lender}", arr.toString()).apply()
                importedSchedule = rows
                pdfMessage = "EMI schedule submitted successfully. Saved ${rows.size} dated installments for review."
            }.onFailure { error ->
                pdfMessage = error.message ?: "Could not validate this PDF. No schedule was saved."
            }
            pdfBusy = false
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(page),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = emerald, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Loan Details & EMI Schedule", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(loan.lender, color = Muted, fontSize = 12.sp)
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(mint).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text("TRACKED", color = forest, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 17.dp, horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MONTHLY EMI", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(7.dp))
                        Text(Money.format(loan.emi), color = forest, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    Box(Modifier.width(1.dp).height(42.dp).background(page))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL INTEREST*", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        Spacer(Modifier.height(7.dp))
                        Text(if (estimateValid) Money.format(totalInterestEstimate) else "—", color = forest, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                    }
                    Box(Modifier.width(1.dp).height(42.dp).background(page))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL REPAYMENT*", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        Spacer(Modifier.height(7.dp))
                        Text(if (estimateValid) Money.format(totalRepaymentEstimate) else "—", color = forest, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = forest), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(98.dp)) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = Color(0xFF34D399), trackColor = Color.White.copy(alpha = .18f), strokeWidth = 8.dp, strokeCap = StrokeCap.Round)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("reduced", color = Color.White.copy(alpha = .75f), fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("OUTSTANDING BALANCE", color = Color.White.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(Money.format(loan.balance), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(if (loan.emi > 0L) "About $monthsRemaining EMI(s) remaining" else "Add an EMI to estimate remaining term", color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = Color(0xFF34D399), trackColor = Color.White.copy(alpha = .2f))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Schedule", "Overview", "Bank Verify").forEach { section ->
                    val selected = selectedSection == section
                    Box(Modifier.weight(if (section == "Bank Verify") 1.2f else 1f).clip(RoundedCornerShape(11.dp)).background(if (selected) forest else Color.Transparent).clickable { selectedSection = section }.padding(horizontal = 5.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(section, color = if (selected) Color.White else Muted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (selectedSection == "Overview") {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Loan overview", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        LoanOverviewRow("Original principal", Money.format(loan.originalPrincipal))
                        LoanOverviewRow("Current outstanding", Money.format(loan.balance))
                        LoanOverviewRow("Monthly EMI", Money.format(loan.emi))
                        LoanOverviewRow("Annual interest rate", "${loan.annualRate}%")
                        LoanOverviewRow("Payments recorded", loan.paymentsMade.toString())
                        Text("Progress is estimated from the principal and outstanding balance entered in MoneyLog.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (selectedSection == "Bank Verify") "Bank Schedule Verify" else "EMI Schedule", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (selectedSection == "Bank Schedule") "Compare your lender statement" else "Next 6 installments · estimate", color = Muted, fontSize = 11.sp)
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(mint).padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(if (selectedSection == "Bank Schedule") "VERIFY" else "ESTIMATE", color = forest, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (selectedSection == "Bank Verify") {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Compare a bank-statement installment", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Or upload your lender's EMI schedule PDF. Only readable PDFs with a recognizable schedule and dated installment rows are accepted.", color = Muted, fontSize = 11.sp)
                            OutlinedButton(
                                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                enabled = !pdfBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (pdfBusy) "Validating PDF…" else "Upload EMI Schedule PDF")
                            }
                            if (pdfMessage.isNotBlank()) {
                                Text(pdfMessage, color = if (pdfMessage.startsWith("EMI schedule submitted")) forest else Color(0xFFB45309), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (importedSchedule.isNotEmpty()) {
                                Text("Saved schedule · ${importedSchedule.size} installments", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                importedSchedule.take(12).forEachIndexed { index, row ->
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF5F8F7)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${index + 1}. ${row.first}", modifier = Modifier.weight(1f), color = Ink, fontSize = 11.sp)
                                        Text(Money.format(row.second), color = forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (importedSchedule.size > 12) Text("Showing first 12 rows. All ${importedSchedule.size} installments are saved.", color = Muted, fontSize = 10.sp)
                            }
                            Text("Enter the EMI amount and date exactly as shown on your lender statement. This compares one installment only; it does not connect to your bank.", color = Muted, fontSize = 11.sp)
                            OutlinedTextField(value = bankEmiAmount, onValueChange = { bankEmiAmount = it.filter { ch -> ch.isDigit() || ch == '.' }; bankCheckAttempted = false }, label = { Text("Bank EMI amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = bankEmiDate, onValueChange = { bankEmiDate = it; bankCheckAttempted = false }, label = { Text("Bank EMI date (e.g. 5 Nov 2026)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { bankCheckAttempted = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = forest)) { Text("Compare installment") }
                            if (bankCheckAttempted) {
                                val amount = bankEmiAmount.toDoubleOrNull()
                                val formatter = java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM yyyy").toFormatter(Locale.ENGLISH)
                                val bankDate = runCatching { java.time.LocalDate.parse(bankEmiDate.trim(), formatter) }.getOrNull()
                                val expectedDate = firstDueDate
                                val validInput = amount != null && amount >= 0.0 && bankDate != null
                                val amountMatches = amount != null && kotlin.math.abs(amount - loan.emi.toDouble()) < 0.5
                                val dateMatches = bankDate != null && expectedDate != null && bankDate == expectedDate
                                Text(if (!validInput) "Unable to compare: enter a valid amount and date such as 5 Nov 2026." else if (amountMatches && dateMatches) "Amount and date match MoneyLog's current estimate." else "Difference found: " + listOfNotNull(if (!amountMatches) "bank amount differs from the EMI entered in MoneyLog" else null, if (!dateMatches) "bank date differs from MoneyLog's next due date" else null).joinToString("; ") + ".", color = if (validInput && amountMatches && dateMatches) forest else Color(0xFFB45309), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("This is a manual comparison, not bank verification. Confirm against the lender's official schedule.", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            if (selectedSection != "Bank Verify") item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(mint).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("DATE", Modifier.weight(1.05f), color = forest, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("AMOUNT", Modifier.weight(1f), color = forest, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("STATUS", Modifier.weight(.85f), color = forest, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }
            if (selectedSection != "Bank Verify") {
                if (importedSchedule.isNotEmpty()) items(importedSchedule.indices.toList()) { index ->
                    val row = importedSchedule[index]
                    val dateLabel = row.first
                    val amount = row.second
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(dateLabel, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text("Imported from lender PDF", color = Muted, fontSize = 9.sp)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Money.format(amount), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Actual PDF amount", color = Muted, fontSize = 9.sp)
                        }
                        Box(Modifier.weight(.85f), contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.clip(RoundedCornerShape(50)).background(SkyTint).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("PDF", color = Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                } else items(schedule.indices.toList()) { index ->
                val (interest, principalPart, closingBalance) = schedule[index]
                val installmentDate = firstDueDate?.plusMonths(index.toLong())
                val installmentLabel = installmentDate?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                    ?: if (loan.due.equals("Set due date", ignoreCase = true) || loan.due.isBlank()) "Set due date" else loan.due
                val paid = index < loan.paymentsMade
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White).combinedClickable(onClick = { onEmiLongPress() }, onLongClick = { onEmiLongPress() }).padding(horizontal = 12.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(installmentLabel, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Text("Principal ${Money.format(principalPart)}", color = Muted, fontSize = 9.sp)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Money.format(loan.emi), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Int. ${Money.format(interest)}", color = Muted, fontSize = 9.sp)
                    }
                    Box(Modifier.weight(.85f), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (paid) paidTint else upcomingTint).padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(if (paid) "Paid" else "Upcoming", color = if (paid) paidGreen else upcomingAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            }
            if (selectedSection != "Bank Verify" && paymentTooLow) item {
                InfoCard("EMI may not cover interest", "At the entered interest rate, the EMI is not greater than the estimated monthly interest. The balance may not reduce unless the EMI or rate changes.", Icons.Default.Warning)
            }
            item {
                Text(if (selectedSection == "Bank Schedule") "Bank Schedule is a comparison view, not a bank feed. Dates and amounts shown here are MoneyLog estimates; verify every installment against your lender's official statement." else "Schedule is estimated from the current outstanding balance, entered EMI and interest rate. Paid labels reflect only the payment count you entered; verify dates and amounts against your lender statement.", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun LoanOverviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
private fun TransactionDetailsDialog(
    transaction: Transaction,
    allTransactions: List<Transaction>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onTips: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val categoryTotal = allTransactions.filter { it.category.equals(transaction.category, ignoreCase = true) && it.income == transaction.income }.sumOf { it.amount }
    val currency = Money
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFCFF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(54.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color(0xFFC9CDE0)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close transaction details", tint = Ink) }
                        Text("TRANSACTION DETAILS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        IconButton(onClick = onTips) { Icon(Icons.Default.Lightbulb, contentDescription = "Transaction tips", tint = Purple) }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(68.dp).clip(CircleShape).background(categoryTint(transaction.category)), contentAlignment = Alignment.Center) {
                            Icon(if (transaction.income) Icons.Default.TrendingUp else Icons.Default.Restaurant, contentDescription = null, tint = categoryColor(transaction.category), modifier = Modifier.size(32.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(transaction.category, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(transaction.title, color = Muted, fontSize = 14.sp)
                            Text((if (transaction.income) "+" else "−") + currency.format(transaction.amount), color = if (transaction.income) IncomeGreen else Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                            Surface(color = if (transaction.income) IncomeTint else ExpenseTint, shape = RoundedCornerShape(50)) {
                                Text(if (transaction.income) "  INCOME  " else "  EXPENSE  ", modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp), color = if (transaction.income) IncomeGreen else ExpenseRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF4FC)), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(IncomeGreen), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Total in this category", color = Muted, fontSize = 12.sp)
                                Text(currency.format(categoryTotal), color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailQuickAction("Edit", Icons.Default.Edit, LavenderTint, Purple, onEdit)
                        DetailQuickAction("Delete", Icons.Default.Delete, ExpenseTint, ExpenseRed) { showDeleteConfirm = true }
                        DetailQuickAction("Duplicate", Icons.Default.ContentCopy, SkyTint, Blue, onDuplicate)
                        DetailQuickAction("Add Note", Icons.Default.Notes, SkyTint, Blue, onEdit)
                        DetailQuickAction("Add Tag", Icons.Default.LocalOffer, LavenderTint, Purple, onEdit)
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E6F1)), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 4.dp)) {
                            TransactionDetailRow(Icons.Default.FolderOpen, "Category", transaction.category)
                            TransactionDetailRow(Icons.Default.LocalOffer, "Tag", transaction.tag.ifBlank { "No tag added" })
                            TransactionDetailRow(Icons.Default.CalendarMonth, "Date", transaction.date)
                            TransactionDetailRow(Icons.Default.CreditCard, "Payment Method", transaction.paymentMethod.ifBlank { "Not set" })
                            TransactionDetailRow(Icons.Default.Storefront, "Merchant / Description", transaction.title)
                            TransactionDetailRow(Icons.Default.Notes, "Notes", transaction.notes.ifBlank { "No notes added" })
                            if (transaction.source.isNotBlank()) TransactionDetailRow(Icons.Default.NotificationsActive, "Captured from", transaction.source)
                            if (transaction.captureStatus.isNotBlank()) TransactionDetailRow(Icons.Default.Info, "Capture status", transaction.captureStatus)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F8F5)), shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, null, tint = Teal, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("MoneyLog tip", color = Teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(if (transaction.income) "Check that this income is categorized correctly to keep reports accurate." else "Use consistent categories and tags to make monthly reports more useful.", color = Ink, fontSize = 12.sp)
                            }
                            IconButton(onClick = onTips) { Icon(Icons.Default.ChevronRight, null, tint = Teal) }
                        }
                    }
                }
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BAA83), contentColor = Color.White)) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(9.dp))
                    Text("Edit Transaction", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete transaction?") },
            text = { Text("This will permanently remove this transaction from MoneyLog on this device.") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete", color = ExpenseRed) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailQuickAction(label: String, icon: ImageVector, tint: Color, iconColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.widthIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 3.dp)) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(21.dp))
        }
        Text(label, color = Ink, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun TransactionDetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Icon(icon, null, tint = Color(0xFF50617D), modifier = Modifier.size(19.dp))
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(0.85f))
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1.15f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionDialog(transaction: Transaction, onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    var title by remember(transaction) { mutableStateOf(transaction.title) }
    var amount by remember(transaction) { mutableStateOf(transaction.amount.toString()) }
    var category by remember(transaction) { mutableStateOf(transaction.category) }
    var tag by remember(transaction) { mutableStateOf(transaction.tag) }
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
                OutlinedTextField(tag, { tag = it.removePrefix("#") }, label = { Text("Tag (optional)") }, singleLine = true)
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
                else onSave(transaction.copy(title = title.trim(), amount = parsed, category = if (isIncome) "Income" else category.ifBlank { "Other" }, date = date.ifBlank { "Date not set" }, income = isIncome, tag = tag.trim()))
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
    categoryRefreshToken: Int = 0,
    onSave: (String, Long, Boolean, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val context = LocalContext.current
    // Read the same on-device category list used by Settings > Categories.
    // The refresh token changes whenever categories are added, renamed, or deleted.
    val savedCategories = remember(context, categoryRefreshToken) {
        runCatching {
            val raw = context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE)
                .getString("custom_categories", null)
            if (raw.isNullOrBlank()) DEFAULT_MONEY_CATEGORIES else {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.getString(it).trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
                    .ifEmpty { DEFAULT_MONEY_CATEGORIES }
            }
        }.getOrDefault(DEFAULT_MONEY_CATEGORIES)
    }
    // Category Management has no income/expense type field, so show every saved
    // category for either transaction type instead of hiding user-created entries.
    val transactionCategoryOptions = (savedCategories + listOf("Salary", "Other income"))
        .distinctBy { it.lowercase(Locale.ROOT) }
    var isIncome by remember { mutableStateOf(initialIncome) }
    var category by remember {
        mutableStateOf(
            savedCategories.firstOrNull {
                it.equals(if (initialIncome) "Income" else "Food", ignoreCase = true)
            } ?: savedCategories.firstOrNull() ?: if (initialIncome) "Salary" else "Other"
        )
    }
    LaunchedEffect(savedCategories, transactionCategoryOptions) {
        if (category !in transactionCategoryOptions) {
            category = savedCategories.firstOrNull() ?: "Other"
        }
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val defaultTags = listOf("Credit Card", "Cash", "Apple Pay", "Amazon", "Google Play", "Debit Card", "PayPal", "Google Pay", "Online")
    val preferences = remember { context.getSharedPreferences("open_finance_data", android.content.Context.MODE_PRIVATE) }
    var tags by remember {
        mutableStateOf(runCatching {
            val raw = preferences.getString("custom_tags", null)
            val saved = if (raw.isNullOrBlank()) emptyList() else {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.getString(it).trim().removePrefix("#").trim() }.filter { it.isNotBlank() }
            }
            (defaultTags + saved).distinctBy { it.lowercase(Locale.ROOT) }
        }.getOrDefault(defaultTags))
    }
    var selectedTag by remember { mutableStateOf("") }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var showCreateTag by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }
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
                    FilterChip(
                        selected = !isIncome,
                        onClick = {
                            isIncome = false
                            if (category.equals("Salary", true) || category.equals("Other income", true) || category.equals("Income", true)) {
                                category = savedCategories.firstOrNull { !it.equals("Salary", true) && !it.equals("Other income", true) && !it.equals("Income", true) } ?: "Other"
                            }
                        },
                        label = { Text("Expense", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ExpenseTint, selectedLabelColor = ExpenseRed),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                    )
                    FilterChip(
                        selected = isIncome,
                        onClick = {
                            isIncome = true
                            if (!category.equals("Salary", true) && !category.equals("Other income", true) && !category.equals("Income", true)) {
                                category = savedCategories.firstOrNull { it.equals("Income", true) }
                                    ?: savedCategories.firstOrNull { it.equals("Salary", true) }
                                    ?: "Salary"
                            }
                        },
                        label = { Text("Income", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IncomeTint, selectedLabelColor = IncomeGreen),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                    )
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
                        transactionCategoryOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { category = option; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = tagMenuExpanded, onExpandedChange = { tagMenuExpanded = it }) {
                    OutlinedTextField(
                        value = selectedTag, onValueChange = {}, readOnly = true,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        label = { Text("Tag (optional)") }, placeholder = { Text("Choose a tag") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagMenuExpanded) },
                        singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(expanded = tagMenuExpanded, onDismissRequest = { tagMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("No tag") }, onClick = { selectedTag = ""; tagMenuExpanded = false })
                        tags.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { selectedTag = option; tagMenuExpanded = false }) }
                        DropdownMenuItem(text = { Text("+ Create tag", color = Green, fontWeight = FontWeight.SemiBold) }, onClick = { tagMenuExpanded = false; tagDraft = ""; showCreateTag = true })
                    }
                }
                OutlinedButton(onClick = { tagDraft = ""; showCreateTag = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Create Tag")
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
                    onSave(title.trim(), parsed, isIncome, category.ifBlank { "Other" }, date, paymentMethod, notes.trim(), selectedTag)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = if (isIncome) IncomeGreen else Green), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save transaction", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showCreateTag) {
        AlertDialog(
            onDismissRequest = { showCreateTag = false },
            title = { Text("Create Tag") },
            text = { OutlinedTextField(tagDraft, { tagDraft = it }, label = { Text("Tag name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = tagDraft.trim().removePrefix("#").trim()
                    if (name.isNotBlank()) {
                        val existing = tags.firstOrNull { it.equals(name, ignoreCase = true) }
                        if (existing != null) selectedTag = existing
                        else {
                            tags = tags + name
                            val array = JSONArray()
                            tags.forEach { array.put(it) }
                            preferences.edit().putString("custom_tags", array.toString()).apply()
                            selectedTag = name
                        }
                        showCreateTag = false
                    }
                }) { Text("Save tag", color = Green) }
            },
            dismissButton = { TextButton(onClick = { showCreateTag = false }) { Text("Cancel") } }
        )
    }

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
            put("tag", transaction.tag)
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
            item.optString("notes", ""),
            item.optString("tag", "")
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
