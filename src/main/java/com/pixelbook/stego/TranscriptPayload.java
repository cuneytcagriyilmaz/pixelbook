package com.pixelbook.stego;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Resmin içinde taşınan kimlik ve karne.
 * Gelen form: ad Ahmet Yılmaz, no 2026001, bölüm Bilgisayar Mühendisliği, dersler boş.
 * İçeride kompakt JSON:
 * {"no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği","dersler":[]}
 * Dışarı: no(), ad(), bolum() aynı metinleri döndürür. "ALI" gelirse "ALI" kalır, harf küçültülmez.
 * Bölüm, formda yazıldığı gibi durur; "Bilgisayar Mühendisliği" kodu "CENG" yapılmaz.
 */
@JsonPropertyOrder({"no", "ad", "bolum", "dersler"})
public record TranscriptPayload(String no, String ad, String bolum, List<CourseGrade> dersler) {

    /**
     * Gelen: dersler null ya da [{"ders":"Veri Yapıları","puan":85}].
     * İçeride: null boş listeye çevrilir, dolu liste aynı sırayla kalır.
     * Dışarı: null için [], dolu için aynı tek elemanlı liste.
     */
    public List<CourseGrade> courses() {
        return dersler == null ? List.of() : dersler;
    }
}
