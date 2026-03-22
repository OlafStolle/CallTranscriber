-- supabase/migrations/004_enable_rls_policies.sql
ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transcripts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audio_files ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own calls" ON public.calls FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can insert own calls" ON public.calls FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own calls" ON public.calls FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete own calls" ON public.calls FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can view own transcripts" ON public.transcripts FOR SELECT
    USING (EXISTS (SELECT 1 FROM public.calls WHERE calls.id = transcripts.call_id AND calls.user_id = auth.uid()));
CREATE POLICY "Service role can insert transcripts" ON public.transcripts FOR INSERT WITH CHECK (true);

CREATE POLICY "Users can view own audio files" ON public.audio_files FOR SELECT
    USING (EXISTS (SELECT 1 FROM public.calls WHERE calls.id = audio_files.call_id AND calls.user_id = auth.uid()));
CREATE POLICY "Service role can insert audio files" ON public.audio_files FOR INSERT WITH CHECK (true);
