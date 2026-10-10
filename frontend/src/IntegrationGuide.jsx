import { useState } from "react";

export default function IntegrationGuide() {
  const [open, setOpen] = useState(true);
  const [copied, setCopied] = useState("");

  async function copy(name, value) {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      setCopied("");
      return;
    }
    setCopied(name);
    window.setTimeout(() => setCopied(""), 1600);
  }

  const browserExample = `const token = await yourIdp.getAccessToken();

const orders = await fetch("https://gateway.example.com/v1/orders", {
  headers: {
    Authorization: \`Bearer \${token}\`
  }
}).then(response => response.json());`;

  return (
    <section className="card integration-guide">
      <button type="button" className="guide-toggle" onClick={() => setOpen((current) => !current)} aria-expanded={open}>
        <span className="guide-icon" aria-hidden="true">↗</span>
        <span>
          <span className="eyebrow">Integration guide</span>
          <strong>Put Gatekeeper between your UI and private services</strong>
          <small>The browser calls the gateway. Your backends stay private.</small>
        </span>
        <span className="guide-chevron">{open ? "−" : "+"}</span>
      </button>

      {open ? (
        <div className="guide-content">
          <div className="architecture-flow" aria-label="Recommended request flow">
            <FlowNode label="Customer UI" detail="xyz.com · React, mobile" tone="light" />
            <FlowArrow label="User token" />
            <FlowNode label="Gatekeeper" detail="Who may call · limits · routing" tone="active" />
            <FlowArrow label="Forward" />
            <FlowNode label="Private services" detail="pay · report" />
          </div>

          <div className="security-note">
            <span aria-hidden="true">!</span>
            <div>
              <strong>Customers never call abc.com</strong>
              <p>Point the UI at the Gatekeeper URL. The user is already logged in to your app; the gateway only checks their token, then reaches pay or report for them.</p>
            </div>
          </div>

          <div className="guide-steps">
            <GuideStep number="1" title="Create an application">
              Name your SaaS app. This is just a folder for its routes.
            </GuideStep>
            <GuideStep number="2" title="Publish a route">
              Map <code>/pay</code> to your private pay service. Add <code>https://xyz.com</code> as the browser origin. Paste the issuer and audience from the login provider your UI already uses.
            </GuideStep>
            <GuideStep number="3" title="Point the UI at the gateway">
              Change the frontend base URL to the public Gatekeeper domain. Your screens and databases stay the same.
            </GuideStep>
            <GuideStep number="4" title="Try a call">
              Use Request Lab with a user token and the website origin, then check Traffic analytics.
            </GuideStep>
          </div>

          <div className="guide-code-grid">
            <CodeExample
              title="What the UI changes"
              badge="One URL and one header"
              code={browserExample}
              copied={copied === "frontend"}
              onCopy={() => copy("frontend", browserExample)}
            />
          </div>

          <div className="guide-callout">
            <strong>What Gatekeeper changes</strong>
            <span>The public URL the UI calls, and checks before your service runs.</span>
            <strong>What stays unchanged</strong>
            <span>Your screens, business logic, API controllers, and database.</span>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function FlowNode({ label, detail, tone = "" }) {
  return (
    <div className={`flow-node ${tone}`}>
      <strong>{label}</strong>
      <span>{detail}</span>
    </div>
  );
}

function FlowArrow({ label }) {
  return (
    <div className="flow-arrow">
      <span>{label}</span>
      <i>→</i>
    </div>
  );
}

function GuideStep({ number, title, children }) {
  return (
    <article className="guide-step">
      <span>{number}</span>
      <div><strong>{title}</strong><p>{children}</p></div>
    </article>
  );
}

function CodeExample({ title, badge, code, copied, onCopy }) {
  return (
    <div className="guide-code">
      <div>
        <strong>{title}</strong>
        <span>{badge}</span>
        <button type="button" className="copy-button" onClick={onCopy}>{copied ? "Copied" : "Copy"}</button>
      </div>
      <pre>{code}</pre>
    </div>
  );
}
