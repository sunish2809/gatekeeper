import { useEffect, useState } from "react";
import { publicApi, publicGet } from "./api.js";

export default function AboutPage({ onSignIn }) {
  const [contactEmail, setContactEmail] = useState("");
  const [form, setForm] = useState({ name: "", company: "", email: "", website: "", message: "" });
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    publicGet("/api/auth/options")
      .then((options) => setContactEmail(options.contactEmail || ""))
      .catch(() => setContactEmail(""));
  }, []);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await publicApi("/api/inquiries", form);
      setDone(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="about-page">
      <header className="about-nav">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">G</span>
          <div>
            <span className="brand-kicker">Gatekeeper Cloud</span>
            <h1>API gateway for SaaS teams</h1>
          </div>
        </div>
        <button type="button" className="secondary" onClick={onSignIn}>Sign in</button>
      </header>

      <main className="about-main">
        <section className="about-hero">
          <p className="eyebrow">How we work with you</p>
          <h2>Read this first. Then tell us you want in.</h2>
          <p className="lede">
            Gatekeeper sits between your customer UI and your private services. We do not open public signup.
            After you review what we provide, send a request. A Gatekeeper administrator creates your organization
            and the one login for your team.
          </p>
        </section>

        <section className="about-grid">
          <article className="card">
            <p className="eyebrow">What we provide</p>
            <h3>A controlled front door for your APIs</h3>
            <ul className="about-list">
              <li>Your UI calls Gatekeeper. Pay, report, and other services stay private.</li>
              <li>Each company gets a slug, so Foodfast and Quick can both publish /order as /foodfast/order and /quick/order.</li>
              <li>The gateway checks the website origin and the logged-in user token before forwarding.</li>
              <li>Rate limits, timeouts, idempotent POST, and header rules are set per route.</li>
              <li>Traffic, latency, and failures show up in analytics without changing your backend code.</li>
            </ul>
          </article>
          <article className="card">
            <p className="eyebrow">How you join</p>
            <h3>Provisioned access</h3>
            <ol className="about-steps">
              <li>You send company details with the form on this page.</li>
              <li>We review fit, policies, and anything legal or commercial that needs agreement.</li>
              <li>A platform admin creates your organization, slug, and tenant administrator.</li>
              <li>You sign in, publish routes, and point your UI at the gateway.</li>
            </ol>
          </article>
        </section>

        <section className="about-grid">
          <article className="card">
            <p className="eyebrow">Policies</p>
            <h3>What you agree to operate under</h3>
            <ul className="about-list">
              <li>No self-serve accounts. Only provisioned tenant admins may sign in.</li>
              <li>You manage routes for your organization only. You cannot see another tenant.</li>
              <li>Customer login stays in your product. Gatekeeper verifies tokens; it does not replace your IdP.</li>
              <li>Do not expose private service URLs to browsers. The public address is the gateway path.</li>
              <li>We may suspend or remove an organization if it violates these rules or an agreed contract.</li>
            </ul>
          </article>
          <article className="card">
            <p className="eyebrow">Pricing</p>
            <h3>Talk to us before you buy</h3>
            <p className="muted">
              Pricing depends on traffic, number of routes, and whether you run on our hosted gateway or a dedicated setup.
              There is no checkout on this site. Tell us volume and we will send terms.
            </p>
            <p className="muted">
              Local and evaluation workspaces are for review only. Production use starts after we provision the organization.
            </p>
          </article>
        </section>

        <section className="card about-contact">
          <p className="eyebrow">Contact</p>
          <h3>Request access</h3>
          <p className="muted">
            Use this form so a Gatekeeper administrator can see your company in the platform console.
            {contactEmail ? <> You can also email <a href={`mailto:${contactEmail}`}>{contactEmail}</a>.</> : null}
          </p>
          {done ? (
            <div className="empty-state">
              <strong>Request received</strong>
              <span>We will reply at the email you entered if the workspace is a fit.</span>
            </div>
          ) : (
            <form onSubmit={submit}>
              {error ? <div className="banner">{error}</div> : null}
              <div className="row">
                <div>
                  <label htmlFor="inqName">Your name</label>
                  <input id="inqName" value={form.name} placeholder="Priya Sharma" required onChange={(event) => setForm({ ...form, name: event.target.value })} />
                </div>
                <div>
                  <label htmlFor="inqCompany">Company</label>
                  <input id="inqCompany" value={form.company} placeholder="Foodfast" required onChange={(event) => setForm({ ...form, company: event.target.value })} />
                </div>
              </div>
              <div className="row">
                <div>
                  <label htmlFor="inqEmail">Work email</label>
                  <input id="inqEmail" type="email" value={form.email} placeholder="priya@foodfast.com" required onChange={(event) => setForm({ ...form, email: event.target.value })} />
                </div>
                <div>
                  <label htmlFor="inqSite">Website</label>
                  <input id="inqSite" value={form.website} placeholder="https://foodfast.com" onChange={(event) => setForm({ ...form, website: event.target.value })} />
                </div>
              </div>
              <label htmlFor="inqMessage">What you need</label>
              <textarea id="inqMessage" value={form.message} required placeholder="We have a customer UI and private pay/report services. We want Gatekeeper in front of them." onChange={(event) => setForm({ ...form, message: event.target.value })} />
              <div className="actions">
                <button type="submit" disabled={busy}>{busy ? "Sending…" : "Send request"}</button>
                <button type="button" className="secondary" onClick={onSignIn}>Already have an account</button>
              </div>
            </form>
          )}
        </section>
      </main>
    </div>
  );
}
