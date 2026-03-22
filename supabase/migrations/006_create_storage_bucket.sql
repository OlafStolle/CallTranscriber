-- supabase/migrations/006_create_storage_bucket.sql
INSERT INTO storage.buckets (id, name, public)
VALUES ('call-recordings', 'call-recordings', false);

CREATE POLICY "Users can read own recordings"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'call-recordings' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Service role can upload recordings"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'call-recordings');
