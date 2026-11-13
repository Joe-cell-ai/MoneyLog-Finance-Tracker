package com.openfinanceai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

internal data class CCAccount(val id:String,val bank:String,val nickname:String,val last4:String,val network:String)
internal data class CCStatement(val id:String,val cardId:String,val date:String,val due:String,val total:Long,val minimum:Long,val paid:Long)
internal data class CCPayment(val id:String,val statementId:String,val amount:Long,val date:String,val method:String,val source:String,val reference:String)
internal data class CCData(val cards:List<CCAccount>,val bills:List<CCStatement>,val payments:List<CCPayment>)
internal object CreditCardBills {
 private const val P="open_finance_data"
 private fun array(c:Context,k:String)=runCatching{JSONArray(c.getSharedPreferences(P,0).getString(k,"[]")?:"[]")}.getOrElse{JSONArray()}
 fun read(c:Context):CCData {
  val a=array(c,"cc_cards_v2");val b=array(c,"cc_bills_v2");val p=array(c,"cc_payments_v2")
  val cards=(0 until a.length()).mapNotNull{a.optJSONObject(it)?.let{j->CCAccount(j.optString("id"),j.optString("bank"),j.optString("nickname"),j.optString("last4"),j.optString("network"))}}
  val bills=(0 until b.length()).mapNotNull{b.optJSONObject(it)?.let{j->CCStatement(j.optString("id"),j.optString("cardId"),j.optString("date"),j.optString("due"),j.optLong("total"),j.optLong("minimum"),j.optLong("paid"))}}
  val pays=(0 until p.length()).mapNotNull{p.optJSONObject(it)?.let{j->CCPayment(j.optString("id"),j.optString("statementId"),j.optLong("amount"),j.optString("date"),j.optString("method"),j.optString("source"),j.optString("reference"))}}
  return CCData(cards,bills,pays)
 }
 private fun save(c:Context,d:CCData){
  val cards=JSONArray().apply{d.cards.forEach{put(JSONObject().put("id",it.id).put("bank",it.bank).put("nickname",it.nickname).put("last4",it.last4).put("network",it.network))}}
  val bills=JSONArray().apply{d.bills.forEach{put(JSONObject().put("id",it.id).put("cardId",it.cardId).put("date",it.date).put("due",it.due).put("total",it.total).put("minimum",it.minimum).put("paid",it.paid))}}
  val pays=JSONArray().apply{d.payments.forEach{put(JSONObject().put("id",it.id).put("statementId",it.statementId).put("amount",it.amount).put("date",it.date).put("method",it.method).put("source",it.source).put("reference",it.reference))}}
  c.getSharedPreferences(P,0).edit().putString("cc_cards_v2",cards.toString()).putString("cc_bills_v2",bills.toString()).putString("cc_payments_v2",pays.toString()).apply()
 }
 fun addCard(c:Context,bank:String,nick:String,last4:String,network:String){val d=read(c);if(d.cards.any{it.bank.equals(bank,true)&&it.last4==last4})return;save(c,d.copy(cards=d.cards+CCAccount(UUID.randomUUID().toString(),bank,nick,last4,network)))}
 fun addBill(c:Context,card:String,date:String,due:String,total:Long,min:Long):Boolean{
  if(runCatching{LocalDate.parse(date);LocalDate.parse(due)}.isFailure||total<=0||min<0||min>total)return false
  val d=read(c);if(d.bills.any{it.cardId==card&&it.due==due&&it.total==total})return false
  save(c,d.copy(bills=d.bills+CCStatement(UUID.randomUUID().toString(),card,date,due,total,min,0)));CreditCardReminderScheduler.schedule(c);return true
 }
 fun addPayment(c:Context,billId:String,amount:Long,date:String,method:String,source:String,ref:String):Boolean{
  if(amount<=0||runCatching{LocalDate.parse(date)}.isFailure)return false
  val d=read(c);val bill=d.bills.firstOrNull{it.id==billId}?:return false
  if(amount>bill.total-bill.paid)return false
  save(c,d.copy(bills=d.bills.map{if(it.id==billId)it.copy(paid=it.paid+amount)else it},payments=d.payments+CCPayment(UUID.randomUUID().toString(),billId,amount,date,method,source,ref)));return true
 }
 fun ingestSms(c:Context,body:String):Boolean{
  val d=read(c)
  val last=Regex("""(?i)(?:xx|\*{2,}|x{2,})(\d{4})""").find(body)?.groupValues?.get(1)
    ?:Regex("""(?i)(?:card|account|a/c)[^0-9]{0,12}(\d{4})""").find(body)?.groupValues?.get(1)
    ?:return false
  val card=d.cards.firstOrNull{it.last4==last}?:return false
  if(Regex("""(?i)(statement|total\s+amt|amount\s+due)""").containsMatchIn(body)){
   val dueText=Regex("""(?i)due\s+on\s*:?\s*(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""").find(body)?.groupValues?.get(1)?:return false
   val parts=dueText.split('-','/');if(parts.size!=3)return false
   val year=(parts[2].toIntOrNull()?:return false).let{if(it<100)2000+it else it}
   val due=runCatching{LocalDate.of(year,parts[1].toInt(),parts[0].toInt())}.getOrNull()?:return false
   val totalText=Regex("""(?i)(?:total\s+amt|total\s+amount|amount\s+due)\s*:?\s*(?:inr|rs\.?|₹)?\s*(?:dr\.?\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)?.groupValues?.get(1)?:return false
   val minText=Regex("""(?i)min(?:imum)?\s+amt(?:ount)?\s+due\s*:?\s*(?:inr|rs\.?|₹)?\s*(?:dr\.?\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""").find(body)?.groupValues?.get(1)?:totalText
   val amount=totalText.replace(",","").toDoubleOrNull()?.toLong()?:return false
   val minimum=minText.replace(",","").toDoubleOrNull()?.toLong()?:return false
   return addBill(c,card.id,LocalDate.now().toString(),due.toString(),amount,minimum)
  }
  if(Regex("""(?i)(payment|bill).{0,35}(successful|received|credited|paid)""").containsMatchIn(body)&&!Regex("""(?i)(failed|declined|unsuccessful)""").containsMatchIn(body)){
   val amt=Regex("""(?i)(?:inr|rs\.?|₹)\s*([0-9,]+)""").find(body)?.groupValues?.get(1)?.replace(",","")?.toLongOrNull()?:return false
   val open=d.bills.filter{it.cardId==card.id&&it.total>it.paid}
   if(open.size!=1)return false
   return addPayment(c,open[0].id,amt,LocalDate.now().toString(),"Bank SMS","SMS","")
  }
  return false
 }
}
private fun ccFormat(v:Long)=NumberFormat.getCurrencyInstance(Locale("en","IN")).format(v)
@Composable
fun CreditCardsHomeScreen(onBack:()->Unit){
 val ctx=LocalContext.current
 var data by remember{mutableStateOf(CreditCardBills.read(ctx))}
 var page by remember{mutableIntStateOf(0)}
 var addCard by remember{mutableStateOf(false)}
 var billFor by remember{mutableStateOf<CCAccount?>(null)}
 var payFor by remember{mutableStateOf<CCStatement?>(null)}
 var bank by remember{mutableStateOf("")};var nick by remember{mutableStateOf("")};var last by remember{mutableStateOf("")};var network by remember{mutableStateOf("Visa")}
 var total by remember{mutableStateOf("")};var minimum by remember{mutableStateOf("")};var due by remember{mutableStateOf(LocalDate.now().plusDays(20).toString())}
 var amount by remember{mutableStateOf("")};var method by remember{mutableStateOf("UPI")};var reference by remember{mutableStateOf("")}
 var r7 by remember{mutableStateOf(true)};var r3 by remember{mutableStateOf(true)};var r1 by remember{mutableStateOf(true)}
 fun refresh(){data=CreditCardBills.read(ctx)}
 DisposableEffect(ctx){val r=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){refresh()}};ContextCompat.registerReceiver(ctx,r,IntentFilter(SmsAutoImportReceiver.ACTION_IMPORTED),ContextCompat.RECEIVER_NOT_EXPORTED);onDispose{runCatching{ctx.unregisterReceiver(r)}}}
 val outstanding=data.bills.sumOf{(it.total-it.paid).coerceAtLeast(0)}
 Column(Modifier.fillMaxSize().padding(14.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")};Text("Credit Cards",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));TextButton(onClick={addCard=true}){Text("+ Add")}}
  Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Text("Total Outstanding");Text(ccFormat(outstanding),fontSize=28.sp,fontWeight=FontWeight.Bold);Text(data.cards.size.toString()+" cards · "+data.bills.count{it.total>it.paid}+" unpaid bills")}}
  ScrollableTabRow(selectedTabIndex=page,edgePadding=0.dp){listOf("All Cards","Insights","Payment Options","Reminders","Statements").forEachIndexed{i,t->Tab(page==i,{page=i},text={Text(t)})}}
  Spacer(Modifier.height(8.dp))
  when(page){
   0->{
    if(data.cards.isEmpty())Text("Add your credit card to start tracking statements and payments.",Modifier.padding(16.dp))
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(data.cards,key={it.id}){card->
     val bill=data.bills.filter{it.cardId==card.id}.maxByOrNull{it.due}
     Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.CreditCard,null);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(card.bank.uppercase(),fontWeight=FontWeight.Bold);Text(card.nickname+" · •••• "+card.last4,fontSize=12.sp)};Text(card.network,fontSize=11.sp)}
      if(bill!=null){Divider(Modifier.padding(vertical=8.dp));Text("Outstanding",fontSize=12.sp);Text(ccFormat(bill.total-bill.paid),fontSize=24.sp,fontWeight=FontWeight.Bold);Text("Due "+bill.due+" · Minimum "+ccFormat(bill.minimum));val days=runCatching{java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(bill.due))}.getOrNull();Text(if(bill.paid>=bill.total)"Paid" else if(days!=null&&days<0)"Overdue" else "Due in "+days+" days",color=MaterialTheme.colorScheme.primary);Row{TextButton(onClick={billFor=card}){Text("Add statement")};TextButton(onClick={payFor=bill}){Text("Record payment")}}}
      else TextButton(onClick={billFor=card}){Text("Add statement")}
     }}
    }}
   }
   1->{
    val spent=data.bills.sumOf{it.total};val payments=data.payments.sumOf{it.amount}
    Text("Credit Card Insights",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Card(Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text("Total Billed");Text(ccFormat(spent),fontWeight=FontWeight.Bold,fontSize=18.sp)}};Card(Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text("Payments");Text(ccFormat(payments),fontWeight=FontWeight.Bold,fontSize=18.sp)}}}
    Spacer(Modifier.height(12.dp));Text("Upcoming Bills",fontWeight=FontWeight.Bold)
    data.bills.filter{it.total>it.paid}.sortedBy{it.due}.forEach{b->val c=data.cards.firstOrNull{it.id==b.cardId};Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){Text((c?.bank?:"Card")+" •••• "+(c?.last4?:""));Text(ccFormat(b.total-b.paid),fontWeight=FontWeight.Bold);Text("Due "+b.due)}}}
   }
   2->{
    Text("Choose payment method",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    listOf("UPI","Net Banking","Card Payment","Manual Entry").forEach{m->Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable{method=m}){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(method==m,{method=m});Text(m,fontWeight=FontWeight.SemiBold)}}}
    Text("Select a statement under All Cards or Statements to record a payment. Actual money transfer is completed in your bank app.",fontSize=12.sp)
    data.payments.sortedByDescending{it.date}.forEach{p->Text(p.date+" · "+p.method+" · "+ccFormat(p.amount),Modifier.padding(5.dp))}
   }
   3->{
    Text("Due Date Reminder",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    listOf("7 days before" to r7,"3 days before" to r3,"1 day before" to r1).forEachIndexed{i,p->Row(Modifier.fillMaxWidth().clickable{when(i){0->r7=!r7;1->r3=!r3;else->r1=!r1}}.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(p.second,{v->when(i){0->r7=v;1->r3=v;else->r1=v}});Text(p.first)}}
    Button(onClick={ctx.getSharedPreferences("open_finance_data",0).edit().putBoolean("cc_reminder_7",r7).putBoolean("cc_reminder_3",r3).putBoolean("cc_reminder_1",r1).apply();CreditCardReminderScheduler.schedule(ctx)}){Text("Save reminder settings")}
    Text("Reminder preferences saved. Notifications require scheduling and notification permission.",fontSize=12.sp)
   }
   else->{
    Text("Statement History",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    data.bills.sortedByDescending{it.date}.forEach{b->val c=data.cards.firstOrNull{it.id==b.cardId};Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){Text((c?.bank?:"Card")+" •••• "+(c?.last4?:""),fontWeight=FontWeight.Bold);Text("Statement "+b.date+" · Due "+b.due);Text("Total "+ccFormat(b.total)+" · Paid "+ccFormat(b.paid)+" · Remaining "+ccFormat(b.total-b.paid));TextButton(onClick={payFor=b}){Text("Record payment")}}}}
   }
  }
 }
 if(addCard)AlertDialog(onDismissRequest={addCard=false},title={Text("Add credit card")},text={Column{OutlinedTextField(bank,{bank=it},label={Text("Bank name")});OutlinedTextField(nick,{nick=it},label={Text("Nickname")});OutlinedTextField(last,{last=it.filter(Char::isDigit).take(4)},label={Text("Last four digits")});OutlinedTextField(network,{network=it},label={Text("Network")});Text("Never enter full card number, CVV or PIN.",fontSize=11.sp)}},confirmButton={TextButton(onClick={if(bank.isNotBlank()&&last.length==4){CreditCardBills.addCard(ctx,bank,nick.ifBlank{"Credit Card"},last,network);addCard=false;refresh();bank="";nick="";last=""}}){Text("Save")}},dismissButton={TextButton(onClick={addCard=false}){Text("Cancel")}})
 billFor?.let{c->AlertDialog(onDismissRequest={billFor=null},title={Text("Add statement")},text={Column{OutlinedTextField(total,{total=it},label={Text("Total amount due ₹")});OutlinedTextField(minimum,{minimum=it},label={Text("Minimum amount due ₹")});OutlinedTextField(due,{due=it},label={Text("Due date YYYY-MM-DD")})}},confirmButton={TextButton(onClick={if(CreditCardBills.addBill(ctx,c.id,LocalDate.now().toString(),due,total.toLongOrNull()?:0,minimum.toLongOrNull()?:0)){billFor=null;refresh();total="";minimum=""}}){Text("Save statement")}},dismissButton={TextButton(onClick={billFor=null}){Text("Cancel")}})
 payFor?.let{b->AlertDialog(onDismissRequest={payFor=null},title={Text("Record payment")},text={Column{Text("Remaining "+ccFormat(b.total-b.paid));OutlinedTextField(amount,{amount=it},label={Text("Amount ₹")});OutlinedTextField(method,{method=it},label={Text("Method: UPI / Net Banking / Card / Manual")});OutlinedTextField(reference,{reference=it},label={Text("Reference (optional)")})}},confirmButton={TextButton(onClick={if(CreditCardBills.addPayment(ctx,b.id,amount.toLongOrNull()?:0,LocalDate.now().toString(),method,"Manual",reference)){payFor=null;refresh();amount="";reference=""}}){Text("Save payment")}},dismissButton={TextButton(onClick={payFor=null}){Text("Cancel")}})
}
