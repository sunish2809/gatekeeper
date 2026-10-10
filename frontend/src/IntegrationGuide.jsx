import { useState } from "react";

export default function IntegrationGuide() {
  const [open, setOpen] = useState(true);
  const [copied, setCopied] = useState("");

  async function copy(name, value) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(name);
      window.setTimeout(() => setCopied(""), 1600);
    } catch {
      setCopied("");
    }
  }

  const serverExample = `const response = await fetch(
  \`\${process.env.GATEKEEPER_URL}/commerce/orders\`,
  {
    headers: {
      "X-Api-Key": process.env.GATEKEEPER_API_KEY
    }
  }
);`;

  const frontendExample = `// Your browser calls your own backend.
const orders = await fetch("/api/orders")
  .then(response => response.json());`;

  return (
    <section className="card integration-guide">
      <button type="button" className="guide-toggle" onClick={() => setOpen((current) => !current)} aria-expanded={open}>
        <span className="guide-icon" aria-hidden="true">↗</span>
        <span>
          <span className="eyebrow">Integration guide</span>
          <strong>Connect Gatekeeper to your application</strong>
          <small>See where the gateway belongs and what changes in your existing app.</small>
        </span>
        <span className="guide-chevron">{open ? "−" : "+"}</span>
      </button>

      {open ? (
        <div className="guide-content">
          <div className="architecture-flow" aria-label="Recommended request flow">
            <FlowNode label="Customer UI" detail="React, mobile, web" tone="light" />
            <FlowArrow label="HTTPS" />
            <FlowNode label="App backend / BFF" detail="Keeps the API key secret" />
            <FlowArrow label="X-Api-Key" />
            <FlowNode label="Gatekeeper" detail="Auth · limits · routing" tone="active" />
            <FlowArrow label="Proxied request" />
            <FlowNode label="API services" detail="Your existing backends" />
          </div>

          <div className="security-note">
            <span aria-hidden="true">!</span>
            <div>
              <strong>Keep API keys out of browser code</strong>
              <p>A browser bundle is public. Your UI should call your backend or BFF; that server calls Gatekeeper with the client API key stored in an environment variable.</p>
            </div>
          </div>

          <div className="guide-steps">
            <GuideStep number="1" title="Create an API client">
              Create one client for each calling application. Store its generated or chosen API key in that application's server-side secrets.
            </GuideStep>
            <GuideStep number="2" title="Publish a route">
              Map a gateway path such as <code>/commerce</code> to your service URL such as <code>https://api.acme.com</code>. With prefix stripping, <code>/commerce/orders</code> reaches <code>/orders</code>.
            </GuideStep>
            <GuideStep number="3" title="Change the server base URL">
              Point your backend or BFF at the public Gatekeeper URL and include <code>X-Api-Key</code>. Your target service does not need gateway-specific code.
            </GuideStep>
            <GuideStep number="4" title="Verify and observe">
              Send a request, then use Traffic analytics to confirm the status, latency, route, throttling, and correlation ID.
            </GuideStep>
          </div>

          <div className="guide-code-grid">
            <CodeExample
              title="Server or BFF"
              badge="Secret stays here"
              code={serverExample}
              copied={copied === "server"}
              onCopy={() => copy("server", serverExample)}
            />
            <CodeExample
              title="Browser UI"
              badge="No gateway secret"
              code={frontendExample}
              copied={copied === "frontend"}
              onCopy={() => copy("frontend", frontendExample)}
            />
          </div>

          <div className="guide-callout">
            <strong>What Gatekeeper changes</strong>
            <span>Only the URL your server calls and the addition of an API-key header.</span>
            <strong>What stays unchanged</strong>
            <span>Your frontend screens, business logic, API controllers, and database.</span>
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
