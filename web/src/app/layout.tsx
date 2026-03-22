import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Call Transcriber",
  description: "Persoenliches Konto fuer Gespraeche und Transkripte",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="de">
      <body>{children}</body>
    </html>
  );
}
