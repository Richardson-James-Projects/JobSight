-- Add company_id (linked to Supabase Auth user id) to all tenant tables and enforce strict RLS isolation
alter table public.customers
  add column if not exists company_id uuid references auth.users(id) on delete cascade default auth.uid();

alter table public.customer_locations
  add column if not exists company_id uuid references auth.users(id) on delete cascade default auth.uid();

alter table public.captured_photos
  add column if not exists company_id uuid references auth.users(id) on delete cascade default auth.uid();

alter table public.search_sites
  add column if not exists company_id uuid references auth.users(id) on delete cascade default auth.uid();

create index if not exists customers_company_id_idx on public.customers (company_id);
create index if not exists customer_locations_company_id_idx on public.customer_locations (company_id);
create index if not exists captured_photos_company_id_idx on public.captured_photos (company_id);
create index if not exists search_sites_company_id_idx on public.search_sites (company_id);

alter policy "Allow read customers for field app"
  on public.customers
  to authenticated
  using ((select auth.uid()) = company_id);

alter policy "Allow insert customers for field app"
  on public.customers
  to authenticated
  with check ((select auth.uid()) = company_id);

alter policy "Allow update customers for field app"
  on public.customers
  to authenticated
  using ((select auth.uid()) = company_id)
  with check ((select auth.uid()) = company_id);

alter policy "Allow delete customers for field app"
  on public.customers
  to authenticated
  using ((select auth.uid()) = company_id);

alter policy "Allow read customer_locations for field app"
  on public.customer_locations
  to authenticated
  using ((select auth.uid()) = company_id);

alter policy "Allow insert customer_locations for field app"
  on public.customer_locations
  to authenticated
  with check ((select auth.uid()) = company_id);

alter policy "Allow update customer_locations for field app"
  on public.customer_locations
  to authenticated
  using ((select auth.uid()) = company_id)
  with check ((select auth.uid()) = company_id);

alter policy "Allow delete customer_locations for field app"
  on public.customer_locations
  to authenticated
  using ((select auth.uid()) = company_id);

alter policy "Allow read captured_photos for field app"
  on public.captured_photos
  to authenticated
  using ((select auth.uid()) = company_id);

alter policy "Allow insert captured_photos for field app"
  on public.captured_photos
  to authenticated
  with check ((select auth.uid()) = company_id);

alter policy "Allow update captured_photos for field app"
  on public.captured_photos
  to authenticated
  using ((select auth.uid()) = company_id)
  with check ((select auth.uid()) = company_id);

alter policy "Allow delete captured_photos for field app"
  on public.captured_photos
  to authenticated
  using ((select auth.uid()) = company_id);

alter policy "Allow read search_sites for field app"
  on public.search_sites
  to authenticated
  using ((select auth.uid()) = company_id);

alter policy "Allow insert search_sites for field app"
  on public.search_sites
  to authenticated
  with check ((select auth.uid()) = company_id);

alter policy "Allow update search_sites for field app"
  on public.search_sites
  to authenticated
  using ((select auth.uid()) = company_id)
  with check ((select auth.uid()) = company_id);

alter policy "Allow delete search_sites for field app"
  on public.search_sites
  to authenticated
  using ((select auth.uid()) = company_id);
