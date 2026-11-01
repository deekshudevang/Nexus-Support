import React, { useEffect, useState } from 'react';
import {
  ShieldAlert,
  Radio,
  Lock,
  Clock,
  Volume2,
  VolumeX,
  LogOut,
  RefreshCw,
  Satellite,
  Activity,
  AlertTriangle
} from 'lucide-react';

export default function TopHUD({
  wsStatus,
  soundEnabled,
  setSoundEnabled,
  onLogout,
  onRefresh,
  activeNodesCount = 142,
  totalNodesCount = 144,
  operatorId = 'OPERATOR-01',
}) {
  const [zuluTime, setZuluTime] = useState('');

  useEffect(() => {
    const updateZulu = () => {
      const now = new Date();
      setZuluTime(now.toISOString().replace('T', ' ').substring(0, 19) + ' ZULU');
    };
    updateZulu();
    const interval = setInterval(updateZulu, 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <>
      {/* Top HUD Primary Navigation & System Bar */}
      <header className="bg-[#090f15] border-b border-[#30353d] flex justify-between items-center w-full px-3 h-12 z-50 shrink-0 select-none">
        {/* Brand Anchor & Region */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-[#ff4500]" />
            <span className="font-bold tracking-tight text-[#ffb5a0] text-sm md:text-base uppercase font-sans">
              MESHLINK COMMAND // INCIDENT DISPATCH
            </span>
          </div>
          <div className="h-4 w-px bg-[#30353d] hidden sm:block"></div>
          <span className="text-[10px] font-mono font-bold tracking-wider text-[#9cf0ff] bg-[#1b2027] px-1.5 py-0.5 border border-[#30353d] hidden md:inline-block">
            SYS-NODE // SECTOR-04 TACTICAL HUD
          </span>
        </div>

        {/* Tactical Telemetry Readouts */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Node & Sat Link Telemetry */}
          <div className="hidden lg:flex items-center gap-2 text-[#8b949e] font-mono text-xs bg-[#171c23] px-2 py-1 border border-[#30353d]">
            <span className="flex items-center gap-1 text-[#4edea3]">
              <Activity className="w-3.5 h-3.5" />
              <span>NODES: {activeNodesCount}/{totalNodesCount} ON</span>
            </span>
            <span className="text-[#30353d]">|</span>
            <span className="flex items-center gap-1 text-[#00e3fd]">
              <Satellite className="w-3.5 h-3.5" />
              <span>SAT: IRIDIUM-9</span>
            </span>
            <span className="text-[#30353d]">|</span>
            <span className="flex items-center gap-1 text-[#4edea3]">
              <Lock className="w-3 h-3" />
              <span>AES-256 GCM</span>
            </span>
          </div>

          {/* Connection Status Badge */}
          <div
            data-testid="ws-status-badge"
            className={`flex items-center gap-1.5 px-2 py-0.5 font-mono text-[11px] font-bold border ${
              wsStatus === 'OPEN'
                ? 'bg-[#003824] text-[#6ffbbe] border-[#00a572]'
                : wsStatus === 'CONNECTING'
                ? 'bg-[#3b2d00] text-[#ffddb8] border-[#874e00]'
                : 'bg-[#541100] text-[#ffdad6] border-[#ff5625]'
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full inline-block ${
                wsStatus === 'OPEN'
                  ? 'bg-[#4edea3] animate-pulse'
                  : 'bg-[#ff5625]'
              }`}
            ></span>
            <span>
              {wsStatus === 'OPEN'
                ? 'WSS LIVE'
                : wsStatus === 'CONNECTING'
                ? 'WSS SYNCING'
                : 'WSS OFFLINE'}
            </span>
          </div>

          {/* Audio Mute/Unmute */}
          <button
            onClick={() => setSoundEnabled(!soundEnabled)}
            className="p-1.5 bg-[#1b2027] hover:bg-[#252a32] text-[#dee2ec] border border-[#30353d] transition-colors"
            title={soundEnabled ? 'Mute Alert Sirens' : 'Enable Alert Sirens'}
            aria-label="Toggle siren audio"
          >
            {soundEnabled ? (
              <Volume2 className="w-4 h-4 text-[#00e3fd]" />
            ) : (
              <VolumeX className="w-4 h-4 text-[#8b949e]" />
            )}
          </button>

          {/* Manual Refresh Sync */}
          {onRefresh && (
            <button
              onClick={onRefresh}
              className="p-1.5 bg-[#1b2027] hover:bg-[#252a32] text-[#dee2ec] border border-[#30353d] transition-colors"
              title="Force Roster & Incident Sync"
              aria-label="Sync roster"
            >
              <RefreshCw className="w-4 h-4 text-[#9cf0ff]" />
            </button>
          )}

          {/* Operator Logout */}
          <button
            data-testid="logout-button"
            onClick={onLogout}
            className="flex items-center gap-1 bg-[#252a32] hover:bg-[#541100] hover:text-[#ffdad6] text-[#dee2ec] px-2 py-1 font-mono text-[11px] border border-[#30353d] transition-colors"
            title="Terminate Tactical Session"
          >
            <LogOut className="w-3.5 h-3.5 text-[#ff5625]" />
            <span className="hidden sm:inline font-bold">TERMINATE</span>
          </button>
        </div>
      </header>

      {/* Emergency DEFCON Ticker Bar */}
      <section className="bg-[#171c23] border-b border-[#30353d] px-3 py-1 flex items-center justify-between text-xs font-mono shrink-0 select-none">
        {/* DEFCON Indicator */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 px-2 py-0.5 bg-[#93000a] text-white border border-[#ff5625]">
            <span className="w-2 h-2 rounded-full bg-[#ffb4ab] animate-ping inline-block"></span>
            <span className="text-[10px] tracking-widest font-extrabold uppercase">
              DEFCON 2
            </span>
          </div>
          <span className="text-[#ffb5a0] font-semibold hidden md:inline text-xs">
            EMERGENCY INCIDENT RESPONSE PROTOCOL ACTIVE
          </span>
        </div>

        {/* High-Visibility Emergency Broadcast Ticker */}
        <div className="flex-1 mx-3 overflow-hidden bg-[#090f15] px-2 py-0.5 border border-[#30353d] flex items-center text-[11px]">
          <div className="flex items-center gap-1 text-[#ff5625] font-bold whitespace-nowrap">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>URGENT:</span>
          </div>
          <div className="overflow-hidden whitespace-nowrap w-full pl-2 text-[#dee2ec]">
            <span className="inline-block animate-pulse">
              LEVEE BREACH SECTOR 04-B // AD-HOC MESH FREQ 433.850 MHz // ENCRYPTED BEACONS ACTIVE // MAINTAIN RELAY INTEGRITY
            </span>
          </div>
        </div>

        {/* Real-time Zulu UTC Clock */}
        <div className="hidden xl:flex items-center gap-1 text-[#8b949e]">
          <Clock className="w-3.5 h-3.5 text-[#00e3fd]" />
          <span className="text-[#00e3fd] font-semibold text-[11px]">
            {zuluTime}
          </span>
        </div>
      </section>
    </>
  );
}
