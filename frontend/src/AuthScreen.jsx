import { useEffect, useRef, useState } from "react";
import { oidcLoginUrl, publicApi, publicGet } from "./api.js";
import AboutPage from "./AboutPage.jsx";

export default function AuthScreen({ onSuccess }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [googleEnabled, setGoogleEnabled] = useState(false);
  const [view, setView] = useState("login");
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
      const result = await publicApi("/api/auth/login", { email, password });
      onSuccess(result.token, result.admin);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (view === "about") {
    return <AboutPage onSignIn={() => setView("login")} />;
  }

  return (
    <div className="auth-shell">
      <section className="auth-story">
        <div className="auth-brand"><span className="brand-mark">G</span><strong>Gatekeeper</strong></div>
        <div className="auth-copy">
          <p className="eyebrow">Your API control plane</p>
          <h1>Access is provisioned, not self-serve.</h1>
          <p>Organizations join after review. A Gatekeeper administrator creates the workspace. You sign in and publish routes.</p>
          <ul>
            <li><i />One slug per tenant, so /foodfast/order never collides with /quick/order</li>
            <li><i />Tenant admins manage only their organization</li>
            <li><i />The gateway reads live config with a service key, not a user login</li>
          </ul>
        </div>
        <p className="auth-foot">
          No public signup.{" "}
          <button type="button" className="auth-link" onClick={() => setView("about")}>Read how it works and request access</button>
        </p>
      </section>
      <section className="auth-form-wrap">
        <form className="auth-card" onSubmit={submit}>
          <p className="eyebrow">Welcome back</p>
          <h2>Sign in to Gatekeeper</h2>
          <p className="lede">Use the email a Gatekeeper administrator created for you.</p>
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
          <label htmlFor="email">Work email</label>
          <input id="email" type="email" value={email} placeholder="priya@foodfast.com" autoComplete="email" onChange={(event) => setEmail(event.target.value)} required />
          <label htmlFor="password">Password</label>
          <input id="password" type="password" value={password} placeholder="Enter your password" autoComplete="current-password" onChange={(event) => setPassword(event.target.value)} required />
          <button type="submit" disabled={busy}>{busy ? "Please wait…" : "Sign in"}</button>
          <p className="field-help">Need a workspace? <button type="button" className="auth-link" onClick={() => setView("about")}>See details and contact us</button></p>
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
