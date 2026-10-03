import { Landmark, type LucideIcon, Palette, Wheat } from "lucide-react";
import type { Domain } from "@/lib/types";

type DomainStyle = {
  title: string;
  /** Solid fill, for lifespan bars and markers. */
  fill: string;
  /** Soft fill, for chips and avatar fallbacks. */
  soft: string;
  text: string;
  /** Ring around portraits. */
  ring: string;
  icon: LucideIcon;
};

// Full class names, so Tailwind can see them at build time.
export const DOMAINS: Record<Domain, DomainStyle> = {
  POWER: { title: "Politics and power", fill: "bg-power", soft: "bg-power/15", text: "text-power", ring: "ring-power/70", icon: Landmark },
  ARTS: { title: "Arts and ideas", fill: "bg-arts", soft: "bg-arts/15", text: "text-arts", ring: "ring-arts/70", icon: Palette },
  EVERYDAY: { title: "Everyday life", fill: "bg-everyday", soft: "bg-everyday/15", text: "text-everyday", ring: "ring-everyday/70", icon: Wheat },
};

export const DOMAIN_ORDER: Domain[] = ["POWER", "ARTS", "EVERYDAY"];
