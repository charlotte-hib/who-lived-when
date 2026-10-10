import { EraButton } from "@/components/era-panel";
import type { LifeLine } from "@/lib/types";
import { cn } from "@/lib/utils";
import { formatYear } from "@/lib/years";

const TEXT_STYLE: Record<LifeLine["kind"], string> = {
  BIRTH: "text-foreground",
  ERA: "italic text-muted-foreground",
  EVENT: "text-muted-foreground",
  OWN_EVENT: "font-medium text-foreground",
  DEATH: "text-foreground",
};

/**
 * "Their life in their time": each change around them, with their age. Built by the API from dated records.
 * The regime they were born under and each new one open in a panel.
 */
export function LifeInTime({ lines }: { lines: LifeLine[] }) {
  return (
    <div>
      <div aria-hidden className="grid grid-cols-[4rem_2.5rem_1fr] gap-2 pb-1 font-mono text-[11px] tracking-wider text-muted-foreground uppercase">
        <span>Year</span>
        <span>Age</span>
      </div>
      <ol className="divide-y divide-border/60">
        {lines.map((line, index) => (
          <li key={index} className="grid grid-cols-[4rem_2.5rem_1fr] gap-2 py-2 text-sm">
            <span className="pt-px font-mono text-xs text-muted-foreground tabular-nums">{formatYear(line.year)}</span>
            <span className="pt-px font-mono text-xs text-lamp tabular-nums">
              {line.age ?? ""}
              {line.age !== null && <span className="sr-only"> years old</span>}
            </span>
            <span className={cn(TEXT_STYLE[line.kind])}>
              {line.era ? <EraButton era={line.era} className="text-left text-inherit">{line.text}</EraButton> : line.text}
              {line.role && <span className="block text-xs font-normal text-arts">{line.role}</span>}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
