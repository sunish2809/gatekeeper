import { useEffect, useRef, useState } from "react";
import { oidcLoginUrl, publicApi, publicGet } from "./api.js";
import { go } from "./nav.js";

export default function AuthScreen({ portal, onSuccess }) {
  const platform = portal === "platform";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(() => sessionStorage.getItem("gk.authError") || "");
  const [busy, setBusy] = useState(false);
  const [googleEnabled, setGoogleEnabled] = useState(false);
  const [demoEnabled, setDemoEnabled] = useState(false);
  const oidcHandled = useRef(false);

  useEffect(() => {
    publicGet("/api/auth/options")
      .then((options) => {
        setGoogleEnabled(!!options.googleOidcEnabled);
        setDemoEnabled(!!options.demoEnabled);
      })
      .catch(() => {
        setGoogleEnabled(false);
        setDemoEnabled(false);
      });
    const storedError = sessionStorage.getItem("gk.authError");
    if (storedError) {
      sessionStorage.removeItem("gk.authError");
      setError(storedError);
    }

    if (oidcHandled.current) return;
    oidcHandled.current = true;
    const params = new URLSearchParams(window.location.search);
    const code = params.get("oidc_code");
    const oidcError = params.get("oidc_error");
    const intended = sessionStorage.getItem("gk.portal") || portal;
    if (!code && !oidcError) return;

    if (oidcError) {
      sessionStorage.setItem("gk.authError", oidcError);
      go(platform ? "/admin" : "/login");
      return;
    }
    setBusy(true);
    publicApi("/api/auth/oidc/exchange", { code })
      .then((result) => finish(result, intended, onSuccess, (message) => {
        sessionStorage.setItem("gk.authError", message);
        go(platform ? "/admin" : "/login");
      }))
      .catch((err) => {
        sessionStorage.setItem("gk.authError", err.message);
        go(platform ? "/admin" : "/login");
      })
      .finally(() => setBusy(false));
  }, [onSuccess, platform, portal]);

  async function tryDemo() {
    setError("");
    setBusy(true);
    try {
      const result = await publicApi("/api/auth/demo", {});
      onSuccess(result.token, result.admin);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const result = await publicApi("/api/auth/login", {
        email,
        password,
        portal: platform ? "PLATFORM" : "TENANT"
      });
      finish(result, portal, onSuccess, setError);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const copy = platform ? platformCopy : tenantCopy;

  return (
    <div className="auth-shell">
      <section className="auth-story">
        <div className="auth-brand"><span className="brand-mark">G</span><strong>Gatekeeper</strong></div>
        <div className="auth-copy">
          <p className="eyebrow">{copy.eyebrow}</p>
          <h1>{copy.headline}</h1>
          <p>{copy.story}</p>
          <ul>
            {copy.points.map((point) => <li key={point}><i />{point}</li>)}
          </ul>
        </div>
        <p className="auth-foot">{copy.foot}</p>
      </section>
      <section className="auth-form-wrap">
        <form className="auth-card" onSubmit={submit}>
          <p className="eyebrow">{copy.formEyebrow}</p>
          <h2>{copy.formTitle}</h2>
          <p className="lede">{copy.lede}</p>
          {error ? <div className="banner">{error}</div> : null}
          {googleEnabled ? (
            <>
              <button
                type="button"
                className="google-button"
                disabled={busy}
                onClick={() => {
                  sessionStorage.setItem("gk.portal", portal);
                  window.location.assign(oidcLoginUrl("google"));
                }}
              >
                <GoogleIcon />
                Continue with Google
              </button>
              <div className="auth-divider"><span>or use email</span></div>
            </>
          ) : null}
          <label htmlFor="email">Work email</label>
          <input
            id="email"
            type="email"
            value={email}
            placeholder={copy.emailPlaceholder}
            autoComplete="email"
            onChange={(event) => setEmail(event.target.value)}
            required
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            placeholder="Enter your password"
            autoComplete="current-password"
            onChange={(event) => setPassword(event.target.value)}
            required
          />
          <button type="submit" disabled={busy}>{busy ? "Please wait…" : copy.submit}</button>
          {demoEnabled ? (
            <button type="button" className="secondary" disabled={busy} onClick={tryDemo}>
              Try the shared demo
            </button>
          ) : null}
          <p className="field-help">
            {copy.otherPrompt}{" "}
            <button type="button" className="auth-link" onClick={() => go(platform ? "/login" : "/admin")}>
              {copy.otherAction}
            </button>
          </p>
          <p className="field-help">
            New here?{" "}
            <button type="button" className="auth-link" onClick={() => go("/")}>How we work with you</button>
          </p>
        </form>
      </section>
    </div>
  );
}

const tenantCopy = {
  eyebrow: "SaaS administrator",
  headline: "Publish routes for your product.",
  story: "This sign-in is for the person who runs your organization’s gateway. Gatekeeper staff use a different page.",
  points: [
    "You only see your company, for example /foodfast/order",
    "Map public paths to private services and set origin plus user-token checks",
    "Customer login stays in your app; this console is not for end users"
  ],
  foot: "Accounts are created when we provision the organization. There is no public signup.",
  formEyebrow: "Organization console",
  formTitle: "SaaS administrator sign in",
  lede: "Use the email created for your company, not a Gatekeeper staff account.",
  emailPlaceholder: "priya@foodfast.com",
  submit: "Sign in to your organization",
  otherPrompt: "Gatekeeper staff?",
  otherAction: "Platform admin sign in"
};

const platformCopy = {
  eyebrow: "Platform admin",
  headline: "Provision tenants. Do not publish their routes here.",
  story: "This sign-in is only for Gatekeeper administrators who create, suspend, and remove organizations.",
  points: [
    "Create the organization, slug, and one SaaS administrator",
    "Review access requests from the public contact page",
    "Tenant routing lives in the organization console, not here"
  ],
  foot: "SaaS administrators cannot use this page, even with a valid password.",
  formEyebrow: "Gatekeeper Cloud",
  formTitle: "Platform admin sign in",
  lede: "Use your Gatekeeper staff email. Organization logins are rejected here.",
  emailPlaceholder: "platform@gatekeeper.local",
  submit: "Sign in to platform",
  otherPrompt: "SaaS administrator?",
  otherAction: "Organization sign in"
};

function finish(result, intendedPortal, onSuccess, fail) {
  const role = result?.admin?.role;
  const wantsPlatform = intendedPortal === "platform";
  const isPlatform = role === "PLATFORM_ADMIN";
  if (wantsPlatform !== isPlatform) {
    fail(wantsPlatform
      ? "This account is a SaaS administrator login. Use the organization sign-in page."
      : "This account is a Gatekeeper platform login. Use the platform admin page.");
    return;
  }
  sessionStorage.removeItem("gk.portal");
  onSuccess(result.token, result.admin);
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
