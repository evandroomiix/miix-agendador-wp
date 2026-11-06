package com.miix.agendadorwp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import com.miix.agendadorwp.service.AppUpdateManager
import com.miix.agendadorwp.service.PlatformHelper
import com.miix.agendadorwp.service.UpdateInfo
import com.miix.agendadorwp.service.WhatsAppAccessibilityService
import com.miix.agendadorwp.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAccessibilityActive by remember { mutableStateOf(WhatsAppAccessibilityService.isServiceRunning) }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptimization(context)) }

    val installedApps = remember { PlatformHelper.getInstalledApps(context) }

    // Atualização do Aplicativo (In-App Updater)
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações & Ajustes", fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // CARD DE ATUALIZAÇÕES DO APLICATIVO
            item {
                Text("Atualizações do Aplicativo", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
            }

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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Versão Instalada: v1.4.0 PRO MAX", fontWeight = FontWeight.Bold, color = GreenPrimary, fontSize = 14.sp)
                                Text("Verifique se há novas melhorias e recursos", fontSize = 12.sp, color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    isCheckingUpdate = true
                                    scope.launch {
                                        val update = AppUpdateManager.checkForUpdates(context)
                                        isCheckingUpdate = false
                                        if (update != null) {
                                            availableUpdate = update
                                        } else {
                                            Toast.makeText(context, "🎉 Você já está usando a versão mais recente!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buscar", color = DarkBackground, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

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
                        Text("Para envios de madrugada ou com tela bloqueada, certifique-se de que o aparelho não possua biometria rígida que impeça a tela de ligar.", color = TextSecondary, fontSize = 12.sp)
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
                        Text("Versão 1.4.0 • Edição PRO MAX (Sem Limites)", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Desenvolvido por Evandroo Miix", color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // 🌟 DIÁLOGO DE ATUALIZAÇÃO AUTOMÁTICA
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
