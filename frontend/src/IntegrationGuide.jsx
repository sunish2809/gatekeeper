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

  const serverExample = `const response = await fetch(
  \`\${process.env.GATEKEEPER_URL}/commerce/orders\`,
  {
    headers: {
      "X-Api-Key": process.env.GATEKEEPER_API_KEY
    }
  }
);`;

  return (
    <section className="card integration-guide">
      <button type="button" className="guide-toggle" onClick={() => setOpen((current) => !current)} aria-expanded={open}>
        <span className="guide-icon" aria-hidden="true">↗</span>
        <span>
          <span className="eyebrow">Integration guide</span>
          <strong>Put Gatekeeper between your UI and private services</strong>
          <small>The browser calls the gateway. Your backends stay off the public internet.</small>
        </span>
        <span className="guide-chevron">{open ? "−" : "+"}</span>
      </button>

      {open ? (
        <div className="guide-content">
          <div className="architecture-flow" aria-label="Recommended request flow">
            <FlowNode label="Customer UI" detail="xyz.com · React, mobile" tone="light" />
            <FlowArrow label="Bearer token" />
            <FlowNode label="Gatekeeper" detail="CORS · OIDC · limits · routing" tone="active" />
            <FlowArrow label="X-Gatekeeper-User" />
            <FlowNode label="Private services" detail="abc.com/pay · abc.com/report" />
          </div>

          <div className="security-note">
            <span aria-hidden="true">!</span>
            <div>
              <strong>Do not put API keys in the browser</strong>
              <p>Browser callers send the signed-in user&apos;s OIDC access token. Keep <code>X-Api-Key</code> for server-to-server callers. Publish the gateway, not <code>abc.com</code>.</p>
            </div>
          </div>

          <div className="guide-steps">
            <GuideStep number="1" title="Create an API client">
              Create one client for the SaaS application. Server jobs can still use its API key. Browser users are identified by their token, not that key.
            </GuideStep>
            <GuideStep number="2" title="Publish a browser route">
              Map <code>/v1/orders</code> to a private target such as <code>https://orders.internal</code>. Add the UI origin under Allowed browser origins. Set the token issuer and audience from your identity provider.
            </GuideStep>
            <GuideStep number="3" title="Point the UI at the gateway">
              Change the frontend base URL to the public Gatekeeper domain. Leave controllers, services, and databases as they are. Optionally check <code>X-Gatekeeper-User</code> or a shared request header on the backend.
            </GuideStep>
            <GuideStep number="4" title="Verify and observe">
              Send a request from Request Lab with a Bearer token and Origin, then confirm status, latency, and correlation ID in Traffic analytics.
            </GuideStep>
          </div>

          <div className="guide-code-grid">
            <CodeExample
              title="Browser UI"
              badge="User token, no API key"
              code={browserExample}
              copied={copied === "frontend"}
              onCopy={() => copy("frontend", browserExample)}
            />
            <CodeExample
              title="Server job or webhook"
              badge="API key stays on the server"
              code={serverExample}
              copied={copied === "server"}
              onCopy={() => copy("server", serverExample)}
            />
          </div>

          <div className="guide-callout">
            <strong>What Gatekeeper changes</strong>
            <span>The public URL the UI calls, CORS, user-token checks, and the requirement that backends are private or verify gateway headers.</span>
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
