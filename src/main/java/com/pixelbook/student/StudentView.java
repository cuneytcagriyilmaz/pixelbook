package com.pixelbook.student;

import com.pixelbook.stego.CourseGrade;

import java.util.List;

/**
 * 3. ekranın listede gördüğü öğrenci.
 * Gelen satır no 2026001, fotoğrafın içindeki dersler [{"ders":"Veri Yapıları","puan":85}].
 * Dışarı JSON: {"no":"2026001","ad":"Ahmet Yılmaz","bolum":"Bilgisayar Mühendisliği","dersler":[...],"gecerli":true,"cop":false}
 */
public record StudentView(
        String no,
        String ad,
        String bolum,
        List<CourseGrade> dersler,
        boolean gecerli,
        boolean cop
) {
}
