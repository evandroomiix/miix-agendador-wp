package com.miix.agendadorwp.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miix.agendadorwp.data.*
import com.miix.agendadorwp.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(database: AppDatabase) {
    val allMessages by database.scheduledMessageDao().getAllMessagesFlow().collectAsState(initial = emptyList())
    val historyMessages = allMessages.filter { it.status != MessageStatus.PENDING }
    val scope = rememberCoroutineScope()
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Envios", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (historyMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Nenhum envio registrado no histórico", color = TextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyMessages, key = { it.id }) { msg ->
                    val statusColor = when (msg.status) {
                        MessageStatus.SENT -> GreenPrimary
                        MessageStatus.FAILED -> AccentRed
                        MessageStatus.CANCELLED -> AccentOrange
                        MessageStatus.SENDING -> AccentBlue
                        else -> TextSecondary
                    }

                    val statusLabel = when (msg.status) {
                        MessageStatus.SENT -> "ENVIADO"
                        MessageStatus.FAILED -> "FALHOU"
                        MessageStatus.CANCELLED -> "CANCELADO"
                        MessageStatus.SENDING -> "ENVIANDO..."
                        else -> "PENDENTE"
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
                                    msg.title.ifBlank { "Sem Título" },
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 15.sp
                                )
                                Surface(
                                    color = statusColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        statusLabel,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(msg.text, color = TextSecondary, fontSize = 13.sp, maxLines = 2)

                            if (msg.lastExecutionLog.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Log: ${msg.lastExecutionLog}",
                                    color = TextSecondary.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    sdf.format(Date(msg.scheduledTimeMillis)),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                IconButton(onClick = {
                                    scope.launch {
                                        database.scheduledMessageDao().deleteMessage(msg)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = AccentRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
