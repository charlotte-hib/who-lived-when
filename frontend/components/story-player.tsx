"use client";

import { BookOpen, ChevronRight, X } from "lucide-react";
import { AnimatePresence, animate, motion, useMotionValue, useTransform } from "motion/react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { ArtworkImage } from "@/components/artwork-image";
import { DoorCard } from "@/components/door-card";
import { PersonAvatar } from "@/components/person-avatar";
import { buttonVariants } from "@/components/ui/button";
import { titleOf } from "@/lib/moments";
import type { Story, StoryCard } from "@/lib/types";
import { cn } from "@/lib/utils";
import { ageLabel, formatYear } from "@/lib/years";

const SWIPE_PX = 50;
const SWIPE_VELOCITY = 400;
/** How far a turned card keeps sliding while it fades out. */
const TURN_PX = 160;
/** The card follows the finger at this fraction of its travel. */
const DRAG_RATIO = 0.5;

/** `?card=` is 1-based and counts the doors as the last card, so a link or a back button returns to the same page. */
function cardIndex(param: string | null, last: number) {
  const card = Number(param);
  return Number.isInteger(card) && card >= 1 ? Math.min(card, last + 1) - 1 : 0;
}

/**
 * A moment told one card at a time over its painting. Tap or swipe to turn the page; there is no timer.
 * The last card opens doors to other moments.
 */
export function StoryPlayer({ story }: { story: Story }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { moment, cards, doors } = story;
  const [index, setIndex] = useState(() => cardIndex(searchParams.get("card"), cards.length));
  const atEnd = index === cards.length;
  const card = cards[index];
  const art = card?.art ?? moment.art;

  // The card follows a horizontal drag. A drag also ends in a click on the tap zones, which must not turn the page twice.
  const dragX = useMotionValue(0);
  const dragOpacity = useTransform(dragX, [-2 * TURN_PX, 0, 2 * TURN_PX], [0.3, 1, 0.3]);
  const dragged = useRef(false);

  const step = useCallback((by: number) => setIndex((current) => Math.min(cards.length, Math.max(0, current + by))), [cards.length]);
  const close = useCallback(() => router.push(`/moment/${moment.id}`), [router, moment.id]);
  const tap = (by: number) => {
    if (!dragged.current) step(by);
  };

  useEffect(() => {
    const url = new URL(window.location.href);
    if (index === 0) url.searchParams.delete("card");
    else url.searchParams.set("card", String(index + 1));
    window.history.replaceState(null, "", url);
  }, [index]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "ArrowRight" || event.key === " ") {
        event.preventDefault();
        step(1);
      } else if (event.key === "ArrowLeft") step(-1);
      else if (event.key === "Escape") close();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [step, close]);

  return (
    <motion.div
      className="fixed inset-0 z-50 touch-none overflow-hidden bg-black text-white select-none"
      onPointerDown={() => (dragged.current = false)}
      onPanStart={() => (dragged.current = true)}
      onPan={(_, { offset }) => {
        if (Math.abs(offset.x) > Math.abs(offset.y)) dragX.set(offset.x * DRAG_RATIO);
      }}
      onPanEnd={(_, { offset, velocity }) => {
        const by = offset.x < 0 ? 1 : -1;
        const next = Math.min(cards.length, Math.max(0, index + by));
        const swiped = Math.abs(offset.x) > Math.abs(offset.y) && (Math.abs(offset.x) > SWIPE_PX || Math.abs(velocity.x) > SWIPE_VELOCITY);
        if (swiped && next !== index) {
          setIndex(next);
          animate(dragX, -by * TURN_PX, { duration: 0.25, ease: "easeOut" });
        } else {
          animate(dragX, 0, { type: "spring", stiffness: 400, damping: 35 });
        }
      }}
    >
      <AnimatePresence initial={false}>
        {art && (
          <motion.div
            key={art.url}
            className="absolute inset-0"
            initial={{ opacity: 0, scale: 1.04 }}
            animate={{ opacity: 1, scale: 1.1 }}
            exit={{ opacity: 0 }}
            transition={{ opacity: { duration: 0.6 }, scale: { duration: 12, ease: "linear" } }}
          >
            <ArtworkImage art={art} priority />
          </motion.div>
        )}
      </AnimatePresence>
      <div className="absolute inset-0 bg-gradient-to-t from-black via-black/55 to-black/20" />

      <div className="absolute inset-x-4 top-4 z-20 flex items-start gap-3">
        <div className="flex flex-1 gap-1 pt-2" aria-hidden>
          {[...cards, null].map((_, i) => (
            <span key={i} className={cn("h-0.5 flex-1 rounded-full", i <= index ? "bg-white" : "bg-white/30")} />
          ))}
        </div>
        <button type="button" onClick={close} aria-label="Close story" className={cn(buttonVariants({ variant: "outline", size: "icon" }), "rounded-full border-white/30 bg-black/30")}>
          <X />
        </button>
      </div>
      {art && (
        <a href={art.sourceUrl} target="_blank" rel="noreferrer" className="absolute top-12 right-4 z-20 max-w-xs text-right text-[11px] text-white/60 hover:underline">
          {art.credit}
        </a>
      )}

      {!atEnd && (
        <>
          <button type="button" aria-label="Previous card" onClick={() => tap(-1)} className="absolute inset-y-0 left-0 z-10 w-[30%] cursor-w-resize" />
          <button type="button" aria-label="Next card" onClick={() => tap(1)} className="absolute inset-y-0 right-0 z-10 w-[70%] cursor-e-resize" />
        </>
      )}

      <motion.div style={{ x: dragX, opacity: dragOpacity }} className="pointer-events-none absolute inset-x-0 bottom-0 z-20 mx-auto max-w-3xl px-6 pb-10">
        <AnimatePresence mode="wait" onExitComplete={() => dragX.set(0)}>
          <motion.div
            key={index}
            className="pointer-events-auto max-h-[calc(100dvh-7rem)] touch-pan-y overflow-y-auto overscroll-contain"
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.35 }}
            aria-live="polite"
          >
            {atEnd ? (
              <EndCard>
                <div className="mt-4 grid gap-3 sm:grid-cols-3">
                  {doors.map((door) => <DoorCard key={door.target.id} door={door} />)}
                </div>
                <Link href={`/moment/${moment.id}`} className={cn(buttonVariants({ variant: "outline" }), "mt-4 rounded-full border-white/30 bg-black/30")}>
                  Meet everyone in {titleOf(moment)}
                </Link>
              </EndCard>
            ) : (
              <Card card={card} />
            )}
            {index === 0 && (
              <p className="mt-6 flex items-center gap-1 text-xs tracking-wide text-white/60">
                Tap or swipe to turn the page <ChevronRight className="size-3.5" />
              </p>
            )}
          </motion.div>
        </AnimatePresence>
      </motion.div>
    </motion.div>
  );
}

function Kicker({ children }: { children: ReactNode }) {
  return <p className="mb-2 text-xs font-medium tracking-[0.12em] text-lamp uppercase">{children}</p>;
}

const titleClass = "font-story text-3xl leading-tight font-medium sm:text-5xl";
const textClass = "max-w-2xl font-story text-lg leading-relaxed text-white/90 sm:text-xl";

function Card({ card }: { card: StoryCard }) {
  if (card.type === "SCENE") {
    return (
      <>
        <Kicker>{card.kicker}</Kicker>
        <h2 className={cn(titleClass, "mb-3")}>{card.title}</h2>
        <p className={textClass}>{card.text}</p>
      </>
    );
  }

  if (card.type === "PERSON" && card.person) {
    const { person } = card;
    return (
      <>
        <Kicker>Someone who lived it</Kicker>
        <Link href={card.year ? `/person/${person.slug}?year=${card.year}` : `/person/${person.slug}`} className="mb-4 flex items-center gap-4 hover:opacity-90">
          <PersonAvatar person={person} className="size-24 ring-4" />
          <span>
            <span className={titleClass}>{person.name}</span>
            <span className="mt-1 block text-sm text-white/70">
              {person.occupation}
              {card.year && ` · ${ageLabel(person, card.year)} in ${formatYear(card.year)}`}
            </span>
          </span>
        </Link>
        <p className={textClass}>{card.text ?? person.bioShort}</p>
      </>
    );
  }

  if (card.type === "LIFE" && card.life) {
    return (
      <>
        <Kicker>Everyday life</Kicker>
        <div className="mb-4 flex items-center gap-4">
          <span aria-hidden className="size-24 rounded-full bg-[repeating-linear-gradient(45deg,var(--color-everyday)_0_4px,transparent_4px_10px)] opacity-80 ring-4 ring-everyday/60" />
          <span>
            <span className={titleClass}>{card.life.label}</span>
            <span className="mt-1 block text-sm text-white/70">Illustrated · typical of the period</span>
          </span>
        </div>
        <p className={textClass}>{card.text ?? card.life.description}</p>
      </>
    );
  }

  if (card.type === "EVENT" && card.event) {
    const { event } = card;
    return (
      <>
        <Kicker>Documented event · {formatYear(event.year)}</Kicker>
        <h2 className={cn(titleClass, "mb-3")}>{event.title}</h2>
        <p className={textClass}>
          {event.description}
          {card.text && ` ${card.text}`}
        </p>
        {event.participants.length > 0 && (
          <ul className="mt-4 flex flex-wrap gap-2">
            {event.participants.map((participant) => (
              <li key={participant.slug}>
                <Link href={`/person/${participant.slug}?year=${event.year}`} className="rounded-full bg-white/15 px-3 py-1 text-sm hover:bg-white/25">
                  {participant.name} · {participant.role}
                </Link>
              </li>
            ))}
          </ul>
        )}
        <a href={event.sourceUrl} target="_blank" rel="noreferrer" className="mt-4 inline-flex items-center gap-1.5 text-xs text-white/70 hover:underline">
          <BookOpen className="size-3.5" /> Source: {decodeURIComponent(event.sourceUrl.replace("https://en.wikipedia.org/wiki/", "wikipedia:"))}
        </a>
      </>
    );
  }

  return null;
}

function EndCard({ children }: { children: ReactNode }) {
  return (
    <>
      <Kicker>Where next?</Kicker>
      <h2 className={titleClass}>Step through another door</h2>
      {children}
    </>
  );
}
