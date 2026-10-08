"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { type KeyboardEvent, type ReactNode, useEffect, useId, useRef, useState } from "react";
import { PersonAvatar } from "@/components/person-avatar";
import { openingFromSearch, track } from "@/lib/analytics";
import { fetchSearch } from "@/lib/api-browser";
import { momentHref, titleOf } from "@/lib/moments";
import type { SearchResults } from "@/lib/types";
import { cn } from "@/lib/utils";
import { formatLifespan } from "@/lib/years";

const DEBOUNCE_MS = 150;

type Option = { key: string; kind: "person" | "moment"; href: string; title: string; detail: string; thumbnail: ReactNode };
const EMPTY: SearchResults = { people: [], moments: [] };

/** Search people and moments; picking one goes straight to it. */
export function SearchBox({ className }: { className?: string }) {
  const router = useRouter();
  const listId = useId();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState(EMPTY);
  const [active, setActive] = useState(0);
  // One search is counted from its first results until the box is cleared, however many letters are typed.
  const searched = useRef(false);

  useEffect(() => {
    if (query.trim().length < 2) {
      searched.current = false;
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      fetchSearch(query, controller.signal).then((next) => {
        if (!searched.current) track({ name: "search_used" });
        searched.current = true;
        setResults(next);
        setActive(0);
      }).catch(() => { /* aborted or offline: keep the previous results */ });
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  const options: Option[] = query.trim().length < 2 ? [] : [
    ...results.moments.map((moment) => ({
      key: `m-${moment.id}`,
      kind: "moment" as const,
      href: momentHref(moment),
      title: titleOf(moment),
      detail: `${moment.storyCards > 0 ? "Story" : "Moment"} · ${moment.region}`,
      thumbnail: (
        <span aria-hidden className="size-7 rounded-md bg-muted bg-cover bg-center" style={{ backgroundImage: moment.art ? `url("${moment.art.url}")` : undefined }} />
      ),
    })),
    ...results.people.map((person) => ({
      key: `p-${person.slug}`,
      kind: "person" as const,
      href: `/person/${person.slug}`,
      title: person.name,
      detail: `${formatLifespan(person.birthYear, person.deathYear)} · ${person.region}`,
      thumbnail: <PersonAvatar person={person} className="size-7" />,
    })),
  ];

  const go = ({ kind, href }: Option) => {
    track({ name: "search_result_opened", kind });
    if (kind === "person") openingFromSearch();
    setQuery("");
    setResults(EMPTY);
    router.push(href);
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (!options.length) return;
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      setActive((current) => (current + (event.key === "ArrowDown" ? 1 : -1) + options.length) % options.length);
    } else if (event.key === "Enter") {
      go(options[active]);
    } else if (event.key === "Escape") {
      setQuery("");
    }
  };

  return (
    <div className={cn("relative w-full max-w-xs", className)}>
      <input
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={onKeyDown}
        placeholder="Search a name you know…"
        aria-label="Search people and moments"
        role="combobox"
        aria-expanded={options.length > 0}
        aria-controls={listId}
        // 16px on touch screens: below that, Safari on iPhone zooms the page in when the box gets focus.
        className="w-full rounded-full border bg-background/60 py-2 pr-4 pl-9 text-sm backdrop-blur placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring/60 focus-visible:outline-none pointer-coarse:text-base"
      />
      {/* After the input, which its backdrop blur would otherwise paint over. */}
      <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
      {options.length > 0 && (
        <ul id={listId} role="listbox" className="absolute inset-x-0 top-full z-20 mt-2 max-h-[60vh] overflow-y-auto rounded-xl border bg-popover p-1 shadow-xl">
          {options.map((option, index) => (
            <li key={option.key} role="option" aria-selected={index === active}>
              <button
                type="button"
                onMouseEnter={() => setActive(index)}
                onClick={() => go(option)}
                className={cn("flex w-full items-center gap-3 rounded-lg px-2 py-1.5 text-left text-sm", index === active && "bg-muted")}
              >
                {option.thumbnail}
                <span>
                  {option.title}
                  <span className="block text-xs text-muted-foreground">{option.detail}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
