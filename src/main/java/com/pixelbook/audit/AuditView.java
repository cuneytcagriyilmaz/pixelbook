package com.pixelbook.audit;

import java.time.Instant;

/**
 * Denetim listesinin tek satırı.
 * Gelen: kullanici "Ayşe Memur", islem "cope-at", no "2026001".
 * Dışarı JSON: {"zaman":"...","kullanici":"Ayşe Memur","islem":"cope-at","no":"2026001","ozet":"..."}
 */
public record AuditView(Instant zaman, String kullanici, String islem, String no, String ozet) {
}
