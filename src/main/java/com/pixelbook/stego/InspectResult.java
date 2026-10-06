package com.pixelbook.stego;

import java.util.List;

/**
 * Denetim ve kayıt ekranının okuduğu sonuç.
 * Geçerli örnek: gecerli true, ad "Ahmet Yılmaz", no "2026001", bolum "Bilgisayar Mühendisliği", dersler [].
 * Bozuk örnek: gecerli false, mesaj KRİTİK UYARI cümlesi, dersler içinde puan 71.
 */
public record InspectResult(
        boolean gecerli,
        String mesaj,
        String ad,
        String no,
        String bolum,
        List<CourseGrade> dersler,
        String json
) {
}
