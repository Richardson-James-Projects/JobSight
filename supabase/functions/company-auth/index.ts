import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.49.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function companyNameToSyntheticEmail(companyName: string): string {
  const normalized = companyName
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ".")
    .replace(/^\.+|\.+$/g, "");
  let hash = 2166136261;
  const cleanLower = companyName.trim().toLowerCase();
  for (let i = 0; i < cleanLower.length; i++) {
    hash ^= cleanLower.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  const hex = (hash >>> 0).toString(16);
  const slug = normalized.length > 0 ? normalized.slice(0, 32) : "company";
  return `${slug}.${hex}@company.jobsight.app`;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body = await req.json();
    const action: string = body.action || "login"; // "register" | "login"
    const companyName: string = (body.company_name || "").trim();
    const password: string = body.password || "";

    if (!companyName || companyName.length < 2) {
      return new Response(
        JSON.stringify({ error: "Please enter a Company Name (at least 2 characters)." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    if (!password || password.length < 6) {
      return new Response(
        JSON.stringify({ error: "Password must be at least 6 characters." }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseServiceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY")!;

    const adminClient = createClient(supabaseUrl, supabaseServiceKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const syntheticEmail = companyNameToSyntheticEmail(companyName);

    if (action === "register") {
      const { data: created, error: createError } = await adminClient.auth.admin.createUser({
        email: syntheticEmail,
        password: password,
        email_confirm: true,
        user_metadata: {
          company_name: companyName,
        },
      });

      if (createError) {
        const msg = createError.message.toLowerCase();
        if (msg.includes("already") || msg.includes("exists") || msg.includes("registered")) {
          return new Response(
            JSON.stringify({
              error: `Company '${companyName}' is already registered. Please switch to 'Sign In' instead.`,
            }),
            { status: 409, headers: { ...corsHeaders, "Content-Type": "application/json" } },
          );
        }
        return new Response(
          JSON.stringify({ error: createError.message }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
        );
      }

      const newCompanyId = created.user?.id;
      if (newCompanyId) {
        await adminClient.from("search_sites").insert([
          {
            company_id: newCompanyId,
            name: "Google Search",
            url_template: "https://www.google.com/search?q={query}",
          },
        ]);
      }
    }

    const publicClient = createClient(supabaseUrl, supabaseAnonKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const { data: signInData, error: signInError } = await publicClient.auth.signInWithPassword({
      email: syntheticEmail,
      password: password,
    });

    if (signInError || !signInData.session || !signInData.user) {
      return new Response(
        JSON.stringify({
          error: "Invalid Company Name or Password. If your company is new, tap 'Register Company'.",
        }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const storedCompanyName =
      (signInData.user.user_metadata?.company_name as string) || companyName;

    return new Response(
      JSON.stringify({
        company_id: signInData.user.id,
        company_name: storedCompanyName,
        access_token: signInData.session.access_token,
        refresh_token: signInData.session.refresh_token,
      }),
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
