import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.49.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface IdentifiedPart {
  part_name: string;
  model_number: string | null;
  search_query: string;
}

function uint8ToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, i + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

function findGeminiApiKey(): string | undefined {
  const direct =
    Deno.env.get("GEMINI_API_KEY") ||
    Deno.env.get("JobSight Gemini API Key") ||
    Deno.env.get("JOBSIGHT_GEMINI_API_KEY") ||
    Deno.env.get("JobSight_Gemini_API_Key");
  if (direct && direct.trim().length > 0) {
    return direct.trim();
  }

  for (const [k, v] of Object.entries(Deno.env.toObject())) {
    if (k.toUpperCase().includes("GEMINI") && v && v.trim().length > 10) {
      return v.trim();
    }
  }
  return undefined;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const payload = await req.json();
    const photoId: number | undefined = payload.photo_id ?? payload.record?.id;
    const imageUrl: string | undefined = payload.image_url ?? payload.record?.image_url;

    if (!photoId || !imageUrl) {
      return new Response(
        JSON.stringify({ error: "Missing photo_id or image_url in request payload" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const geminiApiKey = findGeminiApiKey();

    if (!geminiApiKey) {
      return new Response(
        JSON.stringify({
          error: "Gemini API Key secret is not configured in Supabase Edge Functions yet.",
        }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    // Check if this photo was already analyzed
    const { data: existingRow } = await supabase
      .from("captured_photos")
      .select("id, ai_analysis_json")
      .eq("id", photoId)
      .maybeSingle();

    if (
      existingRow?.ai_analysis_json &&
      Array.isArray(existingRow.ai_analysis_json) &&
      existingRow.ai_analysis_json.length > 0 &&
      !payload.force
    ) {
      return new Response(
        JSON.stringify({ status: "already_analyzed", parts: existingRow.ai_analysis_json }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    // 1. Download the image from Supabase Storage public URL
    const imageResponse = await fetch(imageUrl);
    if (!imageResponse.ok) {
      throw new Error(`Failed to fetch image from ${imageUrl}: ${imageResponse.status}`);
    }
    const contentType = imageResponse.headers.get("content-type") || "image/jpeg";
    const imageBuffer = new Uint8Array(await imageResponse.arrayBuffer());
    const base64Image = uint8ToBase64(imageBuffer);

    // 2. Call Google Gemini Vision API with broad part/equipment/data-plate prompt
    const prompt = `Analyze this photo taken on a job site or field inspection.
Identify all visible equipment, parts, components, tools, appliances, electrical/plumbing/HVAC hardware, products, or data plates/labels.
If a data plate, barcode sticker, or nameplate is visible, extract the manufacturer, model number, or part number.
Return ONLY a JSON array of objects with these exact keys:
- "part_name": string (clear descriptive name of the identified part, equipment, or object)
- "model_number": string or null (extracted model number, part number, or specs if visible; null if not visible)
- "search_query": string (concise search query for finding this exact item or replacement part online)
Always return at least 1 object in the JSON array describing the primary item in the photo.`;

    const candidateModels = [
      "gemini-3.8-flash",
      "gemini-3.5-flash-lite",
      "gemini-flash-latest",
    ];

    let geminiJson: any = null;
    let lastErrorText = "";

    for (const modelName of candidateModels) {
      const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${geminiApiKey}`;
      const geminiResponse = await fetch(geminiUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              parts: [
                { text: prompt },
                {
                  inlineData: {
                    mimeType: contentType,
                    data: base64Image,
                  },
                },
              ],
            },
          ],
          generationConfig: {
            responseMimeType: "application/json",
            temperature: 0.2,
          },
        }),
      });

      if (geminiResponse.ok) {
        geminiJson = await geminiResponse.json();
        break;
      } else {
        lastErrorText = `(${modelName} ${geminiResponse.status}): ${await geminiResponse.text()}`;
      }
    }

    if (!geminiJson) {
      throw new Error(`Gemini API error ${lastErrorText}`);
    }

    const rawText: string =
      geminiJson?.candidates?.[0]?.content?.parts?.[0]?.text ?? "[]";

    let parsedParts: IdentifiedPart[] = [];
    try {
      const decoded = JSON.parse(rawText);
      if (Array.isArray(decoded)) {
        parsedParts = decoded;
      } else if (decoded && Array.isArray(decoded.parts)) {
        parsedParts = decoded.parts;
      } else if (decoded && decoded.part_name) {
        parsedParts = [decoded];
      }
    } catch {
      parsedParts = [];
    }

    if (parsedParts.length === 0) {
      parsedParts = [
        {
          part_name: "Unidentified Field Equipment / Component",
          model_number: null,
          search_query: "commercial field equipment replacement parts",
        },
      ];
    }

    // 3. Update the captured_photos row in Supabase
    const { error: updateError } = await supabase
      .from("captured_photos")
      .update({ ai_analysis_json: parsedParts })
      .eq("id", photoId);

    if (updateError) {
      throw new Error(`Failed to update captured_photos row: ${updateError.message}`);
    }

    return new Response(
      JSON.stringify({ status: "ok", photo_id: photoId, parts: parsedParts }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return new Response(
      JSON.stringify({ error: message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});
