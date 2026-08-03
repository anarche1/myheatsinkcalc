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
import androidx.compose.foundation.horizontalScroll
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

// === Screens.kt: Tum ekranlar (WelcomeScreen, PageOne..Four, tab'lar, yardimci composable'lar) ===

@Composable
fun WelcomeScreen(
    modifier: Modifier,
    uiState: HeatsinkUiState,
    savedProjectNames: List<String>,
    onProjectNameChange: (String) -> Unit,
    onEnvStateChange: (EnvState) -> Unit,
    onShowInfo: (String, String) -> Unit,
    // DUZELTILDI: Bu 10 callback ilk refaktorde unutulmustu, "Birim Konfigurasyonu" karti
    // hala dogrudan viewModel.updateXUnit(...) cagirmaya calisip derleme hatasi veriyordu.
    onLengthUnitChange: (String) -> Unit,
    onTempUnitChange: (String) -> Unit,
    onAltitudeUnitChange: (String) -> Unit,
    onFlowUnitChange: (String) -> Unit,
    onVelocityUnitChange: (String) -> Unit,
    onPressureUnitChange: (String) -> Unit,
    onWeightUnitChange: (String) -> Unit,
    onPowerUnitChange: (String) -> Unit,
    onConductivityUnitChange: (String) -> Unit,
    onResistanceUnitChange: (String) -> Unit,
    onStartMetric: () -> Unit,
    onStartImperial: () -> Unit,
    onStartComplete: () -> Unit
) {
    val context = LocalContext.current
    // DUZELTILDI (Mimari Tutarlilik): Bu composable artik kendi icinde viewModel() cagirmiyor;
    // PageOne/Two/Three ile ayni desende state ve callback'leri parametre olarak aliyor.

    // YENİ: Kalibrasyon doğrulaması ve çekmece durumu buraya taşındı
    val calibrationFactor = uiState.envState.calibrationFactor
    val cFact = calibrationFactor.replace(",", ".").toDoubleOrNull()
    val isCalibInvalid = cFact == null || cFact < 0.1 || cFact > 10.0
    var isCalibrationExpanded by remember { mutableStateOf(false) }

    // YENİ: Proje adı, şu an düzenlenen projenin kendi adından farklı olup zaten kayıtlı
    // bir projeyle çakışıyorsa erken uyarı veriyoruz (Sayfa 4'e kadar gidip hesaplama yapmaya gerek kalmadan).
    val isDuplicateName = uiState.projectName.isNotBlank() && uiState.projectName != uiState.originalLoadedName && savedProjectNames.contains(uiState.projectName)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        val logoId = context.resources.getIdentifier("app_logo", "drawable", context.packageName)
        if (logoId != 0) {
            Image(
                painter = painterResource(id = logoId),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier
                .size(100.dp)
                .background(Color.White, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = android.R.drawable.ic_menu_edit), contentDescription = "Logo", modifier = Modifier.size(60.dp), tint = Color(0xFFD62828))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("My Heat Sink Calc", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Text("Dovahkiin V2.1", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))

        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2226))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Proje Kimliği", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                OutlinedTextField(
                    value = uiState.projectName,
                    onValueChange = { onProjectNameChange(it) },
                    label = { Text("Proje Adı (Örn: 50W LED Kasa)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isDuplicateName,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (isDuplicateName) {
                            Text("⚠️", modifier = Modifier.padding(end = 12.dp))
                        } else if (uiState.projectName.isNotBlank()) {
                            Text("✅", modifier = Modifier.padding(end = 12.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF81C784), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White, errorBorderColor = Color(0xFFE63946))
                )
                if (isDuplicateName) {
                    Text("⚠️ Bu isimde kayıtlı bir proje zaten var. Devam etmek için farklı bir isim seçin.", color = Color(0xFFE63946), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // YENİ: Harika çekmece tasarımımız (Accordion Menu) artık burada!
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isCalibrationExpanded = !isCalibrationExpanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2226))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CFD / Deney Kalibrasyon Çarpanı", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onShowInfo("Kalibrasyon Çarpanı Nedir?", buildString {
                        appendLine("CFD (SolidWorks vb.) veya gerçek test sonuçlarınızla analitik modeli kalibre etmenizi sağlar.")
                        appendLine()
                        append("Eğer testlerde %20 daha iyi soğutma gördüyseniz buraya 1.2 yazabilirsiniz. Orijinal hesaba dönmek için 1.0 yazın.")
                    }) }) {
                        Text("ℹ️", fontSize = 16.sp)
                    }
                    Text(if (isCalibrationExpanded) "▲" else "▼", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                }

                AnimatedVisibility(visible = isCalibrationExpanded) {
                    Column {
                        OutlinedTextField(
                            value = calibrationFactor,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { char -> char.isDigit() || char == '.' || char == ',' }
                                onEnvStateChange(uiState.envState.copy(calibrationFactor = filtered))
                            },
                            label = { Text("Sistem Düzeltme Katsayısı (Varsayılan: 1.0)", color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF81C784), unfocusedBorderColor = Color.DarkGray)
                        )
                        if (isCalibInvalid) {
                            Text("⚠️ Çarpan 0.1 ile 10.0 arasında olmalıdır.", color = Color(0xFFE63946), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                AnimatedVisibility(visible = !isCalibrationExpanded) {
                    val isDefault = calibrationFactor == "1.0" || calibrationFactor.isEmpty()
                    Text(
                        text = if (isDefault) "Mevcut Çarpan: 1.0 (Standart Analitik Model Aktif)" else "⚠️ Mevcut Çarpan: $calibrationFactor (Kalibre Edilmiş Model Aktif)",
                        color = if (isDefault) Color.Gray else Color(0xFFFFB703),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2226))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Birim Konfigürasyonu", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onStartMetric, modifier = Modifier.height(36.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784))
                        ) { Text("Metrik", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                        Button(
                            onClick = onStartImperial, modifier = Modifier.height(36.dp), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))
                        ) { Text("Imperial", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }
                }

                HorizontalDivider(color = Color(0xFF2C3136), thickness = 1.dp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UnitDropdown("Uzunluk", uiState.lengthUnit, listOf("mm", "cm", "m", "inch", "ft"), { onLengthUnitChange(it) }, Modifier.weight(1f))
                    UnitDropdown("Sıcaklık", uiState.tempUnit, listOf("°C", "°F", "K"), { onTempUnitChange(it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UnitDropdown("Rakım", uiState.altitudeUnit, listOf("m", "ft"), { onAltitudeUnitChange(it) }, Modifier.weight(1f))
                    UnitDropdown("Debi (Flow)", uiState.flowUnit, listOf("m³/s", "CFM", "L/min", "m³/h"), { onFlowUnitChange(it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UnitDropdown("Hava Hızı", uiState.velocityUnit, listOf("m/sec", "ft/min"), { onVelocityUnitChange(it) }, Modifier.weight(1f))
                    UnitDropdown("Basınç", uiState.pressureUnit, listOf("Pa", "in-H2O", "mm-H2O"), { onPressureUnitChange(it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UnitDropdown("Ağırlık", uiState.weightUnit, listOf("g", "kg", "lbs", "oz"), { onWeightUnitChange(it) }, Modifier.weight(1f))
                    UnitDropdown("Güç", uiState.powerUnit, listOf("W", "kW"), { onPowerUnitChange(it) }, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UnitDropdown("Termal İletkenlik", uiState.conductivityUnit, listOf("W/(m·K)", "BTU/(hr·ft·°F)"), { onConductivityUnitChange(it) }, Modifier.weight(1f))
                    UnitDropdown("Termal Direnç", uiState.resistanceUnit, listOf("°C/W", "°F/W", "K/W"), { onResistanceUnitChange(it) }, Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GÜNCELLEME: Çarpan hatalıysa VEYA proje adı çakışıyorsa İleri butonunu devre dışı bırakıyoruz.
        Button(
            onClick = onStartComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            enabled = uiState.projectName.isNotBlank() && !isCalibInvalid && !isDuplicateName,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF81C784),
                disabledContainerColor = Color(0xFF2C3136)
            )
        ) {
            Text(
                text = "Tasarım Ortamına Geç ➔",
                fontWeight = FontWeight.Bold,
                color = if(uiState.projectName.isNotBlank() && !isCalibInvalid && !isDuplicateName) Color.Black else Color.Gray,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Powered by AI", color = Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(24.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOneScreen(modifier: Modifier, isTargetTemperatureEnabled: Boolean,
                  onTargetTemperatureToggle: (Boolean) -> Unit,uiState: HeatsinkUiState,
                  targetTemperature: String,
                  onTargetTemperatureChange: (String) -> Unit, onBackPage: () -> Unit, width: String, onWidthChange: (String) -> Unit, length: String, onLengthChange: (String) -> Unit, baseThickness: String, onBaseThicknessChange: (String) -> Unit, finHeight: String, onFinHeightChange: (String) -> Unit, finThickness: String, onFinThicknessChange: (String) -> Unit, finSpacing: String, onFinSpacingChange: (String) -> Unit, selectedMaterialName: String, onMaterialNameChange: (String) -> Unit, selectedConductivity: String, onConductivityChange: (String) -> Unit, customDensity: String, onCustomDensityChange: (String) -> Unit, customSpecificHeat: String, onCustomSpecificHeatChange: (String) -> Unit, isOptimizationEnabled: Boolean, onOptimizationToggle: (Boolean) -> Unit, minBaseThick: String, onMinBaseThickChange: (String) -> Unit, maxBaseThick: String, onMaxBaseThickChange: (String) -> Unit, minFinThick: String, onMinFinThickChange: (String) -> Unit, maxFinThick: String, onMaxFinThickChange: (String) -> Unit, minFinGap: String, onMinFinGapChange: (String) -> Unit, maxFinGap: String, onMaxFinGapChange: (String) -> Unit, onShowInfo: (String, String) -> Unit, onNextPage: () -> Unit) {

    val materialList = remember { listOf(MaterialItem("Custom (Kendin Oluştur)", 0.0), MaterialItem("Al (1100 - Saf Alüminyum)", 237.0), MaterialItem("Al (2024-T6 Alaşım)", 177.0), MaterialItem("Al (6063-T5 Standart Profil)", 209.0), MaterialItem("Al (6063-T6 En Yaygın Soğutucu)", 200.0), MaterialItem("Al (6061-T4 Alaşım)", 154.0), MaterialItem("Al (6061-T6 Levha/Blok)", 167.0), MaterialItem("Cu (C11040 - Elektrolitik Bakır)", 401.0), MaterialItem("Carbon-Steel (Karbon Çeliği)", 60.5), MaterialItem("SST (AISI 304 Paslanmaz Çelik)", 14.9), MaterialItem("SST (AISI 316 Paslanmaz Çelik)", 13.4)) }
    var materialMenuExpanded by remember { mutableStateOf(false) }
    val dimUnit = uiState.lengthUnit
    val limitDiv = when(dimUnit) { "cm" -> 10.0; "m" -> 1000.0; "inch" -> 25.4; "ft" -> 304.8; else -> 1.0 }
    val maxW = 1000.0 / limitDiv; val minW = 10.0 / limitDiv; val maxL = 1000.0 / limitDiv; val minL = 10.0 / limitDiv
    val maxHf = 300.0 / limitDiv; val minHf = 5.0 / limitDiv; val maxTb = 100.0 / limitDiv; val minTbVal = 1.0 / limitDiv
    val maxTf = 50.0 / limitDiv; val maxS = 100.0 / limitDiv

    val displayTb = if (isOptimizationEnabled) "Oto. Hesap" else baseThickness
    val displayTf = if (isOptimizationEnabled) "Oto. Hesap" else finThickness
    val displayS = if (isOptimizationEnabled) "Oto. Hesap" else finSpacing
    val W = width.replace(",", ".").toDoubleOrNull() ?: 0.0
    val L = length.replace(",", ".").toDoubleOrNull() ?: 0.0
    val hf = finHeight.replace(",", ".").toDoubleOrNull() ?: 0.0
    val effectiveTb = if (isOptimizationEnabled) ((minBaseThick.replace(",", ".").toDoubleOrNull() ?: 2.0) + (maxBaseThick.replace(",", ".").toDoubleOrNull() ?: 12.0)) / 2.0 else baseThickness.replace(",", ".").toDoubleOrNull() ?: 0.0
    val effectiveTf = if (isOptimizationEnabled) ((minFinThick.replace(",", ".").toDoubleOrNull() ?: 1.0) + (maxFinThick.replace(",", ".").toDoubleOrNull() ?: 5.0)) / 2.0 else finThickness.replace(",", ".").toDoubleOrNull() ?: 0.0
    val effectiveS = if (isOptimizationEnabled) ((minFinGap.replace(",", ".").toDoubleOrNull() ?: 2.0) + (maxFinGap.replace(",", ".").toDoubleOrNull() ?: 8.0)) / 2.0 else finSpacing.replace(",", ".").toDoubleOrNull() ?: 0.0

    val isCustomSelected = selectedMaterialName == "Custom (Kendin Oluştur)"
    val kVal = selectedConductivity.replace(",", ".").toDoubleOrNull()
    val isMaterialInvalid = isCustomSelected && (kVal == null || kVal < 0.1 || kVal > 5000.0)
    // YENİ: Custom malzeme yoğunluk (g/cm³) ve özgül ısı (J/kg·K) doğrulaması
    val customDensityVal = customDensity.replace(",", ".").toDoubleOrNull()
    val customSpecificHeatVal = customSpecificHeat.replace(",", ".").toDoubleOrNull()
    val isCustomPropsInvalid = isCustomSelected && (customDensityVal == null || customDensityVal < 0.1 || customDensityVal > 25.0 || customSpecificHeatVal == null || customSpecificHeatVal < 50.0 || customSpecificHeatVal > 3000.0)
    val isGeometryInvalid = W < minW || W > maxW || L < minL || L > maxL || hf < minHf || hf > maxHf || effectiveTb < minTbVal || effectiveTb > maxTb || effectiveTf <= 0.0 || effectiveTf > maxTf || effectiveS <= 0.0 || effectiveS > maxS
    val isPageOneInvalid = isGeometryInvalid || isMaterialInvalid || isCustomPropsInvalid

    val finCalculation = remember(W, effectiveTf, effectiveS) { if (W > 0.0 && effectiveTf > 0.0 && effectiveS > 0.0) { val maxN = floor((W + effectiveS) / (effectiveTf + effectiveS)).toInt(); if (maxN > 0) { val usedWidth = (maxN * effectiveTf) + ((maxN - 1) * effectiveS); Pair(maxN, (W - usedWidth) / 2.0) } else Pair(0, 0.0) } else Pair(0, 0.0) }
    val maxFinCount = finCalculation.first
    val leftOverGap = finCalculation.second
    var renderCache by remember { mutableStateOf(HeatsinkRenderCache(100.0, 1.5, 2.5, 30.0, 5.0, 25, 2.0)) }

    if (!isGeometryInvalid && maxFinCount > 0) { renderCache = HeatsinkRenderCache(W, effectiveTf, effectiveS, hf, effectiveTb, maxFinCount, leftOverGap) } else { renderCache = HeatsinkRenderCache(if(W>0) W else 100.0, effectiveTf, effectiveS, if(hf>0) hf else 30.0, if(effectiveTb>0) effectiveTb else 5.0, 0, 0.0) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 70.dp)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Soğutucu Blok Malzemesi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ExposedDropdownMenuBox(expanded = materialMenuExpanded, onExpandedChange = { materialMenuExpanded = !materialMenuExpanded }) {
                OutlinedTextField(value = selectedMaterialName, onValueChange = {}, readOnly = true, label = { Text("Malzeme Seçimi") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = materialMenuExpanded) }, modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                ExposedDropdownMenu(expanded = materialMenuExpanded, onDismissRequest = { materialMenuExpanded = false }) { materialList.forEach { mat -> DropdownMenuItem(onClick = { onMaterialNameChange(mat.name); if (mat.name != "Custom (Kendin Oluştur)") onConductivityChange(mat.conductivity.toString()) else onConductivityChange(""); materialMenuExpanded = false }, text = { Text(mat.name, color = Color.White) }) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { LocalInputField(value = selectedConductivity, onValueChange = { if (isCustomSelected) onConductivityChange(it) }, label = if (isCustomSelected) "Termal İletkenlik Değerini Girin (k)" else "Malzeme Termal İletkenliği (Sabit)", unit = "W/m·K", enabled = isCustomSelected) }
                IconButton(onClick = { onShowInfo("Termal İletkenlik (k) Nedir?", "Termal iletkenlik, malzemenin ısıyı ne kadar hızlı ilettiğini gösterir. Bakır (401 W/mK) ısıyı alüminyuma (200 W/mK) göre iki kat daha hızlı yayar ancak daha ağır ve maliyetlidir.") }) { Text("ℹ️", fontSize = 18.sp) }
            }
            if (isMaterialInvalid) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE63946).copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("⚠️ Hatalı Malzeme İletkenliği", fontWeight = FontWeight.Bold, color = Color(0xFFE63946), fontSize = 13.sp); Text("Özel termal iletkenlik (k) değeri mühendislik sınırları olan 0.1 ile 5000 W/m·K arasında geçerli bir sayı olmalıdır.", color = Color(0xFFF1FAEE), fontSize = 12.sp) } } }

            // YENİ: Custom malzeme seçiliyken yoğunluk ve özgül ısı giriş alanları
            AnimatedVisibility(visible = isCustomSelected) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) { LocalInputField(value = customDensity, onValueChange = onCustomDensityChange, label = "Yoğunluk (Ağırlık İçin)", unit = "g/cm³") }
                        Box(modifier = Modifier.weight(1f)) { LocalInputField(value = customSpecificHeat, onValueChange = onCustomSpecificHeatChange, label = "Özgül Isı (Tau İçin)", unit = "J/kg·K") }
                    }
                    if (isCustomPropsInvalid) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE63946).copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("⚠️ Hatalı Yoğunluk / Özgül Isı", fontWeight = FontWeight.Bold, color = Color(0xFFE63946), fontSize = 13.sp); Text("Yoğunluk 0.1-25 g/cm³, özgül ısı 50-3000 J/kg·K aralığında olmalıdır.", color = Color(0xFFF1FAEE), fontSize = 12.sp) } } }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) { Text("Fiziksel Blok Ölçüleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { onShowInfo("Fiziksel Ölçülerin Akışa Etkisi", "Genişlik (W) akışın giriş kesitini, Uzunluk (L) ise havanın kanatlar içinde katettiği mesafeyi belirtir.") }) { Text("ℹ️", fontSize = 18.sp) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = width, onValueChange = onWidthChange, label = "Genişlik (W)", unit = dimUnit) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = length, onValueChange = onLengthChange, label = "Uzunluk (L)", unit = dimUnit) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = finHeight, onValueChange = onFinHeightChange, label = "Kanat Yük. (Hf)", unit = dimUnit) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = displayTb, onValueChange = onBaseThicknessChange, label = "Taban Kal. (tb)", unit = if(!isOptimizationEnabled) dimUnit else "", enabled = !isOptimizationEnabled) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = displayTf, onValueChange = onFinThicknessChange, label = "Kanat Kal. (tf)", unit = if(!isOptimizationEnabled) dimUnit else "", enabled = !isOptimizationEnabled) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = displayS, onValueChange = onFinSpacingChange, label = "Kanat Aralığı (S)", unit = if(!isOptimizationEnabled) dimUnit else "", enabled = !isOptimizationEnabled) } }

            AnimatedVisibility(visible = !isOptimizationEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Gerçek Zamanlı İmalat Kesit Görünümü", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Gray)

                    val textMeasurer = rememberTextMeasurer()

                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF15181B), RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            color = if (isGeometryInvalid) Color(0xFFE63946) else Color(0xFF2C3136),
                            shape = RoundedCornerShape(12.dp)
                        ), contentAlignment = Alignment.Center) {

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val padLeft = 30f
                            val padRight = 120f
                            val padTop = 30f
                            val padBottom = 70f

                            val hsW = w - padLeft - padRight
                            val hsH = h - padTop - padBottom

                            if (hsW <= 0f || hsH <= 0f) return@Canvas

                            val scaleX = if (renderCache.W > 0.0) hsW / renderCache.W.toFloat() else 1f

                            // ==========================================
                            // GÜNCELLEME: Hf ve tb Değerlerini Dinamik Hale Getirme
                            // ==========================================
                            // Kullanıcının girdiği değerleri güvenli bir şekilde çekiyoruz
                            val realHf = if (renderCache.hf > 0) renderCache.hf.toFloat() else 30f
                            val realTb = if (renderCache.tb > 0) renderCache.tb.toFloat() else 5f
                            val totalRealH = realHf + realTb

                            // Görselin bozulmaması ve okların sığması için min %15, max %85 sınır (clamp) koyuyoruz
                            val hfRatio = (realHf / totalRealH).coerceIn(0.15f, 0.85f)
                            val tbRatio = 1f - hfRatio

                            // Yeni hesaplanan oranları çizim alanına uyguluyoruz
                            val drawHf = hsH * hfRatio
                            val drawTb = hsH * tbRatio
                            val baseTopY = padTop + drawHf

                            val finColor = if (isGeometryInvalid) Color(0xFF6C757D).copy(alpha = 0.4f) else Color(0xFFADB5BD)
                            val baseColor = if (isGeometryInvalid) Color(0xFF4A4E53) else Color(0xFF6C757D)

                            // 1. Taban çizimi
                            drawRect(color = baseColor, topLeft = Offset(padLeft, baseTopY), size = Size(hsW, drawTb))

                            // 2. Kanat çizimi
                            for (i in 0 until renderCache.maxFinCount) {
                                val finLeftX = padLeft + ((renderCache.sideGap + (i * (renderCache.tf + renderCache.S))) * scaleX).toFloat()
                                val finWidthDraw = (renderCache.tf * scaleX).toFloat()
                                drawRect(color = finColor, topLeft = Offset(finLeftX, padTop), size = Size(finWidthDraw, drawHf))
                            }

                            // ==========================================
                            // 3. ÖLÇÜLENDİRME OKLARI (Sadece Harfler)
                            // ==========================================
                            val arrowColor = Color(0xFF81C784)
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            val textStyle = TextStyle(color = arrowColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            // W Oku (Yatay - Altta)
                            val wY = padTop + hsH + 25f
                            drawLine(color = arrowColor, start = Offset(padLeft, wY), end = Offset(padLeft + hsW, wY), strokeWidth = 2.5f)
                            drawLine(color = arrowColor, start = Offset(padLeft, wY - 8f), end = Offset(padLeft, wY + 8f), strokeWidth = 2.5f)
                            drawLine(color = arrowColor, start = Offset(padLeft + hsW, wY - 8f), end = Offset(padLeft + hsW, wY + 8f), strokeWidth = 2.5f)

                            val textW = "W"
                            val textLayoutW = textMeasurer.measure(textW, textStyle)
                            drawText(textMeasurer, textW, topLeft = Offset(padLeft + (hsW / 2f) - (textLayoutW.size.width / 2f), wY + 8f), style = textStyle)

                            // Hf Oku (Dikey - Sağ Üstte)
                            val arrowX = padLeft + hsW + 20f
                            drawLine(color = arrowColor.copy(alpha = 0.5f), start = Offset(padLeft + hsW, padTop), end = Offset(arrowX + 10f, padTop), strokeWidth = 1.5f, pathEffect = dashEffect)
                            drawLine(color = arrowColor.copy(alpha = 0.5f), start = Offset(padLeft + hsW, baseTopY), end = Offset(arrowX + 10f, baseTopY), strokeWidth = 1.5f, pathEffect = dashEffect)

                            drawLine(color = arrowColor, start = Offset(arrowX, padTop), end = Offset(arrowX, baseTopY), strokeWidth = 2.5f)
                            drawLine(color = arrowColor, start = Offset(arrowX - 8f, padTop), end = Offset(arrowX + 8f, padTop), strokeWidth = 2.5f)
                            drawLine(color = arrowColor, start = Offset(arrowX - 8f, baseTopY), end = Offset(arrowX + 8f, baseTopY), strokeWidth = 2.5f)

                            drawText(textMeasurer, "Hf", topLeft = Offset(arrowX + 12f, padTop + (drawHf / 2f) - 15f), style = textStyle)

                            // tb Oku (Dikey - Sağ Altta)
                            val bottomY = padTop + hsH
                            drawLine(color = arrowColor.copy(alpha = 0.5f), start = Offset(padLeft + hsW, bottomY), end = Offset(arrowX + 10f, bottomY), strokeWidth = 1.5f, pathEffect = dashEffect)

                            drawLine(color = arrowColor, start = Offset(arrowX, baseTopY), end = Offset(arrowX, bottomY), strokeWidth = 2.5f)
                            drawLine(color = arrowColor, start = Offset(arrowX - 8f, bottomY), end = Offset(arrowX + 8f, bottomY), strokeWidth = 2.5f)

                            drawText(textMeasurer, "tb", topLeft = Offset(arrowX + 12f, baseTopY + (drawTb / 2f) - 15f), style = textStyle)
                        }
                    }

                    Text("💡 Uzunluk (L) ölçüsü bloğun ekrandan içeriye (derinliğe) doğru olan mesafesidir.", color = Color(0xFF64B5F6), fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

                    if (isGeometryInvalid) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE63946).copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("⚠️ Hatalı Ölçü Toleransı", fontWeight = FontWeight.Bold, color = Color(0xFFE63946), fontSize = 13.sp); Text("Maksimum limitler: En/Boy ${String.format(java.util.Locale.US, "%.2f", maxW)} $dimUnit, Yükseklik ${String.format(java.util.Locale.US, "%.2f", maxHf)} $dimUnit ile sınırlandırılmıştır.", color = Color(0xFFF1FAEE), fontSize = 12.sp) } }
                    } else if (maxFinCount > 0) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Geometri Doğrulama Analizi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp); Text("Girilen ölçülere göre bu bloğa en fazla $maxFinCount adet kanat sığmaktadır. Kanat yerleşimi tabana göre tam ortalandı.", color = Color.White, fontSize = 12.sp); Text(text = String.format(java.util.Locale.US, "Yerleşim sonrası her bir kenarda kalan boşluk ölçüsü: %.2f %s", leftOverGap, dimUnit), color = Color(0xFF81C784), fontWeight = FontWeight.Medium, fontSize = 12.sp) } }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Termal Güvenlik Sınırı (Opsiyonel)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                    IconButton(onClick = { onShowInfo("Hedef Sıcaklık", "Analiz sonucunda bu değer aşılırsa sistem sizi uyarır.") }, modifier = Modifier
                                        .size(24.dp)
                                        .padding(start = 4.dp)) { Text("ℹ️", fontSize = 14.sp) }
                                }
                                Text("Tasarımın bu sıcaklığı aşmamasını denetler.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(checked = isTargetTemperatureEnabled, onCheckedChange = onTargetTemperatureToggle)
                        }
                        AnimatedVisibility(visible = isTargetTemperatureEnabled) {
                            LocalInputField(value = targetTemperature, onValueChange = onTargetTemperatureChange, label = "Maksimum Çip Sıcaklığı", unit = uiState.tempUnit)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2C3136))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Kısıtlı Alan Optimizasyonu", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                    IconButton(onClick = { onShowInfo("Optimizasyon Mimarisi", "Sınır toleransları arasında en verimli geometri hesaplanacaktır.") }, modifier = Modifier
                                        .size(24.dp)
                                        .padding(start = 4.dp)) { Text("ℹ️", fontSize = 14.sp) }
                                }
                                Text("Sınır toleranslarında otomatik hesaplama.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Switch(checked = isOptimizationEnabled, onCheckedChange = onOptimizationToggle)
                        }
                        AnimatedVisibility(visible = isOptimizationEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f))) { Text("✅ Optimizasyon Aktif", color = Color(0xFF81C784), fontSize = 11.sp, modifier = Modifier.padding(10.dp)) }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = minBaseThick, onValueChange = onMinBaseThickChange, label = "Min tb", unit = dimUnit) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = maxBaseThick, onValueChange = onMaxBaseThickChange, label = "Max tb", unit = dimUnit) } }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = minFinThick, onValueChange = onMinFinThickChange, label = "Min tf", unit = dimUnit) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = maxFinThick, onValueChange = onMaxFinThickChange, label = "Max tf", unit = dimUnit) } }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = minFinGap, onValueChange = onMinFinGapChange, label = "Min Gap (S)", unit = dimUnit) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = maxFinGap, onValueChange = onMaxFinGapChange, label = "Max Gap (S)", unit = dimUnit) } }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Surface(modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(65.dp), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBackPage, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))) { Text("← Ayarlar", fontWeight = FontWeight.Bold, color = Color.Black) }
                Button(onClick = onNextPage, enabled = !isPageOneInvalid, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, disabledContainerColor = Color(0xFF2C3136))) { Text("İleri ➔", fontWeight = FontWeight.Bold, color = if (!isPageOneInvalid) Color.Black else Color.Gray) }
            }
        }
    }
}

@Composable
fun PageTwoScreen(modifier: Modifier, blockWidthStr: String, blockLengthStr: String, dimUnit: String, powerUnit: String, heatSources: List<HeatSourceData>, onUpdateSources: (List<HeatSourceData>) -> Unit, onShowInfo: (String, String) -> Unit, onBackPage: () -> Unit, onStartSolution: () -> Unit) {
    val bW = blockWidthStr.replace(",", ".").toDoubleOrNull() ?: 1.0
    val bL = blockLengthStr.replace(",", ".").toDoubleOrNull() ?: 1.0
    val isImperial = dimUnit == "inch" || dimUnit == "ft"

    var srcName by remember { mutableStateOf("") }
    var srcWatt by remember { mutableStateOf("") }
    var srcWs by remember { mutableStateOf("") }
    var srcLs by remember { mutableStateOf("") }
    var srcX by remember { mutableStateOf("") }
    var srcY by remember { mutableStateOf("") }
    var hasTim by remember { mutableStateOf(false) }
    var timThick by remember { mutableStateOf("0.2") }
    var timK by remember { mutableStateOf("3.5") }

    // YENİ EKLENEN DEĞİŞKENLER: Özel Rjc (Junction-to-Case) değerleri
    var useCustomRjc by remember { mutableStateOf(false) }
    var customRjcVal by remember { mutableStateOf("") }

    var activePreviewSource by remember { mutableStateOf<HeatSourceData?>(null) }
    var editingSourceId by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) } // YENİ: silme onayı bekleyen kaynağın id'si

    // YENİ: activePreviewSource güncellenirken yeni değişkenler de boş olarak (false, "") geçiriliyor
    LaunchedEffect(srcName, srcWatt, srcWs, srcLs, srcX, srcY) { if (srcX.isNotBlank() && srcY.isNotBlank() && srcWs.isNotBlank() && srcLs.isNotBlank()) { activePreviewSource = HeatSourceData(id = -1L, name = srcName.ifBlank { "Önizleme" }, watt = srcWatt.ifBlank { "0" }, wS = srcWs, lS = srcLs, posX = srcX, posY = srcY, hasTim = false, timThick = "", timK = "", useCustomRjc = false, customRjcVal = "") } else if (editingSourceId == null) { activePreviewSource = null } }

    val tThick = timThick.replace(",", ".").toDoubleOrNull(); val tK = timK.replace(",", ".").toDoubleOrNull()
    val isTimInvalid = hasTim && (tThick == null || tThick < 0.01 || tThick > 10.0 || tK == null || tK < 0.1 || tK > 150.0)

    // YENİ: Rjc için hata doğrulama (Validation)
    val cRjc = customRjcVal.replace(",", ".").toDoubleOrNull()
    val isRjcInvalid = useCustomRjc && (cRjc == null || cRjc < 0.001 || cRjc > 100.0)

    val validationError = remember(heatSources, srcName, srcX, srcY, srcWs, srcLs, srcWatt, editingSourceId) { val listToCheck = if (editingSourceId != null) heatSources.filter { it.id != editingSourceId } else heatSources; checkFormValidation(srcName, srcX, srcY, srcWs, srcLs, srcWatt, bW, bL, listToCheck) }

    // YENİ: Butonun aktif olması için Rjc'nin de geçerli olması şartı eklendi (!isRjcInvalid)
    val isFormValid = srcName.isNotBlank() && srcWatt.isNotBlank() && (srcWatt.replace(",", ".").toDoubleOrNull() ?: 0.0) >= 0.0 && srcWs.isNotBlank() && (srcWs.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0.0 && srcLs.isNotBlank() && (srcLs.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0.0 && srcX.isNotBlank() && srcY.isNotBlank() && validationError == null && !isTimInvalid && !isRjcInvalid
    val totalWatt = heatSources.sumOf { it.watt.replace(",", ".").toDoubleOrNull() ?: 0.0 }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 75.dp)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))) { Row(modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Sistem Özet Raporu", fontWeight = FontWeight.Bold, color = Color.White); Text("Toplam Güç: $totalWatt $powerUnit", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Canlı Isıl Yerleşim Şeması", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { onShowInfo("Koordinat Sistemi", "X ve Y değerleri çipin SOL ALT KÖŞESİNİ referans alır.") }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) } }
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .background(Color(0xFF1E2226), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF3A3F44), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { HeatsinkVisualizerCanvas(bW = bW, bL = bL, sources = heatSources, activePreview = activePreviewSource, dimUnit = dimUnit, isImperial = isImperial, editingId = editingSourceId) }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (editingSourceId == null) "Yeni Isı Kaynağı Tanımla" else "Seçili Isı Kaynağını Düzenle", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(modifier = Modifier.weight(1.2f)) { LocalInputField(value = srcName, onValueChange = { newName -> srcName = newName.filter { ch -> ch != '|' && ch != '~' } /* DUZELTILDI: "|" ve "~" karakterleri serialize formatinda ayirici olarak kullanildigi icin isim icinde yasaklandi, aksi halde kayit/yukleme bozulabilir. */ }, label = "Kaynak Adı", unit = "", isText = true) }; Box(modifier = Modifier.weight(0.8f)) { LocalInputField(value = srcWatt, onValueChange = { srcWatt = it }, label = "Güç", unit = powerUnit) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = srcWs, onValueChange = { srcWs = it }, label = "Genişlik (Ws)", unit = dimUnit) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = srcLs, onValueChange = { srcLs = it }, label = "Uzunluk (Ls)", unit = dimUnit) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = srcX, onValueChange = { srcX = it }, label = "Konum X (Sol Kenar)", unit = dimUnit) }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = srcY, onValueChange = { srcY = it }, label = "Konum Y (Alt Kenar)", unit = dimUnit) } }

                    // TIM Giriş Alanı
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) { Text("Thermal Interface Material (TIM) Var mı?", fontSize = 12.sp, color = Color.White); IconButton(onClick = { onShowInfo("TIM Nedir?", "Termal macun veya ped (TIM), boşlukları doldurarak direnci düşürür.") }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) { Text("ℹ️", fontSize = 14.sp) } }; Checkbox(checked = hasTim, onCheckedChange = { hasTim = it }) }
                    AnimatedVisibility(visible = hasTim) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(modifier = Modifier.weight(1f)) { LocalInputField(value = timThick, onValueChange = { timThick = it }, label = "TIM Kalınlık", unit = "mm") }; Box(modifier = Modifier.weight(1f)) { LocalInputField(value = timK, onValueChange = { timK = it }, label = "TIM İletkenlik", unit = "W/mK") } }
                            if (isTimInvalid) { Text("⚠️ TIM Kalınlığı (0.01-10.0 mm) ve İletkenliği (0.1-150.0 W/mK) arasında olmalıdır.", color = Color(0xFFE63946), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }

                    // YENİ EKLENEN KISIM: Datasheet Rjc (Yayılma Direnci Override) Giriş Alanı
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("Üretici Datasheet Rjc (Opsiyonel)", fontSize = 12.sp, color = Color.White)
                            IconButton(onClick = { onShowInfo("Rjc (Junction-to-Case) Nedir?", "Çipinizin üretici belgesinde (Datasheet) net bir Rjc değeri verilmişse buraya girin. Analitik yayılma (Spreading) hesaplaması atlanarak doğrudan bu kesin değer kullanılır.") }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                                Text("ℹ️", fontSize = 14.sp)
                            }
                        }
                        Checkbox(checked = useCustomRjc, onCheckedChange = { useCustomRjc = it })
                    }
                    AnimatedVisibility(visible = useCustomRjc) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LocalInputField(value = customRjcVal, onValueChange = { customRjcVal = it }, label = "Datasheet Termal Direnci (Rjc)", unit = "°C/W")
                            if (isRjcInvalid) { Text("⚠️ Rjc değeri 0.001 ile 100.0 °C/W arasında olmalıdır.", color = Color(0xFFE63946), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }

                    validationError?.let { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                    // YENİ: Listeye ekleme ve güncelleme (Edit) butonlarına yeni Rjc verileri eklendi
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingSourceId != null) { Button(onClick = { srcName = ""; srcWatt = ""; srcWs = ""; srcLs = ""; srcX = ""; srcY = ""; hasTim = false; useCustomRjc = false; customRjcVal = ""; editingSourceId = null; activePreviewSource = null }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), shape = RoundedCornerShape(12.dp)) { Text("İptal Et", color = Color.White) } }
                        Button(onClick = {
                            if (isFormValid) {
                                if (editingSourceId == null) {
                                    val newList = heatSources + HeatSourceData(id = System.currentTimeMillis(), name = srcName.trim(), watt = srcWatt, wS = srcWs, lS = srcLs, posX = srcX, posY = srcY, hasTim = hasTim, timThick = timThick, timK = timK, useCustomRjc = useCustomRjc, customRjcVal = customRjcVal)
                                    onUpdateSources(newList)
                                } else {
                                    val newList = heatSources.map { if (it.id == editingSourceId) HeatSourceData(it.id, srcName.trim(), srcWatt, srcWs, srcLs, srcX, srcY, hasTim, timThick, timK, useCustomRjc, customRjcVal) else it }
                                    onUpdateSources(newList)
                                    editingSourceId = null
                                }
                                srcName = ""; srcWatt = ""; srcWs = ""; srcLs = ""; srcX = ""; srcY = ""; hasTim = false; useCustomRjc = false; customRjcVal = ""; activePreviewSource = null
                            }
                        }, modifier = Modifier.weight(1f), enabled = isFormValid, shape = RoundedCornerShape(12.dp)) { Text(if (editingSourceId == null) "Kaynağı Listeye Ekle ➕" else "Güncelle", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (heatSources.isNotEmpty()) {
                Text("Kayıtlı Isı Elemanları (Isı Yoğunluğu Analizli)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                heatSources.forEach { src ->
                    val sWVal = src.wS.replace(",", ".").toDoubleOrNull() ?: 1.0; val sLVal = src.lS.replace(",", ".").toDoubleOrNull() ?: 1.0; val wVal = src.watt.replace(",", ".").toDoubleOrNull() ?: 0.0; val areaUnitFactorToCm2 = when(dimUnit) { "mm" -> 0.01; "cm" -> 1.0; "m" -> 10000.0; "inch" -> 6.4516; "ft" -> 929.0304; else -> 0.01 }; val areaCm2 = (sWVal * sLVal) * areaUnitFactorToCm2; val heatFlux = if (areaCm2 > 0) wVal / areaCm2 else 0.0; val isHotspot = heatFlux > 5.0

                    // YENİ: Kart tıklandığında (düzenleme modunda) yeni Rjc değişkenlerinin de yüklenmesi sağlandı
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), onClick = { editingSourceId = src.id; srcName = src.name; srcWatt = src.watt; srcWs = src.wS; srcLs = src.lS; srcX = src.posX; srcY = src.posY; hasTim = src.hasTim; timThick = src.timThick; timK = src.timK; useCustomRjc = src.useCustomRjc; customRjcVal = src.customRjcVal; activePreviewSource = src }, colors = CardDefaults.cardColors(containerColor = if (editingSourceId == src.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Güç: ${src.watt} $powerUnit | Ölçü: ${src.wS}x${src.lS} $dimUnit | Konum: (${src.posX}, ${src.posY})", fontSize = 12.sp, color = Color.LightGray)
                                // YENİ: Listede Rjc ayarını ufak bir not olarak gösterme
                                if (src.useCustomRjc) {
                                    Text("🔗 Datasheet Rjc Aktif: ${src.customRjcVal} °C/W", fontSize = 11.sp, color = Color(0xFF64B5F6), fontWeight = FontWeight.Medium)
                                }
                                Text(text = String.format(java.util.Locale.US, "Isı Yoğunluğu: %.2f W/cm²", heatFlux), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isHotspot) Color(0xFFE63946) else Color(0xFF81C784))
                                if (isHotspot) Text("⚠️ Yüksek Isı Yoğunluğu (Hotspot Riski!)", color = Color(0xFFFFB703), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                            IconButton(onClick = { pendingDeleteId = src.id }) { Text("❌", color = Color.Red, fontSize = 12.sp) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Surface(modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(65.dp), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBackPage, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))) {
                    Text("← Geometriye Dön", fontWeight = FontWeight.Bold, color = Color.Black)
                }

                Button(onClick = onStartSolution, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), enabled = heatSources.isNotEmpty(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, disabledContainerColor = Color(0xFF2C3136))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ortam Koşulları ➔", fontWeight = FontWeight.Bold, color = if(heatSources.isNotEmpty()) Color.Black else Color.Gray)
                        if (heatSources.isEmpty()) {
                            Text("Lütfen ısı kaynağı ekleyin", fontSize = 9.sp, color = Color.Gray)
                        }
                    }
                }

            }
        }

        // YENİ: Isı kaynağı silme onay diyaloğu
        pendingDeleteId?.let { idToDelete ->
            val srcToDelete = heatSources.find { it.id == idToDelete }
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("Isı Kaynağını Sil", fontWeight = FontWeight.Bold) },
                text = { Text("\"${srcToDelete?.name ?: ""}\" adlı ısı kaynağını silmek istediğinize emin misiniz?") },
                confirmButton = {
                    TextButton(onClick = {
                        if (editingSourceId == idToDelete) { srcName = ""; srcWatt = ""; srcWs = ""; srcLs = ""; srcX = ""; srcY = ""; hasTim = false; useCustomRjc = false; customRjcVal = ""; editingSourceId = null; activePreviewSource = null }
                        onUpdateSources(heatSources.filter { it.id != idToDelete })
                        pendingDeleteId = null
                    }) { Text("Evet", color = Color(0xFFE63946), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Hayır") } },
                containerColor = Color(0xFF1E2226),
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }
    }
}


fun checkFormValidation(nameStr: String, xStr: String, yStr: String, wStr: String, lStr: String, wattStr: String, bW: Double, bL: Double, currentList: List<HeatSourceData>): String? {
    if (nameStr.trim().isBlank()) return "Bileşen adı boş olamaz."
    if (currentList.any { it.name.equals(nameStr.trim(), ignoreCase = true) }) return "'${nameStr.trim()}' adında başka bir bileşen zaten var!"
    val x = xStr.replace(",", ".").toDoubleOrNull() ?: return null; val y = yStr.replace(",", ".").toDoubleOrNull() ?: return null
    val w = wStr.replace(",", ".").toDoubleOrNull() ?: return null; val l = lStr.replace(",", ".").toDoubleOrNull() ?: return null
    val watt = wattStr.replace(",", ".").toDoubleOrNull() ?: return null
    if (watt < 0.0 || watt > 500.0) return "Bileşen gücü 0 ile 500W arasında olmalıdır!"
    if (x < 0 || y < 0 || w <= 0 || l <= 0) return "Ölçüler sıfırdan büyük olmalıdır."
    if (x + w > bW) return "Bileşen sağ taraftan blok sınırını taşmaktadır!"
    if (y + l > bL) return "Bileşen üst taraftan blok sınırını taşmaktadır!"
    currentList.forEach { src ->
        val sx = src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0; val sy = src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0
        val sw = src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0; val sl = src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0
        val xConflict = (x < sx + sw) && (x + w > sx); val yConflict = (y < sy + sl) && (y + l > sy)
        if (xConflict && yConflict) return "'${src.name}' bileşeni ile çakışma tespit edildi!"
    }
    return null
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageThreeScreen(modifier: Modifier, uiState: HeatsinkUiState, onOrientationChange: (Int) -> Unit, onShowInfo: (String, String) -> Unit, onBackPage: () -> Unit, onSaveEnvState: (EnvState) -> Unit, onCalculate: (ambientTemp: String, flowRate: String, flowType: String, channelHeight: String) -> Unit, onCancel: () -> Unit) {
    val env = uiState.envState
    val dimUnit = uiState.lengthUnit
    val tempUnit = uiState.tempUnit
    val altitudeUnit = uiState.altitudeUnit
    val flowUnit = uiState.flowUnit
    val velocityUnit = uiState.velocityUnit
    val pressureUnit = uiState.pressureUnit

    var ambientTemp by remember { mutableStateOf(env.ambientTemp) }
    var altitude by remember { mutableStateOf(env.altitude) }
    var selectedFlowType by remember { mutableStateOf(env.selectedFlowType) }
    var flowMenuExpanded by remember { mutableStateOf(false) }
    val flowOptions = remember { listOf("Doğal Taşınım (Serbest Hava)", "Zorlanmış Taşınım (Fanlı)") }
    var isEnclosedChassis by remember { mutableStateOf(env.isEnclosedChassis) }
    var selectedFanMethod by remember { mutableStateOf(env.selectedFanMethod) }
    var fanMethodExpanded by remember { mutableStateOf(false) }
    val fanMethodOptions = remember { listOf("Fan Eğrisi Girişi (P-Q Grafiği)", "Sabit Akış Hızı Girişi", "Sabit Fan Debisi Girişi") }
    var isTunnelEnabled by remember { mutableStateOf(env.isTunnelEnabled) }
    var chassisCw by remember { mutableStateOf(env.chassisCw) }
    var chassisCh by remember { mutableStateOf(env.chassisCh) }
    var fanCurvePoints by remember { mutableStateOf(env.fanCurvePoints) }
    var fixedSpeedStr by remember { mutableStateOf(env.fixedSpeedStr) }
    var fixedFlowStr by remember { mutableStateOf(env.fixedFlowStr) }

    val allEmissivityOptions = remember { listOf("Alüminyum Siyah Eloksallı (ε = 0.85)" to "0.85", "Alüminyum Renksiz/Mat Eloksallı (ε = 0.60)" to "0.60", "Alüminyum Ham / Frezelenmiş (ε = 0.20)" to "0.20", "Alüminyum Parlatılmış (ε = 0.05)" to "0.05", "Bakır Ağır Oksitlenmiş Mat (ε = 0.65)" to "0.65", "Bakır Parlak / Cilalı (ε = 0.05)" to "0.05", "Çelik Oksitlenmiş / Mat (ε = 0.80)" to "0.80", "Paslanmaz Çelik Mat (ε = 0.35)" to "0.35", "Mat Siyah Boyalı Yüzey (ε = 0.90)" to "0.90", "Kullanıcı Tanımlı (Custom)" to "") }
    val selectedMaterialName = uiState.selectedMaterialName
    val filteredEmissivityOptions = remember(selectedMaterialName) { allEmissivityOptions.filter { (name, _) -> when { selectedMaterialName.contains("Al", ignoreCase = true) -> name.startsWith("Alüminyum") || name.contains("Boyalı") || name.contains("Custom"); selectedMaterialName.contains("Cu", ignoreCase = true) || selectedMaterialName.contains("Bakır", ignoreCase = true) -> name.startsWith("Bakır") || name.contains("Boyalı") || name.contains("Custom"); selectedMaterialName.contains("Steel", ignoreCase = true) || selectedMaterialName.contains("SST", ignoreCase = true) || selectedMaterialName.contains("Çelik", ignoreCase = true) -> name.contains("Çelik") || name.contains("Boyalı") || name.contains("Custom"); else -> true } } }
    var selectedEmissivityName by remember { mutableStateOf(env.selectedEmissivityName) }
    var emissivityValueStr by remember { mutableStateOf(env.emissivityValueStr) }
    var emissivityMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(filteredEmissivityOptions) { if (filteredEmissivityOptions.isNotEmpty() && filteredEmissivityOptions.none { it.first == selectedEmissivityName }) { selectedEmissivityName = filteredEmissivityOptions.first().first; emissivityValueStr = filteredEmissivityOptions.first().second } }

    val tAmb = ambientTemp.replace(",", ".").toDoubleOrNull(); val altVal = altitude.replace(",", ".").toDoubleOrNull(); val epsVal = emissivityValueStr.replace(",", ".").toDoubleOrNull(); val cCwVal = chassisCw.replace(",", ".").toDoubleOrNull(); val cChVal = chassisCh.replace(",", ".").toDoubleOrNull()
    val maxTemp = when(tempUnit) { "°C" -> 100.0; "°F" -> 212.0; "K" -> 373.15; else -> 100.0 }
    val minTemp = when(tempUnit) { "°C" -> -50.0; "°F" -> -58.0; "K" -> 223.15; else -> -50.0 }
    val isTempValid = tAmb != null && tAmb >= minTemp && tAmb <= maxTemp
    val maxAlt = if (altitudeUnit == "m") 15000.0 else 49212.0
    val isAltValid = altVal != null && altVal >= 0.0 && altVal <= maxAlt
    val bW = uiState.width.replace(",", ".").toDoubleOrNull() ?: 0.0
    val tbOpt = if (uiState.isOptimizationEnabled) uiState.maxBaseThick.replace(",", ".").toDoubleOrNull() ?: 12.0 else uiState.baseThickness.replace(",", ".").toDoubleOrNull() ?: 0.0
    val hf = uiState.finHeight.replace(",", ".").toDoubleOrNull() ?: 0.0
    val totalH = tbOpt + hf
    val isBypassTooSmall = isTunnelEnabled && selectedFlowType.contains("Fanlı") && cCwVal != null && cChVal != null && (cCwVal < bW || cChVal < totalH)
    val isBypassInvalid = selectedFlowType.contains("Fanlı") && isTunnelEnabled && (cCwVal == null || cCwVal <= 0.0 || cChVal == null || cChVal <= 0.0)
    val isEmissivityInvalid = selectedEmissivityName.contains("Custom") && (epsVal == null || epsVal < 0.0 || epsVal > 1.0)

    // GÜNCELLEME: Maksimum limit sınırları kaldırıldı, yalnızca boş olup olmadığı ve sıfırdan küçük olup olmadığı kontrol ediliyor.
    val isFanDataInvalid = if (selectedFlowType.contains("Fanlı")) {
        when (selectedFanMethod) {
            "Sabit Akış Hızı Girişi" -> { val v = fixedSpeedStr.replace(",", ".").toDoubleOrNull(); v == null || v <= 0.0 }
            "Sabit Fan Debisi Girişi" -> { val q = fixedFlowStr.replace(",", ".").toDoubleOrNull(); q == null || q <= 0.0 }
            else -> {
                val hasBadValue = fanCurvePoints.any { val q = it.first.replace(",", ".").toDoubleOrNull(); val p = it.second.replace(",", ".").toDoubleOrNull(); q == null || q < 0.0 || p == null || p < 0.0 }
                // DUZELTILDI (Kritik Hata): Eskiden tek nokta veya tekrar eden debi (Q) degerleri
                // kabul ediliyordu. Bu durum HeatsinkSolver.interpolatePressure icinde payda sifir
                // olup NaN/Infinity uretmesine ve sonuclarin sessizce bozulmasina yol aciyordu.
                val parsedPoints = fanCurvePoints.mapNotNull {
                    val q = it.first.replace(",", ".").toDoubleOrNull()
                    val p = it.second.replace(",", ".").toDoubleOrNull()
                    if (q != null && p != null) Pair(q, p) else null
                }
                val hasDuplicateQ = parsedPoints.map { it.first }.distinct().size != parsedPoints.size
                hasBadValue || parsedPoints.size < 2 || hasDuplicateQ
            }
        }
    } else false

    val isFormInvalid = !isTempValid || !isAltValid || isEmissivityInvalid || isBypassInvalid || isBypassTooSmall || isFanDataInvalid

    val orientationOptions = remember { listOf("Dikey Kanatçık (Doğal Baca Etkisi)", "Yatay Kanatçık (Kısıtlı Akış Direnci)", "Yukarı Bakan (Yatay Üst Yerleşim)", "Aşağı Bakan (Ters Alt Yerleşim)") }
    var orientationMenuExpanded by remember { mutableStateOf(false) }

    fun saveState() {
        onSaveEnvState(EnvState(
            ambientTemp = ambientTemp, tempUnit = tempUnit, altitude = altitude, altitudeUnit = altitudeUnit,
            selectedFlowType = selectedFlowType, isEnclosedChassis = isEnclosedChassis, selectedFanMethod = selectedFanMethod,
            isTunnelEnabled = isTunnelEnabled, chassisCw = chassisCw, chassisCh = chassisCh, fanCurvePoints = fanCurvePoints,
            fanFlowUnit = flowUnit, fanPressureUnit = pressureUnit, fixedSpeedStr = fixedSpeedStr, fixedSpeedUnit = velocityUnit,
            fixedFlowStr = fixedFlowStr, fixedFlowUnit = flowUnit, selectedEmissivityName = selectedEmissivityName, emissivityValueStr = emissivityValueStr,
            calibrationFactor = env.calibrationFactor
        ))
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Analiz Ortamı Parametreleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("1. Ortam Sıcaklığı", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = ambientTemp, onValueChange = { ambientTemp = it }, label = { Text("Referans Sıcaklık Değeri") }, trailingIcon = { Text(tempUnit, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = { ambientTemp = when(tempUnit) { "°F" -> "68.0"; "K" -> "293.15"; else -> "25.0" } }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("Oda Sıcaklığı", fontSize = 10.sp) }; OutlinedButton(onClick = { ambientTemp = when(tempUnit) { "°F" -> "113.0"; "K" -> "318.15"; else -> "45.0" } }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), shape = RoundedCornerShape(8.dp)) { Text("Sıcak Pano", fontSize = 10.sp) } }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("2. Çalışma Yüksekliği / Rakım", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { onShowInfo("Rakım Etkisi", "Yüksek rakımlarda hava seyrekleştiği için soğutma performansı zayıflatır.") }) { Text("ℹ️", fontSize = 14.sp) } }
                    OutlinedTextField(value = altitude, onValueChange = { altitude = it }, label = { Text("Rakım Girişi") }, trailingIcon = { Text(altitudeUnit, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("3. Hava Akış Modu Seçimi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    ExposedDropdownMenuBox(expanded = flowMenuExpanded, onExpandedChange = { flowMenuExpanded = !flowMenuExpanded }) { TextField(value = selectedFlowType, onValueChange = {}, readOnly = true, label = { Text("Hava Sirkülasyon Tipi") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = flowMenuExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E2226), unfocusedContainerColor = Color(0xFF1E2226), focusedTextColor = Color.White, unfocusedTextColor = Color.White)); ExposedDropdownMenu(expanded = flowMenuExpanded, onDismissRequest = { flowMenuExpanded = false }) { flowOptions.forEach { option -> DropdownMenuItem(onClick = { selectedFlowType = option; flowMenuExpanded = false }, text = { Text(option, color = Color.White) }) } } }
                    AnimatedVisibility(visible = selectedFlowType.contains("Doğal")) { Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1D20), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("Kapalı Kasa / Pano İçi Montaj", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White); Text("Soğutucu hava akışı kesilmiş kapalı bir hacimdeyse sirkülasyon kaybını hesaplar.", fontSize = 11.sp, color = Color.Gray) }; Switch(checked = isEnclosedChassis, onCheckedChange = { isEnclosedChassis = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50), checkedTrackColor = Color(0xFF1B5E20))) } }
                    AnimatedVisibility(visible = selectedFlowType.contains("Fanlı")) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            HorizontalDivider(color = Color(0xFF2C3136)); Text("Fan Güç Parametreleri", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            ExposedDropdownMenuBox(expanded = fanMethodExpanded, onExpandedChange = { fanMethodExpanded = !fanMethodExpanded }) { TextField(value = selectedFanMethod, onValueChange = {}, readOnly = true, label = { Text("Özelleştirilmiş Giriş Metodu") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fanMethodExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1A1D20), unfocusedContainerColor = Color(0xFF1A1D20), focusedTextColor = Color.White, unfocusedTextColor = Color.White)); ExposedDropdownMenu(expanded = fanMethodExpanded, onDismissRequest = { fanMethodExpanded = false }) { fanMethodOptions.forEach { method -> DropdownMenuItem(onClick = { selectedFanMethod = method; fanMethodExpanded = false }, text = { Text(method, color = Color.White) }) } } }
                            if (selectedFanMethod.contains("Eğrisi")) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    fanCurvePoints.forEachIndexed { index, point -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Text("${index + 1}", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(16.dp)); OutlinedTextField(value = point.first, onValueChange = { newVal -> fanCurvePoints = fanCurvePoints.mapIndexed { i, p -> if (i == index) Pair(newVal, p.second) else p } }, label = { Text("Akış Hızı (${flowUnit})", fontSize = 9.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)); OutlinedTextField(value = point.second, onValueChange = { newVal -> fanCurvePoints = fanCurvePoints.mapIndexed { i, p -> if (i == index) Pair(p.first, newVal) else p } }, label = { Text("Basınç (${pressureUnit})", fontSize = 9.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)); if (fanCurvePoints.size > 1) { IconButton(onClick = { fanCurvePoints = fanCurvePoints.filterIndexed { i, _ -> i != index } }) { Text("❌", color = Color.Red, fontSize = 10.sp) } } } }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (fanCurvePoints.size < 15) { OutlinedButton(onClick = { fanCurvePoints = fanCurvePoints + Pair("", "") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Nokta Ekle [${fanCurvePoints.size}/15]", fontSize = 11.sp) } }; OutlinedButton(onClick = { fanCurvePoints = listOf(Pair("0.0", "150.0"), Pair("50.0", "0.0")) }, modifier = Modifier.weight(0.8f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)) { Text("Eğriyi Sıfırla", fontSize = 11.sp) } }
                                    val textMeasurer = rememberTextMeasurer()
                                    Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color(0xFF15181B), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF2C3136), RoundedCornerShape(8.dp))) {
                                        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                            val w = size.width; val h = size.height
                                            drawLine(color = Color.DarkGray, start = Offset(0f, h), end = Offset(w, h), strokeWidth = 1.5f); drawLine(color = Color.DarkGray, start = Offset(0f, 0f), end = Offset(0f, h), strokeWidth = 1.5f)
                                            drawText(textMeasurer, "P(${pressureUnit})", topLeft = Offset(4f, 4f), style = TextStyle(color = Color.Gray, fontSize = 9.sp)); drawText(textMeasurer, "Q(${flowUnit})", topLeft = Offset(w - 32f, h - 14f), style = TextStyle(color = Color.Gray, fontSize = 9.sp))
                                            val validPoints = fanCurvePoints.mapNotNull { val x = it.first.replace(",", ".").toDoubleOrNull(); val y = it.second.replace(",", ".").toDoubleOrNull(); if (x != null && y != null) Pair(x, y) else null }
                                            if (validPoints.size > 1) { val maxX = validPoints.maxOf { it.first }.coerceAtLeast(1.0); val maxY = validPoints.maxOf { it.second }.coerceAtLeast(1.0); var lastOffset: Offset? = null; validPoints.sortedBy { it.first }.forEach { p -> val cx = ((p.first / maxX) * w).toFloat(); val cy = (h - ((p.second / maxY) * h)).toFloat(); val currentOffset = Offset(cx, cy); drawCircle(color = Color(0xFF64B5F6), radius = 2.5f, center = currentOffset); lastOffset?.let { drawLine(color = Color(0xFF64B5F6), start = it, end = currentOffset, strokeWidth = 1.5f) }; lastOffset = currentOffset } }
                                        }
                                    }
                                }
                            }
                            if (selectedFanMethod.contains("Hızı")) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(value = fixedSpeedStr, onValueChange = { fixedSpeedStr = it }, label = { Text("Hava Giriş Hızı") }, trailingIcon = { Text(velocityUnit, color = Color.Gray, modifier = Modifier.padding(end = 8.dp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) } }
                            if (selectedFanMethod.contains("Debisi")) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedTextField(value = fixedFlowStr, onValueChange = { fixedFlowStr = it }, label = { Text("Volumetrik Fan Debisi") }, trailingIcon = { Text(flowUnit, color = Color.Gray, modifier = Modifier.padding(end = 8.dp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) } }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = selectedFlowType.contains("Fanlı")) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("Hava Tüneli Ölçüleri (Bypass Flow)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp); Text("Kanal geometrisini aktif ederek akış kaçış kayıplarını simüle edin.", fontSize = 11.sp, color = Color.Gray) }; Switch(checked = isTunnelEnabled, onCheckedChange = { isTunnelEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50), checkedTrackColor = Color(0xFF1B5E20))) }; AnimatedVisibility(visible = isTunnelEnabled) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { LocalInputField(value = chassisCw, onValueChange = { chassisCw = it }, label = "Kasa Genişliği (Cw)", unit = dimUnit); LocalInputField(value = chassisCh, onValueChange = { chassisCh = it }, label = "Kasa Yük. (Ch)", unit = dimUnit) }; val textMeasurer = rememberTextMeasurer(); Box(modifier = Modifier.weight(1f).height(105.dp).background(Color(0xFF15181B), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF2C3136), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) { val w = size.width; val h = size.height; drawRect(color = Color(0xFF64B5F6), topLeft = Offset(0f, 0f), size = Size(w, h), style = Stroke(width = 3f)); val hsW = w * 0.45f; val hsH = h * 0.40f; drawRect(color = Color(0xFFFFB703), topLeft = Offset((w - hsW) / 2f, h - hsH), size = Size(hsW, hsH)); val arrY = -6f; drawLine(Color.White, Offset(0f, arrY), Offset(w, arrY), strokeWidth = 2f); drawText(textMeasurer, "Cw: $chassisCw", topLeft = Offset(w/2 - 25f, arrY - 14f), style=TextStyle(color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Bold)); val arrX = -6f; drawLine(Color.White, Offset(arrX, 0f), Offset(arrX, h), strokeWidth = 2f); drawText(textMeasurer, "Ch: $chassisCh", topLeft = Offset(arrX - 25f, h/2 - 5f), style=TextStyle(color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Bold)) } } } } } } }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("4. Yüzey Kaplaması ve Radyasyon Ayarları", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { onShowInfo("Emisivite (Radyasyon Katsayısı) Nedir?", "Siyah eloksallı veya boyalı yüzeyler ısıyı mükemmel yayıp sıcaklığı düşürür.") }) { Text("ℹ️", fontSize = 14.sp) } }; ExposedDropdownMenuBox(expanded = emissivityMenuExpanded, onExpandedChange = { emissivityMenuExpanded = !emissivityMenuExpanded }) { TextField(value = selectedEmissivityName, onValueChange = {}, readOnly = true, label = { Text("Yüzey Kaplama / İşlem Türü") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = emissivityMenuExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E2226), unfocusedContainerColor = Color(0xFF1E2226), focusedTextColor = Color.White, unfocusedTextColor = Color.White)); ExposedDropdownMenu(expanded = emissivityMenuExpanded, onDismissRequest = { emissivityMenuExpanded = false }) { filteredEmissivityOptions.forEach { option -> DropdownMenuItem(onClick = { selectedEmissivityName = option.first; if (option.first != "Kullanıcı Tanımlı (Custom)") { emissivityValueStr = option.second }; emissivityMenuExpanded = false }, text = { Text(option.first, color = Color.White) }) } } }; AnimatedVisibility(visible = selectedEmissivityName.contains("Custom")) { OutlinedTextField(value = emissivityValueStr, onValueChange = { emissivityValueStr = it }, label = { Text("Özel Radyasyon Katsayısı (ε)") }, trailingIcon = { Text("0.0 - 1.0", color = Color.Gray, modifier = Modifier.padding(end = 8.dp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E2226), unfocusedContainerColor = Color(0xFF1E2226), focusedTextColor = Color.White, unfocusedTextColor = Color.White)) }; if (!selectedEmissivityName.contains("Custom")) Text(text = "Aktif Işınım Katsayısı (ε): $emissivityValueStr", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium) } }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("5. Soğutucunun Fiziksel Konumu", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    AnimatedVisibility(visible = selectedFlowType.contains("Fanlı")) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB703).copy(alpha = 0.15f))) { Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text("ℹ️", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp)); Text("Not: Zorlanmış taşınımda (Fanlı akış) havanın momentumu çok yüksek olduğundan, yerçekiminin soğutmaya etkisi ihmal edilebilir düzeydedir.", color = Color(0xFFFFB703), fontSize = 11.sp, lineHeight = 14.sp) } } }
                    ExposedDropdownMenuBox(expanded = orientationMenuExpanded, onExpandedChange = { orientationMenuExpanded = !orientationMenuExpanded }) { TextField(value = orientationOptions[uiState.selectedOrientationIndex], onValueChange = {}, readOnly = true, label = { Text("Yerleşim Açısı Seçimi") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = orientationMenuExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E2530), unfocusedContainerColor = Color(0xFF1E2530), focusedTextColor = Color.White, unfocusedTextColor = Color.White)); ExposedDropdownMenu(expanded = orientationMenuExpanded, onDismissRequest = { orientationMenuExpanded = false }) { orientationOptions.forEachIndexed { index, option -> DropdownMenuItem(onClick = { onOrientationChange(index); orientationMenuExpanded = false }, text = { Text(option, color = Color.White) }) } } }
                    val textMeasurer = rememberTextMeasurer()
                    Box(modifier = Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF15181B), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF3A3F44), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f; val hsSize = min(w, h) * 0.55f
                            val arrowX = w - 30f; val arrowY1 = h * 0.2f; val arrowY2 = h * 0.8f
                            drawLine(color = Color(0xFF81C784), start = Offset(arrowX, arrowY1), end = Offset(arrowX, arrowY2), strokeWidth = 4f)
                            drawLine(color = Color(0xFF81C784), start = Offset(arrowX, arrowY2), end = Offset(arrowX - 8f, arrowY2 - 12f), strokeWidth = 4f)
                            drawLine(color = Color(0xFF81C784), start = Offset(arrowX, arrowY2), end = Offset(arrowX + 8f, arrowY2 - 12f), strokeWidth = 4f)
                            // DÜZELTİLDİ: size + softWrap=false ile metin artık asla tek satırdan ikiye bölünmüyor, ok ile arası daha açık
                            val gravityLabel = "Yerçekimi (g)"
                            val gravityLayout = textMeasurer.measure(gravityLabel, TextStyle(color = Color.LightGray, fontSize = 9.sp))
                            drawText(textMeasurer, gravityLabel, topLeft = Offset(arrowX - (gravityLayout.size.width / 2f), (arrowY1 - 28f).coerceAtLeast(2f)), style = TextStyle(color = Color.LightGray, fontSize = 9.sp), softWrap = false, size = Size(gravityLayout.size.width.toFloat() + 4f, gravityLayout.size.height.toFloat() + 2f))

                            if (uiState.selectedOrientationIndex == 0) {
                                val rectSize = hsSize * 1.5f // YENİ: Örnek soğutucu 1.5 kat büyütüldü
                                val rectTopLeft = Offset(cx - rectSize / 2f, cy - rectSize / 2f)
                                drawRect(color = Color(0xFF90A4AE).copy(alpha = 0.3f), topLeft = rectTopLeft, size = Size(rectSize, rectSize))
                                drawRect(color = Color(0xFF90A4AE), topLeft = rectTopLeft, size = Size(rectSize, rectSize), style = Stroke(width = 4f))
                                val finCount = 5; val finWidth = rectSize / (finCount * 2 - 1)
                                for (i in 0 until finCount) { val finX = rectTopLeft.x + (i * 2 * finWidth); drawRect(color = if (selectedFlowType.contains("Doğal")) Color(0xFF64B5F6) else Color(0xFFADB5BD), topLeft = Offset(finX, rectTopLeft.y), size = Size(finWidth, rectSize)) }

                                // YENİ: Doğal taşınımda kanat aralıklarından yükselen sıcak hava akış okları
                                if (selectedFlowType.contains("Doğal")) {
                                    val warmColor = Color(0xFFE53935)
                                    // DÜZELTİLDİ: Ok sayısı artık kanat sayısından bağımsız, sabit 7
                                    val flowTopY = (rectTopLeft.y - 4f).coerceAtLeast(2f)
                                    val arrowCount = 7
                                    val spanW = rectSize * 0.85f
                                    val startX = cx - spanW / 2f
                                    val waveAmp = (spanW / arrowCount * 0.3f).coerceIn(1.5f, 4f)
                                    for (i in 0 until arrowCount) {
                                        val gapCenterX = startX + (spanW * (i + 0.5f) / arrowCount)
                                        val flowBottomY = rectTopLeft.y + rectSize * 0.25f
                                        val segH = (flowBottomY - flowTopY) / 2f
                                        for (off in floatArrayOf(-1.6f, 1.6f)) {
                                            val wavePath = androidx.compose.ui.graphics.Path()
                                            wavePath.moveTo(gapCenterX + off, flowBottomY)
                                            wavePath.cubicTo(gapCenterX + off - waveAmp, flowBottomY - segH * 0.5f, gapCenterX + off + waveAmp, flowBottomY - segH * 0.5f, gapCenterX + off, flowBottomY - segH)
                                            wavePath.cubicTo(gapCenterX + off - waveAmp, flowBottomY - segH * 1.5f, gapCenterX + off + waveAmp, flowBottomY - segH * 1.5f, gapCenterX + off, flowTopY)
                                            drawPath(path = wavePath, color = warmColor.copy(alpha = if (off < 0f) 0.85f else 0.5f), style = Stroke(width = 1.8f, cap = StrokeCap.Round))
                                        }
                                        drawLine(color = warmColor.copy(alpha = 0.85f), start = Offset(gapCenterX, flowTopY), end = Offset(gapCenterX - 5f, flowTopY + 7f), strokeWidth = 2.5f)
                                        drawLine(color = warmColor.copy(alpha = 0.85f), start = Offset(gapCenterX, flowTopY), end = Offset(gapCenterX + 5f, flowTopY + 7f), strokeWidth = 2.5f)
                                    }
                                    val warmLabel = "Verimli Taşınım ↑"
                                    val warmLayout = textMeasurer.measure(warmLabel, TextStyle(color = warmColor, fontSize = 9.sp, fontWeight = FontWeight.Medium))
                                    drawText(textMeasurer, warmLabel, topLeft = Offset(cx - (warmLayout.size.width / 2f), (rectTopLeft.y - 34f).coerceAtLeast(2f)), style = TextStyle(color = warmColor, fontSize = 9.sp, fontWeight = FontWeight.Medium), softWrap = false, size = Size(warmLayout.size.width.toFloat() + 4f, warmLayout.size.height.toFloat() + 2f))
                                }
                            } else {
                                val hsSizeScaled = hsSize * 1.5f // YENİ: Diğer üç yerleşim de Dikey Kanatçık ile aynı 1.5 kat büyütüldü
                                val rotationAngle = when (uiState.selectedOrientationIndex) { 1 -> 90f; 2 -> 0f; 3 -> 180f; else -> 0f }
                                rotate(degrees = rotationAngle, pivot = Offset(cx, cy)) {
                                    val baseThick = hsSizeScaled * 0.22f; val finLength = hsSizeScaled * 0.78f; val hsLeft = cx - (hsSizeScaled / 2f); val hsBottom = cy + (hsSizeScaled / 2f)
                                    drawRoundRect(color = Color(0xFF90A4AE), topLeft = Offset(hsLeft, hsBottom - baseThick), size = Size(hsSizeScaled, baseThick), cornerRadius = CornerRadius(4f))
                                    val finCount = 5; val finW = hsSizeScaled * 0.08f; val spacing = (hsSizeScaled - (finCount * finW)) / (finCount - 1)
                                    for(i in 0 until finCount) { val finX = hsLeft + (i * (finW + spacing)); drawRect(color = if (selectedFlowType.contains("Doğal")) Color(0xFF64B5F6) else Color(0xFFADB5BD), topLeft = Offset(finX, hsBottom - baseThick - finLength), size = Size(finW, finLength)) }
                                }
                                // YENİ: Yatay/Yukarı Bakan/Aşağı Bakan yerleşimlerde de yaklaşık akış oku (döndürülmemiş, gerçek yerçekimi yönüne göre)
                                if (selectedFlowType.contains("Doğal")) {
                                    val warmColor = Color(0xFFE53935) // DÜZELTİLDİ: turuncudan kırmızıya, sıcak hava artık net anlaşılıyor
                                    val (arrowCount, arrowAlpha, warmText) = when (uiState.selectedOrientationIndex) {
                                        1 -> Triple(7, 0.55f, "Kısıtlı Taşınım")
                                        2 -> Triple(7, 0.9f, "Güçlü Taşınım ↑")
                                        3 -> Triple(7, 0.35f, "Zayıf Taşınım")
                                        else -> Triple(7, 0.7f, "Sıcak Hava ↑")
                                    }
                                    val flowTopY = (cy - hsSizeScaled / 2f - 6f).coerceAtLeast(2f)
                                    val flowBottomY = cy - hsSizeScaled * 0.1f
                                    val spanW = hsSizeScaled * 0.8f
                                    val startX = cx - spanW / 2f
                                    val waveAmp = (spanW / arrowCount * 0.28f).coerceIn(2.5f, 6f)
                                    for (i in 0 until arrowCount) {
                                        val ax = startX + (spanW * (i + 0.5f) / arrowCount)
                                        val segH = (flowBottomY - flowTopY) / 2f
                                        // YENİ: Düz çizgi yerine kırmızı ÇİFT DALGALI ok (sıcak hava titreşimi)
                                        for (off in floatArrayOf(-2f, 2f)) {
                                            val wavePath = androidx.compose.ui.graphics.Path()
                                            wavePath.moveTo(ax + off, flowBottomY)
                                            wavePath.cubicTo(ax + off - waveAmp, flowBottomY - segH * 0.5f, ax + off + waveAmp, flowBottomY - segH * 0.5f, ax + off, flowBottomY - segH)
                                            wavePath.cubicTo(ax + off - waveAmp, flowBottomY - segH * 1.5f, ax + off + waveAmp, flowBottomY - segH * 1.5f, ax + off, flowTopY)
                                            drawPath(path = wavePath, color = warmColor.copy(alpha = if (off < 0f) arrowAlpha else arrowAlpha * 0.6f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
                                        }
                                        drawLine(color = warmColor.copy(alpha = arrowAlpha), start = Offset(ax, flowTopY), end = Offset(ax - 4f, flowTopY + 6f), strokeWidth = 2f)
                                        drawLine(color = warmColor.copy(alpha = arrowAlpha), start = Offset(ax, flowTopY), end = Offset(ax + 4f, flowTopY + 6f), strokeWidth = 2f)
                                    }
                                    val warmLayout = textMeasurer.measure(warmText, TextStyle(color = warmColor, fontSize = 9.sp, fontWeight = FontWeight.Medium))
                                    drawText(textMeasurer, warmText, topLeft = Offset(cx - (warmLayout.size.width / 2f), (flowTopY - 24f).coerceAtLeast(2f)), style = TextStyle(color = warmColor, fontSize = 9.sp, fontWeight = FontWeight.Medium), softWrap = false, size = Size(warmLayout.size.width.toFloat() + 4f, warmLayout.size.height.toFloat() + 2f))
                                }
                            }
                        }
                    }
                }
            }
            if (isFormInvalid) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE63946).copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Girdi Tolerans Hatası", fontWeight = FontWeight.Bold, color = Color(0xFFE63946), fontSize = 13.sp); val errorText = buildString { append("Lütfen form alanlarındaki hataları düzeltin:\n\n"); if (!isTempValid) append("• Sıcaklık değeri fiziksel sınırları olan -50 °C ile +100 °C arasında olmalıdır.\n"); if (!isAltValid) append("• Rakım/Yükseklik değeri 0 ile maksimum 15.000 metre arasında olmalıdır.\n"); if (isEmissivityInvalid) append("• Özel radyasyon katsayısı (ε) 0.0 ile 1.0 arasında geçerli bir sayı olmalıdır.\n"); if (isBypassInvalid) append("• Aktif tünel boyutları (Cw, Ch) boş bırakılamaz ve sıfırdan büyük olmalıdır.\n"); if (isBypassTooSmall) append("• Kasa ölçüleri (Cw, Ch) soğutucu bloğunun boyutlarından küçük olamaz!\n"); if (isFanDataInvalid) append("• Belirlenen fan hız/debi/eğri değerleri hatalı veya boş bırakılamaz. Fan eğrisi en az 2 farklı debi (Q) noktası içermeli ve tekrarlayan Q değerleri olmamalıdır.") }; Text(errorText.trimEnd(), color = Color(0xFFF1FAEE), fontSize = 12.sp, lineHeight = 16.sp) } } }
        }

        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { saveState(); onBackPage() }, modifier = Modifier.weight(1f).fillMaxHeight(), enabled = !uiState.isCalculating, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373), disabledContainerColor = Color(0xFF4A4E53))) { Text("Kaynaklara Dön", fontWeight = FontWeight.Bold, color = if (!uiState.isCalculating) Color.Black else Color.Gray) }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (uiState.isCalculating) {
                            onCancel()
                        } else if (!isFormInvalid) {
                            saveState()
                            val finalFlowParam = if (selectedFlowType.contains("Doğal")) "0.0" else { when (selectedFanMethod) { "Sabit Akış Hızı Girişi" -> fixedSpeedStr; "Sabit Fan Debisi Girişi" -> fixedFlowStr; else -> fanCurvePoints.firstOrNull()?.first ?: "0.0" } }
                            val finalChannelHeight = if (selectedFlowType.contains("Doğal") || !isTunnelEnabled) "0.0" else chassisCh
                            onCalculate(ambientTemp, finalFlowParam, selectedFlowType, finalChannelHeight)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(), enabled = uiState.isCalculating || !isFormInvalid, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isCalculating) Color(0xFFE63946) else MaterialTheme.colorScheme.primary, disabledContainerColor = Color(0xFF2C3136))
                ) {
                    if (uiState.isCalculating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("İptal Et ❌", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 13.sp)
                    } else {
                        Text("Çözümü Başlat", fontWeight = FontWeight.ExtraBold, color = if (!isFormInvalid) Color.Black else Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun PageFourScreen(modifier: Modifier, uiState: HeatsinkUiState, result: SolverResult, onBack: () -> Unit, onShowInfo: (String, String) -> Unit, onSaveProject: () -> Boolean, onAddToComparison: () -> Unit, onRemoveFromComparison: (Int) -> Unit) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabTitles = listOf("Termal Harita", "Performans", "Karşılaştır") // DÜZELTİLDİ: "Özet" kaldırıldı, içerik artık sadece PDF raporunda
    val context = LocalContext.current
    // DUZELTILDI (Mimari Tutarlilik): viewModel() burada dogrudan cagrilmiyor; kayit callback ile yapiliyor.

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex, containerColor = MaterialTheme.colorScheme.background, indicator = { tabPositions -> TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = MaterialTheme.colorScheme.primary, height = 3.dp) }) {
            tabTitles.forEachIndexed { index, title -> Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title, fontWeight = FontWeight.Bold, color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else Color.Gray, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }) }
        }
        Box(modifier = Modifier
            .weight(1f)
            .padding(horizontal = 16.dp)) {
            when (selectedTabIndex) {
                0 -> ThermalMapTab(uiState, result, onShowInfo)
                1 -> PerformanceTab(uiState, result, onShowInfo)
                2 -> ComparisonTab(uiState, onShowInfo, onAddToComparison, onRemoveFromComparison)
            }
        }
        Surface(modifier = Modifier
            .fillMaxWidth()
            .height(65.dp), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBack, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))) { Text("← Düzenle", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp) }
                Button(onClick = {
                    val saved = onSaveProject()
                    if (saved) {
                        Toast.makeText(context, "Proje Hafızaya Kaydedildi 💾", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "⚠️ Bu isimde bir proje zaten var. Lütfen farklı bir isim seçin.", Toast.LENGTH_LONG).show()
                    }
                }, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))) { Text("Kaydet", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp) }
                Button(onClick = { PdfGenerator.exportReport(context, uiState, result) }, modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("PDF İndir", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun ComparisonTab(uiState: HeatsinkUiState, onShowInfo: (String, String) -> Unit, onAddToComparison: () -> Unit, onRemoveFromComparison: (Int) -> Unit) {
    val entries = uiState.comparisonDesigns
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚖️ Tasarım Karşılaştırma", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), modifier = Modifier.weight(1f))
            IconButton(onClick = { onShowInfo("Tasarım Karşılaştırma Nedir?", buildString {
                appendLine("Farklı geometri, malzeme veya fan ayarlarıyla birkaç kez hesapladıktan sonra her sonucu buraya ekleyerek yan yana karşılaştırabilirsiniz (en fazla 4 tasarım).")
                appendLine()
                append("Yeşil renkli değer, o metrikte en iyi (en düşük direnç / en hafif) tasarımı gösterir.")
            }) }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) }
        }

        Button(
            onClick = onAddToComparison,
            enabled = entries.size < 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784), disabledContainerColor = Color(0xFF2C3136))
        ) {
            Text(if (entries.size < 4) "➕ Mevcut Tasarımı Karşılaştırmaya Ekle" else "Maksimum 4 Tasarım Eklendi", fontWeight = FontWeight.Bold, color = if (entries.size < 4) Color.Black else Color.Gray, fontSize = 13.sp)
        }

        if (entries.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Henüz karşılaştırmaya eklenen bir tasarım yok.", color = Color.LightGray, fontSize = 13.sp)
                    Text("Farklı ayarlarla birkaç kez hesapladıktan sonra yukarıdaki butonla her sonucu buraya ekleyip yan yana karşılaştırabilirsiniz.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            val resUnit = uiState.resistanceUnit
            val weightUnit = uiState.weightUnit
            fun rDisplay(r: Double) = if (resUnit == "°F/W") r * 1.8 else r
            fun wDisplay(g: Double) = when (weightUnit) { "kg" -> g / 1000.0; "lbs" -> g * 0.00220462; "oz" -> g * 0.035274; else -> g }

            val bestR = entries.minOfOrNull { it.result.rTotalSystem }
            val bestW = entries.minOfOrNull { it.result.totalWeightGram }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier
                    .padding(16.dp)
                    .horizontalScroll(rememberScrollState())) {

                    Column(modifier = Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(" ", fontSize = 11.sp)
                        Text("Malzeme", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Ölçüler (tb/tf/S)", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("R_total", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Ağırlık", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Verimlilik", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(" ", fontSize = 11.sp)
                    }

                    entries.forEachIndexed { index, entry ->
                        val r = entry.result
                        Column(modifier = Modifier
                            .width(130.dp)
                            .padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(entry.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(entry.materialName, color = Color.LightGray, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(String.format(java.util.Locale.US, "%.1f/%.1f/%.1f", r.usedTb, r.usedTf, r.usedS), color = Color.LightGray, fontSize = 10.sp)
                            Text(
                                String.format(java.util.Locale.US, "%.3f %s", rDisplay(r.rTotalSystem), resUnit),
                                color = if (r.rTotalSystem == bestR) Color(0xFF81C784) else Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                            Text(
                                String.format(java.util.Locale.US, "%.1f %s", wDisplay(r.totalWeightGram), weightUnit),
                                color = if (r.totalWeightGram == bestW) Color(0xFF81C784) else Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                            Text(String.format(java.util.Locale.US, "%% %.0f", r.finEfficiencyPercent), color = Color.LightGray, fontSize = 11.sp)
                            IconButton(onClick = { onRemoveFromComparison(index) }, modifier = Modifier.size(28.dp)) {
                                Text("✕", color = Color(0xFFE57373), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Text("Yeşil renkli değerler, eklenen tasarımlar arasında en düşük dirence (en iyi soğutma) veya en düşük ağırlığa sahip olanı gösterir.", color = Color.Gray, fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ThermalMapTab(uiState: HeatsinkUiState, result: SolverResult, onShowInfo: (String, String) -> Unit) {
    val lMul = when(uiState.lengthUnit) { "cm" -> 10.0; "m" -> 1000.0; "inch" -> 25.4; "ft" -> 304.8; else -> 1.0 }
    val bW = (uiState.width.replace(",", ".").toDoubleOrNull() ?: 100.0) * lMul
    val bL = (uiState.length.replace(",", ".").toDoubleOrNull() ?: 100.0) * lMul

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Canlı Termal Isı Dağılımı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { onShowInfo("Termal Harita Nedir?", "Bu şema çiplerin ulaştığı maksimum çekirdek sıcaklıklarını (T_junction) gösterir.\nMaviden (Soğuk) Kırmızıya (Kritik) doğru renklenir.") }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) }
        }

        Box(modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(Color(0xFF1E2226), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3A3F44), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { ThermalMapCanvas(uiState = uiState, bW = bW, bL = bL, chipResults = result.chipResults) }

        Text("💡 Üst: Ebat (Genişlik×Uzunluk) · Alt: Konum (X, Y)", color = Color(0xFF64B5F6), fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

        Text("Çip Sıcaklık Raporu (T_junction)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        result.chipResults.forEach { chip ->
            val customTarget = uiState.targetTemperature.replace(",", ".").toDoubleOrNull() ?: 90.0
            val activeLimit = if (uiState.isTargetTemperatureEnabled) customTarget else 90.0
            val isWarning = chip.tempJunction >= activeLimit || chip.isHotspot
            val displayTemp = when(uiState.tempUnit) { "°F" -> (chip.tempJunction * 1.8) + 32.0; "K" -> chip.tempJunction + 273.15; else -> chip.tempJunction }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chip.sourceInfo.name, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Isı Yoğunluğu: ${String.format(java.util.Locale.US, "%.1f", chip.heatFlux)} W/cm²", fontSize = 11.sp, color = Color.LightGray)
                        if (isWarning) {
                            val warningMessage = if (chip.isHotspot) { "⚠️ YÜKSEK ISI YOĞUNLUĞU (HOTSPOT RİSKİ!)" } else if (uiState.isTargetTemperatureEnabled) { "⚠️ HEDEFLENEN GÜVENLİK SINIRI ($customTarget °C) AŞILDI!" } else { "⚠️ KRİTİK DONANIM SICAKLIĞI (90°C) AŞILDI!" }
                            Card(modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE63946).copy(alpha = 0.15f))) { Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(text = warningMessage, color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) } }
                        }
                    }
                    Text(text = String.format(java.util.Locale.US, "%.1f %s", displayTemp, uiState.tempUnit), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if(chip.tempJunction >= 90.0) Color(0xFFE63946) else Color(0xFF81C784))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ThermalMapCanvas(uiState: HeatsinkUiState, bW: Double, bL: Double, chipResults: List<ChipResultData>) {
    val textMeasurer = rememberTextMeasurer()
    val minTemp = uiState.envState.ambientTemp.replace(",", ".").toDoubleOrNull() ?: when(uiState.tempUnit) { "°F" -> 68.0; "K" -> 293.15; else -> 20.0 }
    val maxTemp = minTemp + when(uiState.tempUnit) { "°F" -> 135.0; "K" -> 75.0; else -> 75.0 }
    val lMul = when(uiState.lengthUnit) { "cm" -> 10.0; "m" -> 1000.0; "inch" -> 25.4; "ft" -> 304.8; else -> 1.0 } // YENİ: mm'den kullanıcı birimine geri çevirmek için

    fun getJetColor(value: Float): Color {
        val v = value.coerceIn(0f, 1f)
        return when {
            v < 0.25f -> lerp(Color(0xFF000080), Color(0xFF00FFFF), v / 0.25f)
            v < 0.50f -> lerp(Color(0xFF00FFFF), Color(0xFF00FF00), (v - 0.25f) / 0.25f)
            v < 0.75f -> lerp(Color(0xFF00FF00), Color(0xFFFFFF00), (v - 0.50f) / 0.25f)
            else -> lerp(Color(0xFFFFFF00), Color(0xFFFF0000), (v - 0.75f) / 0.25f)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // DÜZELTİLDİ: Kenar boşlukları artık Canvas'ın kendi çizim alanı İÇİNDE ayrılıyor
        // (eskiden Modifier.padding dışarıda kalıyordu, cetvel rakamları çizim alanının dışına taşıp görünmüyordu)
        val padLeft = 46f; val padRight = 16f; val padTop = 64f; val padBottom = 40f // DÜZELTİLDİ: üst boşluk artırıldı, "Blok Boyutu" yazısı artık "100" ile çakışmıyor
        val availW = size.width - padLeft - padRight
        val availH = size.height - padTop - padBottom
        val blockRatio = bW / bL; val canvasRatio = availW / availH
        val cW: Float; val cH: Float; val offsetX: Float; val offsetY: Float
        if (blockRatio > canvasRatio) { cW = availW; cH = (availW / blockRatio).toFloat(); offsetX = padLeft; offsetY = padTop + (availH - cH) / 2f } else { cH = availH; cW = (availH * blockRatio).toFloat(); offsetX = padLeft + (availW - cW) / 2f; offsetY = padTop }
        val scaleX = cW / bW.toFloat(); val scaleY = cH / bL.toFloat()

        drawRect(color = getJetColor(0f), topLeft = Offset(offsetX, offsetY), size = Size(cW, cH))
        val maxRadius = kotlin.math.hypot(cW.toDouble(), cH.toDouble()).toFloat()

        clipRect(left = offsetX, top = offsetY, right = offsetX + cW, bottom = offsetY + cH) {
            chipResults.forEach { chip ->
                val src = chip.sourceInfo
                val sX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY; val sW = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sL = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY
                val drawX = offsetX + sX.toFloat(); val drawY = (offsetY + cH - sY - sL).toFloat()
                val centerX = drawX + (sW.toFloat() / 2f); val centerY = drawY + (sL.toFloat() / 2f); val centerOffset = Offset(centerX, centerY)

                val displayTemp = when(uiState.tempUnit) { "°F" -> (chip.tempJunction * 1.8) + 32.0; "K" -> chip.tempJunction + 273.15; else -> chip.tempJunction }
                val chipRatio = ((displayTemp - minTemp) / (maxTemp - minTemp)).coerceIn(0.0, 1.0).toFloat()

                val colorStops = mutableListOf<Pair<Float, Color>>()
                for (i in 0..15) { val distanceFraction = i.toFloat() / 15; val currentTempRatio = chipRatio * (1f - distanceFraction); val alpha = Math.pow((1f - distanceFraction).toDouble(), 1.5).toFloat(); colorStops.add(distanceFraction to getJetColor(currentTempRatio).copy(alpha = alpha.coerceIn(0f, 1f))) }
                val gradientRadius = maxRadius * 0.9f
                val radialBrush = Brush.radialGradient(colorStops = colorStops.toTypedArray(), center = centerOffset, radius = gradientRadius)
                drawCircle(brush = radialBrush, radius = gradientRadius, center = centerOffset)
            }

            chipResults.forEach { chip ->
                val src = chip.sourceInfo
                val sX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY; val sW = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sL = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY
                val drawX = offsetX + sX.toFloat(); val drawY = (offsetY + cH - sY - sL).toFloat()
                val boxW = sW.toFloat(); val boxH = sL.toFloat()
                val displayTemp = when(uiState.tempUnit) { "°F" -> (chip.tempJunction * 1.8) + 32.0; "K" -> chip.tempJunction + 273.15; else -> chip.tempJunction }
                drawRect(color = Color.Black.copy(alpha = 0.8f), topLeft = Offset(drawX, drawY), size = Size(boxW, boxH), style = Stroke(width = 3f))

                // DÜZELTİLDİ: Ebat ve koordinat artık kutunun DIŞINDA (üstünde/altında) - kutu içi sadece sıcaklığa ait, göz yormuyor
                val tempText = String.format(java.util.Locale.US, "%.0f°", displayTemp)
                val tempStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                val tempLayout = textMeasurer.measure(tempText, tempStyle)
                drawText(textMeasurer = textMeasurer, text = tempText, style = tempStyle, topLeft = Offset(drawX + (boxW / 2f) - (tempLayout.size.width / 2f), drawY + (boxH / 2f) - (tempLayout.size.height / 2f)))

                val dispWs = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) / lMul
                val dispLs = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) / lMul
                val sizeText = String.format(java.util.Locale.US, "%.0fx%.0f", dispWs, dispLs)
                val sizeStyle = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                val sizeLayout = textMeasurer.measure(sizeText, sizeStyle)
                drawText(textMeasurer = textMeasurer, text = sizeText, style = sizeStyle, topLeft = Offset(drawX + (boxW / 2f) - (sizeLayout.size.width / 2f), drawY - sizeLayout.size.height.toFloat() - 6f))

                val dispPosX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) / lMul
                val dispPosY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) / lMul
                val posText = String.format(java.util.Locale.US, "(%.0f, %.0f)", dispPosX, dispPosY)
                val posStyle = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                val posLayout = textMeasurer.measure(posText, posStyle)
                drawText(textMeasurer = textMeasurer, text = posText, style = posStyle, topLeft = Offset(drawX + (boxW / 2f) - (posLayout.size.width / 2f), drawY + boxH + 6f))
            }
        }
        drawRect(color = Color.Black, topLeft = Offset(offsetX, offsetY), size = Size(cW, cH), style = Stroke(width = 4f))

        // YENİ: Blok boyutu başlığı + X/Y cetvel rakamları
        val dimUnit = uiState.lengthUnit
        val dispBW = bW / lMul; val dispBL = bL / lMul
        val maxDim = maxOf(dispBW, dispBL)
        val formatStr = if (maxDim <= 2.0) "%.2f" else if (maxDim <= 10.0) "%.1f" else "%.0f"
        drawText(textMeasurer = textMeasurer, text = String.format(java.util.Locale.US, "Blok Boyutu: $formatStr x $formatStr %s", dispBW, dispBL, dimUnit), style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold), topLeft = Offset(offsetX, 4f))

        val numRulerSteps = 4
        for (i in 0..numRulerSteps) {
            val ratio = i.toFloat() / numRulerSteps
            val rX = offsetX + (cW * ratio); val valX = dispBW * ratio
            drawText(textMeasurer = textMeasurer, text = String.format(java.util.Locale.US, formatStr, valX), style = TextStyle(color = Color.LightGray, fontSize = 9.sp), topLeft = Offset(rX - 10f, offsetY + cH + 8f))
            val rY = (offsetY + cH) - (cH * ratio); val valY = dispBL * ratio
            drawText(textMeasurer = textMeasurer, text = String.format(java.util.Locale.US, formatStr, valY), style = TextStyle(color = Color.LightGray, fontSize = 9.sp), topLeft = Offset((offsetX - 34f).coerceAtLeast(2f), rY - 6f))
        }
    }
}

// YENİ: Isıl Darboğaz ve Duyarlılık Analizi çubukları için önem-renk skalası (kırmızı=en önemli, mavi=en önemsiz)
fun importanceColor(ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return when {
        r < 0.25f -> lerp(Color(0xFF64B5F6), Color(0xFF26C6DA), r / 0.25f)
        r < 0.50f -> lerp(Color(0xFF26C6DA), Color(0xFF81C784), (r - 0.25f) / 0.25f)
        r < 0.75f -> lerp(Color(0xFF81C784), Color(0xFFFFEB3B), (r - 0.50f) / 0.25f)
        else -> lerp(Color(0xFFFFEB3B), Color(0xFFE63946), (r - 0.75f) / 0.25f)
    }
}

@Composable
fun PerformanceTab(uiState: HeatsinkUiState, result: SolverResult, onShowInfo: (String, String) -> Unit) {
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        // YENİ: Optimizasyon aramasi gecerli bir kombinasyon bulamadiginda, kullaniciyi
        // korkutmadan bilgilendiren sakin tonlu bir not (hata degil, bilgi).
        if (result.usedFallbackDesign) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB703).copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ℹ️ Optimizasyon Notu", fontWeight = FontWeight.Bold, color = Color(0xFFFFB703), fontSize = 13.sp)
                    Text("Hesaplama başarıyla tamamlandı. Ancak belirttiğiniz Min/Maks arama aralığında hava akışı veya üretilebilirlik sınırlarını karşılayan daha iyi bir kombinasyon bulunamadığı için, sonuçlar Sayfa 1'de doğrudan girdiğiniz ölçülere göre hesaplandı (optimizasyon kapalıymış gibi). Daha iyi bir sonuç için arama aralığını genişletmeyi veya fan gücünü artırmayı deneyebilirsiniz.", color = Color.White, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 1. BÖLÜM: KRİTİK UYARILAR
        val showChokingWarning = result.isChoked && uiState.envState.selectedFlowType.contains("Doğal")
        val showRadiationWarning = result.viewFactor < 0.60
        val showBypassWarning = result.pressureDropPa > 0 && result.bypassFactor < 0.98

        if (showChokingWarning || showRadiationWarning || showBypassWarning) {
            Text("⚠️ Kritik Sistem Uyarıları", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))

            if (showChokingWarning) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE63946).copy(alpha = 0.15f))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Hava Akışı Boğulması (Choking)", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252), fontSize = 13.sp)
                        Text("Kanatçıklar çok sıkışık, doğal taşınım durma noktasına geldi. Kanat aralığını (S) artırın.", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            if (showRadiationWarning) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.15f))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Isıl Işınım (Radyasyon) Blokajı", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252), fontSize = 13.sp)
                        Text(String.format(java.util.Locale.US, "Radyasyonun yaklaşık %% %.0f'si kanatların birbirine bakması nedeniyle içeride hapsoluyor (Kavite Etkisi).", (1.0 - result.viewFactor) * 100), color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            if (showBypassWarning) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB703).copy(alpha = 0.15f))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Hava Kaçağı (Bypass Flow)", fontWeight = FontWeight.Bold, color = Color(0xFFFFB703), fontSize = 13.sp)
                        Text(String.format(java.util.Locale.US, "Havanın yaklaşık %% %.0f'si kanatlara girmeden etraftan kaçıyor. Soğutma verimi düştü.", (1.0 - result.bypassFactor) * 100), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))
        }

        // 2. BÖLÜM: TEMEL PERFORMANS SONUÇLARI
        Text("📊 Temel Performans Verileri", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Toplam Sistem Direnci (R_total)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val resValue = if(uiState.resistanceUnit == "°F/W") result.rTotalSystem * 1.8 else result.rTotalSystem
                Text(text = String.format(java.util.Locale.US, "%.3f %s", resValue, uiState.resistanceUnit), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Mükemmel soğutma için bu değerin olabildiğince düşük olması gerekir.", fontSize = 11.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Verimlilik (η)", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                        Text(String.format(java.util.Locale.US, "%% %.1f", result.finEfficiencyPercent), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = if(result.finEfficiencyPercent >= 70) Color(0xFF81C784) else Color(0xFFFFB703))
                    }
                    LinearProgressIndicator(progress = (result.finEfficiencyPercent / 100.0).toFloat(), modifier = Modifier.fillMaxWidth().height(6.dp), color = if(result.finEfficiencyPercent >= 70) Color(0xFF81C784) else Color(0xFFFFB703), trackColor = Color(0xFF2C3136), strokeCap = StrokeCap.Round)
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Termal Süre", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
                        IconButton(onClick = { onShowInfo("Zaman Sabiti (Tau) Nedir?", "Sistemin kütlesi, malzemesi ve ısıl kapasitesine bağlı olarak, maksimum denge sıcaklığına ulaşması için geçen tahmini süredir.") }, modifier = Modifier.size(16.dp).offset(y = (-2).dp)) { Text("ℹ️", fontSize = 12.sp) }
                    }
                    val totalSeconds = result.timeConstantSeconds.toInt()
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    Text(if (minutes > 0) "$minutes dk $seconds sn" else "$seconds sn", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tahmini Ağırlık", fontSize = 12.sp, color = Color.Gray)
                    val wValue = when(uiState.weightUnit) { "kg" -> result.totalWeightGram / 1000.0; "lbs" -> result.totalWeightGram * 0.00220462; "oz" -> result.totalWeightGram * 0.035274; else -> result.totalWeightGram }
                    val wFormat = if(uiState.weightUnit == "kg" || uiState.weightUnit == "lbs") "%.3f %s" else "%.1f %s"
                    Text(String.format(java.util.Locale.US, wFormat, wValue, uiState.weightUnit), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Toplam Hacim", fontSize = 12.sp, color = Color.Gray)
                    val isImperial = uiState.lengthUnit == "inch" || uiState.lengthUnit == "ft"
                    val vValue = if(isImperial) result.totalVolumeCm3 * 0.0610237 else result.totalVolumeCm3
                    val vUnit = if(isImperial) "in³" else "cm³"
                    Text(String.format(java.util.Locale.US, "%.1f %s", vValue, vUnit), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
        }

        // 3. BÖLÜM: AKIŞ DİNAMİKLERİ VE P-Q GRAFİĞİ
        AnimatedVisibility(visible = result.pressureDropPa > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌬️ Akış Dinamikleri ve P-Q Grafiği", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), modifier = Modifier.weight(1f))
                    IconButton(onClick = { onShowInfo("P-Q Grafiği Nedir?", "Mavi Çizgi: Fanınızın üretebileceği maksimum güç kapasitesi (Fan Curve).\nKırmızı Çizgi: Soğutucunun havaya gösterdiği direnç (System Resistance).\nYeşil Hedef (Operating Point): Sistemin ve fanın dengelendiği, gerçekte çalışacağı debi ve basınç noktasıdır.") }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) }
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val pValue = when(uiState.pressureUnit) { "in-H2O" -> result.pressureDropPa * 0.00401865; "mm-H2O" -> result.pressureDropPa * 0.101972; else -> result.pressureDropPa }
                            val displayFlow = when(uiState.flowUnit) { "CFM" -> result.operatingFlowM3s * 2118.88; "L/min" -> result.operatingFlowM3s * 60000.0; "m³/h" -> result.operatingFlowM3s * 3600.0; else -> result.operatingFlowM3s }

                            Column {
                                Text("Sistem Basınç Kaybı (ΔP)", color = Color.Gray, fontSize = 11.sp)
                                Text(String.format(java.util.Locale.US, "%.1f %s", pValue, uiState.pressureUnit), fontWeight = FontWeight.Bold, color = Color(0xFFE63946), fontSize = 15.sp)
                            }
                            if (result.operatingFlowM3s >= 0.0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Dengelenen Debi (Q)", color = Color.Gray, fontSize = 11.sp)
                                    Text(String.format(java.util.Locale.US, "%.1f %s", displayFlow, uiState.flowUnit), fontWeight = FontWeight.Bold, color = Color(0xFF81C784), fontSize = 15.sp)
                                }
                            }
                        }

                        val rawCurve = uiState.envState.fanCurvePoints.mapNotNull {
                            val q = it.first.replace(",", ".").toDoubleOrNull()
                            val p = it.second.replace(",", ".").toDoubleOrNull()
                            if (q != null && p != null) Pair(q, p) else null
                        }.sortedBy { it.first }

                        val opQ = when(uiState.flowUnit) { "CFM" -> result.operatingFlowM3s * 2118.88; "L/min" -> result.operatingFlowM3s * 60000.0; "m³/h" -> result.operatingFlowM3s * 3600.0; else -> result.operatingFlowM3s }
                        val opP = when(uiState.pressureUnit) { "in-H2O" -> result.pressureDropPa * 0.00401865; "mm-H2O" -> result.pressureDropPa * 0.101972; else -> result.pressureDropPa }

                        if (uiState.envState.selectedFanMethod.contains("Eğrisi") && rawCurve.size > 1) {
                            Spacer(modifier = Modifier.height(4.dp))

                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(Color(0xFF15181B), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF2C3136), RoundedCornerShape(8.dp))) {

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    // GÜNCELLEME: Çakışmayı önleyen kusursuz iç boşluk ayarları
                                    val padLeft = 70f    // Sadece rakamlara yetecek kadar
                                    val padBottom = 60f  // X ekseni değerleri ve yazısı için
                                    val padTop = 45f     // Basınç (P) başlığı için geniş alan
                                    val padRight = 30f

                                    // Çizilebilir asıl grafik alanı
                                    val gw = w - padLeft - padRight
                                    val gh = h - padTop - padBottom

                                    val maxQ = maxOf(rawCurve.maxOf { it.first }, opQ * 1.1).coerceAtLeast(1.0)
                                    val maxP = maxOf(rawCurve.maxOf { it.second }, opP * 1.5).coerceAtLeast(1.0)

                                    // --- 1. EKSEN VE IZGARALARIN (GRID) ÇİZİLMESİ ---
                                    val gridEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    val numGridLines = 4

                                    // Yatay çizgiler ve P ekseni değerleri
                                    for (i in 0..numGridLines) {
                                        val y = padTop + gh - (i * gh / numGridLines)
                                        val valP = (i * maxP / numGridLines)
                                        if (i > 0) {
                                            drawLine(color = Color.DarkGray.copy(alpha = 0.5f), start = Offset(padLeft, y), end = Offset(padLeft + gw, y), strokeWidth = 1.5f, pathEffect = gridEffect)
                                        }
                                        // Değerler çizginin hemen soluna yerleştirildi
                                        drawText(textMeasurer, String.format(java.util.Locale.US, "%.1f", valP), topLeft = Offset(padLeft - 50f, y - 15f), style = TextStyle(color = Color.Gray, fontSize = 9.sp))
                                    }

                                    // Dikey çizgiler ve Q ekseni değerleri
                                    for (i in 0..numGridLines) {
                                        val x = padLeft + (i * gw / numGridLines)
                                        val valQ = (i * maxQ / numGridLines)
                                        if (i > 0) {
                                            drawLine(color = Color.DarkGray.copy(alpha = 0.5f), start = Offset(x, padTop), end = Offset(x, padTop + gh), strokeWidth = 1.5f, pathEffect = gridEffect)
                                        }
                                        // Değerler çizginin hemen altına yerleştirildi
                                        drawText(textMeasurer, String.format(java.util.Locale.US, "%.1f", valQ), topLeft = Offset(x - 12f, padTop + gh + 8f), style = TextStyle(color = Color.Gray, fontSize = 9.sp))
                                    }

                                    // Ana Eksen Çizgileri
                                    drawLine(color = Color.Gray, start = Offset(padLeft, padTop + gh), end = Offset(padLeft + gw, padTop + gh), strokeWidth = 2.5f) // Alt Eksen
                                    drawLine(color = Color.Gray, start = Offset(padLeft, padTop), end = Offset(padLeft, padTop + gh), strokeWidth = 2.5f) // Sol Eksen

                                    // EKSEN İSİMLERİ (Artık rakamlarla çakışmıyor)
                                    drawText(textMeasurer, "Basınç (P)", topLeft = Offset(padLeft - 25f, padTop - 35f), style = TextStyle(color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                    val textQ = "Debi (Q) [${uiState.flowUnit}]"
                                    drawText(textMeasurer, textQ, topLeft = Offset(padLeft + (gw / 2f) - 40f, padTop + gh + 30f), style = TextStyle(color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))

                                    // --- 2. GRAFİK EĞRİLERİNİN ÇİZİLMESİ (GÜVENLİ KIRPMA İLE) ---
                                    clipRect(left = padLeft, top = padTop, right = padLeft + gw, bottom = padTop + gh) {

                                        // A. Sistem Direnci Eğrisi (Kırmızı Parabol) P = k * Q^2
                                        val safeOpQ = maxOf(opQ, 0.0001)
                                        val k = opP / (safeOpQ * safeOpQ)

                                        val resPath = androidx.compose.ui.graphics.Path()
                                        var isFirstRes = true
                                        val stepQ = maxQ / 50.0
                                        for (i in 0..50) {
                                            val currentQ = i * stepQ
                                            val currentP = minOf(k * (currentQ * currentQ), maxP * 2.0)
                                            val cx = padLeft + ((currentQ / maxQ) * gw).toFloat()
                                            val cy = padTop + gh - ((currentP / maxP) * gh).toFloat()

                                            if (isFirstRes) { resPath.moveTo(cx, cy); isFirstRes = false }
                                            else { resPath.lineTo(cx, cy) }

                                            if (currentP >= maxP * 2.0) break
                                        }
                                        drawPath(path = resPath, color = Color(0xFFE63946), style = Stroke(width = 3.5f))

                                        // B. Fan Eğrisi Çizimi (Mavi Çizgi)
                                        val fanPath = androidx.compose.ui.graphics.Path()
                                        var isFirstFan = true
                                        rawCurve.forEach { point ->
                                            val cx = padLeft + ((point.first / maxQ) * gw).toFloat()
                                            val cy = padTop + gh - ((point.second / maxP) * gh).toFloat()
                                            if (isFirstFan) { fanPath.moveTo(cx, cy); isFirstFan = false }
                                            else { fanPath.lineTo(cx, cy) }
                                        }
                                        drawPath(path = fanPath, color = Color(0xFF64B5F6), style = Stroke(width = 3.5f))

                                        // C. Çalışma Noktası (Kesişim - Yeşil Hedef)
                                        if (opQ > 0.0 && opP > 0.0) {
                                            val opCx = (padLeft + ((opQ / maxQ) * gw).toFloat()).coerceIn(padLeft, padLeft + gw)
                                            val opCy = (padTop + gh - ((opP / maxP) * gh).toFloat()).coerceIn(padTop, padTop + gh)

                                            val dashEffectIntersection = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                            drawLine(color = Color.LightGray.copy(alpha=0.6f), start = Offset(padLeft, opCy), end = Offset(opCx, opCy), strokeWidth = 1.5f, pathEffect = dashEffectIntersection)
                                            drawLine(color = Color.LightGray.copy(alpha=0.6f), start = Offset(opCx, padTop + gh), end = Offset(opCx, opCy), strokeWidth = 1.5f, pathEffect = dashEffectIntersection)

                                            drawCircle(color = Color(0xFF81C784), radius = 6f, center = Offset(opCx, opCy))
                                            drawCircle(color = Color.White, radius = 6f, center = Offset(opCx, opCy), style = Stroke(width = 2f))
                                        }
                                    }

                                    // --- 3. LEJANT (BİLGİ KUTUSU) ---
                                    val legendX = padLeft + gw - 120f
                                    val legendY = padTop + 10f

                                    // Yarı Saydam Lejant Arka Planı (Düzenlendi)
                                    drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(legendX - 10f, legendY - 15f), size = Size(130f, 50f), style = androidx.compose.ui.graphics.drawscope.Fill)

                                    drawLine(color = Color(0xFF64B5F6), start = Offset(legendX, legendY), end = Offset(legendX + 15f, legendY), strokeWidth = 5f)
                                    drawText(textMeasurer, "Fan Eğrisi", topLeft = Offset(legendX + 25f, legendY - 8f), style = TextStyle(color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))

                                    drawLine(color = Color(0xFFE63946), start = Offset(legendX, legendY + 20f), end = Offset(legendX + 15f, legendY + 20f), strokeWidth = 5f)
                                    drawText(textMeasurer, "Sistem Direnci", topLeft = Offset(legendX + 25f, legendY + 12f), style = TextStyle(color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        // Fan Öneri Modülü
                        val cfmReq = if (uiState.envState.selectedFanMethod.contains("Debisi")) { uiState.envState.fixedFlowStr.replace(",", ".").toDoubleOrNull() ?: 10.0 } else if (uiState.envState.selectedFanMethod.contains("Hızı")) { (uiState.envState.fixedSpeedStr.replace(",", ".").toDoubleOrNull() ?: 2.0) * 20.0 } else { 30.0 }
                        val fanSuggestion = when {
                            cfmReq < 15.0 && result.pressureDropPa < 15.0 -> "40mm / 60mm Eksenel (Axial) Fan"
                            cfmReq in 15.0..40.0 && result.pressureDropPa < 30.0 -> "80mm / 92mm Standart Kasa Fanı"
                            cfmReq > 40.0 && result.pressureDropPa < 50.0 -> "120mm / 140mm Yüksek Akışlı Fan"
                            result.pressureDropPa >= 50.0 && result.pressureDropPa < 100.0 -> "Yüksek Statik Basınçlı veya Blower (Salyangoz) Fan"
                            result.pressureDropPa >= 100.0 -> "Endüstriyel Salyangoz Fan veya Çift Fanlı (Push-Pull)"
                            else -> "Uygulamaya Özel Performans Fanı"
                        }
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3F44))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("💡 Önerilen Fan Tipi:", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(fanSuggestion, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. BÖLÜM: DARBOĞAZ (BOTTLENECK) ANALİZİ
        HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎯 Isıl Darboğaz (Bottleneck) Analizi", fontWeight = FontWeight.Bold, color = Color(0xFF81C784), modifier = Modifier.weight(1f))
            IconButton(onClick = { onShowInfo("Bu Grafik Nedir?", "Sistemdeki toplam direncin nerede biriktiğini gösterir.\nGrafikteki en uzun çubuk, soğutma performansınızı kısıtlayan ana darboğazdır (Bottleneck).") }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // DÜZELTİLDİ: Pasta grafiği yerine sütun (bar) grafiği - az sayıda kategoriyi kıyaslarken daha okunaklı
                val rTotalForBar = result.rTotalSystem.coerceAtLeast(0.0001)
                val bottleneckItems = listOf(
                    Pair("Taşınım (Havaya Atım)", result.rConv),
                    Pair("Taban İletimi (Kalınlık)", result.rCondBase),
                    Pair("Yayılma (Spreading)", result.rSpreadAvg),
                    Pair("TIM (Macun/Pad)", result.rTimAvg)
                ).sortedByDescending { it.second }
                val maxBarVal = bottleneckItems.maxOf { it.second }.coerceAtLeast(0.0001)
                bottleneckItems.forEach { (label, value) ->
                    val ratio = (value / maxBarVal).toFloat().coerceIn(0f, 1f)
                    val percent = value / rTotalForBar * 100.0
                    // YENİ: Renk artık öneme (orana) göre - çok önemli kırmızı, az önemli mavi
                    val itemColor = importanceColor(ratio)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(String.format(java.util.Locale.US, "%% %.0f", percent), fontSize = 11.sp, color = itemColor, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(Color(0xFF2C3136), RoundedCornerShape(4.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(ratio).fillMaxHeight().background(itemColor, RoundedCornerShape(4.dp)))
                        }
                    }
                }

                val maxResistance = maxOf(result.rTimAvg, result.rSpreadAvg, result.rCondBase, result.rConv)
                val (diag1, diag2) = when (maxResistance) {
                    result.rConv -> Pair("Darboğaz: Havaya Atım (Taşınım).", "Isı soğutucuya başarıyla ulaşıyor ancak havaya atılamıyor. Fan debisini (${uiState.flowUnit}) artırmayı veya kanat sayısını çoğaltmayı deneyin.")
                    result.rTimAvg -> Pair("Darboğaz: Termal Macun/Pad (TIM).", "Isı çipten soğutucuya geçişte zorlanıyor. Daha ince veya iletkenliği daha yüksek bir arayüz malzemesi (Termal Macun vb.) seçin.")
                    result.rSpreadAvg -> Pair("Darboğaz: Taban Yayılımı (Spreading).", "Çip çok küçük, soğutucu geniş; Isı yanlara yayılamıyor. Taban kalınlığını (tb) artırın veya Bakır (Cu) malzeme kullanın.")
                    else -> Pair("Darboğaz: Taban İletimi.", "Tabanınız (tb) gereğinden fazla kalın. Isı, kanatçıklara ulaşmadan taban içinde hapsoluyor. Taban kalınlığını düşürmeyi deneyin.")
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(diag1, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(diag2, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // YENİ: 5. BÖLÜM - Duyarlılık Analizi (Tornado Chart)
        if (result.sensitivityItems.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌪️ Duyarlılık Analizi (Hangi Parametre Daha Etkili?)", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), modifier = Modifier.weight(1f))
                IconButton(onClick = { onShowInfo("Duyarlılık Analizi Nedir?", buildString {
                    appendLine("Her parametre (tb, tf, S, k, hf), mevcut tasarım değerinin ±%10 civarında oynatılır ve toplam direncin (R_total) ne kadar değiştiği ölçülür.")
                    appendLine()
                    appendLine("Çubuk UZUN ise: o parametredeki küçük bir değişiklik R_total'ı büyük ölçüde etkiler — yani tasarımı iyileştirmek için önce bu parametreyi değiştirmelisiniz.")
                    appendLine()
                    append("Çubuk KISA ise: o parametreyi değiştirmek neredeyse hiçbir fark yaratmaz, zaman kaybetmeyin, başka bir parametreye odaklanın.")
                }) }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) }
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val maxDelta = result.sensitivityItems.maxOf { it.deltaR }.coerceAtLeast(0.0001)
                    val resUnit = uiState.resistanceUnit
                    result.sensitivityItems.forEach { item ->
                        // YENİ: Isıl Darboğaz'daki gibi öneme göre renk - çok önemli kırmızı, az önemli mavi
                        val itemColor = importanceColor((item.deltaR / maxDelta).toFloat().coerceIn(0f, 1f))
                        val displayDelta = if (resUnit == "°F/W") item.deltaR * 1.8 else item.deltaR
                        val ratio = (item.deltaR / maxDelta).toFloat().coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.paramName, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(String.format(java.util.Locale.US, "Δ %.3f %s", displayDelta, resUnit), fontSize = 11.sp, color = itemColor, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(Color(0xFF2C3136), RoundedCornerShape(4.dp))) {
                                Box(modifier = Modifier.fillMaxWidth(ratio).fillMaxHeight().background(itemColor, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                    val topItem = result.sensitivityItems.maxByOrNull { it.deltaR }
                    if (topItem != null) {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF64B5F6).copy(alpha = 0.15f))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("🎯 En Etkili Parametre: ${topItem.paramName}", color = Color(0xFF64B5F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Tasarımı iyileştirmek istiyorsanız önce bunu değiştirin — R_total üzerinde en büyük etkiye sahip parametre bu.", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // YENİ: 6. BÖLÜM - Geçici (Transient) Sıcaklık Eğrisi T(t)
        val transientTotalPowerW = uiState.heatSources.sumOf { it.watt.replace(",", ".").toDoubleOrNull() ?: 0.0 }
        val transientAmbientC = uiState.envState.ambientTemp.replace(",", ".").toDoubleOrNull() ?: 25.0
        val transientTau = result.timeConstantSeconds
        val transientDeltaTss = transientTotalPowerW * (result.rConv + result.rCondBase)

        if (transientTau > 0.01 && transientDeltaTss > 0.01) {
            HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌡️ Geçici Sıcaklık Eğrisi (Isınma Süreci)", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), modifier = Modifier.weight(1f))
                IconButton(onClick = { onShowInfo("Geçici Sıcaklık Eğrisi Nedir?", buildString {
                    appendLine("Bu grafik, cihaz açıldığı anda (t=0) ortam sıcaklığında olan soğutucunun, zamanla nasıl kararlı (denge) sıcaklığına yükseldiğini gösterir.")
                    appendLine()
                    appendLine("τ (tau) anında sıcaklık farkının yaklaşık %63'üne, 3τ anında ise %95'inden fazlasına ulaşılır — pratikte sistem bu noktada 'ısınmış' sayılabilir.")
                    appendLine()
                    append("Bu hesap sabit güç varsayımıyla yapılmıştır; darbeli (duty-cycle) yük senaryoları ileride eklenecektir.")
                }) }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) }
            }

            fun fmtTransientTime(totalSecondsDouble: Double): String {
                val totalSec = totalSecondsDouble.toInt()
                val m = totalSec / 60; val s = totalSec % 60
                return if (m > 0) "$m dk $s sn" else "$s sn"
            }
            fun convertTransientTemp(c: Double): Double = when (uiState.tempUnit) { "°F" -> (c * 1.8) + 32.0; "K" -> c + 273.15; else -> c }

            val tauTimeLabel = fmtTransientTime(transientTau)
            val tau3TimeLabel = fmtTransientTime(transientTau * 3.0)
            val tauTempDisplay = convertTransientTemp(transientAmbientC + transientDeltaTss * (1.0 - Math.exp(-1.0)))
            val tau3TempDisplay = convertTransientTemp(transientAmbientC + transientDeltaTss * (1.0 - Math.exp(-3.0)))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    val steadyStateDisplay = convertTransientTemp(transientAmbientC + transientDeltaTss)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Kararlı Hal Sıcaklığı", color = Color.Gray, fontSize = 11.sp)
                            Text(String.format(java.util.Locale.US, "%.1f %s", steadyStateDisplay, uiState.tempUnit), fontWeight = FontWeight.Bold, color = Color(0xFFE63946), fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Pratik Kararlılık Süresi (3τ)", color = Color.Gray, fontSize = 11.sp)
                            Text(tau3TimeLabel, fontWeight = FontWeight.Bold, color = Color(0xFF81C784), fontSize = 15.sp)
                        }
                    }

                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color(0xFF15181B), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF2C3136), RoundedCornerShape(8.dp))) {

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val padLeft = 65f
                            val padBottom = 45f
                            val padTop = 20f
                            val padRight = 20f
                            val gw = w - padLeft - padRight
                            val gh = h - padTop - padBottom
                            if (gw <= 0f || gh <= 0f) return@Canvas

                            val duration = transientTau * 5.0
                            val useMinutes = duration > 180.0
                            val timeDiv = if (useMinutes) 60.0 else 1.0
                            val timeUnitLabel = if (useMinutes) "dk" else "sn"

                            val gridEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            for (i in 0..4) {
                                val y = padTop + gh - (i * gh / 4)
                                val fracVal = i / 4.0
                                if (i > 0) drawLine(color = Color.DarkGray.copy(alpha = 0.4f), start = Offset(padLeft, y), end = Offset(padLeft + gw, y), strokeWidth = 1.5f, pathEffect = gridEffect)
                                val tempAtFrac = transientAmbientC + transientDeltaTss * fracVal
                                val displayVal = convertTransientTemp(tempAtFrac)
                                drawText(textMeasurer, String.format(java.util.Locale.US, "%.0f°", displayVal), topLeft = Offset(4f, y - 8f), style = TextStyle(color = Color.Gray, fontSize = 9.sp))
                            }
                            for (i in 0..4) {
                                val x = padLeft + (i * gw / 4)
                                val tVal = (i * duration / 4) / timeDiv
                                drawText(textMeasurer, String.format(java.util.Locale.US, "%.0f%s", tVal, timeUnitLabel), topLeft = Offset(x - 12f, padTop + gh + 8f), style = TextStyle(color = Color.Gray, fontSize = 9.sp))
                            }

                            val transientPath = androidx.compose.ui.graphics.Path()
                            val steps = 60
                            for (i in 0..steps) {
                                val t = (i.toDouble() / steps) * duration
                                val temp = transientAmbientC + transientDeltaTss * (1.0 - Math.exp(-t / transientTau))
                                val xPos = padLeft + ((t / duration) * gw).toFloat()
                                val yFrac = ((temp - transientAmbientC) / transientDeltaTss).coerceIn(0.0, 1.0)
                                val yPos = padTop + gh - (yFrac * gh).toFloat()
                                if (i == 0) transientPath.moveTo(xPos, yPos) else transientPath.lineTo(xPos, yPos)
                            }
                            drawPath(path = transientPath, color = Color(0xFFE63946), style = Stroke(width = 3f))

                            val tauX = padLeft + ((transientTau / duration) * gw).toFloat()
                            val tau3X = padLeft + (((transientTau * 3.0) / duration) * gw).toFloat()
                            val tauYFrac = (1.0 - Math.exp(-1.0)).coerceIn(0.0, 1.0)
                            val tau3YFrac = (1.0 - Math.exp(-3.0)).coerceIn(0.0, 1.0)
                            val tauY = padTop + gh - (tauYFrac * gh).toFloat()
                            val tau3Y = padTop + gh - (tau3YFrac * gh).toFloat()

                            val markEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            drawLine(color = Color(0xFF64B5F6).copy(alpha = 0.5f), start = Offset(tauX, padTop), end = Offset(tauX, padTop + gh), strokeWidth = 1.5f, pathEffect = markEffect)
                            drawLine(color = Color(0xFF81C784).copy(alpha = 0.5f), start = Offset(tau3X, padTop), end = Offset(tau3X, padTop + gh), strokeWidth = 1.5f, pathEffect = markEffect)

                            drawCircle(color = Color(0xFF64B5F6), radius = 6f, center = Offset(tauX, tauY))
                            drawCircle(color = Color(0xFF81C784), radius = 6f, center = Offset(tau3X, tau3Y))

                            val tauLabelX = (tauX - 20f).coerceIn(padLeft, padLeft + gw - 70f)
                            val tau3LabelX = (tau3X - 20f).coerceIn(padLeft, padLeft + gw - 70f)
                            drawText(textMeasurer, tauTimeLabel, topLeft = Offset(tauLabelX, padTop - 2f), style = TextStyle(color = Color(0xFF64B5F6), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                            drawText(textMeasurer, tau3TimeLabel, topLeft = Offset(tau3LabelX, padTop - 2f), style = TextStyle(color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        }
                    }

                    Text(String.format(java.util.Locale.US, "Kırmızı eğri, cihaz açıldığı andan itibaren sıcaklığın zamanla yükselişini gösterir. Mavi nokta (%.1f %s, %s) sıcaklık farkının %%63'üne, yeşil nokta (%.1f %s, %s) ise %%95'inden fazlasına ulaşan anı işaret eder.", tauTempDisplay, uiState.tempUnit, tauTimeLabel, tau3TempDisplay, uiState.tempUnit, tau3TimeLabel), color = Color.Gray, fontSize = 10.sp)
                }
            }
        }

        // YENİ: 7. BÖLÜM - Optimizasyon Isı Haritası (tb sabit, tf x S taraması)
        if (result.heatmapCells.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🗺️ Optimizasyon Isı Haritası", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), modifier = Modifier.weight(1f))
                IconButton(onClick = { onShowInfo("Isı Haritası Nedir?", buildString {
                    appendLine("Algoritmanın bulduğu en iyi taban kalınlığı (tb) sabit tutulup, kanat kalınlığı (tf) ve kanat aralığı (S) için taranan tüm kombinasyonların toplam dirence (R_total) etkisini gösterir.")
                    appendLine()
                    appendLine("Yeşil: daha soğuk (düşük direnç) — Kırmızı: daha sıcak (yüksek direnç).")
                    appendLine()
                    appendLine("Gri hücreler: üretilebilirlik (hf/tf oranı) veya hava akışı sınırlarını aştığı için geçersiz kombinasyonlardır.")
                    append("Beyaz çerçeveli hücre: algoritmanın seçtiği nihai tasarımdır.")
                }) }, modifier = Modifier.size(24.dp)) { Text("ℹ️", fontSize = 16.sp) }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    Text("Taban Kalınlığı (tb) = ${String.format(java.util.Locale.US, "%.1f", result.usedTb)} ${uiState.lengthUnit} olarak sabit tutuldu", color = Color.Gray, fontSize = 11.sp)

                    val tfValues = result.heatmapCells.map { it.tf }.distinct().sorted()
                    val sValues = result.heatmapCells.map { it.s }.distinct().sorted()
                    val cellLookup = result.heatmapCells.associateBy { Pair(it.tf, it.s) }
                    val validRValues = result.heatmapCells.filter { it.isValid }.map { it.rTotal }
                    val minR = validRValues.minOrNull() ?: 0.0
                    val maxR = (validRValues.maxOrNull() ?: 1.0).coerceAtLeast(minR + 0.0001)

                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color(0xFF15181B), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF2C3136), RoundedCornerShape(8.dp))) {

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val padLeft = 55f
                            val padBottom = 40f
                            val padTop = 12f
                            val padRight = 12f
                            val gw = w - padLeft - padRight
                            val gh = h - padTop - padBottom
                            if (gw <= 0f || gh <= 0f || tfValues.isEmpty() || sValues.isEmpty()) return@Canvas

                            val nRows = tfValues.size
                            val nCols = sValues.size
                            val cellW = gw / nCols
                            val cellH = gh / nRows

                            for (rowIdx in 0 until nRows) {
                                val tfVal = tfValues[rowIdx]
                                val yTop = padTop + (nRows - 1 - rowIdx) * cellH
                                for (colIdx in 0 until nCols) {
                                    val sVal = sValues[colIdx]
                                    val xLeft = padLeft + colIdx * cellW
                                    val cell = cellLookup[Pair(tfVal, sVal)]
                                    val cellColor = if (cell == null) {
                                        Color(0xFF2C3136)
                                    } else if (!cell.isValid) {
                                        Color(0xFF3A3E42)
                                    } else {
                                        val frac = ((cell.rTotal - minR) / (maxR - minR)).coerceIn(0.0, 1.0).toFloat()
                                        lerp(Color(0xFF81C784), Color(0xFFE63946), frac)
                                    }
                                    drawRect(color = cellColor, topLeft = Offset(xLeft + 1f, yTop + 1f), size = Size(cellW - 2f, cellH - 2f))

                                    val isChosenCell = cell != null && kotlin.math.abs(cell.tf - result.usedTf) < 0.01 && kotlin.math.abs(cell.s - result.usedS) < 0.01
                                    if (isChosenCell) {
                                        drawRect(color = Color.White, topLeft = Offset(xLeft + 1f, yTop + 1f), size = Size(cellW - 2f, cellH - 2f), style = Stroke(width = 2.5f))
                                    }
                                }
                            }

                            val labelIndices = listOf(0, nRows / 2, nRows - 1).distinct()
                            labelIndices.forEach { idx ->
                                val yTop = padTop + (nRows - 1 - idx) * cellH
                                drawText(textMeasurer, String.format(java.util.Locale.US, "%.1f", tfValues[idx]), topLeft = Offset(2f, yTop + (cellH / 2f) - 8f), style = TextStyle(color = Color.Gray, fontSize = 9.sp))
                            }
                            val colLabelIndices = listOf(0, nCols / 2, nCols - 1).distinct()
                            colLabelIndices.forEach { idx ->
                                val xLeft = padLeft + idx * cellW
                                drawText(textMeasurer, String.format(java.util.Locale.US, "%.1f", sValues[idx]), topLeft = Offset(xLeft + (cellW / 2f) - 10f, padTop + gh + 6f), style = TextStyle(color = Color.Gray, fontSize = 9.sp))
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("↑ Kanat Kalınlığı (tf)", color = Color.Gray, fontSize = 10.sp)
                        Text("Kanat Aralığı (S) →", color = Color.Gray, fontSize = 10.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF81C784), RoundedCornerShape(2.dp)))
                        Text("Soğuk", color = Color.LightGray, fontSize = 10.sp)
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFFE63946), RoundedCornerShape(2.dp)))
                        Text("Sıcak", color = Color.LightGray, fontSize = 10.sp)
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF3A3E42), RoundedCornerShape(2.dp)))
                        Text("Geçersiz", color = Color.LightGray, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LegendItem(color: Color, text: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier
    .size(10.dp)
    .background(color, RoundedCornerShape(2.dp))); Spacer(modifier = Modifier.width(6.dp)); Text(text, fontSize = 11.sp, color = Color.LightGray) } }

@Composable
fun SummaryTab(uiState: HeatsinkUiState, result: SolverResult) {
    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Girdi Parametreleri Özeti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        AnimatedVisibility(visible = uiState.isOptimizationEnabled) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB703).copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("✨ Optimizasyon Önerisi", fontWeight = FontWeight.Bold, color = Color(0xFFFFB703), fontSize = 13.sp); Text("Belirttiğiniz sınırlar dahilinde algoritmanın bulduğu en verimli (en soğuk) geometri ölçüleri:", color = Color.LightGray, fontSize = 11.sp); Text(text = String.format(java.util.Locale.US, "Taban: %.1f | Kanat: %.1f | Aralık: %.1f", result.usedTb, result.usedTf, result.usedS), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("📏 Geometri ve Malzeme", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp); Text("Malzeme: ${uiState.selectedMaterialName}", fontSize = 11.sp, color = Color.LightGray); Text("Ölçüler: ${uiState.width}x${uiState.length} ${uiState.lengthUnit} (Taban: ${uiState.baseThickness}, Kanat: ${uiState.finHeight})", fontSize = 11.sp, color = Color.LightGray); Text("Kanat Kalınlığı: ${uiState.finThickness}, Aralık: ${uiState.finSpacing}", fontSize = 11.sp, color = Color.LightGray) } }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("🔥 Isı Kaynakları", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp); Text("Toplam Eklenen Çip: ${uiState.heatSources.size} Adet", fontSize = 11.sp, color = Color.LightGray); val totalPower = uiState.heatSources.sumOf { it.watt.replace(",", ".").toDoubleOrNull() ?: 0.0 }; Text("Sistemdeki Toplam Isı Yükü: $totalPower ${uiState.powerUnit}", fontSize = 11.sp, color = Color.LightGray) } }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("🌍 Ortam ve Akış Koşulları", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp); Text("Sıcaklık: ${uiState.envState.ambientTemp} ${uiState.tempUnit} | Rakım: ${uiState.envState.altitude} ${uiState.altitudeUnit}", fontSize = 11.sp, color = Color.LightGray); Text("Sirkülasyon: ${uiState.envState.selectedFlowType}", fontSize = 11.sp, color = Color.LightGray); if (uiState.envState.selectedFlowType.contains("Fanlı")) { Text("Fan Modu: ${uiState.envState.selectedFanMethod}", fontSize = 11.sp, color = Color.LightGray) }; Text("Radyasyon (Işınım) Katsayısı: ${uiState.envState.emissivityValueStr}", fontSize = 11.sp, color = Color.LightGray) } }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF64B5F6).copy(alpha = 0.15f))) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Akış Analizi", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), fontSize = 13.sp); Text("Mühendislik düzeyinde Fan P-Q Eğrisi ve Sistem Direnci Çakışma Grafiği hesaplanarak sistem basınç kayıpları dahil edildi.", color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp) } }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalInputField(value: String, onValueChange: (String) -> Unit, label: String, unit: String, enabled: Boolean = true, isText: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue -> if (!isText) { val filtered = newValue.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }; onValueChange(filtered) } else { onValueChange(newValue) } },
        label = { Text(label, fontSize = 12.sp) }, enabled = enabled, suffix = { if (unit.isNotBlank()) Text(unit, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold) else null },
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = if (isText) KeyboardType.Text else KeyboardType.Decimal, imeAction = ImeAction.Next),
        singleLine = true, shape = RoundedCornerShape(12.dp), colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), focusedIndicatorColor = MaterialTheme.colorScheme.primary, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent, focusedTextColor = Color(0xFFE9ECEF), unfocusedTextColor = Color(0xFFE9ECEF), disabledTextColor = Color(0xFF757874))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, label = { Text(label, fontSize = 10.sp, color = Color.Gray) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .menuAnchor(),
            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E2226), unfocusedContainerColor = Color(0xFF1E2226), focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.DarkGray)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF1E2226))) { options.forEach { opt -> DropdownMenuItem(onClick = { onSelect(opt); expanded = false }, text = { Text(opt, color = Color.White) }) } }
    }
}

@Composable
fun HeatsinkVisualizerCanvas(bW: Double, bL: Double, sources: List<HeatSourceData>, activePreview: HeatSourceData?, dimUnit: String, isImperial: Boolean, editingId: Long? = null) {
    val textMeasurer = rememberTextMeasurer()
    val allValidWatts = (sources + listOfNotNull(activePreview)).mapNotNull { it.watt.replace(",", ".").toDoubleOrNull() }
    val minW = allValidWatts.minOrNull() ?: 0.0
    val maxW = allValidWatts.maxOrNull() ?: 0.0
    val wRange = maxW - minW
    Canvas(modifier = Modifier.fillMaxSize()) {
        // YENİ: Kenar boşlukları artık Canvas'ın KENDİ çizim alanı içinde ayrılıyor.
        // Eskiden Modifier.padding ile ayrılan boşluk Canvas'ın çizim alanının DIŞINDA kalıyordu,
        // bu yüzden X ekseni sayıları görünmüyordu (çizim alanının dışına taşıp kırpılıyordu).
        val padLeft = 50f; val padRight = 20f; val padTop = 44f; val padBottom = 54f
        val availW = size.width - padLeft - padRight
        val availH = size.height - padTop - padBottom
        val blockRatio = bW / bL; val canvasRatio = availW / availH
        val cW: Float; val cH: Float; val offsetX: Float; val offsetY: Float
        if (blockRatio > canvasRatio) { cW = availW; cH = (availW / blockRatio).toFloat(); offsetX = padLeft; offsetY = padTop + (availH - cH) / 2f } else { cH = availH; cW = (availH * blockRatio).toFloat(); offsetX = padLeft + (availW - cW) / 2f; offsetY = padTop }
        val scaleX = cW / bW.toFloat(); val scaleY = cH / bL.toFloat()
        drawRect(color = Color(0xFF2C3136), topLeft = Offset(offsetX, offsetY), size = Size(cW, cH))

        val maxDim = maxOf(bW, bL)
        val gridStep = when {
            maxDim > 400.0 -> 50.0
            maxDim > 150.0 -> 20.0
            maxDim > 50.0 -> 10.0
            maxDim > 10.0 -> 2.0
            maxDim > 2.0 -> 0.5
            else -> 0.02
        }
        val formatStr = if (maxDim <= 2.0) "%.2f" else if (maxDim <= 10.0) "%.1f" else "%.0f"

        val dashPath = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
        var currentXGrid = gridStep
        while (currentXGrid < bW) { val gridCanvasX = offsetX + (currentXGrid * scaleX).toFloat(); drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(gridCanvasX, offsetY), end = Offset(gridCanvasX, offsetY + cH), strokeWidth = 1f, pathEffect = dashPath); currentXGrid += gridStep }
        var currentYGrid = gridStep
        while (currentYGrid < bL) { val gridCanvasY = offsetY + cH - (currentYGrid * scaleY).toFloat(); drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(offsetX, gridCanvasY), end = Offset(offsetX + cW, gridCanvasY), strokeWidth = 1f, pathEffect = dashPath); currentYGrid += gridStep }
        drawRect(color = Color.Gray, topLeft = Offset(offsetX, offsetY), size = Size(cW, cH), style = Stroke(width = 2f))

        val rulerLineEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 0f), 0f); val numRulerSteps = 4
        for (i in 0..numRulerSteps) { val ratio = i.toFloat() / numRulerSteps; val rX = offsetX + (cW * ratio); val valX = bW * ratio; drawLine(color = Color.White.copy(alpha = 0.4f), start = Offset(rX, offsetY + cH), end = Offset(rX, offsetY + cH + 8f), strokeWidth = 2f); drawText(textMeasurer = textMeasurer, text = String.format(java.util.Locale.US, formatStr, valX), style = TextStyle(color = Color.LightGray, fontSize = 9.sp), topLeft = Offset(rX - 8f, offsetY + cH + 10f)) }
        for (i in 0..numRulerSteps) { val ratio = i.toFloat() / numRulerSteps; val rY = (offsetY + cH) - (cH * ratio); val valY = bL * ratio; drawLine(color = Color.White.copy(alpha = 0.4f), start = Offset(offsetX - 8f, rY), end = Offset(offsetX, rY), strokeWidth = 2f); drawText(textMeasurer = textMeasurer, text = String.format(java.util.Locale.US, formatStr, valY), style = TextStyle(color = Color.LightGray, fontSize = 9.sp), topLeft = Offset(offsetX - 28f, rY - 6f)) }
        val originX = offsetX; val originY = offsetY + cH
        drawLine(color = Color.White.copy(alpha = 0.3f), start = Offset(originX, originY), end = Offset(offsetX + cW, originY), strokeWidth = 2f, pathEffect = rulerLineEffect)
        drawLine(color = Color.White.copy(alpha = 0.3f), start = Offset(originX, offsetY), end = Offset(originX, originY), strokeWidth = 2f, pathEffect = rulerLineEffect)
        drawCircle(color = Color(0xFF4CAF50), radius = 6f, center = Offset(originX, originY))

        // DÜZELTİLDİ: Artık her zaman en üstte sabit, "100" ile üst üste binmiyor
        drawText(textMeasurer = textMeasurer, text = String.format(java.util.Locale.US, "Blok Boyutu: $formatStr x $formatStr %s", bW, bL, dimUnit), style = TextStyle(color = Color(0xFFADB5BD), fontSize = 11.sp, fontWeight = FontWeight.Bold), topLeft = Offset(offsetX, 4f))
        sources.filter { it.id != editingId }.forEach { src ->
            val sX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY; val sW = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sL = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY; val wattVal = src.watt.replace(",", ".").toDoubleOrNull() ?: 0.0
            val ratio = if (wRange > 0) ((wattVal - minW) / wRange).toFloat() else 0.5f
            val interpolColor = lerp(Color(0xFFFFB703), Color(0xFFD62828), ratio)
            val drawX = offsetX + sX.toFloat(); val drawY = (offsetY + cH - sY - sL).toFloat()
            drawRect(color = interpolColor, topLeft = Offset(drawX, drawY), size = Size(sW.toFloat(), sL.toFloat()))
            drawRect(color = Color.White.copy(alpha = 0.6f), topLeft = Offset(drawX, drawY), size = Size(sW.toFloat(), sL.toFloat()), style = Stroke(width = 1.5f))
            drawText(textMeasurer = textMeasurer, text = "${src.name}\n(${src.watt}W)", style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold), topLeft = Offset(drawX + 4f, drawY + 4f))
        }
        activePreview?.let { src ->
            val sX = (src.posX.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sY = (src.posY.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY; val sW = (src.wS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleX; val sL = (src.lS.replace(",", ".").toDoubleOrNull() ?: 0.0) * scaleY
            val drawX = offsetX + sX.toFloat(); val drawY = (offsetY + cH - sY - sL).toFloat(); val pX = drawX; val pY = drawY + sL.toFloat()
            val previewDash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            drawLine(color = Color.White.copy(alpha = 0.8f), start = Offset(originX, pY), end = Offset(pX, pY), strokeWidth = 2f, pathEffect = previewDash)
            drawLine(color = Color.White.copy(alpha = 0.8f), start = Offset(pX, originY), end = Offset(pX, pY), strokeWidth = 2f, pathEffect = previewDash)
            drawText(textMeasurer = textMeasurer, text = "X: ${src.posX}", style = TextStyle(color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(pX - 15f, originY - 16f))
            drawText(textMeasurer = textMeasurer, text = "Y: ${src.posY}", style = TextStyle(color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold), topLeft = Offset(originX + 4f, pY + 2f))
            drawRect(color = Color(0xFF4CAF50).copy(alpha = 0.25f), topLeft = Offset(drawX, drawY), size = Size(sW.toFloat(), sL.toFloat()))
            drawRect(color = Color(0xFF4CAF50), topLeft = Offset(drawX, drawY), size = Size(sW.toFloat(), sL.toFloat()), style = Stroke(width = 2f, pathEffect = previewDash))
            drawText(textMeasurer = textMeasurer, text = "Yeni Eleman\nKonum Önizleme", style = TextStyle(color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold), topLeft = Offset(drawX + 5f, drawY + 5f))
        }
    }
}
