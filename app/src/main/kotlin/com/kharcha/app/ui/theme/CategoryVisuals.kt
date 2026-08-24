package com.kharcha.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Category taxonomy: eight base hues, reused across fourteen categories.
 *
 * The eight hues are the v2 design system's category ramp — clay, gold, sage,
 * teal, indigo, plum, rust, stone. They are warm-biased and deliberately
 * desaturated relative to a stock Material palette, because a dashboard shows
 * six of them at once in a donut and a saturated ramp turns that into a
 * pinwheel.
 *
 * ACCESSIBILITY & CVD STRATEGY:
 * Eight hues cannot be fully distinguishable under dichromacy (the mathematical
 * ceiling is ~4-5, because the red-green opponent axis collapses). So colour is
 * never the sole encoder — every category is drawn as icon + label + colour, and
 * the icon is what actually carries the identity. The palette additionally
 * spreads the eight hues across the L* range so that when hue collapses,
 * lightness still separates them; [CategoryVisualsTest] holds that line.
 *
 * Do not add a ninth category. Reuse an existing hue instead.
 */
object CategoryVisuals {

    /**
     * The eight base hues. `DARK` is the value used on the dark scheme's
     * near-black surfaces; `LIGHT` is the same hue darkened to stay legible on
     * the light scheme's off-white ones.
     */
    object CategoryColors {
        // Clay — the debit red. Transfers, the money that leaves in bulk.
        const val CLAY_DARK = 0xFFD8583F.toInt()
        const val CLAY_LIGHT = 0xFFB0402B.toInt()

        // Gold — the accent hue. Food & dining.
        const val GOLD_DARK = 0xFFD4A03C.toInt()
        const val GOLD_LIGHT = 0xFF8A5206.toInt()

        // Sage — the credit green. Groceries and income.
        const val SAGE_DARK = 0xFF8FAE4F.toInt()
        const val SAGE_LIGHT = 0xFF4F6B25.toInt()

        // Teal — bills and utilities.
        const val TEAL_DARK = 0xFF4FA6A0.toInt()
        const val TEAL_LIGHT = 0xFF2F6F6A.toInt()

        // Indigo — education and travel.
        const val INDIGO_DARK = 0xFF6E7BC4.toInt()
        const val INDIGO_LIGHT = 0xFF454F91.toInt()

        // Plum — transport.
        const val PLUM_DARK = 0xFFA96BA6.toInt()
        const val PLUM_LIGHT = 0xFF743F72.toInt()

        // Rust — shopping.
        const val RUST_DARK = 0xFFC4703A.toInt()
        const val RUST_LIGHT = 0xFF8A4718.toInt()

        // Stone — the neutral. Other, and anything uncategorised.
        const val STONE_DARK = 0xFF8F7B68.toInt()
        const val STONE_LIGHT = 0xFF5E4E3F.toInt()

        // ---- Category assignments (categories reuse base hues) ----
        const val FOOD_DINING_DARK = GOLD_DARK
        const val FOOD_DINING_LIGHT = GOLD_LIGHT

        const val GROCERIES_DARK = SAGE_DARK
        const val GROCERIES_LIGHT = SAGE_LIGHT

        const val SHOPPING_DARK = RUST_DARK
        const val SHOPPING_LIGHT = RUST_LIGHT

        const val TRANSPORT_DARK = PLUM_DARK
        const val TRANSPORT_LIGHT = PLUM_LIGHT

        const val BILLS_UTILITIES_DARK = TEAL_DARK
        const val BILLS_UTILITIES_LIGHT = TEAL_LIGHT

        const val TRANSFER_DARK = CLAY_DARK
        const val TRANSFER_LIGHT = CLAY_LIGHT

        const val EDUCATION_DARK = INDIGO_DARK
        const val EDUCATION_LIGHT = INDIGO_LIGHT

        const val UNCATEGORIZED_DARK = STONE_DARK
        const val UNCATEGORIZED_LIGHT = STONE_LIGHT

        // Reused hues.
        const val INCOME_DARK = SAGE_DARK
        const val INCOME_LIGHT = SAGE_LIGHT

        const val HEALTH_DARK = TEAL_DARK
        const val HEALTH_LIGHT = TEAL_LIGHT

        const val ENTERTAINMENT_DARK = PLUM_DARK
        const val ENTERTAINMENT_LIGHT = PLUM_LIGHT

        const val TRAVEL_DARK = INDIGO_DARK
        const val TRAVEL_LIGHT = INDIGO_LIGHT

        const val RENT_DARK = CLAY_DARK
        const val RENT_LIGHT = CLAY_LIGHT

        const val FEES_DARK = STONE_DARK
        const val FEES_LIGHT = STONE_LIGHT
    }

    /** Category definition pairing icon, dark-theme color, and light-theme color. */
    data class Category(
        val icon: ImageVector,
        val darkColor: Int,
        val lightColor: Int,
    )

    /**
     * Category name to visuals. The first eight entries are the eight base hues,
     * in ramp order — [CategoryVisualsTest] takes exactly those eight when it
     * checks mutual distinctness, so keep the base-hue entries first.
     *
     * Icons are the Outlined set, not Filled: the design draws category glyphs
     * as ~1.9dp line art inside a tinted tile, and a filled glyph at 16dp turns
     * that tile into a solid blob.
     */
    val CATEGORIES: Map<String, Category> = mapOf(
        // ---- the eight base hues ----
        "Transfers" to Category(
            icon = Icons.Outlined.SwapHoriz,
            darkColor = CategoryColors.TRANSFER_DARK,
            lightColor = CategoryColors.TRANSFER_LIGHT,
        ),
        "Food & Dining" to Category(
            icon = Icons.Outlined.RestaurantMenu,
            darkColor = CategoryColors.FOOD_DINING_DARK,
            lightColor = CategoryColors.FOOD_DINING_LIGHT,
        ),
        "Groceries" to Category(
            icon = Icons.Outlined.ShoppingCart,
            darkColor = CategoryColors.GROCERIES_DARK,
            lightColor = CategoryColors.GROCERIES_LIGHT,
        ),
        "Bills & Utilities" to Category(
            icon = Icons.Outlined.Bolt,
            darkColor = CategoryColors.BILLS_UTILITIES_DARK,
            lightColor = CategoryColors.BILLS_UTILITIES_LIGHT,
        ),
        "Education" to Category(
            icon = Icons.Outlined.School,
            darkColor = CategoryColors.EDUCATION_DARK,
            lightColor = CategoryColors.EDUCATION_LIGHT,
        ),
        "Transport" to Category(
            icon = Icons.Outlined.LocalTaxi,
            darkColor = CategoryColors.TRANSPORT_DARK,
            lightColor = CategoryColors.TRANSPORT_LIGHT,
        ),
        "Shopping" to Category(
            icon = Icons.Outlined.ShoppingBag,
            darkColor = CategoryColors.SHOPPING_DARK,
            lightColor = CategoryColors.SHOPPING_LIGHT,
        ),
        "Other" to Category(
            icon = Icons.Outlined.CreditCard,
            darkColor = CategoryColors.UNCATEGORIZED_DARK,
            lightColor = CategoryColors.UNCATEGORIZED_LIGHT,
        ),
        // ---- reused hues ----
        "Income" to Category(
            icon = Icons.Outlined.ArrowUpward,
            darkColor = CategoryColors.INCOME_DARK,
            lightColor = CategoryColors.INCOME_LIGHT,
        ),
        "Health" to Category(
            icon = Icons.Outlined.FavoriteBorder,
            darkColor = CategoryColors.HEALTH_DARK,
            lightColor = CategoryColors.HEALTH_LIGHT,
        ),
        "Entertainment" to Category(
            icon = Icons.Outlined.Movie,
            darkColor = CategoryColors.ENTERTAINMENT_DARK,
            lightColor = CategoryColors.ENTERTAINMENT_LIGHT,
        ),
        "Travel" to Category(
            icon = Icons.Outlined.Flight,
            darkColor = CategoryColors.TRAVEL_DARK,
            lightColor = CategoryColors.TRAVEL_LIGHT,
        ),
        "Rent" to Category(
            icon = Icons.Outlined.Home,
            darkColor = CategoryColors.RENT_DARK,
            lightColor = CategoryColors.RENT_LIGHT,
        ),
        "Fees" to Category(
            icon = Icons.Outlined.Percent,
            darkColor = CategoryColors.FEES_DARK,
            lightColor = CategoryColors.FEES_LIGHT,
        ),
    )

    /** The eight base hues in ramp order, for charts that need N distinct series colours. */
    val baseHueOrder: List<String> = CATEGORIES.keys.take(8).toList()

    fun getCategory(name: String): Category? = CATEGORIES[name]

    /** The colour for a category in the given scheme, or null if the name is unknown. */
    fun getColor(name: String, darkTheme: Boolean): Int? {
        val category = CATEGORIES[name] ?: return null
        return if (darkTheme) category.darkColor else category.lightColor
    }

    fun getIcon(name: String): ImageVector? = CATEGORIES[name]?.icon

    /**
     * The colour to draw [name] in, falling back to the neutral stone hue rather
     * than to null — a chart slice or a category tile always needs *some*
     * colour, and an unknown category is conceptually "Other".
     */
    fun colorOrFallback(name: String, darkTheme: Boolean): Color {
        val argb = getColor(name, darkTheme)
            ?: if (darkTheme) CategoryColors.STONE_DARK else CategoryColors.STONE_LIGHT
        return Color(argb)
    }

    /** The icon to draw [name] with, falling back to the neutral card glyph. */
    fun iconOrFallback(name: String): ImageVector = getIcon(name) ?: Icons.Outlined.CreditCard
}
