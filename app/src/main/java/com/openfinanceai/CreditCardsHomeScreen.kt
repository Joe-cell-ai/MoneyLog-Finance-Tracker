package com.openfinanceai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreditCardsHomeScreen(onBack: () -> Unit) {
 var showAdd by remember { mutableStateOf(false) }
 var bank by remember { mutableStateOf("") }
 var lastFour by remember { mutableStateOf("") }
 var cards by remember { mutableStateOf(listOf<Pair<String,String>>()) }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) { IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}; Text("Credit Cards",style=MaterialTheme.typography.headlineSmall); Spacer(Modifier.weight(1f)); TextButton(onClick={showAdd=true}){Text("Add card")} }
  Text("Track your credit cards",style=MaterialTheme.typography.titleMedium)
  Text("Add a card using only its bank name and last four digits. Never enter your full card number, CVV or PIN.",fontSize=13.sp)
  Spacer(Modifier.height(16.dp))
  if(cards.isEmpty()) Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){ Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.CreditCard,null,modifier=Modifier.size(36.dp));Text("No cards added yet");TextButton(onClick={showAdd=true}){Text("Add credit card")} } } else cards.forEach { c -> Card(Modifier.fillMaxWidth().padding(bottom=10.dp)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.CreditCard,null);Spacer(Modifier.width(12.dp));Text(c.first+" •••• "+c.second)} } }
 }
 if(showAdd) AlertDialog(onDismissRequest={showAdd=false},title={Text("Add credit card")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(bank,{bank=it},label={Text("Bank name")},singleLine=true);OutlinedTextField(lastFour,{lastFour=it.filter(Char::isDigit).take(4)},label={Text("Last four digits")},singleLine=true)}},confirmButton={TextButton(onClick={if(bank.isNotBlank()&&lastFour.length==4){cards=cards+(bank.trim() to lastFour);bank="";lastFour="";showAdd=false}}){Text("Save")}},dismissButton={TextButton(onClick={showAdd=false}){Text("Cancel")}})
}
