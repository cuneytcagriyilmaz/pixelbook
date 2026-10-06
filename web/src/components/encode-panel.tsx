"use client";

import { useState, type FormEvent } from "react";
import { encodeImage, samplePortrait, saveBlob } from "@/lib/api";
import { Dropzone } from "./dropzone";

/**
 * 1. ekran. Vesikalığı ve kimliği mühürletir.
 * Gelen: ad "Ahmet Yılmaz", no "2026001", bölüm "Bilgisayar Mühendisliği", seçilen PNG.
 * İçeride: encodeImage JSON'u son bite yazdırır.
 * Dışarı: 2026001_akilli_vesikalik.png iner. Not satırında tuval ve JSON bayt sayısı görünür.
 */
export function EncodePanel() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [ad, setAd] = useState("Ahmet Yılmaz");
  const [no, setNo] = useState("2026001");
  const [bolum, setBolum] = useState("Bilgisayar Mühendisliği");
  const [note, setNote] = useState("");
  const [bad, setBad] = useState(false);
  const [busy, setBusy] = useState(false);

  function choose(next: File) {
    setPreview((current) => {
      if (current) URL.revokeObjectURL(current);
      return URL.createObjectURL(next);
    });
    setFile(next);
  }

  async function useSample() {
    setBad(false);
    setBusy(true);
    try {
      const sample = await samplePortrait(ad);
      choose(sample);
      setNote("Örnek vesikalık hazır. Henüz mühür yok.");
    } catch (error) {
      setBad(true);
      setNote(error instanceof Error ? error.message : "İstek tamamlanamadı.");
    } finally {
      setBusy(false);
    }
  }

  async function seal(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      setBad(true);
      setNote("Fotoğraf seçilmedi.");
      return;
    }
    setBusy(true);
    setBad(false);
    try {
      const sealed = await encodeImage(file, ad, no, bolum);
      saveBlob(sealed.blob, sealed.filename);
      setNote(`${sealed.filename} indi. Tuval ${sealed.width}×${sealed.height} piksel. Gömülü JSON ${sealed.jsonBytes} bayt.`);
    } catch (error) {
      setBad(true);
      setNote(error instanceof Error ? error.message : "İstek tamamlanamadı.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="sheet-top">
        <span>01 / Üretim</span>
        <span>Pixelbook</span>
      </div>
      <h1>Akıllı resim</h1>
      <p className="lead">Vesikalık seçilir, kimlik yazılır. İnen dosya sıradan bir PNG gibi durur; numara, ad ve bölüm piksellerin son bitindedir.</p>
      <form className="split" onSubmit={seal}>
        <div>
          {preview ? (
            <figure className="frame">
              {/* Blob önizlemesi; next/image uzak ve geçici adresleri bu kare için uygun değil. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={preview} alt="Seçilen vesikalık" />
              <figcaption className="caption">Seçilen kare · mürekkep kayıt değildir</figcaption>
            </figure>
          ) : (
            <Dropzone accept="image/png,image/jpeg,image/webp" onFile={choose} className="well">
              <div>
                <strong>Fotoğrafı buraya bırakın</strong>
                <br />
                <em>PNG, JPEG ya da WebP.</em>
              </div>
            </Dropzone>
          )}
          {preview ? (
            <div style={{ marginTop: 14 }}>
              <Dropzone accept="image/png,image/jpeg,image/webp" onFile={choose} className="slim">
                <span>Başka kare seç</span>
              </Dropzone>
            </div>
          ) : null}
        </div>
        <div className="fields">
          <label className="field">
            Ad soyad
            <input value={ad} onChange={(event) => setAd(event.target.value)} autoComplete="name" />
          </label>
          <label className="field">
            Öğrenci no
            <input value={no} onChange={(event) => setNo(event.target.value)} inputMode="numeric" />
          </label>
          <label className="field">
            Bölüm
            <input value={bolum} onChange={(event) => setBolum(event.target.value)} />
          </label>
          <div className="actions">
            <button className="btn" type="submit" disabled={busy}>Akıllı Resim Oluştur ve İndir</button>
            <button className="btn ghost" type="button" onClick={useSample} disabled={busy}>Örnek vesikalık kullan</button>
          </div>
          <p className={`note ${bad ? "bad" : ""}`}>{note}</p>
        </div>
      </form>
    </>
  );
}
