"use client";

import { Slider } from "@base-ui/react/slider";
import { Play } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { DoorCard } from "@/components/door-card";
import { LifeChip, PersonChip } from "@/components/person-chip";
import { WorldAround } from "@/components/world-around";
import { fetchAliveElsewhere } from "@/lib/api-browser";
import { DOMAIN_ORDER, DOMAINS } from "@/lib/domains";
import { hasStory, titleOf } from "@/lib/moments";
import type { Era, MomentDetail, Person } from "@/lib/types";
import { CURRENT_YEAR, ageLabel, formatYear } from "@/lib/years";

const DEBOUNCE_MS = 200;

const isAlive = (person: Person, year: number) => person.birthYear <= year && year <= (person.deathYear ?? CURRENT_YEAR);

/** Consecutive eras share their boundary year; the one that starts later wins it. */
const eraAt = (eras: Era[], year: number) => eras.filter((era) => era.startYear <= year && year <= era.endYear).at(-1);

/** People alive elsewhere in [year], fetched shortly after the year stops changing. */
function useAliveElsewhere(region: string, year: number) {
  const [people, setPeople] = useState<Person[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetchAliveElsewhere(region, year, controller.signal).then(setPeople).catch(() => { /* aborted or offline */ });
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [region, year]);
  return people;
}

/**
 * One place over a few years, and everyone in it. A slider moves through the years; ages, who governs,
 * and who is alive elsewhere follow it.
 */
export function MomentExplorer({ detail }: { detail: MomentDetail }) {
  const { moment, world, eras, people, lives, events, doors } = detail;
  const [year, setYear] = useState(moment.focusYear);
  const elsewhere = useAliveElsewhere(moment.regionCode, year);

  const here = useMemo(() => people.filter((person) => isAlive(person, year)), [people, year]);
  const livesHere = lives.filter((life) => life.startYear <= year && year <= life.endYear);
  const era = eraAt(eras, year);

  const oldest = here.reduce<Person | null>((a, b) => (!a || b.birthYear < a.birthYear ? b : a), null);
  const youngest = here.reduce<Person | null>((a, b) => (!a || b.birthYear > a.birthYear ? b : a), null);

  return (
    <div className="grid gap-10">
      <section aria-label="Year" className="grid gap-3">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <p className="font-display text-7xl leading-none font-extrabold text-lamp tabular-nums sm:text-8xl" aria-live="polite">
            {formatYear(year)}
          </p>
          {era && (
            <p className="pb-2 text-muted-foreground">
              Who governs:{" "}
              <Link href={`/era/${era.id}`} className="text-foreground hover:underline">
                {era.label}
              </Link>
            </p>
          )}
        </div>
        <Slider.Root value={year} onValueChange={setYear} min={moment.startYear} max={moment.endYear} step={1}>
          <Slider.Control className="flex h-6 cursor-ew-resize touch-none items-center select-none">
            <Slider.Track className="h-1 w-full rounded-full bg-muted">
              <Slider.Indicator className="rounded-full bg-lamp" />
              <Slider.Thumb aria-label="Year" getAriaValueText={() => formatYear(year)} className="size-5 rounded-full border-4 border-background bg-lamp shadow outline-none has-[:focus-visible]:ring-[3px] has-[:focus-visible]:ring-ring/50" />
            </Slider.Track>
          </Slider.Control>
        </Slider.Root>
        <div className="flex justify-between font-mono text-xs text-muted-foreground">
          <span>{formatYear(moment.startYear)}</span>
          <span>Drag to move through the moment</span>
          <span>{formatYear(moment.endYear)}</span>
        </div>
        {oldest && youngest && oldest !== youngest && (
          <p className="font-story text-xl">
            In {formatYear(year)}, {oldest.name} is {ageLabel(oldest, year)} and {youngest.name} is {ageLabel(youngest, year)}.
          </p>
        )}
      </section>

      {world && (
        <section aria-labelledby="world-title" className="rounded-2xl border bg-card/40 p-5">
          <h2 id="world-title" className="mb-4 text-xs tracking-widest text-muted-foreground uppercase">
            The world around · {titleOf(moment)}
          </h2>
          <WorldAround
            world={world}
            meanwhile={elsewhere.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {elsewhere.map((person) => <PersonChip key={person.slug} person={person} year={year} showPlace />)}
              </div>
            )}
          />
        </section>
      )}

      <section aria-label="People" className="divide-y border-y">
        {DOMAIN_ORDER.map((domain) => {
          const group = here.filter((person) => person.domain === domain);
          const groupLives = domain === "EVERYDAY" ? livesHere : [];
          const count = group.length + groupLives.length;
          return (
            <div key={domain} className="grid gap-3 py-4 sm:grid-cols-[11rem_1fr]">
              <h3 className="text-sm font-semibold">
                {DOMAINS[domain].title}
                <span className="block font-mono text-xs font-normal text-muted-foreground">
                  {count} here in {formatYear(year)}
                </span>
              </h3>
              <div className="flex flex-wrap gap-2">
                {group.map((person) => <PersonChip key={person.slug} person={person} year={year} />)}
                {groupLives.map((life) => <LifeChip key={life.id} life={life} />)}
                {count === 0 && <p className="py-1 text-sm text-muted-foreground">Nobody in this group yet.</p>}
              </div>
            </div>
          );
        })}
      </section>

      {events.length > 0 && (
        <section aria-labelledby="events-title" className="grid max-w-3xl gap-3">
          <h2 id="events-title" className="text-xs tracking-widest text-muted-foreground uppercase">Documented in this moment</h2>
          {events.map((event) => (
            <article key={event.id} className="border-l-2 border-arts pl-3">
              <h3 className="font-medium">
                {formatYear(event.year)} · {event.title}
              </h3>
              <p className="text-sm text-muted-foreground">{event.description}</p>
              <a href={event.sourceUrl} target="_blank" rel="noreferrer" className="text-xs text-muted-foreground underline">
                Source
              </a>
            </article>
          ))}
        </section>
      )}

      <section aria-labelledby="next-title" className="grid gap-3">
        <h2 id="next-title" className="text-xs tracking-widest text-muted-foreground uppercase">Where next?</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {hasStory(moment) && (
            <Link href={`/moment/${moment.id}/story`} className="flex min-h-40 flex-col justify-end gap-1 rounded-2xl border border-lamp bg-lamp/10 p-4 hover:bg-lamp/20">
              <span className="text-[11px] font-medium tracking-widest text-lamp uppercase">The story</span>
              <span className="flex items-center gap-2 font-story text-xl font-medium">
                <Play className="size-5" /> Play {titleOf(moment)}
              </span>
              <span className="text-sm text-muted-foreground">{moment.storyCards} cards</span>
            </Link>
          )}
          {doors.map((door) => <DoorCard key={door.target.id} door={door} />)}
        </div>
      </section>
    </div>
  );
}
