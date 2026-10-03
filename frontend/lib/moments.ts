import type { MomentSummary } from "@/lib/types";

export const titleOf = (moment: Pick<MomentSummary, "place" | "period">) => `${moment.place}, ${moment.period}`;

export const hasStory = (moment: MomentSummary) => moment.storyCards > 0;

/** A door leads into the story when one is written, otherwise into the moment itself. */
export const momentHref = (moment: MomentSummary) =>
  hasStory(moment) ? `/moment/${moment.id}/story` : `/moment/${moment.id}`;
