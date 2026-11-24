/**
 * Minimal OpenID Connect client for Keycloak (Phase 3.0): Authorization Code flow with PKCE, a public client
 * (no secret in the browser) and no extra dependency.
 *
 * 1. login() sends the browser to Keycloak with a one-time `state` and a PKCE code challenge.
 * 2. Keycloak redirects back to /auth/callback with a code; completeLogin() exchanges it for tokens.
 * 3. getAccessToken() returns a valid access token for API calls and refreshes it shortly before it expires.
 *
 * Tokens live in sessionStorage: per tab and gone when the tab closes. That keeps the tutorial small, but any
 * script running on the page (XSS) could read them. A production app would rather keep tokens on a server
 * (a backend-for-frontend with HttpOnly cookies).
 */

const KEYCLOAK_URL = (process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? "http://localhost:8180").replace(/\/$/, "");
const REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? "taskflow";
const CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "taskflow-web";

const ISSUER = `${KEYCLOAK_URL}/realms/${REALM}`;
const AUTHORIZATION_ENDPOINT = `${ISSUER}/protocol/openid-connect/auth`;
const TOKEN_ENDPOINT = `${ISSUER}/protocol/openid-connect/token`;
const LOGOUT_ENDPOINT = `${ISSUER}/protocol/openid-connect/logout`;

export const CALLBACK_PATH = "/auth/callback";

const SESSION_KEY = "taskflow.auth.session";
const PENDING_KEY = "taskflow.auth.pending";
/** Refresh this long before expiry, so a request never leaves with a token that is about to expire. */
const EXPIRY_MARGIN_MS = 30_000;

interface TokenSet {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  /** Epoch milliseconds. */
  expiresAt: number;
}

interface PendingLogin {
  state: string;
  verifier: string;
  returnTo: string;
}

export interface AuthUser {
  subject: string;
  username: string;
  name?: string;
}

/* ------------------------------------------------------------------ */
/* Storage and encoding helpers                                        */
/* ------------------------------------------------------------------ */

function readJson<T>(key: string): T | null {
  try {
    const raw = window.sessionStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function writeJson(key: string, value: unknown) {
  window.sessionStorage.setItem(key, JSON.stringify(value));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function randomString(byteLength: number): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function pkceChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

/** Reads the claims of a JWT for display only. The backend validates the signature; the browser never trusts it. */
export function decodeClaims(jwt: string): Record<string, unknown> {
  const payload = (jwt.split(".")[1] ?? "").replace(/-/g, "+").replace(/_/g, "/");
  const padded = payload.padEnd(Math.ceil(payload.length / 4) * 4, "=");
  const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
}

function redirectUri(): string {
  return `${window.location.origin}${CALLBACK_PATH}`;
}

export function currentPath(): string {
  return `${window.location.pathname}${window.location.search}`;
}

export function isCallback(): boolean {
  return window.location.pathname === CALLBACK_PATH;
}

/* ------------------------------------------------------------------ */
/* Flow                                                                */
/* ------------------------------------------------------------------ */

// React runs effects twice in development (StrictMode). These guards make both steps idempotent:
// a second exchange of the same code would fail, and a second redirect would overwrite the pending state.
let loginStarted = false;
let callbackResult: Promise<string> | null = null;
let refreshing: Promise<string | null> | null = null;

/** Sends the browser to the Keycloak sign-in page; it comes back to {@link CALLBACK_PATH}. */
export async function login(returnTo: string = currentPath()): Promise<void> {
  if (loginStarted) return;
  loginStarted = true;
  try {
    const verifier = randomString(48);
    const state = randomString(16);
    const safeReturnTo = returnTo.startsWith(CALLBACK_PATH) ? "/" : returnTo;
    writeJson(PENDING_KEY, { state, verifier, returnTo: safeReturnTo } satisfies PendingLogin);
    const params = new URLSearchParams({
      client_id: CLIENT_ID,
      response_type: "code",
      scope: "openid profile",
      redirect_uri: redirectUri(),
      state,
      // crypto.subtle exists only in secure contexts: https, or http://localhost during development.
      code_challenge: await pkceChallenge(verifier),
      code_challenge_method: "S256",
    });
    window.location.assign(`${AUTHORIZATION_ENDPOINT}?${params.toString()}`);
  } catch (error) {
    loginStarted = false;
    throw error;
  }
}

/** Exchanges the authorization code from the callback URL for tokens. Resolves to the page to return to. */
export function completeLogin(): Promise<string> {
  callbackResult ??= exchangeCode();
  return callbackResult;
}

async function exchangeCode(): Promise<string> {
  const url = new URL(window.location.href);
  const pending = readJson<PendingLogin>(PENDING_KEY);
  window.sessionStorage.removeItem(PENDING_KEY);

  const error = url.searchParams.get("error");
  if (error) throw new Error(url.searchParams.get("error_description") ?? error);
  const code = url.searchParams.get("code");
  // `state` must be the value this tab generated: it ties the response to this sign-in attempt (CSRF protection).
  if (!pending || !code || url.searchParams.get("state") !== pending.state) {
    throw new Error("The sign-in response was invalid or expired. Please sign in again.");
  }
  const tokens = await tokenRequest({
    grant_type: "authorization_code",
    code,
    redirect_uri: redirectUri(),
    code_verifier: pending.verifier,
  });
  writeJson(SESSION_KEY, tokens);
  return pending.returnTo;
}

async function tokenRequest(form: Record<string, string>): Promise<TokenSet> {
  const response = await fetch(TOKEN_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: CLIENT_ID, ...form }),
  });
  if (!response.ok) throw new Error(`Sign-in failed: the token request returned ${response.status}.`);
  const body = (await response.json()) as { access_token: string; refresh_token?: string; id_token?: string; expires_in: number };
  return {
    accessToken: body.access_token,
    refreshToken: body.refresh_token,
    idToken: body.id_token,
    expiresAt: Date.now() + body.expires_in * 1000,
  };
}

/** A valid access token, refreshed if needed, or null when the user has to sign in again. */
export async function getAccessToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  const session = readJson<TokenSet>(SESSION_KEY);
  if (!session) return null;
  if (session.expiresAt - EXPIRY_MARGIN_MS > Date.now()) return session.accessToken;
  if (!session.refreshToken) {
    clearSession();
    return null;
  }
  // One refresh for all concurrent callers; refresh tokens may be single-use.
  refreshing ??= tokenRequest({ grant_type: "refresh_token", refresh_token: session.refreshToken })
    .then((tokens) => {
      writeJson(SESSION_KEY, { ...tokens, idToken: tokens.idToken ?? session.idToken });
      return tokens.accessToken;
    })
    .catch(() => {
      clearSession();
      return null;
    })
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

/** The signed-in user for display (name in the top bar). Authorization always happens on the backend. */
export function signedInUser(): AuthUser | null {
  const session = readJson<TokenSet>(SESSION_KEY);
  const token = session?.idToken ?? session?.accessToken;
  if (!token) return null;
  try {
    const claims = decodeClaims(token);
    return {
      subject: String(claims.sub ?? ""),
      username: String(claims.preferred_username ?? claims.sub ?? "user"),
      name: typeof claims.name === "string" ? claims.name : undefined,
    };
  } catch {
    return null;
  }
}

function clearSession() {
  window.sessionStorage.removeItem(SESSION_KEY);
}

/**
 * Signs out of TaskFlow and of the Keycloak session. Everything this tab kept (tokens, the assistant
 * transcript and its conversation id) is cleared, so the next user of the tab starts clean.
 */
export function logout(): void {
  const session = readJson<TokenSet>(SESSION_KEY);
  window.sessionStorage.clear();
  const params = new URLSearchParams({ client_id: CLIENT_ID, post_logout_redirect_uri: `${window.location.origin}/` });
  if (session?.idToken) params.set("id_token_hint", session.idToken);
  window.location.assign(`${LOGOUT_ENDPOINT}?${params.toString()}`);
}
