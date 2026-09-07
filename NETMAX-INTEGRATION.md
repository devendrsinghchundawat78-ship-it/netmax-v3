# NetMax integration

This Nuvio-based build is branded as NetMax and keeps the existing Nuvio playback/catalog engine while adding the NetMax service layer.

Included:
- NetMax branding/logo and app name
- Existing automatic NetMax provider update/download/run pipeline
- Dedicated NetMax Supabase client
- NetMax email credential bridge (primary login remains the existing auth flow)
- NetMax AI chat screen with history and confirmed movie/bug/feature actions; usable without an
  account (a sign-in only adds cross-device history), no per-day request cap
- NetMax AI Supabase migration + Edge Function source
- NetMax core/gate migration sources
- Existing Light mode and Liquid Glass navigation UI

Deploy the included `supabase/migrations` and `supabase/functions/netmax-ai` to the NetMax Supabase project. Set the `OPENROUTER_API_KEY` secret for the `netmax-ai` Edge Function.

For AI access without login (the app also works without it, just without stored history):
- `supabase functions secrets set SUPABASE_SERVICE_ROLE_KEY=...` lets guest chats/requests persist,
- dashboard → Authentication → Providers → **Anonymous sign-ins: ON** gives guests a real session.
