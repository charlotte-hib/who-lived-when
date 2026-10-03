import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import type { Person } from "@/lib/types";
import { DOMAINS } from "@/lib/domains";
import { cn } from "@/lib/utils";

const initials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0])
    .slice(0, 2)
    .join("");

type Props = {
  person: Pick<Person, "name" | "domain" | "portraitUrl">;
  className?: string;
};

export function PersonAvatar({ person, className }: Props) {
  return (
    <Avatar className={cn("ring-2", DOMAINS[person.domain].ring, className)}>
      {person.portraitUrl && <AvatarImage src={person.portraitUrl} alt={person.name} />}
      <AvatarFallback className={cn(DOMAINS[person.domain].soft, DOMAINS[person.domain].text)}>
        {initials(person.name)}
      </AvatarFallback>
    </Avatar>
  );
}
