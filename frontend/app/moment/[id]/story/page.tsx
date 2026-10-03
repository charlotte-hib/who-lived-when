import { notFound } from "next/navigation";
import { StoryPlayer } from "@/components/story-player";
import { getStory } from "@/lib/api";

export default async function StoryPage({ params }: PageProps<"/moment/[id]/story">) {
  const { id } = await params;
  const story = await getStory(id);
  if (!story) notFound();
  return <StoryPlayer story={story} />;
}
