export default function Dashboard({ clientName, overview, onRefresh }) {
  if (!clientName) {
    return (
      <section className="card dashboard">
        <p className="eyebrow">Observability</p>
        <h2>Traffic analytics</h2>
        <div className="empty-state dashboard-empty"><strong>Select an API client</strong><span>Traffic, latency, and status insights will appear here.</span></div>
      </section>
    );
  }
  if (!overview) {
    return (
      <section className="card dashboard">
        <p className="eyebrow">Observability</p>
        <h2>Traffic · {clientName}</h2>
        <p className="muted">Loading the latest calls…</p>
      </section>
    );
  }
  const total = overview.total || 0;
  const successRate = total === 0 ? 0 : Math.round((overview.success / total) * 100);
  const maxCount = Math.max(1, ...(overview.series || []).map((point) => point.count));
  const maxPath = Math.max(1, ...(overview.paths || []).map((path) => path.count));

  return (
    <section className="card dashboard">
      <div className="dash-head">
        <div>
          <p className="eyebrow">Observability</p>
          <h2>Traffic analytics</h2>
          <p className="muted">Latest {total} calls routed for <strong>{clientName}</strong>.</p>
        </div>
        <button type="button" className="secondary" onClick={onRefresh}>Refresh</button>
      </div>
      <div className="stats">
        <Stat label="Requests" value={total} />
        <Stat label="Success" value={`${successRate}%`} tone="good" />
        <Stat label="Avg latency" value={`${overview.avgLatencyMs} ms`} />
        <Stat label="P95 latency" value={`${overview.p95LatencyMs} ms`} />
        <Stat label="Rate limited" value={overview.rateLimited} tone={overview.rateLimited ? "warn" : ""} />
        <Stat label="Server errors" value={overview.serverErrors} tone={overview.serverErrors ? "bad" : ""} />
      </div>
      <div className="charts">
        <div className="chart">
          <h3>Requests over time</h3>
          <SeriesChart series={overview.series || []} max={maxCount} />
        </div>
        <div className="chart">
          <h3>Status mix</h3>
          <StatusMix overview={overview} />
        </div>
      </div>
      <div className="charts">
        <div className="chart">
          <h3>Busiest paths</h3>
          {(overview.paths || []).length === 0 ? <p className="muted">No calls yet.</p> : (
            <ul className="bars">
              {overview.paths.map((path) => (
                <li key={path.path}>
                  <div className="bar-label"><span>{path.path}</span><span>{path.count}</span></div>
                  <div className="bar-track"><div style={{ width: `${(path.count / maxPath) * 100}%` }} /></div>
                  <small>{path.avgLatencyMs} ms avg{path.errors ? ` · ${path.errors} errors` : ""}</small>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="chart">
          <h3>Latest calls</h3>
          <table className="calls">
            <thead><tr><th>Method</th><th>Path</th><th>Status</th><th>Latency</th></tr></thead>
            <tbody>
              {(overview.recent || []).map((call, index) => (
                <tr key={`${call.correlationId || index}`}>
                  <td>{call.method}</td>
                  <td>{call.path}</td>
                  <td><span className={statusClass(call.status)}>{call.status}</span></td>
                  <td>{call.latencyMs} ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function Stat({ label, value, tone }) {
  return (
    <div className={`stat ${tone || ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function SeriesChart({ series, max }) {
  if (series.length === 0) {
    return <p className="muted">Send a call through the gateway and refresh.</p>;
  }
  const width = 640;
  const height = 180;
  const pad = 16;
  const step = series.length === 1 ? 0 : (width - pad * 2) / (series.length - 1);
  const points = series.map((point, index) => {
    const x = pad + index * step;
    const y = height - pad - (point.count / max) * (height - pad * 2);
    return [x, y];
  });
  const line = points.map((point) => point.join(",")).join(" ");
  const area = `${pad},${height - pad} ${line} ${points[points.length - 1][0]},${height - pad}`;
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="line-chart" role="img" aria-label="Requests over time">
      <polygon points={area} className="area" />
      <polyline points={line} className="line" />
      {points.map((point, index) => <circle key={series[index].time} cx={point[0]} cy={point[1]} r="3.5" />)}
    </svg>
  );
}

function StatusMix({ overview }) {
  const parts = [
    { label: "2xx / 3xx", count: overview.success, color: "#00a35c" },
    { label: "4xx", count: overview.clientErrors, color: "#d28a16" },
    { label: "5xx", count: overview.serverErrors, color: "#c43d3d" }
  ];
  const total = parts.reduce((sum, part) => sum + part.count, 0);
  let cursor = 0;
  const gradient = total === 0
    ? "#e6edf5"
    : `conic-gradient(${parts.map((part) => {
      const start = cursor;
      cursor += (part.count / total) * 360;
      return `${part.color} ${start}deg ${cursor}deg`;
    }).join(", ")})`;
  return (
    <div className="mix">
      <div className="donut" style={{ background: gradient }}><span>{total === 0 ? "0" : `${Math.round((overview.success / total) * 100)}%`}</span></div>
      <ul>
        {parts.map((part) => (
          <li key={part.label}><i style={{ background: part.color }} />{part.label}<strong>{part.count}</strong></li>
        ))}
      </ul>
    </div>
  );
}

function statusClass(status) {
  if (status >= 500) return "code bad";
  if (status >= 400) return "code warn";
  return "code good";
}
