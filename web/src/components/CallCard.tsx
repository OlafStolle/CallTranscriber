import type { Call } from "@/lib/types";
import Link from "next/link";

export function CallCard({ call }: { call: Call & { transcript_text?: string } }) {
  const date = new Date(call.started_at).toLocaleString("de-DE");
  const duration = call.duration_seconds
    ? `${Math.floor(call.duration_seconds / 60)}:${String(call.duration_seconds % 60).padStart(2, "0")}`
    : "-";
  return (
    <Link href={`/calls/${call.id}`}>
      <div className="p-4 border rounded-lg hover:bg-gray-50 cursor-pointer">
        <div className="flex justify-between items-center">
          <span className="font-medium">{call.remote_number}</span>
          <span className="text-sm text-gray-500">{call.direction === "inbound" ? "Eingehend" : "Ausgehend"}</span>
        </div>
        <div className="text-sm text-gray-500 mt-1">{date} — {duration} Min</div>
        {call.transcript_text && (<p className="text-sm text-gray-600 mt-2 line-clamp-2">{call.transcript_text}</p>)}
        <span className={`text-xs mt-1 inline-block px-2 py-0.5 rounded ${call.status === "completed" ? "bg-green-100 text-green-800" : call.status === "failed" ? "bg-red-100 text-red-800" : "bg-yellow-100 text-yellow-800"}`}>{call.status}</span>
      </div>
    </Link>
  );
}
