-- Add notes column to captured_photos so technicians can attach field captions/notes to photos
ALTER TABLE public.captured_photos
ADD COLUMN IF NOT EXISTS notes text DEFAULT '';
