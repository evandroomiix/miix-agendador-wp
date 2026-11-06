package com.miix.agendadorwp.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miix.agendadorwp.data.*
import com.miix.agendadorwp.scheduler.SchedulerManager
import com.miix.agendadorwp.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScheduleScreen(
    database: AppDatabase,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var whatsAppType by remember { mutableStateOf(WhatsAppType.WHATSAPP_NORMAL) }
    var repeatMode by remember { mutableStateOf(RepeatMode.NONE) }
    var delaySeconds by remember { mutableStateOf(10f) }

    // Lista de contatos: Pair(Telefone, Nome)
    val contactsList = remember { mutableStateListOf<Pair<String, String>>() }
    var newPhoneInput by remember { mutableStateOf("") }
    var newNameInput by remember { mutableStateOf("") }
    var showBatchDialog by remember { mutableStateOf(false) }
    var batchText by remember { mutableStateOf("") }

    // Data e Hora selecionadas
    val calendar = remember {
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, 5) // Padrão: 5 minutos à frente
        }
    }
    var selectedTimestamp by remember { mutableStateOf(calendar.timeInMillis) }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo Agendamento", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título / Identificador
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome da Campanha / Título") },
                    placeholder = { Text("Ex: Cobrança Clientes, Lembrete Reunião...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = GreenPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            // Seleção de Versão do WhatsApp
            item {
                Text("Enviar através do:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = whatsAppType == WhatsAppType.WHATSAPP_NORMAL,
                        onClick = { whatsAppType = WhatsAppType.WHATSAPP_NORMAL },
                        label = { Text("WhatsApp Normal") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GreenPrimary,
                            selectedLabelColor = DarkBackground,
                            selectedLeadingIconColor = DarkBackground
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = whatsAppType == WhatsAppType.WHATSAPP_BUSINESS,
                        onClick = { whatsAppType = WhatsAppType.WHATSAPP_BUSINESS },
                        label = { Text("WhatsApp Business") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GreenPrimary,
                            selectedLabelColor = DarkBackground,
                            selectedLeadingIconColor = DarkBackground
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Mensagem de Texto com chips de variáveis dinâmicas
            item {
                Column {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("Mensagem") },
                        placeholder = { Text("Digite sua mensagem aqui... Use {nome} para personalizar.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = GreenPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = { messageText += " {nome}" },
                            label = { Text("+ {nome}") }
                        )
                        SuggestionChip(
                            onClick = { messageText += " {telefone}" },
                            label = { Text("+ {telefone}") }
                        )
                    }
                }
            }

            // Destinatários (Contatos individuais ou em lote)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Destinatários (${contactsList.size})", fontWeight = FontWeight.Bold, color = TextPrimary)
                            TextButton(onClick = { showBatchDialog = true }) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Importar Lista / CSV", color = GreenPrimary, fontSize = 12.sp)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newPhoneInput,
                                onValueChange = { newPhoneInput = it },
                                placeholder = { Text("DDDNúmero (ex: 98991234567)") },
                                modifier = Modifier.weight(1.2f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = newNameInput,
                                onValueChange = { newNameInput = it },
                                placeholder = { Text("Nome (opcional)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    val p = newPhoneInput.filter { it.isDigit() }
                                    if (p.length >= 8) {
                                        contactsList.add(Pair(p, newNameInput.ifBlank { p }))
                                        newPhoneInput = ""
                                        newNameInput = ""
                                    } else {
                                        Toast.makeText(context, "Telefone inválido", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, color = DarkBackground)
                            }
                        }

                        if (contactsList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            contactsList.take(5).forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${item.second} (${item.first})", color = TextSecondary, fontSize = 12.sp)
                                    IconButton(
                                        onClick = { contactsList.removeAt(idx) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            if (contactsList.size > 5) {
                                Text("... e mais ${contactsList.size - 5} contatos", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Agendamento de Data e Hora
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    c.set(Calendar.YEAR, y)
                                    c.set(Calendar.MONTH, m)
                                    c.set(Calendar.DAY_OF_MONTH, d)
                                    selectedTimestamp = c.timeInMillis
                                },
                                c.get(Calendar.YEAR),
                                c.get(Calendar.MONTH),
                                c.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(dateFormat.format(Date(selectedTimestamp)), color = TextPrimary)
                    }

                    Button(
                        onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                            TimePickerDialog(
                                context,
                                { _, hour, min ->
                                    c.set(Calendar.HOUR_OF_DAY, hour)
                                    c.set(Calendar.MINUTE, min)
                                    selectedTimestamp = c.timeInMillis
                                },
                                c.get(Calendar.HOUR_OF_DAY),
                                c.get(Calendar.MINUTE),
                                true
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(timeFormat.format(Date(selectedTimestamp)), color = TextPrimary)
                    }
                }
            }

            // Repetição
            item {
                Text("Repetir envio:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RepeatMode.NONE to "Não",
                        RepeatMode.DAILY to "Diário",
                        RepeatMode.WEEKDAYS to "Seg-Sex",
                        RepeatMode.WEEKLY to "Semanal"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = repeatMode == mode,
                            onClick = { repeatMode = mode },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenPrimary,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }
            }

            // Delay Anti-Ban entre contatos
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Intervalo de Segurança (Anti-Ban):", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                        Text("${delaySeconds.toInt()} segundos", color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = delaySeconds,
                        onValueChange = { delaySeconds = it },
                        valueRange = 4f..45f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = GreenPrimary,
                            activeTrackColor = GreenPrimary,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                    Text(
                        "Recomendado: 8 a 15 segundos para listas grandes evitarem bloqueio de spam.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Botão Salvar Agendamento
            item {
                Button(
                    onClick = {
                        if (messageText.isBlank()) {
                            Toast.makeText(context, "Digite a mensagem para enviar", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (contactsList.isEmpty()) {
                            Toast.makeText(context, "Adicione pelo menos um contato", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedTimestamp <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Escolha um horário futuro", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Constrói JSON de destinatários
                        val jsonArr = JSONArray()
                        contactsList.forEach {
                            val obj = JSONObject()
                            obj.put("phone", it.first)
                            obj.put("name", it.second)
                            jsonArr.put(obj)
                        }

                        val schedule = ScheduledMessage(
                            title = title.ifBlank { "Envio para ${contactsList.size} contato(s)" },
                            text = messageText,
                            recipientsJson = jsonArr.toString(),
                            scheduledTimeMillis = selectedTimestamp,
                            whatsAppType = whatsAppType,
                            repeatMode = repeatMode,
                            delayBetweenContactsSeconds = delaySeconds.toInt(),
                            status = MessageStatus.PENDING
                        )

                        scope.launch {
                            val id = database.scheduledMessageDao().insertMessage(schedule)
                            val inserted = schedule.copy(id = id)
                            SchedulerManager.scheduleMessage(context, inserted)
                            Toast.makeText(context, "Agendado com sucesso!", Toast.LENGTH_LONG).show()
                            onSuccess()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agendar Mensagem", fontWeight = FontWeight.Bold, color = DarkBackground, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // Diálogo para Colar Lista / CSV
        if (showBatchDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDialog = false },
                title = { Text("Importar Contatos", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Cole os contatos abaixo. Formato aceito:\nTelefone, Nome (um por linha)\nExemplo:\n98991234567, Carlos\n98988776655, Maria",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = batchText,
                            onValueChange = { batchText = it },
                            placeholder = { Text("Cole aqui...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val lines = batchText.split("\n")
                        var added = 0
                        for (line in lines) {
                            val parts = line.split(",")
                            if (parts.isNotEmpty()) {
                                val p = parts[0].filter { it.isDigit() }
                                if (p.length >= 8) {
                                    val n = if (parts.size > 1) parts[1].trim() else p
                                    contactsList.add(Pair(p, n))
                                    added++
                                }
                            }
                        }
                        Toast.makeText(context, "$added contatos importados!", Toast.LENGTH_SHORT).show()
                        batchText = ""
                        showBatchDialog = false
                    }) {
                        Text("Importar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
