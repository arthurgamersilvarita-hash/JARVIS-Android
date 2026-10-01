package com.jarvis.assistant

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URLEncoder
import java.util.Locale

private val Navy = Color(0xFF050912)
private val Cyan = Color(0xFF4DEBFF)
private val Panel = Color(0xFF0F1926)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        tts = TextToSpeech(this, this)

        setContent {
            JarvisApp(
                onListen = { startListening() },
                onCalendar = { openCalendar() },
                onAlarm = { setAlarm() }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("pt", "BR")
            tts.setSpeechRate(0.95f)
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS")
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Reconhecimento de voz não está disponível neste aparelho.")
            return
        }

        recognizer?.destroy()

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                speak("Estou ouvindo.")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                speak("Não consegui entender. Tente novamente.")
            }

            override fun onResults(results: Bundle?) {
                val matches =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                val text = matches?.firstOrNull()?.trim().orEmpty()

                if (text.isNotEmpty()) {
                    handleCommand(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        recognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {
        val text = command.lowercase(Locale("pt", "BR"))

        when {
            text.contains("clima") ||
                    text.contains("tempo") ||
                    text.contains("temperatura") -> {
                getWeather()
            }

            text.contains("notícia") ||
                    text.contains("noticias") ||
                    text.contains("notícias") -> {
                getNews()
            }

            text.contains("alarme") -> {
                setAlarm()
            }

            text.contains("calendário") ||
                    text.contains("agenda") -> {
                openCalendar()
            }

            text.contains("youtube") -> {
                openApp("com.google.android.youtube")
            }

            text.contains("spotify") -> {
                openApp("com.spotify.music")
            }

            text.contains("whatsapp") -> {
                openApp("com.whatsapp")
            }

            text.contains("pesquise") ||
                    text.contains("pesquisa") ||
                    text.contains("procure") -> {
                searchWeb(command)
            }

            text.contains("lembr") -> {
                saveMemory(command)
                speak("Certo. Vou guardar isso na memória.")
            }

            text.contains("memória") ||
                    text.contains("memoria") -> {
                speak(loadMemory())
            }

            text.contains("limpar memória") ||
                    text.contains("limpar memoria") -> {
                clearMemory()
                speak("Memória da conversa limpa.")
            }

            text.contains("fatura") ||
                    text.contains("cartão") ||
                    text.contains("cart
