package com.example.invoicesaathi

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Invoice(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(),
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val customer: String,
    val phone: String,
    val item: String,
    val quantity: Double,
    val unitPrice: Double,
    val taxPercent: Double,
    val notes: String = ""
) {
    val subtotal get() = quantity * unitPrice
    val tax get() = subtotal * taxPercent / 100.0
    val total get() = subtotal + tax
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { InvoiceSaathiApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSaathiApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    var customer by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var item by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var invoices by remember { mutableStateOf(loadInvoices(context)) }
    var tab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var pendingCsv by remember { mutableStateOf("") }
    var pendingPdf by remember { mutableStateOf<ByteArray?>(null) }
    var pendingPdfName by remember { mutableStateOf("invoice.pdf") }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val prefs = context.getSharedPreferences("export", 0)
            prefs.edit().putString("tree_uri", uri.toString()).apply()
            val old = context.contentResolver.persistedUriPermissions
            exportCsvToTree(context, uri, pendingCsv)
            pendingPdf?.let { savePdfToTree(context, uri, pendingPdfName, it) }
            message = "Export complete. Folder mein CSV/PDF save kar diya."
        } else {
            message = "Folder select nahi hua. Invoice app history mein saved hai."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("InvoiceSaathi", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        tab = 1
                    }) { Icon(Icons.Default.History, contentDescription = "History") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Add, null) }, label = { Text("New Invoice") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Description, null) }, label = { Text("History") }
                )
            }
        },
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 6.dp))
            }
            if (tab == 0) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Naya invoice", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(customer, { customer = it }, label = { Text("Customer / Party name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(item, { item = it }, label = { Text("Product / Service *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(qty, { qty = it }, label = { Text("Quantity") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(price, { price = it }, label = { Text("Unit price (₹)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(tax, { tax = it }, label = { Text("Tax % (e.g. 18; optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes / address (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    val q = qty.toDoubleOrNull() ?: 0.0
                    val p = price.toDoubleOrNull() ?: 0.0
                    val t = tax.toDoubleOrNull() ?: 0.0
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Invoice preview", fontWeight = FontWeight.SemiBold)
                            Text("Subtotal: ${money(q * p)}")
                            Text("Tax: ${money(q * p * t / 100)}")
                            Text("Total: ${money(q * p * (1 + t / 100))}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = customer.isNotBlank() && item.isNotBlank() && q > 0 && p >= 0 && t >= 0,
                        onClick = {
                            val invoice = Invoice(
                                customer = customer.trim(), phone = phone.trim(), item = item.trim(),
                                quantity = q, unitPrice = p, taxPercent = t, notes = notes.trim()
                            )
                            invoices = listOf(invoice) + invoices
                            saveInvoices(context, invoices)
                            pendingCsv = makeCsv(invoices)
                            pendingPdfName = "Invoice-${invoice.id}.pdf"
                            pendingPdf = makePdf(invoice)
                            val tree = context.getSharedPreferences("export", 0).getString("tree_uri", null)
                            if (tree != null) {
                                val uri = Uri.parse(tree)
                                exportCsvToTree(context, uri, pendingCsv)
                                pendingPdf?.let { savePdfToTree(context, uri, pendingPdfName, it) }
                                message = "Invoice ${invoice.id} saved. CSV aur PDF update ho gaye."
                            } else {
                                message = "Invoice saved. Excel folder choose karne ke liye export dabayein."
                            }
                            tab = 1
                            customer = ""; phone = ""; item = ""; qty = "1"; price = ""; tax = "0"; notes = ""
                        }
                    ) { Text("Save Invoice & Generate PDF") }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { folderPicker.launch(null) }
                    ) { Text("Choose Excel / PDF Folder") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Invoice history", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { folderPicker.launch(null) }) { Text("Export folder") }
                }
                if (invoices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Abhi koi invoice nahi bana.") }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(invoices, key = { it.id }) { inv ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(inv.customer, fontWeight = FontWeight.Bold)
                                        Text(money(inv.total), fontWeight = FontWeight.Bold)
                                    }
                                    Text("Invoice ${inv.id} • ${inv.date}")
                                    Text("${inv.item} · Qty ${inv.quantity} × ${money(inv.unitPrice)}")
                                    Text("Tax ${inv.taxPercent}% · Total ${money(inv.total)}")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            val bytes = makePdf(inv)
                                            sharePdf(context, bytes, "Invoice-${inv.id}.pdf")
                                        }) {
                                            Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Share PDF")
                                        }
                                        TextButton(onClick = {
                                            pendingCsv = makeCsv(invoices)
                                            pendingPdfName = "Invoice-${inv.id}.pdf"
                                            pendingPdf = makePdf(inv)
                                            folderPicker.launch(null)
                                        }) { Text("Export") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun money(value: Double): String = "₹" + String.format(Locale.US, "%,.2f", value)

fun loadInvoices(context: android.content.Context): List<Invoice> {
    return try {
        val file = File(context.filesDir, "invoices.json")
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Invoice(
                id = o.optString("id"), date = o.optString("date"),
                customer = o.optString("customer"), phone = o.optString("phone"),
                item = o.optString("item"), quantity = o.optDouble("quantity"),
                unitPrice = o.optDouble("unitPrice"), taxPercent = o.optDouble("taxPercent"),
                notes = o.optString("notes")
            )
        }
    } catch (_: Exception) { emptyList() }
}

fun saveInvoices(context: android.content.Context, invoices: List<Invoice>) {
    val arr = JSONArray()
    invoices.forEach { inv ->
        arr.put(JSONObject().apply {
            put("id", inv.id); put("date", inv.date); put("customer", inv.customer)
            put("phone", inv.phone); put("item", inv.item); put("quantity", inv.quantity)
            put("unitPrice", inv.unitPrice); put("taxPercent", inv.taxPercent); put("notes", inv.notes)
        })
    }
    File(context.filesDir, "invoices.json").writeText(arr.toString())
}

fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""

fun makeCsv(invoices: List<Invoice>): String {
    val rows = mutableListOf("Invoice ID,Date,Customer,Phone,Item,Quantity,Unit Price,Subtotal,Tax %,Tax Amount,Total,Notes")
    invoices.reversed().forEach { i ->
        rows += listOf(
            i.id, i.date, i.customer, i.phone, i.item,
            i.quantity.toString(), "%.2f".format(Locale.US, i.unitPrice),
            "%.2f".format(Locale.US, i.subtotal), i.taxPercent.toString(),
            "%.2f".format(Locale.US, i.tax), "%.2f".format(Locale.US, i.total), i.notes
        ).joinToString(",") { csvCell(it) }
    }
    return "\uFEFF" + rows.joinToString("\r\n")
}

fun makePdf(i: Invoice): ByteArray {
    val doc = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = doc.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
    fun line(text: String, x: Float, y: Float, size: Float = 12f, bold: Boolean = false) {
        paint.textSize = size
        paint.typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        canvas.drawText(text.take(85), x, y, paint)
    }
    line("INVOICE", 42f, 62f, 28f, true)
    line("Invoice No: ${i.id}", 42f, 95f, 12f, true)
    line("Date: ${i.date}", 42f, 117f)
    line("Bill To: ${i.customer}", 42f, 160f, 16f, true)
    if (i.phone.isNotBlank()) line("Phone: ${i.phone}", 42f, 183f)
    if (i.notes.isNotBlank()) line("Notes: ${i.notes}", 42f, 205f)
    canvas.drawLine(42f, 235f, 553f, 235f, paint)
    line("Description", 42f, 260f, 12f, true)
    line("Qty", 320f, 260f, 12f, true)
    line("Unit Price", 370f, 260f, 12f, true)
    line("Amount", 475f, 260f, 12f, true)
    canvas.drawLine(42f, 272f, 553f, 272f, paint)
    line(i.item, 42f, 300f)
    line(i.quantity.toString(), 320f, 300f)
    line(money(i.unitPrice), 370f, 300f)
    line(money(i.subtotal), 475f, 300f)
    canvas.drawLine(42f, 320f, 553f, 320f, paint)
    line("Subtotal", 365f, 355f); line(money(i.subtotal), 475f, 355f)
    line("Tax (${i.taxPercent}%)", 365f, 380f); line(money(i.tax), 475f, 380f)
    canvas.drawLine(350f, 395f, 553f, 395f, paint)
    line("TOTAL", 365f, 425f, 16f, true); line(money(i.total), 465f, 425f, 16f, true)
    line("Thank you for your business.", 42f, 760f, 12f)
    doc.finishPage(page)
    val out = java.io.ByteArrayOutputStream()
    doc.writeTo(out); doc.close()
    return out.toByteArray()
}

fun exportCsvToTree(context: android.content.Context, tree: Uri, csv: String) {
    try {
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree) ?: return
        val file = doc.findFile("invoices.csv") ?: doc.createFile("text/csv", "invoices.csv") ?: return
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
    } catch (_: Exception) { }
}

fun savePdfToTree(context: android.content.Context, tree: Uri, name: String, bytes: ByteArray) {
    try {
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree) ?: return
        val file = doc.findFile(name) ?: doc.createFile("application/pdf", name) ?: return
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
    } catch (_: Exception) { }
}

fun sharePdf(context: android.content.Context, bytes: ByteArray, name: String) {
    try {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share invoice PDF"))
    } catch (_: Exception) {
        // The Export button remains available if sharing provider is not configured.
    }
}
