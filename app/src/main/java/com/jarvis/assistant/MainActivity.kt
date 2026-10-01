package com.jarvis.assistant

import android.Manifest
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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
                onAlarm = { startListening() },
onSettings = { openSettings() }
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
        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "JARVIS"
        )
    }

    private fun startListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("O reconhecimento de voz não está disponível.")
            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

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
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val command =
                        matches?.firstOrNull()?.trim().orEmpty()

                    if (command.isNotEmpty()) {
                        handleCommand(command)
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {}

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "pt-BR"
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

        recognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {

        val text =
            command.lowercase(Locale("pt", "BR"))

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

                setAlarm(command)
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

                speak(
                    "Certo. Vou guardar isso na memória."
                )
            }

            text.contains("memória") ||
                    text.contains("memoria") -> {

                speak(loadMemory())
            }

            text.contains("limpar memória") ||
                    text.contains("limpar memoria") -> {

                clearMemory()

                speak(
                    "Memória da conversa limpa."
                )
            }

            text.contains("fatura") ||
                    text.contains("cartão") ||
                    text.contains("cartao") ||
                    text.contains("limite") -> {

                speak(
                    "A área financeira ainda está protegida. " +
                            "Vamos adicionar biometria e integração bancária."
                )
            }

            else -> {

                saveMemory("Você: $command")

                speak(
                    "Entendido. Você disse: $command."
                )
            }
        }
    }

    private fun saveMemory(text: String) {

        val prefs =
            getSharedPreferences(
                "jarvis_memory",
                Context.MODE_PRIVATE
            )

        val old =
            prefs.getString("history", "")
                ?: ""

        val lines =
            old.split("\n")
                .filter { it.isNotBlank() }
                .toMutableList()

        lines.add(text)

        val limited =
            lines.takeLast(50)

        prefs.edit()
            .putString(
                "history",
                limited.joinToString("\n")
            )
            .apply()
    }

    private fun loadMemory(): String {

        val prefs =
            getSharedPreferences(
                "jarvis_memory",
                Context.MODE_PRIVATE
            )

        val history =
            prefs.getString("history", "")
                ?: ""

        if (history.isBlank()) {
            return "Minha memória ainda está vazia."
        }

        val last =
            history.split("\n")
                .filter { it.isNotBlank() }
                .takeLast(5)

        return "Eu me lembro de: " +
                last.joinToString(". ")
    }

    private fun clearMemory() {

        getSharedPreferences(
            "jarvis_memory",
            Context.MODE_PRIVATE
        )
            .edit()
            .clear()
            .apply()
    }

    private fun getWeather() {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val locationManager =
                    getSystemService(
                        Context.LOCATION_SERVICE
                    ) as LocationManager

                var latitude = -23.5505
                var longitude = -46.6333

                val fine =
                    checkSelfPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                val coarse =
                    checkSelfPermission(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                if (fine || coarse) {

                    val location =
                        locationManager.getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER
                        )

                    if (location != null) {

                        latitude =
                            location.latitude

                        longitude =
                            location.longitude
                    }
                }

                val url =
                    "https://api.open-meteo.com/v1/forecast" +
                            "?latitude=$latitude" +
                            "&longitude=$longitude" +
                            "&current=temperature_2m"

                val request =
                    Request.Builder()
                        .url(url)
                        .build()

                val response =
                    OkHttpClient()
                        .newCall(request)
                        .execute()

                val body =
                    response.body?.string()
                        .orEmpty()

                val temperature =
                    Regex(
                        "\"temperature_2m\"\\s*:\\s*(-?[0-9.]+)"
                    )
                        .find(body)
                        ?.groupValues
                        ?.getOrNull(1)

                withContext(Dispatchers.Main) {

                    if (temperature != null) {

                        speak(
                            "A temperatura atual é " +
                                    "$temperature graus Celsius."
                        )

                    } else {

                        speak(
                            "Não consegui obter a temperatura."
                        )
                    }
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    speak(
                        "Não consegui consultar o clima agora."
                    )
                }
            }
        }
    }

    private fun getNews() {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val url =
                    "https://news.google.com/rss" +
                            "?hl=pt-BR&gl=BR&ceid=BR:pt-419"

                val request =
                    Request.Builder()
                        .url(url)
                        .build()

                val response =
                    OkHttpClient()
                        .newCall(request)
                        .execute()

                val xml =
                    response.body?.string()
                        .orEmpty()

                val factory =
                    XmlPullParserFactory
                        .newInstance()

                val parser =
                    factory.newPullParser()

                parser.setInput(xml.reader())

                val titles =
                    mutableListOf<String>()

                var event =
                    parser.eventType

                while (
                    event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT &&
                    titles.size < 3
                ) {

                    if (
                        event ==
                        org.xmlpull.v1.XmlPullParser.START_TAG &&
                        parser.name == "title"
                    ) {

                        val title =
                            parser.nextText()

                        if (
                            title.isNotBlank() &&
                            !title.equals(
                                "Google News",
                                true
                            )
                        ) {

                            titles.add(title)
                        }
                    }

                    event = parser.next()
                }

                withContext(Dispatchers.Main) {

                    if (titles.isEmpty()) {

                        speak(
                            "Não encontrei notícias agora."
                        )

                    } else {

                        speak(
                            "As principais notícias são: " +
                                    titles.joinToString(". ")
                        )
                    }
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    speak(
                        "Não consegui consultar as notícias."
                    )
                }
            }
        }
    }
private fun setAlarm(command: String) {

    val regex = Regex("""(\d{1,2})[:h](\d{1,2})""")
    val match = regex.find(command)

    if (match == null) {
        speak("Diga o horário do alarme. Por exemplo, sete e quarenta.")
        return
    }

    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()

    if (hour !in 0..23 || minute !in 0..59) {
        speak("Esse horário não é válido.")
        return
    }

    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, hour)
        putExtra(AlarmClock.EXTRA_MINUTES, minute)
        putExtra(
            AlarmClock.EXTRA_MESSAGE,
            "ALARME JARVIS"
        )
        putExtra(
            AlarmClock.EXTRA_SKIP_UI,
            true
        )
    }

    try {
        startActivity(intent)

        speak(
            "Alarme definido para $hour horas e $minute minutos."
        )

    } catch (e: Exception) {
        speak("Não consegui criar o alarme.")
    }
}



    private fun openCalendar() {

        val intent =
            Intent(Intent.ACTION_INSERT).apply {

                data =
                    CalendarContract.Events.CONTENT_URI
            }

        try {

            startActivity(intent)

            speak(
                "Abrindo seu calendário."
            )

        } catch (e: Exception) {

            speak(
                "Não consegui abrir o calendário."
            )
        }
    }

    private fun searchWeb(command: String) {

        val query =
            command
                .replace(
                    "pesquise",
                    "",
                    true
                )
                .replace(
                    "pesquisa",
                    "",
                    true
                )
                .replace(
                    "procure",
                    "",
                    true
                )
                .trim()

        if (query.isBlank()) {

            speak(
                "O que você quer que eu pesquise?"
            )

            return
        }

        val encoded =
            URLEncoder.encode(
                query,
                "UTF-8"
            )

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com/search?q=$encoded"
                )
            )

        try {

            startActivity(intent)

            speak(
                "Abrindo os resultados."
            )

        } catch (e: Exception) {

            speak(
                "Não consegui abrir a pesquisa."
            )
        }
    }

    private fun openApp(packageName: String) {

        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        if (intent != null) {

            startActivity(intent)

            speak(
                "Abrindo o aplicativo."
            )

        } else {

            speak(
                "Esse aplicativo não está instalado."
            )
        }
    }

    override fun onDestroy() {

        recognizer?.destroy()

        tts.stop()
        tts.shutdown()

        super.onDestroy()
    }
}

@Composable
fun JarvisApp(
    onListen: () -> Unit,
    onCalendar: () -> Unit,
    onAlarm: () -> Unit
) {

    var status by remember {
        mutableStateOf("JARVIS ONLINE")
    }

    val pulse =
        rememberInfiniteTransition(
            label = "pulse"
        ).animateFloat(
            initialValue = 0.94f,
            targetValue = 1.08f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            1200,
                            easing =
                                FastOutSlowInEasing
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label = "pulse"
        )

    MaterialTheme(
        colorScheme =
            darkColorScheme(
                background = Navy,
                surface = Panel,
                primary = Cyan
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Navy)
                    .padding(20.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column {

                    Text(
                        text = "JARVIS",
                        color = Cyan,
                        fontSize = 28.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text = status,
                        color =
                            Color.White.copy(
                    Icon            alpha = 0.65f
                            ),
                        fontSize = 12.sp
                    )
                }

val context = androidx.compose.ui.platform.LocalContext.current

IconButton(
    onClick = {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
    }
) {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Configurações",
        tint = Cyan
    )
}
) {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Configurações",
        tint = Cyan
    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(30.dp)
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment =
                    Alignment.Center
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(190.dp)
                            .scale(pulse.value)
                            .alpha(0.25f)
                            .background(
                                Cyan,
                                CircleShape
                            )
                )

                Box(
                    modifier =
                        Modifier
                            .size(130.dp)
                            .background(
                                Color(0xFF081D2B),
                                CircleShape
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    IconButton(
                        onClick = {

                            status =
                                "OUVINDO..."

                            onListen()
                        },
                        modifier =
                            Modifier.size(100.dp)
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Mic,
                            contentDescription =
                                "Falar com JARVIS",
                            tint = Cyan,
                            modifier =
                                Modifier.size(52.dp)
                        )
                    }
                }
            }

            Text(
                text =
                    "Toque no microfone e fale comigo",
                color =
                    Color.White.copy(
                        alpha = 0.7f
                    ),
                modifier =
                    Modifier.align(
                        Alignment.CenterHorizontally
                    )
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                Button(
                    onClick = onAlarm,
                    modifier =
                        Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Panel
                        )
                ) {

                    Text("ALARME")
                }

                Button(
                    onClick = onCalendar,
                    modifier =
                        Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Panel
                        )
                ) {

                    Text("AGENDA")
                }
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Panel
                    ),
                shape =
                    RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(18.dp)
                ) {

                    Text(
                        text = "Módulos V2",
                        color = Cyan,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "✓ Memória persistente\n" +
                                    "✓ Clima por localização\n" +
                                    "✓ Notícias\n" +
                                    "✓ Alarmes e calendário\n" +
                                    "✓ Pesquisa na internet\n" +
                                    "✓ Controle de aplicativos",
                        color =
                            Color.White.copy(
                                alpha = 0.8f
                            ),
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
