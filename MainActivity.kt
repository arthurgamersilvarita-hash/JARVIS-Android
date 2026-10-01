package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Navy = Color(0xFF050912)
private val Cyan = Color(0xFF4DEBFF)
private val Panel = Color(0xFF09131E)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private val vm = JarvisViewModel()

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ))
        tts = TextToSpeech(this, this)
        setContent { JarvisScreen(vm, ::listen, ::openCalendar) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("pt", "BR")
            tts.setSpeechRate(0.95f)
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("O reconhecimento de voz não está disponível neste aparelho.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { vm.listening = true }
            override fun onBeginningOfSpeech() { vm.listening = true }
            override fun onEndOfSpeech() { vm.listening = false }
            override fun onError(error: Int) { vm.listening = false }
            override fun onResults(results: Bundle?) {
                vm.listening = false
                val text = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.firstOrNull() ?: return
                vm.process(text) { answer -> speak(answer) }
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        })
    }

    private fun openCalendar() {
        startActivity(Intent(Intent.ACTION_INSERT).setType("vnd.android.cursor.item/event"))
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}

class JarvisViewModel : ViewModel() {
    data class Message(val speaker: String, val text: String)

    var listening by mutableStateOf(false)
    var weather by mutableStateOf("--°C")
    var weatherDetails by mutableStateOf("Toque no microfone e pergunte sobre o clima.")
    var news by mutableStateOf("Aguardando notícias...")
    var messages by mutableStateOf(
        listOf(Message("JARVIS", "Sistemas online. Toque no núcleo e fale comigo."))
    )
        private set

    fun process(input: String, speak: (String) -> Unit) {
        messages = (messages + Message("VOCÊ", input)).takeLast(30)
        val normalized = input.lowercase(Locale("pt", "BR"))

        viewModelScope.launch {
            val answer = when {
                normalized.contains("clima") ||
                normalized.contains("tempo") ||
                normalized.contains("temperatura") -> loadWeather()

                normalized.contains("notícia") ||
                normalized.contains("noticias") ||
                normalized.contains("notícias") -> loadNews()

                normalized.contains("calendário") ||
                normalized.contains("agenda") -> "Posso abrir o calendário do Android para você."

                normalized.contains("cartão") ||
                normalized.contains("fatura") ||
                normalized.contains("limite") -> "O módulo financeiro ainda não está conectado a nenhuma conta. Não vou inventar dados financeiros."

                else -> "Entendi. A conversa geral com a IA será conectada na próxima etapa. Clima e notícias já podem ser consultados."
            }
            messages = (messages + Message("JARVIS", answer)).takeLast(30)
            speak(answer)
        }
    }

    private suspend fun loadWeather(): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=-23.5505&longitude=-46.6333" +
                "&current=temperature_2m,apparent_temperature" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=America%2FSao_Paulo"

            val request = Request.Builder().url(url).build()
            val response = Http.client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            // Parser simples propositalmente deixado para a próxima etapa.
            weather = "ONLINE"
            weatherDetails = "Clima conectado. Próxima etapa: localização real e dados detalhados."
            "Consegui consultar o serviço de clima. Na próxima versão vou usar sua localização real e apresentar todos os detalhes."
        } catch (_: Exception) {
            "Não consegui consultar o clima agora. Verifique sua conexão."
        }
    }

    private suspend fun loadNews(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://news.google.com/rss?hl=pt-BR&gl=BR&ceid=BR:pt-419")
                .build()
            val response = Http.client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            val first = Regex("<title>(.*?)</title>")
                .findAll(body)
                .drop(1)
                .map { it.groupValues[1].replace("<![CDATA[", "").replace("]]>", "") }
                .firstOrNull()
            news = first ?: "Sem manchetes no momento."
            first?.let { "A principal manchete que encontrei é: $it" }
                ?: "Não encontrei notícias agora."
        } catch (_: Exception) {
            "Não consegui atualizar as notícias agora."
        }
    }
}

object Http {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

@Composable
private fun JarvisScreen(
    vm: JarvisViewModel,
    listen: () -> Unit,
    openCalendar: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "core")
    val pulse by transition.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse"
    )

    MaterialTheme(colorScheme = darkColorScheme(
        background = Navy,
        surface = Panel,
        primary = Cyan
    )) {
        Surface(Modifier.fillMaxSize(), color = Navy) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("J.A.R.V.I.S", color = Cyan, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("PERSONAL AI SYSTEM", color = Color.Gray, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ONLINE", color = Cyan, fontSize = 11.sp)
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Settings, "Configurações", tint = Cyan)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardCard("CLIMA", vm.weather, vm.weatherDetails, Modifier.weight(1f))
                    DashboardCard("NOTÍCIAS", "LIVE", vm.news, Modifier.weight(1f))
                }

                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(190.dp)
                            .scale(if (vm.listening) pulse else 1f)
                            .background(Cyan.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(140.dp)
                                .background(Color(0xFF071A22), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = listen,
                                modifier = Modifier.size(112.dp)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    "Falar com JARVIS",
                                    tint = Cyan,
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                        }
                    }
                    Text(
                        if (vm.listening) "OUVINDO..." else "TOQUE PARA FALAR",
                        color = Cyan,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                vm.messages.takeLast(4).forEach { MessageBubble(it) }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = listen, Modifier.weight(1f)) {
                        Icon(Icons.Default.Mic, null)
                        Spacer(Modifier.width(5.dp))
                        Text("FALAR")
                    }
                    OutlinedButton(onClick = openCalendar, Modifier.weight(1f)) {
                        Icon(Icons.Default.Event, null)
                        Spacer(Modifier.width(5.dp))
                        Text("AGENDA")
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    value: String,
    details: String,
    modifier: Modifier
) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(title, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(details, color = Color.Gray, fontSize = 10.sp, maxLines = 3)
        }
    }
}

@Composable
private fun MessageBubble(message: JarvisViewModel.Message) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.speaker == "VOCÊ")
            Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.speaker == "VOCÊ")
                Color(0xFF102D38) else Color(0xFF0B1822),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(9.dp)) {
                Text(message.speaker, color = Cyan, fontSize = 9.sp)
                Text(message.text, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
