package com.miix.agendadorwp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import com.miix.agendadorwp.service.PlatformHelper
import com.miix.agendadorwp.service.WhatsAppAccessibilityService
import com.miix.agendadorwp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var isAccessibilityActive by remember { mutableStateOf(WhatsAppAccessibilityService.isServiceRunning) }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimization(context)) }

    val installedApps = remember { PlatformHelper.getInstalledApps(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações & Status", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Permissões de Sistema", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            }

            // Permissão de Acessibilidade
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Serviço de Acessibilidade", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Text(
                                    if (isAccessibilityActive) "Status: Ativado ✅" else "Status: Desativado ⚠️",
                                    color = if (isAccessibilityActive) GreenPrimary else AccentOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isAccessibilityActive) DarkBorder else GreenPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isAccessibilityActive) "Ver Ajustes" else "Ativar", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bateria sem restrição
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Economia de Bateria", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                Text(
                                    if (isBatteryOptIgnored) "Sem restrições ✅" else "Otimizado pelo Android ⚠️",
                                    color = if (isBatteryOptIgnored) GreenPrimary else AccentOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (!isBatteryOptIgnored) {
                                Button(
                                    onClick = {
                                        requestIgnoreBatteryOptimization(context)
                                        isBatteryOptIgnored = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Liberar", color = DarkBackground, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Aplicativos Detectados no Aparelho
            item {
                Text("Redes Sociais & Mensageiros Detectados", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        installedApps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(app.appName, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                                    Text(app.category, fontSize = 11.sp, color = TextSecondary)
                                }
                                Surface(
                                    color = if (app.isInstalled) GreenPrimary.copy(alpha = 0.2f) else DarkSurface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        if (app.isInstalled) "INSTALADO" else "NÃO DETECTADO",
                                        color = if (app.isInstalled) GreenPrimary else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Dicas Anti-Ban (Boas Práticas)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Intervalo Seguro:", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 13.sp)
                        Text("Mantenha o delay entre mensagens em no mínimo 8 a 15 segundos para listas com mais de 20 contatos.", color = TextSecondary, fontSize = 12.sp)

                        Text("2. Variáveis na Mensagem:", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 13.sp)
                        Text("Sempre use {nome} na mensagem. Mensagens personalizadas têm risco quase nulo de serem marcadas como spam.", color = TextSecondary, fontSize = 12.sp)

                        Text("3. Tela Desbloqueada:", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 13.sp)
                        Text("Para envios de madrugada ou com tela bloqueada, certifique-se de que o aparelho não possua biometria ou senha rígida que impeça a tela de ligar.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Miix Agendador WP", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 16.sp)
                        Text("Versão 1.1.0 • Edição PRO MAX (Sem Limites)", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Desenvolvido por Evandroo Miix", color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

fun requestIgnoreBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
