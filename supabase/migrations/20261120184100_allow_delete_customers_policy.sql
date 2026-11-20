-- Allow deleting customers from the field app (cascades to customer_locations)
grant delete on public.customers to anon, authenticated;

create policy "Allow delete customers for field app"
  on public.customers
  for delete
  to anon, authenticated
  using (true);
