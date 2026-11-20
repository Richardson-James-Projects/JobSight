-- JobSight MVP Schema Migration
-- Matches Section 2 of architecture-plan.md

-- 1. Customers table
create table if not exists public.customers (
  id bigint generated always as identity primary key,
  name text not null,
  address text not null,
  latitude double precision not null,
  longitude double precision not null,
  created_at timestamptz not null default now()
);

-- 2. Captured photos table
create table if not exists public.captured_photos (
  id bigint generated always as identity primary key,
  customer_id bigint references public.customers(id) on delete set null,
  image_url text not null,
  captured_at timestamptz not null default now(),
  ai_analysis_json jsonb
);

-- Index foreign key per Postgres best practices
create index if not exists captured_photos_customer_id_idx
  on public.captured_photos (customer_id);

-- 3. Enable Row Level Security
alter table public.customers enable row level security;
alter table public.captured_photos enable row level security;

-- Grant Data API table access to anon and authenticated roles
grant select, insert, update on public.customers to anon, authenticated;
grant usage, select on sequence public.customers_id_seq to anon, authenticated;

grant select, insert, update on public.captured_photos to anon, authenticated;
grant usage, select on sequence public.captured_photos_id_seq to anon, authenticated;

-- RLS policies for MVP field app access (anon + authenticated)
create policy "Allow read customers for field app"
  on public.customers
  for select
  to anon, authenticated
  using (true);

create policy "Allow insert customers for field app"
  on public.customers
  for insert
  to anon, authenticated
  with check (true);

create policy "Allow update customers for field app"
  on public.customers
  for update
  to anon, authenticated
  using (true)
  with check (true);

create policy "Allow read captured_photos for field app"
  on public.captured_photos
  for select
  to anon, authenticated
  using (true);

create policy "Allow insert captured_photos for field app"
  on public.captured_photos
  for insert
  to anon, authenticated
  with check (true);

create policy "Allow update captured_photos for field app"
  on public.captured_photos
  for update
  to anon, authenticated
  using (true)
  with check (true);

-- 4. Create job_site_photos Storage Bucket
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'job_site_photos',
  'job_site_photos',
  true,
  10485760, -- 10MB limit
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Storage RLS Policies for job_site_photos (SELECT + INSERT + UPDATE required for uploads/upserts)
create policy "Allow read job_site_photos"
  on storage.objects
  for select
  to anon, authenticated
  using (bucket_id = 'job_site_photos');

create policy "Allow insert job_site_photos"
  on storage.objects
  for insert
  to anon, authenticated
  with check (bucket_id = 'job_site_photos');

create policy "Allow update job_site_photos"
  on storage.objects
  for update
  to anon, authenticated
  using (bucket_id = 'job_site_photos')
  with check (bucket_id = 'job_site_photos');

-- 5. Enable Supabase Realtime on captured_photos for ai_analysis_json updates
alter publication supabase_realtime add table public.captured_photos;
