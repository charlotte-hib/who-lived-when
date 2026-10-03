import Link from "next/link";
import { PersonAvatar } from "@/components/person-avatar";
import type { Person } from "@/lib/types";
import { cn } from "@/lib/utils";
import { ageLabel } from "@/lib/years";

export const chipClass = "inline-flex items-center gap-2 rounded-full border py-1 pr-3 pl-1 text-sm whitespace-nowrap";

type PersonChipProps = {
  person: Person;
  /** The year their age is shown for; also the year their page opens on. */
  year: number;
  /** Add the place after the age, for people from elsewhere. */
  showPlace?: boolean;
};

export function PersonChip({ person, year, showPlace }: PersonChipProps) {
  return (
    <Link href={`/person/${person.slug}?year=${year}`} title={person.occupation} className={cn(chipClass, "hover:border-lamp")}>
      <PersonAvatar person={person} className="size-7" />
      {person.name}
      <span className="font-mono text-xs text-muted-foreground tabular-nums">
        {ageLabel(person, year)}
        {showPlace && ` · ${person.region}`}
      </span>
    </Link>
  );
}
