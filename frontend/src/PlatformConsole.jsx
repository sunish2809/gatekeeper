import { useCallback, useEffect, useState } from "react";
import { createApi } from "./api.js";

export default function PlatformConsole({ token, admin, onLogout }) {
  const [status, setStatus] = useState(null);
  const [orgs, setOrgs] = useState([]);
  const [inquiries, setInquiries] = useState([]);
  const [error, setError] = useState("");
  const [form, setForm] = useState(emptyOrg);
  const [saving, setSaving] = useState(false);

  const run = useCallback(async (path, options) => createApi(token)(path, options), [token]);

  const refresh = useCallback(async () => {
    const [nextStatus, nextOrgs, nextInquiries] = await Promise.all([
      run("/api/console/status"),
      run("/api/platform/organizations"),
      run("/api/platform/inquiries")
    ]);
    setStatus(nextStatus);
    setOrgs(nextOrgs);
    setInquiries(nextInquiries);
  }, [run]);

  useEffect(() => {
    refresh().catch((err) => setError(err.message));
  }, [refresh]);

  async function createOrg() {
    setError("");
    setSaving(true);
    try {
      await run("/api/platform/organizations", { method: "POST", body: JSON.stringify(form) });
      setForm(emptyOrg());
      await refresh();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  async function act(org, action) {
    const labels = {
      activate: `Activate ${org.name}?`,
      suspend: `Suspend ${org.name}? Live routes will stop.`,
      delete: `Soft-delete ${org.name}? The tenant cannot sign in.`
    };
    if (!window.confirm(labels[action])) {
      return;
    }
    setError("");
    try {
      if (action === "delete") {
        await run(`/api/platform/organizations/${org.id}`, { method: "DELETE" });
      } else {
        await run(`/api/platform/organizations/${org.id}/${action}`, { method: "POST" });
      }
      await refresh();
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div className="shell">
      <header>
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">G</span>
          <div>
            <span className="brand-kicker">Gatekeeper Cloud</span>
            <h1>Platform</h1>
          </div>
        </div>
        <div className="account">
          <span className="avatar" aria-hidden="true">{(admin?.name || "P").slice(0, 1).toUpperCase()}</span>
          <div className="account-copy">
            <strong>{admin?.name || "Platform admin"}</strong>
            <span className="muted">{admin?.email} · platform</span>
          </div>
          <button type="button" className="secondary" onClick={onLogout}>Log out</button>
        </div>
      </header>
      <main>
        {error ? <div className="banner">{error}</div> : null}
        <section className="workspace-head">
          <div>
            <p className="eyebrow">Operator console</p>
            <h2>Organizations</h2>
            <p className="lede">Create a tenant after legal review. They sign in and publish routes under their slug.</p>
          </div>
          <div className="legend">
            <StatusPill name="Management" check={status?.management} />
            <StatusPill name="Gateway" check={status?.gateway} />
            <StatusPill name="Analytics" check={status?.analytics} />
            <span className="pill neutral">{status?.routes == null ? "Routes unavailable" : `${status.routes} live routes`}</span>
          </div>
        </section>

        <section className="card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Inbound</p>
              <h2>Access requests</h2>
              <p className="muted">Companies that used the public contact form.</p>
            </div>
            <span className="count-badge">{inquiries.length}</span>
          </div>
          {inquiries.length === 0 ? <p className="muted">No requests yet.</p> : (
            <table className="calls">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Company</th>
                  <th>Contact</th>
                  <th>Message</th>
                </tr>
              </thead>
              <tbody>
                {inquiries.map((item) => (
                  <tr key={item.id}>
                    <td>{item.createdAt ? new Date(item.createdAt).toLocaleString() : ""}</td>
                    <td>{item.company}{item.website ? ` · ${item.website}` : ""}</td>
                    <td>{item.name} · {item.email}</td>
                    <td>{item.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Onboarding</p>
              <h2>Add an organization</h2>
              <p className="muted">This creates the company, its slug, and the one tenant administrator.</p>
            </div>
          </div>
          <div className="row">
            <div>
              <label htmlFor="orgName">Organization name</label>
              <input id="orgName" value={form.name} placeholder="Foodfast" onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </div>
            <div>
              <label htmlFor="orgSlug">Slug</label>
              <input id="orgSlug" value={form.slug} placeholder="foodfast" onChange={(event) => setForm({ ...form, slug: event.target.value })} />
            </div>
          </div>
          <p className="field-help">Public paths become /{form.slug || "foodfast"}/order so two tenants can both publish /order.</p>
          <div className="row">
            <div>
              <label htmlFor="adminName">Tenant admin name</label>
              <input id="adminName" value={form.adminName} placeholder="Priya Sharma" onChange={(event) => setForm({ ...form, adminName: event.target.value })} />
            </div>
            <div>
              <label htmlFor="adminEmail">Tenant admin email</label>
              <input id="adminEmail" type="email" value={form.adminEmail} placeholder="priya@foodfast.com" onChange={(event) => setForm({ ...form, adminEmail: event.target.value })} />
            </div>
          </div>
          <label htmlFor="adminPassword">Temporary password</label>
          <input id="adminPassword" type="password" value={form.adminPassword} placeholder="At least 8 characters" onChange={(event) => setForm({ ...form, adminPassword: event.target.value })} />
          <div className="actions">
            <button type="button" onClick={createOrg} disabled={saving}>{saving ? "Creating…" : "Create organization"}</button>
          </div>
        </section>

        <section className="card">
          <div className="card-head">
            <div>
              <p className="eyebrow">Tenants</p>
              <h2>Provisioned organizations</h2>
            </div>
            <span className="count-badge">{orgs.length}</span>
          </div>
          {orgs.length === 0 ? <p className="muted">No organizations yet.</p> : (
            <table className="calls">
              <thead>
                <tr>
                  <th>Organization</th>
                  <th>Slug</th>
                  <th>Tenant admin</th>
                  <th>Status</th>
                  <th>Routes</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {orgs.map((org) => (
                  <tr key={org.id}>
                    <td>{org.name}</td>
                    <td>/{org.slug}</td>
                    <td>{org.admin ? `${org.admin.name} · ${org.admin.email}` : "—"}</td>
                    <td>{org.status}</td>
                    <td>{org.routeCount}</td>
                    <td>
                      {org.status !== "ACTIVE" ? <button type="button" className="secondary" onClick={() => act(org, "activate")}>Activate</button> : null}
                      {org.status === "ACTIVE" ? <button type="button" className="secondary" onClick={() => act(org, "suspend")}>Suspend</button> : null}
                      {org.status !== "DELETED" ? <button type="button" className="text-danger" onClick={() => act(org, "delete")}>Delete</button> : null}
                    </td>
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

function emptyOrg() {
  return { name: "", slug: "", adminName: "", adminEmail: "", adminPassword: "" };
}

function StatusPill({ name, check }) {
  if (!check) {
    return <span className="pill">{name}: …</span>;
  }
  return <span className={`pill ${check.up ? "up" : "down"}`}>{name}: {check.detail}</span>;
}
