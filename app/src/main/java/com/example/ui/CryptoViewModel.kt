package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.benchmark.BenchmarkEngine
import com.example.benchmark.BenchmarkReport
import com.example.benchmark.HardwareProfiler
import com.example.benchmark.HardwareSpecs
import com.example.data.AppDatabase
import com.example.data.CryptoJobEntity
import com.example.engine.DecryptionResult
import com.example.engine.EncryptionResult
import com.example.engine.ParallelCryptoEngine
import com.example.engine.ProcessingProgress
import com.example.testing.AutomatedTestSuite
import com.example.testing.TestCaseResult
import com.example.testing.TestFileGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

enum class OperationMode {
    ENCRYPT,
    DECRYPT
}

enum class ConcurrencyMode(val label: String) {
    AUTO("Auto (Adaptive)"),
    BALANCED("Balanced"),
    MAX_PERFORMANCE("Max Performance"),
    CUSTOM("Custom")
}

enum class JobState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SelectedFileInfo(
    val uri: Uri?,
    val name: String,
    val sizeBytes: Long,
    val localFile: File? = null
)

class CryptoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val historyDao = db.cryptoHistoryDao()
    private val engine = ParallelCryptoEngine()
    private val automatedTestSuite = AutomatedTestSuite(application)

    // Hardware specifications
    val hardwareSpecs: HardwareSpecs = HardwareProfiler.profile()

    // Mode & Settings
    private val _operationMode = MutableStateFlow(OperationMode.ENCRYPT)
    val operationMode: StateFlow<OperationMode> = _operationMode.asStateFlow()

    private val _concurrencyMode = MutableStateFlow(ConcurrencyMode.AUTO)
    val concurrencyMode: StateFlow<ConcurrencyMode> = _concurrencyMode.asStateFlow()

    private val _customWorkerCount = MutableStateFlow(hardwareSpecs.recommendedWorkers)
    val customWorkerCount: StateFlow<Int> = _customWorkerCount.asStateFlow()

    private val _chunkSizeMb = MutableStateFlow(hardwareSpecs.recommendedChunkSizeMb)
    val chunkSizeMb: StateFlow<Int> = _chunkSizeMb.asStateFlow()

    // Password & Security
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    // Selected File
    private val _selectedFile = MutableStateFlow<SelectedFileInfo?>(null)
    val selectedFile: StateFlow<SelectedFileInfo?> = _selectedFile.asStateFlow()

    // Job Execution State
    private val _jobState = MutableStateFlow(JobState.IDLE)
    val jobState: StateFlow<JobState> = _jobState.asStateFlow()

    private val _progress = MutableStateFlow<ProcessingProgress?>(null)
    val progress: StateFlow<ProcessingProgress?> = _progress.asStateFlow()

    private val _lastEncryptionResult = MutableStateFlow<EncryptionResult?>(null)
    val lastEncryptionResult: StateFlow<EncryptionResult?> = _lastEncryptionResult.asStateFlow()

    private val _lastDecryptionResult = MutableStateFlow<DecryptionResult?>(null)
    val lastDecryptionResult: StateFlow<DecryptionResult?> = _lastDecryptionResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _outputFilePath = MutableStateFlow<String?>(null)
    val outputFilePath: StateFlow<String?> = _outputFilePath.asStateFlow()

    // Benchmark State
    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkStatus = MutableStateFlow("")
    val benchmarkStatus: StateFlow<String> = _benchmarkStatus.asStateFlow()

    private val _benchmarkReport = MutableStateFlow<BenchmarkReport?>(null)
    val benchmarkReport: StateFlow<BenchmarkReport?> = _benchmarkReport.asStateFlow()

    // Automated Test Suite State
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResults = MutableStateFlow<List<TestCaseResult>>(emptyList())
    val testResults: StateFlow<List<TestCaseResult>> = _testResults.asStateFlow()

    // History Flow
    val history: StateFlow<List<CryptoJobEntity>> = historyDao.getAllJobs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Collect engine progress updates
        viewModelScope.launch {
            engine.progress.collect {
                _progress.value = it
            }
        }
    }

    fun setOperationMode(mode: OperationMode) {
        _operationMode.value = mode
    }

    fun setConcurrencyMode(mode: ConcurrencyMode) {
        _concurrencyMode.value = mode
    }

    fun setCustomWorkerCount(count: Int) {
        _customWorkerCount.value = count.coerceIn(1, 16)
    }

    fun setChunkSizeMb(mb: Int) {
        _chunkSizeMb.value = mb
    }

    fun setPassword(pw: String) {
        _password.value = pw
    }

    fun selectFileFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            var fileName = "unknown_file"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "file"
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            // Auto-detect operation mode by extension
            if (fileName.endsWith(".enc", ignoreCase = true) || fileName.endsWith(".hypc", ignoreCase = true)) {
                _operationMode.value = OperationMode.DECRYPT
            }

            _selectedFile.value = SelectedFileInfo(uri = uri, name = fileName, sizeBytes = fileSize)
            _errorMessage.value = null
        }
    }

    fun generateSyntheticFile(sizeMb: Int) {
        viewModelScope.launch {
            _jobState.value = JobState.RUNNING
            _errorMessage.value = null
            try {
                val context = getApplication<Application>()
                val syntheticDir = File(context.cacheDir, "synthetic_files")
                syntheticDir.mkdirs()
                val targetFile = File(syntheticDir, "synthetic_${sizeMb}MB.dat")

                _progress.value = ProcessingProgress(
                    operation = "Generating",
                    processedBytes = 0,
                    totalBytes = sizeMb.toLong() * 1024 * 1024,
                    percent = 0f,
                    currentSpeedMbps = 0.0,
                    averageSpeedMbps = 0.0,
                    etaSeconds = 0,
                    activeWorkers = 1,
                    currentChunkIndex = 0,
                    totalChunks = 1,
                    memoryUsageMb = 0,
                    statusText = "Generating $sizeMb MB synthetic test file..."
                )

                TestFileGenerator.generateTestFile(
                    targetFile = targetFile,
                    sizeBytes = sizeMb.toLong() * 1024 * 1024
                ) { p ->
                    _progress.value = _progress.value?.copy(
                        percent = p,
                        processedBytes = (p * sizeMb * 1024 * 1024).toLong()
                    )
                }

                _selectedFile.value = SelectedFileInfo(
                    uri = null,
                    name = targetFile.name,
                    sizeBytes = targetFile.length(),
                    localFile = targetFile
                )
                _operationMode.value = OperationMode.ENCRYPT
                _jobState.value = JobState.IDLE
                _progress.value = null
            } catch (e: Exception) {
                _jobState.value = JobState.FAILED
                _errorMessage.value = "Failed to generate test file: ${e.message}"
            }
        }
    }

    fun resolveEffectiveWorkerCount(): Int {
        val cores = hardwareSpecs.cpuCores
        return when (_concurrencyMode.value) {
            ConcurrencyMode.AUTO -> hardwareSpecs.recommendedWorkers
            ConcurrencyMode.BALANCED -> maxOf(2, cores / 2)
            ConcurrencyMode.MAX_PERFORMANCE -> cores
            ConcurrencyMode.CUSTOM -> _customWorkerCount.value
        }
    }

    fun startOperation(customOutputUri: Uri? = null) {
        val file = _selectedFile.value ?: run {
            _errorMessage.value = "Please select or generate a file first."
            return
        }

        if (_password.value.isEmpty()) {
            _errorMessage.value = "Please enter an encryption password."
            return
        }

        val workers = resolveEffectiveWorkerCount()
        val chunkSizeBytes = _chunkSizeMb.value * 1024 * 1024
        val mode = _operationMode.value

        viewModelScope.launch {
            _jobState.value = JobState.RUNNING
            _errorMessage.value = null
            _lastEncryptionResult.value = null
            _lastDecryptionResult.value = null

            val context = getApplication<Application>()

            try {
                // Open input stream
                val inputStream: InputStream = if (file.localFile != null) {
                    FileInputStream(file.localFile)
                } else if (file.uri != null) {
                    context.contentResolver.openInputStream(file.uri)
                        ?: throw IllegalStateException("Cannot open input stream from URI")
                } else {
                    throw IllegalStateException("No file source found")
                }

                // Prepare output stream
                val (outputStream: OutputStream, outName: String) = if (customOutputUri != null) {
                    val os = context.contentResolver.openOutputStream(customOutputUri)
                        ?: throw IllegalStateException("Cannot write to destination URI")
                    Pair(os, "Selected Output Location")
                } else {
                    // Create default file in app output cache
                    val outDir = File(context.filesDir, "outputs")
                    outDir.mkdirs()
                    val outputFileName = if (mode == OperationMode.ENCRYPT) {
                        "${file.name}.hypc"
                    } else {
                        if (file.name.endsWith(".hypc", ignoreCase = true)) {
                            file.name.substringBeforeLast(".hypc")
                        } else if (file.name.endsWith(".enc", ignoreCase = true)) {
                            file.name.substringBeforeLast(".enc")
                        } else {
                            "decrypted_${file.name}"
                        }
                    }
                    val outFile = File(outDir, outputFileName)
                    if (outFile.exists()) outFile.delete()
                    Pair(FileOutputStream(outFile), outFile.absolutePath)
                }

                _outputFilePath.value = outName

                if (mode == OperationMode.ENCRYPT) {
                    val result = engine.encrypt(
                        input = inputStream,
                        totalSize = file.sizeBytes,
                        output = outputStream,
                        password = _password.value.toCharArray(),
                        workerCount = workers,
                        chunkSize = chunkSizeBytes
                    )
                    _lastEncryptionResult.value = result
                    _jobState.value = JobState.COMPLETED

                    // Record in history
                    historyDao.insertJob(
                        CryptoJobEntity(
                            fileName = file.name,
                            fileSizeBytes = result.totalBytes,
                            operation = "ENCRYPT",
                            durationMs = result.durationMs,
                            averageSpeedMbps = result.averageSpeedMbps,
                            workerCount = workers,
                            chunkSizeMb = _chunkSizeMb.value,
                            status = "COMPLETED",
                            sha256Checksum = result.sha256DigestHex
                        )
                    )
                } else {
                    val result = engine.decrypt(
                        input = inputStream,
                        output = outputStream,
                        password = _password.value.toCharArray(),
                        workerCount = workers
                    )
                    _lastDecryptionResult.value = result
                    _jobState.value = JobState.COMPLETED

                    historyDao.insertJob(
                        CryptoJobEntity(
                            fileName = file.name,
                            fileSizeBytes = result.totalBytes,
                            operation = "DECRYPT",
                            durationMs = result.durationMs,
                            averageSpeedMbps = result.averageSpeedMbps,
                            workerCount = workers,
                            chunkSizeMb = _chunkSizeMb.value,
                            status = "COMPLETED",
                            sha256Checksum = if (result.footerVerified) "VERIFIED" else "UNVERIFIED"
                        )
                    )
                }
            } catch (e: Exception) {
                _jobState.value = JobState.FAILED
                _errorMessage.value = e.message ?: e.javaClass.simpleName
                historyDao.insertJob(
                    CryptoJobEntity(
                        fileName = file.name,
                        fileSizeBytes = file.sizeBytes,
                        operation = mode.name,
                        durationMs = 0L,
                        averageSpeedMbps = 0.0,
                        workerCount = workers,
                        chunkSizeMb = _chunkSizeMb.value,
                        status = "FAILED"
                    )
                )
            }
        }
    }

    fun pause() {
        engine.pause()
        _jobState.value = JobState.PAUSED
    }

    fun resume() {
        engine.resume()
        _jobState.value = JobState.RUNNING
    }

    fun cancel() {
        engine.cancel()
        _jobState.value = JobState.CANCELLED
    }

    fun resetJob() {
        _jobState.value = JobState.IDLE
        _progress.value = null
        _errorMessage.value = null
    }

    fun runBenchmark(payloadSizeMb: Int = 128) {
        viewModelScope.launch {
            _isBenchmarking.value = true
            _benchmarkReport.value = null
            try {
                val workers = resolveEffectiveWorkerCount()
                val report = BenchmarkEngine.runFullBenchmark(
                    payloadSizeMb = payloadSizeMb,
                    workerCount = workers,
                    chunkSizeMb = _chunkSizeMb.value
                ) { status ->
                    _benchmarkStatus.value = status
                }
                _benchmarkReport.value = report
                _benchmarkStatus.value = "Benchmark completed!"
            } catch (e: Exception) {
                _benchmarkStatus.value = "Benchmark error: ${e.message}"
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    fun runAutomatedTests() {
        viewModelScope.launch {
            _isTesting.value = true
            try {
                automatedTestSuite.runAllTests { list ->
                    _testResults.value = list
                }
            } catch (e: Exception) {
                _errorMessage.value = "Test execution failed: ${e.message}"
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyDao.deleteJob(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyDao.clearHistory()
        }
    }
}
