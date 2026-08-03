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
    // DÜZELTİLDİ (Fizik - Adım 5): Kanal içi gerçek hız hesabı artık ayrı, tek bir fonksiyonda -
    // hem basınç kaybı hem de taşınım katsayısı (h_conv) hesabı aynı gerçek hızı kullanmak zorunda,
    // aksi halde S (kanat aralığı) değiştirildiğinde h_conv değişmiyor gibi bir tutarsızlık oluşuyordu.
    private fun computeChannelVelocity(v_in: Double, W_mm: Double, hf_mm: Double, S_mm: Double, actualFinCount: Int): Double {
        val S_m = S_mm / 1000.0
        val hf_m = hf_mm / 1000.0
        if (actualFinCount <= 0 || S_m <= 0 || hf_m <= 0) return v_in
        val approachAreaM2 = (W_mm * hf_mm) / 1_000_000.0
        val freeFlowArea = actualFinCount * S_m * hf_m
        return if (freeFlowArea > 0) v_in * (approachAreaM2 / freeFlowArea) else v_in
    }

    // YENİ (Fizik - Adım 10): Hava viskozitesi artık tek bir paylaşılan sabit - basınç kaybı VE
    // zorlanmış taşınım katsayısı hesabı aynı Reynolds sayısını kullanmak zorunda, aksi halde
    // aynı akış için iki ayrı yerde bağımsız hesaplanan Re birbirinden sapabilir.
    private const val AIR_DYNAMIC_VISCOSITY = 1.81e-5

    private fun computeChannelPressureDrop(
        v_in: Double, W_mm: Double, hf_mm: Double, S_mm: Double, L_mm: Double,
        actualFinCount: Int, airDensity: Double
    ): Double {
        val S_m = S_mm / 1000.0
        val hf_m = hf_mm / 1000.0
        val L_m = L_mm / 1000.0
        if (actualFinCount <= 0 || S_m <= 0 || hf_m <= 0) return 0.0

        val v_channel = computeChannelVelocity(v_in, W_mm, hf_mm, S_mm, actualFinCount)
        val Dh = (4.0 * (S_m * hf_m)) / (2.0 * (S_m + hf_m))
        val Re = (airDensity * v_channel * Dh) / AIR_DYNAMIC_VISCOSITY
        // DÜZELTİLDİ (Fizik - Adım 4): Darcy-Weisbach formunda kullanılan sürtünme faktörü, Fanning
        // katsayısının (24/Re, paralel plaka için standart laminer değer) 4 katı olmalı (f_Darcy =
        // 4 * f_Fanning) - önceden yanlışlıkla 2 ile çarpılıyordu, bu da laminer rejimde (küçük
        // fanlı tipik hobi soğutucuları) basınç kaybını gerçekte olması gerekenin yaklaşık yarısı
        // olarak hesaplatıyordu.
        val f = if (Re < 2300) (24.0 / Re.coerceAtLeast(1.0)) * 4.0 else 0.316 * Math.pow(Re, -0.25)
        return (f * (L_m / Dh) + 1.0) * (airDensity * Math.pow(v_channel, 2.0) / 2.0)
    }

    // YENİ (Fizik - Adım 3): Doğal taşınım için ΔT'ye bağlı hava özellikleri (Elenbaas korelasyonu
    // burada kullanılıyor). mu: Sutherland yasası (standart, sıcaklık bağımlı dinamik viskozite).
    // rhoFilm: mevcut irtifa/ISA yoğunluk modelinden, film sıcaklığına izobarik ideal gaz düzeltmesi.
    private data class AirPropsResult(val nu: Double, val alpha: Double, val kAir: Double, val beta: Double)

    private fun airPropertiesAtDeltaT(deltaT: Double, ambientAirDensity: Double, ambientTempK: Double): AirPropsResult {
        val dT = deltaT.coerceAtLeast(0.5)
        val filmTempK = ambientTempK + (dT / 2.0)
        val mu0 = 1.716e-5
        val T0 = 273.15
        val cSutherland = 110.4
        val mu = mu0 * ((T0 + cSutherland) / (filmTempK + cSutherland)) * Math.pow(filmTempK / T0, 1.5)
        val rhoFilm = (ambientAirDensity * (ambientTempK / filmTempK)).coerceAtLeast(1e-6)
        val cpAir = 1007.0
        val prandtl = 0.71
        val kAir = mu * cpAir / prandtl
        val nu = (mu / rhoFilm).coerceAtLeast(1e-8)
        val alpha = nu / prandtl
        val beta = 1.0 / filmTempK
        return AirPropsResult(nu, alpha, kAir, beta)
    }

    // YENİ (Fizik - Adım 3): Doğal taşınım h katsayısı - Elenbaas / Bar-Cohen & Rosenow (1984)
    // dikey paralel plaka (kanat kanalı) korelasyonu. ΔT, S (kanat aralığı) ve L'ye (kanal boyu)
    // gerçekten bağlı; sabit bir katsayı değil.
    private fun computeNaturalConvH(
        deltaT: Double, S_mm: Double, L_mm: Double,
        orientationMultiplier: Double, enclosureMultiplier: Double,
        airDensity: Double, ambientTempK: Double
    ): Double {
        val S_m = S_mm.coerceAtLeast(0.1) / 1000.0
        val L_m = L_mm.coerceAtLeast(1.0) / 1000.0
        val dT = deltaT.coerceAtLeast(0.5)
        val air = airPropertiesAtDeltaT(dT, airDensity, ambientTempK)

        val Ra_S = (9.81 * air.beta * dT * Math.pow(S_m, 3.0)) / (air.nu * air.alpha)
        val El = (Ra_S * (S_m / L_m)).coerceAtLeast(1e-6)
        val Nu_S = Math.pow((576.0 / (El * El)) + (2.873 / Math.sqrt(El)), -0.5)
        val hRaw = Nu_S * air.kAir / S_m

        return hRaw * orientationMultiplier * enclosureMultiplier
    }

    // YENİ (Fizik - Adım 6): Yayılma direnci artık taban kalınlığını (tb) ve arka yüzdeki gerçek
    // taşınım/radyasyon katsayısını (h_total, Biot sayısı üzerinden) hesaba katıyor. Yovanovich/Lee/
    // Song (1995) dairesel kaynak modelinin tek-terimli kapalı form yaklaşımı kullanılıyor (dikdörtgen
    // çip için eşdeğer dairesel alan). tb çok büyükken (yarı-sonsuz taban) bu formül otomatik olarak
    // eski sabit formüle ((1-ε)/(k·sqrt(Achip))) yakınsıyor - bu, sayısal olarak doğrulandı.
    private fun computeSpreadingResistance(
        chipAreaM2: Double, baseAreaM2: Double, tb_m: Double, k_heatsink: Double, h_total: Double
    ): Double {
        if (chipAreaM2 <= 0.0 || baseAreaM2 <= 0.0 || chipAreaM2 >= baseAreaM2) return 0.0
        val eps = Math.sqrt(chipAreaM2 / baseAreaM2)
        val b = Math.sqrt(baseAreaM2 / Math.PI).coerceAtLeast(1e-6)
        val tau = tb_m / b
        val Bi = (h_total * b / k_heatsink).coerceAtLeast(1e-6)
        val lambdaC = Math.PI + 1.0 / (Math.sqrt(Math.PI) * eps)
        val tanhTerm = Math.tanh(lambdaC * tau)
        val phiC = (tanhTerm + lambdaC / Bi) / (1.0 + (lambdaC / Bi) * tanhTerm)
        return (1.0 - eps) * phiC / (k_heatsink * Math.sqrt(chipAreaM2))
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

        // DÜZELTİLDİ (Fizik - Adım 7): k_heatsink sıfır/negatif girilirse (örn. custom malzeme
        // k alanı boş/0 kalırsa) rCondBase, kanat verimliliği ve yayılma direnci hesaplarında
        // sıfıra bölme/Infinity riski oluşuyordu. Güvenli bir minimuma çekiliyor.
        val k_heatsink = k_heatsink.coerceAtLeast(0.01)

        // DÜZELTİLDİ (Fizik - Adım 7): tf_mm + S_mm toplamı sıfır/negatifse (örn. ikisi de 0
        // girilirse) bölme NaN/Infinity üretip actualFinCount'u ve tüm hesabı bozuyordu.
        val finPitchDenominator = (tf_mm + S_mm).coerceAtLeast(0.001)
        val maxFinCount = floor((W_mm + S_mm) / finPitchDenominator).toInt()
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

        // DÜZELTİLDİ (Fizik - Adım 7): W veya L sıfır/negatif girilirse taban alanı sıfır/negatif
        // olup rCondBase, rConv gibi hesaplarda sıfıra bölme/Infinity riski oluşuyordu.
        val baseAreaM2 = ((W_mm * L_mm) / 1_000_000.0).coerceAtLeast(1e-8)
        val finAreaM2 = (2 * hf_mm * L_mm * actualFinCount) / 1_000_000.0
        val finFootprintM2 = (tf_mm * L_mm * actualFinCount) / 1_000_000.0

        // DÜZELTİLDİ (Fizik - Adım 7): ~44331m üstü irtifada (1 - 2.25577e-5*altitude_m) negatif
        // oluyor; negatif tabanın kesirli üssü Math.pow ile NaN dönüyordu ve bu NaN, hava yoğunluğu
        // üzerinden tüm sonucu (doğal taşınım, basınç kaybı, her şey) zehirliyordu.
        val isaBase = (1.0 - (2.25577e-5 * altitude_m)).coerceAtLeast(1e-6)
        val airDensity = 1.225 * Math.pow(isaBase, 4.2559)
        val densityRatio = airDensity / 1.225

        var bypassFactor = 1.0
        var pressureDropPa = 0.0
        var finalOperatingFlow = 0.0
        var systemChoked = false

        val isNaturalConvection = flowType.contains("Doğal")
        val orientationMultiplier = when (orientationIndex) { 0 -> 1.0; 1 -> 0.7; 2 -> 1.3; 3 -> 0.5; else -> 1.0 }
        val enclosureMultiplier = if (isEnclosedChassis) 0.6 else 1.0

        val h_conv_base = if (isNaturalConvection) {
            // DÜZELTİLDİ (Fizik - Adım 3): Doğal taşınım katsayısı artık ΔT'den bağımsız sabit
            // bir sayı değil. Gerçek değer Rayleigh sayısına bağlı olduğundan (Elenbaas korelasyonu),
            // burada 0.0 yer tutucu bırakılıyor; asıl hesap aşağıdaki fixed-point iterasyonunun
            // içinde computeNaturalConvH() ile her adımda ΔT'nin o anki tahminine göre yenileniyor.
            0.0
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
            // DÜZELTİLDİ (Fizik - Adım 5): h_conv artık yaklaşım hızı (v_in) yerine kanatlar
            // arasındaki gerçek kanal hızını (v_channel) kullanıyor. Böylece S (kanat aralığı)
            // değiştirildiğinde h_conv de gerçekçi şekilde değişiyor (önce S'in etkisi h_conv'a
            // hemen hemen hiç yansımıyordu, sadece basınç kaybına yansıyordu).
            val v_channel_forH = computeChannelVelocity(v_in, W_mm, hf_mm, S_mm, actualFinCount)

            // DÜZELTİLDİ (Fizik - Adım 10): Eski "10 + 12*sqrt(v)" formülünün hiçbir Reynolds/Nusselt
            // temeli yoktu (yapısal olarak insan derisi için geliştirilmiş rüzgar-soğutması/wind-chill
            // formülüne benziyordu). Artık basınç kaybı hesabıyla (Re<2300 eşiği) AYNI laminer/
            // türbülanslı rejim ayrımı ve AYNI Reynolds sayısı kullanılıyor: laminer için paralel
            // plaka kanalının standart tam-gelişmiş Nusselt sayısı (Nu=7.54), türbülanslı için
            // Dittus-Boelter (Nu=0.023*Re^0.8*Pr^0.4, ısıtma durumu).
            val S_m_forH = S_mm.coerceAtLeast(0.1) / 1000.0
            val hf_m_forH = hf_mm / 1000.0
            val Dh_forH = ((4.0 * (S_m_forH * hf_m_forH)) / (2.0 * (S_m_forH + hf_m_forH))).coerceAtLeast(1e-6)
            val Re_forH = (airDensity * v_channel_forH * Dh_forH) / AIR_DYNAMIC_VISCOSITY
            val prandtlAir = 0.71
            val kAirForH = AIR_DYNAMIC_VISCOSITY * 1007.0 / prandtlAir
            val Nu_forH_fullyDeveloped = if (Re_forH < 2300.0) 7.54 else 0.023 * Math.pow(Re_forH.coerceAtLeast(1.0), 0.8) * Math.pow(prandtlAir, 0.4)
            // DÜZELTİLDİ (Fizik - Giriş Bölgesi Etkisi, literatür doğrulandı): Önceki geçici Graetz
            // sayısı tabanlı "en fazla %50" yaklaşımı, Chrome üzerinden bulunup denklemleri tek tek
            // doğrulanan gerçek kaynakla değiştirildi: Teertstra, P., Yovanovich, M.M., Culham, J.R.,
            // Lemczyk, T.F., "Analytical Forced Convection Modeling of Plate Fin Heat Sinks,"
            // 15th IEEE SEMI-THERM Symposium, 1999. Makale, kanat aralığı b(=S) temel uzunluk olacak
            // şekilde tam-gelişmiş ve gelişmekte olan (developing) akış asimptotlarını Churchill-Usagi
            // tipi kompozit bir formülle birleştiriyor; kombinasyon katsayısı n=3 (yazarların sayısal
            // simülasyonla belirlediği, model-veri RMS farkı %2.1) ve gerçek bir kanatlı ısı emici
            // prototipiyle deneysel olarak da doğrulanmış (RMS %2.1, maksimum %6 fark). Model sadece
            // LAMİNER rejimde geçerli (yazarların doğruladığı aralık: 0.26 <= Re_b* <= 175);
            // türbülanslı dal (Dittus-Boelter) aynen korunuyor çünkü makale türbülansı kapsamıyor ve
            // türbülanslı akış zaten çok daha kısa mesafede gelişir.
            val Nu_forH = if (Re_forH < 2300.0) {
                val Re_b = (airDensity * v_channel_forH * S_m_forH) / AIR_DYNAMIC_VISCOSITY
                val L_m_forEntry = L_mm.coerceAtLeast(1.0) / 1000.0
                val Re_bStar = (Re_b * (S_m_forH / L_m_forEntry)).coerceAtLeast(1e-6)
                val n = 3.0
                val Nu_fd = 0.5 * Re_bStar * prandtlAir
                val Nu_dev = 0.664 * Math.sqrt(Re_bStar) * Math.pow(prandtlAir, 1.0 / 3.0) * Math.pow(1.0 + 3.65 / Math.sqrt(Re_bStar), 1.0 / 3.0)
                Math.pow(Math.pow(Nu_fd, -n) + Math.pow(Nu_dev, -n), -1.0 / n)
            } else {
                Nu_forH_fullyDeveloped
            }
            // Not: laminer rejimde Nu_forH artık b(=S) tabanlı olduğu için h hesabında da uzunluk
            // ölçeği Dh yerine S kullanılıyor (makalenin kendi Nu tanımıyla tutarlı olması için).
            val lengthScaleForH = if (Re_forH < 2300.0) S_m_forH else Dh_forH
            Nu_forH * kAirForH / lengthScaleForH
        }

        val W_m = W_mm / 1000.0
        val L_m = L_mm / 1000.0
        val hf_m = hf_mm / 1000.0

        val envelopeAreaM2 = (W_m * L_m) + (2.0 * hf_m * L_m) + (2.0 * hf_m * W_m)
        val totalPhysicalAreaM2 = baseAreaM2 + finAreaM2

        val viewFactor = if (totalPhysicalAreaM2 > 0) (envelopeAreaM2 / totalPhysicalAreaM2).coerceIn(0.01, 1.0) else 1.0
        val effectiveEmissivity = emissivity * viewFactor

        // GÜNCELLENDİ: Zorlanmış taşınımda kalibrasyon + irtifa düzeltmesi burada uygulanıyor.
        // DÜZELTİLDİ (Fizik - Adım 3): Doğal taşınımda bu satır artık kullanılmıyor; h_conv, ΔT'ye
        // bağlı olduğu için aşağıdaki fixed-point iterasyonunun içinde her adımda yeniden hesaplanıyor
        // (bkz. h_conv_current). İrtifa etkisi de artık Elenbaas formülünün içine (hava yoğunluğu
        // üzerinden) gömülü olduğundan, doğal taşınım için ayrıca sqrt(densityRatio) uygulanmıyor -
        // aksi halde irtifa etkisi iki kez sayılırdı.
        val h_conv = if (isNaturalConvection) 0.0 else (h_conv_base * Math.sqrt(densityRatio)) * calibrationFactor

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
        var h_conv_current = h_conv
        var rConv = 0.0
        var finEfficiency = 1.0
        var effectiveAreaM2 = baseAreaM2 + finAreaM2
        var tSurfaceKGuess = tAmbK + 15.0

        // DÜZELTİLDİ: Eskiden bu döngü ne olursa olsun tam 4 kez dönüyordu, hiçbir yakınsama
        // kontrolü yoktu. Bu tip artan-h/azalan-ΔT geri beslemeli (fixed-point) sistemler sönümlü
        // salınımla yakınsar (bir tur ΔT'yi fazla tahmin eder, sonraki tur eksik tahmin eder vs.) -
        // 4 turda hala birkaç yüzde hata kalabiliyordu. Şimdi: (1) her adımda bir önceki tahmin ile
        // ham yeni tahmin arasına %50 gevşetme (under-relaxation) uygulanarak salınım bastırılıyor,
        // (2) ΔT değişimi 0.01K altına düşünce döngü erken sonlanıyor, (3) güvenlik payı için üst
        // sınır 30 iterasyona çıkarıldı (normalde çok daha az turda yakınsar).
        var prevDeltaT = 15.0
        for (iter in 0 until 30) {
            // DÜZELTİLDİ (Fizik - Adım 3): Doğal taşınımda h_conv artık sabit değil; her iterasyonda
            // o anki yüzey sıcaklığı tahminine (ΔT) göre Elenbaas korelasyonuyla yeniden hesaplanıyor.
            if (isNaturalConvection) {
                val deltaTGuess = (tSurfaceKGuess - tAmbK).coerceAtLeast(0.5)
                val hNatural = computeNaturalConvH(deltaTGuess, S_mm, L_mm, orientationMultiplier, enclosureMultiplier, airDensity, tAmbK)
                h_conv_current = hNatural * calibrationFactor
            }

            val h_rad = effectiveEmissivity * sigma * (Math.pow(tSurfaceKGuess, 2.0) + Math.pow(tAmbK, 2.0)) * (tSurfaceKGuess + tAmbK)
            h_total = h_conv_current + h_rad

            val m = Math.sqrt((2.0 * h_total) / (k_heatsink * tf_m))
            // DUZELTILDI (Hesaplama Dogrulugu): Kanat verimliligi artik "duzeltilmis kanat uzunlugu"
            // (Lc) ile hesaplaniyor. Standart kanat teorisinde (adyabatik uc yaklasimi), kanat ucundan
            // olan tasinim kaybini yaklasik olarak modellemek icin gercek yukseklik (hf) yerine
            // Lc = hf + (tf/2) kullanilir. Bu, ozellikle kalin kanatlarda verimliligi biraz daha
            // gercekci (daha dusuk) gosterir.
            val Lc = hf_m + (tf_m / 2.0)
            val mH = m * Lc
            finEfficiency = if (mH > 0) Math.tanh(mH) / mH else 1.0

            // DÜZELTİLDİ (Fizik - Adım 7): Kanat ayak izi (finFootprint) taban alanından büyükse
            // (tutarsız/aşırı girdi kombinasyonu) effectiveAreaM2 negatif/sıfır olup rConv'da
            // Infinity/negatif direnç riski oluşuyordu.
            effectiveAreaM2 = ((baseAreaM2 - finFootprintM2) + (finEfficiency * finAreaM2)).coerceAtLeast(1e-8)
            rConv = 1.0 / (h_total * effectiveAreaM2)

            // ΔT büyüdükçe h_total da arttığından (Elenbaas + ışınım ikisi de artan fonksiyon) bu
            // iterasyon kendiliğinden sönümlenen (self-limiting) bir sistemdir; gevşetme sadece
            // salınımı azaltıp daha az turda yakınsamasını sağlıyor, üst sınır yok.
            val rawDeltaT = (totalPowerW * rConv).coerceAtLeast(0.5)
            val estimatedSurfaceDeltaT = prevDeltaT + 0.5 * (rawDeltaT - prevDeltaT)
            tSurfaceKGuess = tAmbK + estimatedSurfaceDeltaT

            if (Math.abs(estimatedSurfaceDeltaT - prevDeltaT) < 0.01) {
                prevDeltaT = estimatedSurfaceDeltaT
                break
            }
            prevDeltaT = estimatedSurfaceDeltaT
        }

        // DÜZELTİLDİ (Fizik - Adım 3): "isChoked" artık h'yi yapay olarak düşüren bir çarpan
        // değil, sadece bilgi amaçlı bir bayrak. Kanat aralığı (S), o çalışma noktasındaki gerçek
        // ΔT için optimum aralıktan (Bar-Cohen & Rosenow kapalı formu, S_opt = 2.714*L/Ra_L^0.25)
        // daha darsa true dönüyor. ViewModel/Screens/PdfGenerator bu bayrağı aynı şekilde okuyor.
        if (isNaturalConvection) {
            val finalDeltaT = (tSurfaceKGuess - tAmbK).coerceAtLeast(0.5)
            val finalAirProps = airPropertiesAtDeltaT(finalDeltaT, airDensity, tAmbK)
            val L_m_forOpt = L_mm.coerceAtLeast(1.0) / 1000.0
            val Ra_L = (9.81 * finalAirProps.beta * finalDeltaT * Math.pow(L_m_forOpt, 3.0)) / (finalAirProps.nu * finalAirProps.alpha)
            val S_opt_mm = if (Ra_L > 0.0) 2.714 * L_mm / Math.pow(Ra_L, 0.25) else S_mm
            systemChoked = S_mm < S_opt_mm
        }

        var totalTimR = 0.0
        var totalSpreadR = 0.0
        // YENİ (Fizik - Adım 6): avgTimR/avgSpreadR artık çip gücüyle ağırlıklı ortalama alınabilsin
        // diye toplam güç payını da ayrıca topluyoruz.
        var totalWeightForAvg = 0.0
        val chipResults = mutableListOf<ChipResultData>()

        val systemBaseTemp = ambientTemp + (totalPowerW * (rConv + rCondBase))

        heatSources.forEach { src ->
            // DÜZELTİLDİ (Fizik - Adım 7): Negatif veya sıfır çip boyutu girilirse alan
            // negatif/sıfır olup rTim ve ısı akısı hesaplarında sıfıra bölme/anlamsız negatif
            // direnç riski oluşuyordu. Boyutlar artık pozitif bir minimuma çekiliyor.
            val wS_m = ((src.wS.replace(",", ".").toDoubleOrNull() ?: 1.0) / 1000.0).coerceAtLeast(1e-6)
            val lS_m = ((src.lS.replace(",", ".").toDoubleOrNull() ?: 1.0) / 1000.0).coerceAtLeast(1e-6)
            val pW = src.watt.replace(",", ".").toDoubleOrNull() ?: 0.0

            val chipAreaM2 = wS_m * lS_m
            val chipAreaCm2 = chipAreaM2 * 10_000.0
            val heatFlux = if (chipAreaCm2 > 0) pW / chipAreaCm2 else 0.0
            val isHotspot = heatFlux > 5.0

            val rTim = if (src.hasTim) {
                val tTim_m = (src.timThick.replace(",", ".").toDoubleOrNull() ?: 0.2) / 1000.0
                val kTim = (src.timK.replace(",", ".").toDoubleOrNull() ?: 1.0).coerceAtLeast(0.001)
                tTim_m / (kTim * chipAreaM2)
            } else {
                // DÜZELTİLDİ: Sabit 0.005°C/W yerine alana bağlı kuru temas direnci. Fiziksel olarak
                // temas direnci ~1/(h_temas * Alan) şeklinde ölçeklenir; sabit değer küçük çiplerde
                // gerçek dirençten çok daha düşük çıkıp junction sıcaklığını olduğundan düşük
                // gösteriyordu. 3000 W/(m²·K), TIM'siz orta sıkıştırmalı kaba metal-metal temas
                // için tipik bir literatür değeridir (iyi bir TIM'den belirgin şekilde kötü,
                // beklenen fiziksel yön).
                1.0 / (3000.0 * chipAreaM2)
            }
            totalTimR += rTim * pW

            // Datasheet Rjc Kontrolü (Daha önce eklemiştik)
            // DÜZELTİLDİ (Fizik - Adım 6): Yayılma direnci artık tb ve arka yüzdeki gerçek h_total'a
            // bağlı computeSpreadingResistance() ile hesaplanıyor (Yovanovich/Lee/Song modeli).
            val rSpread = if (src.useCustomRjc) {
                src.customRjcVal.replace(",", ".").toDoubleOrNull() ?: 0.0
            } else {
                computeSpreadingResistance(chipAreaM2, baseAreaM2, tb_m, k_heatsink, h_total)
            }
            totalSpreadR += rSpread * pW
            totalWeightForAvg += pW

            val tJunction = systemBaseTemp + (pW * (rTim + rSpread))
            chipResults.add(ChipResultData(sourceInfo = src, tempJunction = tJunction, heatFlux = heatFlux, isHotspot = isHotspot))
        }

        // DÜZELTİLDİ (Fizik - Adım 6): Düz aritmetik ortalama yerine güç-ağırlıklı ortalama.
        // Önceden 1W'lık bir çip ile 50W'lık bir çip toplam sistem direncine eşit ağırlıkla
        // katkı yapıyordu; artık baskın (yüksek güçlü) çipin direnci daha fazla ağırlık taşıyor.
        val avgTimR = if (totalWeightForAvg > 0.0) totalTimR / totalWeightForAvg else 0.0
        val avgSpreadR = if (totalWeightForAvg > 0.0) totalSpreadR / totalWeightForAvg else 0.0
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

        // YENİ (Madde #50 - Yakınlık Uyarısı): Yukarıdaki spreading-resistance hesabı her çipi TEK
        // BAŞINA, plaka üzerinde başka ısı kaynağı yokmuş gibi ele alıyor (posX/posY sadece
        // görselleştirme/hotspot işaretlemesinde kullanılıyor, ısıl etkileşimde değil). Tam bir
        // çoklu-kaynak süperpozisyon modeli kurmak yüksek risk/doğrulama zorluğu taşıdığı için
        // (kullanıcıyla konuşuldu), onun yerine basit bir YAKINLIK sezgiseli ile kullanıcı
        // bilgilendiriliyor: her çip için "yarıçap" = (genişlik+uzunluk)/4; iki çipin merkezleri
        // arası mesafeden iki yarıçap çıkarılınca kalan boşluk (edgeGap), daha büyük çipin
        // yarıçapından küçükse "yakın" kabul ediliyor. Bu kesin bir fiziksel eşik değil, temkinli
        // ve basit bir yaklaşıktır.
        var chipsTooCloseFlag = false
        if (heatSources.size >= 2) {
            val chipGeom = heatSources.map { src ->
                val wMm = src.wS.replace(",", ".").toDoubleOrNull() ?: 1.0
                val lMm = src.lS.replace(",", ".").toDoubleOrNull() ?: 1.0
                val xMm = src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0
                val yMm = src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0
                Triple(xMm, yMm, (wMm + lMm) / 4.0)
            }
            outerLoop@ for (i in chipGeom.indices) {
                for (j in (i + 1) until chipGeom.size) {
                    val (x1, y1, r1) = chipGeom[i]
                    val (x2, y2, r2) = chipGeom[j]
                    val centerDist = Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
                    val edgeGap = centerDist - r1 - r2
                    if (edgeGap < Math.max(r1, r2)) {
                        chipsTooCloseFlag = true
                        break@outerLoop
                    }
                }
            }
        }

        return SolverResult(
            usedTb = tb_mm, usedTf = tf_mm, usedS = S_mm,
            totalVolumeCm3 = totalVolumeCm3, totalWeightGram = totalWeightGram, finEfficiencyPercent = finEfficiency * 100.0,
            rTimAvg = avgTimR, rSpreadAvg = avgSpreadR, rCondBase = rCondBase, rConv = rConv, rTotalSystem = rTotalSystem,
            pressureDropPa = pressureDropPa, bypassFactor = bypassFactor,
            operatingFlowM3s = finalOperatingFlow,
            isChoked = systemChoked,
            timeConstantSeconds = calculatedTimeConstant,
            viewFactor = viewFactor,
            chipResults = chipResults,
            chipsInCloseProximity = chipsTooCloseFlag
        )
    }
}
