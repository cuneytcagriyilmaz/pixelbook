package com.pixelbook;

/**
 * Ekranda görünen sabit cümleler. Metinler senaryodaki duyuru cümleleriyle aynı kalsın diye tek yerde durur.
 */
public final class Messages {

    /**
     * Denetim ekranının kırmızı alarm cümlesi.
     * Gelen bir sabit metindir, parçalara bölünüp yeniden kurulmaz.
     * Dışarı: aynı cümle, başında emoji yoktur. Emoji arayüzde eklenir.
     */
    public static final String TAMPER =
            "KRİTİK UYARI: Görsel piksellerindeki ders notu verilerinde (payload) manipülasyon tespit edildi! Dijital imza geçersiz.";

    /**
     * Başlık okunamadığında denetim ekranının döndürdüğü cümle.
     * Gelen sabit metin olduğu gibi dışarı çıkar.
     */
    public static final String UNREADABLE = "Bu resimde okunabilir bir gömülü kayıt yok.";

    private Messages() {
    }

    /**
     * İlk kayıt duyurusunu kurar.
     * Gelen: ad "Ahmet Yılmaz", no "2026001", bölüm "Bilgisayar Mühendisliği".
     * İçeride: ad, parantez içindeki numara ve bölüm tek cümlede birleşir.
     * Dışarı: "Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı başarıyla oluşturuldu!"
     */
    public static String registered(String ad, String no, String bolum) {
        return ad + " (" + no + ") - " + bolum + " kaydı başarıyla oluşturuldu!";
    }

    /**
     * Aynı numara ikinci kez bırakılırsa kurulan duyuru.
     * Gelen: ad "Ahmet Yılmaz", no "2026001", bölüm "Bilgisayar Mühendisliği".
     * İçeride: kimlik cümlesinin sonuna güncelleme açıklaması eklenir.
     * Dışarı: "Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı güncellendi. Aynı numara olduğu için yeni satır açılmadı."
     */
    public static String updated(String ad, String no, String bolum) {
        return ad + " (" + no + ") - " + bolum + " kaydı güncellendi. Aynı numara olduğu için yeni satır açılmadı.";
    }

    /**
     * Ders işlendikten sonra dönen cümle.
     * Gelen: ders "Veri Yapıları ve Algoritmalar", puan 85.
     * İçeride: ad ile puan araya "notu" ve "olarak görsele işlendi" konarak birleşir.
     * Dışarı: "Veri Yapıları ve Algoritmalar notu 85 olarak görsele işlendi."
     */
    public static String courseAdded(String ders, int puan) {
        return ders + " notu " + puan + " olarak görsele işlendi.";
    }

    /**
     * Kimlik vesikalığın üstüne yeniden yazılınca kurulan duyuru.
     * Gelen: ad "ELİF DEMİR", no "2026998", bölüm "Yazılım Mühendisliği".
     * İçeride: ad, parantez içindeki numara ve bölüm birleşir. Harfler küçülmez, bölüm "CENG" olmaz.
     * Dışarı: "ELİF DEMİR (2026998) - Yazılım Mühendisliği kaydı güncellendi. Ders notları resmin içinde duruyor."
     */
    public static String identityRevised(String ad, String no, String bolum) {
        return ad + " (" + no + ") - " + bolum + " kaydı güncellendi. Ders notları resmin içinde duruyor.";
    }

    /**
     * Vesikalık bırakılıp kayıt silinince kurulan duyuru.
     * Gelen: ad "Ahmet Yılmaz", no "2026001".
     * İçeride: ad ile parantez içindeki numara birleşir.
     * Dışarı: "Ahmet Yılmaz (2026001) kaydı silindi."
     */
    public static String removed(String ad, String no) {
        return ad + " (" + no + ") kaydı silindi.";
    }

    /**
     * Var olan bir ders satırının puanı değişince kurulan cümle.
     * Gelen: ders "Veri Yapıları ve Algoritmalar", puan 90.
     * İçeride: ders adı ile yeni puan araya "notu" ve "olarak güncellendi" konarak birleşir.
     * Dışarı: "Veri Yapıları ve Algoritmalar notu 90 olarak güncellendi."
     */
    public static String courseChanged(String ders, int puan) {
        return ders + " notu " + puan + " olarak güncellendi.";
    }

    /**
     * Bir ders satırı karneden düşünce kurulan cümle.
     * Gelen: ders "İşletim Sistemleri".
     * İçeride: ders adının sonuna silme cümlesi eklenir.
     * Dışarı: "İşletim Sistemleri notu karneiden silindi."
     */
    public static String courseRemoved(String ders) {
        return ders + " notu karneiden silindi.";
    }

    /**
     * Çöpten çıkarılan kaydın cümlesi.
     * Gelen: ad "Ahmet Yılmaz", no "2026001".
     * İçeride: ad ile parantez içindeki numara birleşir.
     * Dışarı: "Ahmet Yılmaz (2026001) kaydı çöp kutusundan çıkarıldı."
     */
    public static String restored(String ad, String no) {
        return ad + " (" + no + ") kaydı çöp kutusundan çıkarıldı.";
    }

    /**
     * Müdürün kalıcı silme cümlesi.
     * Gelen: ad "Ahmet Yılmaz", no "2026001".
     * İçeride: ad ile numara birleşir.
     * Dışarı: "Ahmet Yılmaz (2026001) kaydı kalıcı olarak silindi."
     */
    public static String purged(String ad, String no) {
        return ad + " (" + no + ") kaydı kalıcı olarak silindi.";
    }
}
