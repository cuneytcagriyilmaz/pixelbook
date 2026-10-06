"use client";

import { useState } from "react";
import { registerStudent, type RegisterResult } from "@/lib/api";
import { Dropzone } from "./dropzone";

/**
 * 2. ekran. Form kutusu yoktur.
 * Gelen: yalnız akıllı PNG.
 * İçeride: registerStudent piksellerdeki kimliği tabloya yazdırır.
 * Dışarı: yeşil mühür ve "Ahmet Yılmaz (2026001) - Bilgisayar Mühendisliği kaydı başarıyla oluşturuldu!"
 * İmza tutmuyorsa mühür kırmızı olur.
 */
export function RegisterPanel({ onBurst }: { onBurst: () => void }) {
  const [result, setResult] = useState<RegisterResult | null>(null);
  const [error, setError] = useState("");

  async function onFile(file: File) {
    setError("");
    try {
      const payload = await registerStudent(file);
      setResult(payload);
      if (payload.yeniKayit) onBurst();
    } catch (caught) {
      setResult(null);
      setError(caught instanceof Error ? caught.message : "İstek tamamlanamadı.");
    }
  }

  return (
    <>
      <div className="sheet-top">
        <span>02 / Kayıt</span>
        <span>Formsuz</span>
      </div>
      <h1>Hızlı kayıt</h1>
      <p className="lead">Bu ekranda ad, numara veya bölüm kutusu yoktur. Akıllı vesikalık ortaya bırakılır.</p>
      {result ? (
        <article className="result-card">
          <div className="wax ok" aria-hidden>✓</div>
          <div>
            <p>{result.mesaj}</p>
            <p className="identity">{result.ad} · {result.no} · {result.bolum}</p>
          </div>
        </article>
      ) : null}
      {error ? (
        <article className="result-card">
          <div className="wax bad" aria-hidden>!</div>
          <p>{error}</p>
        </article>
      ) : null}
      <Dropzone accept="image/png" onFile={onFile} className={result || error ? "" : "well"}>
        <div>
          <strong>Akıllı vesikalığı buraya bırakın</strong>
          <br />
          <em>Kimlik, resmin içinden okunur.</em>
        </div>
      </Dropzone>
    </>
  );
}
