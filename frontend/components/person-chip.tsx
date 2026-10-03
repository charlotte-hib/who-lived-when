import Link from "next/link";
import { PersonAvatar } from "@/components/person-avatar";
import type { Life, Person } from "@/lib/types";
import { cn } from "@/lib/utils";
import { ageLabel } from "@/lib/years";

const chip = "inline-flex items-center gap-2 rounded-full border py-1 pr-3 pl-1 text-sm whitespace-nowrap";

type PersonChipProps = {
  person: Person;
  /** The year their age is shown for; also the year their page opens on. */
  year: number;
  /** Add the place after the age, for people from elsewhere. */
  showPlace?: boolean;
};

export function PersonChip({ person, year, showPlace }: PersonChipProps) {
  return (
    <Link href={`/person/${person.slug}?year=${year}`} title={person.occupation} className={cn(chip, "hover:border-lamp")}>
      <PersonAvatar person={person} className="size-7" />
      {person.name}
      <span className="font-mono text-xs text-muted-foreground tabular-nums">
        {ageLabel(person, year)}
        {showPlace && ` · ${person.region}`}
      </span>
    </Link>
  );
}

/** A typical life: illustrated, representative of the period, so it has no page of its own. */
export function LifeChip({ life }: { life: Life }) {
  return (
    <span title={`${life.description} Illustrated, representative of the period.`} className={cn(chip, "border-dashed")}>
      <span
        aria-hidden
        className="size-7 rounded-full bg-[repeating-linear-gradient(45deg,var(--color-everyday)_0_2px,transparent_2px_6px)] opacity-70"
      />
      {life.label}
      <span className="font-mono text-xs text-muted-foreground">typical life</span>
    </span>
  );
}
