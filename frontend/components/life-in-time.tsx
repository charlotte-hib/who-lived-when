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

/** "Their life in their time": each change around them, with their age. Built by the API from dated records. */
export function LifeInTime({ lines }: { lines: LifeLine[] }) {
  return (
    <ol className="divide-y divide-border/60">
      {lines.map((line, index) => (
        <li key={index} className="grid grid-cols-[4rem_2.5rem_1fr] gap-2 py-2 text-sm">
          <span className="pt-px font-mono text-xs text-muted-foreground tabular-nums">{formatYear(line.year)}</span>
          <span className="pt-px font-mono text-xs text-lamp tabular-nums">{line.age ?? ""}</span>
          <span className={cn(TEXT_STYLE[line.kind])}>
            {line.text}
            {line.role && <span className="block text-xs font-normal text-arts">{line.role}</span>}
          </span>
        </li>
      ))}
    </ol>
  );
}
