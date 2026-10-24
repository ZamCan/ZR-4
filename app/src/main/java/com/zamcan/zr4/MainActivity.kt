package com.zamcan.zr4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ZR 4 - ZamCan RF Observatory
 * ---------------------------------------------------------------------------
 * Premium glassmorphic interface system ("Observatory Glass").
 *
 * Colour relationships are deliberate, not decorative:
 *  - Ink emerald-black is the observatory at night (base).
 *  - Warm gold is the single action / instrument accent (analogous to brass
 *    instrument fittings) and is reserved for primary actions and identity.
 *  - Signal mint-teal is gold's cool complement: it carries "live system /
 *    ready" meaning only, never decoration on its own.
 *  - A whisper of violet appears only in the aurora wash and the Sessions
 *    module, completing a restrained gold-teal-violet triad at very low alpha.
 *
 * Glass is simulated without any blur library (offline-first, Termux-safe):
 * layered translucent gradients, a top light-catch, a gradient hairline
 * border and a soft coloured shadow over an aurora canvas. No new dependency.
 *
 * Product honesty is unchanged: no simulated readings are ever presented as
 * live measurements. The spectrum still says NO DATA until a verified source
 * supplies real samples.
 */
private val Ink        = Color(0xFF090D0A)
private val InkDeep    = Color(0xFF03070B)
private val Emerald    = Color(0xFF151B16)
private val Gold       = Color(0xFFD6B56D)
private val GoldLight  = Color(0xFFF0D493)
private val GoldDeep   = Color(0xFFB98F3E)
private val InkLow     = Color(0xFF07130D)
private val Amber      = Color(0xFFFFB86B)
private val Mint       = Color(0xFF6EE7B7)
private val Stone      = Color(0xFFC9BBA6)
private val Paper      = Color(0xFFF2F5EC)
private val Muted      = Color(0xFFAFBDB0)
private val GlassLine  = Color.White.copy(alpha = 0.16f)

private fun glassBrush(tint: Color = Color.White): Brush = Brush.verticalGradient(
    colors = listOf(
        tint.copy(alpha = 0.16f),
        Color.White.copy(alpha = 0.07f),
        Color.Black.copy(alpha = 0.18f),
    ),
)

private fun glassBorder(accent: Color = Gold): Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.42f),
        Color.White.copy(alpha = 0.08f),
        accent.copy(alpha = 0.32f),
    ),
)

private enum class AppSection(val label: String, val icon: InstrumentIconKind) {
    Overview("Overview", InstrumentIconKind.Crosshair),
    Sources("Sources", InstrumentIconKind.Waveform),
    Sessions("Sessions", InstrumentIconKind.Layers),
    About("About", InstrumentIconKind.Info),
}

private enum class InstrumentIconKind {
    Crosshair, Waveform, Layers, Info, Lens, Radio, Antenna,
    Clock, Settings, Export, Check, Lock, Grid, Diamond, Home,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold,
                    onPrimary = Ink,
                    secondary = Mint,
                    background = Ink,
                    surface = Emerald,
                    onBackground = Paper,
                    onSurface = Paper,
                ),
            ) {
                Zr4App()
            }
        }
    }
}

/* ------------------------------------------------------------------ shell */

@Composable
private fun Zr4App() {
    var section by remember { mutableStateOf(AppSection.Overview) }
    var selectedSource by remember { mutableStateOf("External SDR") }

    Box(Modifier.fillMaxSize().background(InkDeep)) {
        AuroraBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(glassBrush(Gold))
                        .border(1.dp, glassBorder(), RoundedCornerShape(22.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    AppHeader()
                }
            },
            bottomBar = {
                FloatingGlassNav(
                    section = section,
                    onSelect = { section = it },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 8.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Crossfade(
                    targetState = section,
                    animationSpec = tween(durationMillis = 340),
                    label = "section",
                ) { current ->
                    when (current) {
                        AppSection.Overview -> OverviewScreen(
                            selectedSource = selectedSource,
                            onExploreSources = { section = AppSection.Sources },
                            onOpenSessions = { section = AppSection.Sessions },
                        )
                        AppSection.Sources -> SourcesScreen(
                            selectedSource = selectedSource,
                            onSelectSource = { selectedSource = it },
                        )
                        AppSection.Sessions -> SessionsScreen()
                        AppSection.About -> AboutScreen()
                    }
                }

                Text(
                    "ZAMCAN  ·  ZR 4  ·  LOCAL WORKSPACE",
                    color = Muted.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}

/** Slow aurora wash: gold (top-right), teal (left), violet (bottom). Decorative only. */
@Composable
private fun AuroraBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(InkDeep, Ink, InkLow)),
            ),
    )
    Canvas(Modifier.fillMaxSize()) {
        val glows = listOf(
            Triple(Gold.copy(alpha = 0.32f), Offset(size.width * 0.92f, size.height * 0.02f), size.width * 0.55f),
            Triple(Mint.copy(alpha = 0.20f), Offset(size.width * 0.02f, size.height * 0.38f), size.width * 0.48f),
            Triple(Stone.copy(alpha = 0.08f), Offset(size.width * 0.78f, size.height * 1.0f), size.width * 0.52f),
        )
        glows.forEach { (color, center, radius) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

@Composable
private fun FloatingGlassNav(section: AppSection, onSelect: (AppSection) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.55f))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.Black.copy(alpha = 0.34f)),
                    ),
                )
                .border(1.dp, glassBorder(), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSection.entries.forEach { item ->
                val selected = section == item
                val pill by animateColorAsState(
                    if (selected) Gold.copy(alpha = 0.22f) else Color.Transparent,
                    tween(260), label = "navPill",
                )
                val tint by animateColorAsState(
                    if (selected) Gold else Muted,
                    tween(260), label = "navTint",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(pill)
                        .border(
                            1.dp,
                            if (selected) Gold.copy(alpha = 0.45f) else Color.Transparent,
                            RoundedCornerShape(20.dp),
                        )
                        .selectable(selected = selected, role = Role.Tab) { onSelect(item) }
                        .semantics { contentDescription = item.label }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    InstrumentIcon(item.icon, tint, Modifier.size(22.dp))
                    Text(item.label, color = tint, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(10.dp, RoundedCornerShape(17.dp), spotColor = Gold.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(17.dp))
                .background(glassBrush(Gold))
                .border(1.dp, glassBorder(), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Symbol only at top-bar size: the ZR 4 wordmark beside it is the
            // text; lettering inside a 47dp tile would be illegible noise.
            Image(
                painter = painterResource(R.drawable.brand_symbol),
                contentDescription = "ZR 4 — ZamCan RF Observatory logo",
                modifier = Modifier.size(50.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "ZAMCAN RF OBSERVATORY",
                color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            )
            Text("ZR 4", color = Paper, fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
            Text("Scientific observation workspace", color = Muted, fontSize = 12.sp)
        }
        GlassChip(dot = Mint, text = "LOCAL")
    }
}

/* ------------------------------------------------------------- primitives */

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color = Gold,
    shape: Shape = RoundedCornerShape(26.dp),
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = accent.copy(alpha = 0.22f))
            .clip(shape)
            .background(glassBrush(accent))
            .border(1.dp, glassBorder(accent), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
private fun GlassChip(dot: Color, text: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, dot.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(text, color = dot, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
    }
}

@Composable
private fun IconBadge(icon: InstrumentIconKind, tint: Color, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.07f))))
            .border(1.dp, tint.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        InstrumentIcon(icon, tint, Modifier.size(size * 0.52f))
    }
}

/**
 * Precision line icons drawn as vectors on Canvas: one stroke weight, round
 * caps/joins, no icon-font or PNG dependency (this module ships no icon
 * library), and no text glyphs. Decorative only — every icon sits beside a
 * text label, and interactive semantics live on the containing control.
 */
@Composable
private fun InstrumentIcon(kind: InstrumentIconKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h / 2f
        val sw = w * 0.085f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(cx + x1 * w, cy + y1 * h), Offset(cx + x2 * w, cy + y2 * h), sw, StrokeCap.Round)
        fun ring(r: Float, cxx: Float = 0f, cyy: Float = 0f) =
            drawCircle(tint, r * w, Offset(cx + cxx * w, cy + cyy * h), style = stroke)
        fun dot(r: Float, cxx: Float = 0f, cyy: Float = 0f) =
            drawCircle(tint, r * w, Offset(cx + cxx * w, cy + cyy * h))
        fun arc(r: Float, start: Float, sweep: Float, cxx: Float = 0f, cyy: Float = 0f) =
            drawArc(tint, start, sweep, false, Offset(cx + (cxx - r) * w, cy + (cyy - r) * h),
                Size(2f * r * w, 2f * r * h), style = stroke)
        when (kind) {
            InstrumentIconKind.Crosshair -> { ring(0.27f); dot(0.05f)
                line(0f, -0.46f, 0f, -0.34f); line(0f, 0.34f, 0f, 0.46f)
                line(-0.46f, 0f, -0.34f, 0f); line(0.34f, 0f, 0.46f, 0f) }
            InstrumentIconKind.Waveform -> {
                val pts = listOf(-0.44f to 0f, -0.30f to 0f, -0.20f to -0.30f, -0.08f to 0.26f,
                    0.02f to -0.12f, 0.12f to 0.10f, 0.20f to 0f, 0.44f to 0f)
                for (i in 0 until pts.lastIndex) line(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second) }
            InstrumentIconKind.Layers -> {
                line(0f, -0.38f, 0.40f, -0.14f); line(0.40f, -0.14f, 0f, 0.10f); line(0f, 0.10f, -0.40f, -0.14f); line(-0.40f, -0.14f, 0f, -0.38f)
                line(-0.40f, 0.06f, 0f, 0.30f); line(0f, 0.30f, 0.40f, 0.06f)
                line(-0.40f, 0.22f, 0f, 0.46f); line(0f, 0.46f, 0.40f, 0.22f) }
            InstrumentIconKind.Info -> { ring(0.40f); dot(0.055f, 0f, -0.18f); line(0f, -0.04f, 0f, 0.24f) }
            InstrumentIconKind.Lens -> { ring(0.38f); ring(0.20f); dot(0.07f) }
            InstrumentIconKind.Radio -> { dot(0.06f, 0f, 0.22f); arc(0.20f, -150f, 120f, 0f, 0.22f); arc(0.34f, -150f, 120f, 0f, 0.22f) }
            InstrumentIconKind.Antenna -> { dot(0.06f, 0f, -0.20f); arc(0.22f, -165f, 55f, 0f, -0.20f); arc(0.22f, -70f, 55f, 0f, -0.20f)
                arc(0.36f, -165f, 55f, 0f, -0.20f); arc(0.36f, -70f, 55f, 0f, -0.20f)
                line(0f, -0.14f, 0f, 0.32f); line(-0.22f, 0.40f, 0.22f, 0.40f) }
            InstrumentIconKind.Clock -> { ring(0.40f); line(0f, 0f, 0f, -0.24f); line(0f, 0f, 0.17f, 0.10f) }
            InstrumentIconKind.Settings -> { ring(0.17f)
                listOf(0f to -1f, 0.87f to -0.5f, 0.87f to 0.5f, 0f to 1f, -0.87f to 0.5f, -0.87f to -0.5f)
                    .forEach { (dx, dy) -> line(dx * 0.24f, dy * 0.24f, dx * 0.40f, dy * 0.40f) } }
            InstrumentIconKind.Export -> { line(0f, -0.40f, 0f, 0.14f); line(0f, 0.14f, -0.16f, -0.03f); line(0f, 0.14f, 0.16f, -0.03f)
                line(-0.34f, 0.16f, -0.34f, 0.38f); line(-0.34f, 0.38f, 0.34f, 0.38f); line(0.34f, 0.38f, 0.34f, 0.16f) }
            InstrumentIconKind.Check -> { line(-0.36f, 0.02f, -0.10f, 0.28f); line(-0.10f, 0.28f, 0.38f, -0.26f) }
            InstrumentIconKind.Lock -> {
                drawRoundRect(tint, Offset(cx - 0.28f * w, cy - 0.02f * h), Size(0.56f * w, 0.42f * h),
                    CornerRadius(0.08f * w, 0.08f * h), style = stroke)
                arc(0.17f, 180f, 180f, 0f, -0.02f); dot(0.055f, 0f, 0.18f) }
            InstrumentIconKind.Grid -> { line(-0.13f, -0.38f, -0.13f, 0.38f); line(0.13f, -0.38f, 0.13f, 0.38f)
                line(-0.38f, -0.13f, 0.38f, -0.13f); line(-0.38f, 0.13f, 0.38f, 0.13f) }
            InstrumentIconKind.Diamond -> { line(0f, -0.40f, 0.32f, 0f); line(0.32f, 0f, 0f, 0.40f); line(0f, 0.40f, -0.32f, 0f); line(-0.32f, 0f, 0f, -0.40f) }
            InstrumentIconKind.Home -> { line(-0.38f, 0.02f, 0f, -0.34f); line(0f, -0.34f, 0.38f, 0.02f)
                line(-0.26f, -0.08f, -0.26f, 0.36f); line(-0.26f, 0.36f, 0.26f, 0.36f); line(0.26f, 0.36f, 0.26f, -0.08f) }
        }
    }
}

@Composable
private fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Gold.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(GoldLight, Gold, GoldDeep)))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, GlassLine, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Eyebrow(text: String, color: Color = Gold) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(trailing, color = Gold, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun TrustTag(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Mint.copy(alpha = 0.10f))
            .border(1.dp, Mint.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        color = Mint, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
    )
}

@Composable
private fun InfoRow(label: String, value: String, icon: InstrumentIconKind = InstrumentIconKind.Diamond, tint: Color = Gold) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(glassBrush(tint))
            .border(1.dp, glassBorder(tint), RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon, tint, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label.uppercase(), color = Muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, color = Paper, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/* --------------------------------------------------------------- overview */

@Composable
private fun OverviewScreen(
    selectedSource: String,
    onExploreSources: () -> Unit,
    onOpenSessions: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("OBSERVATORY / OVERVIEW")
            Text("Measure what is real.", fontSize = 27.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp)
            Text(
                "A local-first workspace for optical observations, Android-reported radio data, " +
                    "and compatible external receivers.",
                color = Muted, fontSize = 14.sp, lineHeight = 21.sp,
            )
        }

        StatusHero(onExploreSources)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("SPECTRUM", spectrumStateFor(selectedSource).first)
            SpectrumPanel(selectedSource)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("OBSERVATION CHANNELS", "4 MODULES")
            BoxWithConstraints {
                if (maxWidth >= 580.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SourceSummary("Optical", InstrumentIconKind.Lens, Gold, "Camera-visible light", "IN DEVELOPMENT", Modifier.weight(1f))
                            SourceSummary("Wi-Fi / BLE", InstrumentIconKind.Radio, Mint, "Android API observations", "IN DEVELOPMENT", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SourceSummary("External SDR", InstrumentIconKind.Antenna, Amber, "Receiver-dependent RF data", "HARDWARE REQUIRED", Modifier.weight(1f))
                            SourceSummary("Sessions", InstrumentIconKind.Layers, Stone, "Local records and CSV", "IN DEVELOPMENT", Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SourceSummary("Optical observation", InstrumentIconKind.Lens, Gold, "Camera-visible light and indicator timing", "IN DEVELOPMENT")
                        SourceSummary("Wi-Fi and Bluetooth", InstrumentIconKind.Radio, Mint, "Only observations exposed by Android", "IN DEVELOPMENT")
                        SourceSummary("USB SDR receiver", InstrumentIconKind.Antenna, Amber, "RF spectrum only with compatible hardware", "HARDWARE REQUIRED")
                        SourceSummary("Measurement sessions", InstrumentIconKind.Layers, Stone, "Local records and export", "IN DEVELOPMENT")
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Explore sources", onExploreSources, Modifier.weight(1f))
            GlassButton("Sessions", onOpenSessions, Modifier.weight(1f))
        }

        Text(
            "Accuracy rule: no invented readings. A phone camera detects light, not radio " +
                "frequency. Continuous RF analysis requires a compatible receiver.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
        )
    }
}

@Composable
private fun StatusHero(onExploreSources: () -> Unit) {
    GlassPanel(accent = Gold, shape = RoundedCornerShape(28.dp), padding = 0.dp) {
        Box {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Gold.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.88f, 0f),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.88f, 0f),
                )
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Eyebrow("SYSTEM READINESS")
                    GlassChip(Gold, "STANDBY")
                }
                Text("No measurement source connected", fontSize = 21.sp, fontWeight = FontWeight.Black, lineHeight = 27.sp)
                Text(
                    "The interface is ready to configure. Live acquisition remains unavailable until a " +
                        "supported observation source is implemented and connected.",
                    color = Muted, fontSize = 13.sp, lineHeight = 20.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrustTag("OFFLINE-FIRST")
                    TrustTag("SOURCE-VERIFIED")
                }
                GoldButton("Configure observation source", onExploreSources, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
/**
 * Empty-spectrum state for the currently selected module. This is interface
 * state only: it names *why* there is nothing to plot (not implemented vs.
 * hardware required vs. no source) and never fabricates samples, axes or
 * peaks. Returns (state label, waiting label, empty-plot explanation).
 */
private fun spectrumStateFor(selectedSource: String): Triple<String, String, String> =
    when (selectedSource) {
        "Optical observation" -> Triple(
            "NOT IMPLEMENTED",
            "Selected: Optical observation — not implemented in this build",
            "No optical samples have been acquired",
        )
        "Wi-Fi and Bluetooth" -> Triple(
            "NOT IMPLEMENTED",
            "Selected: Wi-Fi and Bluetooth — not implemented in this build",
            "No Wi-Fi or Bluetooth observations have been acquired",
        )
        "External SDR" -> Triple(
            "HARDWARE REQUIRED",
            "Selected: External SDR — a compatible receiver is required",
            "No receiver is connected, so no spectrum can be plotted",
        )
        else -> Triple(
            "NO SOURCE",
            "Waiting for verified input",
            "Connect a supported source to plot observations",
        )
    }

@Composable
private fun SpectrumPanel(selectedSource: String) {
    val spectrumState = spectrumStateFor(selectedSource)
    GlassPanel(accent = Mint, shape = RoundedCornerShape(24.dp), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Spectrum", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(spectrumState.second, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Text("RF / —", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f))))
                .border(1.dp, Mint.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                for (i in 0..4) {
                    val y = size.height * i / 4
                    drawLine(GlassLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                for (i in 0..6) {
                    val x = size.width * i / 6
                    drawLine(GlassLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
            }
            Column(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("NO DATA", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(spectrumState.third, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("FREQUENCY AXIS: UNAVAILABLE", color = Muted, fontSize = 10.5.sp, letterSpacing = 0.6.sp)
            Text("0 SAMPLES", color = Muted, fontSize = 10.5.sp, letterSpacing = 0.6.sp)
        }
        Spacer(Modifier.height(6.dp))
        SpectrumContextRow("SOURCE", selectedSource)
        SpectrumContextRow("OBSERVATION TIME", "—  ·  no samples acquired")
        SpectrumContextRow("FREQUENCY · LEVEL · UNITS", "—  ·  defined by the source, when samples exist")
    }
}

/** Label/value context under the spectrum. Dashes are explicit: nothing measured yet. */
@Composable
private fun SpectrumContextRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(GlassLine))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                label, color = Muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp, modifier = Modifier.weight(0.42f),
            )
            Text(value, color = Paper, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(0.58f))
        }
    }
}

@Composable
private fun SourceSummary(
    title: String,
    icon: InstrumentIconKind,
    tint: Color,
    detail: String,
    status: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(glassBrush(tint))
            .border(1.dp, glassBorder(tint), RoundedCornerShape(20.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            IconBadge(icon, tint, 36.dp)
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
        Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp, minLines = 2)
        Text(status, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

/* ---------------------------------------------------------------- sources */

@Composable
private fun SourcesScreen(selectedSource: String, onSelectSource: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("OBSERVATORY / INPUTS", Mint)
            Text("Observation sources", fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                "Choose a source to inspect its intended role and current implementation status.",
                color = Muted, fontSize = 13.sp, lineHeight = 20.sp,
            )
        }
        GlassPanel(accent = Mint, shape = RoundedCornerShape(20.dp), padding = 15.dp) {
            Text("How to read these states", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "NOT IMPLEMENTED — planned module; acquisition is not active in this build.\n" +
                    "HARDWARE REQUIRED — needs a compatible external receiver before any RF data can exist.\n" +
                    "SELECTED — inspection only; selecting a module never starts a measurement.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
        SourceDetailCard("Optical observation", InstrumentIconKind.Lens, Gold, "Camera-based visible-light sampling", "NOT IMPLEMENTED",
            "Intended for visible indicator states, brightness changes, and blink timing. Camera frame rate, " +
                "exposure, and lighting limit the timing accuracy. This source cannot detect RF directly.",
            selectedSource == "Optical observation") { onSelectSource("Optical observation") }
        SourceDetailCard("Wi-Fi and Bluetooth", InstrumentIconKind.Radio, Mint, "Android-reported observations", "NOT IMPLEMENTED",
            "Will use supported Android APIs and required runtime permissions. Results depend on OS version, " +
                "permissions, radio state, and what the device exposes; they are not a calibrated wideband spectrum.",
            selectedSource == "Wi-Fi and Bluetooth") { onSelectSource("Wi-Fi and Bluetooth") }
        SourceDetailCard("External SDR", InstrumentIconKind.Antenna, Amber, "USB host / supported receiver", "HARDWARE REQUIRED",
            "Continuous RF spectrum or IQ acquisition requires a compatible SDR, supported USB interface, " +
                "permission handling, and a validated receiver pipeline. No RF readings are available without it.",
            selectedSource == "External SDR") { onSelectSource("External SDR") }
        SourceDetailCard("Measurement sessions", InstrumentIconKind.Layers, Stone, "Local records and exports", "NOT IMPLEMENTED",
            "Planned session records will retain timestamps, source identity, units, configuration, and relevant " +
                "limitations. CSV export will only include values actually acquired or explicitly entered.",
            selectedSource == "Measurement sessions") { onSelectSource("Measurement sessions") }

        GlassPanel(accent = Gold, shape = RoundedCornerShape(22.dp), padding = 17.dp) {
            Eyebrow("SELECTED MODULE")
            Spacer(Modifier.height(7.dp))
            Text(selectedSource, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "This module is a planning and interface selection only. Acquisition is not active in this build.",
                color = Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun SourceDetailCard(
    title: String,
    icon: InstrumentIconKind,
    tint: Color,
    subtitle: String,
    status: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border by animateColorAsState(
        if (selected) tint.copy(alpha = 0.85f) else GlassLine, tween(240), label = "cardBorder",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (selected) 12.dp else 4.dp, RoundedCornerShape(22.dp), spotColor = tint.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(22.dp))
            .background(glassBrush(tint))
            .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(22.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(11.dp), Alignment.Top) {
            IconBadge(icon, tint, 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Text(status, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
        }
        Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Text(
            if (selected) "SELECTED MODULE" else "TAP TO INSPECT",
            color = if (selected) tint else Muted, fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp,
        )
    }
}

/* --------------------------------------------------------------- sessions */

@Composable
private fun SessionsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("OBSERVATORY / RECORDS", Stone)
            Text("Session workspace", fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("A clear home for repeatable, traceable experiments.", color = Muted, fontSize = 13.sp)
        }
        GlassPanel(accent = Stone, shape = RoundedCornerShape(26.dp), padding = 24.dp) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                IconBadge(InstrumentIconKind.Layers, Stone, 60.dp)
                Text("No sessions recorded", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "The engine layer defines traceable sessions, observations and analysis runs, " +
                        "but they are not connected to this interface and nothing is persisted on " +
                        "this device yet. No placeholder records are shown.",
                    color = Muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )
            }
        }
        SectionHeading("FINDINGS", "EVIDENCE ONLY")
        GlassPanel(accent = Gold, shape = RoundedCornerShape(22.dp), padding = 18.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconBadge(InstrumentIconKind.Crosshair, Gold, 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("No findings recorded", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Findings are created only from acquired samples with their source, time, " +
                            "units and configuration recorded. Anything unverified is labelled " +
                            "'candidate detected' — never a confirmed transmitter or protocol.",
                        color = Muted, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                    Text(
                        "Selecting a recorded finding will open its detail: frequency, level and " +
                            "units, evidence, limitations, and the analysis run that produced it.",
                        color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                }
            }
        }
        SectionHeading("SESSION RECORD FIELDS", "ENGINE-DEFINED")
        InfoRow("Timestamp", "Local time with explicit timezone handling", InstrumentIconKind.Clock, Mint)
        InfoRow("Observation source", "Device / adapter and source type", InstrumentIconKind.Antenna, Amber)
        InfoRow("Units and configuration", "Recorded alongside every measurement", InstrumentIconKind.Settings, Gold)
        InfoRow("Analysis provenance", "Algorithm, version, parameters and outcome for every analysis run", InstrumentIconKind.Check, Mint)
        InfoRow("Export", "CSV after storage and validation are implemented", InstrumentIconKind.Export, Stone)
    }
}

/* ------------------------------------------------------------------ about */

@Composable
private fun AboutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("PRODUCT / IDENTITY")
        GlassPanel(accent = Gold, shape = RoundedCornerShape(26.dp), padding = 22.dp) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The finished launcher tile (HD PNG) at showcase size — the
                // one place the full symbol + wordmark lockup is legible.
                Image(
                    painter = painterResource(R.drawable.brand_icon),
                    contentDescription = "ZR 4 — ZamCan RF Observatory",
                    modifier = Modifier
                        .size(124.dp)
                        .shadow(14.dp, RoundedCornerShape(28.dp), spotColor = Gold.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(28.dp)),
                )
                Text("ZamCan RF Observatory", fontSize = 23.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(
                    "ZR 4 is a native Android scientific observation workspace designed around traceable " +
                        "inputs, offline-first operation, and honest measurement limits.",
                    color = Muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )
            }
        }
        InfoRow("Product", "ZR 4", InstrumentIconKind.Diamond, Gold)
        InfoRow("Formal name", "ZamCan RF Observatory", InstrumentIconKind.Crosshair, Mint)
        InfoRow("Package", "com.zamcan.zr4", InstrumentIconKind.Grid, Gold)
        InfoRow("Build", "0.1.0 · Foundation interface — Observatory Glass", InstrumentIconKind.Diamond, Amber)
        InfoRow("Operating mode", "Local-first", InstrumentIconKind.Home, Mint)

        SectionHeading("ENGINEERING PRINCIPLES", "NON-NEGOTIABLE")
        InfoRow("Data integrity", "Never fabricate live readings", InstrumentIconKind.Check, Mint)
        InfoRow("Measurement provenance", "Identify the actual source and limitations", InstrumentIconKind.Crosshair, Gold)
        InfoRow("RF capability", "Receiver-dependent; no transmission", InstrumentIconKind.Antenna, Amber)
        InfoRow("Privacy", "Use authorized devices and respect local regulations", InstrumentIconKind.Lock, Stone)

        Text(
            "This version is an interface foundation. The acquisition engines, persistent session database, " +
                "plots based on actual samples, and hardware integrations require separate implementation and verification.",
            color = Muted, fontSize = 12.sp, lineHeight = 19.sp,
        )
    }
}
