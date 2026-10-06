package com.pixelbook.student;

import java.util.List;

/**
 * Sayfalanmış kayıt defteri. Notlar hâlâ her vesikalığın içinden gelir.
 * Gelen: q "Yılmaz", sayfa 1, boyut 8, tabloda 12 aktif satır.
 * Dışarı JSON: {"ogrenciler":[...],"toplam":12,"sayfa":1,"boyut":8,"bolumler":["Bilgisayar Mühendisliği"]}
 */
public record StudentPage(
        List<StudentView> ogrenciler,
        int toplam,
        int sayfa,
        int boyut,
        List<String> bolumler
) {
}
