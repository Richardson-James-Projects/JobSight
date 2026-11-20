-- Migration: Add customer_locations and photo gallery support
alter table public.customers
  alter column address set default '',
  alter column latitude set default 0.0,
  alter column longitude set default 0.0;

create table if not exists public.customer_locations (
  id bigint generated always as identity primary key,
  customer_id bigint not null references public.customers(id) on delete cascade,
  label text not null default 'Job Site',
  address text not null,
  latitude double precision not null,
  longitude double precision not null,
  created_at timestamptz not null default now()
);

create index if not exists customer_locations_customer_id_idx
  on public.customer_locations (customer_id);

alter table public.captured_photos
  add column if not exists customer_location_id bigint references public.customer_locations(id) on delete set null;

create index if not exists captured_photos_customer_location_id_idx
  on public.captured_photos (customer_location_id);

alter table public.customer_locations enable row level security;

grant select, insert, update, delete on public.customer_locations to anon, authenticated;
grant usage, select on sequence public.customer_locations_id_seq to anon, authenticated;

create policy "Allow read customer_locations for field app"
  on public.customer_locations
  for select
  to anon, authenticated
  using (true);

create policy "Allow insert customer_locations for field app"
  on public.customer_locations
  for insert
  to anon, authenticated
  with check (true);

create policy "Allow update customer_locations for field app"
  on public.customer_locations
  for update
  to anon, authenticated
  using (true)
  with check (true);

create policy "Allow delete customer_locations for field app"
  on public.customer_locations
  for delete
  to anon, authenticated
  using (true);
