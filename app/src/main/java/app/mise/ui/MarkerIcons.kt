package app.mise.ui

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import app.mise.R
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.ceil

data class MarkerColors(val badge: Int, val onBadge: Int, val selected: Int, val onSelected: Int, val text: Int, val halo: Int)

/**
 * Draws Google-Maps-style place markers: a round badge with a fork-and-knife glyph, and a separate
 * name label (dark text with a light halo) that sits to the right of the badge. Bitmaps are cached.
 */
class MarkerIcons(private val context: Context, private val colors: MarkerColors) {
    private val d = context.resources.displayMetrics.density
    private val cache = HashMap<String, BitmapDescriptor>()

    fun badgeRadius(selected: Boolean): Float = (if (selected) 17f else 13f) * d

    private val gap = 4f * d
    private val maxTextWidth = 150f * d

    private fun textPaint(selected: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (selected) 14f else 12f) * d
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun ellipsized(text: String, paint: TextPaint): String =
        TextUtils.ellipsize(text, paint, maxTextWidth, TextUtils.TruncateAt.END).toString()

    /** Pixel size of the label bitmap; used to avoid overlapping labels. Left edge sits on the badge center. */
    fun labelSize(text: String, selected: Boolean): Pair<Float, Float> {
        val paint = textPaint(selected)
        val halo = 3f * d
        val width = badgeRadius(selected) + gap + paint.measureText(ellipsized(text, paint)) + halo * 2
        val height = paint.fontMetrics.let { it.descent - it.ascent } + halo * 2
        return width to height
    }

    fun badge(selected: Boolean): BitmapDescriptor = cache.getOrPut(if (selected) "badge-sel" else "badge") {
        val r = badgeRadius(selected)
        val pad = 3f * d
        val size = ceil((r + pad) * 2).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(70, 0, 0, 0)
        c.drawCircle(cx, cx + 1.5f * d, r, paint)
        paint.color = Color.WHITE
        c.drawCircle(cx, cx, r, paint)
        paint.color = if (selected) colors.selected else colors.badge
        c.drawCircle(cx, cx, r - 2f * d, paint)
        val glyph = DrawableCompat.wrap(ContextCompat.getDrawable(context, R.drawable.ic_marker_restaurant)!!.mutate())
        DrawableCompat.setTint(glyph, if (selected) colors.onSelected else colors.onBadge)
        val g = (r * 1.1f).toInt()
        glyph.setBounds((cx - g / 2f).toInt(), (cx - g / 2f).toInt(), (cx + g / 2f).toInt(), (cx + g / 2f).toInt())
        glyph.draw(c)
        BitmapDescriptorFactory.fromBitmap(bmp)
    }

    fun label(text: String, selected: Boolean): BitmapDescriptor = cache.getOrPut("label|$selected|$text") {
        val paint = textPaint(selected)
        val shown = ellipsized(text, paint)
        val (w, h) = labelSize(text, selected)
        val bmp = Bitmap.createBitmap(ceil(w).toInt(), ceil(h).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val x = badgeRadius(selected) + gap
        val y = h / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * d
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = colors.halo
        c.drawText(shown, x, y, paint)
        paint.style = Paint.Style.FILL
        paint.color = colors.text
        c.drawText(shown, x, y, paint)
        BitmapDescriptorFactory.fromBitmap(bmp)
    }
}
