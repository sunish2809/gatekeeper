import { useState } from "react";
import { publicApi } from "./api.js";

export default function AuthScreen({ onSuccess }) {
  const [mode, setMode] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

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
