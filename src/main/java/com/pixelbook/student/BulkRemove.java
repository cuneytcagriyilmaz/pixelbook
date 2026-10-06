package com.pixelbook.student;

import java.util.List;

/**
 * Birden fazla vesikalığın çöpe atılma sonucu.
 * Gelen: iki PNG. Biri mühürlü, biri bozuk.
 * Dışarı JSON: {"silinen":["Ahmet Yılmaz (2026001) kaydı silindi."],"reddedilen":["KRİTİK UYARI: ... Kayıt silinmedi."]}
 */
public record BulkRemove(List<String> silinen, List<String> reddedilen) {
}
