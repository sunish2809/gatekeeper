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
      <form className="auth-card" onSubmit={submit}>
        <p className="eyebrow">Gatekeeper</p>
        <h1>{mode === "register" ? "Create your account" : "Welcome back"}</h1>
        <p className="lede">This account owns the clients and target routes you register. Callers of the gateway still use the API key you create afterward.</p>
        <div className="auth-switch">
          <button type="button" className={mode === "login" ? "" : "secondary"} onClick={() => setMode("login")}>Log in</button>
          <button type="button" className={mode === "register" ? "" : "secondary"} onClick={() => setMode("register")}>Register</button>
        </div>
        {error ? <div className="banner">{error}</div> : null}
        {mode === "register" ? (
          <>
            <label htmlFor="name">Name</label>
            <input id="name" value={name} autoComplete="name" onChange={(event) => setName(event.target.value)} required />
          </>
        ) : null}
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={email} autoComplete="email" onChange={(event) => setEmail(event.target.value)} required />
        <label htmlFor="password">Password</label>
        <input id="password" type="password" value={password} autoComplete={mode === "register" ? "new-password" : "current-password"} minLength={mode === "register" ? 8 : undefined} onChange={(event) => setPassword(event.target.value)} required />
        <button type="submit" disabled={busy}>{busy ? "Please wait" : mode === "register" ? "Create account" : "Log in"}</button>
      </form>
    </div>
  );
}
