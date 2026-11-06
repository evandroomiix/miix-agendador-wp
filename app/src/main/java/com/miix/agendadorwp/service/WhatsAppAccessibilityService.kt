package com.miix.agendadorwp.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.miix.agendadorwp.MainActivity

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppAccService"
        var isServiceRunning = false
        var isAutomating = false
        var onMessageSentListener: (() -> Unit)? = null

        // Modo de captura de Contato/Grupo diretamente no WhatsApp
        var isCaptureModeActive = false
        var onContactCapturedListener: ((String) -> Unit)? = null

        private val SEND_BUTTON_IDS = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.gbwhatsapp:id/send",
            "com.fmwhatsapp:id/send",
            "com.yowhatsapp:id/send",
            "com.instagram.android:id/row_thread_composer_button_send",
            "com.instagram.android:id/action_bar_button_action"
        )
        private val SEND_BUTTON_DESCRIPTIONS = listOf(
            "enviar", "send", "enviar mensagem", "send message", "compartilhar", "share", "publicar", "post"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastClickTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "Serviço de Acessibilidade Conectado com Sucesso!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val rootNode = rootInActiveWindow ?: return

        // 1. MODO DE CAPTURA DE CONTATO / GRUPO
        if (isCaptureModeActive) {
            val capturedName = extractContactOrGroupName(rootNode)
            if (!capturedName.isNullOrBlank() && capturedName.length > 1) {
                isCaptureModeActive = false
                Log.d(TAG, "Contato/Grupo capturado do WhatsApp: $capturedName")
                handler.post {
                    onContactCapturedListener?.invoke(capturedName)
                    // Retorna para o aplicativo Miix Agendador WP
                    val returnIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("CAPTURED_CONTACT", capturedName)
                    }
                    startActivity(returnIntent)
                }
                return
            }
        }

        // 2. MODO DE AUTOMAÇÃO DE ENVIO
        if (!isAutomating) return

        // Evita múltiplos cliques repetidos no mesmo segundo
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 1500) return

        // Procurar o botão de envio
        val sendButton = findSendButton(rootNode)
        if (sendButton != null && sendButton.isEnabled) {
            lastClickTime = now
            Log.d(TAG, "Botão Enviar encontrado! Clicando...")

            // Delay de 400ms para estabilização da interface
            handler.postDelayed({
                val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.d(TAG, "Mensagem enviada com sucesso!")
                    onMessageSentListener?.invoke()
                }
            }, 400)
        }
    }

    private fun extractContactOrGroupName(root: AccessibilityNodeInfo): String? {
        val titleIds = listOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp.w4b:id/conversation_contact_name",
            "com.whatsapp:id/toolbar_title",
            "com.whatsapp.w4b:id/toolbar_title"
        )
        for (id in titleIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()?.trim()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Busca por Resource ID exato do WhatsApp, Mods e Instagram
        for (id in SEND_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) return node
                }
            }
        }

        // 2. Busca por ContentDescription ("Enviar", "Send", "Publicar")
        for (desc in SEND_BUTTON_DESCRIPTIONS) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val d = node.contentDescription?.toString()?.lowercase() ?: ""
                    if (d.contains(desc) && (node.isClickable || node.parent?.isClickable == true)) {
                        return if (node.isClickable) node else node.parent
                    }
                }
            }
        }

        return null
    }

    override fun onInterrupt() {
        isServiceRunning = false
        Log.w(TAG, "Serviço de Acessibilidade Interrompido!")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }
}
