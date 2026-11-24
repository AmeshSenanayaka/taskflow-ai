import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiRequest } from "@/lib/api/client";
import { getAccessToken, login } from "@/lib/auth/oidc";

vi.mock("@/lib/auth/oidc", () => ({
  getAccessToken: vi.fn(),
  login: vi.fn(),
}));

describe("apiRequest (Phase 3.0 identity)", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends the signed-in user's access token as a bearer token", async () => {
    vi.mocked(getAccessToken).mockResolvedValue("token-123");
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));

    await apiRequest("/api/tasks");

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer token-123");
  });

  it("starts a new sign-in when the backend answers 401", async () => {
    vi.mocked(getAccessToken).mockResolvedValue("expired-token");
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));

    await expect(apiRequest("/api/tasks")).rejects.toBeInstanceOf(ApiError);
    expect(login).toHaveBeenCalledTimes(1);
  });
});
