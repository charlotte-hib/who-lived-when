import { BookOpen, Link2 } from "lucide-react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import type { Event } from "@/lib/types";

const sourceLabel = (url: string) =>
  decodeURIComponent(url.replace("https://en.wikipedia.org/wiki/", "wikipedia:"));

export function ConnectionCard({ event }: { event: Event }) {
  return (
    <Card className="border-arts/30 bg-arts/5">
      <CardHeader>
        <CardDescription className="flex items-center gap-1.5 text-xs uppercase tracking-wider text-arts">
          <Link2 className="size-3.5" />
          Documented event · {event.year}
        </CardDescription>
        <CardTitle>{event.title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <p>{event.description}</p>
        <div className="flex flex-wrap gap-1.5">
          {event.participants.map((participant) => (
            <Badge
              key={participant.slug}
              variant="outline"
              className="bg-background"
              render={<Link href={`/person/${participant.slug}`} />}
            >
              {participant.name} · {participant.role}
            </Badge>
          ))}
        </div>
      </CardContent>
      <CardFooter>
        <a
          href={event.sourceUrl}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-1.5 text-xs text-muted-foreground hover:underline"
        >
          <BookOpen className="size-3.5" />
          Source: {sourceLabel(event.sourceUrl)}
        </a>
      </CardFooter>
    </Card>
  );
}
