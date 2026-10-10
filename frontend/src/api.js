const API = import.meta.env.VITE_API_BASE || "http://localhost:8081";

export function createApi(token) {
  return async function api(path, options = {}) {
    const headers = {
      "Content-Type": "application/json",
      ...(options.headers || {})
    };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    const response = await fetch(`${API}${path}`, { ...options, headers });
    const text = await response.text();
    let body = text;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
    }
    if (response.status === 401 && token && !path.startsWith("/api/auth/")) {
      localStorage.removeItem("gk.token");
      localStorage.removeItem("gk.admin");
      window.location.reload();
    }
    if (!response.ok) {
      const message = body && body.message ? body.message : text || String(response.status);
      throw new Error(message);
    }
    return body;
  };
}

export function publicApi(path, body) {
  return createApi(null)(path, { method: "POST", body: JSON.stringify(body) });
}

export function publicGet(path) {
  return createApi(null)(path);
}

export function oidcLoginUrl(provider) {
  return `${API}/oauth2/authorization/${provider}`;
}
