import type { Course } from "@/lib/api";

/**
 * Karne satırlarını çizer.
 * Gelen: [{ders:"Veri Yapıları", puan:85}].
 * İçeride: her kayıt bir tablo satırı olur. Liste boşsa tek satır "Henüz ders yok." olur.
 * Dışarı: tabloda ders solda, puan sağda durur.
 */
export function Ledger({ courses }: { courses: Course[] }) {
  const rows = courses.length ? courses : [{ ders: "Henüz ders yok.", puan: null }];
  return (
    <table className="ledger">
      <thead>
        <tr>
          <th>Ders</th>
          <th className="score">Puan</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((course, index) => (
          <tr key={`${course.ders}-${index}`} className={course.puan === null ? "quiet" : undefined}>
            <td>{course.ders}</td>
            <td className="score">{course.puan ?? ""}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
