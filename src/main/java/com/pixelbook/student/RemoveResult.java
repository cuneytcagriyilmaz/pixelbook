package com.pixelbook.student;

/**
 * Vesikalık bırakılarak silinen kaydın duyurusu.
 * Gelen: mühürlü PNG, içinde ad "Ahmet Yılmaz", no "2026001", bölüm "Bilgisayar Mühendisliği".
 * Dışarı JSON: {"mesaj":"Ahmet Yılmaz (2026001) kaydı silindi.","no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği"}
 */
public record RemoveResult(String mesaj, String no, String ad, String bolum) {
}
