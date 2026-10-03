// Years are integers; negative years are BCE. There is no year 0: 1 BCE is followed by 1 CE.

export const CURRENT_YEAR = new Date().getFullYear();

export const formatYear = (year: number) => (year < 0 ? `${-year} BCE` : year < 1000 ? `${year} CE` : `${year}`);

export const formatLifespan = (birthYear: number, deathYear: number | null) =>
  `${formatYear(birthYear)}–${deathYear === null ? "" : formatYear(deathYear)}`;

/** Whole years from one year to a later one, skipping the year 0 that never existed. */
export const yearsBetween = (from: number, to: number) => to - from - (from < 0 && to > 0 ? 1 : 0);

/** A person's age in a year, as shown on chips: "c. 34" when their dates are approximate, "born" in their first year. */
export function ageLabel(person: { birthYear: number; datesApproximate: boolean }, year: number) {
  const age = yearsBetween(person.birthYear, year);
  if (age === 0) return "born";
  return person.datesApproximate ? `c. ${age}` : `${age}`;
}

/** Whether someone is alive in a year; people without a death year are alive today. */
export const isAlive = (person: { birthYear: number; deathYear: number | null }, year: number) =>
  person.birthYear <= year && year <= (person.deathYear ?? CURRENT_YEAR);

/** Moves to the neighbouring year when a calculation lands on the non-existent year 0. */
export const skipYearZero = (year: number, direction: 1 | -1 = 1) => (year === 0 ? direction : year);
