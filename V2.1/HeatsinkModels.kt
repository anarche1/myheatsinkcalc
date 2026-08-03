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

// === HeatsinkModels.kt: Tum veri siniflari (data class'lar) ===

// ==========================================
// VERİ SINIFLARI (DATA CLASSES)
// ==========================================
data class EnvState(
    val ambientTemp: String = "25.0",
    val tempUnit: String = "°C",
    val altitude: String = "0",
    val altitudeUnit: String = "m",
    val selectedFlowType: String = "Doğal Taşınım (Serbest Hava)",
    val isEnclosedChassis: Boolean = false,
    val selectedFanMethod: String = "Sabit Akış Hızı Girişi",
    val isTunnelEnabled: Boolean = false,
    val chassisCw: String = "120.0",
    val chassisCh: String = "60.0",
    val fanCurvePoints: List<Pair<String, String>> = listOf(Pair("0.0", "150.0"), Pair("50.0", "0.0")),
    val fanFlowUnit: String = "CFM",
    val fanPressureUnit: String = "Pa",
    val fixedSpeedStr: String = "2.0",
    val fixedSpeedUnit: String = "m/sec",
    val fixedFlowStr: String = "45.0",
    val fixedFlowUnit: String = "CFM",
    // DUZELTILDI: Basindaki ":" kaldirildi; artik PageThreeScreen'daki acilir listedeki
    // secenekle birebir ayni string. Eskiden eslesme olmadigi icin LaunchedEffect sessizce
    // ilk secenege reset atiyordu; artik varsayilan deger dogrudan gecerli.
    val selectedEmissivityName: String = "Alüminyum Siyah Eloksallı (ε = 0.85)",
    val emissivityValueStr: String = "0.85",
    // YENİ EKLENEN KISIM: Kalibrasyon Çarpanı (Varsayılan 1.0)
    val calibrationFactor: String = "1.0"
)

data class HeatsinkUiState(
    val currentPage: Int = 0,
    val projectName: String = "",
    val infoDialogTitle: String = "",
    val infoDialogText: String = "",
    val width: String = "100",
    val length: String = "100",
    val baseThickness: String = "5",
    val finHeight: String = "30",
    val finThickness: String = "1.5",
    val finSpacing: String = "2.5",
    val lengthUnit: String = "mm",
    val tempUnit: String = "°C",
    val altitudeUnit: String = "m",
    val flowUnit: String = "m³/s", // Varsayılan Metrik
    val velocityUnit: String = "m/sec",
    val pressureUnit: String = "Pa",
    val weightUnit: String = "g",
    val powerUnit: String = "W",
    val conductivityUnit: String = "W/(m·K)",
    val resistanceUnit: String = "°C/W", // Varsayılan Metrik
    val selectedMaterialName: String = "Al (6063-T6 En Yaygın Soğutucu)",
    val selectedConductivity: String = "200.0",
    val customDensity: String = "2.70",       // YENİ: Custom malzeme yoğunluğu (g/cm³)
    val customSpecificHeat: String = "900",   // YENİ: Custom malzeme özgül ısısı (J/kg·K)
    val isOptimizationEnabled: Boolean = false,
    val targetTemperature: String = "80.0",
    val isTargetTemperatureEnabled: Boolean = false,
    val minBaseThick: String = "2.0",
    val maxBaseThick: String = "12.0",
    val minFinThick: String = "1.0",
    val maxFinThick: String = "5.0",
    val minFinGap: String = "2.0",
    val maxFinGap: String = "8.0",
    val heatSources: List<HeatSourceData> = emptyList(),
    val selectedOrientationIndex: Int = 0,
    val solverResult: SolverResult? = null,
    val isCalculating: Boolean = false,
    val envState: EnvState = EnvState(),
    val showChangelogDialog: Boolean = false,
    // YENİ: Karşılaştırmaya eklenen tasarımların listesi (en fazla 4)
    val comparisonDesigns: List<ComparisonEntry> = emptyList(),
    // YENİ: Şu an yüklü/son kaydedilmiş projenin adı - aynı isimle tekrar kaydetmeyi (güncelleme)
    // farklı bir isimle çakışan yeni bir kayıttan (engellenmesi gereken) ayırt etmek için kullanılır
    val originalLoadedName: String? = null
)

data class MaterialItem(val name: String, val conductivity: Double)
data class HeatSourceData(val id: Long, val name: String, val watt: String, val wS: String, val lS: String, val posX: String, val posY: String, val hasTim: Boolean, val timThick: String, val timK: String, val useCustomRjc: Boolean = false, val customRjcVal: String = "")
data class HeatsinkRenderCache(val W: Double, val tf: Double, val S: Double, val hf: Double, val tb: Double, val maxFinCount: Int, val sideGap: Double)

// YENİ: Duyarlılık analizi (tornado chart) için tek bir parametrenin etkisini tutar
data class SensitivityItem(val paramName: String, val deltaR: Double)

// YENİ: Optimizasyon ısı haritası için tek bir (tf, S) hücresinin sonucunu tutar
data class HeatmapCell(val tf: Double, val s: Double, val rTotal: Double, val isValid: Boolean)

// YENİ: Çoklu tasarım karşılaştırması için kaydedilen tek bir tasarım kaydı
data class ComparisonEntry(val label: String, val materialName: String, val result: SolverResult)

data class SolverResult(
    val usedTb: Double,
    val usedTf: Double,
    val usedS: Double,
    val totalVolumeCm3: Double,
    val totalWeightGram: Double,
    val finEfficiencyPercent: Double,
    val rTimAvg: Double,
    val rSpreadAvg: Double,
    val rCondBase: Double,
    val rConv: Double,
    val rTotalSystem: Double,
    val pressureDropPa: Double,
    val bypassFactor: Double,
    val operatingFlowM3s: Double,
    val isChoked: Boolean,
    val timeConstantSeconds: Double,
    val viewFactor: Double,
    val chipResults: List<ChipResultData>,
    // YENİ: tb, tf, S, k, hf parametrelerinin R_total'a etkisini büyükten küçüğe sıralı tutar
    val sensitivityItems: List<SensitivityItem> = emptyList(),
    // YENİ: Optimizasyon tarandığında tf x S düzlemindeki tüm hücrelerin sonucu (ısı haritası için)
    val heatmapCells: List<HeatmapCell> = emptyList(),
    // YENİ: Arama aralığında geçerli/üretilebilir bir kombinasyon bulunamayıp ham girdi değerlerine
    // geri dönüldüğünde true olur (kullanıcıyı bilgilendirmek için, hata değil)
    val usedFallbackDesign: Boolean = false
)

data class ChipResultData(
    val sourceInfo: HeatSourceData,
    val tempJunction: Double,
    val heatFlux: Double,
    val isHotspot: Boolean
)
