package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.example.ui.CryptoViewModel
import com.example.ui.screens.BenchmarkScreen
import com.example.ui.screens.CryptScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.TestSuiteScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.HyperCipherTheme
import com.example.ui.theme.SpeedGreen

enum class AppNavDestination(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CIPHER("Crypt", Icons.Default.Lock),
    BENCHMARK("Benchmark", Icons.Default.Assessment),
    TESTS("Tests", Icons.Default.Verified),
    HISTORY("History", Icons.Default.History)
}

class MainActivity : ComponentActivity() {

    private val viewModel: CryptoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HyperCipherTheme(darkTheme = true) {
                HyperCipherApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HyperCipherApp(viewModel: CryptoViewModel) {
    var currentDestination by remember { mutableStateOf(AppNavDestination.CIPHER) }

    // Handle back button to return to home if on secondary screen
    if (currentDestination != AppNavDestination.CIPHER) {
        BackHandler {
            currentDestination = AppNavDestination.CIPHER
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HyperCipherTopBar(hardware = viewModel.hardwareSpecs)
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                tonalElevation = 8.dp,
                modifier = Modifier.border(width = 1.dp, color = DarkBorder)
            ) {
                AppNavDestination.values().forEach { dest ->
                    val isSelected = currentDestination == dest
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = dest },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = dest.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00363A),
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkSurface)
        ) {
            when (currentDestination) {
                AppNavDestination.CIPHER -> CryptScreen(viewModel = viewModel)
                AppNavDestination.BENCHMARK -> BenchmarkScreen(viewModel = viewModel)
                AppNavDestination.TESTS -> TestSuiteScreen(viewModel = viewModel)
                AppNavDestination.HISTORY -> HistoryScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HyperCipherTopBar(hardware: com.example.benchmark.HardwareSpecs) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .windowInsetsPadding(WindowInsets.statusBars)
            .border(1.dp, DarkBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF004D54)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "HyperCipher Logo",
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "HYPERCIPHER",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "High-Performance Streaming AES-256-GCM",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary
                    )
                }
            }

            // Hardware Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1E293B)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = SpeedGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${hardware.cpuCores}C • ${if (hardware.hasHardwareAes) "HW-AES" else "SW"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SpeedGreen
                    )
                }
            }
        }
    }
}
