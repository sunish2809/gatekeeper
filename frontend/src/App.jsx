import { useCallback, useEffect, useState } from "react";
import { createApi } from "./api.js";
import AuthScreen from "./AuthScreen.jsx";
import Dashboard from "./Dashboard.jsx";
import IntegrationGuide from "./IntegrationGuide.jsx";

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
  const [clientName, setClientName] = useState("");
  const [clientKey, setClientKey] = useState("");
  const [clientRoles, setClientRoles] = useState("");
  const [call, setCall] = useState({
    method: "GET",
    path: "",
    apiKey: "",
    idempotencyKey: "",
    authorization: "",
    origin: "",
    body: ""
  });
  const [result, setResult] = useState("The gateway response will show here.");
  const [meta, setMeta] = useState("");
  const [overview, setOverview] = useState(null);
  const [audits, setAudits] = useState([]);
  const [editingRouteId, setEditingRouteId] = useState(null);
  const [sending, setSending] = useState(false);

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
    const [nextStatus, nextClients, nextAudits] = await Promise.all([
      run("/api/console/status"),
      run("/api/clients"),
      run("/api/audit")
    ]);
    setStatus(nextStatus);
    setClients(nextClients);
    setAudits(nextAudits);
    const stillThere = nextClients.some((client) => client.id === selectedId);
    const nextId = stillThere ? selectedId : null;
    if (!stillThere) {
      setSelectedId(null);
    }
    if (nextId) {
      await loadRoutes(nextId);
    }
  }, [loadRoutes, run, selectedId]);

  useEffect(() => {
    if (!token) {
      return;
    }
    refresh().catch((err) => setError(err.message));
  }, [refresh, token]);

  function enter(nextToken, nextAdmin) {
    localStorage.setItem("gk.token", nextToken);
    localStorage.setItem("gk.admin", JSON.stringify(nextAdmin));
    setToken(nextToken);
    setAdmin(nextAdmin);
  }

  function logout() {
    localStorage.removeItem("gk.token");
    localStorage.removeItem("gk.admin");
    setToken("");
    setAdmin(null);
  }

  async function selectClient(client) {
    setError("");
    setSelectedId(client.id);
    setEditingRouteId(null);
    setRouteForm(emptyRoute());
    setCall((current) => ({ ...current, apiKey: client.apiKey }));
    try {
      await loadRoutes(client.id);
      setOverview(await run(`/api/console/overview?clientId=${encodeURIComponent(client.id)}`));
    } catch (err) {
      setError(err.message);
    }
  }

  async function removeClient(client) {
    if (!window.confirm(`Delete ${client.name} and its routes?`)) {
      return;
    }
    setError("");
    try {
      await run(`/api/clients/${client.id}`, { method: "DELETE" });
      if (selectedId === client.id) {
        setSelectedId(null);
        setRoutes([]);
        setOverview(null);
      }
      await refresh();
    } catch (err) {
      setError(err.message);
    }
  }

  async function createClientRecord() {
    setError("");
    try {
      const created = await run("/api/clients", {
        method: "POST",
        body: JSON.stringify({ name: clientName, apiKey: clientKey, roles: splitList(clientRoles) })
      });
      setClientName("");
      setClientKey("");
      setClientRoles("");
      setCall((current) => ({ ...current, apiKey: created.apiKey }));
      await refresh();
      setSelectedId(created.id);
      setRoutes(await run(`/api/clients/${created.id}/routes`));
    } catch (err) {
      setError(err.message);
    }
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
        allowedRoles: splitList(routeForm.allowedRoles),
        authRequired: routeForm.authRequired,
        stripPrefix: routeForm.stripPrefix,
        idempotentPost: routeForm.idempotentPost,
        requireUserJwt: routeForm.requireUserJwt,
        rateLimit: Number(routeForm.rateLimit || "60"),
        rateWindowSeconds: 60,
        requestHeaders: parseHeaders(routeForm.requestHeaders),
        allowedOrigins: splitList(routeForm.allowedOrigins),
        oidcIssuer: routeForm.oidcIssuer.trim() || null,
        oidcAudience: routeForm.oidcAudience.trim() || null,
        requiredScopes: splitList(routeForm.requiredScopes)
      };
      if (editingRouteId) {
        await run(`/api/clients/${selected.id}/routes/${editingRouteId}`, { method: "PUT", body: JSON.stringify(body) });
      } else {
        await run(`/api/clients/${selected.id}/routes`, { method: "POST", body: JSON.stringify(body) });
      }
      setCall((current) => ({ ...current, path: savedPath, apiKey: selected.apiKey }));
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
      publicPath: route.publicPath,
      targetUrl: route.targetUrl,
      methods: (route.methods || []).join(","),
      allowedRoles: (route.allowedRoles || []).join(","),
      rateLimit: String(route.rateLimit),
      authRequired: route.authRequired,
      stripPrefix: route.stripPrefix,
      idempotentPost: route.idempotentPost,
      requireUserJwt: !!route.requireUserJwt,
      allowedOrigins: (route.allowedOrigins || []).join(", "),
      oidcIssuer: route.oidcIssuer || "",
      oidcAudience: route.oidcAudience || "",
      requiredScopes: (route.requiredScopes || []).join(", "),
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
    return <AuthScreen onSuccess={enter} />;
  }

  return (
    <div className="shell">
      <header>
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">G</span>
          <div>
            <span className="brand-kicker">Gatekeeper Cloud</span>
            <h1>Control plane</h1>
          </div>
        </div>
        <div className="account">
          <span className="avatar" aria-hidden="true">{(admin?.name || "A").slice(0, 1).toUpperCase()}</span>
          <div className="account-copy">
            <strong>{admin?.name || "Account"}</strong>
            <span className="muted">{admin?.email}</span>
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
            <p className="lede">Configure clients, publish routes, and inspect traffic from one secure workspace.</p>
          </div>
          <div className="legend">
            <StatusPill name="Management" check={status?.management} />
            <StatusPill name="Gateway" check={status?.gateway} />
            <StatusPill name="Analytics" check={status?.analytics} />
            <span className="pill neutral">{status?.routes == null ? "Routes unavailable" : `${status.routes} active routes`}</span>
          </div>
        </section>
        <section className="steps">
          <div><strong>01</strong><span>Register an application</span><small>Create an identity for a UI or a server that will call the gateway.</small></div>
          <div><strong>02</strong><span>Publish a route</span><small>Map a public gateway path to a private backend URL.</small></div>
          <div><strong>03</strong><span>Point the UI at Gatekeeper</span><small>The browser calls the gateway with a user token; backends stay private.</small></div>
          <div><strong>04</strong><span>Observe traffic</span><small>Track volume, latency, failures, and throttled calls.</small></div>
        </section>

        <IntegrationGuide />

        <div className="grid">
          <section className="card">
            <div className="card-head">
              <div>
                <p className="eyebrow">Applications</p>
                <h2>API clients</h2>
                <p className="muted">Issue credentials and roles to applications that call your gateway.</p>
              </div>
              <span className="count-badge">{clients.length}</span>
            </div>
            <div className="row">
              <div>
                <label htmlFor="clientName">Application name</label>
                <input id="clientName" value={clientName} placeholder="Acme Commerce — Production" onChange={(event) => setClientName(event.target.value)} />
              </div>
              <div>
                <label htmlFor="clientKey">Client API key</label>
                <input id="clientKey" value={clientKey} placeholder="e.g. acme_prod_key_7f9a2c" onChange={(event) => setClientKey(event.target.value)} />
              </div>
            </div>
            <label htmlFor="clientRoles">Roles</label>
            <input id="clientRoles" value={clientRoles} placeholder="orders:read, orders:write" onChange={(event) => setClientRoles(event.target.value)} />
            <p className="field-help">Comma-separated permissions used by route-level authorization.</p>
            <div className="actions">
              <button type="button" onClick={createClientRecord}>Create API client</button>
            </div>
            <div className="list">
              {clients.length === 0 ? <div className="empty-state"><strong>No API clients yet</strong><span>Create your first application identity above.</span></div> : clients.map((client) => (
                <div key={client.id} className={client.id === selectedId ? "item selected" : "item"}>
                  <button type="button" className="item-main" onClick={() => selectClient(client)}>
                    <span className="item-title"><i className="client-dot" />{client.name}</span>
                    <small>{client.apiKey} · {(client.roles || []).join(", ") || "no roles"}</small>
                  </button>
                  <button type="button" className="text-danger" onClick={() => removeClient(client)}>Delete</button>
                </div>
              ))}
            </div>
          </section>

          <section className="card">
            <div className="card-head">
              <div>
                <p className="eyebrow">Routing</p>
                <h2>Routes & policies</h2>
                <p className="muted">{selected ? `Configuring ${selected.name}. Changes are published immediately.` : "Select a client, then connect a gateway path to its backend."}</p>
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
                <input id="publicPath" value={routeForm.publicPath} placeholder="/v1/orders" onChange={(event) => setRouteForm({ ...routeForm, publicPath: event.target.value })} />
              </div>
            </div>
            <label htmlFor="targetUrl">Target URL</label>
            <input id="targetUrl" value={routeForm.targetUrl} placeholder="https://orders.internal.acme.com" onChange={(event) => setRouteForm({ ...routeForm, targetUrl: event.target.value })} />
            <p className="field-help">Private service address. Browsers call the gateway path, not this URL.</p>
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
            <label htmlFor="allowedRoles">Allowed roles</label>
            <input id="allowedRoles" value={routeForm.allowedRoles} placeholder="orders:read, admin" onChange={(event) => setRouteForm({ ...routeForm, allowedRoles: event.target.value })} />
            <p className="field-help">Used when another server-side client calls with X-Api-Key. The owner key always works.</p>
            <label htmlFor="allowedOrigins">Allowed browser origins</label>
            <input id="allowedOrigins" value={routeForm.allowedOrigins} placeholder="https://xyz.com, http://localhost:5173" onChange={(event) => setRouteForm({ ...routeForm, allowedOrigins: event.target.value })} />
            <p className="field-help">Required for browser calls. Use the SaaS UI origin, not the backend URL.</p>
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
            <label htmlFor="requiredScopes">Required scopes</label>
            <input id="requiredScopes" value={routeForm.requiredScopes} placeholder="openid, email" onChange={(event) => setRouteForm({ ...routeForm, requiredScopes: event.target.value })} />
            <p className="field-help">Issuer plus audience validates end-user OIDC tokens from JWKS. Leave blank to keep the optional HMAC user JWT.</p>
            <label htmlFor="requestHeaders">Request headers added to the backend</label>
            <textarea id="requestHeaders" value={routeForm.requestHeaders} placeholder={"X-Source=gatekeeper\nX-Internal-Token=verify-me"} onChange={(event) => setRouteForm({ ...routeForm, requestHeaders: event.target.value })} />
            <p className="field-help">One <code>Name=value</code> per line. Backends can reject traffic that did not come through the gateway.</p>
            <div className="checks">
              <label><input type="checkbox" checked={routeForm.authRequired} onChange={(event) => setRouteForm({ ...routeForm, authRequired: event.target.checked })} /> Require API key</label>
              <label><input type="checkbox" checked={routeForm.stripPrefix} onChange={(event) => setRouteForm({ ...routeForm, stripPrefix: event.target.checked })} /> Strip prefix</label>
              <label><input type="checkbox" checked={routeForm.idempotentPost} onChange={(event) => setRouteForm({ ...routeForm, idempotentPost: event.target.checked })} /> Idempotent POST</label>
              <label><input type="checkbox" checked={routeForm.requireUserJwt} onChange={(event) => setRouteForm({ ...routeForm, requireUserJwt: event.target.checked })} /> Require user token (OIDC)</label>
            </div>
            <div className="actions">
              <button type="button" onClick={saveRoute}>{editingRouteId ? "Update route" : "Save route"}</button>
              {editingRouteId ? <button type="button" className="secondary" onClick={() => { setEditingRouteId(null); setRouteForm(emptyRoute()); }}>Cancel</button> : null}
            </div>
            <div className="list">
              {selected && routes.length === 0 ? <div className="empty-state"><strong>No routes published</strong><span>Add the first route for {selected.name}.</span></div> : routes.map((route) => (
                <div key={route.id} className="item">
                  <button type="button" className="item-main" onClick={() => editRoute(route)}>
                    <span className="item-title"><span className="method-tag">{route.methods?.[0] || "ANY"}</span>{route.publicPath}</span>
                    <small className="target-line">→ {route.targetUrl}</small>
                    <small>{route.methods.join(", ")}{route.authRequired ? " · API key" : " · no API key"}{route.stripPrefix ? " · strip prefix" : ""}{route.requireUserJwt || route.oidcIssuer ? " · user token" : ""}{route.allowedOrigins?.length ? ` · CORS: ${route.allowedOrigins.join(", ")}` : ""}{route.allowedRoles?.length ? ` · roles: ${route.allowedRoles.join(", ")}` : ""}</small>
                  </button>
                  <button type="button" className="text-danger" onClick={() => removeRoute(route)}>Delete</button>
                </div>
              ))}
            </div>
          </section>
        </div>

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
              <input id="tryPath" value={call.path} placeholder="/v1/orders/ord_12345" onChange={(event) => setCall({ ...call, path: event.target.value })} />
            </div>
          </div>
          <div className="row">
            <div>
              <label htmlFor="tryKey">X-Api-Key</label>
              <input id="tryKey" value={call.apiKey} placeholder="Optional for browser-style routes" onChange={(event) => setCall({ ...call, apiKey: event.target.value })} />
            </div>
            <div>
              <label htmlFor="tryIdem">Idempotency-Key</label>
              <input id="tryIdem" value={call.idempotencyKey} placeholder="e.g. order-create-8b27f1" onChange={(event) => setCall({ ...call, idempotencyKey: event.target.value })} />
            </div>
          </div>
          <div className="row">
            <div>
              <label htmlFor="tryAuth">Authorization (Bearer)</label>
              <input id="tryAuth" value={call.authorization} placeholder="Paste an end-user access token" onChange={(event) => setCall({ ...call, authorization: event.target.value })} />
            </div>
            <div>
              <label htmlFor="tryOrigin">Origin</label>
              <input id="tryOrigin" value={call.origin} placeholder="https://xyz.com" onChange={(event) => setCall({ ...call, origin: event.target.value })} />
            </div>
          </div>
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

        <section className="card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Governance</p>
              <h2>Administrative activity</h2>
              <p className="muted">Recent configuration changes made in this account.</p>
            </div>
          </div>
          {audits.length === 0 ? <p className="muted">No admin writes yet.</p> : (
            <table className="calls">
              <thead><tr><th>When</th><th>Action</th><th>Path</th></tr></thead>
              <tbody>
                {audits.slice(0, 12).map((entry) => (
                  <tr key={entry.id}>
                    <td>{entry.createdAt ? new Date(entry.createdAt).toLocaleString() : ""}</td>
                    <td>{entry.action}</td>
                    <td>{entry.path}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
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
    allowedRoles: "",
    rateLimit: "60",
    authRequired: false,
    stripPrefix: true,
    idempotentPost: false,
    requireUserJwt: false,
    allowedOrigins: "",
    oidcIssuer: "",
    oidcAudience: "",
    requiredScopes: "",
    requestHeaders: "X-Source=gatekeeper"
  };
}

function StatusPill({ name, check }) {
  if (!check) {
    return <span className="pill">{name}: …</span>;
  }
  return <span className={`pill ${check.up ? "up" : "down"}`}>{name}: {check.detail}</span>;
}
