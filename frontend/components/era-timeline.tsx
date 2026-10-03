"use client";

import { AxisBottom } from "@visx/axis";
import { useParentSize } from "@visx/responsive";
import { scaleLinear } from "@visx/scale";
import { motion } from "motion/react";
import Link from "next/link";
import { useMemo } from "react";
import { PersonAvatar } from "@/components/person-avatar";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { Era, Event, Person } from "@/lib/types";
import { DOMAINS } from "@/lib/domains";
import { cn } from "@/lib/utils";
import { formatLifespan, formatYear } from "@/lib/years";

const ROW_HEIGHT = 64;
const AXIS_HEIGHT = 32;
const AXIS_GAP_PX = 12;
const MARGIN_PX = 24;
const PADDING_YEARS = 5;
const CURRENT_YEAR = new Date().getFullYear();

type YearScale = ReturnType<typeof scaleLinear<number>>;

const endYear = (person: Person) => person.deathYear ?? CURRENT_YEAR;

type Props = { era: Era; people: Person[]; events: Event[] };

/** Lifespans of everyone in an era on one shared axis, with documented events pinned on them. */
export function EraTimeline({ era, people, events }: Props) {
  const { parentRef, width } = useParentSize({ debounceTime: 50 });

  const rows = useMemo(() => [...people].sort((a, b) => a.birthYear - b.birthYear), [people]);

  const x = useMemo(
    () =>
      scaleLinear<number>({
        domain: [
          Math.min(era.startYear, ...rows.map((p) => p.birthYear)) - PADDING_YEARS,
          Math.max(era.endYear, ...rows.map(endYear)) + PADDING_YEARS,
        ],
        range: [MARGIN_PX, Math.max(width - MARGIN_PX, MARGIN_PX)],
      }),
    [era, rows, width],
  );

  const rowOf = (slug: string) => rows.findIndex((p) => p.slug === slug);

  return (
    <div ref={parentRef} className="w-full">
      {width > 0 && (
        <>
          <div className="relative" style={{ height: rows.length * ROW_HEIGHT + AXIS_GAP_PX }}>
            <EraBand era={era} x={x} />
            {events.map((event) => (
              <EventLink key={event.id} event={event} x={x} rows={event.participants.map((p) => rowOf(p.slug))} />
            ))}
            {rows.map((person, index) => (
              <LifespanRow
                key={person.slug}
                person={person}
                index={index}
                x={x}
                events={events.filter((e) => e.participants.some((p) => p.slug === person.slug))}
              />
            ))}
          </div>
          <svg width={width} height={AXIS_HEIGHT} className="overflow-visible text-muted-foreground">
            <AxisBottom
              scale={x}
              numTicks={Math.floor(width / 90)}
              tickFormat={(year) => formatYear(Number(year))}
              stroke="currentColor"
              tickStroke="currentColor"
              tickLabelProps={{ fill: "currentColor", fontSize: 11, textAnchor: "middle" }}
            />
          </svg>
        </>
      )}
    </div>
  );
}

function EraBand({ era, x }: { era: Era; x: YearScale }) {
  return (
    <div
      className="absolute inset-y-0 rounded-md bg-muted"
      style={{ left: x(era.startYear), width: x(era.endYear) - x(era.startYear) }}
    >
      <span className="absolute -top-5 left-1 text-xs text-muted-foreground tabular-nums">
        {formatYear(era.startYear)}–{formatYear(era.endYear)}
      </span>
    </div>
  );
}

/** Dashed line tying together the participants of one event. */
function EventLink({ event, x, rows }: { event: Event; x: YearScale; rows: number[] }) {
  const first = Math.min(...rows);
  const last = Math.max(...rows);
  return (
    <div
      className="absolute border-l border-dashed border-arts/60"
      style={{ left: x(event.year), top: (first + 1) * ROW_HEIGHT - 7, height: (last - first) * ROW_HEIGHT }}
    />
  );
}

type RowProps = { person: Person; index: number; x: YearScale; events: Event[] };

function LifespanRow({ person, index, x, events }: RowProps) {
  const start = x(person.birthYear);
  const end = x(endYear(person));

  return (
    <div className="absolute inset-x-0" style={{ top: index * ROW_HEIGHT, height: ROW_HEIGHT }}>
      <Link
        href={`/person/${person.slug}`}
        className="group absolute bottom-3 flex items-center gap-2"
        style={{ left: start }}
      >
        <PersonAvatar person={person} className="size-8" />
        <div className="leading-tight">
          <div className="text-sm font-medium group-hover:underline">{person.name}</div>
          <div className="text-xs text-muted-foreground tabular-nums">
            {formatLifespan(person.birthYear, person.deathYear)}
          </div>
        </div>
      </Link>

      <motion.div
        className={cn("absolute bottom-1.5 h-0.5 origin-left rounded-full", DOMAINS[person.domain].fill)}
        style={{ left: start, width: end - start }}
        initial={{ scaleX: 0 }}
        animate={{ scaleX: 1 }}
        transition={{ duration: 0.6, delay: index * 0.08, ease: "easeOut" }}
      />

      {events.map((event) => (
        <Tooltip key={event.id}>
          <TooltipTrigger
            aria-label={event.title}
            className="absolute bottom-0 size-3.5 -translate-x-1/2 rounded-full border-2 border-background bg-arts"
            style={{ left: x(event.year) }}
          />
          <TooltipContent>
            {event.year} · {event.title} ({event.participants.find((p) => p.slug === person.slug)?.role})
          </TooltipContent>
        </Tooltip>
      ))}
    </div>
  );
}
