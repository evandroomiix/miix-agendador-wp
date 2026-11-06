package com.miix.agendadorwp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
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
    val pendingMessages by database.scheduledMessageDao().getPendingMessagesFlow().collectAsState(initial = emptyList())

    var isAccessibilityEnabled by remember { mutableStateOf(WhatsAppAccessibilityService.isServiceRunning) }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimization(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Miix Agendador WP", fontWeight = FontWeight.Bold, color = TextPrimary)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            // ⚠️ Alerta de Acessibilidade (Essencial para o envio automático)
            if (!isAccessibilityEnabled) {
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
                                Text(
                                    "Acessibilidade Desativada",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Para que as mensagens sejam enviadas sem você precisar tocar na tela, ative o serviço de acessibilidade do Miix Agendador.",
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Ativar Acessibilidade no Android", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ⚡ Alerta de Otimização de Bateria
            if (!isBatteryOptIgnored) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Evitar suspensão em segundo plano", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                                Text("Permita execução sem restrições de bateria.", color = TextSecondary, fontSize = 11.sp)
                            }
                            TextButton(onClick = {
                                requestIgnoreBatteryOptimization(context)
                                isBatteryOptIgnored = true
                            }) {
                                Text("Permitir", color = GreenPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Cabeçalho da Lista
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Próximos Agendamentos (${pendingMessages.size})",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 17.sp
                    )
                }
            }

            if (pendingMessages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Nenhuma mensagem agendada", color = TextSecondary, fontSize = 15.sp)
                            Text("Clique abaixo para criar um novo agendamento.", color = TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(pendingMessages, key = { it.id }) { msg ->
                    ScheduledMessageCard(
                        message = msg,
                        onCancel = {
                            scope.launch {
                                SchedulerManager.cancelSchedule(context, msg.id)
                                database.scheduledMessageDao().updateStatus(msg.id, MessageStatus.CANCELLED, "Cancelado pelo usuário")
                            }
                        },
                        onDelete = {
                            scope.launch {
                                SchedulerManager.cancelSchedule(context, msg.id)
                                database.scheduledMessageDao().deleteMessage(msg)
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(70.dp)) }
        }
    }
}

@Composable
fun ScheduledMessageCard(
    message: ScheduledMessage,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    val dateStr = sdf.format(Date(message.scheduledTimeMillis))
    val contactCount = try {
        JSONArray(message.recipientsJson).length()
    } catch (e: Exception) {
        1
    }

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
                Text(
                    message.title.ifBlank { "Sem Título" },
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
                
                // Badge WhatsApp Normal ou Business
                Surface(
                    color = if (message.whatsAppType == WhatsAppType.WHATSAPP_BUSINESS) GreenSecondary else GreenDark,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (message.whatsAppType == WhatsAppType.WHATSAPP_BUSINESS) "Business" else "WhatsApp",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                message.text,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(dateStr, color = GreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "$contactCount destinatário(s) • Delay: ${message.delayBetweenContactsSeconds}s",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = AccentOrange)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = AccentRed)
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

fun requestIgnoreBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback
    }
}
