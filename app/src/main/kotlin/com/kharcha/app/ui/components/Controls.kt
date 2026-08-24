package com.kharcha.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.theme.KharchaPillShape
import com.kharcha.app.ui.theme.KharchaSemantics
import com.kharcha.app.ui.theme.LocalKharchaThemeController
import com.kharcha.app.ui.theme.KharchaSpacing
import com.kharcha.app.ui.theme.standardAnimation

/**
 * Interactive parts of the v2 system: the app bar, the pill controls, the
 * progress track. Everything here is pill-shaped or hairline-bordered, and
 * nothing here carries a drop shadow — depth in this app comes from tone.
 */

/**
 * The screen app bar: a Calistoga title, then icon actions. Sized to the
 * design's 8/16/12 padding rather than Material's default `TopAppBar`, which is
 * 64dp tall and would push the hero card off the first screenful.
 */
@Composable
fun KharchaAppBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = KharchaSpacing.lg, end = KharchaSpacing.sm, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KharchaSpacing.xs),
    ) {
        leading?.invoke(this)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/**
 * A 40dp circular icon action. [active] gives it the accent-tinted "on" state
 * the design uses for the control that is currently doing something — sync
 * running, add sheet open.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val background by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = standardAnimation(),
        label = "iconActionBackground",
    )
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.outline
        active -> KharchaSemantics.accent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(KharchaPillShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** The three button weights the design uses, in descending loudness. */
enum class KharchaButtonStyle { Filled, Tonal, Text }

@Composable
fun KharchaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KharchaButtonStyle = KharchaButtonStyle.Tonal,
    enabled: Boolean = true,
) {
    val background = when (style) {
        KharchaButtonStyle.Filled -> KharchaSemantics.accent
        KharchaButtonStyle.Tonal -> MaterialTheme.colorScheme.surfaceVariant
        KharchaButtonStyle.Text -> Color.Transparent
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.outline
        style == KharchaButtonStyle.Filled -> MaterialTheme.colorScheme.background
        style == KharchaButtonStyle.Tonal -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val horizontal = if (style == KharchaButtonStyle.Text) 10.dp else KharchaSpacing.lg
    Box(
        modifier = modifier
            .clip(KharchaPillShape)
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = horizontal, vertical = KharchaSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            maxLines = 1,
        )
    }
}

/**
 * A filter chip. When [onRemove] is non-null the chip renders its selected form
 * with a trailing ✕ — the design's "August ✕" — because a filter you can see is
 * a filter you can take off.
 */
@Composable
fun KharchaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    removable: Boolean = false,
) {
    val shape = KharchaPillShape
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val foreground = if (selected) KharchaSemantics.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .then(
                if (selected) Modifier else Modifier.border(1.dp, hairlineStrong, shape),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = foreground,
            maxLines = 1,
        )
        if (selected && removable) {
            Text(
                text = "✕",
                style = MaterialTheme.typography.bodySmall,
                color = foreground.copy(alpha = 0.65f),
            )
        }
    }
}

/**
 * The two-up segmented control — "Needs review · 12" / "Ignored · 796". A
 * segmented control rather than a tab row because these are two filters over
 * one list, not two destinations.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(KharchaPillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(KharchaPillShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        KharchaPillShape,
                    )
                    .clickable(role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A spend/budget progress bar. [fraction] is clamped, so a category that is
 * 130% of budget fills the track rather than overflowing the card.
 *
 * [paceFraction] draws the design's pace marker: the vertical tick showing where
 * you *would* be if you spent evenly across the month. A bar sitting well past
 * its own tick is the whole point of the budgets screen — it says "ahead of
 * pace" without the user reading a single number.
 */
@Composable
fun ProgressTrack(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 7.dp,
    paceFraction: Float? = null,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(KharchaPillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(height)
                    .clip(KharchaPillShape)
                    .background(color),
            )
        }
        if (paceFraction != null) {
            // Drawn as a fraction of the full width via a transparent spacer, so it
            // stays aligned with the fill at any container width.
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth(paceFraction.coerceIn(0f, 1f)))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(height + 6.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

/**
 * The light/dark switch, as a single app-bar glyph.
 *
 * It shows the mode you would get by tapping — a sun while you are in dark mode
 * — rather than the mode you are in. A control that pictures the current state
 * gives you nothing you cannot already see by looking at the screen.
 */
@Composable
fun ThemeToggleAction(modifier: Modifier = Modifier) {
    val controller = LocalKharchaThemeController.current
    IconAction(
        icon = if (controller.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
        contentDescription = if (controller.isDark) "Switch to light theme" else "Switch to dark theme",
        onClick = controller::toggle,
        modifier = modifier,
    )
}
