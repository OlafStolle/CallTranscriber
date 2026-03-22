"use client";
export function ExportButton({ callId, remoteNumber, date, text }: { callId: string; remoteNumber: string; date: string; text: string }) {
  function exportAsText() {
    const content = `Gespraech: ${remoteNumber}\nDatum: ${new Date(date).toLocaleString("de-DE")}\n\n${text}`;
    const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = `transkript-${remoteNumber}-${date.slice(0, 10)}.txt`; a.click(); URL.revokeObjectURL(url);
  }
  function exportAsCsv() {
    const rows = [["Nummer", "Datum", "Transkript"], [remoteNumber, new Date(date).toLocaleString("de-DE"), `"${text.replace(/"/g, '""')}"`]];
    const csv = rows.map((r) => r.join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = `transkript-${remoteNumber}-${date.slice(0, 10)}.csv`; a.click(); URL.revokeObjectURL(url);
  }
  return (
    <div className="flex gap-2">
      <button onClick={exportAsText} className="px-3 py-1 text-sm border rounded hover:bg-gray-50">TXT Export</button>
      <button onClick={exportAsCsv} className="px-3 py-1 text-sm border rounded hover:bg-gray-50">CSV Export</button>
    </div>
  );
}
