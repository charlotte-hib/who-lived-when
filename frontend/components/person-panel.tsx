"use client";

import { ChevronRight } from "lucide-react";
import { useRouter } from "next/navigation";
import { type ReactNode, createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { DetailPanel } from "@/components/detail-panel";

type Step = { slug: string; name: string; href: string };

const TrailContext = createContext<(step: Step) => void>(() => {});

/**
 * A person's page opened over the current one, so it keeps its place (a story stays on its card).
 * Moving from person to person leaves a trail at the top, to step back along it. Closing goes back to
 * where the panel was opened, which takes the URL back too.
 */
export function PersonPanel({ kicker, children }: { kicker: string; children: ReactNode }) {
  const router = useRouter();
  const [open, setOpen] = useState(true);
  const [trail, setTrail] = useState<Step[]>([]);

  // Revisiting someone already on the trail cuts it back to them.
  const visit = useCallback(
    (step: Step) =>
      setTrail((current) => {
        const at = current.findIndex((other) => other.slug === step.slug);
        return at === -1 ? [...current, step] : current.slice(0, at + 1);
      }),
    [],
  );

  return (
    <DetailPanel open={open} onOpenChange={setOpen} onOpenChangeComplete={(isOpen) => !isOpen && router.back()} kicker={kicker}>
      {trail.length > 1 && (
        <nav aria-label="Your path" className="-mt-1 mb-5 flex flex-wrap items-center gap-x-1 gap-y-1 text-sm">
          {trail.map((step, index) => (
            <span key={step.slug} className="inline-flex items-center gap-1">
              {index > 0 && <ChevronRight aria-hidden className="size-3.5 text-muted-foreground" />}
              {index === trail.length - 1 ? (
                <span aria-current="page" className="font-medium">{step.name}</span>
              ) : (
                <button type="button" onClick={() => router.replace(step.href)} className="text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
                  {step.name}
                </button>
              )}
            </span>
          ))}
        </nav>
      )}
      <TrailContext value={visit}>{children}</TrailContext>
    </DetailPanel>
  );
}

/** Marks the person shown in the panel as a step on the trail, and starts them at the top of the panel. */
export function TrailStep(step: Step) {
  const visit = useContext(TrailContext);
  const marker = useRef<HTMLSpanElement>(null);
  const { slug, name, href } = step;
  useEffect(() => {
    visit({ slug, name, href });
    marker.current?.closest("[data-panel-scroll]")?.scrollTo({ top: 0 });
  }, [visit, slug, name, href]);
  return <span ref={marker} hidden />;
}
