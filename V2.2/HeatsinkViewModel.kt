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

// === HeatsinkViewModel.kt: MVVM ViewModel (is mantigi ve veri deposu) ===

// ==========================================
// VIEWMODEL (İŞ MANTIĞI VE VERİ DEPOSU)
// ==========================================
class HeatsinkViewModel : ViewModel() {

    // DUZELTILDI (Kritik Hata): Proje silme islemi eskiden SharedPreferences anahtarlarini
    // "${projectName}_" on-eki ile eslesenleri (startsWith) silecek sekilde calisiyordu.
    // Bu, bir projenin adi baska bir projenin adinin basiyla ayni oldugunda (orn. "Test" ve "Test_2")
    // yanlislikla ilgisiz bir projenin de verilerini silebiliyordu. Artik silme islemi, asagidaki
    // SABIT ve bilinen alan listesine gore YALNIZCA tam eslesen anahtarlari siliyor.
    // DÜZELTİLDİ (Mimari - Adım 9): Basit String/Boolean/Int alanları için TEK bir deklaratif
    // liste. saveProject() ve PROJECT_FIELD_SUFFIXES artık bu listeden otomatik türetiliyor -
    // yeni bir basit alan eklerken artık sadece BURAYA eklemek yeterli (4 yer yerine 2 yer: burası
    // + loadProject). Depolama formatı (SharedPreferences anahtar adları) hiç değişmedi, mevcut
    // kayıtlı projeler birebir aynı şekilde okunmaya devam eder.
    private sealed class PrefField(val key: String) {
        class Str(key: String, val get: (HeatsinkUiState) -> String) : PrefField(key)
        class Bool(key: String, val get: (HeatsinkUiState) -> Boolean) : PrefField(key)
        class Num(key: String, val get: (HeatsinkUiState) -> Int) : PrefField(key)
    }

    companion object {
        private val SIMPLE_FIELDS: List<PrefField> = listOf(
            PrefField.Str("width") { it.width },
            PrefField.Str("length") { it.length },
            PrefField.Str("baseThickness") { it.baseThickness },
            PrefField.Str("finHeight") { it.finHeight },
            PrefField.Str("finThickness") { it.finThickness },
            PrefField.Str("finSpacing") { it.finSpacing },
            PrefField.Str("lengthUnit") { it.lengthUnit },
            PrefField.Str("tempUnit") { it.tempUnit },
            PrefField.Str("altitudeUnit") { it.altitudeUnit },
            PrefField.Str("flowUnit") { it.flowUnit },
            PrefField.Str("velocityUnit") { it.velocityUnit },
            PrefField.Str("pressureUnit") { it.pressureUnit },
            PrefField.Str("weightUnit") { it.weightUnit },
            PrefField.Str("powerUnit") { it.powerUnit },
            PrefField.Str("conductivityUnit") { it.conductivityUnit },
            PrefField.Str("resistanceUnit") { it.resistanceUnit },
            PrefField.Str("selectedMaterialName") { it.selectedMaterialName },
            PrefField.Str("selectedConductivity") { it.selectedConductivity },
            PrefField.Bool("isOptimizationEnabled") { it.isOptimizationEnabled },
            PrefField.Str("targetTemperature") { it.targetTemperature },
            PrefField.Bool("isTargetTemperatureEnabled") { it.isTargetTemperatureEnabled },
            PrefField.Num("selectedOrientationIndex") { it.selectedOrientationIndex },
            PrefField.Str("minBaseThick") { it.minBaseThick },
            PrefField.Str("maxBaseThick") { it.maxBaseThick },
            PrefField.Str("minFinThick") { it.minFinThick },
            PrefField.Str("maxFinThick") { it.maxFinThick },
            PrefField.Str("minFinGap") { it.minFinGap },
            PrefField.Str("maxFinGap") { it.maxFinGap },
            PrefField.Str("ambientTemp") { it.envState.ambientTemp },
            PrefField.Str("altitude") { it.envState.altitude },
            PrefField.Str("selectedFlowType") { it.envState.selectedFlowType },
            PrefField.Bool("isEnclosedChassis") { it.envState.isEnclosedChassis },
            PrefField.Str("selectedFanMethod") { it.envState.selectedFanMethod },
            PrefField.Bool("isTunnelEnabled") { it.envState.isTunnelEnabled },
            PrefField.Str("chassisCw") { it.envState.chassisCw },
            PrefField.Str("chassisCh") { it.envState.chassisCh },
            PrefField.Str("fixedSpeedStr") { it.envState.fixedSpeedStr },
            PrefField.Str("fixedFlowStr") { it.envState.fixedFlowStr },
            PrefField.Str("selectedEmissivityName") { it.envState.selectedEmissivityName },
            PrefField.Str("emissivityValueStr") { it.envState.emissivityValueStr },
            PrefField.Str("calibrationFactor") { it.envState.calibrationFactor },
            PrefField.Str("customDensity") { it.customDensity },
            PrefField.Str("customSpecificHeat") { it.customSpecificHeat }
        )

        // DÜZELTİLDİ (Mimari - Adım 9): fanCurvePoints/heatSources/comparisonDesigns kendi özel
        // delimiter'lı formatlarıyla ayrı kaydediliyor (SIMPLE_FIELDS'e uymuyorlar), o yüzden
        // anahtar adları ayrıca ekleniyor. Silme listesi artık SIMPLE_FIELDS'ten otomatik
        // türetiliyor - yeni bir basit alan eklediğinizde burayı elle güncellemeniz gerekmiyor.
        private val PROJECT_FIELD_SUFFIXES: List<String> =
            SIMPLE_FIELDS.map { it.key } + listOf("fanCurvePoints", "heatSources", "comparisonDesigns")
    }

    private val _uiState = MutableStateFlow(HeatsinkUiState())
    val uiState: StateFlow<HeatsinkUiState> = _uiState.asStateFlow()

    // YENİ: Arka plandaki hesaplama işlemini tutacağımız değişken (Job).
    // Başlangıçta boş (null). Hesaplama başlayınca içini dolduracağız.
    private var solverJob: Job? = null

    // YENİ: Kullanıcı "İptal Et" butonuna bastığında tetiklenecek fonksiyon.
    fun cancelCalculation() {
        solverJob?.cancel() // Eğer solverJob içinde çalışan bir hesaplama varsa, anında durdur (iptal et).
        _uiState.update { it.copy(isCalculating = false) } // Arayüze (UI) hesaplamanın durduğunu bildir ki yükleme animasyonu kapansın.
    }

    fun updateCurrentPage(page: Int) { _uiState.update { it.copy(currentPage = page) } }
    fun showInfo(title: String, text: String) { _uiState.update { it.copy(infoDialogTitle = title, infoDialogText = text) } }
    fun dismissInfo() { _uiState.update { it.copy(infoDialogTitle = "", infoDialogText = "") } }
    fun updateChangelogDialogState(show: Boolean) { _uiState.update { it.copy(showChangelogDialog = show) } }

    // YENİ: Mevcut hesap sonucunu karşılaştırma listesine ekler (en fazla 4 tasarım)
    fun addCurrentToComparison() {
        val current = _uiState.value
        val res = current.solverResult ?: return
        if (current.comparisonDesigns.size >= 4) return
        // DÜZELTİLDİ: Etiket eskiden sadece o anki liste uzunluğuna göre üretiliyordu, bu yüzden
        // ekle -> çıkar -> tekrar ekle sırasında aynı etiket iki kez oluşabiliyordu (örn. "Tasarım 3"
        // silinip yeni bir tasarım eklenince yine "Tasarım 3" üretilebiliyordu). Artık mevcut
        // etiketlerdeki en yüksek "Tasarım N" numarasının bir fazlası kullanılıyor.
        val existingNumbers = current.comparisonDesigns.mapNotNull { entry ->
            Regex("""Tasarım (\d+)""").find(entry.label)?.groupValues?.get(1)?.toIntOrNull()
        }
        val nextNumber = (existingNumbers.maxOrNull() ?: 0) + 1
        val label = "Tasarım $nextNumber"
        val entry = ComparisonEntry(label = label, materialName = current.selectedMaterialName, result = res)
        _uiState.update { it.copy(comparisonDesigns = it.comparisonDesigns + entry) }
    }

    // YENİ: Karşılaştırma listesinden belirtilen indeksteki tasarımı kaldırır
    fun removeFromComparison(index: Int) {
        _uiState.update { it.copy(comparisonDesigns = it.comparisonDesigns.filterIndexed { i, _ -> i != index }) }
    }

    fun updateWidth(v: String) { _uiState.update { it.copy(width = v) } }
    fun updateLength(v: String) { _uiState.update { it.copy(length = v) } }
    fun updateBaseThickness(v: String) { _uiState.update { it.copy(baseThickness = v) } }
    fun updateFinHeight(v: String) { _uiState.update { it.copy(finHeight = v) } }
    fun updateFinThickness(v: String) { _uiState.update { it.copy(finThickness = v) } }
    fun updateFinSpacing(v: String) { _uiState.update { it.copy(finSpacing = v) } }

    fun updateLengthUnit(v: String) { _uiState.update { it.copy(lengthUnit = v) } }
    fun updateTempUnit(v: String) { _uiState.update { it.copy(tempUnit = v) } }
    fun updateAltitudeUnit(v: String) { _uiState.update { it.copy(altitudeUnit = v) } }
    fun updateFlowUnit(v: String) { _uiState.update { it.copy(flowUnit = v) } }
    fun updateVelocityUnit(v: String) { _uiState.update { it.copy(velocityUnit = v) } }
    fun updatePressureUnit(v: String) { _uiState.update { it.copy(pressureUnit = v) } }
    fun updateWeightUnit(v: String) { _uiState.update { it.copy(weightUnit = v) } }
    fun updatePowerUnit(v: String) { _uiState.update { it.copy(powerUnit = v) } }
    fun updateConductivityUnit(v: String) { _uiState.update { it.copy(conductivityUnit = v) } }
    fun updateResistanceUnit(v: String) { _uiState.update { it.copy(resistanceUnit = v) } }

    fun applyMetricPreset() {
        _uiState.update { it.copy(
            lengthUnit = "mm", tempUnit = "°C", altitudeUnit = "m", flowUnit = "m³/s", velocityUnit = "m/sec", pressureUnit = "Pa", weightUnit = "g", powerUnit = "W", conductivityUnit = "W/(m·K)", resistanceUnit = "°C/W",
            width = "100", length = "100", baseThickness = "5", finHeight = "30", finThickness = "1.5", finSpacing = "2.5",
            minBaseThick = "2.0", maxBaseThick = "12.0", minFinThick = "1.0", maxFinThick = "5.0", minFinGap = "2.0", maxFinGap = "8.0",
            envState = it.envState.copy(ambientTemp = "25.0", altitude = "0", chassisCw = "120.0", chassisCh = "60.0", fixedSpeedStr = "2.0", fixedFlowStr = "45.0", fanCurvePoints = listOf(Pair("0.0", "150.0"), Pair("50.0", "0.0"))),
            heatSources = emptyList()
        ) }
    }

    fun applyImperialPreset() {
        _uiState.update { it.copy(
            lengthUnit = "inch", tempUnit = "°F", altitudeUnit = "ft", flowUnit = "CFM", velocityUnit = "ft/min", pressureUnit = "in-H2O", weightUnit = "lbs", powerUnit = "W", conductivityUnit = "BTU/(hr·ft·°F)", resistanceUnit = "°F/W",
            width = "4.0", length = "4.0", baseThickness = "0.2", finHeight = "1.2", finThickness = "0.06", finSpacing = "0.1",
            minBaseThick = "0.08", maxBaseThick = "0.5", minFinThick = "0.04", maxFinThick = "0.2", minFinGap = "0.08", maxFinGap = "0.3",
            envState = it.envState.copy(ambientTemp = "68.0", altitude = "0", chassisCw = "4.8", chassisCh = "2.4", fixedSpeedStr = "400.0", fixedFlowStr = "100.0", fanCurvePoints = listOf(Pair("0.0", "0.6"), Pair("100.0", "0.0"))),
            heatSources = emptyList()
        ) }
    }

    fun updateSelectedMaterialName(v: String) { _uiState.update { it.copy(selectedMaterialName = v) } }
    fun updateSelectedConductivity(v: String) { _uiState.update { it.copy(selectedConductivity = v) } }
    fun updateCustomDensity(v: String) { _uiState.update { it.copy(customDensity = v) } }
    fun updateCustomSpecificHeat(v: String) { _uiState.update { it.copy(customSpecificHeat = v) } }
    fun updateIsOptimizationEnabled(v: Boolean) { _uiState.update { it.copy(isOptimizationEnabled = v) } }
    fun updateTargetTemperature(v: String) { _uiState.update { it.copy(targetTemperature = v) } }
    fun updateIsTargetTemperatureEnabled(v: Boolean) { _uiState.update { it.copy(isTargetTemperatureEnabled = v) } }

    fun updateMinBaseThick(v: String) { _uiState.update { it.copy(minBaseThick = v) } }
    fun updateMaxBaseThick(v: String) { _uiState.update { it.copy(maxBaseThick = v) } }
    fun updateMinFinThick(v: String) { _uiState.update { it.copy(minFinThick = v) } }
    fun updateMaxFinThick(v: String) { _uiState.update { it.copy(maxFinThick = v) } }
    fun updateMinFinGap(v: String) { _uiState.update { it.copy(minFinGap = v) } }
    fun updateMaxFinGap(v: String) { _uiState.update { it.copy(maxFinGap = v) } }
    fun updateHeatSources(v: List<HeatSourceData>) { _uiState.update { it.copy(heatSources = v) } }
    fun updateOrientationIndex(v: Int) { _uiState.update { it.copy(selectedOrientationIndex = v) } }
    fun updateEnvState(newState: EnvState) { _uiState.update { it.copy(envState = newState) } }
    fun updateProjectName(v: String) { _uiState.update { it.copy(projectName = v) } }

    fun getSavedProjectNames(context: Context): List<String> {
        val prefs = context.getSharedPreferences("DovahkiinPrefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("projectList", emptySet())?.toList()?.sorted() ?: emptyList()
    }

    // DOKÜMANTASYON: Gelişmiş Proje Kaydetme Fonksiyonu (Tüm parametreler kaydedilir)
    // YENİ: Artık Boolean döndürüyor - false ise "bu isimde başka bir proje zaten var" demektir,
    // true ise kayıt (ya da mevcut projenin güncellenmesi) başarıyla tamamlandı demektir.
    fun saveProject(context: Context): Boolean {
        val s = _uiState.value
        if (s.projectName.isBlank()) return false

        val prefs = context.getSharedPreferences("DovahkiinPrefs", Context.MODE_PRIVATE)
        val projectList = prefs.getStringSet("projectList", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // YENİ: Bu isim, şu an düzenlemekte olduğumuz projenin ORİJİNAL adından farklıysa
        // ve zaten kayıtlı bir proje listesindeyse, üzerine yazmadan önce kullanıcıyı durduruyoruz.
        if (s.projectName != s.originalLoadedName && projectList.contains(s.projectName)) {
            return false
        }

        val editor = prefs.edit()
        projectList.add(s.projectName)
        editor.putStringSet("projectList", projectList)

        val p = "${s.projectName}_"

        // DÜZELTİLDİ (Mimari - Adım 9): ~35 ayrı putString/putBoolean/putInt çağrısı yerine,
        // yukarıdaki SIMPLE_FIELDS listesinden tek bir döngüyle yazılıyor. Anahtar adları ve
        // yazılan değerler birebir aynı - mevcut kayıtlı projelerle format uyumluluğu korunuyor.
        SIMPLE_FIELDS.forEach { field ->
            when (field) {
                is PrefField.Str -> editor.putString(p + field.key, field.get(s))
                is PrefField.Bool -> editor.putBoolean(p + field.key, field.get(s))
                is PrefField.Num -> editor.putInt(p + field.key, field.get(s))
            }
        }

        // Fan Eğrisi Kaydı (Noktaları aralarına noktalı virgül koyarak string'e çeviriyoruz)
        // DUZELTILDI (Kritik Hata): Nokta ici Q,P ayiricisi "," yerine "|" yapildi. Nedeni: Kullanici
        // Q veya P degerini Turkce yerellestirmeyle ondalik virgul kullanarak girebilir (orn. "12,5").
        // Eskiden alan ayiricisi da "," oldugu icin "12,5;150,0" gibi bir deger yanlis parcalaniyor,
        // kaydedilen fan egrisi sessizce bozuluyordu.
        val curveStr = s.envState.fanCurvePoints.joinToString(";") { "${it.first}|${it.second}" }
        editor.putString(p + "fanCurvePoints", curveStr)

        // DUZELTILDI: chip.name savunma amacli olarak "|" ve "~" ayirici karakterlerinden temizleniyor
        // (UI zaten bu karakterleri engelliyor, ancak eski kayitli veriler icin ekstra guvenlik).
        val chipsString = s.heatSources.joinToString("~") { chip ->
            val safeName = chip.name.replace("|", "").replace("~", "")
            "${chip.id}|${safeName}|${chip.watt}|${chip.wS}|${chip.lS}|${chip.posX}|${chip.posY}|${chip.hasTim}|${chip.timThick}|${chip.timK}|${chip.useCustomRjc}|${chip.customRjcVal}"
        }
        editor.putString(p + "heatSources", chipsString)

        // DÜZELTİLDİ (Mimari - Adım 9): customDensity/customSpecificHeat artık yukarıdaki
        // SIMPLE_FIELDS döngüsünde yazılıyor, buradaki ayrı çağrılar kaldırıldı (çift yazım önlendi).

        // YENİ: Karşılaştırma listesindeki tasarımların kompakt bir özetini kaydet
        // (SolverResult'ın tamamı değil, karşılaştırma tablosunda gösterilen alanlar yeterli)
        val comparisonStr = s.comparisonDesigns.joinToString(";;") { entry ->
            val safeLabel = entry.label.replace("|", "").replace(";;", "")
            val safeMaterial = entry.materialName.replace("|", "").replace(";;", "")
            "${safeLabel}|${safeMaterial}|${entry.result.usedTb}|${entry.result.usedTf}|${entry.result.usedS}|${entry.result.rTotalSystem}|${entry.result.totalWeightGram}|${entry.result.finEfficiencyPercent}"
        }
        editor.putString(p + "comparisonDesigns", comparisonStr)

        editor.apply()
        _uiState.update { it.copy(originalLoadedName = s.projectName) }
        return true
    }

    // DOKÜMANTASYON: Gelişmiş Proje Yükleme Fonksiyonu
    fun loadProject(context: Context, projectNameToLoad: String) {
        val prefs = context.getSharedPreferences("DovahkiinPrefs", Context.MODE_PRIVATE)
        val p = "${projectNameToLoad}_"

        val savedChipsString = prefs.getString(p + "heatSources", "") ?: ""
        val loadedChips = if (savedChipsString.isNotBlank()) {
            savedChipsString.split("~").mapNotNull { chipStr ->
                val parts = chipStr.split("|")
                if (parts.size == 12) {
                    // V2.1 Formatı: Yeni projeler sorunsuz yüklenir
                    HeatSourceData(
                        id = parts[0].toLongOrNull() ?: 0L, name = parts[1], watt = parts[2], wS = parts[3], lS = parts[4], posX = parts[5], posY = parts[6], hasTim = parts[7].toBooleanStrictOrNull() ?: false, timThick = parts[8], timK = parts[9], useCustomRjc = parts[10].toBooleanStrictOrNull() ?: false, customRjcVal = parts[11]
                    )
                } else if (parts.size == 10) {
                    // Eski V2.0 Formatı: Kullanıcının eski projeleri çökmek yerine varsayılan değerlerle yüklenir
                    HeatSourceData(
                        id = parts[0].toLongOrNull() ?: 0L, name = parts[1], watt = parts[2], wS = parts[3], lS = parts[4], posX = parts[5], posY = parts[6], hasTim = parts[7].toBooleanStrictOrNull() ?: false, timThick = parts[8], timK = parts[9], useCustomRjc = false, customRjcVal = ""
                    )
                } else null
            }
        } else emptyList()

        val curveStr = prefs.getString(p + "fanCurvePoints", "") ?: ""
        val loadedCurve = if (curveStr.isNotBlank()) {
            curveStr.split(";").mapNotNull { pointStr ->
                // DUZELTILDI: Yeni format "|" kullanir. Eski (V2.1 oncesi) kayitlarla geriye donuk
                // uyumluluk icin, "|" bulunamazsa eski "," ayiricisina geri dusuyoruz (Legacy Format).
                val coords = if (pointStr.contains("|")) pointStr.split("|") else pointStr.split(",")
                if (coords.size == 2) Pair(coords[0], coords[1]) else null
            }
        } else listOf(Pair("0.0", "150.0"), Pair("50.0", "0.0"))

        // YENİ: Karşılaştırma listesini kompakt formattan geri yüklüyoruz
        val comparisonStr = prefs.getString(p + "comparisonDesigns", "") ?: ""
        val loadedComparisons = if (comparisonStr.isNotBlank()) {
            comparisonStr.split(";;").mapNotNull { entryStr ->
                val parts = entryStr.split("|")
                if (parts.size == 8) {
                    ComparisonEntry(
                        label = parts[0],
                        materialName = parts[1],
                        result = SolverResult(
                            usedTb = parts[2].toDoubleOrNull() ?: 0.0,
                            usedTf = parts[3].toDoubleOrNull() ?: 0.0,
                            usedS = parts[4].toDoubleOrNull() ?: 0.0,
                            totalVolumeCm3 = 0.0,
                            totalWeightGram = parts[6].toDoubleOrNull() ?: 0.0,
                            finEfficiencyPercent = parts[7].toDoubleOrNull() ?: 0.0,
                            rTimAvg = 0.0, rSpreadAvg = 0.0, rCondBase = 0.0, rConv = 0.0,
                            rTotalSystem = parts[5].toDoubleOrNull() ?: 0.0,
                            pressureDropPa = 0.0, bypassFactor = 1.0, operatingFlowM3s = 0.0,
                            isChoked = false, timeConstantSeconds = 0.0, viewFactor = 1.0,
                            chipResults = emptyList()
                        )
                    )
                } else null
            }.take(4) // DÜZELTİLDİ: Önceki denemede bu sınır yanlışlıkla heatSources listesine
              // uygulanmıştı (5+ çipli projelerde sessiz veri kaybına yol açıyordu). Doğru yer
              // burası: addCurrentToComparison() ekleme sırasında 4 sınırını zorunlu kılıyor ama
              // yükleme bunu kontrol etmiyordu; kayıtlı metin 4'ten fazla giriş içerirse bile
              // sınır burada garanti altına alınıyor.
        } else emptyList()

        val loadedEnvState = EnvState(
            // DÜZELTİLDİ: envState.tempUnit / envState.altitudeUnit daha önce hiç set edilmiyordu,
            // bu yüzden her proje yüklemesinde sessizce sınıf varsayılanına ("°C"/"m") dönüyordu -
            // üst seviyedeki uiState.tempUnit/altitudeUnit doğru yüklenirken bu iç kopya
            // yanlış kalıyordu. Aynı zaten kaydedilmiş anahtarları burada da okuyoruz.
            tempUnit = prefs.getString(p + "tempUnit", "°C") ?: "°C",
            altitudeUnit = prefs.getString(p + "altitudeUnit", "m") ?: "m",
            ambientTemp = prefs.getString(p + "ambientTemp", "25.0") ?: "25.0",
            altitude = prefs.getString(p + "altitude", "0") ?: "0",
            selectedFlowType = prefs.getString(p + "selectedFlowType", "Doğal Taşınım (Serbest Hava)") ?: "Doğal Taşınım (Serbest Hava)",
            isEnclosedChassis = prefs.getBoolean(p + "isEnclosedChassis", false),
            selectedFanMethod = prefs.getString(p + "selectedFanMethod", "Sabit Akış Hızı Girişi") ?: "Sabit Akış Hızı Girişi",
            isTunnelEnabled = prefs.getBoolean(p + "isTunnelEnabled", false),
            chassisCw = prefs.getString(p + "chassisCw", "120.0") ?: "120.0",
            chassisCh = prefs.getString(p + "chassisCh", "60.0") ?: "60.0",
            fixedSpeedStr = prefs.getString(p + "fixedSpeedStr", "2.0") ?: "2.0",
            fixedFlowStr = prefs.getString(p + "fixedFlowStr", "45.0") ?: "45.0",
            selectedEmissivityName = prefs.getString(p + "selectedEmissivityName", "Alüminyum Siyah Eloksallı (ε = 0.85)") ?: "Alüminyum Siyah Eloksallı (ε = 0.85)",
            emissivityValueStr = prefs.getString(p + "emissivityValueStr", "0.85") ?: "0.85",
            fanCurvePoints = loadedCurve,
            // YENİ EKLENEN KISIM: Eski projelerde veri yoksa 1.0 olarak yükler
            calibrationFactor = prefs.getString(p + "calibrationFactor", "1.0") ?: "1.0"
        )

        _uiState.update { it.copy(
            projectName = projectNameToLoad,
            width = prefs.getString(p + "width", "100") ?: "100",
            length = prefs.getString(p + "length", "100") ?: "100",
            baseThickness = prefs.getString(p + "baseThickness", "5") ?: "5",
            finHeight = prefs.getString(p + "finHeight", "30") ?: "30",
            finThickness = prefs.getString(p + "finThickness", "1.5") ?: "1.5",
            finSpacing = prefs.getString(p + "finSpacing", "2.5") ?: "2.5",
            lengthUnit = prefs.getString(p + "lengthUnit", "mm") ?: "mm",
            tempUnit = prefs.getString(p + "tempUnit", "°C") ?: "°C",
            altitudeUnit = prefs.getString(p + "altitudeUnit", "m") ?: "m",
            flowUnit = prefs.getString(p + "flowUnit", "m³/s") ?: "m³/s",
            velocityUnit = prefs.getString(p + "velocityUnit", "m/sec") ?: "m/sec",
            pressureUnit = prefs.getString(p + "pressureUnit", "Pa") ?: "Pa",
            weightUnit = prefs.getString(p + "weightUnit", "g") ?: "g",
            powerUnit = prefs.getString(p + "powerUnit", "W") ?: "W",
            conductivityUnit = prefs.getString(p + "conductivityUnit", "W/(m·K)") ?: "W/(m·K)",
            resistanceUnit = prefs.getString(p + "resistanceUnit", "°C/W") ?: "°C/W",
            selectedMaterialName = prefs.getString(p + "selectedMaterialName", "Al (6063-T6 En Yaygın Soğutucu)") ?: "Al (6063-T6 En Yaygın Soğutucu)",
            selectedConductivity = prefs.getString(p + "selectedConductivity", "200.0") ?: "200.0",
            isOptimizationEnabled = prefs.getBoolean(p + "isOptimizationEnabled", false),
            targetTemperature = prefs.getString(p + "targetTemperature", "80.0") ?: "80.0",
            isTargetTemperatureEnabled = prefs.getBoolean(p + "isTargetTemperatureEnabled", false),
            selectedOrientationIndex = prefs.getInt(p + "selectedOrientationIndex", 0),
            minBaseThick = prefs.getString(p + "minBaseThick", "2.0") ?: "2.0",
            maxBaseThick = prefs.getString(p + "maxBaseThick", "12.0") ?: "12.0",
            minFinThick = prefs.getString(p + "minFinThick", "1.0") ?: "1.0",
            maxFinThick = prefs.getString(p + "maxFinThick", "5.0") ?: "5.0",
            minFinGap = prefs.getString(p + "minFinGap", "2.0") ?: "2.0",
            maxFinGap = prefs.getString(p + "maxFinGap", "8.0") ?: "8.0",
            envState = loadedEnvState,
            heatSources = loadedChips,
            customDensity = prefs.getString(p + "customDensity", "2.70") ?: "2.70",
            customSpecificHeat = prefs.getString(p + "customSpecificHeat", "900") ?: "900",
            comparisonDesigns = loadedComparisons,
            originalLoadedName = projectNameToLoad,
            currentPage = 1
        )}
    }

    fun deleteProject(context: Context, projectNameToDelete: String) {
        val prefs = context.getSharedPreferences("DovahkiinPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val projectList = prefs.getStringSet("projectList", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        projectList.remove(projectNameToDelete)
        editor.putStringSet("projectList", projectList)

        // DUZELTILDI (Kritik Hata): "startsWith" ile on-ek eslestirme yerine, yalnizca bu projeye ait
        // OLMASI GEREKEN tam anahtarlar siliniyor. Boylece "Test" projesini silmek "Test_2" projesinin
        // verilerini asla etkilemez.
        val p = "${projectNameToDelete}_"
        PROJECT_FIELD_SUFFIXES.forEach { suffix -> editor.remove(p + suffix) }
        editor.apply()
    }

    // ... (Sınıfın geri kalanındaki runSolverAndNavigate fonksiyonu aynen devam eder, ona dokunmuyoruz)

    fun runSolverAndNavigate(ambientTempStr: String, flowParamStr: String, flowType: String) {
        _uiState.update { it.copy(isCalculating = true) }
        val s = _uiState.value
        val lMul = when(s.lengthUnit) { "cm" -> 10.0; "m" -> 1000.0; "inch" -> 25.4; "ft" -> 304.8; else -> 1.0 }

        val W = (s.width.replace(",", ".").toDoubleOrNull() ?: 100.0) * lMul
        val L = (s.length.replace(",", ".").toDoubleOrNull() ?: 100.0) * lMul
        val hf = (s.finHeight.replace(",", ".").toDoubleOrNull() ?: 30.0) * lMul

        // DÜZELTİLDİ: selectedConductivity zaten PageOneScreen'deki materialList'ten (tek doğruluk
        // kaynağı) seçim anında doğru şekilde set ediliyor ve kayıt/yükleme sırasında korunuyor.
        // Malzeme adını metinde arayarak (.contains) iletkenliği yeniden tahmin etmeye gerek yok;
        // bu hem gereksiz tekrardı hem de isim ileride değişirse sessizce yanlış değere düşme riski taşıyordu.
        val kBase = s.selectedConductivity.replace(",", ".").toDoubleOrNull() ?: 200.0
        val k = kBase
        val rawAmb = ambientTempStr.replace(",", ".").toDoubleOrNull() ?: 25.0
        val amb = when(s.tempUnit) { "°F" -> (rawAmb - 32.0) * 5.0 / 9.0; "K" -> rawAmb - 273.15; else -> rawAmb }
        val altitudeMeters = (s.envState.altitude.replace(",", ".").toDoubleOrNull() ?: 0.0) * (if (s.envState.altitudeUnit == "ft") 0.3048 else 1.0)

        val rawFlow = flowParamStr.replace(",", ".").toDoubleOrNull() ?: 0.0
        val fanMethod = s.envState.selectedFanMethod
        val flow = if (fanMethod.contains("Debisi") || (!fanMethod.contains("Hızı") && !fanMethod.contains("Debisi"))) {
            when(s.flowUnit) { "CFM" -> rawFlow * 0.000471947; "L/min" -> rawFlow / 60000.0; else -> rawFlow }
        } else {
            if (s.velocityUnit == "ft/min") rawFlow * 0.00508 else rawFlow
        }

        val parsedFanCurve = if (fanMethod.contains("Eğrisi")) {
            s.envState.fanCurvePoints.mapNotNull {
                val rawQ = it.first.replace(",", ".").toDoubleOrNull()
                val rawP = it.second.replace(",", ".").toDoubleOrNull()
                if (rawQ != null && rawP != null) {
                    val qM3s = when(s.flowUnit) { "CFM" -> rawQ * 0.000471947; "L/min" -> rawQ / 60000.0; "m³/h" -> rawQ / 3600.0; else -> rawQ }
                    val pPa = when(s.pressureUnit) { "in-H2O" -> rawP * 248.84; "mm-H2O" -> rawP * 9.80665; else -> rawP }
                    Pair(qM3s, pPa)
                } else null
            }.sortedBy { it.first }
        } else null

        // DUZELTILDI (Hesaplama Dogrulugu): Optimizasyon donguisu eskiden fan metodundan bagimsiz
        // olarak SABIT 40 Pa basinc esigi kullaniyordu. Bu esik "Sabit Hiz/Debi Girisi" modlarinda
        // anlamsizdi (kullanicinin dogrudan belirttigi bir akis kosulunu keyfi bir basincla reddediyordu)
        // ve "Fan Egrisi" modunda guclu fanlarla (>40 Pa uretebilen) gecerli tasarimlarin haksiz yere
        // elenmesine yol aciyordu. Artik esik, secilen fan egrisinin gercek maksimum statik basincinin
        // %90'i (neredeyse tikanma/stall bolgesi) olarak hesaplaniyor; sabit hiz/debi girislerinde ise
        // byle bir fan egrisi context'i olmadigindan bu kontrol devre disi birakiliyor.
        val maxAcceptablePressurePa = if (fanMethod.contains("Eğrisi") && parsedFanCurve != null && parsedFanCurve.isNotEmpty()) {
            (parsedFanCurve.maxOf { it.second }) * 0.9
        } else {
            Double.MAX_VALUE
        }

        val emissivity = s.envState.emissivityValueStr.replace(",", ".").toDoubleOrNull() ?: 0.85
        val isTunnel = s.envState.isTunnelEnabled
        val cCw = (s.envState.chassisCw.replace(",", ".").toDoubleOrNull() ?: (W/lMul)) * lMul

        val pMul = if (s.powerUnit == "kW") 1000.0 else 1.0
        val mappedSources = s.heatSources.map {
            it.copy(
                posX = ((it.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) * lMul).toString(),
                posY = ((it.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) * lMul).toString(),
                wS = ((it.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) * lMul).toString(),
                lS = ((it.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) * lMul).toString(),
                watt = ((it.watt.replace(",", ".").toDoubleOrNull() ?: 0.0) * pMul).toString()
            )
        }

        // ==========================================
        // YENİ EKLENEN KISIM: Arayüzdeki katsayıyı güvenli bir rakama çevir
        // ==========================================
        val cFact = s.envState.calibrationFactor.replace(",", ".").toDoubleOrNull() ?: 1.0
        // YENİ: Custom malzeme yoğunluk/özgül ısı değerlerini güvenli sayıya çeviriyoruz
        val customDensityVal = s.customDensity.replace(",", ".").toDoubleOrNull() ?: 2.70
        val customSpecificHeatVal = s.customSpecificHeat.replace(",", ".").toDoubleOrNull() ?: 900.0

        solverJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1500)

            val bestResult = withContext(Dispatchers.Default) {
                var localBest: SolverResult? = null

                if (s.isOptimizationEnabled) {
                    val minTb = (s.minBaseThick.replace(",", ".").toDoubleOrNull() ?: 2.0) * lMul
                    val maxTb = (s.maxBaseThick.replace(",", ".").toDoubleOrNull() ?: 12.0) * lMul
                    val minTf = (s.minFinThick.replace(",", ".").toDoubleOrNull() ?: 1.0) * lMul
                    val maxTf = (s.maxFinThick.replace(",", ".").toDoubleOrNull() ?: 5.0) * lMul
                    val minS = (s.minFinGap.replace(",", ".").toDoubleOrNull() ?: 2.0) * lMul
                    val maxS = (s.maxFinGap.replace(",", ".").toDoubleOrNull() ?: 8.0) * lMul

                    val stepTb = 1.0
                    val stepTf = 0.5
                    val stepS = 0.5

                    val customTarget = s.targetTemperature.replace(",", ".").toDoubleOrNull() ?: 90.0
                    val isTargetEnabled = s.isTargetTemperatureEnabled

                    var bestValidResult: SolverResult? = null
                    var absoluteColdestResult: SolverResult? = null

                    var lowestVolume = Double.MAX_VALUE
                    var lowestResistanceForFallback = Double.MAX_VALUE

                    var currTb = minTb
                    while (currTb <= maxTb) {
                        var currTf = minTf
                        while (currTf <= maxTf) {
                            var currS = minS
                            while (currS <= maxS) {
                                if (!isActive) return@withContext null

                                val cCh = (s.envState.chassisCh.replace(",", ".").toDoubleOrNull() ?: ((currTb + hf)/lMul)) * lMul
                                val res = HeatsinkSolver.calculate(
                                    W_mm = W, L_mm = L, tb_mm = currTb, tf_mm = currTf, S_mm = currS, hf_mm = hf,
                                    k_heatsink = k, materialName = s.selectedMaterialName,
                                    ambientTemp = amb, flowType = flowType, flowParam = flow,
                                    altitude_m = altitudeMeters, emissivity = emissivity,
                                    isTunnelEnabled = isTunnel, chassisCw = cCw, chassisCh = cCh, fanMethod = fanMethod,
                                    heatSources = mappedSources,
                                    orientationIndex = s.selectedOrientationIndex,
                                    isEnclosedChassis = s.envState.isEnclosedChassis,
                                    fanCurve = parsedFanCurve,
                                    calibrationFactor = cFact, // YENİ: Katsayıyı fizik motoruna gönderdik
                                    customDensityGcm3 = customDensityVal,
                                    customSpecificHeatJkgK = customSpecificHeatVal
                                )
                                val isManufacturable = (hf / currTf) <= 25.0
                                val isFlowAcceptable = if (flowType.contains("Doğal")) !res.isChoked else res.pressureDropPa <= maxAcceptablePressurePa
                                val minRequiredTb = maxOf(3.0, W / 25.0)
                                val isBaseAcceptable = currTb >= minRequiredTb

                                if (isManufacturable && isFlowAcceptable && isBaseAcceptable) {
                                    if (res.rTotalSystem < lowestResistanceForFallback) {
                                        lowestResistanceForFallback = res.rTotalSystem
                                        absoluteColdestResult = res
                                    }
                                    if (isTargetEnabled) {
                                        val maxJunction = res.chipResults.maxOfOrNull { it.tempJunction } ?: 0.0
                                        val displayMaxTemp = when(s.tempUnit) { "°F" -> (maxJunction * 1.8) + 32.0; "K" -> maxJunction + 273.15; else -> maxJunction }
                                        if (displayMaxTemp <= customTarget) {
                                            if (res.totalVolumeCm3 < lowestVolume) {
                                                lowestVolume = res.totalVolumeCm3
                                                bestValidResult = res
                                            }
                                        }
                                    }
                                }
                                currS += stepS
                            }
                            currTf += stepTf
                        }
                        currTb += stepTb
                    }

                    localBest = if (isTargetEnabled && bestValidResult != null) {
                        bestValidResult
                    } else {
                        absoluteColdestResult
                    }

                    if (!isActive) return@withContext null

                    if (localBest == null) {
                        val tb = (s.baseThickness.replace(",", ".").toDoubleOrNull() ?: 5.0) * lMul
                        val tf = (s.finThickness.replace(",", ".").toDoubleOrNull() ?: 1.5) * lMul
                        val spacing = (s.finSpacing.replace(",", ".").toDoubleOrNull() ?: 2.5) * lMul
                        val cChFallback = (s.envState.chassisCh.replace(",", ".").toDoubleOrNull() ?: ((tb + hf)/lMul)) * lMul

                        localBest = HeatsinkSolver.calculate(
                            W_mm = W, L_mm = L, tb_mm = tb, tf_mm = tf, S_mm = spacing, hf_mm = hf,
                            k_heatsink = k, materialName = s.selectedMaterialName,
                            ambientTemp = amb, flowType = flowType, flowParam = flow,
                            altitude_m = altitudeMeters, emissivity = emissivity,
                            isTunnelEnabled = isTunnel, chassisCw = cCw, chassisCh = cChFallback, fanMethod = fanMethod,
                            heatSources = mappedSources,
                            orientationIndex = s.selectedOrientationIndex,
                            isEnclosedChassis = s.envState.isEnclosedChassis,
                            fanCurve = parsedFanCurve,
                            calibrationFactor = cFact, // YENİ: Katsayıyı fizik motoruna gönderdik
                            customDensityGcm3 = customDensityVal,
                            customSpecificHeatJkgK = customSpecificHeatVal
                            // YENİ: Bu sonucun aramadan değil, ham girdi değerlerinden geldiğini işaretliyoruz
                        ).copy(usedFallbackDesign = true)
                    }
                } else {
                    val tb = (s.baseThickness.replace(",", ".").toDoubleOrNull() ?: 5.0) * lMul
                    val tf = (s.finThickness.replace(",", ".").toDoubleOrNull() ?: 1.5) * lMul
                    val spacing = (s.finSpacing.replace(",", ".").toDoubleOrNull() ?: 2.5) * lMul
                    val cCh = (s.envState.chassisCh.replace(",", ".").toDoubleOrNull() ?: ((tb + hf)/lMul)) * lMul

                    localBest = HeatsinkSolver.calculate(
                        W_mm = W, L_mm = L, tb_mm = tb, tf_mm = tf, S_mm = spacing, hf_mm = hf,
                        k_heatsink = k, materialName = s.selectedMaterialName,
                        ambientTemp = amb, flowType = flowType, flowParam = flow,
                        altitude_m = altitudeMeters, emissivity = emissivity,
                        isTunnelEnabled = isTunnel, chassisCw = cCw, chassisCh = cCh, fanMethod = fanMethod,
                        heatSources = mappedSources,
                        orientationIndex = s.selectedOrientationIndex,
                        isEnclosedChassis = s.envState.isEnclosedChassis,
                        fanCurve = parsedFanCurve,
                        calibrationFactor = cFact, // YENİ: Katsayıyı fizik motoruna gönderdik
                        customDensityGcm3 = customDensityVal,
                        customSpecificHeatJkgK = customSpecificHeatVal
                    )
                }

                // YENİ: Duyarlılık Analizi (Tornado Chart) - final tasarımın etrafında tb/tf/S/k/hf
                // parametrelerini ayrı ayrı ±%10 oynatarak R_total üzerindeki etkilerini ölçüyoruz.
                val finalDesign = localBest
                if (finalDesign != null && isActive) {
                    val cChSens = (s.envState.chassisCh.replace(",", ".").toDoubleOrNull() ?: ((finalDesign.usedTb + hf)/lMul)) * lMul

                    fun recomputeR(tbP: Double, tfP: Double, sP: Double, kP: Double, hfP: Double): Double {
                        return HeatsinkSolver.calculate(
                            W_mm = W, L_mm = L, tb_mm = tbP, tf_mm = tfP, S_mm = sP, hf_mm = hfP,
                            k_heatsink = kP, materialName = s.selectedMaterialName,
                            ambientTemp = amb, flowType = flowType, flowParam = flow,
                            altitude_m = altitudeMeters, emissivity = emissivity,
                            isTunnelEnabled = isTunnel, chassisCw = cCw, chassisCh = cChSens, fanMethod = fanMethod,
                            heatSources = mappedSources,
                            orientationIndex = s.selectedOrientationIndex,
                            isEnclosedChassis = s.envState.isEnclosedChassis,
                            fanCurve = parsedFanCurve,
                            calibrationFactor = cFact,
                            customDensityGcm3 = customDensityVal,
                            customSpecificHeatJkgK = customSpecificHeatVal
                        ).rTotalSystem
                    }

                    val bTb = finalDesign.usedTb; val bTf = finalDesign.usedTf; val bS = finalDesign.usedS; val bK = k; val bHf = hf

                    val items = mutableListOf<SensitivityItem>()
                    items.add(SensitivityItem("Taban Kalınlığı (tb)", kotlin.math.abs(
                        recomputeR(bTb * 1.1, bTf, bS, bK, bHf) - recomputeR(bTb * 0.9, bTf, bS, bK, bHf)
                    )))
                    items.add(SensitivityItem("Kanat Kalınlığı (tf)", kotlin.math.abs(
                        recomputeR(bTb, bTf * 1.1, bS, bK, bHf) - recomputeR(bTb, bTf * 0.9, bS, bK, bHf)
                    )))
                    items.add(SensitivityItem("Kanat Aralığı (S)", kotlin.math.abs(
                        recomputeR(bTb, bTf, bS * 1.1, bK, bHf) - recomputeR(bTb, bTf, bS * 0.9, bK, bHf)
                    )))
                    items.add(SensitivityItem("Malzeme İletkenliği (k)", kotlin.math.abs(
                        recomputeR(bTb, bTf, bS, bK * 1.1, bHf) - recomputeR(bTb, bTf, bS, bK * 0.9, bHf)
                    )))
                    items.add(SensitivityItem("Kanat Yüksekliği (hf)", kotlin.math.abs(
                        recomputeR(bTb, bTf, bS, bK, bHf * 1.1) - recomputeR(bTb, bTf, bS, bK, bHf * 0.9)
                    )))

                    // YENİ: Optimizasyon Isı Haritası - tb'yi bulunan en iyi değerde sabit tutup
                    // tf x S düzleminde tüm kombinasyonları tarayarak R_total dağılımını çıkarıyoruz.
                    val heatmapCells = mutableListOf<HeatmapCell>()
                    if (s.isOptimizationEnabled) {
                        val hmMinTf = (s.minFinThick.replace(",", ".").toDoubleOrNull() ?: 1.0) * lMul
                        val hmMaxTf = (s.maxFinThick.replace(",", ".").toDoubleOrNull() ?: 5.0) * lMul
                        val hmMinS = (s.minFinGap.replace(",", ".").toDoubleOrNull() ?: 2.0) * lMul
                        val hmMaxS = (s.maxFinGap.replace(",", ".").toDoubleOrNull() ?: 8.0) * lMul
                        val hmStepTf = 0.5
                        val hmStepS = 0.5

                        var hmTf = hmMinTf
                        while (hmTf <= hmMaxTf) {
                            var hmS = hmMinS
                            while (hmS <= hmMaxS) {
                                if (!isActive) return@withContext null
                                val cChHm = (s.envState.chassisCh.replace(",", ".").toDoubleOrNull() ?: ((bTb + hf)/lMul)) * lMul
                                val hmRes = HeatsinkSolver.calculate(
                                    W_mm = W, L_mm = L, tb_mm = bTb, tf_mm = hmTf, S_mm = hmS, hf_mm = hf,
                                    k_heatsink = k, materialName = s.selectedMaterialName,
                                    ambientTemp = amb, flowType = flowType, flowParam = flow,
                                    altitude_m = altitudeMeters, emissivity = emissivity,
                                    isTunnelEnabled = isTunnel, chassisCw = cCw, chassisCh = cChHm, fanMethod = fanMethod,
                                    heatSources = mappedSources,
                                    orientationIndex = s.selectedOrientationIndex,
                                    isEnclosedChassis = s.envState.isEnclosedChassis,
                                    fanCurve = parsedFanCurve,
                                    calibrationFactor = cFact,
                                    customDensityGcm3 = customDensityVal,
                                    customSpecificHeatJkgK = customSpecificHeatVal
                                )
                                val hmManufacturable = (hf / hmTf) <= 25.0
                                val hmFlowOk = if (flowType.contains("Doğal")) !hmRes.isChoked else hmRes.pressureDropPa <= maxAcceptablePressurePa
                                heatmapCells.add(HeatmapCell(tf = hmTf, s = hmS, rTotal = hmRes.rTotalSystem, isValid = hmManufacturable && hmFlowOk))
                                hmS += hmStepS
                            }
                            hmTf += hmStepTf
                        }
                    }

                    localBest = finalDesign.copy(sensitivityItems = items.sortedByDescending { it.deltaR }, heatmapCells = heatmapCells)
                }

                localBest
            }

            if (isActive && bestResult != null) {
                _uiState.update { it.copy(solverResult = bestResult, currentPage = 4, isCalculating = false) }
            }
        }
    }
}
