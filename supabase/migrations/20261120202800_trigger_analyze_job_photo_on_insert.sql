-- Enable pg_net for asynchronous HTTP webhook triggers from Postgres to analyze-job-photo Edge Function
create extension if not exists pg_net with schema extensions;

create or replace function public.invoke_analyze_job_photo()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform net.http_post(
    url := 'https://otxoozkqhelwqzdqkcxv.supabase.co/functions/v1/analyze-job-photo',
    headers := '{"Content-Type": "application/json"}'::jsonb,
    body := jsonb_build_object(
      'type', 'INSERT',
      'table', 'captured_photos',
      'photo_id', new.id,
      'image_url', new.image_url
    )
  );
  return new;
end;
$$;

revoke execute on function public.invoke_analyze_job_photo() from public, anon, authenticated;

drop trigger if exists on_captured_photo_inserted on public.captured_photos;

create trigger on_captured_photo_inserted
  after insert on public.captured_photos
  for each row
  execute function public.invoke_analyze_job_photo();
