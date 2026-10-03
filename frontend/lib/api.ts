import { connection } from "next/server";
import { BACKEND_URL } from "@/lib/backend-url";
import type { EraDetail, MomentDetail, MomentSummary, PersonDetail, Story } from "@/lib/types";

/** Server-side fetch. Returns null for a 404 so pages can call `notFound()`. */
async function get<T>(path: string): Promise<T | null> {
  // Data lives in the backend, so render at request time, not at build time.
  await connection();
  const res = await fetch(`${BACKEND_URL}${path}`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`GET ${path} failed: ${res.status}`);
  return res.json();
}

const segment = encodeURIComponent;

export const getMoments = async () => (await get<MomentSummary[]>("/api/moments")) ?? [];
export const getMoment = (id: string) => get<MomentDetail>(`/api/moments/${segment(id)}`);
export const getStory = (id: string) => get<Story>(`/api/moments/${segment(id)}/story`);
export const getEra = (id: string) => get<EraDetail>(`/api/eras/${segment(id)}`);
export const getPerson = (slug: string, year?: number) =>
  get<PersonDetail>(`/api/people/${segment(slug)}${year ? `?year=${year}` : ""}`);
