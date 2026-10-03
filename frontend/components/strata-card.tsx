import Link from "next/link";
import { PersonAvatar } from "@/components/person-avatar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { Domain, Person } from "@/lib/types";
import { DOMAINS } from "@/lib/domains";

type Props = { domain: Domain; people: Person[] };

/** One social stratum of an era, with its people as chips. */
export function StrataCard({ domain, people }: Props) {
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle className="text-xs font-normal uppercase tracking-wider text-muted-foreground">
          {DOMAINS[domain].title}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2">
        {people.map((person) => (
          <Link
            key={person.slug}
            href={`/person/${person.slug}`}
            title={person.bioShort ?? person.occupation}
            className="inline-flex items-center gap-2 rounded-full border bg-background py-1 pr-3 pl-1 text-sm hover:bg-muted"
          >
            <PersonAvatar person={person} className="size-6" />
            {person.name}
          </Link>
        ))}
      </CardContent>
    </Card>
  );
}
