# 📱 Miix Agendador WP (Modo PRO)

> Agendador inteligente de mensagens automatizadas para **WhatsApp** e **WhatsApp Business**, sem limites, sem mensalidades e sem marcas d'água.

Desenvolvido por **Evandroo Miix**.

---

## ✨ Funcionalidades Principais (Recursos PRO)

* 📅 **Agendamento com Data e Hora Exata:** Alarmes de alta precisão através do `AlarmManager` nativo do Android.
* 🤖 **Envio 100% Automático:** Utiliza o Serviço de Acessibilidade (`AccessibilityService`) do Android para digitar e pressionar "Enviar" no WhatsApp sem exigir sua intervenção manual.
* 🏢 **WhatsApp Normal & WhatsApp Business:** Suporte total a ambos os aplicativos oficiais.
* 👥 **Disparo em Lote:** Envie para dezenas ou centenas de contatos em uma única campanha agendada.
* 🛡️ **Intervalo de Segurança Anti-Ban:** Delay personalizável entre cada envio (ex: 8 a 15 segundos) para manter seu número protegido contra bloqueios por spam.
* 📝 **Mensagens Personalizadas com Variáveis:** Use `{nome}` e `{telefone}` no corpo do texto para tornar cada mensagem única.
* 📁 **Importação de Listas / CSV:** Cole ou importe listas no formato `Telefone, Nome` direto pelo app.
* 🔁 **Repetição Automática:** Agendamentos Diários, em Dias Úteis (Segunda a Sexta) ou Semanais.
* 🌙 **Interface Moderna em Modo Escuro:** Desenvolvida em **Jetpack Compose (Material 3)**.

---

## 🛠️ Como Funciona a Compilação Automática (GitHub Actions)

Você não precisa de Android Studio ou SDK pesados instalados no seu computador. A nuvem do GitHub compila o APK para você automaticamente:

1. Suba este projeto para o seu repositório no GitHub:
   ```bash
   git init
   git add .
   git commit -m "feat: Initial commit Miix Agendador WP"
   git remote add origin https://github.com/evandroomiix/miix-agendador-wp.git
   git push -u origin main
   ```
2. Acesse a aba **Actions** no seu repositório do GitHub.
3. O workflow **Compilar APK Android** iniciará automaticamente e gerará o arquivo **`MiixAgendadorWP.apk`** na seção de **Artifacts** ou em **Releases**, pronto para download direto no seu celular!

---

## 📲 Permissões Necessárias no Celular

Ao instalar o APK pela primeira vez, o aplicativo solicitará:

1. **Serviço de Acessibilidade:** Em *Configurações > Acessibilidade > Apps Instalados > Miix Agendador WP* (essencial para que o robô possa clicar no botão de enviar).
2. **Ignorar Otimização de Bateria:** Para que o sistema Android não coloque o agendador em suspensão profunda.
3. **Alarmes Exatos:** Para tocar rigorosamente no minuto agendado.
