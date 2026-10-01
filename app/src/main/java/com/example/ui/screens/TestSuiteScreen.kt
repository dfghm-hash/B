package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testing.TestCaseResult
import com.example.testing.TestStatus
import com.example.ui.CryptoViewModel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.SpeedGreen
import com.example.ui.theme.SpeedRed

@Composable
fun TestSuiteScreen(
    viewModel: CryptoViewModel,
    modifier: Modifier = Modifier
) {
    val isTesting by viewModel.isTesting.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val scrollState = rememberScrollState()

    val passedCount = testResults.count { it.status == TestStatus.PASSED }
    val failedCount = testResults.count { it.status == TestStatus.FAILED }
    val totalCount = testResults.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Test Suite Header Card
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
                        text = "AUTOMATED VERIFICATION SUITE",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CyanPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Runs 10 strict verification tests covering small/medium/large files, tampered chunk detection, wrong password rejection, truncated files, multi-core fidelity, and memory invariance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                if (totalCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryPill("PASSED", "$passedCount / $totalCount", SpeedGreen, Modifier.weight(1f))
                        SummaryPill("FAILED", "$failedCount", if (failedCount > 0) SpeedRed else Color(0xFF94A3B8), Modifier.weight(1f))
                    }
                }

                Button(
                    onClick = { viewModel.runAutomatedTests() },
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        disabledContainerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("button_run_tests")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            color = Color(0xFF00363A),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running Test Suite...", color = Color(0xFF00363A), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00363A))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EXECUTE ALL AUTOMATED TESTS", color = Color(0xFF00363A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Test Cases List
        if (testResults.isNotEmpty()) {
            Text(
                text = "TEST EXECUTION AUDIT",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            testResults.forEach { test ->
                TestCaseCard(test)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TestCaseCard(test: TestCaseResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                when (test.status) {
                    TestStatus.PASSED -> SpeedGreen.copy(alpha = 0.4f)
                    TestStatus.FAILED -> SpeedRed.copy(alpha = 0.6f)
                    TestStatus.RUNNING -> CyanPrimary.copy(alpha = 0.6f)
                    else -> DarkBorder
                },
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = test.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (test.status) {
                        TestStatus.PASSED -> SpeedGreen.copy(alpha = 0.2f)
                        TestStatus.FAILED -> SpeedRed.copy(alpha = 0.2f)
                        TestStatus.RUNNING -> CyanPrimary.copy(alpha = 0.2f)
                        else -> Color(0xFF1E293B)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (test.status) {
                            TestStatus.PASSED -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SpeedGreen, modifier = Modifier.size(14.dp))
                            TestStatus.FAILED -> Icon(Icons.Default.Error, contentDescription = null, tint = SpeedRed, modifier = Modifier.size(14.dp))
                            TestStatus.RUNNING -> CircularProgressIndicator(color = CyanPrimary, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            else -> Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = test.status.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = when (test.status) {
                                TestStatus.PASSED -> SpeedGreen
                                TestStatus.FAILED -> SpeedRed
                                TestStatus.RUNNING -> CyanPrimary
                                else -> Color(0xFF94A3B8)
                            }
                        )
                    }
                }
            }

            Text(
                text = test.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            if (test.details.isNotEmpty()) {
                Text(
                    text = test.details,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (test.status == TestStatus.PASSED) SpeedGreen else Color(0xFFE2E8F0)
                )
            }

            if (test.error != null) {
                Text(
                    text = "Error: ${test.error}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = SpeedRed
                )
            }

            if (test.durationMs > 0) {
                Text(
                    text = "Completed in ${test.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun SummaryPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF162032))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = color
        )
    }
}
