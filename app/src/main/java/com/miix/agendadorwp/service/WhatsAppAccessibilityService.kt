package com.miix.agendadorwp.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppAccService"
        var isServiceRunning = false
        var isAutomating = false
        var onMessageSentListener: (() -> Unit)? = null

        private val SEND_BUTTON_IDS = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send"
        )
        private val SEND_BUTTON_DESCRIPTIONS = listOf(
            "enviar", "send", "enviar mensagem", "send message"
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
        if (!isAutomating || event == null) return

        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName != "com.whatsapp" && pkgName != "com.whatsapp.w4b") {
            return
        }

        // Evita múltiplos cliques repetidos no mesmo segundo
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 1500) return

        val rootNode = rootInActiveWindow ?: return

        // Procurar o botão de envio
        val sendButton = findSendButton(rootNode)
        if (sendButton != null && sendButton.isEnabled) {
            lastClickTime = now
            Log.d(TAG, "Botão Enviar encontrado! Clicando...")

            // Pequeno delay de 400ms para estabilização da interface
            handler.postDelayed({
                val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.d(TAG, "Mensagem enviada com sucesso!")
                    onMessageSentListener?.invoke()
                }
            }, 400)
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Busca por Resource ID exato do WhatsApp e WhatsApp Business
        for (id in SEND_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.isNotEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) return node
                }
            }
        }

        // 2. Busca por ContentDescription ("Enviar", "Send")
        for (desc in SEND_BUTTON_DESCRIPTIONS) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes != null && nodes.isNotEmpty()) {
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
