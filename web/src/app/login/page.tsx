"use client";
import { createClient } from "@/lib/supabase/client";
import { useRouter } from "next/navigation";
import { useState } from "react";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const supabase = createClient();

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) { setError(error.message); setLoading(false); }
    else { router.push("/calls"); }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg shadow">
        <h2 className="text-3xl font-bold text-center">Call Transcriber</h2>
        <form onSubmit={handleLogin} className="space-y-4">
          <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} className="w-full px-3 py-2 border rounded-md" required />
          <input type="password" placeholder="Passwort" value={password} onChange={(e) => setPassword(e.target.value)} className="w-full px-3 py-2 border rounded-md" required />
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <button type="submit" disabled={loading} className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
            {loading ? "Wird angemeldet..." : "Anmelden"}
          </button>
        </form>
        <p className="text-center text-sm">
          Noch kein Konto?{" "}<a href="/register" className="text-blue-600 hover:underline">Registrieren</a>
        </p>
      </div>
    </div>
  );
}
