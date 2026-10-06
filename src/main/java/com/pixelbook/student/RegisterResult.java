package com.pixelbook.student;

/**
 * Hızlı kayıt ekranının yeşil duyurusu.
 * Gelen ilk kayıt: mesaj "Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı başarıyla oluşturuldu!", yeniKayit true.
 * Gelen ikinci bırakış: mesaj "...kaydı güncellendi...", yeniKayit false.
 */
public record RegisterResult(String mesaj, String no, String ad, String bolum, boolean yeniKayit) {
}
