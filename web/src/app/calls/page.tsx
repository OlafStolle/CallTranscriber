import { createServerSupabaseClient } from "@/lib/supabase/server";
import { CallCard } from "@/components/CallCard";
import { SearchBar } from "@/components/SearchBar";
import type { Call } from "@/lib/types";

export default async function CallsPage({ searchParams }: { searchParams: Promise<{ q?: string }> }) {
  const { q } = await searchParams;
  const supabase = await createServerSupabaseClient();
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return null;

  let calls: (Call & { transcript_text?: string })[];

  if (q) {
    const { data } = await supabase.rpc("search_transcripts", { search_query: q, user_uuid: user.id });
    calls = (data || []).map((row: any) => ({
      id: row.call_id, user_id: user.id, remote_number: row.remote_number, direction: row.direction,
      started_at: row.started_at, ended_at: null, duration_seconds: null, status: "completed" as const, created_at: row.started_at, transcript_text: row.transcript_text,
    }));
  } else {
    const { data: callData } = await supabase.from("calls").select("*").order("started_at", { ascending: false }).limit(50);
    const callIds = (callData || []).map((c: Call) => c.id);
    const { data: transcripts } = callIds.length > 0
      ? await supabase.from("transcripts").select("call_id, text").in("call_id", callIds)
      : { data: [] };
    const transcriptMap = new Map((transcripts || []).map((t: any) => [t.call_id, t.text]));
    calls = (callData || []).map((c: Call) => ({ ...c, transcript_text: transcriptMap.get(c.id) }));
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Gespraeche</h1>
        <form action="/api/auth/signout" method="POST">
          <button className="text-sm text-gray-500 hover:text-gray-700">Abmelden</button>
        </form>
      </div>
      <div className="mb-6"><SearchBar /></div>
      <div className="space-y-3">
        {calls.length === 0 ? (<p className="text-center text-gray-500 py-12">Keine Gespraeche gefunden</p>) : calls.map((call) => <CallCard key={call.id} call={call} />)}
      </div>
    </div>
  );
}
