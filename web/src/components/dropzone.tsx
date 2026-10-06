"use client";

import { useState, type ReactNode } from "react";

/**
 * Dosya bırakma alanı.
 * Gelen: ilk dosya ahmet.png.
 * İçeride: drop veya change olayından files[0] alınır. Sonraki dosyalar kullanılmaz.
 * Dışarı: onFile aynı File nesnesini verir.
 */
export function Dropzone({
  accept,
  onFile,
  className,
  children,
}: {
  accept: string;
  onFile: (file: File) => void;
  className?: string;
  children: ReactNode;
}) {
  const [hot, setHot] = useState(false);

  return (
    <label
      className={`drop ${hot ? "hot" : ""} ${className ?? ""}`}
      onDragOver={(event) => {
        event.preventDefault();
        setHot(true);
      }}
      onDragLeave={() => setHot(false)}
      onDrop={(event) => {
        event.preventDefault();
        setHot(false);
        const file = event.dataTransfer.files[0];
        if (file) onFile(file);
      }}
    >
      <input
        type="file"
        accept={accept}
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) onFile(file);
          event.target.value = "";
        }}
      />
      {children}
    </label>
  );
}
