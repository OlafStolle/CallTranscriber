-- supabase/migrations/003_create_audio_files_table.sql
CREATE TABLE public.audio_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id UUID NOT NULL REFERENCES public.calls(id) ON DELETE CASCADE,
    storage_path TEXT NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    format TEXT NOT NULL DEFAULT 'wav',
    duration_seconds INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audio_files_call_id ON public.audio_files(call_id);
