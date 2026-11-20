-- Migration: Configurable online search sites for HVAC part sourcing
create table if not exists public.search_sites (
  id bigint generated always as identity primary key,
  name text not null,
  url_template text not null,
  created_at timestamptz not null default now()
);

alter table public.search_sites enable row level security;

grant select, insert, update, delete on public.search_sites to anon, authenticated;
grant usage, select on sequence public.search_sites_id_seq to anon, authenticated;

create policy "Allow read search_sites for field app"
  on public.search_sites
  for select
  to anon, authenticated
  using (true);

create policy "Allow insert search_sites for field app"
  on public.search_sites
  for insert
  to anon, authenticated
  with check (true);

create policy "Allow update search_sites for field app"
  on public.search_sites
  for update
  to anon, authenticated
  using (true)
  with check (true);

create policy "Allow delete search_sites for field app"
  on public.search_sites
  for delete
  to anon, authenticated
  using (true);
