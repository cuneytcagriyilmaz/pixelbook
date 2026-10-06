package com.pixelbook.stego;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Karneye eklenen tek ders satırı. kod, satırın sırası değişse de aynı kalan kimliğidir.
 * Gelen: kod "k1", ders adı "Veri Yapıları", puan 85.
 * İçeride: {@code {"kod":"k1","ders":"Veri Yapıları","puan":85}}. kod boşsa JSON'a yazılmaz.
 * Dışarı: kod() "k1", ders() "Veri Yapıları", puan() 85. "ALI" küçülmez, puana harf notu eklenmez.
 */
@JsonPropertyOrder({"kod", "ders", "puan"})
public record CourseGrade(
        @JsonInclude(JsonInclude.Include.NON_NULL) String kod,
        String ders,
        int puan
) {

    /**
     * Eski çağrıların kodsuz satır kurması.
     * Gelen: ders "Veri Yapıları", puan 85.
     * İçeride: kod null bırakılır. Mühürleme sırasında k1 gibi bir kod verilir.
     * Dışarı: kod() null, ders() "Veri Yapıları", puan() 85.
     */
    public CourseGrade(String ders, int puan) {
        this(null, ders, puan);
    }
}
