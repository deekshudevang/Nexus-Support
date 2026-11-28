import React, { useState, useMemo } from 'react';
import {
  Flame,
  HeartPulse,
  Waves,
  ShieldAlert,
  Search,
  Users,
  MapPin,
  Clock,
  Radio,
  Send,
  AlertTriangle,
  Copy,
  ChevronRight
} from 'lucide-react';

export default function IncidentQueue({
  incidents = [],
  selectedIncident,
  onSelectIncident,
  onOpenDispatch,
}) {
  const [filterSeverity, setFilterSeverity] = useState('ALL'); // 'ALL' | 'CRITICAL' | 'URGENT' | 'LOW' | 'RESOLVED'
  const [searchQuery, setSearchQuery] = useState('');

  // Priority / Severity icon mapper
  const getEmergencyIcon = (type = '') => {
    const t = (type || '').toUpperCase();
    if (t.includes('FIRE') || t.includes('SMOKE')) return <Flame className="w-4 h-4 text-[#ef4444]" />;
    if (t.includes('MED') || t.includes('TRAUMA')) return <HeartPulse className="w-4 h-4 text-[#ef4444]" />;
    if (t.includes('FLOOD') || t.includes('WATER')) return <Waves className="w-4 h-4 text-[#38bdf8]" />;
    return <ShieldAlert className="w-4 h-4 text-[#cbd5e1]" />;
  };

  // Filter and search logic
  const filteredIncidents = useMemo(() => {
    return incidents.filter((inc) => {
      const sev = inc.severity !== undefined ? inc.severity : 5;
      const status = (inc.status || 'ACTIVE').toUpperCase();

      // Severity / Status filter
      if (filterSeverity === 'CRITICAL') {
        if (sev < 8 && inc.priority !== 'CRITICAL') return false;
      } else if (filterSeverity === 'URGENT') {
        if ((sev < 5 || sev >= 8) && inc.priority !== 'HIGH') return false;
      } else if (filterSeverity === 'LOW') {
        if (sev >= 5 && inc.priority !== 'LOW' && inc.priority !== 'MEDIUM') return false;
      } else if (filterSeverity === 'RESOLVED') {
        if (status !== 'RESOLVED' && status !== 'CLOSED') return false;
      }

      // Query filter
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const msg = (inc.message || '').toLowerCase();
        const sender = (inc.senderName || inc.senderId || '').toLowerCase();
        const type = (inc.emergencyType || '').toLowerCase();
        const id = (inc.uuid || inc.id || '').toLowerCase();
        const assigned = (inc.assignedTo || '').toLowerCase();
        if (
          !msg.includes(q) &&
          !sender.includes(q) &&
          !type.includes(q) &&
          !id.includes(q) &&
          !assigned.includes(q)
        ) {
          return false;
        }
      }

      return true;
    });
  }, [incidents, filterSeverity, searchQuery]);

  // Counts for tabs
  const criticalCount = useMemo(() => {
    return incidents.filter(i => (i.severity >= 8 || i.priority === 'CRITICAL')).length;
  }, [incidents]);

  const urgentCount = useMemo(() => {
    return incidents.filter(i => (i.severity >= 5 && i.severity < 8) || i.priority === 'HIGH').length;
  }, [incidents]);

  const lowCount = useMemo(() => {
    return incidents.filter(i => (i.severity < 5 || i.priority === 'LOW' || i.priority === 'MEDIUM')).length;
  }, [incidents]);

  return (
    <aside className="w-full md:w-88 xl:w-96 flex flex-col bg-[#0b0f19] border-r border-[#334155] shrink-0 z-20 select-none">
      {/* Panel Header */}
      <div className="p-2.5 bg-[#111827] border-b border-[#334155] flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ShieldAlert className="w-4 h-4 text-[#38bdf8]" />
          <h2 className="font-bold text-xs uppercase tracking-wider text-[#f8fafc]">
            INCIDENT QUEUE
          </h2>
          <span className="bg-[#1e293b] text-[#cbd5e1] text-[10px] font-mono font-semibold px-2 py-0.5 rounded border border-[#334155]">
            {incidents.length} TOTAL
          </span>
        </div>
        <button
          onClick={() => {
            if (filteredIncidents.length > 0) {
              onOpenDispatch(filteredIncidents[0]);
            }
          }}
          className="bg-[#2563eb] hover:bg-[#1d4ed8] text-white text-[11px] font-semibold px-2.5 py-1 rounded flex items-center gap-1.5 transition-colors shadow-sm"
        >
          <Send className="w-3 h-3" />
          <span>NEW DISPATCH</span>
        </button>
      </div>

      {/* Filter Tabs */}
      <div className="grid grid-cols-4 bg-[#111827] border-b border-[#334155] text-center font-sans text-xs font-semibold">
        <button
          onClick={() => setFilterSeverity('ALL')}
          className={`py-1.5 transition-colors border-b-2 ${
            filterSeverity === 'ALL'
              ? 'bg-[#1e293b] text-[#f8fafc] border-[#2563eb]'
              : 'text-[#94a3b8] hover:bg-[#1e293b]/50 border-transparent'
          }`}
        >
          ALL ({incidents.length})
        </button>
        <button
          data-testid="filter-critical"
          onClick={() => setFilterSeverity('CRITICAL')}
          className={`py-1.5 flex items-center justify-center gap-1 transition-colors border-b-2 ${
            filterSeverity === 'CRITICAL'
              ? 'bg-[#1e293b] text-[#fca5a5] border-[#ef4444]'
              : 'text-[#ef4444] hover:bg-[#1e293b]/50 border-transparent'
          }`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-[#ef4444]"></span>
          CRIT ({criticalCount})
        </button>
        <button
          onClick={() => setFilterSeverity('URGENT')}
          className={`py-1.5 transition-colors border-b-2 ${
            filterSeverity === 'URGENT'
              ? 'bg-[#1e293b] text-[#fde68a] border-[#f59e0b]'
              : 'text-[#f59e0b] hover:bg-[#1e293b]/50 border-transparent'
          }`}
        >
          URG ({urgentCount})
        </button>
        <button
          data-testid="filter-low"
          onClick={() => setFilterSeverity('LOW')}
          className={`py-1.5 transition-colors border-b-2 ${
            filterSeverity === 'LOW'
              ? 'bg-[#1e293b] text-[#a7f3d0] border-[#10b981]'
              : 'text-[#10b981] hover:bg-[#1e293b]/50 border-transparent'
          }`}
        >
          LOW ({lowCount})
        </button>
      </div>

      {/* Search Input Bar */}
      <div className="p-2 bg-[#111827] border-b border-[#334155] flex gap-1.5">
        <div className="relative flex-1">
          <Search className="w-3.5 h-3.5 text-[#64748b] absolute left-2.5 top-2.5" />
          <input
            data-testid="search-input"
            type="text"
            placeholder="Search by callsign, message, ID..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-[#0b0f19] border border-[#334155] rounded text-[#f8fafc] text-xs pl-8 pr-2.5 py-1.5 focus:border-[#38bdf8] outline-none"
          />
        </div>
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="px-2.5 py-1 bg-[#1e293b] text-[#94a3b8] hover:text-white rounded text-xs border border-[#334155]"
          >
            CLEAR
          </button>
        )}
      </div>

      {/* Scrollable Incident Cards Stream */}
      <div className="flex-1 overflow-y-auto p-2 space-y-2">
        {filteredIncidents.length === 0 ? (
          <div className="p-6 text-center text-[#64748b] text-xs border border-dashed border-[#334155] rounded m-2">
            NO INCIDENTS MATCHING CRITERIA
          </div>
        ) : (
          filteredIncidents.map((inc) => {
            const isSelected = selectedIncident && (selectedIncident.uuid === inc.uuid || selectedIncident.id === inc.id);
            const sev = inc.severity !== undefined ? inc.severity : 5;
            const isCritical = sev >= 8 || inc.priority === 'CRITICAL';
            const hopCount = inc.hopCount !== undefined ? inc.hopCount : 0;
            const hasGps =
              inc.latitude !== null &&
              inc.latitude !== undefined &&
              inc.longitude !== null &&
              inc.longitude !== undefined &&
              Number.isFinite(inc.latitude) &&
              Number.isFinite(inc.longitude);

            return (
              <div
                key={inc.uuid || inc.id || Math.random()}
                onClick={() => onSelectIncident(inc)}
                className={`relative p-3 rounded transition-all cursor-pointer border ${
                  isSelected
                    ? 'bg-[#1e293b] border-[#38bdf8] shadow'
                    : 'bg-[#111827] border-[#334155] hover:border-[#475569] hover:bg-[#172033]'
                } ${isCritical ? 'border-l-4 border-l-[#dc2626]' : 'border-l-4 border-l-[#2563eb]'}`}
              >
                {/* Header: Priority Badge & Hop Badge */}
                <div className="flex items-center justify-between gap-1 mb-2">
                  <div className="flex items-center gap-1.5">
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded border flex items-center gap-1 ${
                        isCritical
                          ? 'bg-[#450a0a] text-[#fecaca] border-[#dc2626]'
                          : sev >= 5
                          ? 'bg-[#451a03] text-[#fde68a] border-[#b45309]'
                          : 'bg-[#064e3b] text-[#a7f3d0] border-[#059669]'
                      }`}
                    >
                      {isCritical && (
                        <span className="w-1.5 h-1.5 bg-[#ef4444] rounded-full inline-block"></span>
                      )}
                      <span>
                        {isCritical ? 'CRITICAL P0' : sev >= 5 ? 'URGENT P1' : 'MONITORING P2'}
                      </span>
                    </span>

                    {/* Hop Count Badge */}
                    <span
                      data-testid="hop-badge"
                      className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-[#0b0f19] text-[#94a3b8] border border-[#334155]"
                    >
                      {hopCount === 0 ? 'DIRECT RF' : `HOP: ${hopCount}`}
                    </span>
                  </div>

                  {/* Relative / Timestamp */}
                  <span className="font-mono text-[10px] text-[#64748b]">
                    {inc.timestamp
                      ? new Date(inc.timestamp > 1e12 ? inc.timestamp : inc.timestamp * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                      : 'LIVE'}
                  </span>
                </div>

                {/* Emergency Type & Sender */}
                <div className="flex items-center justify-between mb-1.5">
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-[#f8fafc]">
                    {getEmergencyIcon(inc.emergencyType)}
                    <span className="uppercase tracking-wide">
                      {inc.emergencyType || 'EMERGENCY BEACON'}
                    </span>
                  </div>
                  <span className="font-mono text-xs text-[#38bdf8] font-medium">
                    {inc.senderName || inc.senderId || 'UNKNOWN NODE'}
                  </span>
                </div>

                {/* Distress Message */}
                <p className="text-xs text-[#cbd5e1] leading-relaxed mb-2.5 line-clamp-2">
                  {inc.message || 'Distress signal received without payload notes.'}
                </p>

                {/* Metadata Bento Bar: GPS (Honest!), Casualties, Duplicate */}
                <div className="grid grid-cols-2 gap-1.5 bg-[#0b0f19] p-2 rounded border border-[#334155] text-[11px] mb-2">
                  <div>
                    <span className="text-[#64748b] block text-[9px] font-bold">LOCATION:</span>
                    {hasGps ? (
                      <span className="text-[#e2e8f0] font-mono text-[10px] font-medium">
                        {inc.latitude.toFixed(4)}°, {inc.longitude.toFixed(4)}°
                      </span>
                    ) : (
                      <span className="text-[#f59e0b] font-medium flex items-center gap-1 text-[10px]">
                        <AlertTriangle className="w-2.5 h-2.5 text-[#f59e0b]" />
                        <span>LOCATION UNAVAILABLE</span>
                      </span>
                    )}
                  </div>
                  <div>
                    <span className="text-[#64748b] block text-[9px] font-bold">ASSIGNMENT:</span>
                    <span className={`text-[10px] font-medium ${inc.assignedTo ? 'text-[#10b981]' : 'text-[#64748b]'}`}>
                      {inc.assignedTo || 'UNASSIGNED'}
                    </span>
                  </div>
                </div>

                {/* Footer Actions */}
                <div className="flex items-center justify-between pt-1.5 border-t border-[#334155]">
                  {inc.duplicate_count ? (
                    <span className="text-[10px] text-[#f59e0b] flex items-center gap-1 font-medium">
                      <Copy className="w-3 h-3" />
                      <span>{inc.duplicate_count} LINKED REPORTS</span>
                    </span>
                  ) : (
                    <span className="font-mono text-[10px] text-[#64748b]">
                      ID: {(inc.uuid || inc.id || 'N/A').substring(0, 10)}...
                    </span>
                  )}

                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenDispatch(inc);
                    }}
                    className="bg-[#1e293b] hover:bg-[#2563eb] text-[#f8fafc] text-[11px] font-semibold px-2.5 py-0.5 rounded border border-[#334155] hover:border-[#3b82f6] transition-colors flex items-center gap-1"
                  >
                    <span>DISPATCH</span>
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </aside>
  );
}
