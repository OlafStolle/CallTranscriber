export interface Call {
  id: string;
  user_id: string;
  remote_number: string;
  direction: "inbound" | "outbound";
  started_at: string;
  ended_at: string | null;
  duration_seconds: number | null;
  status: "recording" | "uploading" | "transcribing" | "completed" | "failed";
  created_at: string;
}

export interface Transcript {
  id: string;
  call_id: string;
  text: string;
  language: string;
  segments: TranscriptSegment[];
  created_at: string;
}

export interface TranscriptSegment {
  start: number;
  end: number;
  text: string;
}

export interface AudioFile {
  id: string;
  call_id: string;
  storage_path: string;
  size_bytes: number;
  format: string;
}
