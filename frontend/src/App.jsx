import { useCallback, useEffect, useState } from "react";
import { createApi } from "./api.js";
import AuthScreen from "./AuthScreen.jsx";
import Dashboard from "./Dashboard.jsx";

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
  const [call, setCall] = useState({
    method: "GET",
    path: "",
    apiKey: "",
    idempotencyKey: "",
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
        body: JSON.stringify({ name: clientName, apiKey: clientKey, roles: ["caller"] })
      });
      setClientName("");
      setClientKey("");
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
        methods: routeForm.methods.split(",").map((method) => method.trim()).filter(Boolean),
        authRequired: routeForm.authRequired,
        stripPrefix: routeForm.stripPrefix,
        idempotentPost: routeForm.idempotentPost,
        requireUserJwt: routeForm.requireUserJwt,
        rateLimit: Number(routeForm.rateLimit || "60"),
        rateWindowSeconds: 60,
        requestHeaders: { "X-Source": "gatekeeper" }
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
      rateLimit: String(route.rateLimit),
      authRequired: route.authRequired,
      stripPrefix: route.stripPrefix,
      idempotentPost: route.idempotentPost,
      requireUserJwt: !!route.requireUserJwt
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
          <h1>Gatekeeper</h1>
          <p className="lede">Register a client and the target routes. Callers then send traffic to the smart gateway.</p>
        </div>
        <div className="account">
          <div>
            <strong>{admin?.name || "Account"}</strong>
            <span className="muted">{admin?.email}</span>
          </div>
          <button type="button" className="secondary" onClick={logout}>Log out</button>
        </div>
      </header>
      <main>
        {error ? <div className="banner">{error}</div> : null}
        <div className="legend">
          <StatusPill name="Management" check={status?.management} />
          <StatusPill name="Gateway" check={status?.gateway} />
          <StatusPill name="Analytics" check={status?.analytics} />
          <span className="pill">{status?.routes == null ? "routes unknown" : `${status.routes} routes in memory`}</span>
        </div>
        <section className="steps">
          <div><strong>1</strong>Create the client. That identity is allowed to call the gateway.</div>
          <div><strong>2</strong>Save a route: public path, target URL, rate limit, auth, idempotent POST.</div>
          <div><strong>3</strong>The gateway loads that config into memory and applies it on the live call.</div>
          <div><strong>4</strong>Analytics stores latency, status, and rate-limit misses for that path.</div>
        </section>

        <div className="grid">
          <section className="card">
            <h2>Clients</h2>
            <div className="row">
              <div>
                <label htmlFor="clientName">Name</label>
                <input id="clientName" value={clientName} placeholder="demo-shop" onChange={(event) => setClientName(event.target.value)} />
              </div>
              <div>
                <label htmlFor="clientKey">API key</label>
                <input id="clientKey" value={clientKey} placeholder="gk_demo_shop" onChange={(event) => setClientKey(event.target.value)} />
              </div>
            </div>
            <div className="actions">
              <button type="button" onClick={createClientRecord}>Create client</button>
            </div>
            <div className="list">
              {clients.length === 0 ? <p className="muted">No clients yet.</p> : clients.map((client) => (
                <div key={client.id} className={client.id === selectedId ? "item selected" : "item"}>
                  <button type="button" className="item-main" onClick={() => selectClient(client)}>
                    <strong>{client.name}</strong><br />
                    <small>{client.apiKey} · {(client.roles || []).join(", ")}</small>
                  </button>
                  <button type="button" className="text-danger" onClick={() => removeClient(client)}>Delete</button>
                </div>
              ))}
            </div>
          </section>

          <section className="card">
            <h2>Targets</h2>
            <p className="muted">{selected ? `Routes for ${selected.name}. A saved change is pushed to the gateway.` : "Select a client, then point a public path at their target."}</p>
            <div className="row">
              <div>
                <label htmlFor="routeName">Name</label>
                <input id="routeName" value={routeForm.name} placeholder="orders" onChange={(event) => setRouteForm({ ...routeForm, name: event.target.value })} />
              </div>
              <div>
                <label htmlFor="publicPath">Public path</label>
                <input id="publicPath" value={routeForm.publicPath} placeholder="/try" onChange={(event) => setRouteForm({ ...routeForm, publicPath: event.target.value })} />
              </div>
            </div>
            <label htmlFor="targetUrl">Target URL</label>
            <input id="targetUrl" value={routeForm.targetUrl} placeholder="http://localhost:9000" onChange={(event) => setRouteForm({ ...routeForm, targetUrl: event.target.value })} />
            <div className="row">
              <div>
                <label htmlFor="methods">Methods</label>
                <input id="methods" value={routeForm.methods} onChange={(event) => setRouteForm({ ...routeForm, methods: event.target.value })} />
              </div>
              <div>
                <label htmlFor="rateLimit">Rate limit / minute</label>
                <input id="rateLimit" value={routeForm.rateLimit} onChange={(event) => setRouteForm({ ...routeForm, rateLimit: event.target.value })} />
              </div>
            </div>
            <div className="checks">
              <label><input type="checkbox" checked={routeForm.authRequired} onChange={(event) => setRouteForm({ ...routeForm, authRequired: event.target.checked })} /> Auth required</label>
              <label><input type="checkbox" checked={routeForm.stripPrefix} onChange={(event) => setRouteForm({ ...routeForm, stripPrefix: event.target.checked })} /> Strip prefix</label>
              <label><input type="checkbox" checked={routeForm.idempotentPost} onChange={(event) => setRouteForm({ ...routeForm, idempotentPost: event.target.checked })} /> Idempotent POST</label>
              <label><input type="checkbox" checked={routeForm.requireUserJwt} onChange={(event) => setRouteForm({ ...routeForm, requireUserJwt: event.target.checked })} /> Require user JWT</label>
            </div>
            <div className="actions">
              <button type="button" onClick={saveRoute}>{editingRouteId ? "Update route" : "Save route"}</button>
              {editingRouteId ? <button type="button" className="secondary" onClick={() => { setEditingRouteId(null); setRouteForm(emptyRoute()); }}>Cancel</button> : null}
            </div>
            <div className="list">
              {routes.map((route) => (
                <div key={route.id} className="item">
                  <button type="button" className="item-main" onClick={() => editRoute(route)}>
                    <strong>{route.publicPath} → {route.targetUrl}</strong><br />
                    <small>{route.methods.join(", ")}{route.authRequired ? " · API key" : " · public"}{route.stripPrefix ? " · strip prefix" : ""}{route.requireUserJwt ? " · user JWT" : ""}</small>
                  </button>
                  <button type="button" className="text-danger" onClick={() => removeRoute(route)}>Delete</button>
                </div>
              ))}
            </div>
          </section>
        </div>

        <section className="card">
          <h2>Send through the gateway</h2>
          <p className="muted">This is the client call. It hits port 8080. The gateway checks the key, the rate limit, and idempotency, then forwards to the target or returns 429.</p>
          <div className="row">
            <div>
              <label htmlFor="tryMethod">Method</label>
              <select id="tryMethod" value={call.method} onChange={(event) => setCall({ ...call, method: event.target.value })}>
                {["GET", "POST", "PUT", "PATCH", "DELETE"].map((method) => <option key={method}>{method}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="tryPath">Gateway path</label>
              <input id="tryPath" value={call.path} placeholder="/try/order" onChange={(event) => setCall({ ...call, path: event.target.value })} />
            </div>
          </div>
          <div className="row">
            <div>
              <label htmlFor="tryKey">X-Api-Key</label>
              <input id="tryKey" value={call.apiKey} onChange={(event) => setCall({ ...call, apiKey: event.target.value })} />
            </div>
            <div>
              <label htmlFor="tryIdem">Idempotency-Key</label>
              <input id="tryIdem" value={call.idempotencyKey} placeholder="only for idempotent POST" onChange={(event) => setCall({ ...call, idempotencyKey: event.target.value })} />
            </div>
          </div>
          <label htmlFor="tryBody">Body</label>
          <textarea id="tryBody" value={call.body} onChange={(event) => setCall({ ...call, body: event.target.value })} />
          <div className="actions">
            <button type="button" onClick={send} disabled={sending}>Send</button>
            <span className="muted">{meta}</span>
          </div>
          <pre>{result}</pre>
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
          <h2>Admin audit</h2>
          <p className="muted">Creates, updates, and deletes on this account.</p>
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

function emptyRoute() {
  return {
    name: "",
    publicPath: "",
    targetUrl: "",
    methods: "GET,POST",
    rateLimit: "60",
    authRequired: true,
    stripPrefix: true,
    idempotentPost: false,
    requireUserJwt: false
  };
}

function StatusPill({ name, check }) {
  if (!check) {
    return <span className="pill">{name}: …</span>;
  }
  return <span className={`pill ${check.up ? "up" : "down"}`}>{name}: {check.detail}</span>;
}
