import Link from "next/link";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { hasStory, momentHref, titleOf } from "@/lib/moments";
import { createTimeScale } from "@/lib/time-scale";
import type { MomentSummary } from "@/lib/types";
import { cn } from "@/lib/utils";
import { CURRENT_YEAR, formatYear } from "@/lib/years";

const scale = createTimeScale(-3000, CURRENT_YEAR);
const TICKS: [number, string, boolean?][] = [
  [-3000, "3000 BCE"], [-1000, "1000 BCE", true], [1, "1 CE"], [1000, "1000"], [1500, "1500"], [1800, "1800", true], [1900, "1900"], [CURRENT_YEAR, "Today"],
];
// Fixed precision, so server and client render identical style strings.
const left = (year: number) => `${(scale.toFraction(year) * 100).toFixed(3)}%`;

/** Where the stories sit in time: one row per place, one dot per moment. No people here, only ways in. */
export function StoryMap({ moments }: { moments: MomentSummary[] }) {
  // Places with the most moments first.
  const counts = new Map<string, { name: string; count: number }>();
  for (const m of moments) counts.set(m.regionCode, { name: m.region, count: (counts.get(m.regionCode)?.count ?? 0) + 1 });
  const regions = [...counts.entries()]
    .sort(([, a], [, b]) => b.count - a.count || a.name.localeCompare(b.name))
    .map(([code, { name }]) => [code, name] as const);

  return (
    <div>
      <div className="border-t">
        {regions.map(([code, name]) => (
          <div key={code} className="relative flex h-11 items-center border-b">
            <span className="w-20 shrink-0 text-xs font-medium text-muted-foreground">{name}</span>
            <div className="relative h-full flex-1">
              {moments.filter((m) => m.regionCode === code).map((moment) => (
                <Tooltip key={moment.id}>
                  <TooltipTrigger
                    render={<Link href={momentHref(moment)} aria-label={`${titleOf(moment)}${hasStory(moment) ? ", story" : ""}`} />}
                    style={{ left: left(moment.focusYear) }}
                    className={cn(
                      "absolute top-1/2 size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 transition-transform hover:scale-150",
                      hasStory(moment) ? "border-lamp bg-lamp shadow-[0_0_12px_var(--color-lamp)]" : "border-muted-foreground bg-background",
                    )}
                  />
                  <TooltipContent>
                    {titleOf(moment)} · {hasStory(moment) ? "story" : "explore"}
                  </TooltipContent>
                </Tooltip>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div aria-hidden className="relative ml-20 h-6 font-mono text-[10.5px] text-muted-foreground">
        {TICKS.map(([year, label, minor]) => (
          <span
            key={year}
            style={{ left: left(year) }}
            className={cn("absolute top-1.5 -translate-x-1/2 whitespace-nowrap first:translate-x-0 last:-translate-x-full", minor && "max-sm:hidden")}
          >
            {label}
          </span>
        ))}
      </div>
      <p className="sr-only">Moments from {formatYear(-3000)} to today.</p>
    </div>
  );
}
