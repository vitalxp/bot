package app.taxicom.commandos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.taxicom.commandos.ui.theme.Amber
import app.taxicom.commandos.ui.theme.Maybe
import app.taxicom.commandos.ui.theme.Skip
import app.taxicom.commandos.ui.theme.Take
import app.taxicom.core.model.Verdict
import kotlin.math.max

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Text(
        text = "TAXI COMMANDOS",
        style = MaterialTheme.typography.labelLarge,
        color = Amber,
        letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = modifier,
    )
}

@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF0E1116), Color(0xFF12161E), Color(0xFF0A0C10)),
            ),
        ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Amber.copy(alpha = pulse * 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.08f),
                    radius = size.minDimension * 0.7f,
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(size.width * 0.85f, size.height * 0.08f),
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1200)),
        shape = RoundedCornerShape(14.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(14.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Kpi(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.padding(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        when (verdict) {
            Verdict.TAKE -> Take
            Verdict.MAYBE -> Maybe
            Verdict.SKIP -> Skip
        },
        label = "verdict",
    )
    val label = when (verdict) {
        Verdict.TAKE -> androidx.compose.ui.res.stringResource(app.taxicom.commandos.R.string.verdict_take)
        Verdict.MAYBE -> androidx.compose.ui.res.stringResource(app.taxicom.commandos.R.string.verdict_maybe)
        Verdict.SKIP -> androidx.compose.ui.res.stringResource(app.taxicom.commandos.R.string.verdict_skip)
    }
    Box(
        modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.5.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Black)
    }
}

@Composable
fun HourBars(values: List<Pair<Int, Double>>, modifier: Modifier = Modifier, barColor: Color = Amber) {
    val peak = max(values.maxOfOrNull { it.second } ?: 1.0, 1.0)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, (_, v) ->
            val h = (v / peak * size.height * 0.85f).toFloat().coerceAtLeast(2f)
            val x = i * (w + gap)
            drawRoundRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(x, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}

@Composable
fun ScoreRing(relative: Double, modifier: Modifier = Modifier) {
    val color = when {
        relative >= 0.15 -> Take
        relative >= -0.1 -> Maybe
        else -> Skip
    }
    val progress = ((relative + 0.5).coerceIn(0.0, 1.5) / 1.5).toFloat()
    Box(modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color.White.copy(alpha = 0.08f), -90f, 360f, false, style = stroke)
            drawArc(color, -90f, 360f * progress, false, style = stroke)
        }
        Text(
            text = if (relative >= 0) "+${(relative * 100).toInt()}%" else "${(relative * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun PlatformDot(name: String, modifier: Modifier = Modifier) {
    val color = when (name) {
        "BOLT" -> app.taxicom.commandos.ui.theme.BoltGreen
        "UBER" -> app.taxicom.commandos.ui.theme.UberWhite
        else -> app.taxicom.commandos.ui.theme.FreeNowPink
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(name.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
        content = { content() },
    )
}
