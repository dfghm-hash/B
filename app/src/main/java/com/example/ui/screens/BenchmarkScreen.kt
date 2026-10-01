package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.benchmark.BenchmarkReport
import com.example.ui.CryptoViewModel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.SpeedGreen

@Composable
fun BenchmarkScreen(
    viewModel: CryptoViewModel,
    modifier: Modifier = Modifier
) {
    val isBenchmarking by viewModel.isBenchmarking.collectAsState()
    val benchmarkStatus by viewModel.benchmarkStatus.collectAsState()
    val report by viewModel.benchmarkReport.collectAsState()
    val hardware = viewModel.hardwareSpecs

    var selectedPayloadMb by remember { mutableStateOf(64) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hardware Specifications Card
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
                        text = "HARDWARE PROFILE",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CyanPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hardware.hasHardwareAes) SpeedGreen.copy(alpha = 0.2f) else Color(0xFF334155)
                    ) {
                        Text(
                            text = if (hardware.hasHardwareAes) "ARMv8 / AES-NI ACTIVE" else "STANDARD JCA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (hardware.hasHardwareAes) SpeedGreen else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpecPill(
                        icon = Icons.Default.Memory,
                        title = "CPU CORES",
                        value = "${hardware.cpuCores} Cores",
                        modifier = Modifier.weight(1f)
                    )
                    SpecPill(
                        icon = Icons.Default.Bolt,
                        title = "MAX HEAP",
                        value = "${hardware.maxHeapMemoryMb} MB",
                        modifier = Modifier.weight(1f)
                    )
                    SpecPill(
                        icon = Icons.Default.Speed,
                        title = "CHUNK SIZE",
                        value = "${hardware.recommendedChunkSizeMb} MB",
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Architecture: ${hardware.osArch} • Recommended Concurrency: ${hardware.recommendedWorkers} Workers",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Benchmark Launcher Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "HARDWARE THROUGHPUT CALIBRATION",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = CyanPrimary
                )

                Text(
                    text = "Select synthetic payload size to calibrate real encryption/decryption throughput and detect system bottlenecks:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(32, 64, 128, 256).forEach { mb ->
                        FilterChip(
                            selected = selectedPayloadMb == mb,
                            onClick = { selectedPayloadMb = mb },
                            label = { Text("${mb}MB") },
                            modifier = Modifier.weight(1f).testTag("chip_bench_${mb}mb")
                        )
                    }
                }

                Button(
                    onClick = { viewModel.runBenchmark(selectedPayloadMb) },
                    enabled = !isBenchmarking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        disabledContainerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("button_run_benchmark")
                ) {
                    if (isBenchmarking) {
                        CircularProgressIndicator(
                            color = Color(0xFF00363A),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calibrating...", color = Color(0xFF00363A), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = Color(0xFF00363A))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "RUN HARDWARE BENCHMARK (${selectedPayloadMb} MB)",
                            color = Color(0xFF00363A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (benchmarkStatus.isNotEmpty()) {
                    Text(
                        text = benchmarkStatus,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = CyanPrimary
                    )
                }
            }
        }

        // Benchmark Results Report
        if (report != null) {
            BenchmarkReportView(report!!)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BenchmarkReportView(report: BenchmarkReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CALIBRATION REPORT",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Payload: ${report.testSizeMb} MB",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = CyanPrimary
                    )
                )
            }

            // High-Level Metrics Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportStatCard(
                    title = "AVERAGE",
                    value = "${"%.1f".format(report.averageThroughputMbps)} MB/s",
                    modifier = Modifier.weight(1f)
                )
                ReportStatCard(
                    title = "PEAK SPEED",
                    value = "${"%.1f".format(report.peakThroughputMbps)} MB/s",
                    modifier = Modifier.weight(1f)
                )
                ReportStatCard(
                    title = "TOTAL TIME",
                    value = "${"%.2f".format(report.totalTimeSec)} s",
                    modifier = Modifier.weight(1f)
                )
            }

            // Pipeline Breakdown Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF162032))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pipeline Throughput Breakdown",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                BreakdownRow("Simulated Read Bandwidth", "${"%.1f".format(report.simulatedReadSpeedMbps)} MB/s")
                BreakdownRow("AES-256-GCM Encryption", "${"%.1f".format(report.encryptionSpeedMbps)} MB/s")
                BreakdownRow("AES-256-GCM Decryption", "${"%.1f".format(report.decryptionSpeedMbps)} MB/s")
                BreakdownRow("Simulated Write Bandwidth", "${"%.1f".format(report.simulatedWriteSpeedMbps)} MB/s")
                BreakdownRow("Active Worker Pool", "${report.workerCount} Workers")
            }

            // Bottleneck Detection Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1F2937))
                    .padding(12.dp)
            ) {
                Text(
                    text = "SYSTEM BOTTLENECK ESTIMATION",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.estimatedBottleneck,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Processing 5 GB on this hardware is estimated at ~${"%.1f".format((5120.0 / report.averageThroughputMbps))} seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            // Multi-Core Scaling Chart
            if (report.scalingResults.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF162032))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Multi-Core Scaling Efficiency",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    val maxSpeed = report.scalingResults.maxOfOrNull { it.throughputMbps } ?: 1.0

                    report.scalingResults.forEach { scale ->
                        val ratio = (scale.throughputMbps / maxSpeed).coerceIn(0.05, 1.0).toFloat()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${scale.workers} Worker${if (scale.workers > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                                Text(
                                    text = "${"%.1f".format(scale.throughputMbps)} MB/s (${"%.1f".format(scale.speedupRatio)}x)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyanPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1E293B))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SpeedGreen)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
    }
}

@Composable
fun ReportStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF162032))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = CyanPrimary
        )
    }
}

@Composable
fun SpecPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF162032))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
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
