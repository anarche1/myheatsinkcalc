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

// === HeatsinkSolver.kt: Termal cozum motoru (fizik hesaplamalari) ===

// ==========================================
// TERMAL ÇÖZÜM MOTORU (SOLVER ENGINE)
// ==========================================
object HeatsinkSolver {

    private fun interpolatePressure(curve: List<Pair<Double, Double>>, qTest: Double): Double {
        if (curve.isEmpty()) return 0.0
        if (qTest <= curve.first().first) return curve.first().second
        if (qTest >= curve.last().first) return 0.0

        for (i in 0 until curve.size - 1) {
            val p1 = curve[i]
            val p2 = curve[i+1]
            if (qTest >= p1.first && qTest <= p2.first) {
                val ratio = (qTest - p1.first) / (p2.first - p1.first)
                return p1.second + ratio * (p2.second - p1.second)
            }
        }
        return 0.0
    }

    // DUZELTILDI (Mimari: DRY): Kanal basinc kaybi / Reynolds / surtunme faktoru hesabi eskiden
    // hem "Fan Egrisi" bisection donguisu icinde hem de "Sabit Hiz/Debi" dalinda neredeyse birebir
    // ayni sekilde iki kez yaziliyordu. Bu, ileride biri guncellenip digerinin unutulmasi riski
    // tasiyordu. Artik tek bir fonksiyonda birlestirildi.
    private fun computeChannelPressureDrop(
        v_in: Double, W_mm: Double, hf_mm: Double, S_mm: Double, L_mm: Double,
        actualFinCount: Int, airDensity: Double
    ): Double {
        val S_m = S_mm / 1000.0
        val hf_m = hf_mm / 1000.0
        val L_m = L_mm / 1000.0
        if (actualFinCount <= 0 || S_m <= 0 || hf_m <= 0) return 0.0

        val approachAreaM2 = (W_mm * hf_mm) / 1_000_000.0
        val freeFlowArea = actualFinCount * S_m * hf_m
        val v_channel = if (freeFlowArea > 0) v_in * (approachAreaM2 / freeFlowArea) else v_in
        val Dh = (4.0 * (S_m * hf_m)) / (2.0 * (S_m + hf_m))
        val dynamicViscosity = 1.81e-5
        val Re = (airDensity * v_channel * Dh) / dynamicViscosity
        val f = if (Re < 2300) (24.0 / Re.coerceAtLeast(1.0)) * 2.0 else 0.316 * Math.pow(Re, -0.25)
        return (f * (L_m / Dh) + 1.0) * (airDensity * Math.pow(v_channel, 2.0) / 2.0)
    }

    fun calculate(
        W_mm: Double, L_mm: Double, tb_mm: Double, tf_mm: Double, S_mm: Double, hf_mm: Double,
        k_heatsink: Double, materialName: String,
        ambientTemp: Double, flowType: String, flowParam: Double,
        altitude_m: Double = 0.0, emissivity: Double = 0.85,
        isTunnelEnabled: Boolean = false, chassisCw: Double = 100.0, chassisCh: Double = 100.0, fanMethod: String = "",
        heatSources: List<HeatSourceData>,
        orientationIndex: Int = 0,
        isEnclosedChassis: Boolean = false,
        fanCurve: List<Pair<Double, Double>>? = null,
        // YENİ EKLENEN KISIM: Kalibrasyon parametresi fizik motoruna tanıtıldı
        calibrationFactor: Double = 1.0,
        // YENİ: Custom malzeme icin kullanici tanimli yogunluk (g/cm3) ve ozgul isi (J/kgK)
        customDensityGcm3: Double = 2.70,
        customSpecificHeatJkgK: Double = 900.0
    ): SolverResult {

        val maxFinCount = floor((W_mm + S_mm) / (tf_mm + S_mm)).toInt()
        val actualFinCount = if (maxFinCount > 0) maxFinCount else 0

        val baseVolumeMm3 = W_mm * L_mm * tb_mm
        val oneFinVolumeMm3 = tf_mm * L_mm * hf_mm
        val totalVolumeCm3 = (baseVolumeMm3 + (oneFinVolumeMm3 * actualFinCount)) / 1000.0

        val density = when {
            materialName.contains("Custom") -> customDensityGcm3
            materialName.contains("Cu") || materialName.contains("Bakır") -> 8.96
            materialName.contains("Steel") || materialName.contains("Çelik") -> 7.85
            else -> 2.70
        }
        val totalWeightGram = totalVolumeCm3 * density

        val baseAreaM2 = (W_mm * L_mm) / 1_000_000.0
        val finAreaM2 = (2 * hf_mm * L_mm * actualFinCount) / 1_000_000.0
        val finFootprintM2 = (tf_mm * L_mm * actualFinCount) / 1_000_000.0

        val airDensity = 1.225 * Math.pow((1.0 - (2.25577e-5 * altitude_m)), 4.2559)
        val densityRatio = airDensity / 1.225

        var bypassFactor = 1.0
        var pressureDropPa = 0.0
        var finalOperatingFlow = 0.0
        var systemChoked = false

        val h_conv_base = if (flowType.contains("Doğal")) {
            val orientationMultiplier = when (orientationIndex) { 0 -> 1.0; 1 -> 0.7; 2 -> 1.3; 3 -> 0.5; else -> 1.0 }
            val enclosureMultiplier = if (isEnclosedChassis) 0.6 else 1.0

            val L_m = L_mm / 1000.0
            val optimumS_mm = 7.0 * Math.pow(L_m.coerceAtLeast(0.01), 0.25)
            val actualS_mm = S_mm.coerceAtLeast(0.1)

            val chokingFactor = if (actualS_mm < optimumS_mm) {
                systemChoked = true
                Math.pow(actualS_mm / optimumS_mm, 1.5).coerceIn(0.1, 1.0)
            } else {
                1.0
            }

            7.5 * orientationMultiplier * enclosureMultiplier * chokingFactor
        } else {
            var v_in = 0.1
            if (fanCurve != null && fanCurve.size > 1) {
                var qMin = 0.0
                var qMax = fanCurve.last().first
                var bestVIn = 0.1
                var bestDP = 0.0
                var bestBypass = 1.0
                var qTest = 0.0

                for (i in 0..20) {
                    qTest = (qMin + qMax) / 2.0
                    val fanP = interpolatePressure(fanCurve, qTest)
                    val approachAreaM2 = (W_mm * hf_mm) / 1_000_000.0
                    var testVIn = if (approachAreaM2 > 0) qTest / approachAreaM2 else 0.1
                    var bypass = 1.0

                    if (isTunnelEnabled) {
                        val hsCrossArea = W_mm * (tb_mm + hf_mm)
                        val tunnelArea = chassisCw * chassisCh
                        if (tunnelArea > hsCrossArea && tunnelArea > 0) {
                            bypass = (hsCrossArea / tunnelArea).coerceIn(0.1, 1.0)
                            testVIn *= bypass
                        }
                    }

                    // DUZELTILDI (DRY): Ortak hesap artik computeChannelPressureDrop icinde.
                    val sysP = computeChannelPressureDrop(testVIn, W_mm, hf_mm, S_mm, L_mm, actualFinCount, airDensity)

                    bestVIn = testVIn; bestDP = sysP; bestBypass = bypass

                    if (sysP > fanP) qMax = qTest else qMin = qTest
                }
                v_in = bestVIn; pressureDropPa = bestDP; bypassFactor = bestBypass; finalOperatingFlow = qTest
            } else {
                v_in = if (fanMethod.contains("Debisi")) {
                    val q_m3s = flowParam
                    val approachAreaM2 = (W_mm * hf_mm) / 1_000_000.0
                    finalOperatingFlow = q_m3s
                    if (approachAreaM2 > 0) q_m3s / approachAreaM2 else 0.1
                } else {
                    val approachAreaM2 = (W_mm * hf_mm) / 1_000_000.0
                    finalOperatingFlow = flowParam.coerceAtLeast(0.1) * approachAreaM2
                    flowParam.coerceAtLeast(0.1)
                }

                if (isTunnelEnabled) {
                    val hsCrossArea = W_mm * (tb_mm + hf_mm)
                    val tunnelArea = chassisCw * chassisCh
                    if (tunnelArea > hsCrossArea && tunnelArea > 0) {
                        bypassFactor = (hsCrossArea / tunnelArea).coerceIn(0.1, 1.0)
                        v_in *= bypassFactor
                    }
                }

                // DUZELTILDI (DRY): Ortak hesap artik computeChannelPressureDrop icinde.
                pressureDropPa = computeChannelPressureDrop(v_in, W_mm, hf_mm, S_mm, L_mm, actualFinCount, airDensity)
            }
            10.0 + (12.0 * Math.sqrt(v_in))
        }

        val W_m = W_mm / 1000.0
        val L_m = L_mm / 1000.0
        val hf_m = hf_mm / 1000.0

        val envelopeAreaM2 = (W_m * L_m) + (2.0 * hf_m * L_m) + (2.0 * hf_m * W_m)
        val totalPhysicalAreaM2 = baseAreaM2 + finAreaM2

        val viewFactor = if (totalPhysicalAreaM2 > 0) (envelopeAreaM2 / totalPhysicalAreaM2).coerceIn(0.01, 1.0) else 1.0
        val effectiveEmissivity = emissivity * viewFactor

        // GÜNCELLENDİ: Taşınım katsayısı kullanıcının girdiği kalibrasyon çarpanı ile çarpılıyor!
        val h_conv = (h_conv_base * Math.sqrt(densityRatio)) * calibrationFactor

        // DUZELTILDI (Hesaplama Dogrulugu): Toplam guc, radyasyon iterasyonunda kullanilabilmesi
        // icin daha erken hesaplaniyor (eskiden daha asagida, chip donguisunden hemen once hesaplaniyordu).
        val totalPowerW = heatSources.sumOf { it.watt.replace(",", ".").toDoubleOrNull() ?: 0.0 }

        val sigma = 5.67e-8
        val tAmbK = ambientTemp + 273.15
        val tb_m = tb_mm / 1000.0
        val rCondBase = tb_m / (k_heatsink * baseAreaM2)
        val tf_m = tf_mm / 1000.0

        // ==========================================
        // DUZELTILDI (Hesaplama Dogrulugu): Radyasyon katsayisi artik sabit bir tahmin yerine
        // birkac adimda kendini duzelten (fixed-point iterasyon) bir yontemle cozuluyor.
        // Eskiden yuzey sicakligi HER ZAMAN "Ortam + 15°C" olarak sabit varsayiliyordu; bu, cok
        // dusuk veya cok yuksek guclu tasarimlarda h_rad'i (ve dolayisiyla toplam direnci) belirgin
        // sekilde yanlis hesaplatiyordu. Simdi tahmini yuzey sicakligi, bir onceki iterasyonun
        // hesapladigi gercek tasinim direncine (rConv) gore guncellenip 4 kez tekrarlaniyor.
        // ==========================================
        var h_total = h_conv
        var rConv = 0.0
        var finEfficiency = 1.0
        var effectiveAreaM2 = baseAreaM2 + finAreaM2
        var tSurfaceKGuess = tAmbK + 15.0

        repeat(4) {
            val h_rad = effectiveEmissivity * sigma * (Math.pow(tSurfaceKGuess, 2.0) + Math.pow(tAmbK, 2.0)) * (tSurfaceKGuess + tAmbK)
            h_total = h_conv + h_rad

            val m = Math.sqrt((2.0 * h_total) / (k_heatsink * tf_m))
            // DUZELTILDI (Hesaplama Dogrulugu): Kanat verimliligi artik "duzeltilmis kanat uzunlugu"
            // (Lc) ile hesaplaniyor. Standart kanat teorisinde (adyabatik uc yaklasimi), kanat ucundan
            // olan tasinim kaybini yaklasik olarak modellemek icin gercek yukseklik (hf) yerine
            // Lc = hf + (tf/2) kullanilir. Bu, ozellikle kalin kanatlarda verimliligi biraz daha
            // gercekci (daha dusuk) gosterir.
            val Lc = hf_m + (tf_m / 2.0)
            val mH = m * Lc
            finEfficiency = if (mH > 0) Math.tanh(mH) / mH else 1.0

            effectiveAreaM2 = (baseAreaM2 - finFootprintM2) + (finEfficiency * finAreaM2)
            rConv = 1.0 / (h_total * effectiveAreaM2)

            // Bir sonraki iterasyon icin yuzey sicakligini, o anki direnc tahminine gore guncelliyoruz.
            val estimatedSurfaceDeltaT = (totalPowerW * rConv).coerceIn(0.5, 200.0)
            tSurfaceKGuess = tAmbK + estimatedSurfaceDeltaT
        }

        var totalTimR = 0.0
        var totalSpreadR = 0.0
        val chipResults = mutableListOf<ChipResultData>()

        val systemBaseTemp = ambientTemp + (totalPowerW * (rConv + rCondBase))

        heatSources.forEach { src ->
            val wS_m = (src.wS.replace(",", ".").toDoubleOrNull() ?: 1.0) / 1000.0
            val lS_m = (src.lS.replace(",", ".").toDoubleOrNull() ?: 1.0) / 1000.0
            val pW = src.watt.replace(",", ".").toDoubleOrNull() ?: 0.0

            val chipAreaM2 = wS_m * lS_m
            val chipAreaCm2 = chipAreaM2 * 10_000.0
            val heatFlux = if (chipAreaCm2 > 0) pW / chipAreaCm2 else 0.0
            val isHotspot = heatFlux > 5.0

            val rTim = if (src.hasTim) {
                val tTim_m = (src.timThick.replace(",", ".").toDoubleOrNull() ?: 0.2) / 1000.0
                val kTim = src.timK.replace(",", ".").toDoubleOrNull() ?: 1.0
                tTim_m / (kTim * chipAreaM2)
            } else {
                0.005
            }
            totalTimR += rTim

            // Datasheet Rjc Kontrolü (Daha önce eklemiştik)
            val rSpread = if (src.useCustomRjc) {
                src.customRjcVal.replace(",", ".").toDoubleOrNull() ?: 0.0
            } else if (chipAreaM2 < baseAreaM2) {
                (1.0 - Math.sqrt(chipAreaM2 / baseAreaM2)) / (k_heatsink * Math.sqrt(chipAreaM2))
            } else 0.0
            totalSpreadR += rSpread

            val tJunction = systemBaseTemp + (pW * (rTim + rSpread))
            chipResults.add(ChipResultData(sourceInfo = src, tempJunction = tJunction, heatFlux = heatFlux, isHotspot = isHotspot))
        }

        val avgTimR = if (heatSources.isNotEmpty()) totalTimR / heatSources.size else 0.0
        val avgSpreadR = if (heatSources.isNotEmpty()) totalSpreadR / heatSources.size else 0.0
        val rTotalSystem = avgTimR + avgSpreadR + rCondBase + rConv

        val specificHeatCp = when {
            materialName.contains("Custom") -> customSpecificHeatJkgK
            materialName.contains("Cu") || materialName.contains("Bakır") -> 385.0
            materialName.contains("Steel") || materialName.contains("Çelik") -> 490.0
            else -> 900.0
        }

        val massKg = totalWeightGram / 1000.0
        val thermalCapacity = massKg * specificHeatCp
        val calculatedTimeConstant = rTotalSystem * thermalCapacity

        return SolverResult(
            usedTb = tb_mm, usedTf = tf_mm, usedS = S_mm,
            totalVolumeCm3 = totalVolumeCm3, totalWeightGram = totalWeightGram, finEfficiencyPercent = finEfficiency * 100.0,
            rTimAvg = avgTimR, rSpreadAvg = avgSpreadR, rCondBase = rCondBase, rConv = rConv, rTotalSystem = rTotalSystem,
            pressureDropPa = pressureDropPa, bypassFactor = bypassFactor,
            operatingFlowM3s = finalOperatingFlow,
            isChoked = systemChoked,
            timeConstantSeconds = calculatedTimeConstant,
            viewFactor = viewFactor,
            chipResults = chipResults
        )
    }
}
