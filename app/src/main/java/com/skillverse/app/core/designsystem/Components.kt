package com.skillverse.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skillverse.app.domain.model.Difficulty
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Standard surface card: rounded, hairline border, optional click and gradient. */
@Composable
fun SvCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    brush: Brush? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val base = modifier
        .clip(shape)
        .then(if (brush != null) Modifier.background(brush) else Modifier.background(MaterialTheme.colorScheme.surface))
        .border(BorderStroke(1.dp, SvTheme.colors.cardBorder), shape)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
        .padding(contentPadding)
    Column(modifier = base, content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Animated gradient progress bar. Respects reduced motion. */
@Composable
fun XpBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    brush: Brush = SvTheme.colors.xpBrush,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    description: String? = null,
) {
    val target = fraction.coerceIn(0f, 1f)
    val reduced = SvTheme.reducedMotion
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) tween(0) else tween(700, easing = FastOutSlowInEasing),
        label = "xpBar",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                if (description != null) contentDescription = description
            },
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(brush),
            )
        }
    }
}

/** Circular progress ring with centred content. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit = {},
) {
    val reduced = SvTheme.reducedMotion
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reduced) tween(0) else tween(800, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    supporting: String? = null,
) {
    SvCard(modifier = modifier.semantics(mergeDescendants = true) {}, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
fun difficultyColor(d: Difficulty): Color = when (d) {
    Difficulty.EASY -> SvTheme.colors.success
    Difficulty.MEDIUM -> SvTheme.colors.warning
    Difficulty.HARD -> SvTheme.colors.danger
}

@Composable
fun DifficultyPill(d: Difficulty, modifier: Modifier = Modifier) = Pill(d.label, difficultyColor(d), modifier)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = SvIcons.Target,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.xl, horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.md))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading") {
    Box(modifier.fillMaxSize().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorState(title: String, message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        EmptyState(title = title, message = message, icon = SvIcons.Bug, actionLabel = onRetry?.let { "Try again" }, onAction = onRetry)
    }
}

/** Hexagonal emblem used for achievements, skill nodes and avatars. */
@Composable
fun HexEmblem(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    enabled: Boolean = true,
    glyphColor: Color = Color.White,
) {
    val locked = SvTheme.colors.locked
    val fill = if (enabled) Brush.linearGradient(colors) else Brush.linearGradient(listOf(locked.copy(alpha = 0.35f), locked.copy(alpha = 0.2f)))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(this.size.width, this.size.height) / 2f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val path = Path().apply {
                for (i in 0 until 6) {
                    val angle = Math.toRadians((60.0 * i) - 90.0)
                    val x = cx + r * cos(angle).toFloat()
                    val y = cy + r * sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, fill)
        }
        Text(
            glyph,
            color = if (enabled) glyphColor else locked,
            style = if (glyph.length <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Avatars are original geometric emblems; no images or personal data are needed. */
object Avatars {
    data class Avatar(val id: Int, val name: String, val glyph: String, val colors: List<Color>)

    val all = listOf(
        Avatar(0, "Nova", "N", listOf(Color(0xFF7C5CFF), Color(0xFF3DD6E8))),
        Avatar(1, "Circuit", "C", listOf(Color(0xFF3DD6E8), Color(0xFF3DDC97))),
        Avatar(2, "Ember", "E", listOf(Color(0xFFFF8A5C), Color(0xFFFFB547))),
        Avatar(3, "Byte", "B", listOf(Color(0xFF5B3DF5), Color(0xFFFF6B81))),
        Avatar(4, "Kernel", "K", listOf(Color(0xFF14895A), Color(0xFF3DD6E8))),
        Avatar(5, "Pulse", "P", listOf(Color(0xFFFF6B81), Color(0xFF7C5CFF))),
        Avatar(6, "Vector", "V", listOf(Color(0xFF2B6CFF), Color(0xFF7C5CFF))),
        Avatar(7, "Quartz", "Q", listOf(Color(0xFFB5A3FF), Color(0xFF3DDC97))),
    )

    fun byId(id: Int): Avatar = all.getOrElse(id) { all.first() }
}

@Composable
fun AvatarBadge(avatarId: Int, modifier: Modifier = Modifier, size: Dp = 56.dp, level: Int? = null) {
    val avatar = Avatars.byId(avatarId)
    Box(modifier.size(size + if (level != null) 6.dp else 0.dp).semantics { contentDescription = "Avatar ${avatar.name}" + (level?.let { ", level $it" } ?: "") }) {
        HexEmblem(avatar.glyph, size = size, colors = avatar.colors, modifier = Modifier.align(Alignment.TopStart))
        if (level != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Text(
                    "$level",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** Monospace code block with horizontal scroll and line numbers. */
@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier, showLineNumbers: Boolean = true) {
    val lines = code.lines()
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SvTheme.colors.codeBackground)
            .semantics { contentDescription = "Code: $code" },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(Spacing.md)) {
            if (showLineNumbers && lines.size > 1) {
                Column(horizontalAlignment = Alignment.End) {
                    lines.indices.forEach {
                        Text("${it + 1}", style = CodeTextStyle, color = SvTheme.colors.codeText.copy(alpha = 0.4f))
                    }
                }
                Spacer(Modifier.width(Spacing.md))
            }
            Column {
                lines.forEach { Text(it.ifEmpty { " " }, style = CodeTextStyle, color = SvTheme.colors.codeText, softWrap = false) }
            }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** One-shot celebratory scale-in used for level-ups and achievements; static when motion is reduced. */
@Composable
fun rememberPopInScale(key: Any?): Float {
    val reduced = SvTheme.reducedMotion
    val scale = remember(key) { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(key, reduced) {
        if (!reduced) scale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    return scale.value
}

@Composable
fun LabeledDivider(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/** Lightweight top bar for pushed screens (no nested Scaffold, so insets are applied once). */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing()
    }
}
