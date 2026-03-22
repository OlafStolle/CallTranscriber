-- supabase/migrations/002_create_transcripts_table.sql
CREATE TABLE public.transcripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL REFERENCES public.calls(id) ON DELETE CASCADE,
    text TEXT NOT NULL DEFAULT '',
    language TEXT DEFAULT 'de',
    segments JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transcripts_call_id ON public.transcripts(call_id);
