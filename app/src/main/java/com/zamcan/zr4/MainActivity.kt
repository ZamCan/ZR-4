package com.zamcan.zr4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ObservatoryBackground = Color(0xFF101310)
private val ObservatorySurface = Color(0xFF1A201B)
private val ObservatoryAccent = Color(0xFFD6B56D)
private val ObservatoryMuted = Color(0xFFA9B2A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ObservatoryAccent,
                    background = ObservatoryBackground,
                    surface = ObservatorySurface,
                    onPrimary = Color(0xFF17170F),
                    onBackground = Color(0xFFF0F1E9),
                    onSurface = Color(0xFFF0F1E9)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObservatoryBackground
                ) {
                    ObservatoryHome()
                }
            }
        }
    }
}

@Composable
private fun ObservatoryHome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ZAMCAN / INSTRUMENTS",
                    color = ObservatoryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "RF Observatory",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("ZR-4  ·  Local measurement workspace", color = ObservatoryMuted, fontSize = 13.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObservatorySurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("OBSERVATION STATUS", color = ObservatoryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Ready to observe", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(
                    "No measurement source is connected. Live values will appear only when a supported source provides real observations.",
                    color = ObservatoryMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("OFFLINE-FIRST")
                    StatusPill("NO SIMULATED DATA")
                }
            }
        }

        Text("OBSERVATION SOURCES", color = ObservatoryMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        SourceCard(
            title = "Optical observation",
            detail = "Camera-based visible light and indicator timing",
            status = "PLANNED"
        )
        SourceCard(
            title = "Wi-Fi & Bluetooth",
            detail = "Android-reported nearby radio observations",
            status = "PLANNED"
        )
        SourceCard(
            title = "USB SDR receiver",
            detail = "External spectrum and IQ data when supported hardware is attached",
            status = "HARDWARE REQUIRED"
        )
        SourceCard(
            title = "Measurement sessions",
            detail = "Local records, source metadata, and CSV export",
            status = "PLANNED"
        )

        Spacer(Modifier.height(2.dp))
        Button(
            onClick = { },
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF34392F))
        ) {
            Text("Start measurement — source not connected")
        }
        Text(
            "A phone camera observes light, not RF. Continuous spectrum analysis requires a compatible external receiver.",
            color = ObservatoryMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Text("ZamCan · ZR-4 · Foundation build", color = ObservatoryMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SourceCard(title: String, detail: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObservatorySurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = ObservatoryMuted, fontSize = 13.sp, lineHeight = 19.sp)
            Text(status, color = ObservatoryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        color = Color(0xFF2A3128),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = ObservatoryAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
