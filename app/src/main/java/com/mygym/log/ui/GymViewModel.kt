package com.mygym.log.ui

import android.net.Uri
import androidx.lifecycle.*
import com.mygym.log.GymApplication
import com.mygym.log.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class GymViewModel(private val app: GymApplication) : ViewModel() {
    val repo=app.repository
    val data=repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Snapshot())
    val preferences=repo.preferences.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow<String?>(null)
    val pendingBackup=MutableStateFlow<Backup?>(null)
    val runError=app.runError
    val runCommandPending=app.runCommandPending
    fun runAction(action: String,id: String?=null) {
        runError.value=null
        try { com.mygym.log.running.RunTrackingService.send(app,action,id) }
        catch(e: Exception){runError.value=e.message ?: "Unable to start tracking."}
    }
    init { act { app.ready.await(); repo.seed() } }
    fun act(block: suspend () -> Unit) {
        if(busy.value) return
        busy.value=true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { message.value=e.message ?: "Unable to save change." }
            finally { busy.value=false }
        }
    }
    fun export(uri: Uri) = act { app.backup.export(uri); message.value="Backup exported." }
    fun prepareRestore(uri: Uri) = act { pendingBackup.value=app.backup.read(uri) }
    fun restore() { val b=pendingBackup.value ?: return; act { app.backup.restore(b); pendingBackup.value=null; message.value="Backup restored." } }
    companion object {
        fun factory(app: GymApplication) = object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T = GymViewModel(app) as T
        }
    }
}
