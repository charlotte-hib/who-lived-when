import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ConnectionCard } from "@/components/connection-card";
import { EraTimeline } from "@/components/era-timeline";
import { StrataCard } from "@/components/strata-card";
import { Card, CardContent } from "@/components/ui/card";
import { getEra } from "@/lib/api";
import { DOMAIN_ORDER } from "@/lib/domains";
import { formatYear } from "@/lib/years";

export default async function EraPage({ params }: PageProps<"/era/[id]">) {
  const { id } = await params;
  const detail = await getEra(id);
  if (!detail) notFound();

  const { era, people, events } = detail;
  const everyone = DOMAIN_ORDER.flatMap((domain) => people[domain] ?? []);

  return (
    <main className="mx-auto w-full max-w-4xl space-y-8 px-4 py-10">
      <Link href="/" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:underline">
        <ArrowLeft className="size-4" /> All stories
      </Link>

      <header className="text-center">
        <h1 className="text-3xl font-semibold">{era.label}</h1>
        <p className="text-muted-foreground">
          {era.region} · {formatYear(era.startYear)}–{formatYear(era.endYear)}
          {era.governedBy !== era.label && ` · ${era.governedBy}`}
        </p>
      </header>

      <Card>
        <CardContent className="pt-6">
          <EraTimeline era={era} people={everyone} events={events} />
        </CardContent>
      </Card>

      <section className="grid items-start gap-3 sm:grid-cols-2">
        {DOMAIN_ORDER.filter((domain) => people[domain]?.length).map((domain) => (
          <StrataCard key={domain} domain={domain} people={people[domain]!} />
        ))}
      </section>

      <section className="grid items-start gap-3 sm:grid-cols-2">
        {events.map((event) => (
          <ConnectionCard key={event.id} event={event} />
        ))}
      </section>
    </main>
  );
}
