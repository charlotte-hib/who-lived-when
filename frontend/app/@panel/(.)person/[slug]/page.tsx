import { notFound } from "next/navigation";
import { Connections } from "@/components/connections";
import { LifeChip } from "@/components/life";
import { LifeInTime } from "@/components/life-in-time";
import { PersonChip } from "@/components/person-chip";
import { PersonAbout, PersonHeader } from "@/components/person-header";
import { FullPageButton, TrailStep } from "@/components/person-panel";
import { Section } from "@/components/section";
import { buttonVariants } from "@/components/ui/button";
import { getPerson } from "@/lib/api";
import { cn } from "@/lib/utils";
import { formatYear, parseYear } from "@/lib/years";

/**
 * A person opened from within the site: the essentials in a panel over the current page. A shared link or
 * a reload shows the full page instead (`app/person/[slug]/page.tsx`).
 */
export default async function PersonPanelPage({ params, searchParams }: PageProps<"/person/[slug]">) {
  const { slug } = await params;
  const detail = await getPerson(slug, parseYear((await searchParams).year));
  if (!detail) notFound();

  const { person, year } = detail;

  return (
    <div className="grid gap-8">
      <TrailStep slug={person.slug} name={person.name} href={`/person/${person.slug}?year=${year}`} />
      <div className="grid gap-5">
        <PersonHeader detail={detail} />
        <PersonAbout detail={detail} />
        <FullPageButton href={`/person/${person.slug}?year=${year}`} className={cn(buttonVariants({ variant: "outline" }), "w-fit rounded-full")}>
          Open the full page
        </FullPageButton>
      </div>

      <Section title="Their life in their time">
        <LifeInTime lines={detail.lifeline} />
      </Section>

      {detail.connections.length > 0 && (
        <Section title="Connected to">
          <Connections connections={detail.connections} replace />
        </Section>
      )}

      {(detail.aroundPeople.length > 0 || detail.aroundLives.length > 0) && (
        <Section title={`Around them in ${person.region}, ${formatYear(year)}`}>
          <div className="flex flex-wrap gap-2">
            {detail.aroundPeople.map((other) => <PersonChip key={other.slug} person={other} year={year} replace />)}
            {detail.aroundLives.map((life) => <LifeChip key={life.id} life={life} around={detail.aroundPeople} year={year} />)}
          </div>
        </Section>
      )}
    </div>
  );
}
