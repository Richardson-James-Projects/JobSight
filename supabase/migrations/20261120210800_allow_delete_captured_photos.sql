-- Allow deleting captured photos and storage objects from the field app
grant delete on public.captured_photos to anon, authenticated;

create policy "Allow delete captured_photos for field app"
  on public.captured_photos
  for delete
  to anon, authenticated
  using (true);

create policy "Allow delete job_site_photos"
  on storage.objects
  for delete
  to anon, authenticated
  using (bucket_id = 'job_site_photos');
