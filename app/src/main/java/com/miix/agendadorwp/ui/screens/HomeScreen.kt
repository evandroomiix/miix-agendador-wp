package com.miix.agendadorwp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miix.agendadorwp.data.*
import com.miix.agendadorwp.scheduler.SchedulerManager
import com.miix.agendadorwp.service.CalendarSyncHelper
import com.miix.agendadorwp.service.ExecutionForegroundService
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
    onNavigateToNew: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 5 Abas solicitadas: Pendentes, Feito, Falhado, Rascunhos, Excluído
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Pendentes", "Feito", "Falhado", "Rascunhos", "Excluído")

    val pendingMessages by database.scheduledMessageDao().getPendingMessagesFlow().collectAsState(initial = emptyList())
    val doneMessages by database.scheduledMessageDao().getDoneMessagesFlow().collectAsState(initial = emptyList())
    val failedMessages by database.scheduledMessageDao().getFailedMessagesFlow().collectAsState(initial = emptyList())
    val draftMessages by database.scheduledMessageDao().getDraftMessagesFlow().collectAsState(initial = emptyList())
    val deletedMessages by database.scheduledMessageDao().getDeletedMessagesFlow().collectAsState(initial = emptyList())

    var isAccessibilityEnabled by remember { mutableStateOf(WhatsAppAccessibilityService.isServiceRunning) }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimization(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Miix Agendador WP", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                            Text("Versão PRO MAX • Sem Limites", fontSize = 11.sp, color = GreenPrimary)
                        }
                    }
                },
                actions = {
                    // Botão Sincronizar Calendários
                    IconButton(onClick = {
                        scope.launch {
                            var syncedCount = 0
                            pendingMessages.forEach { msg ->
                                val eventId = CalendarSyncHelper.syncEvent(
                                    context,
                                    msg.title,
                                    msg.text,
                                    msg.scheduledTimeMillis,
                                    msg.calendarEventId
                                )
                                if (eventId != null) {
                                    database.scheduledMessageDao().updateMessage(msg.copy(calendarEventId = eventId, calendarSync = true))
                                    syncedCount++
                                }
                            }
                            Toast.makeText(
                                context,
                                if (syncedCount > 0) "✅ $syncedCount eventos sincronizados com a Google Agenda!" else "📅 Calendário atualizado (permissão necessária)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Sincronizar Calendários", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToNew,
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

                // Alertas de Permissão (Acessibilidade e Bateria)
                if (!isAccessibilityEnabled && selectedTabIndex == 0) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Acessibilidade Desativada", fontWeight = FontWeight.Bold, color = AccentOrange, fontSize = 15.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Para envio automático sem toque na tela, ative a acessibilidade do Miix Agendador.",
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                                ) {
                                    Text("Ativar Acessibilidade", color = DarkBackground, fontWeight = FontWeight.Bold)
                                }
                            }
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
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun MessageItemCard(
    message: ScheduledMessage,
    tabIndex: Int,
    onSoftDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onSendNow: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")) }
    val dataHoraFormatada = remember(message.scheduledTimeMillis) {
        dateFormat.format(Date(message.scheduledTimeMillis))
    }

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
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Linha do Título e Badges da Plataforma / Tipo
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

                // Badges de Plataforma
                Surface(
                    color = when (message.whatsAppType) {
                        WhatsAppType.WHATSAPP_BUSINESS -> GreenSecondary
                        WhatsAppType.WHATSAPP_MOD -> Color(0xFF00897B)
                        WhatsAppType.INSTAGRAM -> Color(0xFFE1306C)
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

            Spacer(modifier = Modifier.height(4.dp))

            // Badges secundárias: Tipo de Agendamento + Anexo
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

            // Linha Inferior com Horário, Destinatários e Ações
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

                // Ações por Aba
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
                            IconButton(onClick = onSendNow, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Send, contentDescription = "Agendar", tint = GreenPrimary, modifier = Modifier.size(18.dp))
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

fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
