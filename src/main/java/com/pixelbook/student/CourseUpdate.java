package com.pixelbook.student;

import com.pixelbook.stego.CourseGrade;

import java.util.List;

/**
 * Ders eklendikten sonra 3. ekrana dönen özet.
 * Gelen: "İşletim Sistemleri notu 70 olarak görsele işlendi.", dersler tek satır, tuval 320x400.
 * İçeride boş karnenin 86 baytlık JSON'una bu satır eklenir.
 * Dışarı: jsonBayt 127. png bu nesnede yoktur; fotoğraf ayrı adresten indirilir.
 */
public record CourseUpdate(
        String mesaj,
        List<CourseGrade> dersler,
        int genislik,
        int yukseklik,
        int jsonBayt
) {
}
