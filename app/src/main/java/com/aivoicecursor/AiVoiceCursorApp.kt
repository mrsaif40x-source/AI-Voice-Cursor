package com.aivoicecursor

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.room.Room
import com.aivoicecursor.ai.ActionPlanner
import com.aivoicecursor.ai.MediaPipeGemmaEngine
import com.aivoicecursor.core.AgentPipeline
import com.aivoicecursor.data.AppDatabase
import com.aivoicecursor.data.HistoryRepository
import com.aivoicecursor.vision.NoVisionEngine
import com.aivoicecursor.vision.ScreenVisionEngine
import com.aivoicecursor.voice.VoskSpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = Room.databaseBuilder(app, AppDatabase::class.java, "history.db").build()
    val history = HistoryRepository(db.historyDao())
    val llm = MediaPipeGemmaEngine(app)
    val recognizer = VoskSpeechRecognizer(app)
    val vision: ScreenVisionEngine = NoVisionEngine()
    val pipeline = AgentPipeline(ActionPlanner(llm), history, vision)
}

class AiVoiceCursorApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            container.scope.launch { container.llm.unloadModel() }
        }
    }
}
