package com.pixelbook.web;

import com.pixelbook.stego.StegoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

/**
 * Hataları ekranın okuyacağı tek alana, "mesaj" anahtarına çevirir.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Görüntü ve form hatalarını 400 yapar.
     * Gelen: StegoException, mesaj "Puan 0 ile 100 arasında olmalı. Gelen: 150".
     * İçeride: HTTP 400 ve tek anahtarlı harita.
     * Dışarı: {"mesaj":"Puan 0 ile 100 arasında olmalı. Gelen: 150"}
     */
    @ExceptionHandler(StegoException.class)
    public ResponseEntity<Map<String, String>> stego(StegoException ex) {
        return ResponseEntity.badRequest().body(Map.of("mesaj", ex.getMessage()));
    }

    /**
     * Fotoğraf parçası hiç gelmezse 400 döner.
     * Gelen: MissingServletRequestPartException, parça adı "photo".
     * İçeride: parça adı kullanıcı cümlesine çevrilmez, sabit cümle seçilir.
     * Dışarı: {"mesaj":"Fotoğraf seçilmedi."}
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> missingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(Map.of("mesaj", "Fotoğraf seçilmedi."));
    }

    /**
     * Ders gövdesi JSON değilse 400 döner.
     * Gelen: {"puan":"seksen"} gibi okunamayan gövde.
     * İçeride: ayrıştırma hatası tek cümleye indirilir.
     * Dışarı: {"mesaj":"Ders ve puan okunamadı."}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("mesaj", "Ders ve puan okunamadı."));
    }

    /**
     * Beklenmeyen hataları 500 yapar. Yığın izi cevaba konmaz.
     * Gelen: RuntimeException.
     * İçeride: durum 500, gövde sabit cümle.
     * Dışarı: {"mesaj":"İstek tamamlanamadı."}
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> unexpected(RuntimeException ex) {
        log.error("İstek tamamlanamadı", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("mesaj", "İstek tamamlanamadı."));
    }
}
