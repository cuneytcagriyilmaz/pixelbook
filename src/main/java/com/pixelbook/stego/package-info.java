/**
 * Piksellerin içine yazılan çerçevenin sırası sabittir:
 * [4 bayt uzunluk][JSON baytları][32 bayt HMAC-SHA256].
 * <p>
 * Uzunluk, JSON'un bayt sayısıdır ve big-endian yazılır.
 * Örnek: JSON 7 bayt ise başlık {@code 00 00 00 07} olur, çerçeve 4+7+32 = 43 bayttır.
 * HMAC, uzunluk baytları ile JSON baytlarının birleşimini kapsar. İmza baytlarının kendisini kapsamaz.
 * <p>
 * Bitler satır satır, soldan sağa yürür. Her pikselde sıra kırmızı, yeşil, mavidir.
 * Her baytın yüksek biti önce yazılır. {@code 'A' = 0x41 = 01000001} sekiz kanala
 * {@code 0,1,0,0,0,0,0,1} olarak dağılır.
 */
package com.pixelbook.stego;
