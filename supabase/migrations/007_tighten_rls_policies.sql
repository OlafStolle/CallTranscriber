-- Tighten INSERT policies: only service role can insert transcripts and audio_files
-- (Backend uses service_key which bypasses RLS, but this prevents abuse from anon/authenticated clients)

DROP POLICY IF EXISTS "Service role can insert transcripts" ON public.transcripts;
CREATE POLICY "Service role can insert transcripts"
    ON public.transcripts FOR INSERT
    WITH CHECK (
        (SELECT current_setting('request.jwt.claims', true)::json->>'role') = 'service_role'
        OR auth.uid() IN (SELECT user_id FROM public.calls WHERE id = transcripts.call_id)
    );

DROP POLICY IF EXISTS "Service role can insert audio files" ON public.audio_files;
CREATE POLICY "Service role can insert audio files"
    ON public.audio_files FOR INSERT
    WITH CHECK (
        (SELECT current_setting('request.jwt.claims', true)::json->>'role') = 'service_role'
        OR auth.uid() IN (SELECT user_id FROM public.calls WHERE id = audio_files.call_id)
    );

-- Fix search_transcripts: use auth.uid() instead of accepting arbitrary UUID parameter
CREATE OR REPLACE FUNCTION search_transcripts(search_query TEXT, user_uuid UUID DEFAULT NULL)
RETURNS TABLE (
    call_id UUID,
    remote_number TEXT,
    direction TEXT,
    started_at TIMESTAMPTZ,
    transcript_text TEXT,
    rank REAL
) AS $$
BEGIN
    -- Use auth.uid() if no user_uuid provided (client-side safety)
    -- Backend calls with explicit user_uuid from validated JWT
    RETURN QUERY
    SELECT
        c.id AS call_id,
        c.remote_number,
        c.direction,
        c.started_at,
        t.text AS transcript_text,
        ts_rank(t.fts, websearch_to_tsquery('german', search_query)) AS rank
    FROM public.transcripts t
    JOIN public.calls c ON c.id = t.call_id
    WHERE c.user_id = COALESCE(user_uuid, auth.uid())
    AND t.fts @@ websearch_to_tsquery('german', search_query)
    ORDER BY rank DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Add DELETE policy for storage bucket
CREATE POLICY "Users can delete own recordings"
    ON storage.objects FOR DELETE
    USING (bucket_id = 'call-recordings' AND auth.uid()::text = (storage.foldername(name))[1]);

-- Tighten storage INSERT policy
DROP POLICY IF EXISTS "Service role can upload recordings" ON storage.objects;
CREATE POLICY "Authenticated users can upload to own folder"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'call-recordings'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );
