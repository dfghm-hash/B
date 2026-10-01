package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ConcurrencyMode
import com.example.ui.CryptoViewModel
import com.example.ui.JobState
import com.example.ui.OperationMode
import com.example.ui.components.ChunkMatrix
import com.example.ui.components.SpeedGauge
import com.example.ui.components.WorkerGrid
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.SpeedGreen
import com.example.ui.theme.SpeedRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CryptScreen(
    viewModel: CryptoViewModel,
    modifier: Modifier = Modifier
) {
    val operationMode by viewModel.operationMode.collectAsState()
    val concurrencyMode by viewModel.concurrencyMode.collectAsState()
    val customWorkers by viewModel.customWorkerCount.collectAsState()
    val chunkSizeMb by viewModel.chunkSizeMb.collectAsState()
    val password by viewModel.password.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val jobState by viewModel.jobState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val lastEncResult by viewModel.lastEncryptionResult.collectAsState()
    val lastDecResult by viewModel.lastDecryptionResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val outputFilePath by viewModel.outputFilePath.collectAsState()
    val hardware = viewModel.hardwareSpecs

    var passwordVisible by remember { mutableStateOf(false) }

    // SAF File Open Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFileFromUri(it) }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Mode Switcher (Encrypt vs Decrypt)
        TabRow(
            selectedTabIndex = if (operationMode == OperationMode.ENCRYPT) 0 else 1,
            containerColor = Color(0xFF131C2E),
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        tabPositions[if (operationMode == OperationMode.ENCRYPT) 0 else 1]
                    ),
                    color = CyanPrimary
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = operationMode == OperationMode.ENCRYPT,
                onClick = { viewModel.setOperationMode(OperationMode.ENCRYPT) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ENCRYPT FILE", fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = CyanPrimary,
                unselectedContentColor = Color(0xFF94A3B8),
                modifier = Modifier.testTag("tab_encrypt")
            )
            Tab(
                selected = operationMode == OperationMode.DECRYPT,
                onClick = { viewModel.setOperationMode(OperationMode.DECRYPT) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DECRYPT FILE", fontWeight = FontWeight.Bold)
                    }
                },
                selectedContentColor = CyanPrimary,
                unselectedContentColor = Color(0xFF94A3B8),
                modifier = Modifier.testTag("tab_decrypt")
            )
        }

        // Live Execution Dashboard (Shown when RUNNING, PAUSED, COMPLETED, or FAILED)
        AnimatedVisibility(
            visible = jobState != JobState.IDLE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (jobState == JobState.COMPLETED) Icons.Default.CheckCircle
                                else if (jobState == JobState.FAILED) Icons.Default.Error
                                else Icons.Default.Speed,
                                contentDescription = null,
                                tint = if (jobState == JobState.COMPLETED) SpeedGreen
                                else if (jobState == JobState.FAILED) SpeedRed
                                else CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = progress?.operation ?: jobState.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        // State Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (jobState) {
                                JobState.RUNNING -> CyanPrimary.copy(alpha = 0.2f)
                                JobState.PAUSED -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                JobState.COMPLETED -> SpeedGreen.copy(alpha = 0.2f)
                                JobState.FAILED -> SpeedRed.copy(alpha = 0.2f)
                                else -> Color.Transparent
                            }
                        ) {
                            Text(
                                text = jobState.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when (jobState) {
                                    JobState.RUNNING -> CyanPrimary
                                    JobState.PAUSED -> Color(0xFFF59E0B)
                                    JobState.COMPLETED -> SpeedGreen
                                    JobState.FAILED -> SpeedRed
                                    else -> Color.White
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Real-Time Speed Gauge
                    SpeedGauge(
                        currentSpeedMbps = progress?.currentSpeedMbps ?: 0.0,
                        averageSpeedMbps = progress?.averageSpeedMbps ?: 0.0
                    )

                    // Linear Smooth Progress Bar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${"%.1f".format((progress?.percent ?: 0f) * 100f)}%",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = CyanPrimary
                            )
                            Text(
                                text = formatSize(progress?.processedBytes ?: 0L) + " / " +
                                        formatSize(progress?.totalBytes ?: selectedFile?.sizeBytes ?: 0L),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress?.percent ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CyanPrimary,
                            trackColor = Color(0xFF1E293B)
                        )
                    }

                    // Telemetry Grid: ETA, Workers, Memory
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryPill("ETA", if (jobState == JobState.COMPLETED) "0s" else "${progress?.etaSeconds ?: 0}s")
                        TelemetryPill("WORKERS", "${viewModel.resolveEffectiveWorkerCount()} Cores")
                        TelemetryPill("RAM USED", "${progress?.memoryUsageMb ?: 0} MB")
                    }

                    // Worker Grid & Chunk Pipeline
                    WorkerGrid(
                        totalWorkers = viewModel.resolveEffectiveWorkerCount(),
                        activeWorkers = progress?.activeWorkers ?: 0
                    )

                    ChunkMatrix(
                        currentChunk = progress?.currentChunkIndex ?: 0L,
                        totalChunks = progress?.totalChunks ?: 0L,
                        chunkSizeMb = chunkSizeMb
                    )

                    // Completion Results & Integrity Check
                    if (jobState == JobState.COMPLETED) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F291E))
                                .border(1.dp, SpeedGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = SpeedGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cryptographic Integrity Verified",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SpeedGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            lastEncResult?.let { enc ->
                                Text(
                                    text = "SHA-256: ${enc.sha256DigestHex}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFFE2E8F0)
                                )
                                Text(
                                    text = "Processed ${formatSize(enc.totalBytes)} in ${"%.2f".format(enc.durationMs / 1000.0)}s (${"%.1f".format(enc.averageSpeedMbps)} MB/s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            lastDecResult?.let { dec ->
                                Text(
                                    text = "Status: AES-256-GCM AEAD Tag Valid | Stream Footer Verified",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFE2E8F0)
                                )
                                Text(
                                    text = "Restored ${formatSize(dec.totalBytes)} in ${"%.2f".format(dec.durationMs / 1000.0)}s (${"%.1f".format(dec.averageSpeedMbps)} MB/s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (outputFilePath != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Saved: $outputFilePath",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = CyanPrimary
                                )
                            }
                        }
                    }

                    // Error Box if Failed
                    if (jobState == JobState.FAILED && errorMessage != null) {
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpeedRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpeedRed.copy(alpha = 0.1f))
                                .padding(10.dp)
                        )
                    }

                    // Control Buttons (Pause / Resume / Cancel / Reset)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (jobState == JobState.RUNNING) {
                            OutlinedButton(
                                onClick = { viewModel.pause() },
                                modifier = Modifier.weight(1f).testTag("button_pause")
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause")
                            }
                            Button(
                                onClick = { viewModel.cancel() },
                                colors = ButtonDefaults.buttonColors(containerColor = SpeedRed),
                                modifier = Modifier.weight(1f).testTag("button_cancel")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel")
                            }
                        } else if (jobState == JobState.PAUSED) {
                            Button(
                                onClick = { viewModel.resume() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                modifier = Modifier.weight(1f).testTag("button_resume")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume", color = Color(0xFF00363A))
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancel() },
                                modifier = Modifier.weight(1f).testTag("button_cancel_paused")
                            ) {
                                Text("Cancel")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.resetJob() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24334A)),
                                modifier = Modifier.fillMaxWidth().testTag("button_dismiss_job")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Another Task")
                            }
                        }
                    }
                }
            }
        }

        // File Selection Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. TARGET FILE",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CyanPrimary
                    )
                    Text(
                        text = "Streaming I/O",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                    )
                }

                if (selectedFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFile!!.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            Text(
                                text = formatSize(selectedFile!!.sizeBytes),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF94A3B8)
                            )
                        }
                        IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Change file", tint = Color.White)
                        }
                    }
                } else {
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("button_pick_file")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select File from Storage", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Quick Synthetic Generator for Benchmarking large files (50MB, 100MB, 500MB, 1GB)
                Column {
                    Text(
                        text = "Or generate synthetic test file:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(25, 50, 100, 500).forEach { sizeMb ->
                            OutlinedButton(
                                onClick = { viewModel.generateSyntheticFile(sizeMb) },
                                modifier = Modifier.weight(1f).testTag("button_gen_${sizeMb}mb"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${sizeMb}MB",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Password Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. PASSCODE & KEY DERIVATION",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CyanPrimary
                    )
                    Text(
                        text = "PBKDF2-HMAC-SHA256",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8))
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.setPassword(it) },
                    label = { Text("Encryption Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = CyanPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_password")
                )

                // Password Strength Bar
                val strength = calculateStrength(password)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Entropy: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = strength.first,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = strength.second
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { strength.third },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = strength.second,
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }

        // Engine Architecture & Multi-Core Tuning Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3. MULTI-CORE & CHUNK CONFIG",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CyanPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = SpeedGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${hardware.cpuCores} Cores Detected",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SpeedGreen
                        )
                    }
                }

                // Concurrency Mode selector
                Text(
                    text = "Worker Scheduling Strategy:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConcurrencyMode.values().forEach { mode ->
                        FilterChip(
                            selected = concurrencyMode == mode,
                            onClick = { viewModel.setConcurrencyMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyanPrimary
                            ),
                            modifier = Modifier.testTag("chip_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                // Custom worker slider if CUSTOM mode
                if (concurrencyMode == ConcurrencyMode.CUSTOM) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Worker Threads", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            Text(
                                "$customWorkers workers",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = CyanPrimary
                            )
                        }
                        Slider(
                            value = customWorkers.toFloat(),
                            onValueChange = { viewModel.setCustomWorkerCount(it.toInt()) },
                            valueRange = 1f..16f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanPrimary,
                                activeTrackColor = CyanPrimary
                            ),
                            modifier = Modifier.testTag("slider_workers")
                        )
                    }
                }

                // Chunk Size Selector
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Stream Chunk Buffer Size:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "${chunkSizeMb} MB",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 4, 8, 16, 32).forEach { mb ->
                            FilterChip(
                                selected = chunkSizeMb == mb,
                                onClick = { viewModel.setChunkSizeMb(mb) },
                                label = { Text("${mb}MB") },
                                modifier = Modifier.weight(1f).testTag("chip_chunk_${mb}mb")
                            )
                        }
                    }
                }

                // Hardware Capabilities Summary Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF131D2E))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hardware AES: ${if (hardware.hasHardwareAes) "ACTIVE (ARMv8/AES-NI)" else "STANDARD"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (hardware.hasHardwareAes) SpeedGreen else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Max Heap: ${hardware.maxHeapMemoryMb}MB",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Primary Start Action Button
        Button(
            onClick = { viewModel.startOperation() },
            enabled = selectedFile != null && password.isNotEmpty() && jobState != JobState.RUNNING,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                disabledContainerColor = Color(0xFF1E293B)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("button_start_operation")
        ) {
            Icon(
                imageVector = if (operationMode == OperationMode.ENCRYPT) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (selectedFile != null && password.isNotEmpty()) Color(0xFF00363A) else Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (operationMode == OperationMode.ENCRYPT) "ENCRYPT FILE (${viewModel.resolveEffectiveWorkerCount()} WORKERS)"
                else "DECRYPT FILE & VERIFY AUTHENTICITY",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (selectedFile != null && password.isNotEmpty()) Color(0xFF00363A) else Color(0xFF64748B)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TelemetryPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF162032))
            .border(1.dp, Color(0xFF24334A), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White
        )
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.2f MB".format(mb)
        kb >= 1.0 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

fun calculateStrength(pw: String): Triple<String, Color, Float> {
    if (pw.isEmpty()) return Triple("None", Color(0xFF64748B), 0.05f)
    var score = 0
    if (pw.length >= 8) score++
    if (pw.length >= 12) score++
    if (pw.any { it.isUpperCase() } && pw.any { it.isLowerCase() }) score++
    if (pw.any { it.isDigit() }) score++
    if (pw.any { !it.isLetterOrDigit() }) score++

    return when (score) {
        0, 1 -> Triple("Weak", SpeedRed, 0.25f)
        2, 3 -> Triple("Medium", Color(0xFFF59E0B), 0.55f)
        4 -> Triple("Strong", SpeedGreen, 0.8f)
        else -> Triple("Maximum", CyanPrimary, 1.0f)
    }
}
