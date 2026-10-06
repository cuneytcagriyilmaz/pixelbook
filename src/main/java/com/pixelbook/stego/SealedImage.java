package com.pixelbook.stego;

/**
 * Mühürlenmiş PNG ve onun ölçüleri.
 * Gelen: png baytları, genişlik 320, yükseklik 400, jsonBytes 86.
 * Dışarı: png() aynı dosya baytları, width() 320, height() 400, jsonBytes() 86.
 */
public record SealedImage(byte[] png, int width, int height, int jsonBytes) {
}
