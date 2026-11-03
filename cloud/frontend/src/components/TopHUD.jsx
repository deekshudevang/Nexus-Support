import React, { useEffect, useState } from 'react';
import {
  ShieldAlert,
  Clock,
  Volume2,
  VolumeX,
  LogOut,
  RefreshCw,
  Activity,
  Radio,
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
}) {
  const [zuluTime, setZuluTime] = useState('');

  useEffect(() => {
    const updateZulu = () => {
      const now = new Date();
      setZuluTime(now.toISOString().replace('T', ' ').substring(0, 19) + ' UTC');
    };
    updateZulu();
    const interval = setInterval(updateZulu, 1000);
    return () => clearInterval(interval);
  }, []);

  return (
    <>
      {/* Top HUD Primary Navigation & System Bar */}
      <header className="bg-[#111827] border-b border-[#334155] flex justify-between items-center w-full px-3 h-12 z-50 shrink-0 select-none">
        {/* Brand Anchor & Region */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-[#dc2626]" />
            <span className="font-bold tracking-tight text-[#f8fafc] text-sm md:text-base uppercase font-sans">
              MESHLINK COMMAND // INCIDENT DISPATCH
            </span>
          </div>
          <div className="h-4 w-px bg-[#334155] hidden sm:block"></div>
          <span className="text-[11px] font-medium text-[#94a3b8] bg-[#1e293b] px-2 py-0.5 rounded border border-[#334155] hidden md:inline-block">
            GATEWAY LINK // LIVE DISPATCH HUD
          </span>
        </div>

        {/* Tactical Telemetry Readouts */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Node & Network Link Telemetry */}
          <div className="hidden lg:flex items-center gap-2 text-[#94a3b8] font-sans text-xs bg-[#1e293b] px-2.5 py-1 rounded border border-[#334155]">
            <span className="flex items-center gap-1.5 text-[#16a34a] font-medium">
              <Activity className="w-3.5 h-3.5" />
              <span>NODES: {activeNodesCount}/{totalNodesCount} ONLINE</span>
            </span>
            <span className="text-[#475569]">|</span>
            <span className="flex items-center gap-1 text-[#38bdf8] font-medium">
              <Radio className="w-3.5 h-3.5" />
              <span>MESH NETWORK: ACTIVE</span>
            </span>
          </div>

          {/* Connection Status Badge */}
          <div
            data-testid="ws-status-badge"
            className={`flex items-center gap-1.5 px-2 py-0.5 font-mono text-[11px] font-semibold rounded border ${
              wsStatus === 'OPEN'
                ? 'bg-[#064e3b] text-[#a7f3d0] border-[#059669]'
                : wsStatus === 'CONNECTING'
                ? 'bg-[#451a03] text-[#fde68a] border-[#b45309]'
                : 'bg-[#450a0a] text-[#fecaca] border-[#dc2626]'
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full inline-block ${
                wsStatus === 'OPEN'
                  ? 'bg-[#10b981]'
                  : wsStatus === 'CONNECTING'
                  ? 'bg-[#f59e0b] animate-pulse'
                  : 'bg-[#ef4444]'
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
            className="p-1.5 bg-[#1e293b] hover:bg-[#334155] text-[#f8fafc] rounded border border-[#334155] transition-colors"
            title={soundEnabled ? 'Mute Alert Sirens' : 'Enable Alert Sirens'}
            aria-label="Toggle siren audio"
          >
            {soundEnabled ? (
              <Volume2 className="w-4 h-4 text-[#38bdf8]" />
            ) : (
              <VolumeX className="w-4 h-4 text-[#64748b]" />
            )}
          </button>

          {/* Manual Refresh Sync */}
          {onRefresh && (
            <button
              onClick={onRefresh}
              className="p-1.5 bg-[#1e293b] hover:bg-[#334155] text-[#f8fafc] rounded border border-[#334155] transition-colors"
              title="Force Roster & Incident Sync"
              aria-label="Sync roster"
            >
              <RefreshCw className="w-4 h-4 text-[#38bdf8]" />
            </button>
          )}

          {/* Operator Logout */}
          <button
            data-testid="logout-button"
            onClick={onLogout}
            className="flex items-center gap-1.5 bg-[#1e293b] hover:bg-[#991b1b] text-[#f8fafc] px-2.5 py-1 rounded font-medium text-xs border border-[#334155] hover:border-[#dc2626] transition-colors"
            title="Terminate Tactical Session"
          >
            <LogOut className="w-3.5 h-3.5 text-[#ef4444]" />
            <span className="hidden sm:inline font-semibold">SIGN OUT</span>
          </button>
        </div>
      </header>

      {/* Emergency Operational Status Bar */}
      <section className="bg-[#0f172a] border-b border-[#334155] px-3 py-1 flex items-center justify-between text-xs font-sans shrink-0 select-none">
        {/* Status Indicator */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 px-2 py-0.5 bg-[#1e293b] text-[#f8fafc] rounded border border-[#475569]">
            <span className="w-2 h-2 rounded-full bg-[#10b981] inline-block"></span>
            <span className="text-[10px] tracking-wider font-bold uppercase">
              STATUS: OPERATIONAL
            </span>
          </div>
          <span className="text-[#94a3b8] font-medium hidden md:inline text-xs">
            EMERGENCY INCIDENT MONITORING ACTIVE
          </span>
        </div>

        {/* High-Visibility Emergency Broadcast Ticker */}
        <div className="flex-1 mx-3 overflow-hidden bg-[#0b0f19] px-2.5 py-0.5 rounded border border-[#334155] flex items-center text-xs">
          <div className="flex items-center gap-1 text-[#d97706] font-semibold whitespace-nowrap">
            <AlertTriangle className="w-3.5 h-3.5" />
            <span>MESH STATUS:</span>
          </div>
          <div className="overflow-hidden whitespace-nowrap w-full pl-2 text-[#cbd5e1] font-mono text-[11px]">
            <span>
              RADIO RELAYS ACTIVE // P2P BLE & LORA MESH READY // MONITORING INCOMING SOS BEACONS
            </span>
          </div>
        </div>

        {/* Real-time Zulu UTC Clock */}
        <div className="hidden xl:flex items-center gap-1.5 text-[#94a3b8] font-mono">
          <Clock className="w-3.5 h-3.5 text-[#38bdf8]" />
          <span className="text-[#e2e8f0] font-semibold text-xs">
            {zuluTime}
          </span>
        </div>
      </section>
    </>
  );
}
