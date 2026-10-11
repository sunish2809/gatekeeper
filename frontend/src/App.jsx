import { useCallback, useEffect, useState } from "react";
import { createApi } from "./api.js";
import AboutPage from "./AboutPage.jsx";
import AuthScreen from "./AuthScreen.jsx";
import Dashboard from "./Dashboard.jsx";
import IntegrationGuide from "./IntegrationGuide.jsx";
import { go, usePath } from "./nav.js";
import PlatformConsole from "./PlatformConsole.jsx";

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem("gk.token") || "");
  const [admin, setAdmin] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem("gk.admin") || "null");
    } catch {
      return null;
    }
  });
  const [status, setStatus] = useState(null);
  const [clients, setClients] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [routes, setRoutes] = useState([]);
  const [error, setError] = useState("");
  const [routeForm, setRouteForm] = useState(emptyRoute);
  const [call, setCall] = useState({
    method: "GET",
    path: "",
    idempotencyKey: "",
    authorization: "",
    origin: "",
    body: ""
  });
  const [result, setResult] = useState("The gateway response will show here.");
  const [meta, setMeta] = useState("");
  const [overview, setOverview] = useState(null);
  const [editingRouteId, setEditingRouteId] = useState(null);
  const [sending, setSending] = useState(false);
  const path = usePath();

  const selected = clients.find((client) => client.id === selectedId) || null;

  const run = useCallback(async (path, options) => {
    return createApi(token)(path, options);
  }, [token]);

  const loadRoutes = useCallback(async (clientId) => {
    if (!clientId) {
      setRoutes([]);
      return;
    }
    setRoutes(await run(`/api/clients/${clientId}/routes`));
  }, [run]);

  const refresh = useCallback(async () => {
    const [nextStatus, nextClients] = await Promise.all([
      run("/api/console/status"),
      run("/api/clients")
    ]);
    setStatus(nextStatus);
    setClients(nextClients);
    const stillThere = nextClients.some((client) => client.id === selectedId);
    const nextId = stillThere ? selectedId : nextClients[0]?.id || null;
    setSelectedId(nextId);
    if (nextId) {
      await loadRoutes(nextId);
      setOverview(await run(`/api/console/overview?clientId=${encodeURIComponent(nextId)}`));
    } else {
      setRoutes([]);
    }
  }, [loadRoutes, run, selectedId]);

  useEffect(() => {
    if (!token) {
      return;
    }
    run("/api/auth/me")
      .then((result) => {
        localStorage.setItem("gk.admin", JSON.stringify(result.admin));
        setAdmin(result.admin);
      })
      .catch((err) => setError(err.message));
  }, [run, token]);

  useEffect(() => {
    if (!token || !admin?.role || admin.role === "PLATFORM_ADMIN") {
      return;
    }
    refresh().catch((err) => setError(err.message));
  }, [admin?.role, refresh, token]);

  function enter(nextToken, nextAdmin) {
    localStorage.setItem("gk.token", nextToken);
    localStorage.setItem("gk.admin", JSON.stringify(nextAdmin));
    setToken(nextToken);
    setAdmin(nextAdmin);
    go(nextAdmin?.role === "PLATFORM_ADMIN" ? "/admin" : "/login");
  }

  function logout() {
    const dest = admin?.role === "PLATFORM_ADMIN" ? "/admin" : "/login";
    localStorage.removeItem("gk.token");
    localStorage.removeItem("gk.admin");
    setToken("");
    setAdmin(null);
    go(dest);
  }

  async function saveRoute() {
    setError("");
    if (!selected) {
      setError("Create or select a client first.");
      return;
    }
    try {
      const savedPath = routeForm.publicPath;
      const body = {
        name: routeForm.name,
        publicPath: routeForm.publicPath,
        targetUrl: routeForm.targetUrl,
        methods: splitList(routeForm.methods),
        allowedRoles: [],
        authRequired: false,
        stripPrefix: routeForm.stripPrefix,
        idempotentPost: routeForm.idempotentPost,
        requireUserJwt: routeForm.requireUserJwt,
        timeoutMs: Number(routeForm.timeoutMs || "5000"),
        rateLimit: Number(routeForm.rateLimit || "60"),
        rateWindowSeconds: 60,
        requestHeaders: parseHeaders(routeForm.requestHeaders),
        allowedOrigins: splitList(routeForm.allowedOrigins),
        oidcIssuer: routeForm.oidcIssuer.trim() || null,
        oidcAudience: routeForm.oidcAudience.trim() || null,
        requiredScopes: []
      };
      if (editingRouteId) {
        await run(`/api/clients/${selected.id}/routes/${editingRouteId}`, { method: "PUT", body: JSON.stringify(body) });
      } else {
        await run(`/api/clients/${selected.id}/routes`, { method: "POST", body: JSON.stringify(body) });
      }
      const slug = admin?.organizationSlug;
      const livePath = slug && !savedPath.startsWith(`/${slug}`)
        ? `/${slug}${savedPath.startsWith("/") ? savedPath : `/${savedPath}`}`
        : savedPath;
      setCall((current) => ({ ...current, path: livePath }));
      setEditingRouteId(null);
      setRouteForm(emptyRoute());
      await refresh();
      await loadRoutes(selected.id);
      setOverview(await run(`/api/console/overview?clientId=${encodeURIComponent(selected.id)}`));
    } catch (err) {
      setError(err.message);
    }
  }

  async function removeRoute(route) {
    if (!window.confirm(`Delete route ${route.publicPath}?`)) {
      return;
    }
    setError("");
    try {
      await run(`/api/clients/${selected.id}/routes/${route.id}`, { method: "DELETE" });
      if (editingRouteId === route.id) {
        setEditingRouteId(null);
        setRouteForm(emptyRoute());
      }
      await refresh();
      await loadRoutes(selected.id);
    } catch (err) {
      setError(err.message);
    }
  }

  function editRoute(route) {
    setEditingRouteId(route.id);
    setRouteForm({
      name: route.name,
      publicPath: route.relativePath || route.publicPath,
      targetUrl: route.targetUrl,
      methods: (route.methods || []).join(","),
      rateLimit: String(route.rateLimit),
      timeoutMs: String(route.timeoutMs || 5000),
      stripPrefix: route.stripPrefix,
      idempotentPost: route.idempotentPost,
      requireUserJwt: !!route.requireUserJwt,
      allowedOrigins: (route.allowedOrigins || []).join(", "),
      oidcIssuer: route.oidcIssuer || "",
      oidcAudience: route.oidcAudience || "",
      requestHeaders: formatHeaders(route.requestHeaders)
    });
  }

  async function send() {
    setError("");
    setSending(true);
    try {
      const response = await run("/api/console/try", {
        method: "POST",
        body: JSON.stringify(call)
      });
      setMeta(response.error
        ? response.error
        : `${response.status} · ${response.latencyMs} ms · ${response.correlationId || "no correlation id"}`);
      setResult(response.error
        ? response.error
        : JSON.stringify({
          status: response.status,
          headers: response.headers,
          body: response.body,
          truncated: response.truncated
        }, null, 2));
      setStatus(await run("/api/console/status"));
      if (selectedId) {
        await new Promise((resolve) => setTimeout(resolve, 800));
        setOverview(await run(`/api/console/overview?clientId=${encodeURIComponent(selectedId)}`));
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  }

  if (!token) {
    const params = new URLSearchParams(window.location.search);
    const oidcReturn = params.has("oidc_code") || params.has("oidc_error");
    const portal = oidcReturn
      ? (sessionStorage.getItem("gk.portal") === "platform" ? "platform" : "tenant")
      : path === "/admin" ? "platform" : path === "/login" ? "tenant" : null;
    if (portal) {
      return <AuthScreen portal={portal} onSuccess={enter} />;
    }
    return <AboutPage />;
  }

  if (admin?.role === "PLATFORM_ADMIN") {
    return <PlatformConsole token={token} admin={admin} onLogout={logout} />;
  }

  return (
    <div className="shell">
      <header>
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">G</span>
          <div>
            <span className="brand-kicker">Organization console</span>
            <h1>{admin?.organizationName || "SaaS administrator"}</h1>
          </div>
        </div>
        <div className="account">
          <span className="avatar" aria-hidden="true">{(admin?.name || "A").slice(0, 1).toUpperCase()}</span>
          <div className="account-copy">
            <strong>{admin?.name || "Account"}</strong>
            <span className="muted">{admin?.email}{admin?.organizationName ? ` · ${admin.organizationName}` : ""}</span>
          </div>
          <button type="button" className="secondary" onClick={logout}>Log out</button>
        </div>
      </header>
      <main>
        {error ? <div className="banner">{error}</div> : null}
        <section className="workspace-head">
          <div>
            <p className="eyebrow">API operations</p>
            <h2>Gateway overview</h2>
            <p className="lede">{admin?.organizationSlug
              ? `Routes for ${admin.organizationName} are published under /${admin.organizationSlug}.`
              : "Publish routes for your organization."}</p>
          </div>
          <div className="legend">
            <StatusPill name="Management" check={status?.management} />
            <StatusPill name="Gateway" check={status?.gateway} />
            <StatusPill name="Analytics" check={status?.analytics} />
            <span className="pill neutral">{status?.routes == null ? "Routes unavailable" : `${status.routes} active routes`}</span>
          </div>
        </section>
        <section className="steps">
          <div><strong>01</strong><span>Publish a route</span><small>Map /order to your private order service. Gatekeeper stores /{admin?.organizationSlug || "org"}/order.</small></div>
          <div><strong>02</strong><span>Protect it</span><small>Your website origin and the login provider that already signs in your users.</small></div>
          <div><strong>03</strong><span>Point the UI at Gatekeeper</span><small>The browser calls the gateway with a user token; backends stay private.</small></div>
          <div><strong>04</strong><span>Observe traffic</span><small>Track volume, latency, failures, and throttled calls.</small></div>
        </section>

        <IntegrationGuide />

        <section className="card">
            <div className="card-head">
              <div>
                <p className="eyebrow">Routing</p>
                <h2>Routes & policies</h2>
                <p className="muted">{selected ? `Live path prefix /${admin?.organizationSlug || ""}. Changes publish immediately.` : "Your organization workspace is loading."}</p>
              </div>
              <span className="count-badge">{routes.length}</span>
            </div>
            <div className="row">
              <div>
                <label htmlFor="routeName">Name</label>
                <input id="routeName" value={routeForm.name} placeholder="Orders API" onChange={(event) => setRouteForm({ ...routeForm, name: event.target.value })} />
              </div>
              <div>
                <label htmlFor="publicPath">Public path</label>
                <input id="publicPath" value={routeForm.publicPath} placeholder="/order" onChange={(event) => setRouteForm({ ...routeForm, publicPath: event.target.value })} />
              </div>
            </div>
            <label htmlFor="targetUrl">Target URL</label>
            <input id="targetUrl" value={routeForm.targetUrl} placeholder="https://orders.internal.acme.com" onChange={(event) => setRouteForm({ ...routeForm, targetUrl: event.target.value })} />
            <p className="field-help">Private service address. Callers use /{admin?.organizationSlug || "org"}{routeForm.publicPath || "/order"}.</p>
            <div className="row">
              <div>
                <label htmlFor="methods">Methods</label>
                <input id="methods" value={routeForm.methods} placeholder="GET, POST" onChange={(event) => setRouteForm({ ...routeForm, methods: event.target.value })} />
              </div>
              <div>
                <label htmlFor="rateLimit">Rate limit / minute</label>
                <input id="rateLimit" type="number" min="0" value={routeForm.rateLimit} placeholder="60" onChange={(event) => setRouteForm({ ...routeForm, rateLimit: event.target.value })} />
              </div>
            </div>
            <div className="row">
              <div>
                <label htmlFor="timeoutMs">Timeout (ms)</label>
                <input id="timeoutMs" type="number" min="1" value={routeForm.timeoutMs} placeholder="5000" onChange={(event) => setRouteForm({ ...routeForm, timeoutMs: event.target.value })} />
              </div>
            </div>
            <label htmlFor="allowedOrigins">Allowed browser origins</label>
            <input id="allowedOrigins" value={routeForm.allowedOrigins} placeholder="https://xyz.com, http://localhost:5173" onChange={(event) => setRouteForm({ ...routeForm, allowedOrigins: event.target.value })} />
            <p className="field-help">Your website address, so the browser is allowed to call the gateway.</p>
            <div className="row">
              <div>
                <label htmlFor="oidcIssuer">User token issuer</label>
                <input id="oidcIssuer" value={routeForm.oidcIssuer} placeholder="https://accounts.google.com" onChange={(event) => setRouteForm({ ...routeForm, oidcIssuer: event.target.value })} />
              </div>
              <div>
                <label htmlFor="oidcAudience">Audience</label>
                <input id="oidcAudience" value={routeForm.oidcAudience} placeholder="your-app-client-id" onChange={(event) => setRouteForm({ ...routeForm, oidcAudience: event.target.value })} />
              </div>
            </div>
            <p className="field-help">From the same login provider your UI already uses. The gateway checks the user token; it does not log the user in.</p>
            <label htmlFor="requestHeaders">Header rules added to the backend</label>
            <textarea id="requestHeaders" value={routeForm.requestHeaders} placeholder={"X-Source=gatekeeper"} onChange={(event) => setRouteForm({ ...routeForm, requestHeaders: event.target.value })} />
            <p className="field-help">One Name=value per line. Your service can require these so only Gatekeeper traffic is accepted.</p>
            <div className="checks">
              <label><input type="checkbox" checked={routeForm.stripPrefix} onChange={(event) => setRouteForm({ ...routeForm, stripPrefix: event.target.checked })} /> Strip prefix</label>
              <label><input type="checkbox" checked={routeForm.idempotentPost} onChange={(event) => setRouteForm({ ...routeForm, idempotentPost: event.target.checked })} /> Idempotent POST</label>
              <label><input type="checkbox" checked={routeForm.requireUserJwt} onChange={(event) => setRouteForm({ ...routeForm, requireUserJwt: event.target.checked })} /> Require user token</label>
            </div>
            <div className="actions">
              <button type="button" onClick={saveRoute}>{editingRouteId ? "Update route" : "Save route"}</button>
              {editingRouteId ? <button type="button" className="secondary" onClick={() => { setEditingRouteId(null); setRouteForm(emptyRoute()); }}>Cancel</button> : null}
            </div>
            <div className="list">
              {!selected ? <div className="empty-state"><strong>Workspace not ready</strong><span>Ask a Gatekeeper administrator to provision this organization.</span></div> : selected && routes.length === 0 ? <div className="empty-state"><strong>No routes published</strong><span>Add /order or /pay for {admin?.organizationName || selected.name}.</span></div> : routes.map((route) => (
                <div key={route.id} className="item">
                  <button type="button" className="item-main" onClick={() => editRoute(route)}>
                    <span className="item-title"><span className="method-tag">{route.methods?.[0] || "ANY"}</span>{route.publicPath}</span>
                    <small className="target-line">→ {route.targetUrl}</small>
                    <small>{route.methods.join(", ")}{route.requireUserJwt || route.oidcIssuer ? " · user token" : ""}{route.allowedOrigins?.length ? ` · ${route.allowedOrigins.join(", ")}` : ""}{route.stripPrefix ? " · strip prefix" : ""}</small>
                  </button>
                  <button type="button" className="text-danger" onClick={() => removeRoute(route)}>Delete</button>
                </div>
              ))}
            </div>
        </section>

        <section className="card request-lab">
          <div className="card-head">
            <div>
              <p className="eyebrow">Request lab</p>
              <h2>Test the gateway</h2>
              <p className="muted">Send a real request and inspect the status, headers, and response body.</p>
            </div>
            <span className="live-badge"><i /> Live request</span>
          </div>
          <div className="row">
            <div>
              <label htmlFor="tryMethod">Method</label>
              <select id="tryMethod" value={call.method} onChange={(event) => setCall({ ...call, method: event.target.value })}>
                {["GET", "POST", "PUT", "PATCH", "DELETE"].map((method) => <option key={method}>{method}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="tryPath">Gateway path</label>
              <input id="tryPath" value={call.path} placeholder={admin?.organizationSlug ? `/${admin.organizationSlug}/order` : "/foodfast/order"} onChange={(event) => setCall({ ...call, path: event.target.value })} />
            </div>
          </div>
          <div className="row">
            <div>
              <label htmlFor="tryAuth">User token</label>
              <input id="tryAuth" value={call.authorization} placeholder="Paste the signed-in user's access token" onChange={(event) => setCall({ ...call, authorization: event.target.value })} />
            </div>
            <div>
              <label htmlFor="tryOrigin">Website origin</label>
              <input id="tryOrigin" value={call.origin} placeholder="https://xyz.com" onChange={(event) => setCall({ ...call, origin: event.target.value })} />
            </div>
          </div>
          <label htmlFor="tryIdem">Idempotency-Key</label>
          <input id="tryIdem" value={call.idempotencyKey} placeholder="Only needed if Idempotent POST is on" onChange={(event) => setCall({ ...call, idempotencyKey: event.target.value })} />
          <label htmlFor="tryBody">JSON body</label>
          <textarea id="tryBody" value={call.body} placeholder={'{\n  "productId": "prod_123",\n  "quantity": 1\n}'} onChange={(event) => setCall({ ...call, body: event.target.value })} />
          <div className="actions">
            <button type="button" onClick={send} disabled={sending}>{sending ? "Sending…" : "Send request"}</button>
            <span className="muted">{meta}</span>
          </div>
          <div className="response-panel">
            <span className="response-label">Response</span>
            <pre>{result}</pre>
          </div>
        </section>

        {selected ? (
          <Dashboard
            clientName={selected?.name}
            overview={overview}
            onRefresh={() => {
              if (!selectedId) return;
              run(`/api/console/overview?clientId=${encodeURIComponent(selectedId)}`)
                .then(setOverview)
                .catch((err) => setError(err.message));
            }}
          />
        ) : null}
      </main>
    </div>
  );
}

function splitList(value) {
  return (value || "").split(",").map((item) => item.trim()).filter(Boolean);
}

function parseHeaders(raw) {
  const headers = {};
  for (const line of (raw || "").split("\n")) {
    const split = line.indexOf("=");
    if (split <= 0) {
      continue;
    }
    const name = line.slice(0, split).trim();
    if (name) {
      headers[name] = line.slice(split + 1);
    }
  }
  return headers;
}

function formatHeaders(headers) {
  if (!headers || typeof headers !== "object") {
    return "X-Source=gatekeeper";
  }
  const lines = Object.entries(headers).map(([name, value]) => `${name}=${value ?? ""}`);
  return lines.length ? lines.join("\n") : "X-Source=gatekeeper";
}

function emptyRoute() {
  return {
    name: "",
    publicPath: "",
    targetUrl: "",
    methods: "GET,POST",
    rateLimit: "60",
    timeoutMs: "5000",
    stripPrefix: true,
    idempotentPost: false,
    requireUserJwt: true,
    allowedOrigins: "",
    oidcIssuer: "",
    oidcAudience: "",
    requestHeaders: "X-Source=gatekeeper"
  };
}

function StatusPill({ name, check }) {
  if (!check) {
    return <span className="pill">{name}: …</span>;
  }
  return <span className={`pill ${check.up ? "up" : "down"}`}>{name}: {check.detail}</span>;
}
