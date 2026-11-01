import React, { useState, useMemo } from 'react';
import {
  Flame,
  HeartPulse,
  Waves,
  ShieldAlert,
  Search,
  Filter,
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
    if (t.includes('FIRE') || t.includes('SMOKE')) return <Flame className="w-4 h-4 text-[#ff5625]" />;
    if (t.includes('MED') || t.includes('TRAUMA')) return <HeartPulse className="w-4 h-4 text-[#ef4444]" />;
    if (t.includes('FLOOD') || t.includes('WATER')) return <Waves className="w-4 h-4 text-[#00e3fd]" />;
    return <ShieldAlert className="w-4 h-4 text-[#ffb5a0]" />;
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
    <aside className="w-80 md:w-88 xl:w-96 flex flex-col bg-[#090f15] border-r border-[#30353d] shrink-0 z-20 select-none">
      {/* Panel Header */}
      <div className="p-2.5 bg-[#171c23] border-b border-[#30353d] flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ShieldAlert className="w-4 h-4 text-[#00e3fd]" />
          <h2 className="font-bold text-xs uppercase tracking-wider text-[#dee2ec]">
            INCIDENT QUEUE
          </h2>
          <span className="bg-[#30353d] text-[#dee2ec] text-[10px] font-mono font-bold px-1.5 py-0.5">
            {incidents.length} TOTAL
          </span>
        </div>
        <button
          onClick={() => {
            if (filteredIncidents.length > 0) {
              onOpenDispatch(filteredIncidents[0]);
            }
          }}
          className="bg-[#ff5625] hover:bg-[#b12d00] text-[#090f15] text-[10px] font-mono font-bold px-2 py-1 flex items-center gap-1 border border-[#ffb5a0] transition-colors"
        >
          <Send className="w-3 h-3" />
          <span>NEW DISPATCH</span>
        </button>
      </div>

      {/* Filter Tabs */}
      <div className="grid grid-cols-4 gap-px bg-[#30353d] border-b border-[#30353d] text-center font-mono text-[10px] font-bold">
        <button
          onClick={() => setFilterSeverity('ALL')}
          className={`py-1.5 transition-colors ${
            filterSeverity === 'ALL'
              ? 'bg-[#252a32] text-[#dee2ec] border-b-2 border-[#00e3fd]'
              : 'bg-[#171c23] text-[#8b949e] hover:bg-[#1b2027]'
          }`}
        >
          ALL ({incidents.length})
        </button>
        <button
          data-testid="filter-critical"
          onClick={() => setFilterSeverity('CRITICAL')}
          className={`py-1.5 flex items-center justify-center gap-1 transition-colors ${
            filterSeverity === 'CRITICAL'
              ? 'bg-[#252a32] text-[#ffdad6] border-b-2 border-[#ef4444]'
              : 'bg-[#171c23] text-[#ef4444] hover:bg-[#1b2027]'
          }`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-[#ef4444] animate-pulse"></span>
          CRIT ({criticalCount})
        </button>
        <button
          onClick={() => setFilterSeverity('URGENT')}
          className={`py-1.5 transition-colors ${
            filterSeverity === 'URGENT'
              ? 'bg-[#252a32] text-[#dee2ec] border-b-2 border-[#ff9500]'
              : 'bg-[#171c23] text-[#ad887e] hover:bg-[#1b2027]'
          }`}
        >
          URG ({urgentCount})
        </button>
        <button
          data-testid="filter-low"
          onClick={() => setFilterSeverity('LOW')}
          className={`py-1.5 transition-colors ${
            filterSeverity === 'LOW'
              ? 'bg-[#252a32] text-[#dee2ec] border-b-2 border-[#4edea3]'
              : 'bg-[#171c23] text-[#8b949e] hover:bg-[#1b2027]'
          }`}
        >
          LOW ({lowCount})
        </button>
      </div>

      {/* Search Input Bar */}
      <div className="p-1.5 bg-[#171c23] border-b border-[#30353d] flex gap-1">
        <div className="relative flex-1">
          <Search className="w-3.5 h-3.5 text-[#8b949e] absolute left-2 top-2" />
          <input
            data-testid="search-input"
            type="text"
            placeholder="FILTER BY CALLSIGN, MSG, ID..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-[#090f15] border border-[#30353d] text-[#dee2ec] font-mono text-xs pl-7 pr-2 py-1 focus:border-[#00e3fd] outline-none"
          />
        </div>
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="px-2 py-1 bg-[#252a32] text-[#dee2ec] font-mono text-[10px] border border-[#30353d]"
          >
            CLEAR
          </button>
        )}
      </div>

      {/* Scrollable Incident Cards Stream */}
      <div className="flex-1 overflow-y-auto p-1.5 space-y-1.5">
        {filteredIncidents.length === 0 ? (
          <div className="p-6 text-center text-[#8b949e] font-mono text-xs border border-dashed border-[#30353d] m-2">
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
                className={`relative p-2.5 transition-all cursor-pointer border ${
                  isSelected
                    ? 'bg-[#252a32] border-[#00e3fd] shadow-md'
                    : 'bg-[#171c23] border-[#30353d] hover:border-[#484f58] hover:bg-[#1b2027]'
                } ${isCritical ? 'border-l-4 border-l-[#ef4444]' : 'border-l-4 border-l-[#00e3fd]'}`}
              >
                {/* Header: Priority Badge & Hop Badge */}
                <div className="flex items-center justify-between gap-1 mb-1.5">
                  <div className="flex items-center gap-1.5">
                    <span
                      className={`font-mono text-[10px] font-bold px-1.5 py-0.5 border flex items-center gap-1 ${
                        isCritical
                          ? 'bg-[#93000a] text-white border-[#ef4444]'
                          : sev >= 5
                          ? 'bg-[#3b2d00] text-[#ffddb8] border-[#ff9500]'
                          : 'bg-[#003824] text-[#6ffbbe] border-[#4edea3]'
                      }`}
                    >
                      {isCritical && (
                        <span className="w-1.5 h-1.5 bg-white rounded-full animate-ping"></span>
                      )}
                      <span>
                        {isCritical ? 'CRITICAL P0' : sev >= 5 ? 'URGENT P1' : 'MONITORING P2'}
                      </span>
                    </span>

                    {/* Hop Count Badge */}
                    <span
                      data-testid="hop-badge"
                      className="font-mono text-[10px] px-1.5 py-0.5 bg-[#090f15] text-[#9cf0ff] border border-[#30353d]"
                    >
                      {hopCount === 0 ? 'DIRECT RF' : `HOP: ${hopCount}`}
                    </span>
                  </div>

                  {/* Relative / Timestamp */}
                  <span className="font-mono text-[10px] text-[#8b949e]">
                    {inc.timestamp
                      ? new Date(inc.timestamp * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                      : 'LIVE'}
                  </span>
                </div>

                {/* Emergency Type & Sender */}
                <div className="flex items-center justify-between mb-1">
                  <div className="flex items-center gap-1.5 text-xs font-bold text-[#dee2ec]">
                    {getEmergencyIcon(inc.emergencyType)}
                    <span className="uppercase tracking-wide">
                      {inc.emergencyType || 'EMERGENCY BEACON'}
                    </span>
                  </div>
                  <span className="font-mono text-[11px] text-[#00daf3] font-medium">
                    {inc.senderName || inc.senderId || 'UNKNOWN NODE'}
                  </span>
                </div>

                {/* Distress Message */}
                <p className="font-sans text-xs text-[#dee2ec] leading-relaxed mb-2 line-clamp-2">
                  {inc.message || 'Distress signal received without payload notes.'}
                </p>

                {/* Metadata Bento Bar: GPS (Honest!), Casualties, Duplicate */}
                <div className="grid grid-cols-2 gap-1 bg-[#090f15] p-1.5 border border-[#30353d] font-mono text-[10px] mb-2">
                  <div>
                    <span className="text-[#8b949e] block text-[9px]">LOCATION:</span>
                    {hasGps ? (
                      <span className="text-[#dee2ec] font-semibold">
                        {inc.latitude.toFixed(4)}°, {inc.longitude.toFixed(4)}°
                      </span>
                    ) : (
                      <span className="text-[#ff9500] font-bold flex items-center gap-0.5">
                        <AlertTriangle className="w-2.5 h-2.5 text-[#ff9500]" />
                        <span>LOCATION UNAVAILABLE</span>
                      </span>
                    )}
                  </div>
                  <div>
                    <span className="text-[#8b949e] block text-[9px]">ASSIGNMENT:</span>
                    <span className={inc.assignedTo ? 'text-[#4edea3]' : 'text-[#8b949e]'}>
                      {inc.assignedTo || 'UNASSIGNED'}
                    </span>
                  </div>
                </div>

                {/* Footer Actions */}
                <div className="flex items-center justify-between pt-1 border-t border-[#30353d]">
                  {inc.duplicate_count ? (
                    <span className="font-mono text-[10px] text-[#ff9500] flex items-center gap-1">
                      <Copy className="w-2.5 h-2.5" />
                      <span>{inc.duplicate_count} LINKED REPORTS</span>
                    </span>
                  ) : (
                    <span className="font-mono text-[10px] text-[#8b949e]">
                      ID: {(inc.uuid || inc.id || 'N/A').substring(0, 10)}...
                    </span>
                  )}

                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenDispatch(inc);
                    }}
                    className="bg-[#1b2027] hover:bg-[#ff5625] hover:text-[#090f15] text-[#9cf0ff] font-mono text-[10px] font-bold px-2 py-0.5 border border-[#30353d] transition-colors flex items-center gap-0.5"
                  >
                    <span>DISPATCH</span>
                    <ChevronRight className="w-3 h-3" />
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
