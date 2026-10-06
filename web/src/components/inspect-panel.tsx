"use client";

import { useState } from "react";
import { inspectImage, saveBlob, tamperImage, type InspectResult } from "@/lib/api";
import { Dropzone } from "./dropzone";
import { Ledger } from "./ledger";

/**
 * 4. ekran. Mührü sorar, istenirse bir rakamı bozar.
 * Gelen: orijinal PNG.
 * İçeride: inspectImage imzayı yeniden hesaplatır.
 * Dışarı: gecerli true ise başlık "Mühür geçerli". Bozuk kopyada "Mühür geçersiz" ve KRİTİK UYARI cümlesi.
 */
export function InspectPanel() {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<InspectResult | null>(null);
  const [error, setError] = useState("");

  async function onFile(next: File) {
    setFile(next);
    setError("");
    try {
      setResult(await inspectImage(next));
    } catch (caught) {
      setResult(null);
      setError(caught instanceof Error ? caught.message : "İstek tamamlanamadı.");
    }
  }

  async function tamper() {
    if (!file) return;
    setError("");
    try {
      const forged = await tamperImage(file);
      saveBlob(forged.blob, forged.filename);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "İstek tamamlanamadı.");
    }
  }

  return (
    <>
      <div className="sheet-top">
        <span>04 / Laboratuvar</span>
        <span>HMAC-SHA256</span>
      </div>
      <h1>Denetim</h1>
      <p className="lead">Şüpheli vesikalık yüklenir. Mühür tutuyorsa karne dökülür. Payload kurcalanmışsa alarm yanar.</p>
      {result ? (
        <section className={result.gecerli ? "verdict tone-ok" : "verdict tone-bad"}>
          <h2>{result.gecerli ? "Mühür geçerli" : "Mühür geçersiz"}</h2>
          <p>{result.mesaj}</p>
          {result.ad ? <p className="identity">{result.ad} ({result.no}) · {result.bolum}</p> : null}
          <Ledger courses={result.dersler} />
          {result.json ? (
            <details>
              <summary>Gömülü JSON</summary>
              <pre>{result.json}</pre>
            </details>
          ) : null}
          <div className="actions" style={{ marginTop: 18 }}>
            <button className="btn danger" type="button" onClick={tamper}>Bir rakamı boz ve sahte kopyayı indir</button>
          </div>
          <p className="fine">Yeşil sonuç, bu dosyanın kendi mühürünün tuttuğunu gösterir.</p>
        </section>
      ) : null}
      {error ? <p className="note bad">{error}</p> : null}
      <Dropzone accept="image/png,image/*" onFile={onFile} className={result ? "slim" : "well"}>
        <div>
          <strong>{result ? "Başka vesikalık bırakın" : "Vesikalığı buraya bırakın"}</strong>
          <br />
          <em>Orijinal yeşil, kurcalanmış kopya kırmızı döner.</em>
        </div>
      </Dropzone>
    </>
  );
}
