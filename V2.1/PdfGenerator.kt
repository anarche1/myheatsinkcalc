@file:OptIn(ExperimentalMaterial3Api::class) // DÜZELTİLDİ: Etiket her şeyin üstüne, 1. satıra alındı!

package com.example.myheatsinkcalc

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myheatsinkcalc.ui.theme.MyHeatSinkCalcTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.floor
import kotlin.math.min
import android.graphics.Color as AndroidColor
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.myheatsinkcalc.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

// === PdfGenerator.kt: PDF rapor olusturucu ===

// ==========================================
// PDF RAPOR OLUŞTURUCU
// ==========================================
object PdfGenerator {
    fun exportReport(context: Context, uiState: HeatsinkUiState, result: SolverResult) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val primaryColor = AndroidColor.parseColor("#95D5B2")
        val leftMargin = 40f
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("tr"))
        val currentDate = sdf.format(java.util.Date())
        val s = uiState
        val env = s.envState

        val lDiv = when(s.lengthUnit) { "cm" -> 10.0; "m" -> 1000.0; "inch" -> 25.4; "ft" -> 304.8; else -> 1.0 }
        val tbOpt = result.usedTb / lDiv; val tfOpt = result.usedTf / lDiv; val sOpt = result.usedS / lDiv

        val isImperialVol = s.lengthUnit == "inch" || s.lengthUnit == "ft"
        val vValue = if(isImperialVol) result.totalVolumeCm3 * 0.0610237 else result.totalVolumeCm3
        val vUnit = if(isImperialVol) "in³" else "cm³"

        val wValue = when(s.weightUnit) { "kg" -> result.totalWeightGram / 1000.0; "lbs" -> result.totalWeightGram * 0.00220462; "oz" -> result.totalWeightGram * 0.035274; else -> result.totalWeightGram }
        val pValue = when(s.pressureUnit) { "in-H2O" -> result.pressureDropPa * 0.00401865; "mm-H2O" -> result.pressureDropPa * 0.101972; else -> result.pressureDropPa }
        val resValue = if(s.resistanceUnit == "°F/W") result.rTotalSystem * 1.8 else result.rTotalSystem

        // YENİ: P-Q grafiğinin 3. sayfaya eklenip eklenmeyeceğini önceden belirliyoruz
        // ki footer'daki "Sayfa X / Y" toplam sayfa sayısı en baştan doğru olsun.
        val rawCurve = s.envState.fanCurvePoints.mapNotNull {
            val q = it.first.replace(",", ".").toDoubleOrNull()
            val p = it.second.replace(",", ".").toDoubleOrNull()
            if (q != null && p != null) Pair(q, p) else null
        }.sortedBy { it.first }
        val hasFanCurveGraph = env.selectedFanMethod.contains("Eğrisi") && rawCurve.size > 1
        val hasSensitivity = result.sensitivityItems.isNotEmpty() // YENİ: Duyarlılık Analizi sayfası
        val hasHeatmap = result.heatmapCells.isNotEmpty() // YENİ: Optimizasyon Isı Haritası sayfası
        val hasComparison = s.comparisonDesigns.isNotEmpty() // YENİ: Tasarım Karşılaştırma sayfası
        val totalPages = 3 + (if (hasFanCurveGraph) 1 else 0) + (if (hasSensitivity) 1 else 0) + (if (hasHeatmap) 1 else 0) + (if (hasComparison) 1 else 0)

        fun drawHeaderAndFooter(canvas: android.graphics.Canvas, pageNumber: Int) {
            paint.color = primaryColor
            canvas.drawRect(0f, 0f, 595f, 90f, paint)
            paint.color = AndroidColor.BLACK
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("My Heat Sink Calc", leftMargin, 40f, paint)
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Termal Analiz Raporu | Dovahkiin V2.1", leftMargin, 65f, paint)
            canvas.drawText("Tarih: $currentDate | Proje: ${s.projectName}", leftMargin, 80f, paint)
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            paint.color = AndroidColor.DKGRAY
            canvas.drawText("Sayfa $pageNumber / $totalPages", leftMargin, 810f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Dovahkiin Termal Motoru", 595f - leftMargin, 810f, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.color = AndroidColor.BLACK
        }

        val page1Info = PdfDocument.PageInfo.Builder(595, 842, 1).create(); val page1 = pdfDocument.startPage(page1Info); val canvas1 = page1.canvas
        drawHeaderAndFooter(canvas1, 1)

        var yPos1 = 130f
        paint.color = primaryColor; canvas1.drawRect(leftMargin, yPos1 - 15f, 555f, yPos1 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas1.drawText("1. Soğutucu Geometrisi ve Malzeme", leftMargin + 5f, yPos1, paint)
        yPos1 += 25f; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (s.isOptimizationEnabled) { paint.color = AndroidColor.parseColor("#E63946"); paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas1.drawText("✨ Optimizasyon Aktif! Değerler sistem tarafından seçilmiştir.", leftMargin, yPos1, paint); yPos1 += 15f; paint.color = AndroidColor.BLACK; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        canvas1.drawText("Malzeme: ${s.selectedMaterialName}", leftMargin, yPos1, paint); canvas1.drawText("İletkenlik (k): ${s.selectedConductivity} ${s.conductivityUnit}", 300f, yPos1, paint)
        yPos1 += 15f; canvas1.drawText("Dış Ölçüler: ${s.width} x ${s.length} ${s.lengthUnit}", leftMargin, yPos1, paint); canvas1.drawText("Taban (tb): ${String.format(java.util.Locale.US, "%.2f", tbOpt)} ${s.lengthUnit}", 300f, yPos1, paint)
        yPos1 += 15f; canvas1.drawText("Kanat Yük.(hf): ${s.finHeight} ${s.lengthUnit}", leftMargin, yPos1, paint); canvas1.drawText("Kanat Kal.(tf): ${String.format(java.util.Locale.US, "%.2f", tfOpt)} ${s.lengthUnit}", 300f, yPos1, paint)
        yPos1 += 15f; canvas1.drawText("Kanat Aralığı (S): ${String.format(java.util.Locale.US, "%.2f", sOpt)} ${s.lengthUnit}", leftMargin, yPos1, paint); yPos1 += 30f

        // DEĞİŞTİRİLDİ: Uygulamadaki "Gerçek Zamanlı İmalat Kesit Görünümü" (PageOneScreen) ile birebir aynı mantık
        paint.color = AndroidColor.BLACK; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas1.drawText("Gerçek Zamanlı İmalat Kesit Görünümü", leftMargin, yPos1, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos1 += 14f

        val crossW = 555f - leftMargin; val crossH = 180f
        val crossTop = yPos1

        val hsW = 340f; val dimReserve = 50f // görsel + ok/etiket sütunu tek blok kabul edilip ortalanıyor
        val crossOX = leftMargin + (crossW - (hsW + dimReserve)) / 2f
        val padTopY = 30f; val padBottomY = 70f
        val hsH = crossH - padTopY - padBottomY

        val wDisp = s.width.replace(",", ".").toDoubleOrNull() ?: 100.0
        val hfDisp = s.finHeight.replace(",", ".").toDoubleOrNull() ?: 30.0
        val tbDisp = tbOpt; val tfDisp = tfOpt; val sDisp = sOpt
        val maxNFins = kotlin.math.floor((wDisp + sDisp) / (tfDisp + sDisp)).toInt().coerceAtLeast(0)
        val usedWidthDisp = if (maxNFins > 0) (maxNFins * tfDisp) + ((maxNFins - 1) * sDisp) else 0.0
        val sideGapDisp = if (maxNFins > 0) (wDisp - usedWidthDisp) / 2.0 else 0.0

        val scaleXCross = if (wDisp > 0.0) hsW / wDisp.toFloat() else 1f
        val totalRealH = hfDisp + tbDisp
        val hfRatio = (hfDisp / totalRealH).coerceIn(0.15, 0.85).toFloat()
        val drawHf = hsH * hfRatio; val drawTb = hsH * (1f - hfRatio)
        val baseTopY = crossTop + padTopY + drawHf

        paint.style = Paint.Style.FILL; paint.color = AndroidColor.parseColor("#6C757D")
        canvas1.drawRect(crossOX, baseTopY, crossOX + hsW, baseTopY + drawTb, paint)

        paint.color = AndroidColor.parseColor("#ADB5BD")
        for (i in 0 until maxNFins) {
            val finLeftX = crossOX + ((sideGapDisp + (i * (tfDisp + sDisp))) * scaleXCross).toFloat()
            val finWidthDraw = (tfDisp * scaleXCross).toFloat()
            canvas1.drawRect(finLeftX, crossTop + padTopY, finLeftX + finWidthDraw, crossTop + padTopY + drawHf, paint)
        }

        // Ölçülendirme okları (uygulamadaki ile aynı: W altta, Hf/tb sağda)
        val dimColor = AndroidColor.parseColor("#2E7D32")
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = dimColor

        val wY = crossTop + padTopY + hsH + 25f
        canvas1.drawLine(crossOX, wY, crossOX + hsW, wY, paint)
        canvas1.drawLine(crossOX, wY - 6f, crossOX, wY + 6f, paint)
        canvas1.drawLine(crossOX + hsW, wY - 6f, crossOX + hsW, wY + 6f, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas1.drawText("W", crossOX + hsW / 2f, wY + 18f, paint)

        val arrowX = crossOX + hsW + 20f
        val bottomY = crossTop + padTopY + hsH
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = dimColor; paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
        canvas1.drawLine(crossOX + hsW, crossTop + padTopY, arrowX + 10f, crossTop + padTopY, paint)
        canvas1.drawLine(crossOX + hsW, baseTopY, arrowX + 10f, baseTopY, paint)
        canvas1.drawLine(crossOX + hsW, bottomY, arrowX + 10f, bottomY, paint)
        paint.pathEffect = null

        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = dimColor
        canvas1.drawLine(arrowX, crossTop + padTopY, arrowX, baseTopY, paint)
        canvas1.drawLine(arrowX - 6f, crossTop + padTopY, arrowX + 6f, crossTop + padTopY, paint)
        canvas1.drawLine(arrowX - 6f, baseTopY, arrowX + 6f, baseTopY, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.LEFT
        canvas1.drawText("Hf", arrowX + 10f, crossTop + padTopY + (drawHf / 2f) + 4f, paint)

        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = dimColor
        canvas1.drawLine(arrowX, baseTopY, arrowX, bottomY, paint)
        canvas1.drawLine(arrowX - 6f, bottomY, arrowX + 6f, bottomY, paint)
        paint.style = Paint.Style.FILL
        canvas1.drawText("tb", arrowX + 10f, baseTopY + (drawTb / 2f) + 4f, paint)

        // GÜVENLİK: Paint durumunu bir sonraki bölüme sızdırmamak için kesin olarak sıfırla
        paint.style = Paint.Style.FILL; paint.pathEffect = null; paint.strokeWidth = 1f; paint.textAlign = Paint.Align.LEFT; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        yPos1 = crossTop + crossH + 12f
        paint.color = AndroidColor.parseColor("#1565C0"); paint.textSize = 9f
        canvas1.drawText("Not: Uzunluk (L) ölçüsü bu görselde derinliğe doğru olduğu için gösterilmiyor.", leftMargin, yPos1, paint)
        yPos1 += 16f

        paint.color = AndroidColor.BLACK
        canvas1.drawText(String.format(java.util.Locale.US, "Taban (tb): %.2f %s   |   Kanatçık Yük.(hf): %s %s   |   Kanatçık Kal.(tf): %.2f %s", tbOpt, s.lengthUnit, s.finHeight, s.lengthUnit, tfOpt, s.lengthUnit), leftMargin, yPos1, paint)
        yPos1 += 13f
        canvas1.drawText(String.format(java.util.Locale.US, "Kanatçık Aralığı (S): %.2f %s   |   Toplam Kanatçık Sayısı: %d adet", sOpt, s.lengthUnit, maxNFins), leftMargin, yPos1, paint)
        yPos1 += 25f

        paint.color = primaryColor; canvas1.drawRect(leftMargin, yPos1 - 15f, 555f, yPos1 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas1.drawText("2. Sisteme Eklenen Isı Kaynakları", leftMargin + 5f, yPos1, paint)
        yPos1 += 25f; paint.textSize = 10f; canvas1.drawText("İsim", leftMargin, yPos1, paint); canvas1.drawText("Güç", leftMargin + 100f, yPos1, paint); canvas1.drawText("Ölçü (${s.lengthUnit})", leftMargin + 160f, yPos1, paint); canvas1.drawText("Konum (${s.lengthUnit})", leftMargin + 260f, yPos1, paint); canvas1.drawText("TIM", leftMargin + 360f, yPos1, paint)
        yPos1 += 8f; paint.strokeWidth = 1f; canvas1.drawLine(leftMargin, yPos1, 555f, yPos1, paint); yPos1 += 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        // DUZELTILDI (PDF-UI Senkronizasyonu): Datasheet Rjc bilgisi UI'da (PageTwoScreen / ThermalMapTab)
        // gosteriliyordu ama PDF raporunda hic yer almiyordu. Artik her cip icin varsa ikinci bir satirda basiliyor.
        s.heatSources.forEach { chip ->
            canvas1.drawText(chip.name, leftMargin, yPos1, paint)
            canvas1.drawText("${chip.watt} ${s.powerUnit}", leftMargin + 100f, yPos1, paint)
            canvas1.drawText("${chip.wS} x ${chip.lS}", leftMargin + 160f, yPos1, paint)
            canvas1.drawText("X:${chip.posX}, Y:${chip.posY}", leftMargin + 260f, yPos1, paint)
            val timInfo = if(chip.hasTim) "${chip.timThick}mm, ${chip.timK}k" else "Yok"
            canvas1.drawText(timInfo, leftMargin + 360f, yPos1, paint)
            yPos1 += 15f
            if (chip.useCustomRjc) {
                paint.color = AndroidColor.parseColor("#1565C0")
                paint.textSize = 9f
                canvas1.drawText("↳ Datasheet Rjc Aktif: ${chip.customRjcVal} °C/W", leftMargin + 15f, yPos1, paint)
                paint.color = AndroidColor.BLACK
                paint.textSize = 10f
                yPos1 += 13f
            }
        }
        yPos1 += 20f

        paint.color = primaryColor; canvas1.drawRect(leftMargin, yPos1 - 15f, 555f, yPos1 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas1.drawText("3. Ortam Koşulları ve Akış", leftMargin + 5f, yPos1, paint)
        yPos1 += 25f; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas1.drawText("Ortam Sıcaklığı: ${env.ambientTemp} ${s.tempUnit}", leftMargin, yPos1, paint); canvas1.drawText("Rakım: ${env.altitude} ${s.altitudeUnit}", 300f, yPos1, paint)
        yPos1 += 15f; canvas1.drawText("Akış Tipi: ${env.selectedFlowType}", leftMargin, yPos1, paint); canvas1.drawText("Radyasyon (ε): ${env.emissivityValueStr}", 300f, yPos1, paint)
        if (env.selectedFlowType.contains("Fanlı")) { yPos1 += 15f; canvas1.drawText("Fan Modu: ${env.selectedFanMethod}", leftMargin, yPos1, paint); val tunnelInfo = if(env.isTunnelEnabled) "Bypass Tüneli: ${env.chassisCw}x${env.chassisCh} ${s.lengthUnit}" else "Kapalı Kasa: Yok"; canvas1.drawText(tunnelInfo, 300f, yPos1, paint) }

        // YENİ (PDF-UI Senkronizasyonu): Kalibrasyon çarpanı 1.0 dışındaysa mühendislik şeffaflığı için not düşülüyor.
        val calFactor = env.calibrationFactor.replace(",", ".").toDoubleOrNull() ?: 1.0
        if (kotlin.math.abs(calFactor - 1.0) > 0.001) {
            yPos1 += 18f
            paint.color = AndroidColor.parseColor("#F57C00")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas1.drawText(String.format(java.util.Locale.US, "⚠️ Kalibrasyon Çarpanı Aktif: x%.2f (Standart analitik modelden sapma var)", calFactor), leftMargin, yPos1, paint)
            paint.color = AndroidColor.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        pdfDocument.finishPage(page1)

        val page2Info = PdfDocument.PageInfo.Builder(595, 842, 2).create(); val page2 = pdfDocument.startPage(page2Info); val canvas2 = page2.canvas
        drawHeaderAndFooter(canvas2, 2)

        var yPos2 = 130f
        paint.color = primaryColor; canvas2.drawRect(leftMargin, yPos2 - 15f, 555f, yPos2 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas2.drawText("4. Sistem Performansı ve Akış Dinamikleri", leftMargin + 5f, yPos2, paint)
        yPos2 += 25f; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas2.drawText(String.format(java.util.Locale.US, "Toplam Sistem Direnci (R_total): %.3f %s", resValue, s.resistanceUnit), leftMargin, yPos2, paint)

        // DOKÜMANTASYON: PDF Raporuna Termal Süre (Tau) verisi eklendi
        val totalSecs = result.timeConstantSeconds.toInt()
        val timeStr = if (totalSecs / 60 > 0) "${totalSecs / 60} dk ${totalSecs % 60} sn" else "$totalSecs sn"
        canvas2.drawText("Zaman Sabiti (Tau): $timeStr", 300f, yPos2, paint)

        yPos2 += 15f; val wFormatStr = if(s.weightUnit == "kg" || s.weightUnit == "lbs") "Tahmini Ağırlık: %.3f %s" else "Tahmini Ağırlık: %.1f %s"; canvas2.drawText(String.format(java.util.Locale.US, wFormatStr, wValue, s.weightUnit), leftMargin, yPos2, paint)
        canvas2.drawText(String.format(java.util.Locale.US, "Kanat Verimliliği: %% %.1f", result.finEfficiencyPercent), 300f, yPos2, paint)
        yPos2 += 15f; canvas2.drawText(String.format(java.util.Locale.US, "Toplam Hacim: %.1f %s", vValue, vUnit), leftMargin, yPos2, paint)

        yPos2 += 25f;
        if (result.pressureDropPa > 0) {
            canvas2.drawText(String.format(java.util.Locale.US, "Sistem Basınç Kaybı (ΔP): %.1f %s", pValue, s.pressureUnit), leftMargin, yPos2, paint)
            if (result.bypassFactor < 0.98) {
                paint.color = AndroidColor.RED; canvas2.drawText(String.format(java.util.Locale.US, "⚠️ Bypass Kaçağı: %% %.0f", (1.0 - result.bypassFactor) * 100), 300f, yPos2, paint); paint.color = AndroidColor.BLACK
            }

            val cfmReq = if (s.envState.selectedFanMethod.contains("Debisi")) {
                s.envState.fixedFlowStr.replace(",", ".").toDoubleOrNull() ?: 10.0
            } else if (s.envState.selectedFanMethod.contains("Hızı")) {
                (s.envState.fixedSpeedStr.replace(",", ".").toDoubleOrNull() ?: 2.0) * 20.0
            } else { 30.0 }
            val fanSuggestion = when {
                cfmReq < 15.0 && result.pressureDropPa < 15.0 -> "40/60mm Düşük Profilli Eksenel Fan"
                cfmReq in 15.0..40.0 && result.pressureDropPa < 30.0 -> "80/92mm Standart Kasa Fanı"
                cfmReq > 40.0 && result.pressureDropPa < 50.0 -> "120/140mm Yüksek Akışlı Fan"
                result.pressureDropPa >= 50.0 && result.pressureDropPa < 100.0 -> "Yüksek Statik Basınçlı veya Blower Fan"
                result.pressureDropPa >= 100.0 -> "Endüstriyel Salyangoz veya Çift Fanlı (Push-Pull)"
                else -> "Uygulamaya Özel Performans Fanı"
            }
            yPos2 += 15f
            paint.color = AndroidColor.parseColor("#F57C00") // Turuncu Renk
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas2.drawText("💡 Önerilen Fan Tipi: $fanSuggestion", leftMargin, yPos2, paint)
            paint.color = AndroidColor.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        if (result.operatingFlowM3s > 0.0) {
            val displayFlow = when(s.flowUnit) {
                "CFM" -> result.operatingFlowM3s * 2118.88
                "L/min" -> result.operatingFlowM3s * 60000.0
                "m³/h" -> result.operatingFlowM3s * 3600.0
                else -> result.operatingFlowM3s
            }
            yPos2 += 15f
            paint.color = AndroidColor.parseColor("#388E3C") // Koyu Yeşil Renk
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas2.drawText(String.format(java.util.Locale.US, "✅ Sistem Dengelendi: %.1f %s çalışma debisi hesaplandı.", displayFlow, s.flowUnit), leftMargin, yPos2, paint)
            paint.color = AndroidColor.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        yPos2 += 35f

        // DÜZELTİLDİ: Başlık arayüz ile birebir aynı yapıldı
        paint.color = primaryColor; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas2.drawRect(leftMargin, yPos2 - 15f, 555f, yPos2 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; canvas2.drawText("5. Canlı Termal Isı Dağılımı", leftMargin + 5f, yPos2, paint); yPos2 += 20f

        // YENİ: Uygulamadaki ThermalMapCanvas ile birebir aynı kenar boşluğu/eksen mantığı
        val totalMapAreaHeight = 260f
        val padLeft = 46f; val padRight = 16f; val padTop = 26f; val padBottom = 40f
        val areaLeft = leftMargin + padLeft; val areaTop = yPos2 + padTop
        val availW = (555f - leftMargin) - padLeft - padRight
        val availH = totalMapAreaHeight - padTop - padBottom
        val bW_mm = (s.width.replace(",", ".").toDoubleOrNull() ?: 100.0) * lDiv; val bL_mm = (s.length.replace(",", ".").toDoubleOrNull() ?: 100.0) * lDiv
        val blockRatio = bW_mm / bL_mm; val canvasRatio = availW / availH; val cW: Float; val cH: Float; val mapOffsetX: Float; val mapOffsetY: Float
        if (blockRatio > canvasRatio) { cW = availW; cH = (availW / blockRatio).toFloat(); mapOffsetX = areaLeft; mapOffsetY = areaTop + (availH - cH) / 2f } else { cH = availH; cW = (availH * blockRatio).toFloat(); mapOffsetX = areaLeft + (availW - cW) / 2f; mapOffsetY = areaTop }

        val scaleX = cW / bW_mm.toFloat(); val scaleY = cH / bL_mm.toFloat()
        val minTemp = s.envState.ambientTemp.replace(",", ".").toDoubleOrNull() ?: when(s.tempUnit) { "°F" -> 68.0; "K" -> 293.15; else -> 20.0 }
        val maxTemp = minTemp + when(s.tempUnit) { "°F" -> 135.0; "K" -> 75.0; else -> 75.0 }

        fun lerpColor(color1: Int, color2: Int, ratio: Float): Int {
            val r = ratio.coerceIn(0f, 1f); val a = (AndroidColor.alpha(color1) * (1 - r) + AndroidColor.alpha(color2) * r).toInt(); val red = (AndroidColor.red(color1) * (1 - r) + AndroidColor.red(color2) * r).toInt(); val green = (AndroidColor.green(color1) * (1 - r) + AndroidColor.green(color2) * r).toInt(); val blue = (AndroidColor.blue(color1) * (1 - r) + AndroidColor.blue(color2) * r).toInt()
            return AndroidColor.argb(a, red, green, blue)
        }
        fun getJetColor(value: Float): Int {
            val v = value.coerceIn(0f, 1f)
            return when { v < 0.25f -> lerpColor(AndroidColor.parseColor("#000080"), AndroidColor.parseColor("#00FFFF"), v / 0.25f); v < 0.50f -> lerpColor(AndroidColor.parseColor("#00FFFF"), AndroidColor.parseColor("#00FF00"), (v - 0.25f) / 0.25f); v < 0.75f -> lerpColor(AndroidColor.parseColor("#00FF00"), AndroidColor.parseColor("#FFFF00"), (v - 0.50f) / 0.25f); else -> lerpColor(AndroidColor.parseColor("#FFFF00"), AndroidColor.parseColor("#FF0000"), (v - 0.75f) / 0.25f) }
        }

        paint.color = getJetColor(0f); canvas2.drawRect(mapOffsetX, mapOffsetY, mapOffsetX + cW, mapOffsetY + cH, paint)
        canvas2.save(); canvas2.clipRect(mapOffsetX, mapOffsetY, mapOffsetX + cW, mapOffsetY + cH)
        val maxRadius = kotlin.math.hypot(cW.toDouble(), cH.toDouble()).toFloat()

        result.chipResults.forEach { chip ->
            val src = chip.sourceInfo; val sX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY; val sW = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sL = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY
            val drawX = mapOffsetX + sX.toFloat(); val drawY = mapOffsetY + cH - sY.toFloat() - sL.toFloat()
            val centerX = drawX + (sW.toFloat() / 2f); val centerY = drawY + (sL.toFloat() / 2f)
            val displayTemp = when(s.tempUnit) { "°F" -> (chip.tempJunction * 1.8) + 32.0; "K" -> chip.tempJunction + 273.15; else -> chip.tempJunction }
            val chipRatio = ((displayTemp - minTemp) / (maxTemp - minTemp)).coerceIn(0.0, 1.0).toFloat()
            val gradientRadius = maxRadius * 0.9f
            val colors = IntArray(16); val positions = FloatArray(16)
            for (i in 0..15) { val distanceFraction = i.toFloat() / 15f; val currentTempRatio = chipRatio * (1f - distanceFraction); val alphaFloat = Math.pow((1f - distanceFraction).toDouble(), 1.5).toFloat(); val alpha = (alphaFloat * 255).toInt().coerceIn(0, 255); val baseColor = getJetColor(currentTempRatio); colors[i] = AndroidColor.argb(alpha, AndroidColor.red(baseColor), AndroidColor.green(baseColor), AndroidColor.blue(baseColor)); positions[i] = distanceFraction }
            val radialGradient = android.graphics.RadialGradient(centerX, centerY, gradientRadius, colors, positions, android.graphics.Shader.TileMode.CLAMP)
            paint.shader = radialGradient; canvas2.drawCircle(centerX, centerY, gradientRadius, paint); paint.shader = null
        }

        // DÜZELTİLDİ: Ebat (üstte) ve koordinat (altta) artık kutunun DIŞINDA - uygulamayla birebir aynı
        result.chipResults.forEach { chip ->
            val src = chip.sourceInfo; val sX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY; val sW = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sL = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY
            val drawX = mapOffsetX + sX.toFloat(); val drawY = mapOffsetY + cH - sY.toFloat() - sL.toFloat()
            val boxW = sW.toFloat(); val boxH = sL.toFloat()
            paint.style = Paint.Style.STROKE; paint.color = AndroidColor.BLACK; paint.strokeWidth = 1.5f; canvas2.drawRect(drawX, drawY, drawX + boxW, drawY + boxH, paint)
            paint.style = Paint.Style.FILL

            val displayTemp = when(s.tempUnit) { "°F" -> (chip.tempJunction * 1.8) + 32.0; "K" -> chip.tempJunction + 273.15; else -> chip.tempJunction }
            paint.color = AndroidColor.WHITE; paint.textSize = 10f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textAlign = Paint.Align.CENTER
            canvas2.drawText(String.format(java.util.Locale.US, "%.0f°", displayTemp), drawX + boxW / 2f, drawY + boxH / 2f + 3.5f, paint)

            val dispWs = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) / lDiv; val dispLs = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) / lDiv
            paint.textSize = 8f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas2.drawText(String.format(java.util.Locale.US, "%.0fx%.0f", dispWs, dispLs), drawX + boxW / 2f, drawY - 5f, paint)

            val dispPosX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) / lDiv; val dispPosY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) / lDiv
            canvas2.drawText(String.format(java.util.Locale.US, "(%.0f, %.0f)", dispPosX, dispPosY), drawX + boxW / 2f, drawY + boxH + 10f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        canvas2.restore()

        paint.style = Paint.Style.STROKE; paint.color = AndroidColor.BLACK; paint.strokeWidth = 2f; canvas2.drawRect(mapOffsetX, mapOffsetY, mapOffsetX + cW, mapOffsetY + cH, paint); paint.style = Paint.Style.FILL

        // YENİ: Blok boyutu başlığı + X/Y cetvel rakamları (uygulamayla birebir aynı)
        val dimUnit = s.lengthUnit
        val dispBW = bW_mm / lDiv; val dispBL = bL_mm / lDiv
        val maxDim = maxOf(dispBW, dispBL)
        val formatStr = if (maxDim <= 2.0) "%.2f" else if (maxDim <= 10.0) "%.1f" else "%.0f"
        paint.color = AndroidColor.BLACK; paint.textSize = 10f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textAlign = Paint.Align.LEFT
        canvas2.drawText(String.format(java.util.Locale.US, "Blok Boyutu: $formatStr x $formatStr %s", dispBW, dispBL, dimUnit), areaLeft, yPos2 + 12f, paint)

        paint.textSize = 8f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); paint.color = AndroidColor.DKGRAY
        val numRulerSteps = 4
        for (i in 0..numRulerSteps) {
            val ratio = i.toFloat() / numRulerSteps
            val rX = mapOffsetX + (cW * ratio); val valX = dispBW * ratio
            paint.textAlign = Paint.Align.CENTER; canvas2.drawText(String.format(java.util.Locale.US, formatStr, valX), rX, mapOffsetY + cH + 14f, paint)
            val rY = (mapOffsetY + cH) - (cH * ratio); val valY = dispBL * ratio
            paint.textAlign = Paint.Align.RIGHT; canvas2.drawText(String.format(java.util.Locale.US, formatStr, valY), mapOffsetX - 6f, rY + 3f, paint)
        }
        paint.textAlign = Paint.Align.LEFT

        yPos2 += totalMapAreaHeight + 15f

        pdfDocument.finishPage(page2)

        // DEĞİŞTİRİLDİ: Darboğaz artık kendi ayrı sayfasında (Sayfa 2 sıkışıklığını gidermek için)
        val darbogazPageNum = 3
        val pageBnInfo = PdfDocument.PageInfo.Builder(595, 842, darbogazPageNum).create()
        val pageBn = pdfDocument.startPage(pageBnInfo)
        val canvasBn = pageBn.canvas
        drawHeaderAndFooter(canvasBn, darbogazPageNum)

        var yPosBn = 130f
        paint.color = primaryColor; canvasBn.drawRect(leftMargin, yPosBn - 15f, 555f, yPosBn + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvasBn.drawText("6. Akıllı Teşhis (Darboğaz) Asistanı", leftMargin + 5f, yPosBn, paint)
        yPosBn += 25f; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // DOKÜMANTASYON: PDF üzerindeki uyarı metinleri güncellendi
        if (result.isChoked && s.envState.selectedFlowType.contains("Doğal")) {
            paint.color = AndroidColor.RED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvasBn.drawText("⚠️ Kritik Uyarı: Hava Akışı Boğulması (Choking)", leftMargin, yPosBn, paint)
            yPosBn += 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvasBn.drawText("Kanatçıklar çok sıkışık, doğal taşınım durma noktasına geldi. Kanat aralığını (S) artırın.", leftMargin, yPosBn, paint)
            yPosBn += 20f
        }

        if (result.viewFactor < 0.60) {
            paint.color = AndroidColor.RED
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvasBn.drawText("⚠️ Kritik Uyarı: Isıl Işınım (Radyasyon) Blokajı", leftMargin, yPosBn, paint)
            yPosBn += 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvasBn.drawText(String.format(java.util.Locale.US, "Radyasyonun yaklaşık %% %.0f'si kanatların birbirine bakması nedeniyle içeride hapsoluyor.", (1.0 - result.viewFactor) * 100), leftMargin, yPosBn, paint)
            yPosBn += 20f
        }

        // Isıl Darboğaz bar grafiği - uygulamadaki ile aynı, öneme göre renklendirilmiş (kırmızı=en önemli, mavi=en önemsiz)
        val rTotalForBar = result.rTotalSystem.coerceAtLeast(0.0001)
        val bottleneckItems = listOf(
            Pair("Taşınım (Havaya Atım)", result.rConv),
            Pair("Taban İletimi (Kalınlık)", result.rCondBase),
            Pair("Yayılma (Spreading)", result.rSpreadAvg),
            Pair("TIM (Macun/Pad)", result.rTimAvg)
        ).sortedByDescending { it.second }
        val maxBarValBn = bottleneckItems.maxOf { it.second }.coerceAtLeast(0.0001)
        val barAreaLeftBn = leftMargin + 150f
        val barAreaWBn = 555f - barAreaLeftBn - 70f
        val barHBn = 18f

        fun bottleneckColorInt(ratio: Float): Int {
            val r = ratio.coerceIn(0f, 1f)
            return when {
                r < 0.25f -> lerpColor(AndroidColor.parseColor("#64B5F6"), AndroidColor.parseColor("#26C6DA"), r / 0.25f)
                r < 0.50f -> lerpColor(AndroidColor.parseColor("#26C6DA"), AndroidColor.parseColor("#81C784"), (r - 0.25f) / 0.25f)
                r < 0.75f -> lerpColor(AndroidColor.parseColor("#81C784"), AndroidColor.parseColor("#FFEB3B"), (r - 0.50f) / 0.25f)
                else -> lerpColor(AndroidColor.parseColor("#FFEB3B"), AndroidColor.parseColor("#E63946"), (r - 0.75f) / 0.25f)
            }
        }

        bottleneckItems.forEach { (label, value) ->
            val ratioBn = (value / maxBarValBn).toFloat().coerceIn(0f, 1f)
            val percentBn = value / rTotalForBar * 100.0

            paint.color = AndroidColor.BLACK; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvasBn.drawText(label, leftMargin, yPosBn + barHBn - 5f, paint)

            paint.color = AndroidColor.parseColor("#E0E0E0")
            canvasBn.drawRect(barAreaLeftBn, yPosBn, barAreaLeftBn + barAreaWBn, yPosBn + barHBn, paint)

            paint.color = bottleneckColorInt(ratioBn)
            canvasBn.drawRect(barAreaLeftBn, yPosBn, barAreaLeftBn + (barAreaWBn * ratioBn), yPosBn + barHBn, paint)

            paint.color = AndroidColor.DKGRAY; paint.textSize = 10f
            canvasBn.drawText(String.format(java.util.Locale.US, "%%%.0f", percentBn), barAreaLeftBn + barAreaWBn + 6f, yPosBn + barHBn - 5f, paint)

            yPosBn += barHBn + 14f
        }
        yPosBn += 15f

        // GÜVENLİK: Paint durumunu bir sonrakine sızdırmamak için sıfırla
        paint.color = AndroidColor.BLACK; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val maxResistance = maxOf(result.rTimAvg, result.rSpreadAvg, result.rCondBase, result.rConv)
        val (diag1, diag2) = when (maxResistance) {
            result.rConv -> Pair("Darboğaz: Havaya Atım (Taşınım).", "Isı soğutucuya başarıyla ulaşıyor ancak havaya atılamıyor. Fan debisini (${s.flowUnit}) artırın.")
            result.rTimAvg -> Pair("Darboğaz: Termal Macun/Pad (TIM).", "Isı çipten soğutucuya geçişte zorlanıyor. Daha ince/iletken bir arayüz malzemesi seçin.")
            result.rSpreadAvg -> Pair("Darboğaz: Taban Yayılımı (Spreading).", "Isı yanlara yayılamıyor. Taban kalınlığını (tb) artırın veya Bakır malzeme kullanın.")
            else -> Pair("Darboğaz: Taban İletimi.", "Tabanınız gereğinden fazla kalın. Isı, kanatçıklara ulaşmadan taban içinde hapsoluyor.")
        }

        paint.color = AndroidColor.BLACK
        canvasBn.drawText("Sistemdeki en yüksek ısıl direnç test edildi. Teşhis ve Önerimiz:", leftMargin, yPosBn, paint); yPosBn += 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.color = AndroidColor.parseColor("#D62828"); canvasBn.drawText(diag1, leftMargin, yPosBn, paint); yPosBn += 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); paint.color = AndroidColor.DKGRAY; canvasBn.drawText(diag2, leftMargin, yPosBn, paint)

        paint.color = AndroidColor.BLACK
        pdfDocument.finishPage(pageBn)

        // YENİ: Fan P-Q Kesişim Grafiği (sadece "Fan Eğrisi Girişi" metodu kullanıldığında)
        if (hasFanCurveGraph) {
            val fanCurvePageNum = darbogazPageNum + 1
            val page3Info = PdfDocument.PageInfo.Builder(595, 842, fanCurvePageNum).create()
            val page3 = pdfDocument.startPage(page3Info)
            val canvas3 = page3.canvas
            drawHeaderAndFooter(canvas3, fanCurvePageNum)

            var yPos3 = 130f
            paint.color = primaryColor; canvas3.drawRect(leftMargin, yPos3 - 15f, 555f, yPos3 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas3.drawText("7. Fan Performans Grafiği (P-Q Kesişimi)", leftMargin + 5f, yPos3, paint)
            yPos3 += 25f; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas3.drawText("Mavi çizgi: Fanın P-Q eğrisi. Kırmızı çizgi: Sistemin direnç eğrisi. Yeşil nokta: Dengelenen çalışma noktası.", leftMargin, yPos3, paint)
            yPos3 += 25f

            val opQ = when(s.flowUnit) { "CFM" -> result.operatingFlowM3s * 2118.88; "L/min" -> result.operatingFlowM3s * 60000.0; "m³/h" -> result.operatingFlowM3s * 3600.0; else -> result.operatingFlowM3s }
            val opP = when(s.pressureUnit) { "in-H2O" -> result.pressureDropPa * 0.00401865; "mm-H2O" -> result.pressureDropPa * 0.101972; else -> result.pressureDropPa }

            val graphLeft = leftMargin
            val graphTop = yPos3
            val graphW = 555f - leftMargin
            val graphH = 380f

            val padLeft = graphLeft + 55f
            val padBottom = graphTop + graphH - 45f
            val padTop = graphTop + 15f
            val padRight = graphLeft + graphW - 20f
            val gw = padRight - padLeft
            val gh = padBottom - padTop

            val maxQ = maxOf(rawCurve.maxOf { it.first }, opQ * 1.1).coerceAtLeast(1.0)
            val maxP = maxOf(rawCurve.maxOf { it.second }, opP * 1.5).coerceAtLeast(1.0)

            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val gridEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
            val numGridLines = 4
            for (i in 0..numGridLines) {
                val y = padBottom - (i * gh / numGridLines)
                val valP = (i * maxP / numGridLines)
                if (i > 0) {
                    paint.color = AndroidColor.LTGRAY; paint.strokeWidth = 1f; paint.pathEffect = gridEffect
                    canvas3.drawLine(padLeft, y, padRight, y, paint)
                    paint.pathEffect = null
                }
                paint.color = AndroidColor.DKGRAY
                canvas3.drawText(String.format(java.util.Locale.US, "%.1f", valP), padLeft - 45f, y + 3f, paint)
            }
            for (i in 0..numGridLines) {
                val x = padLeft + (i * gw / numGridLines)
                val valQ = (i * maxQ / numGridLines)
                if (i > 0) {
                    paint.color = AndroidColor.LTGRAY; paint.strokeWidth = 1f; paint.pathEffect = gridEffect
                    canvas3.drawLine(x, padTop, x, padBottom, paint)
                    paint.pathEffect = null
                }
                paint.color = AndroidColor.DKGRAY
                canvas3.drawText(String.format(java.util.Locale.US, "%.1f", valQ), x - 10f, padBottom + 15f, paint)
            }

            paint.color = AndroidColor.GRAY; paint.strokeWidth = 2f
            canvas3.drawLine(padLeft, padBottom, padRight, padBottom, paint)
            canvas3.drawLine(padLeft, padTop, padLeft, padBottom, paint)

            paint.color = AndroidColor.DKGRAY; paint.textSize = 10f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas3.drawText("Basınç (P) [${s.pressureUnit}]", padLeft, padTop - 5f, paint)
            canvas3.drawText("Debi (Q) [${s.flowUnit}]", padLeft + (gw / 2f) - 30f, padBottom + 30f, paint)

            canvas3.save()
            canvas3.clipRect(padLeft, padTop, padRight, padBottom)

            val safeOpQ = maxOf(opQ, 0.0001)
            val kFit = opP / (safeOpQ * safeOpQ)
            val resPath = android.graphics.Path()
            val stepQ = maxQ / 50.0
            for (i in 0..50) {
                val currentQ = i * stepQ
                val currentP = minOf(kFit * (currentQ * currentQ), maxP * 2.0)
                val cx = padLeft + ((currentQ / maxQ) * gw).toFloat()
                val cy = padBottom - ((currentP / maxP) * gh).toFloat()
                if (i == 0) resPath.moveTo(cx, cy) else resPath.lineTo(cx, cy)
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = AndroidColor.parseColor("#E63946"); paint.pathEffect = null
            canvas3.drawPath(resPath, paint)

            val fanPath = android.graphics.Path()
            rawCurve.forEachIndexed { i, point ->
                val cx = padLeft + ((point.first / maxQ) * gw).toFloat()
                val cy = padBottom - ((point.second / maxP) * gh).toFloat()
                if (i == 0) fanPath.moveTo(cx, cy) else fanPath.lineTo(cx, cy)
            }
            paint.color = AndroidColor.parseColor("#1565C0")
            canvas3.drawPath(fanPath, paint)

            if (opQ > 0.0 && opP > 0.0) {
                val opCx = (padLeft + ((opQ / maxQ) * gw).toFloat()).coerceIn(padLeft, padRight)
                val opCy = (padBottom - ((opP / maxP) * gh).toFloat()).coerceIn(padTop, padBottom)
                paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
                paint.color = AndroidColor.LTGRAY; paint.strokeWidth = 1f
                canvas3.drawLine(padLeft, opCy, opCx, opCy, paint)
                canvas3.drawLine(opCx, padBottom, opCx, opCy, paint)
                paint.pathEffect = null

                paint.style = Paint.Style.FILL; paint.color = AndroidColor.parseColor("#4CAF50")
                canvas3.drawCircle(opCx, opCy, 5f, paint)
                paint.style = Paint.Style.STROKE; paint.color = AndroidColor.WHITE; paint.strokeWidth = 1.5f
                canvas3.drawCircle(opCx, opCy, 5f, paint)
                paint.style = Paint.Style.FILL
            }

            canvas3.restore()
            paint.style = Paint.Style.FILL // DÜZELTİLDİ: Doğal taşınımda (opQ=0) bu hiç FILL'e dönmüyordu, legend yazıları kalın dış hat olarak (siyah leke gibi) çiziliyordu

            val legendX = padRight - 140f
            val legendY = padTop + 15f
            paint.color = AndroidColor.parseColor("#1565C0"); paint.strokeWidth = 4f
            canvas3.drawLine(legendX, legendY, legendX + 20f, legendY, paint)
            paint.color = AndroidColor.DKGRAY; paint.textSize = 10f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas3.drawText("Fan Eğrisi", legendX + 26f, legendY + 4f, paint)

            paint.color = AndroidColor.parseColor("#E63946")
            canvas3.drawLine(legendX, legendY + 18f, legendX + 20f, legendY + 18f, paint)
            paint.color = AndroidColor.DKGRAY
            canvas3.drawText("Sistem Direnci", legendX + 26f, legendY + 22f, paint)

            paint.color = AndroidColor.BLACK
            pdfDocument.finishPage(page3)
        }

        // YENİ: Duyarlılık Analizi (Tornado) Sayfası - hangi parametrenin R_total'a en çok etkisi olduğunu gösterir
        if (hasSensitivity) {
            val sensitivityPageNum = if (hasFanCurveGraph) 5 else 4
            val sensitivitySectionNum = if (hasFanCurveGraph) 8 else 7
            val page4Info = PdfDocument.PageInfo.Builder(595, 842, sensitivityPageNum).create()
            val page4 = pdfDocument.startPage(page4Info)
            val canvas4 = page4.canvas
            drawHeaderAndFooter(canvas4, sensitivityPageNum)

            var yPos4 = 130f
            paint.color = primaryColor; canvas4.drawRect(leftMargin, yPos4 - 15f, 555f, yPos4 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas4.drawText("$sensitivitySectionNum. Duyarlılık Analizi (Hangi Parametre Daha Etkili?)", leftMargin + 5f, yPos4, paint)
            yPos4 += 25f; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas4.drawText("Her parametre mevcut değerinin ±%10 civarında oynatılıp R_total üzerindeki etkisi ölçüldü.", leftMargin, yPos4, paint)
            yPos4 += 15f
            canvas4.drawText("Çubuk ne kadar uzunsa, o kadar önemlidir.", leftMargin, yPos4, paint)
            yPos4 += 25f

            val resUnit4 = s.resistanceUnit
            val maxDelta = result.sensitivityItems.maxOf { it.deltaR }.coerceAtLeast(0.0001)
            val barAreaLeft = leftMargin + 150f
            val barAreaW = 555f - barAreaLeft - 70f
            val barH = 18f

            // YENİ: Uygulamadaki ile aynı önem-renk skalası (kırmızı=en önemli, mavi=en önemsiz)
            fun importanceColorInt(ratio: Float): Int {
                val r = ratio.coerceIn(0f, 1f)
                return when {
                    r < 0.25f -> lerpColor(AndroidColor.parseColor("#64B5F6"), AndroidColor.parseColor("#26C6DA"), r / 0.25f)
                    r < 0.50f -> lerpColor(AndroidColor.parseColor("#26C6DA"), AndroidColor.parseColor("#81C784"), (r - 0.25f) / 0.25f)
                    r < 0.75f -> lerpColor(AndroidColor.parseColor("#81C784"), AndroidColor.parseColor("#FFEB3B"), (r - 0.50f) / 0.25f)
                    else -> lerpColor(AndroidColor.parseColor("#FFEB3B"), AndroidColor.parseColor("#E63946"), (r - 0.75f) / 0.25f)
                }
            }

            result.sensitivityItems.forEach { item ->
                val displayDelta = if (resUnit4 == "°F/W") item.deltaR * 1.8 else item.deltaR
                val ratio = (item.deltaR / maxDelta).toFloat().coerceIn(0f, 1f)

                paint.color = AndroidColor.BLACK; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas4.drawText(item.paramName, leftMargin, yPos4 + barH - 5f, paint)

                paint.color = AndroidColor.parseColor("#E0E0E0")
                canvas4.drawRect(barAreaLeft, yPos4, barAreaLeft + barAreaW, yPos4 + barH, paint)

                paint.color = importanceColorInt(ratio)
                canvas4.drawRect(barAreaLeft, yPos4, barAreaLeft + (barAreaW * ratio), yPos4 + barH, paint)

                paint.color = AndroidColor.DKGRAY; paint.textSize = 10f
                canvas4.drawText(String.format(java.util.Locale.US, "Δ %.3f %s", displayDelta, resUnit4), barAreaLeft + barAreaW + 8f, yPos4 + barH - 5f, paint)

                yPos4 += barH + 14f
            }

            yPos4 += 15f
            val topItem = result.sensitivityItems.maxByOrNull { it.deltaR }
            if (topItem != null) {
                val bgGreen = AndroidColor.parseColor("#4CAF50")
                paint.color = AndroidColor.argb(38, AndroidColor.red(bgGreen), AndroidColor.green(bgGreen), AndroidColor.blue(bgGreen))
                canvas4.drawRect(leftMargin, yPos4, 555f, yPos4 + 45f, paint)
                paint.color = AndroidColor.BLACK; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textSize = 12f
                canvas4.drawText("En Etkili Parametre: ${topItem.paramName}", leftMargin + 8f, yPos4 + 18f, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); paint.color = AndroidColor.DKGRAY; paint.textSize = 10f
                canvas4.drawText("Tasarımı iyileştirmek için önce bu parametreyi değiştirmeyi deneyin.", leftMargin + 8f, yPos4 + 34f, paint)
            }

            paint.color = AndroidColor.BLACK
            pdfDocument.finishPage(page4)
        }

        // YENİ: Optimizasyon Isı Haritası Sayfası (tb sabit, tf x S taraması)
        if (hasHeatmap) {
            val heatmapPageNum = 3 + (if (hasFanCurveGraph) 1 else 0) + (if (hasSensitivity) 1 else 0) + 1
            val heatmapSectionNum = 6 + (if (hasFanCurveGraph) 1 else 0) + (if (hasSensitivity) 1 else 0) + 1
            val page5Info = PdfDocument.PageInfo.Builder(595, 842, heatmapPageNum).create()
            val page5 = pdfDocument.startPage(page5Info)
            val canvas5 = page5.canvas
            drawHeaderAndFooter(canvas5, heatmapPageNum)

            var yPos5 = 130f
            paint.color = primaryColor; canvas5.drawRect(leftMargin, yPos5 - 15f, 555f, yPos5 + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas5.drawText("$heatmapSectionNum. Optimizasyon Isı Haritası", leftMargin + 5f, yPos5, paint)
            yPos5 += 25f; paint.textSize = 11f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas5.drawText(String.format(java.util.Locale.US, "Taban Kalınlığı (tb) = %.1f %s sabit tutuldu, kanat kalınlığı (tf) ve aralığı (S) tarandı.", result.usedTb / lDiv, s.lengthUnit), leftMargin, yPos5, paint)
            yPos5 += 25f

            val tfValues = result.heatmapCells.map { it.tf }.distinct().sorted()
            val sValues = result.heatmapCells.map { it.s }.distinct().sorted()
            val cellLookup = result.heatmapCells.associateBy { Pair(it.tf, it.s) }
            val validRValues = result.heatmapCells.filter { it.isValid }.map { it.rTotal }
            val minR = validRValues.minOrNull() ?: 0.0
            val maxR = (validRValues.maxOrNull() ?: 1.0).coerceAtLeast(minR + 0.0001)

            val gridLeft = leftMargin + 45f
            val gridTop = yPos5
            val gridW = 555f - gridLeft - 15f
            val gridH = 320f
            val nRows = tfValues.size.coerceAtLeast(1)
            val nCols = sValues.size.coerceAtLeast(1)
            val cellW = gridW / nCols
            val cellH = gridH / nRows

            for (rowIdx in 0 until nRows) {
                val tfVal = tfValues[rowIdx]
                val yTop = gridTop + (nRows - 1 - rowIdx) * cellH
                for (colIdx in 0 until nCols) {
                    val sVal = sValues[colIdx]
                    val xLeft = gridLeft + colIdx * cellW
                    val cell = cellLookup[Pair(tfVal, sVal)]
                    val cellColorInt = if (cell == null) {
                        AndroidColor.parseColor("#2C3136")
                    } else if (!cell.isValid) {
                        AndroidColor.parseColor("#3A3E42")
                    } else {
                        val frac = ((cell.rTotal - minR) / (maxR - minR)).coerceIn(0.0, 1.0).toFloat()
                        lerpColor(AndroidColor.parseColor("#81C784"), AndroidColor.parseColor("#E63946"), frac)
                    }
                    paint.color = cellColorInt
                    canvas5.drawRect(xLeft + 1f, yTop + 1f, xLeft + cellW - 1f, yTop + cellH - 1f, paint)

                    val isChosenCell = cell != null && kotlin.math.abs(cell.tf - result.usedTf) < 0.01 && kotlin.math.abs(cell.s - result.usedS) < 0.01
                    if (isChosenCell) {
                        paint.style = Paint.Style.STROKE; paint.color = AndroidColor.WHITE; paint.strokeWidth = 2f
                        canvas5.drawRect(xLeft + 1f, yTop + 1f, xLeft + cellW - 1f, yTop + cellH - 1f, paint)
                        paint.style = Paint.Style.FILL
                    }
                }
            }

            paint.color = AndroidColor.DKGRAY; paint.textSize = 9f
            val rowLabelIndices = listOf(0, nRows / 2, nRows - 1).distinct()
            rowLabelIndices.forEach { idx ->
                val yTop = gridTop + (nRows - 1 - idx) * cellH
                canvas5.drawText(String.format(java.util.Locale.US, "%.1f", tfValues[idx] / lDiv), leftMargin, yTop + (cellH / 2f) + 3f, paint)
            }
            val colLabelIndices = listOf(0, nCols / 2, nCols - 1).distinct()
            colLabelIndices.forEach { idx ->
                val xLeft = gridLeft + idx * cellW
                canvas5.drawText(String.format(java.util.Locale.US, "%.1f", sValues[idx] / lDiv), xLeft + (cellW / 2f) - 10f, gridTop + gridH + 15f, paint)
            }

            var yPos5b = gridTop + gridH + 35f
            paint.color = AndroidColor.DKGRAY; paint.textSize = 10f
            canvas5.drawText("↑ Kanat Kalınlığı (tf)", leftMargin, yPos5b, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas5.drawText("Kanat Aralığı (S) →", 555f, yPos5b, paint)
            paint.textAlign = Paint.Align.LEFT
            yPos5b += 25f

            val legendSwatch = 10f
            paint.color = AndroidColor.parseColor("#81C784"); canvas5.drawRect(leftMargin, yPos5b - legendSwatch, leftMargin + legendSwatch, yPos5b, paint)
            paint.color = AndroidColor.DKGRAY; canvas5.drawText("Soğuk", leftMargin + legendSwatch + 6f, yPos5b, paint)
            paint.color = AndroidColor.parseColor("#E63946"); canvas5.drawRect(leftMargin + 80f, yPos5b - legendSwatch, leftMargin + 80f + legendSwatch, yPos5b, paint)
            paint.color = AndroidColor.DKGRAY; canvas5.drawText("Sıcak", leftMargin + 80f + legendSwatch + 6f, yPos5b, paint)
            paint.color = AndroidColor.parseColor("#3A3E42"); canvas5.drawRect(leftMargin + 160f, yPos5b - legendSwatch, leftMargin + 160f + legendSwatch, yPos5b, paint)
            paint.color = AndroidColor.DKGRAY; canvas5.drawText("Geçersiz", leftMargin + 160f + legendSwatch + 6f, yPos5b, paint)

            paint.color = AndroidColor.BLACK
            pdfDocument.finishPage(page5)
        }

        // YENİ: Tasarım Karşılaştırma Tablosu Sayfası - uygulamadaki ComparisonTab ile birebir aynı
        if (hasComparison) {
            val comparisonPageNum = 3 + (if (hasFanCurveGraph) 1 else 0) + (if (hasSensitivity) 1 else 0) + (if (hasHeatmap) 1 else 0) + 1
            val comparisonSectionNum = 6 + (if (hasFanCurveGraph) 1 else 0) + (if (hasSensitivity) 1 else 0) + (if (hasHeatmap) 1 else 0) + 1
            val pageCmpInfo = PdfDocument.PageInfo.Builder(595, 842, comparisonPageNum).create()
            val pageCmp = pdfDocument.startPage(pageCmpInfo)
            val canvasCmp = pageCmp.canvas
            drawHeaderAndFooter(canvasCmp, comparisonPageNum)

            var yPosCmp = 130f
            paint.color = primaryColor; canvasCmp.drawRect(leftMargin, yPosCmp - 15f, 555f, yPosCmp + 5f, paint); paint.color = AndroidColor.BLACK; paint.textSize = 14f; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvasCmp.drawText("$comparisonSectionNum. Tasarım Karşılaştırma", leftMargin + 5f, yPosCmp, paint)
            yPosCmp += 30f

            val entriesCmp = s.comparisonDesigns
            val resUnitCmp = s.resistanceUnit; val weightUnitCmp = s.weightUnit
            fun rDisplayCmp(rv: Double) = if (resUnitCmp == "°F/W") rv * 1.8 else rv
            fun wDisplayCmp(g: Double) = when (weightUnitCmp) { "kg" -> g / 1000.0; "lbs" -> g * 0.00220462; "oz" -> g * 0.035274; else -> g }
            val bestRCmp = entriesCmp.minOfOrNull { it.result.rTotalSystem }
            val bestWCmp = entriesCmp.minOfOrNull { it.result.totalWeightGram }

            val labelColW = 130f
            val colAreaLeft = leftMargin + labelColW
            val colAreaW = 555f - colAreaLeft
            val colW = colAreaW / entriesCmp.size.toFloat().coerceAtLeast(1f)

            fun truncateToWidth(text: String, maxWidth: Float): String {
                if (paint.measureText(text) <= maxWidth) return text
                var t = text
                while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) { t = t.dropLast(1) }
                return "$t…"
            }

            // Sütun başlıkları (tasarım adları)
            paint.color = AndroidColor.BLACK; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textSize = 11f; paint.textAlign = Paint.Align.CENTER
            entriesCmp.forEachIndexed { idx, entry ->
                val cx = colAreaLeft + colW * idx + colW / 2f
                canvasCmp.drawText(truncateToWidth(entry.label, colW - 8f), cx, yPosCmp, paint)
            }
            paint.textAlign = Paint.Align.LEFT
            yPosCmp += 12f
            paint.color = AndroidColor.LTGRAY; paint.strokeWidth = 1f
            canvasCmp.drawLine(leftMargin, yPosCmp, 555f, yPosCmp, paint)
            yPosCmp += 20f

            val rowLabelsCmp = listOf("Malzeme", "Ölçüler (tb/tf/S) mm", "R_total", "Ağırlık", "Verimlilik")
            for (rowIdx in rowLabelsCmp.indices) {
                paint.color = AndroidColor.DKGRAY; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); paint.textSize = 10f; paint.textAlign = Paint.Align.LEFT
                canvasCmp.drawText(rowLabelsCmp[rowIdx], leftMargin, yPosCmp, paint)

                entriesCmp.forEachIndexed { idx, entry ->
                    val r = entry.result
                    val cx = colAreaLeft + colW * idx + colW / 2f
                    paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); paint.textSize = 10f
                    when (rowIdx) {
                        0 -> { paint.color = AndroidColor.BLACK; canvasCmp.drawText(truncateToWidth(entry.materialName, colW - 8f), cx, yPosCmp, paint) }
                        1 -> { paint.color = AndroidColor.BLACK; canvasCmp.drawText(String.format(java.util.Locale.US, "%.1f/%.1f/%.1f", r.usedTb, r.usedTf, r.usedS), cx, yPosCmp, paint) }
                        2 -> {
                            paint.color = if (r.rTotalSystem == bestRCmp) AndroidColor.parseColor("#2E7D32") else AndroidColor.BLACK
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            canvasCmp.drawText(String.format(java.util.Locale.US, "%.3f %s", rDisplayCmp(r.rTotalSystem), resUnitCmp), cx, yPosCmp, paint)
                        }
                        3 -> {
                            paint.color = if (r.totalWeightGram == bestWCmp) AndroidColor.parseColor("#2E7D32") else AndroidColor.BLACK
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            canvasCmp.drawText(String.format(java.util.Locale.US, "%.1f %s", wDisplayCmp(r.totalWeightGram), weightUnitCmp), cx, yPosCmp, paint)
                        }
                        else -> { paint.color = AndroidColor.BLACK; canvasCmp.drawText(String.format(java.util.Locale.US, "%%%.0f", r.finEfficiencyPercent), cx, yPosCmp, paint) }
                    }
                }
                yPosCmp += 24f
            }
            paint.textAlign = Paint.Align.LEFT

            yPosCmp += 15f
            paint.color = AndroidColor.parseColor("#455A64"); paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); paint.textSize = 9f
            canvasCmp.drawText("Not: Yeşil renkli değerler, eklenen tasarımlar arasında en düşük dirence veya en düşük ağırlığa sahip olanı gösterir.", leftMargin, yPosCmp, paint)

            // GÜVENLİK: Paint durumunu bir sonrakine sızdırmamak için sıfırla
            paint.color = AndroidColor.BLACK; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); paint.textSize = 11f; paint.textAlign = Paint.Align.LEFT
            pdfDocument.finishPage(pageCmp)
        }

        try {
            // DÜZELTİLDİ: Dosya adı artık "ProjeAdı_günayyıl_saat.pdf" formatında (gün-ay-yıl sırasıyla, tiresiz)
            val fileTimestamp = java.text.SimpleDateFormat("ddMMyyyy_HHmmss", java.util.Locale.US).format(java.util.Date())
            val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "${s.projectName.replace(" ", "_")}_${fileTimestamp}.pdf"); put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) { context.contentResolver.openOutputStream(uri)?.use { outputStream -> pdfDocument.writeTo(outputStream) }; Toast.makeText(context, "Mükemmel! $totalPages Sayfalık Rapor 'İndirilenler' klasörüne kaydedildi!", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) { Toast.makeText(context, "PDF Kayıt Hatası: ${e.message}", Toast.LENGTH_LONG).show() } finally { pdfDocument.close() }
    }
}
