package com.plurali.app.sys

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import com.plurali.app.FrontPickerActivity
import com.plurali.app.R
import com.plurali.app.data.Store
import com.plurali.app.data.Subject
import com.plurali.app.data.currentFronts
import com.plurali.app.data.subject
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Widgets só com a foto de quem está no front. Tocar abre o menu de troca. */
abstract class PhotoWidgetBase(private val px: Int) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        PluraliApplication.ensure(ctx)
        PhotoWidgets.render(ctx, mgr, ids, px)
    }
}

class FrontPhotoWidget : PhotoWidgetBase(256)
class FrontPhotoWidgetLarge : PhotoWidgetBase(512)

object PhotoWidgets {
    fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        mgr.getAppWidgetIds(ComponentName(ctx, FrontPhotoWidget::class.java))
            .takeIf { it.isNotEmpty() }?.let { render(ctx, mgr, it, 256) }
        mgr.getAppWidgetIds(ComponentName(ctx, FrontPhotoWidgetLarge::class.java))
            .takeIf { it.isNotEmpty() }?.let { render(ctx, mgr, it, 512) }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, px: Int) {
        val d = Store.data.value
        val subjects = d.currentFronts().mapNotNull { d.subject(it.subjectId) }.distinctBy { it.id }
        val v = RemoteViews(ctx.packageName, R.layout.widget_photo)
        v.setImageViewBitmap(R.id.w_img, compose(subjects, px))
        v.setContentDescription(
            R.id.w_img,
            if (subjects.isEmpty()) "Ninguém no front. Toque para trocar" else subjects.joinToString(", ") { it.name } + " no front. Toque para trocar",
        )
        val i = Intent(ctx, FrontPickerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        v.setOnClickPendingIntent(
            R.id.w_root,
            PendingIntent.getActivity(ctx, 96, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        runCatching { mgr.updateAppWidget(ids, v) }
    }

    /** Monta uma imagem quadrada com cantos arredondados: 1 pessoa = foto inteira, 2 = metades, 3-4+ = grade. */
    private fun compose(list: List<Subject>, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = px * 0.2f
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, Path.Direction.CW) }
        c.clipPath(clip)
        val gap = max(2, px / 64)
        val h = px / 2
        val cells: List<Rect> = when (min(list.size, 4)) {
            0, 1 -> listOf(Rect(0, 0, px, px))
            2 -> listOf(Rect(0, 0, h - gap / 2, px), Rect(h + gap / 2, 0, px, px))
            3 -> listOf(Rect(0, 0, h - gap / 2, px), Rect(h + gap / 2, 0, px, h - gap / 2), Rect(h + gap / 2, h + gap / 2, px, px))
            else -> listOf(
                Rect(0, 0, h - gap / 2, h - gap / 2), Rect(h + gap / 2, 0, px, h - gap / 2),
                Rect(0, h + gap / 2, h - gap / 2, px), Rect(h + gap / 2, h + gap / 2, px, px),
            )
        }
        if (list.isEmpty()) {
            drawLetter(c, cells[0], 0xFF384048, "?")
            return out
        }
        cells.forEachIndexed { i, cell ->
            if (i == 3 && list.size > 4) {
                drawLetter(c, cell, 0xFF384048, "+${list.size - 3}")
            } else {
                val s = list[i]
                val bmp = s.avatar?.let { load(it, max(cell.width(), cell.height())) }
                if (bmp != null) drawCrop(c, bmp, cell) else drawLetter(c, cell, s.color, s.name.trim().take(1).uppercase().ifBlank { "?" })
            }
        }
        return out
    }

    private fun load(path: String, target: Int): Bitmap? = runCatching {
        val f = File(Store.filesDir, path)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var ss = 1
        while (min(o.outWidth, o.outHeight) / (ss * 2) >= target) ss *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss })
    }.getOrNull()

    /** Desenha a foto preenchendo a célula (corta o excesso, centralizado). */
    private fun drawCrop(c: Canvas, b: Bitmap, cell: Rect) {
        val scale = max(cell.width().toFloat() / b.width, cell.height().toFloat() / b.height)
        val sw = (cell.width() / scale).toInt()
        val sh = (cell.height() / scale).toInt()
        val sx = (b.width - sw) / 2
        val sy = (b.height - sh) / 2
        c.drawBitmap(b, Rect(sx, sy, sx + sw, sy + sh), cell, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun drawLetter(c: Canvas, cell: Rect, color: Long, text: String) {
        c.drawRect(cell, Paint().apply { this.color = color.toInt() })
        val lum = (0.299 * Color.red(color.toInt()) + 0.587 * Color.green(color.toInt()) + 0.114 * Color.blue(color.toInt())) / 255
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (lum > 0.5) Color.BLACK else Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = min(cell.width(), cell.height()) * (if (text.length > 2) 0.32f else 0.45f)
        }
        val y = cell.exactCenterY() - (p.descent() + p.ascent()) / 2
        c.drawText(text, cell.exactCenterX(), y, p)
    }
}
