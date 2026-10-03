// Shapes of the JSON returned by the Spring Boot API.

export type Domain = "POWER" | "ARTS" | "EVERYDAY";

export type Region = { code: string; name: string };

/** A region at a time span, named after who governed it. Both years are inclusive. */
export type Era = {
  id: string;
  label: string;
  regionCode: string;
  region: string;
  governedBy: string;
  startYear: number;
  endYear: number;
};

export type Person = {
  slug: string;
  name: string;
  birthYear: number;
  deathYear: number | null;
  datesApproximate: boolean;
  regionCode: string;
  region: string;
  domain: Domain;
  occupation: string;
  bioShort: string | null;
  portraitUrl: string | null;
};

/** A typical existence in a period, shown as representative rather than as a real person. */
export type Life = {
  id: string;
  label: string;
  description: string;
  startYear: number;
  endYear: number;
};

/** Someone in an event, with their part in it, e.g. "author". */
export type Participant = { person: Person; role: string };

export type Event = {
  id: string;
  title: string;
  year: number;
  description: string;
  sourceUrl: string;
  participants: Participant[];
};

export type EraDetail = {
  era: Era;
  people: Partial<Record<Domain, Person[]>>;
  events: Event[];
};

export type Artwork = { url: string; credit: string; sourceUrl: string };

/** The world around a moment: one line per domain, and what happens elsewhere at the same time. */
export type WorldAround = { governs: string; everyday: string; arts: string; meanwhile: string };

/** A place over a few years, small enough to tell as one story. */
export type MomentSummary = {
  id: string;
  regionCode: string;
  region: string;
  place: string;
  period: string;
  startYear: number;
  endYear: number;
  focusYear: number;
  hook: string;
  art: Artwork | null;
  governedBy: string | null;
  /** Number of cards in the story; 0 when no story is written yet. */
  storyCards: number;
  cast: Person[];
  featured: boolean;
};

export type Door = { kind: string; text: string; target: MomentSummary; faces: Person[] };

export type MomentDetail = {
  moment: MomentSummary;
  world: WorldAround | null;
  eras: Era[];
  people: Person[];
  lives: Life[];
  events: Event[];
  doors: Door[];
};

export type StoryCard = {
  type: "SCENE" | "PERSON" | "LIFE" | "EVENT";
  kicker: string | null;
  title: string | null;
  text: string | null;
  year: number | null;
  person: Person | null;
  life: Life | null;
  event: Event | null;
  art: Artwork | null;
};

export type Story = { moment: MomentSummary; cards: StoryCard[]; doors: Door[] };

export type LifeLine = {
  year: number;
  age: number | null;
  kind: "BIRTH" | "ERA" | "EVENT" | "OWN_EVENT" | "DEATH";
  text: string;
  role: string | null;
};

export type PersonDetail = {
  person: Person;
  about: string | null;
  wikipediaUrl: string | null;
  /** The year the page looks at them in. */
  year: number;
  age: number;
  world: WorldAround | null;
  worldMoment: MomentSummary | null;
  lifeline: LifeLine[];
  aroundPeople: Person[];
  aroundLives: Life[];
  elsewhere: Person[];
  moments: MomentSummary[];
};

export type SearchResults = { people: Person[]; moments: MomentSummary[] };
