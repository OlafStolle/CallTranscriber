-- supabase/migrations/005_create_fulltext_index.sql
ALTER TABLE public.transcripts ADD COLUMN IF NOT EXISTS fts tsvector
    GENERATED ALWAYS AS (to_tsvector('german', coalesce(text, ''))) STORED;
CREATE INDEX idx_transcripts_fts ON public.transcripts USING GIN(fts);

CREATE OR REPLACE FUNCTION search_transcripts(search_query TEXT, user_uuid UUID)
RETURNS TABLE (
    call_id UUID, remote_number TEXT, direction TEXT, started_at TIMESTAMPTZ, transcript_text TEXT, rank REAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT c.id AS call_id, c.remote_number, c.direction, c.started_at, t.text AS transcript_text,
        ts_rank(t.fts, websearch_to_tsquery('german', search_query)) AS rank
    FROM public.transcripts t
    JOIN public.calls c ON c.id = t.call_id
    WHERE c.user_id = user_uuid AND t.fts @@ websearch_to_tsquery('german', search_query)
    ORDER BY rank DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
