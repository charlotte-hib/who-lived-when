"use client";

import { useRouter } from "next/navigation";
import { type ReactNode, useState } from "react";
import { DetailPanel } from "@/components/detail-panel";

/**
 * A person's page opened over the current one, so it keeps its place (a story stays on its card).
 * Closing it goes back, which takes the URL back too.
 */
export function PersonPanel({ kicker, children }: { kicker: string; children: ReactNode }) {
  const router = useRouter();
  const [open, setOpen] = useState(true);
  return (
    <DetailPanel open={open} onOpenChange={setOpen} onOpenChangeComplete={(isOpen) => !isOpen && router.back()} kicker={kicker}>
      {children}
    </DetailPanel>
  );
}
