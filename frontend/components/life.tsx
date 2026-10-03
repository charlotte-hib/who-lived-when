"use client";

import { useState } from "react";
import { DetailPanel } from "@/components/detail-panel";
import { PersonChip, chipClass } from "@/components/person-chip";
import type { Life, Person } from "@/lib/types";
import { cn } from "@/lib/utils";
import { formatYear } from "@/lib/years";

const STRIPES = {
  sm: "size-7 bg-[repeating-linear-gradient(45deg,var(--color-everyday)_0_2px,transparent_2px_6px)] opacity-70",
  lg: "size-24 bg-[repeating-linear-gradient(45deg,var(--color-everyday)_0_4px,transparent_4px_10px)] opacity-80 ring-4 ring-everyday/60",
};

/** Typical lives have no portrait: they are drawn as a hatched circle, so they never pass for real people. */
export function LifeMark({ size, className }: { size: keyof typeof STRIPES; className?: string }) {
  return <span aria-hidden className={cn("shrink-0 rounded-full", STRIPES[size], className)} />;
}

type AroundProps = {
  /** Real people alive at the same time and place, to set the life among them. */
  around?: Person[];
  year?: number;
};

/** A typical life in full: what it was like, when, and who else was alive then. */
export function LifeDetails({ life, around = [], year }: { life: Life } & AroundProps) {
  return (
    <article className="grid gap-5">
      <div className="flex items-center gap-4">
        <LifeMark size="lg" />
        <div>
          <h2 className="font-story text-3xl leading-tight font-medium">{life.label}</h2>
          <p className="mt-1 font-mono text-sm text-muted-foreground">
            {formatYear(life.startYear)}–{formatYear(life.endYear)}
          </p>
        </div>
      </div>
      <p className="font-story text-lg leading-relaxed text-foreground/90">{life.description}</p>
      <p className="text-sm text-muted-foreground">
        A typical life of the period, illustrated rather than documented: not a real person.
      </p>
      {around.length > 0 && year !== undefined && (
        <section className="grid gap-3">
          <h3 className="text-xs tracking-widest text-muted-foreground uppercase">Alive at the same time, {formatYear(year)}</h3>
          <div className="flex flex-wrap gap-2">
            {around.map((person) => <PersonChip key={person.slug} person={person} year={year} />)}
          </div>
        </section>
      )}
    </article>
  );
}

/** A typical life as a chip. It has no page of its own, so it opens its details in a panel. */
export function LifeChip({ life, around, year }: { life: Life } & AroundProps) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={cn(chipClass, "border-dashed hover:border-lamp")}>
        <LifeMark size="sm" />
        {life.label}
        <span className="font-mono text-xs text-muted-foreground">typical life</span>
      </button>
      <DetailPanel open={open} onOpenChange={setOpen} kicker="Everyday life">
        <LifeDetails life={life} around={around} year={year} />
      </DetailPanel>
    </>
  );
}
