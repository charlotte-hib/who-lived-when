import { skipYearZero } from "@/lib/years";

/** How many years before today the log curve starts to flatten. Smaller values give recent years more room. */
const RECENT_YEARS = 150;

export type TimeScale = {
  start: number;
  end: number;
  /** Position of a year on the dial, from 0 at the start to 1 at the end. */
  toFraction: (year: number) => number;
  /** The whole year at a position on the dial. Never returns 0. */
  toYear: (fraction: number) => number;
};

/**
 * A scale on log(years before the end), so recent, better documented centuries get more room
 * than distant ones: 3,000 years of antiquity fit in the space of a few modern centuries.
 */
export function createTimeScale(start: number, end: number): TimeScale {
  const log = (year: number) => Math.log(end - year + RECENT_YEARS);
  const logStart = log(start);
  const logRange = logStart - log(end);

  const toFraction = (year: number) => Math.min(1, Math.max(0, (logStart - log(year)) / logRange));
  const toYear = (fraction: number) => {
    const exact = end + RECENT_YEARS - Math.exp(logStart - fraction * logRange);
    return skipYearZero(Math.round(exact), exact < 0 ? -1 : 1);
  };

  return { start, end, toFraction, toYear };
}
