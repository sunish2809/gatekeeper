import { useEffect, useRef, useState } from "react";
import { oidcLoginUrl, publicApi, publicGet } from "./api.js";

export default function AuthScreen({ onSuccess }) {
  const [mode, setMode] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [googleEnabled, setGoogleEnabled] = useState(false);
  const oidcHandled = useRef(false);

  useEffect(() => {
    publicGet("/api/auth/options")
      .then((options) => setGoogleEnabled(!!options.googleOidcEnabled))
      .catch(() => setGoogleEnabled(false));

    if (oidcHandled.current) return;
    oidcHandled.current = true;
    const params = new URLSearchParams(window.location.search);
    const code = params.get("oidc_code");
    const oidcError = params.get("oidc_error");
    if (!code && !oidcError) return;

    window.history.replaceState({}, document.title, window.location.pathname);
    if (oidcError) {
      setError(oidcError);
      return;
    }
    setBusy(true);
    publicApi("/api/auth/oidc/exchange", { code })
      .then((result) => onSuccess(result.token, result.admin))
      .catch((err) => setError(err.message))
      .finally(() => setBusy(false));
  }, [onSuccess]);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const path = mode === "register" ? "/api/auth/register" : "/api/auth/login";
      const body = mode === "register" ? { name, email, password } : { email, password };
      const result = await publicApi(path, body);
      onSuccess(result.token, result.admin);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-story">
        <div className="auth-brand"><span className="brand-mark">G</span><strong>Gatekeeper</strong></div>
        <div className="auth-copy">
          <p className="eyebrow">Your API control plane</p>
          <h1>Ship APIs with confidence.</h1>
          <p>Route, protect, and understand every request—without coupling gateway logic to your applications.</p>
          <ul>
            <li><i />Dynamic routing and policy enforcement</li>
            <li><i />Per-client authentication and rate limits</li>
            <li><i />Real-time traffic and latency analytics</li>
          </ul>
        </div>
        <p className="auth-foot">Built for teams that want visibility without complexity.</p>
      </section>
      <section className="auth-form-wrap">
        <form className="auth-card" onSubmit={submit}>
          <p className="eyebrow">{mode === "register" ? "Start building" : "Welcome back"}</p>
          <h2>{mode === "register" ? "Create your workspace" : "Sign in to Gatekeeper"}</h2>
          <p className="lede">{mode === "register" ? "Set up your API control plane in a few seconds." : "Manage your clients, routes, and gateway traffic."}</p>
          <div className="auth-switch">
            <button type="button" className={mode === "login" ? "" : "secondary"} onClick={() => setMode("login")}>Log in</button>
            <button type="button" className={mode === "register" ? "" : "secondary"} onClick={() => setMode("register")}>Create account</button>
          </div>
          {error ? <div className="banner">{error}</div> : null}
          {googleEnabled ? (
            <>
              <button type="button" className="google-button" disabled={busy} onClick={() => window.location.assign(oidcLoginUrl("google"))}>
                <GoogleIcon />
                Continue with Google
              </button>
              <div className="auth-divider"><span>or use email</span></div>
            </>
          ) : null}
          {mode === "register" ? (
            <>
              <label htmlFor="name">Full name</label>
              <input id="name" value={name} placeholder="Alex Morgan" autoComplete="name" onChange={(event) => setName(event.target.value)} required />
            </>
          ) : null}
          <label htmlFor="email">Work email</label>
          <input id="email" type="email" value={email} placeholder="alex@company.com" autoComplete="email" onChange={(event) => setEmail(event.target.value)} required />
          <label htmlFor="password">Password</label>
          <input id="password" type="password" value={password} placeholder={mode === "register" ? "At least 8 characters" : "Enter your password"} autoComplete={mode === "register" ? "new-password" : "current-password"} minLength={mode === "register" ? 8 : undefined} onChange={(event) => setPassword(event.target.value)} required />
          <button type="submit" disabled={busy}>{busy ? "Please wait…" : mode === "register" ? "Create workspace" : "Sign in"}</button>
        </form>
      </section>
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M21.6 12.2c0-.7-.1-1.4-.2-2.1H12v4h5.4a4.6 4.6 0 0 1-2 3v2.6h3.3c1.9-1.8 2.9-4.4 2.9-7.5Z" />
      <path fill="#34A853" d="M12 22c2.7 0 5-.9 6.7-2.3l-3.3-2.6c-.9.6-2.1 1-3.4 1a5.9 5.9 0 0 1-5.5-4.1H3.1v2.7A10 10 0 0 0 12 22Z" />
      <path fill="#FBBC05" d="M6.5 14a6 6 0 0 1 0-3.9V7.4H3.1a10 10 0 0 0 0 9.3L6.5 14Z" />
      <path fill="#EA4335" d="M12 6a5.4 5.4 0 0 1 3.8 1.5l2.9-2.8A9.7 9.7 0 0 0 3.1 7.4l3.4 2.7A5.9 5.9 0 0 1 12 6Z" />
    </svg>
  );
}
