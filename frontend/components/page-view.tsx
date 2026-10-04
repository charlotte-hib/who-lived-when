"use client";

import { useEffect } from "react";
import { type PageKind, trackPageView } from "@/lib/analytics";

/** Counts a view of the page it is on, by template (and moment), never by URL. Renders nothing. */
export function PageView({ page, moment }: { page: PageKind; moment?: string }) {
  useEffect(() => trackPageView(page, moment), [page, moment]);
  return null;
}
