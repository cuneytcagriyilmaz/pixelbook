package com.pixelbook.stego;

/**
 * Gömülü kayıt okunamadığında veya form alanı işlenemediğinde fırlatılır.
 * İmza uyuşmazlığı her zaman istisna değildir; denetim ekranı onu sonuç nesnesiyle taşır.
 */
public class StegoException extends RuntimeException {

    /**
     * Gelen: "Fotoğraf boş."
     * İçeride: mesaj olduğu gibi saklanır, başka alana çevrilmez.
     * Dışarı: getMessage() aynı cümleyi döndürür.
     */
    public StegoException(String message) {
        super(message);
    }

    /**
     * Gelen: "Dosya resim olarak açılamadı." ve altta bir IOException.
     * İçeride: mesaj üstte, neden olarak IOException durur.
     * Dışarı: getMessage() üst cümleyi, getCause() alttaki hatayı verir.
     */
    public StegoException(String message, Throwable cause) {
        super(message, cause);
    }
}
