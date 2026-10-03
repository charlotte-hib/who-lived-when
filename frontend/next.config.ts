import type { NextConfig } from "next";
import { BACKEND_URL } from "./lib/backend-url";

const nextConfig: NextConfig = {
  // A self-contained server (.next/standalone/server.js) for the Docker image.
  output: "standalone",
  // The browser only talks to this site: API calls are forwarded to the Spring Boot backend,
  // so the app works behind any single public address (a tunnel, a reverse proxy) without CORS.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` }];
  },
};

export default nextConfig;
