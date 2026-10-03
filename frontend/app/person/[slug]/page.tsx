import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import { DoorCard } from "@/components/door-card";
import { LifeInTime } from "@/components/life-in-time";
import { LifeChip } from "@/components/life";
import { PersonChip } from "@/components/person-chip";
import { PersonAvatar } from "@/components/person-avatar";
import { SiteHeader } from "@/components/site-header";
import { Badge } from "@/components/ui/badge";
import { WorldAround } from "@/components/world-around";
import { getPerson } from "@/lib/api";
import { DOMAINS } from "@/lib/domains";
import { hasStory } from "@/lib/moments";
import { cn } from "@/lib/utils";
import { formatLifespan, formatYear } from "@/lib/years";

function parseYear(value: string | string[] | undefined) {
  const year = Number(Array.isArray(value) ? value[0] : value);
  return Number.isInteger(year) && year !== 0 ? year : undefined;
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid gap-3">
      <h2 className="text-xs tracking-widest text-muted-foreground uppercase">{title}</h2>
      {children}
    </section>
  );
}

/** A person in the world around them: who they were, what was happening, and who else was alive. */
export default async function PersonPage({ params, searchParams }: PageProps<"/person/[slug]">) {
  const { slug } = await params;
  const detail = await getPerson(slug, parseYear((await searchParams).year));
  if (!detail) notFound();

  const { person, year } = detail;
  const domain = DOMAINS[person.domain];

  return (
    <>
      <SiteHeader />
      <main className="mx-auto grid w-full max-w-3xl gap-10 px-4 py-10">
        <header className="flex items-center gap-5">
          <PersonAvatar person={person} className="size-28 ring-4" />
          <div className="grid gap-1">
            <h1 className="font-story text-4xl leading-tight font-medium">{person.name}</h1>
            <p className="text-sm text-muted-foreground">
              {formatLifespan(person.birthYear, person.deathYear)} · {person.occupation} · {person.region}
            </p>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="secondary" className={cn(domain.soft, domain.text)}>{domain.title}</Badge>
              <span className="font-mono text-xs text-lamp">
                {detail.age} in {formatYear(year)}
              </span>
            </div>
          </div>
        </header>

        {(detail.about ?? person.bioShort) && (
          <div>
            <p className="font-story text-lg leading-relaxed text-foreground/90">{detail.about ?? person.bioShort}</p>
            {detail.wikipediaUrl && (
              <a href={detail.wikipediaUrl} target="_blank" rel="noreferrer" className="mt-2 inline-block text-xs text-muted-foreground underline">
                Read more on Wikipedia
              </a>
            )}
          </div>
        )}

        {detail.world && detail.worldMoment && (
          <Section title={`The world around them · ${detail.worldMoment.place}, ${detail.worldMoment.period}`}>
            <div className="rounded-2xl border bg-card/40 p-5">
              <WorldAround world={detail.world} compact />
            </div>
          </Section>
        )}

        <Section title="Their life in their time">
          <LifeInTime lines={detail.lifeline} />
        </Section>

        {(detail.aroundPeople.length > 0 || detail.aroundLives.length > 0) && (
          <Section title={`Around them in ${person.region}, ${formatYear(year)}`}>
            <div className="flex flex-wrap gap-2">
              {detail.aroundPeople.map((other) => <PersonChip key={other.slug} person={other} year={year} />)}
              {detail.aroundLives.map((life) => <LifeChip key={life.id} life={life} around={detail.aroundPeople} year={year} />)}
            </div>
          </Section>
        )}

        {detail.elsewhere.length > 0 && (
          <Section title={`Meanwhile, elsewhere in ${formatYear(year)}`}>
            <div className="flex flex-wrap gap-2">
              {detail.elsewhere.map((other) => <PersonChip key={other.slug} person={other} year={year} showPlace />)}
            </div>
          </Section>
        )}

        {detail.moments.length > 0 && (
          <Section title="Step into their time">
            <div className="grid gap-3 sm:grid-cols-2">
              {detail.moments.map((moment) => (
                <DoorCard
                  key={moment.id}
                  door={{ kind: hasStory(moment) ? "Story" : "Moment", text: moment.hook, target: moment, faces: [] }}
                />
              ))}
            </div>
          </Section>
        )}
      </main>
    </>
  );
}
