# 📱 DeckPad — PC Remote Control Ecosystem

**DeckPad**, Android cihazınızı bilgisayarınız için gecikmesiz, yüksek hassasiyetli bir **Dokunmatik Yüzey (Touchpad)**, **Numerik Tuş Takımı (Numpad)**, **Medya & Sunum Kumandası** ve **Sanal PC Klavyesi**ne dönüştüren açık kaynaklı modern bir Android uygulamasıdır.

Bilgisayarınıza herhangi bir ekstra sunucu programı, sürücü veya üçüncü parti yazılım yüklemenize gerek kalmadan, standart **Bluetooth HID (Human Interface Device)** protokolü üzerinden doğrudan donanım seviyesinde bağlanır.

---

## ✨ Temel Özellikler

### 🖱️ 1. Gelişmiş Dokunmatik Yüzey (Touchpad)
* **Gelişmiş Çoklu Dokunma Jestleri:**
  * **1 Parmak:** Hassas imleç hareketi ve tıklama.
  * **Çift Dokunma & Sürükleme:** Pencereleri ve nesneleri sürükle-bırak (Drag Lock).
  * **2 Parmak Kaydırma (Scroll):** Dikey ve yatay yumuşak (smooth) sayfa kaydırma.
  * **2 Parmak Yakınlaştırma (Pinch-to-Zoom):** Hassas yakınlaştırma ve uzaklaştırma (Ctrl + Wheel).
  * **3 Parmak Dokunma / Kaydırma:** Görev Görünümü (Win+Tab), Masaüstünü Göster (Win+D) veya özel eylemler.
  * **4 Parmak Dokunma / Kaydırma:** Bildirim Merkezi (Win+A) veya ses kontrolleri.
* **Tam Ekran Modu:** İmmersive mod ve ekran parlaklığı koruması ile dikkat dağıtmayan dev dokunmatik alan.

### 🔢 2. Numerik Klavye (Numpad)
* Excel tabloları, veri girişi ve hesaplama işlemleri için optimize edilmiş masaüstü klavye düzeni.
* Aritmetik operatörler (`/`, `*`, `-`, `+`), `NumLk`, `Sil` (Backspace), `Tab` ve `Enter` tuşları.
* Tüm ekran boyutlarında ve tabletlerde ergonomik kare tuş oranları (`aspectRatio`).

### 🎵 3. Medya & Sunum Kumandası
* **Medya Oynatıcı:** Şarkı/video duraklatma, başlatma, önceki/sonraki parça ve ses açma/kısma/sessize alma.
* **Sunum Kumandası (PowerPoint / PDF):**
  * Slayt ileri / geri için geniş dokunma alanları.
  * Tam ekranda başlatma (`F5`), geçerli slayttan başlatma (`Shift+F5`), ekranı karartma (`B`) ve sunumu bitirme (`Esc`).

### ⌨️ 4. Sanal PC Klavyesi
* **Çift Düzen Desteği:** Tek tıkla **Türkçe Q** ve **İngilizce US** klavye geçişi.
* **Akıllı Niteleyici Tuşlar:** `Shift` (tek basım & kilitleme), `CapsLock`, yapışkan `Ctrl`, `Alt`, `Win`.
* **Çift Modlu Windows Tuşu:** Tek basımda `Win+D`, `Win+E` gibi kısayolları tetikler; uzun basımda veya çift tıklamada doğrudan Windows Başlat Menüsü'nü açar.
* **Hızlı Kısayol Çubuğu:** `Esc`, `Tab`, `Ctrl+C` (Kopyala), `Ctrl+V` (Yapıştır), `Ctrl+Z` (Geri Al), `Ctrl+Alt+Del`.

---

## 🚀 Teknolojik Altyapı & Mimari

* **Dil & Çatı:** 100% Kotlin & Jetpack Compose (Modern Bildirimsel UI).
* **Mimari:** Clean Architecture + MVVM + Unidirectional Data Flow (UDF).
* **Protokol:** Android Bluetooth HID Device Profile (`BluetoothHidDevice`).
* **Bağımlılık Yönetimi (DI):** Google Hilt (Dependency Injection).
* **Kalıcılık:** Android Jetpack DataStore Preferences.
* **Arka Plan Servisi:** `HidForegroundService` ile ekran kapandığında veya uygulama alta alındığında kopmayan kesintisiz Bluetooth bağlantısı.

---

## 🛠️ Kurulum ve Derleme

Projeyi Android Studio ile derlemek için:

```bash
# Projeyi klonlayın
git clone https://github.com/ahmetselmancloud/touchpad-ekosistem.git

# Proje dizinine girin
cd touchpad-ekosistem

# Debug APK derleyin
./gradlew assembleDebug

# Cihaza yükleyin
./gradlew installDebug
```

---

## 📄 Lisans
Bu proje açık kaynaklıdır ve MIT lisansı altında dağıtılmaktadır.
