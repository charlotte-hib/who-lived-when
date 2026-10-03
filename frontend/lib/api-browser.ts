import type { Person, SearchResults } from "@/lib/types";

/**
 * Browser-side fetches, for data that changes while the user interacts.
 * Paths are relative: Next.js forwards `/api/*` to the backend (see `next.config.ts`).
 */
async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(path, { signal });
  if (!res.ok) throw new Error(`GET ${path} failed: ${res.status}`);
  return res.json();
}

/** A few people alive in [year] in every region except [region]. */
export const fetchAliveElsewhere = (region: string, year: number, signal?: AbortSignal) =>
  get<Person[]>(`/api/years/${year}/people?exclude=${region}`, signal);

export const fetchSearch = (query: string, signal?: AbortSignal) =>
  get<SearchResults>(`/api/search?q=${encodeURIComponent(query)}`, signal);
