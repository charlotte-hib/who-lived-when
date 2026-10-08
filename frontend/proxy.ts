import { NextResponse, type NextRequest } from "next/server";
import { take } from "@/lib/rate-limit";

/** Limits how fast one visitor can load pages and call the API (see `lib/rate-limit.ts`). */
export function proxy(request: NextRequest) {
  const wait = take(request.headers.get("x-forwarded-for"));
  if (wait === 0) return NextResponse.next();
  return new NextResponse("Too many requests. Please wait a moment and try again.\n", {
    status: 429,
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store", "Retry-After": String(wait) },
  });
}

// Pages, navigations and API calls, but not static files, nor Next.js prefetches: a page prefetches every link in
// view, and a prefetch only holds the shell of a page, no data. A prefetch carries both headers below, so a request
// counts when either is missing, and sending them does not get a page's data past the limit.
export const config = {
  matcher: [
    { source: "/((?!_next/static|_next/image|favicon.ico).*)", missing: [{ type: "header", key: "rsc" }] },
    { source: "/((?!_next/static|_next/image|favicon.ico).*)", missing: [{ type: "header", key: "next-router-prefetch" }] },
  ],
};
