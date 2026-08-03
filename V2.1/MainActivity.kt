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

// === MainActivity.kt: Uygulama giris noktasi ve ana Composable (DovahkiinApp) ===

// ==========================================
// ANA UYGULAMA BAŞLATICISI VE YÖNLENDİRİCİ
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyHeatSinkCalcTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DovahkiinApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DovahkiinApp() {
    val viewModel: HeatsinkViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Yasal Uyarı Durumu
    val sharedPref = context.getSharedPreferences("DovahkiinPrefs", Context.MODE_PRIVATE)
    var showDisclaimer by remember { mutableStateOf(!sharedPref.getBoolean("disclaimer_accepted", false)) }

    // YENİ: "Hakkında" menüsü ve alt pencereleri için durumlar
    var showAboutDialog by remember { mutableStateOf(false) }
    var showMethodologyDialog by remember { mutableStateOf(false) }
    var showDisclaimerViewDialog by remember { mutableStateOf(false) }

    // Kayar Menü Durumu
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }
    val savedProjects = remember(refreshTrigger, drawerState.isOpen, uiState.currentPage) { viewModel.getSavedProjectNames(context) }

    // DOKÜMANTASYON: Silme ve Çıkış Onayları için State'ler
    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    if (uiState.infoDialogTitle.isNotEmpty()) {
        InfoDialog(
            title = uiState.infoDialogTitle,
            text = uiState.infoDialogText,
            onDismiss = { viewModel.dismissInfo() }
        )
    }

    if (uiState.showChangelogDialog) {
        ChangelogDialog(onDismiss = { viewModel.updateChangelogDialogState(false) })
    }

    // YENİ: "Hakkında" penceresi ve içindeki alt pencereler
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onShowChangelog = { showAboutDialog = false; viewModel.updateChangelogDialogState(true) },
            onShowDisclaimer = { showAboutDialog = false; showDisclaimerViewDialog = true },
            onShowMethodology = { showAboutDialog = false; showMethodologyDialog = true }
        )
    }
    if (showMethodologyDialog) {
        MethodologyDialog(onDismiss = { showMethodologyDialog = false })
    }
    if (showDisclaimerViewDialog) {
        DisclaimerViewDialog(onDismiss = { showDisclaimerViewDialog = false })
    }

    // DOKÜMANTASYON: Proje Silme Onay Penceresi (Dialog)
    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Projeyi Sil", fontWeight = FontWeight.Bold, color = Color(0xFFE57373)) },
            text = { Text("'$projectToDelete' projesini kalıcı olarak silmek istediğinize emin misiniz?", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(context, projectToDelete!!)
                    Toast.makeText(context, "$projectToDelete Silindi!", Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                    projectToDelete = null // Pencereyi kapat
                }) { Text("Evet", color = Color(0xFFE57373), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("Hayır", color = Color.White) } },
            containerColor = Color(0xFF1E2226)
        )
    }

    // DOKÜMANTASYON: Uygulamadan Çıkış Onay Penceresi (Dialog)
    if (showExitDialog) {
        val activity = LocalContext.current as? android.app.Activity
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Çıkış Yap", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6)) },
            text = { Text("Uygulamadan çıkmak istediğinize emin misiniz? Kaydedilmeyen tüm verileriniz kaybolacaktır.", color = Color.LightGray) },
            confirmButton = { TextButton(onClick = { activity?.finish() }) { Text("Evet", color = Color(0xFFE57373), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Hayır", color = Color.White) } },
            containerColor = Color(0xFF1E2226)
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !showDisclaimer, // Yasal uyarı varken menü açılamasın
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF15181B)) {

                // 1. ÜST KISIM: Sabit başlık
                Spacer(Modifier.height(24.dp))
                Text("Dovahkiin Menü", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                HorizontalDivider(color = Color(0xFF2C3136))

                // 2. ORTA KISIM: Kalan tüm boşluğu doldurur (weight(1f)) ve projeler artarsa kendi içinde kayar
                Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("💾 Kayıtlı Projeler", color = Color.Gray, modifier = Modifier.padding(16.dp, 8.dp))

                    if (savedProjects.isEmpty()) {
                        Text("Kayıtlı proje bulunamadı.", color = Color.DarkGray, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
                    } else {
                        savedProjects.forEach { proj ->
                            NavigationDrawerItem(
                                label = { Text(proj, color = Color.LightGray) },
                                selected = false,
                                onClick = {
                                    viewModel.loadProject(context, proj)
                                    Toast.makeText(context, "$proj Yüklendi!", Toast.LENGTH_SHORT).show()
                                    scope.launch { drawerState.close() }
                                },
                                badge = {
                                    IconButton(onClick = { projectToDelete = proj }) {
                                        Text("🗑️", fontSize = 14.sp)
                                    }
                                },
                                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                            )
                        }
                    }
                }

                // 3. ALT KISIM: Orta kısım üstteki tüm boşluğu iteceği için Çıkış butonu her zaman en alta yapışır.
                HorizontalDivider(color = Color(0xFF2C3136))
                NavigationDrawerItem(
                    label = { Text("ℹ️ Hakkında", color = Color.White) },
                    selected = false,
                    onClick = {
                        showAboutDialog = true
                        scope.launch { drawerState.close() }
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )
                HorizontalDivider(color = Color(0xFF2C3136))
                NavigationDrawerItem(
                    label = { Text("🚪 Çıkış Yap", color = Color(0xFFE57373), fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = { showExitDialog = true; scope.launch { drawerState.close() } },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.currentPage == 0) {
                                if (uiState.projectName.isNotBlank()) uiState.projectName else "Yeni Proje"
                            } else "Adım ${uiState.currentPage}: ${
                                when (uiState.currentPage) {
                                    1 -> "Geometri ve Malzeme"
                                    2 -> "Isı Kaynakları"
                                    3 -> "Ortam Koşulları"
                                    4 -> "Analiz Raporu"
                                    else -> ""
                                }
                            }",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", fontSize = 24.sp, color = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (uiState.currentPage) {
                    0 -> WelcomeScreen(
                        modifier = Modifier,
                        // DUZELTILDI (Mimari Tutarlilik): WelcomeScreen artik PageOne/Two/Three gibi
                        // state ve callback'leri parametre olarak aliyor; kendi icinde viewModel() cagirmiyor.
                        uiState = uiState,
                        savedProjectNames = savedProjects,
                        onProjectNameChange = { viewModel.updateProjectName(it) },
                        onEnvStateChange = { viewModel.updateEnvState(it) },
                        onShowInfo = { title, text -> viewModel.showInfo(title, text) },
                        onLengthUnitChange = { viewModel.updateLengthUnit(it) },
                        onTempUnitChange = { viewModel.updateTempUnit(it) },
                        onAltitudeUnitChange = { viewModel.updateAltitudeUnit(it) },
                        onFlowUnitChange = { viewModel.updateFlowUnit(it) },
                        onVelocityUnitChange = { viewModel.updateVelocityUnit(it) },
                        onPressureUnitChange = { viewModel.updatePressureUnit(it) },
                        onWeightUnitChange = { viewModel.updateWeightUnit(it) },
                        onPowerUnitChange = { viewModel.updatePowerUnit(it) },
                        onConductivityUnitChange = { viewModel.updateConductivityUnit(it) },
                        onResistanceUnitChange = { viewModel.updateResistanceUnit(it) },
                        onStartMetric = { viewModel.applyMetricPreset() },
                        onStartImperial = { viewModel.applyImperialPreset() },
                        onStartComplete = { viewModel.updateCurrentPage(1) }
                    )
                    1 -> PageOneScreen(
                        modifier = Modifier,
                        isTargetTemperatureEnabled = uiState.isTargetTemperatureEnabled,
                        onTargetTemperatureToggle = { viewModel.updateIsTargetTemperatureEnabled(it) },
                        uiState = uiState,
                        targetTemperature = uiState.targetTemperature,
                        onTargetTemperatureChange = { viewModel.updateTargetTemperature(it) },
                        onBackPage = { viewModel.updateCurrentPage(0) },
                        width = uiState.width, onWidthChange = { viewModel.updateWidth(it) },
                        length = uiState.length, onLengthChange = { viewModel.updateLength(it) },
                        baseThickness = uiState.baseThickness, onBaseThicknessChange = { viewModel.updateBaseThickness(it) },
                        finHeight = uiState.finHeight, onFinHeightChange = { viewModel.updateFinHeight(it) },
                        finThickness = uiState.finThickness, onFinThicknessChange = { viewModel.updateFinThickness(it) },
                        finSpacing = uiState.finSpacing, onFinSpacingChange = { viewModel.updateFinSpacing(it) },
                        selectedMaterialName = uiState.selectedMaterialName, onMaterialNameChange = { viewModel.updateSelectedMaterialName(it) },
                        selectedConductivity = uiState.selectedConductivity, onConductivityChange = { viewModel.updateSelectedConductivity(it) },
                        customDensity = uiState.customDensity, onCustomDensityChange = { viewModel.updateCustomDensity(it) },
                        customSpecificHeat = uiState.customSpecificHeat, onCustomSpecificHeatChange = { viewModel.updateCustomSpecificHeat(it) },
                        isOptimizationEnabled = uiState.isOptimizationEnabled, onOptimizationToggle = { viewModel.updateIsOptimizationEnabled(it) },
                        minBaseThick = uiState.minBaseThick, onMinBaseThickChange = { viewModel.updateMinBaseThick(it) },
                        maxBaseThick = uiState.maxBaseThick, onMaxBaseThickChange = { viewModel.updateMaxBaseThick(it) },
                        minFinThick = uiState.minFinThick, onMinFinThickChange = { viewModel.updateMinFinThick(it) },
                        maxFinThick = uiState.maxFinThick, onMaxFinThickChange = { viewModel.updateMaxFinThick(it) },
                        minFinGap = uiState.minFinGap, onMinFinGapChange = { viewModel.updateMinFinGap(it) },
                        maxFinGap = uiState.maxFinGap, onMaxFinGapChange = { viewModel.updateMaxFinGap(it) },
                        onShowInfo = { title, text -> viewModel.showInfo(title, text) },
                        onNextPage = { viewModel.updateCurrentPage(2) }
                    )
                    2 -> PageTwoScreen(
                        modifier = Modifier,
                        blockWidthStr = uiState.width,
                        blockLengthStr = uiState.length,
                        dimUnit = uiState.lengthUnit,
                        powerUnit = uiState.powerUnit,
                        heatSources = uiState.heatSources,
                        onUpdateSources = { viewModel.updateHeatSources(it) },
                        onShowInfo = { title, text -> viewModel.showInfo(title, text) },
                        onBackPage = { viewModel.updateCurrentPage(1) },
                        onStartSolution = { viewModel.updateCurrentPage(3) }
                    )
                    3 -> PageThreeScreen(
                        modifier = Modifier,
                        uiState = uiState,
                        onOrientationChange = { viewModel.updateOrientationIndex(it) },
                        onShowInfo = { title, text -> viewModel.showInfo(title, text) },
                        onBackPage = { viewModel.updateCurrentPage(2) },
                        onSaveEnvState = { viewModel.updateEnvState(it) },
                        onCalculate = { amb, flow, type, chHeight -> viewModel.runSolverAndNavigate(amb, flow, type) },
                        onCancel = { viewModel.cancelCalculation() } // YENİ EKLENDİ
                    )
                    4 -> {
                        val currentResult = uiState.solverResult
                        if (currentResult != null) {
                            PageFourScreen(
                                modifier = Modifier,
                                uiState = uiState,
                                result = currentResult,
                                onBack = { viewModel.updateCurrentPage(1) },
                                onShowInfo = { title, text -> viewModel.showInfo(title, text) },
                                // DUZELTILDI (Mimari Tutarlilik): Kaydetme islemi artik callback uzerinden yapiliyor.
                                onSaveProject = { viewModel.saveProject(context) },
                                // YENİ: Çoklu tasarım karşılaştırması callback'leri
                                onAddToComparison = { viewModel.addCurrentToComparison() },
                                onRemoveFromComparison = { index -> viewModel.removeFromComparison(index) }
                            )
                        } else {
                            // DUZELTILDI: Sonuc olmadan bu sayfaya gelinirse bos ekranda kalmak yerine
                            // guvenli sekilde 1. sayfaya (Geometri) geri yonlendiriyoruz.
                            LaunchedEffect(Unit) { viewModel.updateCurrentPage(1) }
                        }
                    }
                }

                if (showDisclaimer) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Card(modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF15181B))) {
                            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                                Text("⚖️ Sorumluluk Reddi Beyanı", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Lütfen uygulamayı kullanmadan önce aşağıdaki şartları okuyunuz:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text("1. Bu yazılım, analitik ve ampirik termodinamik formüllere dayalı bir 'Ön Tasarım Aracıdır'.", color = Color.LightGray, fontSize = 12.sp)
                                    Text("2. Elde edilen analiz raporları %100 doğruluk garantisi vermez ve gerçek testlerin yerini tutamaz.", color = Color.LightGray, fontSize = 12.sp)
                                    Text("3. Uygulamanın sağladığı verilerle yapılacak nihai imalat kararları tamamen kullanıcının kendi sorumluluğundadır.", color = Color.LightGray, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        sharedPref.edit().putBoolean("disclaimer_accepted", true).apply()
                                        showDisclaimer = false
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784))
                                ) {
                                    Text("Okudum ve Kabul Ediyorum", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
