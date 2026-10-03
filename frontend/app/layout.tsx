import type { Metadata } from "next";
import { Geist, Geist_Mono, Newsreader, Sofia_Sans_Extra_Condensed } from "next/font/google";
import { TooltipProvider } from "@/components/ui/tooltip";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

// A book face for stories and descriptions.
const story = Newsreader({
  variable: "--font-newsreader",
  subsets: ["latin"],
  style: ["normal", "italic"],
});

// Condensed numerals for big years.
const display = Sofia_Sans_Extra_Condensed({
  variable: "--font-display-condensed",
  subsets: ["latin"],
  weight: ["800"],
});

export const metadata: Metadata = {
  title: "Who Lived When",
  description: "Step into a moment in history and meet the people who lived it, from kings to washerwomen.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`dark ${geistSans.variable} ${geistMono.variable} ${story.variable} ${display.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <TooltipProvider>{children}</TooltipProvider>
      </body>
    </html>
  );
}
