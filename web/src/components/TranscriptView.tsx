import type { Transcript } from "@/lib/types";

function formatTime(seconds: number): string {
  const min = Math.floor(seconds / 60);
  const sec = Math.floor(seconds % 60);
  return `${min}:${String(sec).padStart(2, "0")}`;
}

export function TranscriptView({ transcript }: { transcript: Transcript }) {
  if (transcript.segments.length > 0) {
    return (
      <div className="space-y-3">
        {transcript.segments.map((segment, i) => (
          <div key={i} className="flex gap-3">
            <span className="text-xs text-gray-400 font-mono min-w-[50px] pt-0.5">{formatTime(segment.start)}</span>
            <p className="text-gray-800">{segment.text}</p>
          </div>
        ))}
      </div>
    );
  }
  return <p className="text-gray-800 whitespace-pre-wrap">{transcript.text}</p>;
}
