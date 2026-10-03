/** Where the Spring Boot backend listens, as seen from this server. Never sent to the browser. */
export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
