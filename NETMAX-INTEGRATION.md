# NetMax integration

This Nuvio-based build is branded as NetMax and keeps the existing Nuvio playback/catalog engine while adding the NetMax service layer.

Included:
- NetMax branding/logo and app name
- Existing automatic NetMax provider update/download/run pipeline
- Dedicated NetMax Supabase client
- NetMax email credential bridge (primary login remains the existing auth flow)
- NetMax AI chat screen with history, daily quota, and confirmed movie/bug/feature actions
- NetMax AI Supabase migration + Edge Function source
- NetMax core/gate migration sources
- Existing Light mode and Liquid Glass navigation UI

Deploy the included `supabase/migrations` and `supabase/functions/netmax-ai` to the NetMax Supabase project. Set the `OPENROUTER_API_KEY` secret for the `netmax-ai` Edge Function.
