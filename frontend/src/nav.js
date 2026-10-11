import { useEffect, useState } from "react";

export function currentPath() {
  return window.location.pathname.replace(/\/+$/, "") || "/";
}

export function go(path) {
  const next = path.startsWith("/") ? path : `/${path}`;
  if (`${window.location.pathname}${window.location.search}` !== next) {
    window.history.pushState({}, "", next);
  }
  window.dispatchEvent(new PopStateEvent("popstate"));
}

export function usePath() {
  const [path, setPath] = useState(currentPath);
  useEffect(() => {
    const onPop = () => setPath(currentPath());
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);
  return path;
}
