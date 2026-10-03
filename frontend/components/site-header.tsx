import Link from "next/link";
import { SearchBox } from "@/components/search-box";
import { cn } from "@/lib/utils";

/** Brand and search. Floats over the artwork on pages that open with a painting. */
export function SiteHeader({ overlay = false }: { overlay?: boolean }) {
  return (
    <header className={cn("z-10 w-full", overlay ? "absolute top-0" : "relative border-b")}>
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-4">
        <Link href="/" className="text-sm whitespace-nowrap text-muted-foreground hover:text-foreground">
          <b className="font-semibold text-foreground">Who lived when</b>
          <span className="max-sm:hidden"> · step into a moment</span>
        </Link>
        <SearchBox />
      </div>
    </header>
  );
}
