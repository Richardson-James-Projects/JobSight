# App MVP Architecture: Field Photo, Auto-Tagging & AI Part ID

## 1. The Core Wedge (MVP Goal)
A zero-click native Android camera app that captures photos on job sites, automatically tags the correct customer based on GPS location (with support for manual customer override), lets technicians manage customers with multiple job site locations (by street address or current GPS coordinates), allows viewing all photos taken per customer, and pushes images directly to a secure internal cloud. Once uploaded, an AI Vision model analyzes the photo to identify HVAC parts and equipment, providing the techs with component names and sourcing links.

## 2. Infrastructure (Supabase)
The backend relies entirely on Supabase for Auth, PostgreSQL, Storage, and Edge Functions.
*   **Table:** `customers` (id, name, address, latitude, longitude, created_at)
*   **Table:** `customer_locations` (id, customer_id, label, address, latitude, longitude, created_at) — supports multiple locations per customer added via street address geocoding or current GPS coordinates.
*   **Table:** `captured_photos` (id, customer_id, customer_location_id, image_url, captured_at, ai_analysis_json)
*   **Storage:** `job_site_photos` bucket

## 3. Native Android Architecture (Kotlin)
*   **Camera:** Use Android `CameraX` with `PreviewView` for a full-screen, distortion-free viewfinder.
*   **Location:** Use `FusedLocationProviderClient` on app launch to grab device coordinates.
*   **Geofence & Manual Selection Logic:** Calculate `Location.distanceTo()` against all `customer_locations`. If distance is < 100 meters, snap the UI overlay to "Auto-Detected: [Customer Name] ([Location Label])". Allow the technician to manually select a different customer at any time or switch back to GPS auto-detect.
*   **Customer & Multi-Location Management:** Allow adding new customers and adding multiple locations per customer either by **Current GPS Location** or by **Street Address** (geocoded to latitude/longitude).
*   **Customer Photo Gallery:** Allow viewing all photos captured for any customer, including the full image, timestamp, location tag, and AI-identified HVAC components.
*   **Action:** On shutter press, capture the image to cache, upload it to the Supabase storage bucket, and insert a database row linking the photo URL to the active customer ID (auto-detected or manually selected). Flush the local cache immediately.
*   **UI Update:** Listen for database changes. When the `ai_analysis_json` column populates, display a bottom sheet card showing the identified HVAC parts and buttons to search for them online.

## 4. AI Vision Integration (Edge Function)
Instead of syncing to an external CRM, the app uses AI to generate immediate value for the technician in the field.
*   **Trigger:** A Supabase Webhook or Edge Function fires on `INSERT` to the `captured_photos` table.
*   **Action:** The function calls a Multimodal Vision API (e.g., Gemini 1.5 Flash or OpenAI GPT-4o) passing the secure image URL.
*   **Prompt:** "Analyze this image from an HVAC job site. Identify any visible HVAC equipment, components, or data plates. Extract model numbers if visible. Return a JSON array containing 'part_name', 'model_number', and 'search_query' for finding replacement parts."
*   **Resolution:** The Edge Function parses the AI response and runs an `UPDATE` on the `captured_photos` row, inserting the structured JSON into the `ai_analysis_json` column.
