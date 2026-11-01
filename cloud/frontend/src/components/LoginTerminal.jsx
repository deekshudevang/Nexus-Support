import React, { useState } from 'react';
import { ShieldAlert, KeyRound, AlertTriangle, Terminal, Lock } from 'lucide-react';

export default function LoginTerminal({ onAuthenticate, authError, isAuthenticating }) {
  const [tokenInput, setTokenInput] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!tokenInput.trim()) return;
    onAuthenticate(tokenInput.trim());
  };

  return (
    <div className="h-screen w-screen bg-[#090f15] text-[#dee2ec] flex flex-col items-center justify-center p-4 font-mono select-none">
      {/* Container Frame */}
      <div className="w-full max-w-md bg-[#171c23] border-2 border-[#30353d] shadow-2xl p-6 relative">
        {/* Corner Accents */}
        <div className="absolute top-0 left-0 w-3 h-3 border-t-2 border-l-2 border-[#ff5625]"></div>
        <div className="absolute top-0 right-0 w-3 h-3 border-t-2 border-r-2 border-[#ff5625]"></div>
        <div className="absolute bottom-0 left-0 w-3 h-3 border-b-2 border-l-2 border-[#ff5625]"></div>
        <div className="absolute bottom-0 right-0 w-3 h-3 border-b-2 border-r-2 border-[#ff5625]"></div>

        {/* Brand Banner */}
        <div className="flex items-center gap-3 mb-4 border-b border-[#30353d] pb-3">
          <div className="p-2 bg-[#541100] border border-[#ff5625]">
            <ShieldAlert className="w-6 h-6 text-[#ff5625]" />
          </div>
          <div>
            <h1 className="font-bold text-base text-[#dee2ec] tracking-wider uppercase">
              MESHLINK COMMAND TERMINAL
            </h1>
            <p className="text-[10px] text-[#00daf3]">
              TACTICAL EMERGENCY RESPONDER INTERFACE
            </p>
          </div>
        </div>

        {/* Warning Prose */}
        <p className="text-xs text-[#8b949e] mb-4 leading-relaxed font-sans">
          Access restricted to authorized incident dispatchers and disaster response coordinators. Enter operator security key to initialize live radar.
        </p>

        {/* Error Banner */}
        {authError && (
          <div
            data-testid="auth-error"
            className="mb-4 p-2.5 bg-[#541100] border border-[#ff5625] text-[#ffdad6] text-xs flex items-start gap-2"
          >
            <AlertTriangle className="w-4 h-4 text-[#ff5625] shrink-0 mt-0.5" />
            <div className="space-y-0.5">
              <span className="font-bold block">AUTHENTICATION REJECTED</span>
              <span className="text-[11px] block">{authError}</span>
            </div>
          </div>
        )}

        {/* Token Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-[11px] font-bold text-[#dee2ec] uppercase mb-1 flex items-center gap-1.5">
              <KeyRound className="w-3.5 h-3.5 text-[#00e3fd]" />
              <span>OPERATOR CREDENTIAL / TOKEN:</span>
            </label>
            <input
              data-testid="token-input"
              type="password"
              autoComplete="current-password"
              value={tokenInput}
              onChange={(e) => setTokenInput(e.target.value)}
              placeholder="e.g. op_token_valid_1"
              className="w-full bg-[#090f15] border border-[#30353d] text-[#dee2ec] p-2 text-xs focus:border-[#00e3fd] outline-none"
            />
          </div>

          <button
            data-testid="auth-submit"
            type="submit"
            disabled={isAuthenticating || !tokenInput.trim()}
            className={`w-full py-2.5 font-bold text-xs uppercase tracking-wider border flex items-center justify-center gap-2 transition-all ${
              isAuthenticating || !tokenInput.trim()
                ? 'bg-[#30353d] text-[#8b949e] border-[#30353d] cursor-not-allowed'
                : 'bg-[#ff5625] hover:bg-[#b12d00] text-[#090f15] border-[#ffb5a0] shadow-lg'
            }`}
          >
            <Lock className="w-4 h-4" />
            <span>{isAuthenticating ? 'VERIFYING CREDENTIALS...' : 'INITIALIZE COMMAND RADAR'}</span>
          </button>
        </form>

        {/* Quick Demo Helper Chips */}
        <div className="mt-4 pt-3 border-t border-[#30353d] text-[10px] text-[#8b949e]">
          <span className="block mb-1">AUTHORIZED DEMO KEYS:</span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setTokenInput('op_token_valid_1')}
              className="bg-[#090f15] hover:bg-[#252a32] text-[#9cf0ff] px-2 py-0.5 border border-[#30353d]"
            >
              op_token_valid_1
            </button>
            <button
              type="button"
              onClick={() => setTokenInput('operator-secret-123')}
              className="bg-[#090f15] hover:bg-[#252a32] text-[#9cf0ff] px-2 py-0.5 border border-[#30353d]"
            >
              operator-secret-123
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
