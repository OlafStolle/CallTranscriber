"use client";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, Suspense } from "react";

function SearchBarInner() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [query, setQuery] = useState(searchParams.get("q") || "");
  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    const params = new URLSearchParams();
    if (query) params.set("q", query);
    router.push(`/calls?${params.toString()}`);
  }
  return (
    <form onSubmit={handleSearch} className="flex gap-2">
      <input type="text" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Transkripte durchsuchen..." className="flex-1 px-3 py-2 border rounded-md" />
      <button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">Suchen</button>
    </form>
  );
}

export function SearchBar() {
  return <Suspense fallback={<div className="h-10" />}><SearchBarInner /></Suspense>;
}
