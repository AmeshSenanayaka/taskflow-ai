import type { Metadata } from "next";

export const metadata: Metadata = { title: "Signing in" };

/** Keycloak redirects here after sign-in. The AuthGate in the root layout exchanges the code and moves on. */
export default function AuthCallbackPage() {
  return <p className="text-sm text-muted-foreground">Signing you in…</p>;
}
