-- Add pinned photos support and notes to customers and customer locations
ALTER TABLE public.captured_photos
ADD COLUMN IF NOT EXISTS is_pinned boolean DEFAULT false;

ALTER TABLE public.customers
ADD COLUMN IF NOT EXISTS notes text DEFAULT '';

ALTER TABLE public.customer_locations
ADD COLUMN IF NOT EXISTS notes text DEFAULT '';
