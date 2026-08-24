package com.kharcha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.theme.KharchaSpacing

/**
 * The v2 design system's surface vocabulary, in one place.
 *
 * The v1 dashboard drew every section as a tonal slab of the same colour, which
 * is why nothing on it read first. Here there is exactly one card treatment —
 * [KharchaCard]: the plain `surface`, a hairline border, a 16dp radius — and
 * hierarchy comes from what is *inside* a card (a 42sp display amount vs a 10sp
 * mono label), never from giving one card a louder background than its
 * neighbours.
 */

/** The hairline that separates a card from the background, and rows within it. */
val hairline: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

/** A slightly stronger hairline, for borders that have to survive on a tinted fill. */
val hairlineStrong: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)

/**
 * The dimmest readable foreground: captions, "of 6,000", the ignored-message
 * reason tag. One step quieter than `onSurfaceVariant`.
 */
val dimForeground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

/**
 * The one card. [contentPadding] defaults to the design's 14dp; pass
 * `PaddingValues(0.dp)` — or use [KharchaFlushCard] — for a card whose rows run
 * edge to edge and draw their own separators.
 */
@Composable
fun KharchaCard(
    modifier: Modifier = Modifier,
    padding: Dp = KharchaSpacing.cardPadding,
    background: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = hairline,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
        content = content,
    )
}

/** A card whose children are full-bleed rows — a list inside a card. */
@Composable
fun KharchaFlushCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = KharchaCard(modifier = modifier, padding = 0.dp, content = content)

/**
 * The mono, uppercase, wide-tracked section label — "SPENT THIS MONTH". It is
 * the quietest text on screen by design: it names the number below it and then
 * gets out of the way.
 */
@Composable
fun CardLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = dimForeground,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/** Secondary detail — the right-hand caption on a card header, the line under a name. */
@Composable
fun Mini(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = dimForeground,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A card's header line: a [CardLabel] on the left, an optional [Mini] caption
 * pushed to the right. Every card that has a header uses this, which is what
 * keeps the label baselines identical from card to card.
 */
@Composable
fun CardHeader(
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardLabel(label)
        when {
            trailing != null -> trailing()
            caption != null -> Mini(caption)
        }
    }
}

/**
 * The tinted rounded tile a category icon sits in: the category's hue at 16%
 * for the fill, at full strength for the glyph. Used at 30dp in lists and rows.
 */
@Composable
fun CategoryTile(
    color: Color,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(size * 0.53f),
        )
    }
}

/** The 9dp legend square that ties a chart slice to its name. */
@Composable
fun Swatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(9.dp)
            .background(color, RoundedCornerShape(3.dp)),
    )
}
