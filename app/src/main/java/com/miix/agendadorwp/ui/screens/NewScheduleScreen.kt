package com.miix.agendadorwp.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.miix.agendadorwp.service.AudioRecorderHelper
import com.miix.agendadorwp.service.CalendarSyncHelper
import com.miix.agendadorwp.service.PlatformHelper
import com.miix.agendadorwp.service.WhatsAppAccessibilityService
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
    var scheduleType by remember { mutableStateOf(ScheduleType.CONTACT_GROUP) }
    var repeatMode by remember { mutableStateOf(RepeatMode.NONE) }
    var delaySeconds by remember { mutableFloatStateOf(10f) }

    // Opções PRO MAX
    var askBeforeSending by remember { mutableStateOf(false) }
    var checkTimeConflict by remember { mutableStateOf(true) }
    var calendarSync by remember { mutableStateOf(false) }
    var hasConflictDetected by remember { mutableStateOf(false) }
    var conflictMessageInfo by remember { mutableStateOf("") }

    // Enquete
    var pollQuestion by remember { mutableStateOf("") }
    val pollOptions = remember { mutableStateListOf("Sim", "Não") }

    // Anexos
    var attachmentType by remember { mutableStateOf(AttachmentType.NONE) }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentName by remember { mutableStateOf("") }

    // Gravador de Voz
    val audioRecorder = remember { AudioRecorderHelper(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }

    // Destinatários: Pair(Telefone/Identificador, Nome)
    val contactsList = remember { mutableStateListOf<Pair<String, String>>() }
    var newPhoneInput by remember { mutableStateOf("") }
    var newNameInput by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var batchText by remember { mutableStateOf("") }

    // Data e Hora selecionadas
    val calendar = remember {
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, 5) // Padrão: 5 minutos no futuro
        }
    }
    var selectedTimestamp by remember { mutableLongStateOf(calendar.timeInMillis) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }

    // Activity Launchers para Arquivos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachmentUri = uri
            attachmentType = AttachmentType.IMAGE_VIDEO
            attachmentName = uri.lastPathSegment ?: "Imagem / Vídeo"
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachmentUri = uri
            attachmentType = AttachmentType.DOCUMENT
            attachmentName = uri.lastPathSegment ?: "Documento"
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachmentUri = uri
            attachmentType = AttachmentType.AUDIO
            attachmentName = uri.lastPathSegment ?: "Áudio"
        }
    }

    // Seletor de Contatos da Agenda do Telefone
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                        val name = if (nameIndex >= 0) it.getString(nameIndex) ?: "" else ""
                        val id = if (idIndex >= 0) it.getString(idIndex) ?: "" else ""

                        var phone = ""
                        val phoneCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )
                        phoneCursor?.use { pc ->
                            if (pc.moveToFirst()) {
                                val pIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (pIndex >= 0) phone = pc.getString(pIndex) ?: ""
                            }
                        }

                        if (phone.isNotBlank() || name.isNotBlank()) {
                            contactsList.add(Pair(phone.ifBlank { name }, name.ifBlank { phone }))
                            Toast.makeText(context, "Contato adicionado: $name", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao ler contato: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Checagem de Conflito de Tempo
    fun verifyTimeConflict(timeMillis: Long) {
        if (!checkTimeConflict) {
            hasConflictDetected = false
            return
        }
        scope.launch {
            val rangeMs = 3 * 60 * 1000L // 3 minutos de margem
            val conflicts = database.scheduledMessageDao().findTimeConflicts(timeMillis - rangeMs, timeMillis + rangeMs)
            if (conflicts.isNotEmpty()) {
                hasConflictDetected = true
                val first = conflicts[0]
                conflictMessageInfo = "'${first.title}' às ${timeFormat.format(Date(first.scheduledTimeMillis))}"
            } else {
                hasConflictDetected = false
            }
        }
    }

    LaunchedEffect(selectedTimestamp, checkTimeConflict) {
        verifyTimeConflict(selectedTimestamp)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo Agendamento PRO", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // 1. Título da Mensagem
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome / Título da Postagem") },
                    placeholder = { Text("Ex: Flyer Promocional, Enquete do Grupo...") },
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

            // 2. Plataforma Alvo (WhatsApp, Business, Mods, Instagram, Facebook, Meta)
            item {
                Column {
                    Text("Plataforma Alvo:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val platforms = listOf(
                            Triple(WhatsAppType.WHATSAPP_NORMAL, "WhatsApp", GreenDark),
                            Triple(WhatsAppType.WHATSAPP_BUSINESS, "WP Business", GreenSecondary),
                            Triple(WhatsAppType.WHATSAPP_MOD, "WP Mod (GB/FM)", Color(0xFF00897B)),
                            Triple(WhatsAppType.INSTAGRAM, "Instagram", Color(0xFFE1306C)),
                            Triple(WhatsAppType.FACEBOOK, "Facebook", Color(0xFF1877F2)),
                            Triple(WhatsAppType.META_BUSINESS, "Meta Suite", Color(0xFF0064E0))
                        )
                        items(platforms) { (type, label, color) ->
                            FilterChip(
                                selected = whatsAppType == type,
                                onClick = { whatsAppType = type },
                                label = { Text(label, fontWeight = if (whatsAppType == type) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color,
                                    selectedLabelColor = Color.White,
                                    containerColor = DarkCard,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // 3. Tipo de Agendamento (Contatos/Grupos, Status, Enquete, Lista de Transmissão, Campanha)
            item {
                Column {
                    Text("Tipo de Agendamento:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val types = listOf(
                            Pair(ScheduleType.CONTACT_GROUP, "👥 Contatos / Grupos"),
                            Pair(ScheduleType.STATUS, "📱 Status / Stories"),
                            Pair(ScheduleType.POLL, "📊 Enquete"),
                            Pair(ScheduleType.BROADCAST_LIST, "📢 Lista Transmissão"),
                            Pair(ScheduleType.ENGAGEMENT_CAMPAIGN, "⚡ Campanha Engajamento")
                        )
                        items(types) { (type, label) ->
                            FilterChip(
                                selected = scheduleType == type,
                                onClick = { scheduleType = type },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GreenPrimary,
                                    selectedLabelColor = DarkBackground,
                                    containerColor = DarkCard,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // 4. Se for ENQUETE: Pergunta + Opções
            if (scheduleType == ScheduleType.POLL) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Configuração da Enquete", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pollQuestion,
                                onValueChange = { pollQuestion = it },
                                label = { Text("Pergunta da Enquete") },
                                placeholder = { Text("Qual o melhor horário?") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Opções de Resposta:", fontSize = 12.sp, color = TextSecondary)
                            pollOptions.forEachIndexed { index, option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = option,
                                        onValueChange = { pollOptions[index] = it },
                                        label = { Text("Opção ${index + 1}") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (pollOptions.size > 2) {
                                        IconButton(onClick = { pollOptions.removeAt(index) }) {
                                            Icon(Icons.Default.RemoveCircle, contentDescription = "Remover", tint = AccentRed)
                                        }
                                    }
                                }
                            }
                            if (pollOptions.size < 10) {
                                TextButton(onClick = { pollOptions.add("Nova Opção") }) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = GreenPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Adicionar Opção", color = GreenPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Destinatários (Contatos / Grupos) - Não obrigatório se for Status
            if (scheduleType != ScheduleType.STATUS) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Destinatários (${contactsList.size})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)

                                // Botão "+" para Capturar do WhatsApp ou Selecionar da Agenda
                                Box {
                                    FilledTonalButton(
                                        onClick = { showAddMenu = true },
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = GreenPrimary.copy(alpha = 0.2f))
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Destinatário", color = GreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    DropdownMenu(
                                        expanded = showAddMenu,
                                        onDismissRequest = { showAddMenu = false },
                                        modifier = Modifier.background(DarkSurface)
                                    ) {
                                        // 🌟 CAPTURAR DIRETO DO WHATSAPP
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("📲 Capturar do WhatsApp / Grupo", fontWeight = FontWeight.Bold, color = GreenPrimary)
                                                    Text("Abre o WhatsApp e captura o contato ou grupo que você abrir", fontSize = 11.sp, color = TextSecondary)
                                                }
                                            },
                                            onClick = {
                                                showAddMenu = false
                                                val targetPkg = when (whatsAppType) {
                                                    WhatsAppType.WHATSAPP_BUSINESS -> "com.whatsapp.w4b"
                                                    WhatsAppType.WHATSAPP_MOD -> "com.gbwhatsapp"
                                                    else -> "com.whatsapp"
                                                }
                                                WhatsAppAccessibilityService.isCaptureModeActive = true
                                                WhatsAppAccessibilityService.onContactCapturedListener = { captured ->
                                                    contactsList.add(Pair(captured, captured))
                                                    Toast.makeText(context, "✅ Adicionado: $captured", Toast.LENGTH_SHORT).show()
                                                }
                                                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                                                if (launchIntent != null) {
                                                    context.startActivity(launchIntent)
                                                    Toast.makeText(context, "👉 Abra a conversa ou grupo que deseja agendar!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Aplicativo não encontrado no aparelho.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )

                                        // SELECIONAR DA AGENDA NATIVA
                                        DropdownMenuItem(
                                            text = { Text("📖 Buscar na Agenda de Contatos", color = TextPrimary) },
                                            onClick = {
                                                showAddMenu = false
                                                contactPickerLauncher.launch(null)
                                            }
                                        )

                                        // IMPORTAR EM MASSA
                                        DropdownMenuItem(
                                            text = { Text("📋 Colar Lista em Massa", color = TextPrimary) },
                                            onClick = {
                                                showAddMenu = false
                                                showBatchDialog = true
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Entrada Manual Rápida
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newPhoneInput,
                                    onValueChange = { newPhoneInput = it },
                                    label = { Text("Telefone ou Nome do Grupo") },
                                    placeholder = { Text("5511999999999 ou Nome") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (newPhoneInput.isNotBlank()) {
                                            contactsList.add(Pair(newPhoneInput.trim(), newPhoneInput.trim()))
                                            newPhoneInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Adicionar", tint = GreenPrimary)
                                }
                            }

                            // Chips com os contatos adicionados
                            if (contactsList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(contactsList) { (phone, name) ->
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(name, fontSize = 12.sp, color = TextPrimary) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remover",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(14.dp).clickable { contactsList.remove(Pair(phone, name)) }
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurface)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Mensagem / Descrição do Post
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mensagem / Descrição:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                        // Tags dinâmicas para engajamento
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("{nome}", "{saudacao}").forEach { tag ->
                                SuggestionChip(
                                    onClick = { messageText += " $tag " },
                                    label = { Text(tag, fontSize = 10.sp, color = GreenPrimary) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Digite sua mensagem aqui... Ex: Olá {nome}, segue nosso flyer!") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }

            // 7. Campo ANEXO (Imagem/Vídeo, Documento, Áudio, Gravar Voz)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Anexos de Mídia & Documentos", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Botões de escolha do anexo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { mediaPickerLauncher.launch("image/*,video/*") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                                    Text("Imagem/Vídeo", fontSize = 10.sp, color = TextPrimary)
                                }
                            }

                            OutlinedButton(
                                onClick = { docPickerLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                                    Text("Documento", fontSize = 10.sp, color = TextPrimary)
                                }
                            }

                            OutlinedButton(
                                onClick = { audioPickerLauncher.launch("audio/*") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                    Text("Áudio", fontSize = 10.sp, color = TextPrimary)
                                }
                            }

                            // Botão Gravar Voz
                            OutlinedButton(
                                onClick = {
                                    if (!isRecordingVoice) {
                                        val started = audioRecorder.startRecording()
                                        if (started) {
                                            isRecordingVoice = true
                                            Toast.makeText(context, "🎙️ Gravando voz... Toque novamente para salvar!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Permissão de microfone necessária.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val file = audioRecorder.stopRecording()
                                        isRecordingVoice = false
                                        if (file != null && file.exists()) {
                                            attachmentUri = Uri.fromFile(file)
                                            attachmentType = AttachmentType.VOICE_NOTE
                                            attachmentName = "Gravação de Voz (${file.name})"
                                            Toast.makeText(context, "✅ Áudio gravado com sucesso!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = if (isRecordingVoice) ButtonDefaults.outlinedButtonColors(containerColor = AccentRed.copy(alpha = 0.2f)) else ButtonDefaults.outlinedButtonColors(),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, tint = if (isRecordingVoice) AccentRed else GreenPrimary, modifier = Modifier.size(18.dp))
                                    Text(if (isRecordingVoice) "Parar" else "Gravar Voz", fontSize = 10.sp, color = if (isRecordingVoice) AccentRed else TextPrimary)
                                }
                            }
                        }

                        // Preview do anexo selecionado
                        if (attachmentUri != null && attachmentType != AttachmentType.NONE) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            when (attachmentType) {
                                                AttachmentType.IMAGE_VIDEO -> Icons.Default.Image
                                                AttachmentType.DOCUMENT -> Icons.Default.Description
                                                AttachmentType.AUDIO -> Icons.Default.AudioFile
                                                AttachmentType.VOICE_NOTE -> Icons.Default.Mic
                                                else -> Icons.Default.AttachFile
                                            },
                                            contentDescription = null,
                                            tint = GreenPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(attachmentName, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = {
                                            attachmentUri = null
                                            attachmentType = AttachmentType.NONE
                                            attachmentName = ""
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = AccentRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Data, Hora e Alerta de Conflito de Tempo
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Data e Horário do Envio", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(dateFormat.format(Date(selectedTimestamp)), color = TextPrimary)
                            }

                            OutlinedButton(
                                onClick = {
                                    val c = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                                    TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            c.set(Calendar.HOUR_OF_DAY, h)
                                            c.set(Calendar.MINUTE, min)
                                            c.set(Calendar.SECOND, 0)
                                            selectedTimestamp = c.timeInMillis
                                        },
                                        c.get(Calendar.HOUR_OF_DAY),
                                        c.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(timeFormat.format(Date(selectedTimestamp)), color = TextPrimary)
                            }
                        }

                        // Alerta Visual de Conflito de Tempo
                        if (hasConflictDetected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = AccentOrange.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Conflito de Horário! Já existe envio agendado: $conflictMessageInfo",
                                            fontSize = 11.sp,
                                            color = AccentOrange
                                        )
                                    }
                                    TextButton(onClick = {
                                        selectedTimestamp += 5 * 60 * 1000L // Adiciona 5 min
                                    }) {
                                        Text("+5 min", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 9. Opções Avançadas PRO MAX: Perguntar Antes, Conflito, Calendário, Anti-ban
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Opções Avançadas (PRO MAX)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)

                        // Perguntar-me antes de enviar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("🔔 Perguntar-me antes de enviar", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                                Text("Exibe notificação interativa com botões 'Enviar' ou 'Adiar'", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = askBeforeSending,
                                onCheckedChange = { askBeforeSending = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary)
                            )
                        }

                        Divider(color = DarkBorder)

                        // Verificar Conflito de Tempo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⚠️ Verificar conflito de tempo", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                                Text("Alerta se já houver mensagens no mesmo horário", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = checkTimeConflict,
                                onCheckedChange = { checkTimeConflict = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary)
                            )
                        }

                        Divider(color = DarkBorder)

                        // Sincronizar Calendários
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("📅 Sincronizar com Calendário", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                                Text("Salva automaticamente na Google Agenda / Calendário Android", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = calendarSync,
                                onCheckedChange = { calendarSync = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary)
                            )
                        }

                        Divider(color = DarkBorder)

                        // Intervalo Anti-Ban
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🛡️ Atraso Anti-Bloqueio (Anti-Ban):", fontSize = 13.sp, color = TextPrimary)
                                Text("${delaySeconds.toInt()}s entre cada contato", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            }
                            Slider(
                                value = delaySeconds,
                                onValueChange = { delaySeconds = it },
                                valueRange = 3f..60f,
                                steps = 11,
                                colors = SliderDefaults.colors(thumbColor = GreenPrimary, activeTrackColor = GreenPrimary)
                            )
                        }
                    }
                }
            }

            // 10. Botões de Ação: "Salvar como Rascunho" e "Agendar Envio"
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Botão Salvar como Rascunho
                    OutlinedButton(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Informe pelo menos um título para salvar como rascunho.", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }

                            scope.launch {
                                val recipientsJson = JSONArray().apply {
                                    contactsList.forEach { (phone, name) ->
                                        put(JSONObject().apply {
                                            put("phone", phone)
                                            put("name", name)
                                        })
                                    }
                                }.toString()

                                val pollOptionsJson = JSONArray(pollOptions).toString()

                                val draftMessage = ScheduledMessage(
                                    title = title,
                                    text = messageText,
                                    recipientsJson = recipientsJson,
                                    scheduledTimeMillis = selectedTimestamp,
                                    whatsAppType = whatsAppType,
                                    scheduleType = scheduleType,
                                    attachmentType = attachmentType,
                                    attachmentUri = attachmentUri?.toString(),
                                    attachmentName = attachmentName,
                                    pollQuestion = pollQuestion,
                                    pollOptionsJson = pollOptionsJson,
                                    askBeforeSending = askBeforeSending,
                                    checkTimeConflict = checkTimeConflict,
                                    calendarSync = calendarSync,
                                    delayBetweenContactsSeconds = delaySeconds.toInt(),
                                    status = MessageStatus.DRAFT
                                )

                                database.scheduledMessageDao().insertMessage(draftMessage)
                                Toast.makeText(context, "💾 Salvo como Rascunho!", Toast.LENGTH_SHORT).show()
                                onSuccess()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Salvar Rascunho", color = TextPrimary)
                    }

                    // Botão Confirmar Agendamento
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Informe um título ou nome para o agendamento.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (scheduleType != ScheduleType.STATUS && contactsList.isEmpty()) {
                                Toast.makeText(context, "Adicione pelo menos 1 contato ou grupo.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (messageText.isBlank() && attachmentUri == null && scheduleType != ScheduleType.POLL) {
                                Toast.makeText(context, "Digite uma mensagem ou selecione um anexo.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (scheduleType == ScheduleType.POLL && pollQuestion.isBlank()) {
                                Toast.makeText(context, "Digite a pergunta da enquete.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch {
                                val recipientsJson = JSONArray().apply {
                                    contactsList.forEach { (phone, name) ->
                                        put(JSONObject().apply {
                                            put("phone", phone)
                                            put("name", name)
                                        })
                                    }
                                }.toString()

                                val pollOptionsJson = JSONArray(pollOptions).toString()

                                // Sincronização opcional com o calendário
                                var calendarEventId: Long? = null
                                if (calendarSync) {
                                    calendarEventId = CalendarSyncHelper.syncEvent(
                                        context,
                                        title,
                                        messageText.ifBlank { "Postagem agendada no Miix Agendador" },
                                        selectedTimestamp
                                    )
                                }

                                val scheduledMessage = ScheduledMessage(
                                    title = title,
                                    text = messageText,
                                    recipientsJson = recipientsJson,
                                    scheduledTimeMillis = selectedTimestamp,
                                    whatsAppType = whatsAppType,
                                    scheduleType = scheduleType,
                                    attachmentType = attachmentType,
                                    attachmentUri = attachmentUri?.toString(),
                                    attachmentName = attachmentName,
                                    pollQuestion = pollQuestion,
                                    pollOptionsJson = pollOptionsJson,
                                    askBeforeSending = askBeforeSending,
                                    checkTimeConflict = checkTimeConflict,
                                    calendarSync = calendarSync,
                                    calendarEventId = calendarEventId,
                                    repeatMode = repeatMode,
                                    delayBetweenContactsSeconds = delaySeconds.toInt(),
                                    status = MessageStatus.PENDING
                                )

                                val newId = database.scheduledMessageDao().insertMessage(scheduledMessage)
                                val finalMessage = scheduledMessage.copy(id = newId)
                                SchedulerManager.scheduleMessage(context, finalMessage)

                                Toast.makeText(context, "🚀 Agendamento realizado com sucesso!", Toast.LENGTH_SHORT).show()
                                onSuccess()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Agendar Envio", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Diálogo de Importação em Massa de Contatos
    if (showBatchDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            title = { Text("Importar Contatos em Massa", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text("Cole telefones separados por vírgula ou um por linha (com DDD):", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = batchText,
                        onValueChange = { batchText = it },
                        placeholder = { Text("5511999999999\n5521888888888\n5531777777777") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lines = batchText.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                        lines.forEach { phone ->
                            contactsList.add(Pair(phone, phone))
                        }
                        showBatchDialog = false
                        batchText = ""
                        Toast.makeText(context, "${lines.size} contatos adicionados!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Importar", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
