package arun.pkg.workmanagersample

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PhotoCompressViewModel @Inject constructor(
    private val workManager: WorkManager,
) : ViewModel() {

    private val _state = MutableStateFlow<CompressionStatus>(CompressionStatus.Idle)
    val state: StateFlow<CompressionStatus> = _state.asStateFlow()

    private var currentWorkRequestId: UUID? = null // Store the request ID
    fun compressImage(uncompressedUri: Uri) {
        viewModelScope.launch {
            try {
                _state.value = CompressionStatus.Compressing(uncompressedUri)
                val request = OneTimeWorkRequestBuilder<PhotoCompressionWorker>()
                    .setInputData(
                        workDataOf(
                            PhotoCompressionWorker.KEY_IMAGE_URI to uncompressedUri.toString(),
                            PhotoCompressionWorker.KEY_COMPRESSION_THRESHOLD to DEFAULT_COMPRESSION_THRESHOLD_BYTES
                        )
                    )
                    .setConstraints(
                        Constraints(
                            requiresStorageNotLow = true,
                        )
                    )
                    .build()
                currentWorkRequestId = request.id // Store the ID
                workManager.enqueue(request)
                workManager.getWorkInfoByIdFlow(request.id).collectLatest { workInfo ->
                    when (workInfo?.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            workInfo.outputData.getString(PhotoCompressionWorker.KEY_RESULT_PATH)
                                ?.toUri()
                                ?.let { compressedUri ->
                                    _state.value = CompressionStatus.Finished(
                                        uncompressedUri = uncompressedUri,
                                        compressedUri = compressedUri
                                    )
                                } ?: run {
                                // Handle case where result path is unexpectedly null even on success
                                _state.value =
                                    CompressionStatus.Error("Compression succeeded but result path is missing.")
                            }
                        }

                        WorkInfo.State.FAILED -> {
                            // You might want to get more specific error details from workInfo.outputData
                            // if PhotoCompressionWorker provides them.
                            _state.value = CompressionStatus.Error("Compression failed.")
                        }

                        WorkInfo.State.CANCELLED -> {
                            _state.value = CompressionStatus.Error("Compression was cancelled.")
                        }
                        // Handle other states like ENQUEUED, RUNNING, BLOCKED if necessary,
                        // though for a one-time request, SUCCEEDED, FAILED, and CANCELLED are often the most important.
                        else -> {
                            // Optionally update UI for intermediate states or do nothing
                            _state.value = CompressionStatus.Error("Unknown error!")
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = CompressionStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentWorkRequestId?.let {
            workManager.cancelWorkById(it)
        }
    }

    companion object {
        const val DEFAULT_COMPRESSION_THRESHOLD_BYTES = 1024 * 20L // 20KB
    }
}

sealed interface CompressionStatus {
    object Idle : CompressionStatus
    data class Compressing(val uncompressedUri: Uri) : CompressionStatus
    data class Error(val message: String) : CompressionStatus
    data class Finished(val uncompressedUri: Uri, val compressedUri: Uri) : CompressionStatus
}