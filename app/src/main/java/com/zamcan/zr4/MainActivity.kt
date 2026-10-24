package com.zamcan.zr4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0B100D)
private val Panel = Color(0xFF151D17)
private val PanelRaised = Color(0xFF1C2820)
private val Line = Color(0xFF2D3A30)
private val Gold = Color(0xFFD6B56D)
private val Paper = Color(0xFFF0F2E9)
private val Muted = Color(0xFFA7B2A6)
private val Green = Color(0xFF8DB99A)

private enum class AppSection(val label: String) {
    Overview("Overview"),
    Sources("Sources"),
    Sessions("Sessions"),
    About("About")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold,
                    onPrimary = Ink,
                    secondary = Green,
                    background = Ink,
                    surface = Panel,
                    onBackground = Paper,
                    onSurface = Paper
                )
            ) {
                Zr4App()
            }
        }
    }
}

@Composable
private fun Zr4App() {
    var section by remember { mutableStateOf(AppSection.Overview) }
    var selectedSource by remember { mutableStateOf("External SDR") }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(
                containerColor = Panel,
                contentColor = Paper
            ) {
                AppSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = {
                            Text(
                                text = when (item) {
                                    AppSection.Overview -> "01"
                                    AppSection.Sources -> "02"
                                    AppSection.Sessions -> "03"
                                    AppSection.About -> "04"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        label = { Text(item.label, maxLines = 1, fontSize = 11.sp) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AppHeader()
            when (section) {
                AppSection.Overview -> OverviewScreen(
                    onExploreSources = { section = AppSection.Sources },
                    onOpenSessions = { section = AppSection.Sessions }
                )
                AppSection.Sources -> SourcesScreen(
                    selectedSource = selectedSource,
                    onSelectSource = { selectedSource = it }
                )
                AppSection.Sessions -> SessionsScreen()
                AppSection.About -> AboutScreen()
            }
            Text(
                "ZAMCAN  /  ZR 4  /  LOCAL WORKSPACE",
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(PanelRaised, RoundedCornerShape(16.dp))
                .border(1.dp, Line, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("ZR", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("4", color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "ZAMCAN RF OBSERVATORY",
                color = Gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            Text("ZR 4", color = Paper, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            Text("Scientific observation workspace", color = Muted, fontSize = 12.sp)
        }
        Surface(
            shape = CircleShape,
            color = Color(0xFF25342A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B5942))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(6.dp).background(Green, CircleShape))
                Text("LOCAL", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    onExploreSources: () -> Unit,
    onOpenSessions: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow("OBSERVATORY / OVERVIEW")
            Text("Measure what is real.", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp)
            Text(
                "A local-first workspace for optical observations, Android-reported radio data, and compatible external receivers.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }

        StatusHero(onExploreSources)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("LIVE SPECTRUM", "NO SOURCE")
            SpectrumPanel()
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("OBSERVATION CHANNELS", "4 MODULES")
            BoxWithConstraints {
                val wide = maxWidth >= 580.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SourceSummary("Optical", "Camera-visible light", "IN DEVELOPMENT", Modifier.weight(1f))
                        SourceSummary("Wi-Fi / BLE", "Android API observations", "IN DEVELOPMENT", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SourceSummary("External SDR", "Receiver-dependent RF data", "HARDWARE REQUIRED", Modifier.weight(1f))
                        SourceSummary("Sessions", "Local records and CSV", "IN DEVELOPMENT", Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SourceSummary("Optical observation", "Camera-visible light and indicator timing", "IN DEVELOPMENT")
                        SourceSummary("Wi-Fi and Bluetooth", "Only observations exposed by Android", "IN DEVELOPMENT")
                        SourceSummary("USB SDR receiver", "RF spectrum only with compatible hardware", "HARDWARE REQUIRED")
                        SourceSummary("Measurement sessions", "Local records and export", "IN DEVELOPMENT")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onExploreSources,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Explore sources", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onOpenSessions,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Session workspace", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            }
        }

        Text(
            "Accuracy rule: no invented readings. A phone camera detects light, not radio frequency. Continuous RF analysis requires a compatible receiver.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun StatusHero(onExploreSources: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Eyebrow("SYSTEM READINESS")
                Surface(
                    color = Color(0xFF332D1D),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "STANDBY",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Gold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
            Text("No measurement source connected", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)
            Text(
                "The interface is ready to configure. Live acquisition remains unavailable until a supported observation source is implemented and connected.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrustTag("OFFLINE-FIRST")
                TrustTag("SOURCE-VERIFIED")
            }
            Button(
                onClick = onExploreSources,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = Gold)
            ) {
                Text("Configure observation source  →", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SpectrumPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Spectrum preview", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Waiting for verified input", color = Muted, fontSize = 11.sp)
            }
            Text("RF / —", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Ink, RoundedCornerShape(12.dp))
                .border(1.dp, Line, RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val rows = 4
                val columns = 6
                for (i in 0..rows) {
                    val y = size.height * i / rows
                    drawLine(Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                for (i in 0..columns) {
                    val x = size.width * i / columns
                    drawLine(Line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("NO DATA", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Connect a supported source to plot observations", color = Muted, fontSize = 10.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FREQUENCY AXIS: UNAVAILABLE", color = Muted, fontSize = 9.sp, letterSpacing = 0.5.sp)
            Text("0 SAMPLES", color = Muted, fontSize = 9.sp, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun SourcesScreen(
    selectedSource: String,
    onSelectSource: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow("OBSERVATORY / INPUTS")
            Text("Observation sources", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Choose a source to inspect its intended role and current implementation status.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
        SourceDetailCard(
            title = "Optical observation",
            subtitle = "Camera-based visible-light sampling",
            status = "NOT IMPLEMENTED",
            detail = "Intended for visible indicator states, brightness changes, and blink timing. Camera frame rate, exposure, and lighting limit the timing accuracy. This source cannot detect RF directly.",
            selected = selectedSource == "Optical observation",
            onClick = { onSelectSource("Optical observation") }
        )
        SourceDetailCard(
            title = "Wi-Fi and Bluetooth",
            subtitle = "Android-reported observations",
            status = "NOT IMPLEMENTED",
            detail = "Will use supported Android APIs and required runtime permissions. Results depend on OS version, permissions, radio state, and what the device exposes; they are not a calibrated wideband spectrum.",
            selected = selectedSource == "Wi-Fi and Bluetooth",
            onClick = { onSelectSource("Wi-Fi and Bluetooth") }
        )
        SourceDetailCard(
            title = "External SDR",
            subtitle = "USB host / supported receiver",
            status = "HARDWARE REQUIRED",
            detail = "Continuous RF spectrum or IQ acquisition requires a compatible SDR, supported USB interface, permission handling, and a validated receiver pipeline. No RF readings are available without it.",
            selected = selectedSource == "External SDR",
            onClick = { onSelectSource("External SDR") }
        )
        SourceDetailCard(
            title = "Measurement sessions",
            subtitle = "Local records and exports",
            status = "NOT IMPLEMENTED",
            detail = "Planned session records will retain timestamps, source identity, units, configuration, and relevant limitations. CSV export will only include values actually acquired or explicitly entered.",
            selected = selectedSource == "Measurement sessions",
            onClick = { onSelectSource("Measurement sessions") }
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = PanelRaised)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Eyebrow("SELECTED MODULE")
                Text(selectedSource, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "This module is a planning and interface selection only. Acquisition is not active in this build.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun SessionsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow("OBSERVATORY / RECORDS")
            Text("Session workspace", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("A clear home for repeatable, traceable experiments.", color = Muted, fontSize = 13.sp)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(58.dp).background(PanelRaised, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("—", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Light)
                }
                Text("No sessions recorded", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Session storage is not implemented yet. No placeholder sessions or sample measurements are shown as real records.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
        SectionHeading("PLANNED SESSION METADATA", "TRACEABLE")
        InfoRow("Timestamp", "Local time with explicit timezone handling")
        InfoRow("Observation source", "Device / adapter and source type")
        InfoRow("Units and configuration", "Recorded alongside every measurement")
        InfoRow("Export", "CSV after storage and validation are implemented")
    }
}

@Composable
private fun AboutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Eyebrow("PRODUCT / IDENTITY")
        Text("ZamCan RF Observatory", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp)
        Text(
            "ZR 4 is a native Android scientific observation workspace designed around traceable inputs, offline-first operation, and honest measurement limits.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        InfoRow("Product", "ZR 4")
        InfoRow("Formal name", "ZamCan RF Observatory")
        InfoRow("Package", "com.zamcan.zr4")
        InfoRow("Build", "0.1.0 · Foundation interface")
        InfoRow("Operating mode", "Local-first")
        SectionHeading("ENGINEERING PRINCIPLES", "NON-NEGOTIABLE")
        InfoRow("Data integrity", "Never fabricate live readings")
        InfoRow("Measurement provenance", "Identify the actual source and limitations")
        InfoRow("RF capability", "Receiver-dependent; no transmission")
        InfoRow("Privacy", "Use authorized devices and respect local regulations")
        Text(
            "This version is an interface foundation. The acquisition engines, persistent session database, plots based on actual samples, and hardware integrations require separate implementation and verification.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun SourceSummary(
    title: String,
    detail: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Panel, RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, minLines = 2)
        Text(status, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SourceDetailCard(
    title: String,
    subtitle: String,
    status: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) PanelRaised else Panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Gold.copy(alpha = 0.75f) else Line)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(status, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            }
            Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
            Text(if (selected) "SELECTED MODULE" else "TAP TO INSPECT", color = if (selected) Gold else Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(trailing, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
}

@Composable
private fun TrustTag(text: String) {
    Surface(
        color = PanelRaised,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            color = Green,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label.uppercase(), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Paper, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
