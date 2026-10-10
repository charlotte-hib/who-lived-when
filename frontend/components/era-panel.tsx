"use client";

import { type ReactNode, useEffect, useState } from "react";
import { DetailPanel } from "@/components/detail-panel";
import { DoorCard } from "@/components/door-card";
import { PersonChip } from "@/components/person-chip";
import { Section } from "@/components/section";
import { fetchEra } from "@/lib/api-browser";
import { DOMAIN_ORDER, DOMAINS } from "@/lib/domains";
import { hasStory } from "@/lib/moments";
import type { Era, EraDetail } from "@/lib/types";
import { cn } from "@/lib/utils";
import { formatYear } from "@/lib/years";

/** The details of an era, fetched the first time its panel opens. */
function useEraDetail(id: string, open: boolean) {
  const [detail, setDetail] = useState<EraDetail | null>(null);
  const loaded = detail?.era.id === id;
  useEffect(() => {
    if (!open || loaded) return;
    const controller = new AbortController();
    fetchEra(id, controller.signal).then(setDetail).catch(() => { /* aborted or offline */ });
    return () => controller.abort();
  }, [id, open, loaded]);
  return loaded ? detail : null;
}

type Props = {
  era: Era;
  /** The moment the panel is opened from, shown as "You are here" among the era's moments. */
  momentId?: string;
  /** What the link reads, the era's name by default. */
  children?: ReactNode;
  className?: string;
};

/** Who governed, as a link that opens the era in a panel: its moments to step into, and its people. */
export function EraButton({ era, momentId, children, className }: Props) {
  const [open, setOpen] = useState(false);
  const detail = useEraDetail(era.id, open);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={cn("text-foreground underline decoration-muted-foreground/50 underline-offset-4 hover:decoration-lamp", className)}
      >
        {children ?? era.label}
      </button>
      <DetailPanel open={open} onOpenChange={setOpen} kicker="Who governs">
        <article className="grid gap-8">
          <div>
            <h2 className="font-story text-3xl leading-tight font-medium">{era.label}</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {era.region} · {formatYear(era.startYear)}–{formatYear(era.endYear)}
              {era.governedBy !== era.label && ` · ${era.governedBy}`}
            </p>
          </div>

          {!detail ? (
            <div aria-busy className="grid animate-pulse gap-3">
              <div className="h-40 rounded-2xl bg-muted" />
              <div className="h-6 w-2/3 rounded bg-muted" />
            </div>
          ) : (
            <>
              {detail.moments.length > 0 && (
                <Section title="Moments in this era">
                  <div className="grid gap-3">
                    {detail.moments.map((moment) => (
                      <DoorCard
                        key={moment.id}
                        door={{
                          kind: moment.id === momentId ? "You are here" : hasStory(moment) ? "Story" : "Moment",
                          text: moment.hook,
                          target: moment,
                          faces: moment.cast,
                        }}
                      />
                    ))}
                  </div>
                </Section>
              )}

              {DOMAIN_ORDER.filter((domain) => detail.people[domain]?.length).map((domain) => (
                <Section key={domain} title={DOMAINS[domain].title}>
                  <div className="flex flex-wrap gap-2">
                    {detail.people[domain]!.map((person) => <PersonChip key={person.slug} person={person} />)}
                  </div>
                </Section>
              ))}
            </>
          )}
        </article>
      </DetailPanel>
    </>
  );
}
