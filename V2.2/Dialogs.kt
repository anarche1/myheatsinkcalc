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

// === Dialogs.kt: Genel bilgi/surum gecmisi dialoglari ===

// YENİ: 5 dialogda da tekrar eden renkler artık tek yerden yönetiliyor (DRY).
// Görünüm birebir aynı kalır (aynı hex değerler); ileride tema rengi değişirse
// tek satırı güncellemek yeterli olur.
private val DialogContainerColor = Color(0xFF1E2226)
private val DialogBodyTextColor = Color.LightGray
private val DialogBodyTextColorBright = Color.White

// YENİ EKLENEN (KAYIP) FONKSİYON: Arayüzdeki bilgi uyarılarını çizer
@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = { Text(text, fontSize = 13.sp, lineHeight = 18.sp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anladım", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        containerColor = DialogContainerColor,
        titleContentColor = MaterialTheme.colorScheme.primary,
        textContentColor = DialogBodyTextColor
    )
}

// YENİ: Hakkında menüsündeki tıklanabilir satır (Sürüm Geçmişi, Hesaplama Yöntemleri, Sorumluluk Reddi)
@Composable
fun AboutMenuRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

// YENİ: Hakkında Penceresi - Sürüm Geçmişi, Hesaplama Yöntemleri ve Sorumluluk Reddi'ni tek yerde topluyor
@Composable
fun AboutDialog(onDismiss: () -> Unit, onShowChangelog: () -> Unit, onShowDisclaimer: () -> Unit, onShowMethodology: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,

        text = {
            Column {
                HorizontalDivider(color = Color(0xFF2C3136))
                AboutMenuRow("📜", "Sürüm Geçmişi", onShowChangelog)
                HorizontalDivider(color = Color(0xFF2C3136))
                AboutMenuRow("🔬", "Hesaplama Yöntemleri", onShowMethodology)
                HorizontalDivider(color = Color(0xFF2C3136))
                AboutMenuRow("⚖️", "Sorumluluk Reddi Beyanı", onShowDisclaimer)
                HorizontalDivider(color = Color(0xFF2C3136))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        containerColor = DialogContainerColor,
        titleContentColor = MaterialTheme.colorScheme.primary,
        textContentColor = DialogBodyTextColorBright
    )
}

// YENİ: Kullanılan hesaplama yöntemlerinin kısa özeti (kullanıcıya "bu sayılar nereden geliyor" güveni verir)
@Composable
fun MethodologyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔬 Hesaplama Yöntemleri", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    buildString {
                        appendLine("Bu uygulama, kapalı formüllü (analitik) bir hesap motoruna dayanır — CFD/FEA gibi meshli bir simülasyon değildir, hızlı ön tasarım ve kıyaslama amaçlıdır.")
                        appendLine()
                        appendLine("• Doğal Taşınım: Elenbaas korelasyonu ile kanat aralığının 'boğulma' (choking) noktası tespit edilir.")
                        appendLine("• Zorlanmış Taşınım: Fan P-Q eğrisi ile sistem direnç eğrisinin kesişimi (bisection yöntemiyle) bulunarak gerçek çalışma debisi/basıncı hesaplanır.")
                        appendLine("• Işınım (Radyasyon): Kanatların birbirini görmesinden kaynaklanan blokaj (Görüş Faktörü) dikkate alınarak kendini düzelten (iteratif) bir çözümle hesaplanır.")
                        appendLine("• Kanat Verimliliği: Adyabatik uç düzeltmesi (Lc = hf + tf/2) ile klasik kanat teorisine göre hesaplanır.")
                        appendLine("• Çip Sıcaklığı: TIM (termal arayüz) direnci + yayılma (spreading) direnci + soğutucu direnci seri bir ağ olarak modellenir.")
                        appendLine("• Geçici (Transient) Tepki: Sistemin ısıl kütlesi (kütle × özgül ısı) ve toplam direncinden RC tipi bir zaman sabiti (τ) hesaplanır.")
                        append("• Optimizasyon: Belirtilen aralıkta taban/kanat kalınlığı ve aralığı taranarak kısıtlar (üretilebilirlik, hava akışı) içinde en düşük dirençli tasarım bulunur.")
                    },
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anladım", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        containerColor = DialogContainerColor,
        titleContentColor = MaterialTheme.colorScheme.primary,
        textContentColor = DialogBodyTextColor
    )
}

// YENİ: Sorumluluk Reddi Beyanı'nı istediği zaman tekrar okuyabilmesi için (zorunlu ilk açılış ekranından bağımsız)
@Composable
fun DisclaimerViewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚖️ Sorumluluk Reddi Beyanı", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Bu yazılım, analitik ve ampirik termodinamik formüllere dayalı bir 'Ön Tasarım Aracıdır'.", color = Color.LightGray, fontSize = 13.sp)
                Text("2. Elde edilen analiz raporları %100 doğruluk garantisi vermez ve gerçek testlerin yerini tutamaz.", color = Color.LightGray, fontSize = 13.sp)
                Text("3. Uygulamanın sağladığı verilerle yapılacak nihai imalat kararları tamamen kullanıcının kendi sorumluluğundadır.", color = Color.LightGray, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        containerColor = DialogContainerColor,
        titleContentColor = MaterialTheme.colorScheme.primary,
        textContentColor = DialogBodyTextColor
    )
}

// YENİ EKLENEN FONKSİYON: Sürüm Geçmişi Penceresi (Genişletilmiş V2.2)
@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📜 Güncelleme Geçmişi", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dovahkiin V2.2 - Doğrulama ve Sağlamlaştırma Güncellemesi", fontWeight = FontWeight.Bold, color = Color(0xFF81C784))

                Text(
                    buildString {
                        appendLine("🔬 Hesaplama Doğruluğu (Fizik) Düzeltmeleri:")
                        appendLine("• Zorlanmış taşınımda giriş bölgesi (entrance-length) etkisi artık literatürde doğrulanmış bir korelasyonla hesaplanıyor (Teertstra, Yovanovich, Culham, Lemczyk - 1999, deneysel doğruluk %2.1 RMS).")
                        appendLine("• Zorlanmış taşınım h katsayısı gerçek Nusselt/Reynolds sayısı temeline geçirildi; fiziksel dayanağı olmayan eski ampirik formül kaldırıldı.")
                        appendLine("• Doğal taşınımda ışınım/taşınım kendini düzelten iterasyonu gevşetilerek (relaxation) hem tutarsız sıcaklık sıçraması hem de yakınsama doğruluğu birlikte düzeltildi.")
                        appendLine("• Kanal basınç kaybı hesabındaki sürtünme faktörü (Darcy/Fanning karışıklığı, 2 kat hata) giderildi.")
                        appendLine("• Kanat aralığı (S) değişikliğinin taşınım katsayısına gerçek kanal hızı üzerinden yansıması sağlandı.")
                        appendLine("• Yayılma direnci (spreading resistance) modeli, taban kalınlığı ve çip gücüne göre ağırlıklandırılarak gerçekçileştirildi.")
                        appendLine("• TIM'siz (kuru) temas direnci artık sabit bir değer yerine çip alanına göre ölçekleniyor.")
                        appendLine("• Toplam sistem direnci artık çip güçlerine göre ağırlıklı ortalama alınıyor.")
                        appendLine()
                        appendLine("🐛 Kritik Hata Düzeltmeleri:")
                        appendLine("• Çoklu tasarım karşılaştırmasındaki sınırlamanın yanlışlıkla ısı kaynağı listesine uygulanması (olası veri kaybı) düzeltildi.")
                        appendLine("• Proje yüklerken ortam sıcaklığı/irtifa birimlerinin sıfırlanması düzeltildi.")
                        appendLine("• Karşılaştırma listesinden bir tasarım silinip yeniden eklendiğinde oluşan çakışan etiket sorunu giderildi.")
                        appendLine("• Termal harita renk skalası sabit aralık yerine sahneye göre dinamik min/maks alacak şekilde düzeltildi (PDF raporunda da aynı şekilde).")
                        appendLine("• Çip sıcaklık renklendirmesindeki sabit eşik, gerçek uyarı durumuna bağlandı.")
                        appendLine()
                        appendLine("📄 Arayüz ve Rapor İyileştirmeleri:")
                        appendLine("• Toplam güç, R_total ve duyarlılık analizi değerlerinin gösterim hassasiyeti düzeltildi.")
                        appendLine("• Geçici (transient) ısı eğrisindeki kararlı-hâl sıcaklığı, junction sıcaklığıyla tutarlı hale getirildi.")
                        appendLine("• PDF ısı kaynağı tablosundaki sütun taşmaları ve başlıktaki proje adı taşması giderildi.")
                        appendLine("• Çoklu çip yerleşiminde iki veya daha fazla ısı kaynağı birbirine çok yakınsa kullanıcıyı bilgilendiren yeni bir uyarı eklendi (hesaplama her çipi hâlâ bağımsız değerlendiriyor, bu nedenle çok yakın yerleşimlerde gerçek sıcaklıklar biraz daha yüksek çıkabilir).")
                        appendLine()
                        appendLine("🧪 Güvenilirlik:")
                        append("• HeatsinkSolver motoru için otomatik regresyon testleri eklendi; kritik fiziksel davranışlar artık her değişiklikten sonra otomatik doğrulanabiliyor.")
                    },
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))

                Text("Dovahkiin V2.1 - Gelişmiş Analiz Güncellemesi", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))

                Text(
                    buildString {
                        appendLine("✨ Yeni Analiz Özellikleri:")
                        appendLine("• Custom malzeme için yoğunluk (g/cm³) ve özgül ısı (J/kg·K) girişi eklendi.")
                        appendLine("• CFD/Deney Kalibrasyon Çarpanı ile analitik model gerçek test sonuçlarına göre ayarlanabiliyor.")
                        appendLine("• Geçici (Transient) Sıcaklık Eğrisi: cihazın açıldığı andan itibaren sıcaklığın zamanla yükselişini gösteren grafik, τ ve 3τ noktaları işaretli.")
                        appendLine("• Duyarlılık Analizi (Tornado Chart): tb/tf/S/k/hf parametrelerinden hangisinin sonuç üzerinde en etkili olduğunu gösteriyor.")
                        appendLine("• Optimizasyon Isı Haritası: bulunan en iyi tasarımın etrafındaki tüm kanat kalınlığı/aralığı kombinasyonlarını renkli bir ızgarada gösteriyor.")
                        appendLine("• Çoklu Tasarım Karşılaştırma: farklı ayarlarla yapılan hesaplamaları (en fazla 4) yan yana karşılaştırma.")
                        appendLine("• PDF raporuna Fan P-Q kesişim grafiği sayfası eklendi.")
                        appendLine()
                        appendLine("🛠️ Mimari ve Kayıt Sistemi:")
                        appendLine("• Tek dosyalık kod, sorumluluklarına göre 7 ayrı dosyaya bölündü (MainActivity, HeatsinkModels, HeatsinkViewModel, HeatsinkSolver, Screens, PdfGenerator, Dialogs).")
                        appendLine("• Custom malzeme değerleri ve karşılaştırma listesi artık proje kaydına dahil ediliyor.")
                        appendLine("• Aynı isimle proje kaydetmeye çalışıldığında erken uyarı verilip engelleniyor.")
                        appendLine("• Optimizasyon geçerli bir sonuç bulamadığında kullanıcıyı korkutmayan, sakin tonlu bir bilgi notu ekleniyor.")
                        appendLine()
                        appendLine("🔬 Hesaplama Doğruluğu Düzeltmeleri:")
                        appendLine("• Işınım (radyasyon) katsayısı artık sabit tahmin yerine kendini düzelten iterasyonla çözülüyor.")
                        appendLine("• Kanat verimliliği hesabı, adyabatik uç düzeltmesiyle (Lc = hf + tf/2) daha gerçekçi hale getirildi.")
                        append("• Kanal basınç kaybı hesabı tekilleştirildi (DRY), fan eğrisi ve sabit hız/debi modlarında tutarlı çalışıyor.")
                    },
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))

                Text("Dovahkiin V2.0 - Mega Güncelleme", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))

                Text(
                    "🔥 Isı ve Akış Motoru Yenilikleri:\n" +
                            "• P-Q Fan Eğrisi Kesişim Motoru (Sistem Dengesi) eklendi.\n" +
                            "• Görüş Faktörü (View Factor) ışınım blokajı (Kavite Etkisi) hesaplaması eklendi.\n" +
                            "• Zamana Bağlı Isıl Tepki (Termal Zaman Sabiti - Tau) analizleri eklendi.\n" +
                            "• Elenbaas Doğal Taşınım Boğulma (Choking) modeli eklendi.\n" +
                            "• Çoklu Isı Kaynağı (Çip) yerleşimi, TIM katmanı ve Isı Yoğunluğu (W/cm²) analizi eklendi.\n" +
                            "• Kısıtlı Alan Optimizasyon Motoru (Otomatik En İyi Geometriyi Bulma) geliştirildi.\n" +
                            "• Rakım, Kasa Bypass Tüneli ve Yerleşim Açısı (Yerçekimi) etkileri sisteme dahil edildi.\n\n" +
                            "🎨 Arayüz (UI) ve Raporlama:\n" +
                            "• Canlı 2D İmalat Kesiti ve Çip Yerleşim Şeması çizimleri eklendi.\n" +
                            "• Isı Dağılımını gösteren renkli Termal Harita (Heatmap) oluşturuldu.\n" +
                            "• Darboğazları (Bottleneck) bulan Akıllı Teşhis Asistanı ve Pasta Grafiği eklendi.\n" +
                            "• Proje Kaydetme / Yükleme sistemi eklendi (Metrik ve Imperial tam destek).\n" +
                            "• Detaylı 2 Sayfalık PDF Raporlama Sistemi (Termal harita görseliyle birlikte) eklendi.\n\n" +
                            "🛠️ Kritik Hata Düzeltmeleri:\n" +
                            "• [Düzeltme]: 6061-T4 ve T6 termal iletkenlik değerleri mantıksal olarak ayrıştırıldı.\n" +
                            "• [Düzeltme]: Imperial (ft/min) hava hızı validasyon sınırları gerçeğe uygun ayarlandı.\n" +
                            "• [Düzeltme]: Optimizasyon motoru doğrudan dinamik 'isChoked' filtresine bağlandı.",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))

                Text("Dragonborn V1.1", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                Text(
                    buildString {
                        appendLine("• Esnek Birim Sistemi: Tekli Metrik/Imperial anahtarı yerine, her ölçü (uzunluk, sıcaklık, rakım, debi, hız, basınç, ağırlık, güç, iletkenlik, direnç) için ayrı ayrı seçilebilen birimler eklendi.")
                        appendLine("• Hızlı 'Metrik' / 'Imperial' ön ayar butonları eklendi.")
                        appendLine("• Proje Kimliği (Karşılama) ekranı eklendi; kullanıcı artık projesine bir isim veriyor.")
                        appendLine("• PDF raporu tamamen birim-duyarlı hale getirildi ve rapor başlığına proje adı eklendi.")
                        append("• Birim dönüşüm mantığı (ör. CFM→m³/s) hesap motorundan çıkarılıp ViewModel'e taşındı, çifte dönüşüm riski ortadan kaldırıldı.")
                    },
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFF2C3136), modifier = Modifier.padding(vertical = 4.dp))

                Text("Dragonborn V1.0 - İlk Sürüm", fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                Text(
                    buildString {
                        appendLine("• Temel geometri/malzeme girişi, çoklu ısı kaynağı (çip) yerleşimi ve TIM katmanı modellemesi.")
                        appendLine("• Fan P-Q eğrisi kesişimi ile sistem debisi/basıncının bulunması, kasa bypass (hava kaçağı) modellemesi.")
                        appendLine("• Kısıtlı alan optimizasyon motoru (taban/kanat kalınlığı ve aralığını otomatik tarama).")
                        appendLine("• Isı dağılımı termal haritası ve performans özet sekmeleri.")
                        append("• 2 sayfalık PDF rapor çıktısı.")
                    },
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        containerColor = DialogContainerColor,
        titleContentColor = MaterialTheme.colorScheme.primary,
        textContentColor = DialogBodyTextColorBright
    )
}