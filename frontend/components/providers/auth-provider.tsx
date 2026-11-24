"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { completeLogin, getAccessToken, isCallback, login, logout, signedInUser, type AuthUser } from "@/lib/auth/oidc";
import { Button } from "@/components/ui/button";

interface AuthContextValue {
  user: AuthUser;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** The signed-in user, or null outside the gate (for example in component tests). */
export function useOptionalAuth(): AuthContextValue | null {
  return useContext(AuthContext);
}

type GateState = { status: "loading" } | { status: "signed-in"; user: AuthUser } | { status: "error"; message: string };

/**
 * Phase 3.0: nothing in the app renders, and no API call is made, until the user is signed in with Keycloak.
 * Without a session the gate starts the sign-in redirect; on /auth/callback it completes it and returns to
 * the page the user originally opened.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<GateState>({ status: "loading" });

  useEffect(() => {
    let active = true;
    async function run() {
      try {
        if (isCallback()) {
          const returnTo = await completeLogin();
          // A full navigation: the router never rendered the page the user is going back to.
          window.location.replace(returnTo);
          return;
        }
        const token = await getAccessToken();
        const user = token ? signedInUser() : null;
        if (!user) {
          await login();
          return;
        }
        if (active) setState({ status: "signed-in", user });
      } catch (error) {
        if (active) setState({ status: "error", message: error instanceof Error ? error.message : "Sign-in failed." });
      }
    }
    void run();
    return () => {
      active = false;
    };
  }, []);

  if (state.status === "signed-in") {
    return <AuthContext.Provider value={{ user: state.user, signOut: logout }}>{children}</AuthContext.Provider>;
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
      {state.status === "loading" ? (
        <p className="text-sm text-muted-foreground" role="status">
          Signing you in…
        </p>
      ) : (
        <>
          <p className="text-sm text-destructive" role="alert">
            {state.message}
          </p>
          <Button onClick={() => void login("/")}>Sign in again</Button>
        </>
      )}
    </div>
  );
}
