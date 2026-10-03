import { PersonPanel } from "@/components/person-panel";

/** The panel outlives the person in it, so moving from one person to another, or from loading to loaded, keeps it open. */
export default function PersonPanelLayout({ children }: LayoutProps<"/person">) {
  return <PersonPanel kicker="Someone who lived it">{children}</PersonPanel>;
}
