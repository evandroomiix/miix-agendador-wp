package com.miix.agendadorwp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.miix.agendadorwp.data.*
import com.miix.agendadorwp.scheduler.SchedulerManager
import com.miix.agendadorwp.service.AppUpdateManager
import com.miix.agendadorwp.service.CalendarSyncHelper
import com.miix.agendadorwp.service.ExecutionForegroundService
import com.miix.agendadorwp.service.HolidayCalendarHelper
import com.miix.agendadorwp.service.UpdateInfo
import com.miix.agendadorwp.service.WhatsAppAccessibilityService
import com.miix.agendadorwp.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    database: AppDatabase,
    onNavigateToNew: (WhatsAppType) -> Unit,
    onEditMessage: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 5 Abas: Pendentes, Feito, Falhado, Rascunhos, Excluído
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Pendentes", "Feito", "Falhado", "Rascunhos", "Excluído")

    val pendingMessages by database.scheduledMessageDao().getPendingMessagesFlow().collectAsState(initial = emptyList())
    val doneMessages by database.scheduledMessageDao().getDoneMessagesFlow().collectAsState(initial = emptyList())
    val failedMessages by database.scheduledMessageDao().getFailedMessagesFlow().collectAsState(initial = emptyList())
    val draftMessages by database.scheduledMessageDao().getDraftMessagesFlow().collectAsState(initial = emptyList())
    val deletedMessages by database.scheduledMessageDao().getDeletedMessagesFlow().collectAsState(initial = emptyList())

    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimization(context)) }

    // Diálogos de Ação
    var showPlatformSelectorDialog by remember { mutableStateOf(false) }
    var showHolidaysDialog by remember { mutableStateOf(false) }
    var viewingLogMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    var viewingDetailMessage by remember { mutableStateOf<ScheduledMessage?>(null) }

    // Atualização do Aplicativo (In-App Updater)
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    // Atualização em tempo real ao voltar das Configurações do Android
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                isBatteryOptIgnored = checkBatteryOptimization(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Miix Agendador WP", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                            Text("Versão PRO MAX v1.2 • Sem Limites", fontSize = 11.sp, color = GreenPrimary)
                        }
                    }
                },
                actions = {
                    // Botão Buscar Atualizações
                    IconButton(onClick = {
                        isCheckingUpdate = true
                        scope.launch {
                            val update = AppUpdateManager.checkForUpdates(context)
                            isCheckingUpdate = false
                            if (update != null) {
                                availableUpdate = update
                            } else {
                                Toast.makeText(context, "🎉 Você já está com a versão mais recente instalada!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GreenPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Buscar Atualizações", tint = GreenPrimary)
                        }
                    }

                    // Botão Sincronizar Calendário & Feriados Nacionais
                    IconButton(onClick = { showHolidaysDialog = true }) {
                        Icon(Icons.Default.Event, contentDescription = "Feriados & Calendário", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPlatformSelectorDialog = true },
                containerColor = GreenPrimary,
                contentColor = DarkBackground,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo Agendamento", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ScrollableTabRow com as 5 abas
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DarkSurface,
                contentColor = GreenPrimary,
                edgePadding = 12.dp
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val badgeCount = when (index) {
                        0 -> pendingMessages.size
                        1 -> doneMessages.size
                        2 -> failedMessages.size
                        3 -> draftMessages.size
                        4 -> deletedMessages.size
                        else -> 0
                    }
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                                if (badgeCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = if (selectedTabIndex == index) GreenPrimary else DarkCard,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            badgeCount.toString(),
                                            fontSize = 11.sp,
                                            color = if (selectedTabIndex == index) DarkBackground else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // ⚠️ GUIA PASSO A PASSO COMPLETO DE ACESSIBILIDADE E RESTRIÇÕES
                if (!isAccessibilityEnabled && selectedTabIndex == 0) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentOrange)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Ativação de Automação (Android 13/14+)",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentOrange,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No Android moderno, para que o menu dos 3 pontinhos apareça nas configurações do app, o Android exige que você toque primeiro na acessibilidade. Siga os 2 passos:",
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // PASSO 1: TOCAR NA ACESSIBILIDADE PARA O ANDROID REGISTRAR O PEDIDO
                                Surface(
                                    color = DarkCard,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("1º Passo: Registrar Tentativa no Android", fontWeight = FontWeight.Bold, color = AccentBlue, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Toque no botão abaixo, selecione 'Miix Agendador WP' e toque nele (vai dar a mensagem 'Acesso negado'). Toque em Fechar e volte aqui.",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Accessibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("1. Tocar em Acessibilidade", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // PASSO 2: ABRIR DETALHES E LIBERAR NOS 3 PONTINHOS
                                Surface(
                                    color = DarkCard,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("2º Passo: Liberar nos 3 Pontinhos", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Após o 1º passo, toque no botão abaixo. Agora os 3 pontinhos (⋮) no topo direito estarão visíveis! Toque neles e selecione 'Permitir configurações restritas'. Depois é só ligar a chave!",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Abra Configurações > Aplicativos > Miix Agendador", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("2. Liberar nos 3 Pontinhos", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Card de Atalho para Feriados & Calendário
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHolidaysDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Feriados Nacionais & Campanhas", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                    Text("Veja datas comemorativas e crie agendamentos em 1 clique", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }

                // Esvaziar Lixeira se estiver na aba de Excluídos
                if (selectedTabIndex == 4 && deletedMessages.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                scope.launch {
                                    database.scheduledMessageDao().emptyTrash()
                                    Toast.makeText(context, "🗑️ Lixeira esvaziada!", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Esvaziar Lixeira", color = AccentRed, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Lista de mensagens conforme a aba selecionada
                val currentList = when (selectedTabIndex) {
                    0 -> pendingMessages
                    1 -> doneMessages
                    2 -> failedMessages
                    3 -> draftMessages
                    4 -> deletedMessages
                    else -> emptyList()
                }

                if (currentList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    when (selectedTabIndex) {
                                        0 -> Icons.Default.Schedule
                                        1 -> Icons.Default.CheckCircle
                                        2 -> Icons.Default.Error
                                        3 -> Icons.Default.Edit
                                        else -> Icons.Default.Delete
                                    },
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    when (selectedTabIndex) {
                                        0 -> "Nenhum agendamento pendente"
                                        1 -> "Nenhum envio realizado ainda"
                                        2 -> "Nenhum envio falhado"
                                        3 -> "Nenhum rascunho salvo"
                                        else -> "A lixeira está vazia"
                                    },
                                    color = TextSecondary,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                } else {
                    items(currentList, key = { it.id }) { message ->
                        MessageItemCard(
                            message = message,
                            tabIndex = selectedTabIndex,
                            onClick = { viewingDetailMessage = message },
                            onEdit = { onEditMessage(message.id) },
                            onSoftDelete = {
                                scope.launch {
                                    database.scheduledMessageDao().softDelete(message.id)
                                    SchedulerManager.cancelMessage(context, message.id)
                                    Toast.makeText(context, "Movido para a Lixeira", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRestore = {
                                scope.launch {
                                    database.scheduledMessageDao().restoreMessage(message.id)
                                    SchedulerManager.scheduleMessage(context, message)
                                    Toast.makeText(context, "Restaurado para Pendentes!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onPermanentDelete = {
                                scope.launch {
                                    database.scheduledMessageDao().deleteMessage(message)
                                    Toast.makeText(context, "Excluído permanentemente", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSendNow = {
                                ExecutionForegroundService.startExecution(context, message.id)
                                Toast.makeText(context, "Iniciando envio agora...", Toast.LENGTH_SHORT).show()
                            },
                            onDuplicate = {
                                scope.launch {
                                    val copy = message.copy(
                                        id = 0,
                                        title = "${message.title} (Cópia)",
                                        scheduledTimeMillis = message.scheduledTimeMillis + 3600000L, // +1h
                                        status = MessageStatus.DRAFT,
                                        lastExecutionLog = ""
                                    )
                                    database.scheduledMessageDao().insertMessage(copy)
                                    Toast.makeText(context, "📑 Agendamento duplicado como Rascunho!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onTogglePause = {
                                scope.launch {
                                    if (message.status == MessageStatus.PAUSED) {
                                        database.scheduledMessageDao().resumeMessage(message.id)
                                        SchedulerManager.scheduleMessage(context, message)
                                        Toast.makeText(context, "▶️ Agendamento retomado!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        database.scheduledMessageDao().pauseMessage(message.id)
                                        SchedulerManager.cancelMessage(context, message.id)
                                        Toast.makeText(context, "⏸️ Agendamento pausado!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onViewLogs = {
                                viewingLogMessage = message
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // DIÁLOGO SELETOR DE APLICATIVO AO CLICAR EM "+ NOVO AGENDAMENTO"
    if (showPlatformSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showPlatformSelectorDialog = false },
            title = {
                Text("Escolha a Plataforma de Envio", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selecione onde deseja agendar a mensagem:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))

                    val options = listOf(
                        Triple(WhatsAppType.WHATSAPP_NORMAL, "💬 WhatsApp Oficial", GreenDark),
                        Triple(WhatsAppType.WHATSAPP_BUSINESS, "💼 WhatsApp Business", GreenSecondary),
                        Triple(WhatsAppType.WHATSAPP_MOD, "⚡ WhatsApp Mods (GB/FM/Yo/Plus/Aero)", Color(0xFF00897B)),
                        Triple(WhatsAppType.INSTAGRAM, "📸 Instagram Oficial", Color(0xFFE1306C)),
                        Triple(WhatsAppType.INSTAGRAM_MOD, "🌟 Instagram Mods (InstaPro/Instander/Honista)", Color(0xFFC13584)),
                        Triple(WhatsAppType.FACEBOOK, "📘 Facebook & Lite", Color(0xFF1877F2)),
                        Triple(WhatsAppType.META_BUSINESS, "🏢 Meta Business Suite", Color(0xFF0064E0))
                    )

                    options.forEach { (type, label, color) ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPlatformSelectorDialog = false
                                    onNavigateToNew(type)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = color,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlatformSelectorDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // DIÁLOGO DE FERIADOS NACIONAIS & SINCRONIZAÇÃO DE CALENDÁRIO
    if (showHolidaysDialog) {
        AlertDialog(
            onDismissRequest = { showHolidaysDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = GreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calendário & Feriados Nacionais", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Sincronizar com Google Agenda
                        Button(
                            onClick = {
                                scope.launch {
                                    var count = 0
                                    pendingMessages.forEach { msg ->
                                        val id = CalendarSyncHelper.syncEvent(context, msg.title, msg.text, msg.scheduledTimeMillis, msg.calendarEventId)
                                        if (id != null) count++
                                    }
                                    Toast.makeText(context, "Sincronizados $count agendamentos com sua Google Agenda!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sincronizar com a Google Agenda", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Datas Comemorativas do Brasil:", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                    }

                    items(HolidayCalendarHelper.BRAZIL_HOLIDAYS_2026_2027) { holiday ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(holiday.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                        Text("${holiday.day}/${holiday.month} • ${holiday.description}", fontSize = 11.sp, color = GreenPrimary)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                val ts = HolidayCalendarHelper.getHolidayTimestamp(holiday)
                                                val newMsg = ScheduledMessage(
                                                    title = "🎉 ${holiday.name}",
                                                    text = holiday.suggestedMessage,
                                                    recipientsJson = "[]",
                                                    scheduledTimeMillis = ts,
                                                    whatsAppType = WhatsAppType.WHATSAPP_NORMAL,
                                                    scheduleType = ScheduleType.ENGAGEMENT_CAMPAIGN,
                                                    status = MessageStatus.DRAFT
                                                )
                                                database.scheduledMessageDao().insertMessage(newMsg)
                                                Toast.makeText(context, "Criado rascunho para ${holiday.name}!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ Campanha", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHolidaysDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                    Text("Concluir", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface
        )
    }

    // DIÁLOGO DE VISUALIZAÇÃO DE LOGS
    viewingLogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewingLogMessage = null },
            title = { Text("Logs de Execução", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text("Campanha: ${msg.title}", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            msg.lastExecutionLog.ifBlank { "Nenhum log registrado para este agendamento até o momento." },
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewingLogMessage = null }, colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)) {
                    Text("Fechar", color = DarkBackground)
                }
            },
            containerColor = DarkSurface
        )
    }

    // 🌟 DIÁLOGO DE ATUALIZAÇÃO AUTOMÁTICA DO APLICATIVO
    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!isDownloadingUpdate) availableUpdate = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = GreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nova Versão Disponível!", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text("Versão: ${update.versionName}", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Novidades da Versão:", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(update.changelog, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                    }

                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Baixando atualização: ${(downloadProgress * 100).toInt()}%", fontSize = 12.sp, color = GreenPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = GreenPrimary
                        )
                    }
                }
            },
            confirmButton = {
                if (!isDownloadingUpdate) {
                    Button(
                        onClick = {
                            isDownloadingUpdate = true
                            scope.launch {
                                AppUpdateManager.downloadAndInstallApk(
                                    context = context,
                                    apkUrl = update.apkUrl,
                                    onProgress = { p -> downloadProgress = p },
                                    onError = { err ->
                                        isDownloadingUpdate = false
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        Text("Baixar e Atualizar Agora", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(onClick = { availableUpdate = null }) {
                        Text("Depois", color = TextSecondary)
                    }
                }
            },
            containerColor = DarkSurface
        )
    }

    // 🌟 DIÁLOGO COMPLETO DE DETALHES DO AGENDAMENTO (CLIQUE NO CARD)
    viewingDetailMessage?.let { detailMsg ->
        var showDetailMenu by remember { mutableStateOf(false) }

        val calNow = Calendar.getInstance()
        val calMsg = Calendar.getInstance().apply { timeInMillis = detailMsg.scheduledTimeMillis }

        val dayOfWeekFormat = remember { SimpleDateFormat("EEEE", Locale("pt", "BR")) }
        val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }

        val dayOfWeek = remember(detailMsg.scheduledTimeMillis) {
            dayOfWeekFormat.format(Date(detailMsg.scheduledTimeMillis)).replaceFirstChar { it.uppercase() }
        }
        val dateStr = remember(detailMsg.scheduledTimeMillis) {
            dateFormat.format(Date(detailMsg.scheduledTimeMillis))
        }
        val timeStr = remember(detailMsg.scheduledTimeMillis) {
            timeFormat.format(Date(detailMsg.scheduledTimeMillis))
        }

        val relativeLabel = remember(detailMsg.scheduledTimeMillis) {
            val isSameYear = calNow.get(Calendar.YEAR) == calMsg.get(Calendar.YEAR)
            val dayDiff = calMsg.get(Calendar.DAY_OF_YEAR) - calNow.get(Calendar.DAY_OF_YEAR)
            if (isSameYear && dayDiff == 0) {
                "Hoje"
            } else if (isSameYear && dayDiff == 1) {
                "Amanhã"
            } else if (isSameYear && dayDiff == -1) {
                "Ontem"
            } else if (calMsg.after(calNow)) {
                "Agendado no Futuro"
            } else {
                "Data Passada"
            }
        }

        val recipientsList = remember(detailMsg.recipientsJson) {
            val list = mutableListOf<Pair<String, String>>()
            try {
                val arr = JSONArray(detailMsg.recipientsJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val phone = obj.optString("phone", "")
                    val name = obj.optString("name", phone)
                    list.add(Pair(phone, name))
                }
            } catch (_: Exception) {}
            list
        }

        val pollOptionsList = remember(detailMsg.pollOptionsJson) {
            val list = mutableListOf<String>()
            try {
                val arr = JSONArray(detailMsg.pollOptionsJson)
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
            } catch (_: Exception) {}
            list
        }

        AlertDialog(
            onDismissRequest = { viewingDetailMessage = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Detalhes",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ✏️ LÁPIS PARA EDIÇÃO
                        IconButton(onClick = {
                            val id = detailMsg.id
                            viewingDetailMessage = null
                            onEditMessage(id)
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GreenPrimary)
                        }

                        // 🗑️ LIXEIRA PARA EXCLUIR
                        IconButton(onClick = {
                            val id = detailMsg.id
                            viewingDetailMessage = null
                            scope.launch {
                                if (selectedTabIndex == 4) {
                                    database.scheduledMessageDao().deleteMessage(detailMsg)
                                    Toast.makeText(context, "Excluído permanentemente!", Toast.LENGTH_SHORT).show()
                                } else {
                                    database.scheduledMessageDao().softDelete(id)
                                    SchedulerManager.cancelMessage(context, id)
                                    Toast.makeText(context, "Movido para a Lixeira!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = AccentRed)
                        }

                        // 3 PONTINHOS (MENU)
                        Box {
                            IconButton(onClick = { showDetailMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mais opções", tint = TextPrimary)
                            }

                            DropdownMenu(
                                expanded = showDetailMenu,
                                onDismissRequest = { showDetailMenu = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                // 1. Enviar Agora
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Send, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Enviar Agora", color = TextPrimary)
                                        }
                                    },
                                    onClick = {
                                        showDetailMenu = false
                                        viewingDetailMessage = null
                                        ExecutionForegroundService.startExecution(context, detailMsg.id)
                                        Toast.makeText(context, "Iniciando envio agora...", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 2. Duplicar
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Duplicar", color = TextPrimary)
                                        }
                                    },
                                    onClick = {
                                        showDetailMenu = false
                                        viewingDetailMessage = null
                                        scope.launch {
                                            val copy = detailMsg.copy(
                                                id = 0,
                                                title = "${detailMsg.title} (Cópia)",
                                                scheduledTimeMillis = detailMsg.scheduledTimeMillis + 3600000L,
                                                status = MessageStatus.DRAFT,
                                                lastExecutionLog = ""
                                            )
                                            database.scheduledMessageDao().insertMessage(copy)
                                            Toast.makeText(context, "📑 Duplicado como Rascunho!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                // 3. Pausa / Retomar
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (detailMsg.status == MessageStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (detailMsg.status == MessageStatus.PAUSED) "Retomar Agendamento" else "Pausar Agendamento", color = TextPrimary)
                                        }
                                    },
                                    onClick = {
                                        showDetailMenu = false
                                        viewingDetailMessage = null
                                        scope.launch {
                                            if (detailMsg.status == MessageStatus.PAUSED) {
                                                database.scheduledMessageDao().resumeMessage(detailMsg.id)
                                                SchedulerManager.scheduleMessage(context, detailMsg)
                                                Toast.makeText(context, "▶️ Agendamento retomado!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                database.scheduledMessageDao().pauseMessage(detailMsg.id)
                                                SchedulerManager.cancelMessage(context, detailMsg.id)
                                                Toast.makeText(context, "⏸️ Agendamento pausado!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )

                                // 4. Enviando Logs
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Enviando Logs", color = TextPrimary)
                                        }
                                    },
                                    onClick = {
                                        showDetailMenu = false
                                        viewingLogMessage = detailMsg
                                    }
                                )
                            }
                        }
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. TÍTULO E STATUS
                    item {
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    detailMsg.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Badge de Estado da Publicação
                                    Surface(
                                        color = when (detailMsg.status) {
                                            MessageStatus.DONE -> GreenPrimary.copy(alpha = 0.2f)
                                            MessageStatus.FAILED -> AccentRed.copy(alpha = 0.2f)
                                            MessageStatus.PAUSED -> AccentOrange.copy(alpha = 0.2f)
                                            MessageStatus.DRAFT -> TextSecondary.copy(alpha = 0.2f)
                                            MessageStatus.DELETED -> AccentRed.copy(alpha = 0.3f)
                                            else -> GreenPrimary.copy(alpha = 0.15f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            when (detailMsg.status) {
                                                MessageStatus.DONE -> "✅ Feito / Concluído"
                                                MessageStatus.FAILED -> "❌ Erro / Falhado"
                                                MessageStatus.PAUSED -> "⏸️ Pausado"
                                                MessageStatus.DRAFT -> "📝 Rascunho"
                                                MessageStatus.DELETED -> "🗑️ Excluído (Lixeira)"
                                                else -> "⏳ Pendente"
                                            },
                                            color = when (detailMsg.status) {
                                                MessageStatus.DONE -> GreenPrimary
                                                MessageStatus.FAILED -> AccentRed
                                                MessageStatus.PAUSED -> AccentOrange
                                                MessageStatus.DRAFT -> TextSecondary
                                                MessageStatus.DELETED -> AccentRed
                                                else -> GreenPrimary
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Badge da Plataforma
                                    Surface(
                                        color = when (detailMsg.whatsAppType) {
                                            WhatsAppType.WHATSAPP_BUSINESS -> GreenSecondary
                                            WhatsAppType.WHATSAPP_MOD -> Color(0xFF00897B)
                                            WhatsAppType.INSTAGRAM -> Color(0xFFE1306C)
                                            WhatsAppType.INSTAGRAM_MOD -> Color(0xFFC13584)
                                            WhatsAppType.FACEBOOK -> Color(0xFF1877F2)
                                            WhatsAppType.META_BUSINESS -> Color(0xFF0064E0)
                                            else -> GreenDark
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            when (detailMsg.whatsAppType) {
                                                WhatsAppType.WHATSAPP_BUSINESS -> "WP Business"
                                                WhatsAppType.WHATSAPP_MOD -> "WP Mod"
                                                WhatsAppType.INSTAGRAM -> "Instagram"
                                                WhatsAppType.INSTAGRAM_MOD -> "Insta Mod"
                                                WhatsAppType.FACEBOOK -> "Facebook"
                                                WhatsAppType.META_BUSINESS -> "Meta Suite"
                                                else -> "WhatsApp"
                                            },
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. DATA, HORÁRIO E DIA DA SEMANA
                    item {
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📅 Data e Horário Programado", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "$dayOfWeek, $dateStr às $timeStr",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Status: $relativeLabel",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // 3. PARA QUEM FOI (DESTINATÁRIOS)
                    item {
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("👥 Destinatários (Para Quem)", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 12.sp)
                                    Text(
                                        when (detailMsg.scheduleType) {
                                            ScheduleType.STATUS -> "Status / Stories"
                                            ScheduleType.POLL -> "Enquete (${recipientsList.size})"
                                            ScheduleType.BROADCAST_LIST -> "Lista Transmissão (${recipientsList.size})"
                                            ScheduleType.ENGAGEMENT_CAMPAIGN -> "Campanha (${recipientsList.size})"
                                            else -> "Total: ${recipientsList.size}"
                                        },
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                if (detailMsg.scheduleType == ScheduleType.STATUS) {
                                    Text("📱 Postagem programada no Status / Stories", color = TextPrimary, fontSize = 13.sp)
                                } else if (recipientsList.isEmpty()) {
                                    Text("Nenhum contato específico selecionado", color = TextSecondary, fontSize = 12.sp)
                                } else {
                                    recipientsList.take(15).forEach { (phone, name) ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (name.isNotBlank() && name != phone) "$name ($phone)" else phone, color = TextPrimary, fontSize = 12.sp)
                                        }
                                    }
                                    if (recipientsList.size > 15) {
                                        Text("+ ${recipientsList.size - 15} outros destinatários...", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 4. MENSAGEM / ENQUETE
                    item {
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("💬 Conteúdo da Mensagem", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))

                                if (detailMsg.scheduleType == ScheduleType.POLL) {
                                    Text("📊 Pergunta: ${detailMsg.pollQuestion}", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    pollOptionsList.forEachIndexed { idx, opt ->
                                        Text("${idx + 1}. $opt", color = TextSecondary, fontSize = 12.sp)
                                    }
                                } else {
                                    Text(
                                        detailMsg.text.ifBlank { "(Sem texto digitado)" },
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // 5. ANEXO (CASO TENHA)
                    item {
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📎 Anexo", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))

                                if (detailMsg.attachmentType != AttachmentType.NONE) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            when (detailMsg.attachmentType) {
                                                AttachmentType.IMAGE_VIDEO -> Icons.Default.PhotoLibrary
                                                AttachmentType.DOCUMENT -> Icons.Default.Description
                                                AttachmentType.AUDIO -> Icons.Default.AudioFile
                                                AttachmentType.VOICE_NOTE -> Icons.Default.Mic
                                                else -> Icons.Default.AttachFile
                                            },
                                            contentDescription = null,
                                            tint = GreenPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            detailMsg.attachmentName ?: "Arquivo anexado",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                } else {
                                    Text("Nenhum anexo adicionado", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 6. CONFIGURAÇÕES PRO MAX
                    item {
                        Surface(
                            color = DarkBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("⚙️ Configurações Avançadas", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• Perguntar antes de enviar: ${if (detailMsg.askBeforeSending) "Sim" else "Não"}", color = TextSecondary, fontSize = 12.sp)
                                Text("• Sincronizar Google Agenda: ${if (detailMsg.calendarSync) "Sim" else "Não"}", color = TextSecondary, fontSize = 12.sp)
                                Text("• Repetição: ${when (detailMsg.repeatMode) {
                                    RepeatMode.DAILY -> "Diariamente"
                                    RepeatMode.WEEKDAYS -> "Dias úteis (Seg-Sex)"
                                    RepeatMode.WEEKLY -> "Semanalmente"
                                    RepeatMode.MONTHLY -> "Mensalmente"
                                    else -> "Nenhuma"
                                }}", color = TextSecondary, fontSize = 12.sp)
                                Text("• Intervalo anti-ban entre contatos: ${detailMsg.delayBetweenContactsSeconds}s", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // 7. LOGS DE EXECUÇÃO
                    if (detailMsg.lastExecutionLog.isNotBlank()) {
                        item {
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("📋 Logs do Envio", fontWeight = FontWeight.Bold, color = if (detailMsg.status == MessageStatus.FAILED) AccentRed else GreenPrimary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(detailMsg.lastExecutionLog, color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewingDetailMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Fechar", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun MessageItemCard(
    message: ScheduledMessage,
    tabIndex: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onSoftDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onSendNow: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePause: () -> Unit,
    onViewLogs: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")) }
    val dataHoraFormatada = remember(message.scheduledTimeMillis) {
        dateFormat.format(Date(message.scheduledTimeMillis))
    }

    var showMenu by remember { mutableStateOf(false) }

    val recipientsCount = remember(message.recipientsJson) {
        try {
            JSONArray(message.recipientsJson).length()
        } catch (e: Exception) {
            1
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Linha do Título, Badges da Plataforma e Menu 3 Pontinhos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message.title,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badge da Plataforma
                    Surface(
                        color = when (message.whatsAppType) {
                            WhatsAppType.WHATSAPP_BUSINESS -> GreenSecondary
                            WhatsAppType.WHATSAPP_MOD -> Color(0xFF00897B)
                            WhatsAppType.INSTAGRAM -> Color(0xFFE1306C)
                            WhatsAppType.INSTAGRAM_MOD -> Color(0xFFC13584)
                            WhatsAppType.FACEBOOK -> Color(0xFF1877F2)
                            WhatsAppType.META_BUSINESS -> Color(0xFF0064E0)
                            else -> GreenDark
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            when (message.whatsAppType) {
                                WhatsAppType.WHATSAPP_BUSINESS -> "WP Business"
                                WhatsAppType.WHATSAPP_MOD -> "WP Mod"
                                WhatsAppType.INSTAGRAM -> "Instagram"
                                WhatsAppType.INSTAGRAM_MOD -> "Insta Mod"
                                WhatsAppType.FACEBOOK -> "Facebook"
                                WhatsAppType.META_BUSINESS -> "Meta Suite"
                                else -> "WhatsApp"
                            },
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 🌟 MENU DE 3 PONTINHOS COMPLETO
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mais Opções", tint = TextPrimary)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            // 1. EDITAR
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Edit, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Editar", color = TextPrimary) } },
                                onClick = { showMenu = false; onEdit() }
                            )

                            // 2. ENVIAR AGORA
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Send, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Enviar Agora", color = TextPrimary) } },
                                onClick = { showMenu = false; onSendNow() }
                            )

                            // 3. DUPLICAR
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Duplicar", color = TextPrimary) } },
                                onClick = { showMenu = false; onDuplicate() }
                            )

                            // 4. PAUSAR / RETOMAR
                            if (tabIndex == 0) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (message.status == MessageStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (message.status == MessageStatus.PAUSED) "Retomar Agendamento" else "Pausar Agendamento", color = TextPrimary)
                                        }
                                    },
                                    onClick = { showMenu = false; onTogglePause() }
                                )
                            }

                            // 5. VER LOGS
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Ver Logs de Envio", color = TextPrimary) } },
                                onClick = { showMenu = false; onViewLogs() }
                            )

                            Divider(color = DarkBorder)

                            // 6. EXCLUIR / MOVER PARA LIXEIRA
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(if (tabIndex == 4) "Excluir Definitivamente" else "Excluir", color = AccentRed) } },
                                onClick = {
                                    showMenu = false
                                    if (tabIndex == 4) onPermanentDelete() else onSoftDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Badges secundárias
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.status == MessageStatus.PAUSED) {
                    Surface(color = AccentOrange.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("⏸️ Pausado", color = AccentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }

                if (message.scheduleType != ScheduleType.CONTACT_GROUP) {
                    Surface(color = DarkSurface, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            when (message.scheduleType) {
                                ScheduleType.STATUS -> "📱 Status/Stories"
                                ScheduleType.POLL -> "📊 Enquete"
                                ScheduleType.BROADCAST_LIST -> "📢 Lista Transmissão"
                                ScheduleType.ENGAGEMENT_CAMPAIGN -> "⚡ Campanha"
                                else -> ""
                            },
                            color = GreenPrimary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (message.attachmentType != AttachmentType.NONE) {
                    Surface(color = DarkSurface, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            when (message.attachmentType) {
                                AttachmentType.IMAGE_VIDEO -> "📷 Mídia"
                                AttachmentType.DOCUMENT -> "📄 Doc"
                                AttachmentType.AUDIO -> "🎵 Áudio"
                                AttachmentType.VOICE_NOTE -> "🎙️ Voz"
                                else -> ""
                            },
                            color = AccentOrange,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (message.askBeforeSending) {
                    Surface(color = DarkSurface, shape = RoundedCornerShape(4.dp)) {
                        Text("🔔 Perguntar antes", color = AccentBlue, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                message.text.ifBlank { if (message.scheduleType == ScheduleType.POLL) message.pollQuestion else "(Sem mensagem de texto)" },
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 2
            )

            if (message.lastExecutionLog.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    message.lastExecutionLog,
                    color = if (tabIndex == 2) AccentRed else GreenPrimary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Linha Inferior com Horário, Destinatários e Ações Rápidas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(dataHoraFormatada, color = TextPrimary, fontSize = 12.sp)

                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("$recipientsCount", color = TextSecondary, fontSize = 12.sp)
                }

                // Ações Rápidas por Aba
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (tabIndex) {
                        0 -> { // Pendentes
                            IconButton(onClick = onSendNow, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Send, contentDescription = "Enviar Agora", tint = GreenPrimary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onSoftDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        1 -> { // Feito
                            IconButton(onClick = onSendNow, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reenviar", tint = GreenPrimary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onSoftDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Mover para Lixeira", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        2 -> { // Falhado
                            IconButton(onClick = onSendNow, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Tentar novamente", tint = AccentOrange, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onSoftDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Mover para Lixeira", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        3 -> { // Rascunhos
                            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GreenPrimary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onSoftDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Excluir Rascunho", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        4 -> { // Excluído (Lixeira)
                            IconButton(onClick = onRestore, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Restaurar", tint = GreenPrimary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onPermanentDelete, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Excluir Definitivamente", tint = AccentRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (WhatsAppAccessibilityService.isServiceRunning) return true
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.contains(context.packageName, ignoreCase = true)) {
                return true
            }
        }
        false
    } catch (e: Exception) {
        WhatsAppAccessibilityService.isServiceRunning
    }
}

fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
