package app.taxicom.commandos

import android.content.Context
import app.taxicom.commandos.capture.CaptureCoordinator
import app.taxicom.commandos.data.ApiClient
import app.taxicom.commandos.data.BusClient
import app.taxicom.commandos.data.OfferDatabase
import app.taxicom.commandos.data.PreferencesRepository
import app.taxicom.commandos.data.SessionRepository
import app.taxicom.commandos.data.SyncWorker
import app.taxicom.commandos.data.TokenStore
import app.taxicom.commandos.decision.OfferPipeline
import app.taxicom.commandos.voice.Voice
import app.taxicom.commandos.wheel.WheelController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs = PreferencesRepository(appContext)
    val tokens = TokenStore(appContext)
    val db = OfferDatabase.create(appContext)
    val sessions = SessionRepository(appContext, tokens)
    val api = ApiClient(BuildConfig.API_BASE_URL, tokens, sessions::onSessionLost).also { sessions.api = it }
    val bus = BusClient(api, tokens, scope)
    val voice = Voice(appContext)
    val pipeline = OfferPipeline(
        scope = scope,
        dao = db.offers(),
        bus = bus,
        voice = { phrase -> voice.speak(phrase) },
        locale = { Locale.getDefault().language },
    )
    val capture = CaptureCoordinator(scope, pipeline)
    val wheel = WheelController(appContext, pipeline)

    init {
        SyncWorker.schedule(appContext)
        scope.launch {
            prefs.prefs.collect { p ->
                pipeline.updatePrefs(p)
                voice.setEnabled(p.ttsEnabled)
                wheel.setEnabled(p.wheelEnabled)
            }
        }
        scope.launch {
            val initial = prefs.prefs.first()
            pipeline.updatePrefs(initial)
        }
    }
}

class TaxiCommandosApp : android.app.Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
