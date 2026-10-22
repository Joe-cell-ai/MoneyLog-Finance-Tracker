package com.openfinanceai

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
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

private data class Transaction(val title: String, val category: String, val date: String, val amount: Long, val income: Boolean)
private data class Loan(val lender: String, val balance: Long, val emi: Long, val due: String)

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
    var showAdd by remember { mutableStateOf(false) }
    var transactions by remember {
        mutableStateOf(
            listOf(
                Transaction("Monthly salary", "Income", "Today", 65000, true),
                Transaction("Grocery shopping", "Food & groceries", "Today", 1840, false),
                Transaction("Electricity bill", "Bills", "Yesterday", 1250, false),
                Transaction("Fuel", "Transport", "Yesterday", 1800, false),
                Transaction("Coffee shop", "Food & drink", "20 Oct", 240, false)
            )
        )
    }
    val loans = listOf(
        Loan("Fibe", 31000, 6200, "5 Nov"),
        Loan("KreditBee", 24000, 4800, "8 Nov"),
        Loan("Pocketly", 12800, 3200, "10 Nov")
    )
    val income = transactions.filter { it.income }.sumOf { it.amount }
    val expenses = transactions.filterNot { it.income }.sumOf { it.amount }

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
                    "Home" -> HomeScreen(income, expenses, transactions, { tab = "Activity" }, { showAdd = true }, { tab = it })
                    "Activity" -> ActivityScreen(transactions)
                    "Reports" -> ReportsScreen(income, expenses, transactions)
                    "Loans" -> LoansScreen(loans)
                    "Coach" -> CoachScreen(income, expenses, loans.sumOf { it.emi })
                    "Calendar" -> CalendarScreen(loans, transactions)
                    "Insights" -> InsightsScreen(income, expenses, loans.sumOf { it.balance })
                    "Credit health" -> CreditHealthScreen()
                    "Categories" -> CategoriesScreen()
                    "Backup & security" -> BackupSecurityScreen()
                    "Appearance" -> AppearanceScreen()
                    "Loan details" -> LoanDetailsScreen(loans.first())
                    "Add loan" -> AddLoanScreen()
                    else -> SettingsScreen { tab = it }
                }
            }
            if (showAdd) {
                AddTransactionDialog(
                    onDismiss = { showAdd = false },
                    onSave = { title, amount, isIncome, category ->
                        transactions = listOf(Transaction(title, category, "Just now", amount, isIncome)) + transactions
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
    onAll: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Text("OCTOBER 2026", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
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
        item { SectionHeading("Monthly overview", "October 2026") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Spending this month", color = Muted)
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
        items(transactions.take(4)) { TransactionRow(it) }
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
private fun TransactionRow(transaction: Transaction) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun ActivityScreen(transactions: List<Transaction>) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Text("Transactions", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Your tracked money activity", color = Muted)
        }
        items(transactions) { TransactionRow(it) }
    }
}

@Composable
private fun ReportsScreen(income: Long, expenses: Long, transactions: List<Transaction>) {
    val categories = transactions.filterNot { it.income }.groupBy { it.category }.map { entry -> entry.key to entry.value.sumOf { it.amount } }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Monthly reports", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Income, spending and cash flow", color = Muted)
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
        item { SectionHeading("Category breakdown", "This month") }
        items(categories) { (category, amount) ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(15.dp)) {
                Text(category, Modifier.weight(1f), color = Ink)
                Text(Money.format(amount), color = Ink, fontWeight = FontWeight.Bold)
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
private fun LoansScreen(loans: List<Loan>) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Text("Loans & EMIs", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Keep every repayment in view", color = Muted)
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
        items(loans) { loan ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, null, tint = Green)
                        Spacer(Modifier.width(10.dp))
                        Text(loan.lender, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                        Text(Money.format(loan.balance), color = Ink, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Mint)
                    Text("Monthly EMI: " + Money.format(loan.emi), color = Muted)
                    Text("Next due: " + loan.due, color = Muted)
                }
            }
        }
        item { Text("Sample values for UI demonstration. Replace them with your actual loan balances and EMI details.", color = Muted, fontSize = 11.sp) }
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
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Financial calendar", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("October 2026 · Upcoming reminders", color = Muted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("M      T      W      T      F      S      S", color = Muted)
                    (1..31 step 7).forEach { start ->
                        Text((start..(start + 6).coerceAtMost(31)).joinToString("      "), color = Ink)
                    }
                }
            }
        }
        item { SectionHeading("Upcoming EMI reminders", "Loans") }
        items(loans) { loan ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(loan.lender, color = Ink, fontWeight = FontWeight.Bold)
                    Text("Due ${loan.due}", color = Muted, fontSize = 12.sp)
                }
                Text(Money.format(loan.emi), color = Ink, fontWeight = FontWeight.Bold)
            }
        }
        item { SectionHeading("Recent transactions", "") }
        items(transactions.take(5)) { TransactionRow(it) }
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
    val categories = listOf("Food & groceries", "Housing", "Transport", "Bills & utilities", "Health", "Shopping", "Entertainment", "Education", "Loan repayment", "Salary", "Other income")
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Text("Categories", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Income and expense groups", color = Muted)
        }
        items(categories) { category ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Label, null, tint = Green)
                Spacer(Modifier.width(12.dp))
                Text(category, color = Ink)
            }
        }
        item { Text("Editing and saved custom categories are not implemented yet.", color = Muted, fontSize = 11.sp) }
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
        "Backup & security" to Icons.Default.Security
    )
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Settings", fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("Personalize your workspace", color = Muted)
        }
        item { InfoCard("Preferences", "Currency: Indian Rupee (₹)\nTheme: Light\nStorage: demo in memory", Icons.Default.Tune) }
        item { Button(onClick = { onOpen("Categories") }, modifier = Modifier.fillMaxWidth()) { Text("Category management") } }
        item { OutlinedButton(onClick = { onOpen("Appearance") }, modifier = Modifier.fillMaxWidth()) { Text("Settings appearance") } }
        item { OutlinedButton(onClick = { onOpen("Backup & security") }, modifier = Modifier.fillMaxWidth()) { Text("Backup & security") } }
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
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("CIBIL & Credit Health", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { InfoCard("Credit score", "720 · Good (sample only, not a real CIBIL score). Connect an authorized bureau for verified data.", Icons.Default.VerifiedUser) }
        item { InfoCard("Recommendations", "Pay EMIs on time, keep credit utilization low, avoid unnecessary loan applications and review your official report for errors.", Icons.Default.Lightbulb) }
    }
}
@Composable
private fun BackupSecurityScreen() {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Backup & Security", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { InfoCard("Local data", "Demo data may reset when the app closes. Persistent encrypted storage is not connected yet.", Icons.Default.Lock) }
        item { InfoCard("Cloud backup", "Google Drive / OneDrive backup is not connected in this build.", Icons.Default.CloudUpload) }
        item { InfoCard("Restore & export", "Restore, CSV export and scheduled backups are not implemented yet.", Icons.Default.Restore) }
        item { InfoCard("Privacy", "Do not enter banking passwords, card PINs or other secrets into this prototype.", Icons.Default.PrivacyTip) }
    }
}
@Composable
private fun AppearanceScreen() {
    var theme by remember { mutableStateOf("Light") }
    var currency by remember { mutableStateOf("INR (₹)") }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings · Appearance", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", color = Ink, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Light", "Dark", "System").forEach { value -> FilterChip(selected = theme == value, onClick = { theme = value }, label = { Text(value) }) }
                    }
                    Text("Currency", color = Ink, fontWeight = FontWeight.Bold)
                    listOf("INR (₹)", "USD ($)", "EUR (€)").forEach { value -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currency == value, onClick = { currency = value }); Text(value, color = Ink) } }
                    Text("Preferences are a preview and are not saved between app launches yet.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
@Composable
private fun LoanDetailsScreen(loan: Loan) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Loan Details & EMI Schedule", fontSize = 25.sp, color = Ink, fontWeight = FontWeight.Bold) }
        item { MetricCard("Outstanding · ${loan.lender}", Money.format(loan.balance), Modifier.fillMaxWidth()) }
        item { MetricCard("Monthly EMI", Money.format(loan.emi), Modifier.fillMaxWidth()) }
        item { InfoCard("Next payment", "Next due: ${loan.due}. Schedule is illustrative; verify dates and amounts from your lender statement.", Icons.Default.CalendarMonth) }
        item { SectionHeading("Upcoming EMIs", "Schedule") }
        items((0..5).toList()) { index -> Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(13.dp)).padding(14.dp)) { Text(if (index == 0) loan.due else "Month ${index + 1}", Modifier.weight(1f), color = Ink); Text(Money.format(loan.emi), color = if (index == 0) ExpenseRed else Ink, fontWeight = FontWeight.Bold) } }
    }
}
@Composable
private fun AddLoanScreen() {
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
                    Button(onClick = { saved = lender.isNotBlank() && (principal.toLongOrNull() ?: 0L) > 0 }, modifier = Modifier.fillMaxWidth()) { Text("Save Loan") }
                    if (saved) Text("Loan form validated. Persistent saving is not connected yet.", color = IncomeGreen)
                }
            }
        }
    }
}
@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, Boolean, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food & groceries") }
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
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = amount.toLongOrNull()
                if (title.isBlank() || parsed == null || parsed <= 0) {
                    error = "Enter a description and valid amount."
                } else {
                    onSave(title.trim(), parsed, isIncome, if (isIncome) "Income" else category.ifBlank { "Other" })
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
