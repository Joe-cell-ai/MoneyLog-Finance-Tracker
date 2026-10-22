package com.openfinanceai.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.pow

private val Ink = Color(0xFF18233A)
private val Muted = Color(0xFF78839A)
private val Canvas = Color(0xFFF5F7FB)
private val Blue = Color(0xFF3867F4)
private val Mint = Color(0xFF1DBA8A)
private val Coral = Color(0xFFFF7777)
private val Violet = Color(0xFF8C72E8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenFinanceTheme { OpenFinanceApp() } }
    }
}

private data class FinanceTransaction(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val amount: Double,
    val income: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Activity("Activity", Icons.Filled.ReceiptLong),
    Budgets("Budgets", Icons.Filled.PieChart),
    Loans("Loans", Icons.Filled.AccountBalance),
    More("More", Icons.Filled.Settings)
}

@Composable
private fun OpenFinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Mint,
            background = Canvas,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink
        ),
        content = content
    )
}

@Composable
private fun OpenFinanceApp() {
    val context = LocalContext.current
    val transactions = remember {
        mutableStateListOf<FinanceTransaction>().apply { addAll(loadTransactions(context)) }
    }
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val incomeTotal = transactions.filter { it.income }.sumOf { it.amount }
    val expenseTotal = transactions.filterNot { it.income }.sumOf { it.amount }
    val balance = 85000.0 + incomeTotal - expenseTotal

    Scaffold(
        containerColor = Canvas,
        floatingActionButton = {
            if (selectedTab == AppTab.Home || selectedTab == AppTab.Activity) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Blue,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Filled.Add, contentDescription = "Add transaction") }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(Canvas)
        ) {
            when (selectedTab) {
                AppTab.Home -> DashboardScreen(
                    balance = balance,
                    income = incomeTotal,
                    expense = expenseTotal,
                    transactions = transactions.take(4),
                    onSeeAll = { selectedTab = AppTab.Activity },
                    onAdd = { showAddDialog = true }
                )
                AppTab.Activity -> ActivityScreen(
                    transactions = transactions,
                    onDelete = { item ->
                        transactions.remove(item)
                        saveTransactions(context, transactions)
                    }
                )
                AppTab.Budgets -> BudgetsScreen(expenseTotal)
                AppTab.Loans -> LoansScreen()
                AppTab.More -> MoreScreen(
                    transactionCount = transactions.size,
                    onClear = { showClearDialog = true }
                )
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, category, amount, income ->
                transactions.add(
                    0,
                    FinanceTransaction(title = title, category = category, amount = amount, income = income)
                )
                saveTransactions(context, transactions)
                showAddDialog = false
                selectedTab = AppTab.Activity
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all transactions?") },
            text = { Text("This removes locally saved transactions from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    transactions.clear()
                    saveTransactions(context, transactions)
                    showClearDialog = false
                }) { Text("Clear data", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 13.sp, color = Muted)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun DashboardScreen(
    balance: Double,
    income: Double,
    expense: Double,
    transactions: List<FinanceTransaction>,
    onSeeAll: () -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item {
            ScreenHeader(
                title = "Good morning 👋",
                subtitle = "Here's your money at a glance",
                trailing = {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFE6ECFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Blue)
                    }
                }
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Blue)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF3867F4), Color(0xFF6E67E8))))
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("TOTAL BALANCE", color = Color.White.copy(alpha = .8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(formatMoney(balance), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BalanceMetric("Income", income, Icons.Filled.ArrowDownward, Mint, Modifier.weight(1f))
                        BalanceMetric("Expenses", expense, Icons.Filled.ArrowUpward, Color(0xFFFFB7B7), Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickAction("Add expense", Icons.Filled.ArrowUpward, Color(0xFFFFEEEE), Coral, onAdd, Modifier.weight(1f))
                QuickAction("Add income", Icons.Filled.ArrowDownward, Color(0xFFE4F8F1), Mint, onAdd, Modifier.weight(1f))
                QuickAction("Plan budget", Icons.Filled.PieChart, Color(0xFFEDEAFF), Violet, onSeeAll, Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent activity", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                TextButton(onClick = onSeeAll) { Text("See all", color = Blue) }
            }
        }
        if (transactions.isEmpty()) {
            item { EmptyState("No transactions yet", "Tap + to record your first income or expense.") }
        } else {
            items(transactions, key = { it.id }) { item -> TransactionRow(item) }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Blue, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("A small step adds up", fontWeight = FontWeight.Bold, color = Ink)
                        Text("Track every expense to understand your spending habits.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceMetric(label: String, amount: Double, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = .75f))
            Text(formatMoney(amount), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(background), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}

@Composable
private fun ActivityScreen(
    transactions: List<FinanceTransaction>,
    onDelete: (FinanceTransaction) -> Unit
) {
    var filter by remember { mutableStateOf("All") }
    val filtered = when (filter) {
        "Income" -> transactions.filter { it.income }
        "Expenses" -> transactions.filterNot { it.income }
        else -> transactions
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Transactions", "Your income and expenses")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Income", "Expenses").forEach { choice ->
                val selected = filter == choice
                if (selected) {
                    Button(onClick = { filter = choice }, modifier = Modifier.weight(1f)) { Text(choice) }
                } else {
                    OutlinedButton(onClick = { filter = choice }, modifier = Modifier.weight(1f)) { Text(choice) }
                }
            }
        }
        if (filtered.isEmpty()) {
            EmptyState("Nothing to show", "Add a transaction or choose another filter.")
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TransactionRow(item) }
                        TextButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete transaction", tint = Muted)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(item: FinanceTransaction) {
    val tint = if (item.income) Mint else Coral
    val icon = categoryIcon(item.category, item.income)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = tint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text("${item.category} • ${formatDate(item.timestamp)}", fontSize = 11.sp, color = Muted)
        }
        Text(
            (if (item.income) "+" else "−") + formatMoney(item.amount),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = tint
        )
    }
}

@Composable
private fun BudgetsScreen(expenseTotal: Double) {
    val budgets = listOf(
        Triple("Food & groceries", 8000.0, Color(0xFFFFA45B)),
        Triple("Transport", 3500.0, Blue),
        Triple("Shopping", 5000.0, Violet),
        Triple("Bills & utilities", 4500.0, Mint)
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Budgets", "Build better money habits")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Monthly spending overview", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(formatMoney(expenseTotal), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Recorded expenses across all categories", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (expenseTotal / 21000.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Blue,
                    trackColor = Color(0xFFE9EDF5)
                )
                Spacer(Modifier.height(8.dp))
                Text("${formatMoney((21000.0 - expenseTotal).coerceAtLeast(0.0))} of sample monthly limit remaining", fontSize = 12.sp, color = Muted)
            }
        }
        Text("Category budgets", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp))
        budgets.forEach { (name, limit, color) ->
            val spent = if (expenseTotal <= 0) 0.0 else (expenseTotal / budgets.size).coerceAtMost(limit * 1.25)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(9.dp))
                        Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${formatMoney(spent)} / ${formatMoney(limit)}", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (spent / limit).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = color,
                        trackColor = Color(0xFFE9EDF5)
                    )
                }
            }
        }
        Text("Budget amounts are starter examples. Custom budgets are a planned next step.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(18.dp))
    }
}

@Composable
private fun LoansScreen() {
    var principalText by remember { mutableStateOf("300000") }
    var rateText by remember { mutableStateOf("14") }
    var monthsText by remember { mutableStateOf("36") }
    val principal = principalText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val annualRate = rateText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val months = monthsText.toIntOrNull()?.coerceIn(1, 600) ?: 1
    val monthlyRate = annualRate / 1200.0
    val emi = if (monthlyRate == 0.0) principal / months else {
        val factor = (1 + monthlyRate).pow(months)
        principal * monthlyRate * factor / (factor - 1)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Loan planner", "Estimate your monthly EMI")
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Loan details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = principalText, onValueChange = { principalText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Loan amount (₹)") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = rateText, onValueChange = { rateText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("Annual interest %") }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                            )
                            OutlinedTextField(
                                value = monthsText, onValueChange = { monthsText = it.filter(Char::isDigit) },
                                label = { Text("Months") }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(Blue, Violet))).padding(20.dp)
                        ) {
                            Column {
                                Text("ESTIMATED MONTHLY EMI", color = Color.White.copy(alpha = .8f), fontSize = 11.sp, letterSpacing = 1.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(formatMoney(emi), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(14.dp))
                                Divider(color = Color.White.copy(alpha = .25f))
                                Spacer(Modifier.height(12.dp))
                                Row {
                                    Column(Modifier.weight(1f)) {
                                        Text("Total repayment", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                                        Text(formatMoney(emi * months), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Total interest", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                                        Text(formatMoney((emi * months - principal).coerceAtLeast(0.0)), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Blue)
                        Spacer(Modifier.width(10.dp))
                        Text("This is an estimate using a reducing-balance EMI formula. Lender fees, insurance and actual terms may change your payment.", fontSize = 12.sp, color = Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(transactionCount: Int, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("More", "App preferences and data")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFFE7EDFF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Blue, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Open Finance AI", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Private money tracking", color = Muted, fontSize = 12.sp)
                    }
                }
                Divider(Modifier.padding(vertical = 18.dp), color = Color(0xFFEFF1F6))
                SettingRow(Icons.Filled.CreditCard, "Saved transactions", "$transactionCount records")
                SettingRow(Icons.Filled.Savings, "Storage", "Stored locally on this device")
                SettingRow(Icons.Filled.CalendarMonth, "Version", "0.1.0 • Early build")
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Your data, your control", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("This starter version keeps transaction records in app-private local storage. Cloud sync and encrypted backup are not implemented yet.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear transaction history")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Built as an early foundation. Budget editing, export, secure backup and AI features are planned for later milestones.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp))
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFE7EDFF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = Blue, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(detail, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var income by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val categories = listOf("Food", "Transport", "Shopping", "Bills", "Salary", "Health", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!income) Button(onClick = { income = false }, modifier = Modifier.weight(1f)) { Text("Expense") }
                    else OutlinedButton(onClick = { income = false }, modifier = Modifier.weight(1f)) { Text("Expense") }
                    if (income) Button(onClick = { income = true }, modifier = Modifier.weight(1f)) { Text("Income") }
                    else OutlinedButton(onClick = { income = true }, modifier = Modifier.weight(1f)) { Text("Income") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amountText, { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                Text("Category", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { item ->
                        if (category == item) {
                            Button(onClick = { category = item }) { Text(item, fontSize = 11.sp) }
                        } else {
                            OutlinedButton(onClick = { category = item }) { Text(item, fontSize = 11.sp) }
                        }
                    }
                }
                if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text("Enter a description and a valid amount greater than zero.", color = Coral, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (title.isBlank() || amount == null || amount <= 0.0) {
                    error = true
                } else {
                    onSave(title.trim(), category, amount, income)
                }
            }) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun categoryIcon(category: String, income: Boolean): ImageVector = when (category.lowercase()) {
    "food", "food & groceries" -> Icons.Filled.Restaurant
    "shopping" -> Icons.Filled.ShoppingBag
    "salary" -> Icons.Filled.TrendingUp
    "bills", "bills & utilities" -> Icons.Filled.CreditCard
    else -> if (income) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward
}

private fun formatMoney(value: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    formatter.minimumFractionDigits = 0
    return formatter.format(value)
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))

private fun loadTransactions(context: Context): List<FinanceTransaction> {
    val preferences = context.getSharedPreferences("open_finance", Context.MODE_PRIVATE)
    val saved = preferences.getString("transactions", null)
    if (saved == null) {
        return listOf(
            FinanceTransaction(title = "Monthly salary", category = "Salary", amount = 65000.0, income = true),
            FinanceTransaction(title = "Groceries", category = "Food", amount = 1250.0, income = false),
            FinanceTransaction(title = "Metro & cab", category = "Transport", amount = 380.0, income = false)
        )
    }
    return try {
        val array = JSONArray(saved)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            FinanceTransaction(
                id = item.getString("id"),
                title = item.getString("title"),
                category = item.getString("category"),
                amount = item.getDouble("amount"),
                income = item.getBoolean("income"),
                timestamp = item.getLong("timestamp")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveTransactions(context: Context, transactions: List<FinanceTransaction>) {
    val array = JSONArray()
    transactions.forEach { item ->
        array.put(
            JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("amount", item.amount)
                put("income", item.income)
                put("timestamp", item.timestamp)
            }
        )
    }
    context.getSharedPreferences("open_finance", Context.MODE_PRIVATE)
        .edit().putString("transactions", array.toString()).apply()
}
