// =====================================================================
// NETMAX AI ASSISTANT — Supabase Edge Function
//
//   NetMax App → Supabase Auth session → THIS FUNCTION → OpenRouter → reply
//
// Security model:
//   • The OpenRouter API key lives ONLY in this function's secrets
//     (OPENROUTER_API_KEY) — it is never sent to, or stored by, the app.
//   • The user identity is taken from the verified Supabase JWT — the
//     client-sent user_id is never trusted.
//   • 10 requests/user/day is enforced by the ATOMIC database function
//     increment_ai_usage() — concurrent requests cannot bypass it.
//
// Deploy:
//   npx supabase functions deploy netmax-ai
//   (JWT verification stays ON at the gateway — the app always sends a
//    valid user token; this function re-verifies it anyway.)
//
// Protocol (POST, JSON body):
//   { action: "chat", message, conversationId?, imageBase64?, imageMimeType?,
//     clientContext?, device? }
//   { action: "submit", type, payload, conversationId? }
//   { action: "history" }
// =====================================================================

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import {
  DAILY_LIMIT,
  MAX_MESSAGE_CHARS,
  MAX_IMAGE_BASE64_BYTES,
  buildSystemPrompt,
  parseModelResponse,
  trimHistory,
  validateSubmitPayload,
  type AiClientContext,
  type ChatMessageForModel,
} from './protocol.ts';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? '';
const SUPABASE_ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY') ?? '';
const OPENROUTER_KEY = Deno.env.get('OPENROUTER_API_KEY') ?? '';

const TEXT_MODEL = Deno.env.get('OPENROUTER_MODEL') ?? 'openai/gpt-4o-mini';
const VISION_MODEL = Deno.env.get('OPENROUTER_VISION_MODEL') ?? 'google/gemini-2.0-flash-001';

const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';
const AI_TIMEOUT_MS = 60_000;
const HISTORY_MESSAGES = 20;

/* ------------------------------------------------------------------ *
 * Helpers
 * ------------------------------------------------------------------ */

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function err(status: number, code: string, message: string): Response {
  return json({ error: code, message }, status);
}

interface AuthedContext {
  userId: string;
  supabase: ReturnType<typeof createClient>;
}

/** Verify the caller's JWT and build an RLS-scoped client. */
async function authenticate(req: Request): Promise<AuthedContext | null> {
  const authHeader = req.headers.get('Authorization');
  if (!authHeader?.startsWith('Bearer ')) return null;
  const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await supabase.auth.getUser();
  if (error || !data?.user?.id) return null;
  return { userId: data.user.id, supabase };
}

function usageOf(count: number): { used: number; limit: number; remaining: number } {
  return { used: count, limit: DAILY_LIMIT, remaining: Math.max(0, DAILY_LIMIT - count) };
}

/* ------------------------------------------------------------------ *
 * OpenRouter
 * ------------------------------------------------------------------ */

async function callOpenRouter(
  messages: (ChatMessageForModel & { content: unknown })[],
  model: string
): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), AI_TIMEOUT_MS);
  try {
    const res = await fetch(OPENROUTER_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${OPENROUTER_KEY}`,
        'Content-Type': 'application/json',
        'X-Title': 'NetMax',
      },
      body: JSON.stringify({
        model,
        messages,
        max_tokens: 1200,
        temperature: 0.7,
      }),
      signal: controller.signal,
    });

    if (!res.ok) {
      const detail = await res.text().catch(() => '');
      throw new Error(`OpenRouter HTTP ${res.status} ${detail.slice(0, 300)}`);
    }
    const data = (await res.json()) as {
      choices?: { message?: { content?: string } }[];
    };
    const content = data?.choices?.[0]?.message?.content;
    if (typeof content !== 'string' || !content.trim()) {
      throw new Error('Empty response from model');
    }
    return content;
  } finally {
    clearTimeout(timer);
  }
}

/* ------------------------------------------------------------------ *
 * Conversation persistence
 * ------------------------------------------------------------------ */

interface DbMessage {
  role: string;
  content: string;
}

async function loadOrCreateConversation(
  sb: AuthedContext['supabase'],
  userId: string,
  conversationId: string | null,
  firstMessage: string,
  forceNew = false
): Promise<string> {
  if (conversationId) {
    // Verify ownership (RLS would too, but fail fast with a clean error).
    const { data } = await sb
      .from('ai_conversations')
      .select('id')
      .eq('id', conversationId)
      .eq('user_id', userId)
      .maybeSingle();
    if (data?.id) return data.id as string;
  }
  if (!forceNew) {
    // Continue the latest conversation when the client didn't pin one.
    const { data: latest } = await sb
      .from('ai_conversations')
      .select('id')
      .eq('user_id', userId)
      .order('last_message_at', { ascending: false })
      .limit(1)
      .maybeSingle();
    if (latest?.id) return latest.id as string;
  }

  const { data: created, error } = await sb
    .from('ai_conversations')
    .insert({
      user_id: userId,
      title: firstMessage.slice(0, 60),
    })
    .select('id')
    .single();
  if (error || !created?.id) throw new Error('Could not create conversation');
  return created.id as string;
}

async function saveMessage(
  sb: AuthedContext['supabase'],
  userId: string,
  conversationId: string,
  role: 'user' | 'assistant',
  content: string,
  imageMeta?: Record<string, unknown> | null
): Promise<void> {
  await sb.from('ai_messages').insert({
    conversation_id: conversationId,
    user_id: userId,
    role,
    content,
    image_meta: imageMeta ?? null,
  });
  await sb
    .from('ai_conversations')
    .update({ last_message_at: new Date().toISOString() })
    .eq('id', conversationId);
}

/** Insert a confirmed action row. Returns a user-facing confirmation. */
async function insertAction(
  sb: AuthedContext['supabase'],
  userId: string,
  type: 'movie_request' | 'bug_report' | 'feature_request',
  payload: Record<string, unknown>,
  device: AiClientContext['device']
): Promise<string> {
  if (type === 'movie_request') {
    await sb.from('movie_requests').insert({ user_id: userId, ...payload });
    return '✅ Movie request submit kar di gayi hai. Admin review karega — turant add hone ka guarantee nahi hai.';
  }
  if (type === 'bug_report') {
    await sb.from('bug_reports').insert({
      user_id: userId,
      ...payload,
      app_version: device?.appVersion ?? null,
      platform: device?.platform ?? null,
      device_info: device?.model ? { model: device.model } : null,
    });
    return '✅ Bug report submit kar di gayi hai. Team jaldi dekhegi.';
  }
  await sb.from('feature_requests').insert({ user_id: userId, ...payload });
  return '✅ Feature request submit kar di gayi hai. Thanks for the suggestion!';
}

/* ------------------------------------------------------------------ *
 * Action: chat
 * ------------------------------------------------------------------ */

async function handleChat(sb: AuthedContext['supabase'], userId: string, body: Record<string, unknown>): Promise<Response> {
  const message = typeof body.message === 'string' ? body.message.trim().slice(0, MAX_MESSAGE_CHARS) : '';
  const imageBase64 = typeof body.imageBase64 === 'string' ? body.imageBase64 : null;
  const imageMimeType = typeof body.imageMimeType === 'string' ? body.imageMimeType : 'image/jpeg';
  const conversationIdRaw = typeof body.conversationId === 'string' ? body.conversationId : null;
  const newChat = body.newChat === true;
  const clientContext = (body.clientContext ?? {}) as AiClientContext;
  const device = (body.device ?? null) as AiClientContext['device'];

  if (!message && !imageBase64) return err(400, 'INVALID_REQUEST', 'Message khaali hai.');
  if (imageBase64 && imageBase64.length > MAX_IMAGE_BASE64_BYTES) {
    return err(413, 'PAYLOAD_TOO_LARGE', 'Image bahut badi hai. Chhoti image try karein.');
  }

  // ── 1. Atomic daily-limit check (10/day, server clock) ──
  const { data: count, error: rpcError } = await sb.rpc('increment_ai_usage', { p_user: userId });
  if (rpcError) return err(500, 'DB_ERROR', 'Usage tracking temporarily unavailable.');
  const used = Number(count);
  if (used < 0) {
    return err(
      429,
      'DAILY_LIMIT_REACHED',
      'Aap aaj ke 10 AI requests use kar chuke hain. Kal dobara try karein.'
    );
  }

  // ── 2. Conversation + history ──
  let conversationId: string;
  let history: DbMessage[] = [];
  try {
    conversationId = await loadOrCreateConversation(
      sb,
      userId,
      conversationIdRaw,
      message || 'Image question',
      newChat
    );
    // Newest N messages (desc + reverse — a plain ascending limit would
    // return the OLDEST rows instead).
    const { data: msgs } = await sb
      .from('ai_messages')
      .select('role, content')
      .eq('conversation_id', conversationId)
      .order('created_at', { ascending: false })
      .limit(HISTORY_MESSAGES);
    history = ((msgs ?? []) as DbMessage[]).reverse();
  } catch {
    await sb.rpc('refund_ai_usage', { p_user: userId });
    return err(500, 'DB_ERROR', 'Conversation save nahi ho paya.');
  }

  // ── 3. Save the user's message ──
  await saveMessage(
    sb,
    userId,
    conversationId,
    'user',
    message || '(image attached)',
    imageBase64 ? { mime: imageMimeType, bytes: imageBase64.length } : null
  );

  // ── 4. Ask OpenRouter ──
  const system = buildSystemPrompt(clientContext);
  const modelMessages: { role: string; content: unknown }[] = [
    { role: 'system', content: system },
    ...trimHistory(
      history.map((m) => ({
        role: m.role === 'assistant' ? ('assistant' as const) : ('user' as const),
        content: m.content,
      }))
    ).map((m) => ({ role: m.role, content: m.content })),
  ];

  if (imageBase64) {
    modelMessages.push({
      role: 'user',
      content: [
        { type: 'text', text: message || 'Is image ke baare me batao — ye kaunsi movie hai?' },
        { type: 'image_url', image_url: { url: `data:${imageMimeType};base64,${imageBase64}` } },
      ],
    });
  } else {
    modelMessages.push({ role: 'user', content: message });
  }

  let raw: string;
  try {
    raw = await callOpenRouter(modelMessages as never, imageBase64 ? VISION_MODEL : TEXT_MODEL);
  } catch (e) {
    // AI failed → refund the request so the user doesn't lose quota.
    await sb.rpc('refund_ai_usage', { p_user: userId });
    const msg = String(e);
    if (/abort/i.test(msg)) {
      return err(504, 'AI_TIMEOUT', 'AI jawab dene me bahut time le raha hai. Thodi der baad try karein.');
    }
    return err(502, 'AI_ERROR', 'AI service abhi available nahi hai. Thodi der baad try karein.');
  }

  // ── 5. Parse + act ──
  const parsed = parseModelResponse(raw);
  let reply = parsed.reply;
  let pendingAction: Record<string, unknown> | null = null;

  if (parsed.action) {
    const validated = validateSubmitPayload(parsed.action);
    if (validated.ok && parsed.action.confirmed) {
      // Clear command → submit immediately.
      try {
        const confirmation = await insertAction(sb, userId, validated.type, validated.payload, device);
        reply = `${reply}\n\n${confirmation}`;
      } catch {
        reply = `${reply}\n\n⚠️ Request save nahi ho payi — thodi der baad dobara try karein.`;
      }
    } else if (validated.ok) {
      // Ambiguous → ask the user to confirm (client shows Submit/Cancel).
      pendingAction = { ...validated.payload, type: validated.type };
      // Re-add the fields the client needs to render the confirm card.
      if (validated.type === 'movie_request' && parsed.action.movieName) {
        pendingAction.movieName = parsed.action.movieName;
        pendingAction.title = parsed.action.movieName;
      }
      if (parsed.action.year) pendingAction.year = parsed.action.year;
      if (parsed.action.tmdbId) pendingAction.tmdbId = parsed.action.tmdbId;
      if (validated.type === 'bug_report' && parsed.action.category) {
        pendingAction.category = parsed.action.category;
      }
      pendingAction.description = parsed.action.description || parsed.action.title;
    }
  }

  await saveMessage(sb, userId, conversationId, 'assistant', reply);

  return json({
    ok: true,
    conversationId,
    reply,
    pendingAction,
    usage: usageOf(used),
  });
}

/* ------------------------------------------------------------------ *
 * Action: submit (user confirmed a pending action — does NOT consume
 * the daily AI quota; no model call happens here)
 * ------------------------------------------------------------------ */

async function handleSubmit(sb: AuthedContext['supabase'], userId: string, body: Record<string, unknown>): Promise<Response> {
  const validated = validateSubmitPayload(body);
  if (!validated.ok) return err(400, 'INVALID_REQUEST', 'Request ka format galat hai.');

  const conversationIdRaw = typeof body.conversationId === 'string' ? body.conversationId : null;
  let conversationId: string | null = conversationIdRaw;
  if (conversationIdRaw) {
    const { data } = await sb
      .from('ai_conversations')
      .select('id')
      .eq('id', conversationIdRaw)
      .eq('user_id', userId)
      .maybeSingle();
    if (!data?.id) conversationId = null;
  }

  try {
    const device = (body.device ?? null) as AiClientContext['device'];
    const confirmation = await insertAction(sb, userId, validated.type, validated.payload, device);
    if (conversationId) {
      await saveMessage(sb, userId, conversationId, 'assistant', confirmation);
    }
    return json({ ok: true, conversationId, message: confirmation });
  } catch {
    return err(500, 'DB_ERROR', 'Request save nahi ho payi. Thodi der baad try karein.');
  }
}

/* ------------------------------------------------------------------ *
 * Action: history (latest conversation + today's usage)
 * ------------------------------------------------------------------ */

async function handleHistory(sb: AuthedContext['supabase'], userId: string): Promise<Response> {
  const { data: conv } = await sb
    .from('ai_conversations')
    .select('id')
    .eq('user_id', userId)
    .order('last_message_at', { ascending: false })
    .limit(1)
    .maybeSingle();

  let messages: { role: string; content: string; created_at: string; image_meta: unknown }[] = [];
  if (conv?.id) {
    const { data: msgs } = await sb
      .from('ai_messages')
      .select('role, content, created_at, image_meta')
      .eq('conversation_id', conv.id)
      .order('created_at', { ascending: false })
      .limit(100);
    messages = (msgs ?? []).reverse();
  }

  const { data: usageRow } = await sb
    .from('ai_usage_daily')
    .select('request_count')
    .eq('user_id', userId)
    .eq('usage_date', new Date().toISOString().slice(0, 10))
    .maybeSingle();

  return json({
    ok: true,
    conversationId: conv?.id ?? null,
    messages,
    usage: usageOf(Number(usageRow?.request_count ?? 0)),
  });
}

/* ------------------------------------------------------------------ *
 * Router
 * ------------------------------------------------------------------ */

Deno.serve(async (req: Request) => {
  if (req.method !== 'POST') {
    return err(405, 'METHOD_NOT_ALLOWED', 'POST only.');
  }

  const authed = await authenticate(req);
  if (!authed) {
    return err(401, 'UNAUTHORIZED', 'AI use karne ke liye login karein.');
  }
  if (!OPENROUTER_KEY) {
    return err(503, 'AI_NOT_CONFIGURED', 'AI abhi configured nahi hai.');
  }

  let body: Record<string, unknown>;
  try {
    body = (await req.json()) as Record<string, unknown>;
  } catch {
    return err(400, 'INVALID_REQUEST', 'Body JSON nahi hai.');
  }

  const action = body.action;
  try {
    if (action === 'chat') return await handleChat(authed.supabase, authed.userId, body);
    if (action === 'submit') return await handleSubmit(authed.supabase, authed.userId, body);
    if (action === 'history') return await handleHistory(authed.supabase, authed.userId);
    return err(400, 'INVALID_REQUEST', 'Unknown action.');
  } catch (e) {
    console.error('netmax-ai error:', e);
    return err(500, 'INTERNAL', 'Kuch galat ho gaya. Dobara try karein.');
  }
});
