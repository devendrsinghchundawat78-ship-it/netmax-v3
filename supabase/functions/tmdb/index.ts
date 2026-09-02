// NetMax TMDB speed proxy (Supabase Edge Function).
//
// WHY: On many Indian ISPs (Jio/Airtel), api.themoviedb.org is throttled to a
// crawl, which is why users otherwise have to enable a private DNS just to
// use the app. Supabase's edge network is fast from India, so the app calls
// this function as an automatic fallback whenever the direct TMDB route
// fails at the network level (see src/api/client.ts).
//
// Deploy (one time, from the project root):
//   npx supabase login
//   npx supabase link --project-ref xvqxwovekzthaogvgc
//   npx supabase functions deploy tmdb --no-verify-jwt
//   npx supabase secrets set TMDB_API_KEY=<your-server-side-tmdb-key>
//
// Usage (the app does this automatically):
//   GET /functions/v1/tmdb?url=https%3A%2F%2Fapi.themoviedb.org%2F3%2Fmovie%2Fpopular%3F...
//
// SECURITY: this proxy only forwards URLs on https://api.themoviedb.org/3/
// (an open proxy would be abused). If the TMDB_API_KEY secret is set, the
// caller's api_key parameter is replaced with the secret one.

const ALLOWED_PREFIX = 'https://api.themoviedb.org/3/';
const SERVER_KEY = Deno.env.get('TMDB_API_KEY') ?? '';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      // Responses are idempotent GETs — let edge caches help under load.
      'Cache-Control': 'public, max-age=120',
    },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method !== 'GET') {
    return json({ error: 'Method not allowed' }, 405);
  }

  const reqUrl = new URL(req.url);
  const raw = reqUrl.searchParams.get('url');
  if (!raw) {
    return json({ error: 'Missing "url" parameter' }, 400);
  }

  let target: URL;
  try {
    target = new URL(raw);
  } catch {
    return json({ error: 'Invalid "url" parameter' }, 400);
  }

  // Only proxy TMDB API paths — nothing else.
  if (!target.toString().startsWith(ALLOWED_PREFIX)) {
    return json({ error: 'Only api.themoviedb.org URLs are allowed' }, 403);
  }

  // Swap in the server-side key when configured.
  if (SERVER_KEY) target.searchParams.set('api_key', SERVER_KEY);

  try {
    const upstream = await fetch(target.toString(), {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        // Supabase edge functions run close to TMDB's infra — no browser
        // user-agent needed.
        'User-Agent': 'NetMax-TMDB-Proxy/1.0',
      },
    });

    const body = await upstream.text();
    return new Response(body, {
      status: upstream.status,
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'public, max-age=120',
      },
    });
  } catch (e) {
    return json({ error: 'Upstream fetch failed', detail: String(e) }, 502);
  }
});
