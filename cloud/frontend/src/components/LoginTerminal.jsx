import React, { useState } from 'react';
import { ShieldAlert, KeyRound, AlertTriangle, Lock } from 'lucide-react';

export default function LoginTerminal({ onAuthenticate, authError, isAuthenticating }) {
  const [tokenInput, setTokenInput] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!tokenInput.trim()) return;
    onAuthenticate(tokenInput.trim());
  };

  return (
    <div className="h-screen w-screen bg-[#0b0f19] text-[#f8fafc] flex flex-col items-center justify-center p-4 font-sans select-none">
      {/* Container Frame */}
      <div className="w-full max-w-md bg-[#111827] border border-[#334155] rounded-lg shadow-xl p-6 relative">
        {/* Brand Banner */}
        <div className="flex items-center gap-3 mb-4 border-b border-[#334155] pb-4">
          <div className="p-2.5 bg-[#1e293b] border border-[#475569] rounded">
            <ShieldAlert className="w-6 h-6 text-[#dc2626]" />
          </div>
          <div>
            <h1 className="font-bold text-base text-[#f8fafc] tracking-wide uppercase">
              MESHLINK COMMAND TERMINAL
            </h1>
            <p className="text-xs text-[#94a3b8] font-medium">
              EMERGENCY OPERATIONS // DISPATCH CONSOLE
            </p>
          </div>
        </div>

        {/* Warning Prose */}
        <p className="text-xs text-[#94a3b8] mb-5 leading-relaxed">
          Access restricted to authorized incident dispatchers and disaster response coordinators. Enter operator security key to initialize live radar.
        </p>

        {/* Error Banner */}
        {authError && (
          <div
            data-testid="auth-error"
            className="mb-4 p-3 bg-[#450a0a] border border-[#dc2626] text-[#fecaca] text-xs rounded flex items-start gap-2.5"
          >
            <AlertTriangle className="w-4 h-4 text-[#ef4444] shrink-0 mt-0.5" />
            <div className="space-y-0.5">
              <span className="font-bold block uppercase tracking-wider">Authentication Rejected</span>
              <span className="text-[11px] block text-[#fca5a5]">{authError}</span>
            </div>
          </div>
        )}

        {/* Token Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-[#cbd5e1] uppercase mb-1.5 flex items-center gap-1.5">
              <KeyRound className="w-3.5 h-3.5 text-[#38bdf8]" />
              <span>Operator Credential / Token</span>
            </label>
            <input
              data-testid="token-input"
              type="password"
              autoComplete="current-password"
              value={tokenInput}
              onChange={(e) => setTokenInput(e.target.value)}
              placeholder="Enter operator token or password..."
              className="w-full bg-[#0b0f19] border border-[#334155] rounded text-[#f8fafc] p-2.5 text-xs focus:border-[#38bdf8] focus:ring-1 focus:ring-[#38bdf8] outline-none transition-all font-mono"
            />
          </div>

          <button
            data-testid="auth-submit"
            type="submit"
            disabled={isAuthenticating || !tokenInput.trim()}
            className={`w-full py-2.5 rounded font-semibold text-xs uppercase tracking-wider flex items-center justify-center gap-2 transition-all ${
              isAuthenticating || !tokenInput.trim()
                ? 'bg-[#1e293b] text-[#64748b] border border-[#334155] cursor-not-allowed'
                : 'bg-[#2563eb] hover:bg-[#1d4ed8] text-white border border-[#3b82f6] shadow'
            }`}
          >
            <Lock className="w-4 h-4" />
            <span>{isAuthenticating ? 'VERIFYING CREDENTIALS...' : 'SIGN IN TO DISPATCH CONSOLE'}</span>
          </button>
        </form>
      </div>
    </div>
  );
}
