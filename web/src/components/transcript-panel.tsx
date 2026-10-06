"use client";

import { useEffect, useState } from "react";
import { addCourse, allStudents, type CourseUpdate, type Student } from "@/lib/api";
import { Ledger } from "./ledger";

const QUICK = [
  { label: "Veri Yapıları · 85", ders: "Veri Yapıları ve Algoritmalar", puan: "85" },
  { label: "İşletim Sistemleri · 70", ders: "İşletim Sistemleri", puan: "70" },
  { label: "Ayrık Matematik · 92", ders: "Ayrık Matematik", puan: "92" },
];

/**
 * 3. ekran. Sistemdeki akıllı fotoğrafa ders işler.
 * Gelen: no "2026001", ders "İşletim Sistemleri", puan "70".
 * İçeride: addCourse listeye bu satırı ekletir, mühür yenilenir.
 * Dışarı: not satırı "İşletim Sistemleri notu 70 olarak görsele işlendi." ve tuval ölçüsü.
 */
export function TranscriptPanel({ active, onGoRegister }: { active: boolean; onGoRegister: () => void }) {
  const [students, setStudents] = useState<Student[]>([]);
  const [selected, setSelected] = useState("");
  const [ders, setDers] = useState("");
  const [puan, setPuan] = useState("");
  const [note, setNote] = useState("");
  const [bad, setBad] = useState(false);
  const [stamp, setStamp] = useState(0);
  const [measure, setMeasure] = useState<Pick<CourseUpdate, "genislik" | "yukseklik" | "jsonBayt"> | null>(null);

  async function refresh(prefer?: string) {
    const list = await allStudents();
    setStudents(list);
    const next = prefer && list.some((student) => student.no === prefer) ? prefer : list[0]?.no ?? "";
    setSelected(next);
    return list;
  }

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    allStudents()
      .then((list) => {
        if (cancelled) return;
        setStudents(list);
        setSelected((current) => (current && list.some((student) => student.no === current) ? current : list[0]?.no ?? ""));
      })
      .catch((error: Error) => {
        if (cancelled) return;
        setBad(true);
        setNote(error.message);
      });
    return () => {
      cancelled = true;
    };
  }, [active]);

  const student = students.find((item) => item.no === selected);

  async function submit(nextDers: string, nextPuan: string) {
    if (!selected) return;
    setBad(false);
    try {
      const payload = await addCourse(selected, nextDers, nextPuan);
      setNote(`${payload.mesaj} Tuval ${payload.genislik}×${payload.yukseklik} piksel. JSON ${payload.jsonBayt} bayt.`);
      setMeasure(payload);
      setStamp(Date.now());
      setDers("");
      setPuan("");
      await refresh(selected);
    } catch (error) {
      setBad(true);
      setNote(error instanceof Error ? error.message : "İstek tamamlanamadı.");
    }
  }

  if (!students.length) {
    return (
      <>
        <div className="sheet-top">
          <span>03 / Karne</span>
          <span>Boş</span>
        </div>
        <h1>Yaşayan transkript</h1>
        <p className="lead">Henüz kayıt yok. Önce hızlı kayıt ekranına bir akıllı resim bırakın.</p>
        <button className="btn" type="button" onClick={onGoRegister}>Kayıt ekranına geç</button>
      </>
    );
  }

  const photo = `/api/students/${encodeURIComponent(selected)}/photo?t=${stamp}`;

  return (
    <>
      <div className="sheet-top">
        <span>03 / Karne</span>
        <span>{measure ? `${measure.genislik}×${measure.yukseklik} · ${measure.jsonBayt} bayt` : "Resmin içindeki karne"}</span>
      </div>
      <h1>Yaşayan transkript</h1>
      <p className="lead">Sistemdeki akıllı fotoğraf seçilir. Her ders, resmin içindeki listeye eklenir ve mühür yenilenir.</p>
      <label className="field">
        Öğrenci
        <select className="student-select" value={selected} onChange={(event) => setSelected(event.target.value)}>
          {students.map((item) => (
            <option key={item.no} value={item.no}>{item.ad} ({item.no})</option>
          ))}
        </select>
      </label>
      {student ? (
        <div className="split" style={{ marginTop: 28 }}>
          <figure className="frame">
            {/* Akıllı PNG API'den gelir; optimize edici ara katman pikselleri yeniden kodlamasın. */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={photo} alt={`${student.ad} akıllı vesikalığı`} />
            <figcaption className="caption">{student.bolum}</figcaption>
          </figure>
          <div>
            <Ledger courses={student.dersler} />
            <a className="text-link quiet" href={`/api/students/${encodeURIComponent(selected)}/photo`} download={`${selected}_akilli_vesikalik.png`}>
              Güncel akıllı resmi indir
            </a>
            <form
              className="fields"
              style={{ marginTop: 22 }}
              onSubmit={(event) => {
                event.preventDefault();
                submit(ders, puan);
              }}
            >
              <h2 className="subhead">Yeni ders notu ekle</h2>
              <label className="field">
                Ders
                <input value={ders} onChange={(event) => setDers(event.target.value)} placeholder="Veri Yapıları ve Algoritmalar" />
              </label>
              <label className="field">
                Puan
                <input value={puan} onChange={(event) => setPuan(event.target.value)} inputMode="numeric" placeholder="85" />
              </label>
              <button className="btn" type="submit">Nota işle</button>
            </form>
            <div className="chips">
              {QUICK.map((course) => (
                <button key={course.ders} className="chip" type="button" onClick={() => submit(course.ders, course.puan)}>
                  {course.label}
                </button>
              ))}
            </div>
            <p className={`note ${bad ? "bad" : ""}`}>{note}</p>
          </div>
        </div>
      ) : null}
    </>
  );
}
