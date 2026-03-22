import { createServerSupabaseClient } from "@/lib/supabase/server";
import { TranscriptView } from "@/components/TranscriptView";
import { ExportButton } from "@/components/ExportButton";
import Link from "next/link";
import type { Call, Transcript } from "@/lib/types";

export default async function CallDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const supabase = await createServerSupabaseClient();
  const { data: call } = await supabase.from("calls").select("*").eq("id", id).single<Call>();
  const { data: transcript } = await supabase.from("transcripts").select("*").eq("call_id", id).single<Transcript>();
  if (!call) return <p>Gespraech nicht gefunden</p>;
  const date = new Date(call.started_at).toLocaleString("de-DE");
  const duration = call.duration_seconds ? `${Math.floor(call.duration_seconds / 60)}:${String(call.duration_seconds % 60).padStart(2, "0")}` : "-";

  return (
    <div className="max-w-4xl mx-auto p-6">
      <Link href="/calls" className="text-blue-600 hover:underline text-sm">Zurueck zur Liste</Link>
      <div className="mt-4 mb-6">
        <h1 className="text-2xl font-bold">{call.remote_number}</h1>
        <div className="text-gray-500 mt-1">{call.direction === "inbound" ? "Eingehend" : "Ausgehend"} — {date} — {duration} Min</div>
      </div>
      <div className="flex gap-2 mb-6">{transcript && <ExportButton callId={call.id} remoteNumber={call.remote_number} date={call.started_at} text={transcript.text} />}</div>
      <hr className="mb-6" />
      <h2 className="text-xl font-semibold mb-4">Transkript</h2>
      {transcript ? <TranscriptView transcript={transcript} /> : <p className="text-gray-500">Transkript wird erstellt...</p>}
    </div>
  );
}
