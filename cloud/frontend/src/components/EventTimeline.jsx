import React, { useState } from 'react';
import {
  Activity,
  History,
  RotateCcw,
  Shield,
  Send,
  AlertCircle,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import { fetchV1Replay } from '../api';

export default function EventTimeline({
  events = [],
  token,
  onReplayLoaded,
}) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [replaySinceSeq, setReplaySinceSeq] = useState('0');
  const [isReplaying, setIsReplaying] = useState(false);
  const [replayError, setReplayError] = useState(null);

  const handleTriggerReplay = async () => {
    if (!token) return;
    setIsReplaying(true);
    setReplayError(null);
    try {
      const data = await fetchV1Replay(token, parseInt(replaySinceSeq || '0', 10), 50);
      if (onReplayLoaded && data.events) {
        onReplayLoaded(data.events);
      }
    } catch (err) {
      setReplayError(err.message || 'Replay failed.');
    } finally {
      setIsReplaying(false);
    }
  };

  return (
    <div className="bg-[#0b0f19] border-t border-[#334155] font-mono text-xs select-none">
      {/* Timeline Toggle Bar */}
      <div
        onClick={() => setIsExpanded(!isExpanded)}
        className="px-3 py-1.5 bg-[#111827] hover:bg-slate-800 cursor-pointer flex items-center justify-between border-b border-[#334155] transition-colors"
      >
        <div className="flex items-center gap-2">
          <Activity className="w-3.5 h-3.5 text-sky-400" />
          <span className="font-bold text-[11px] text-slate-200 tracking-wider uppercase">
            REAL-TIME STREAM AUDIT TIMELINE ({events.length} EVENTS BUFFERED)
          </span>
          {events.length > 0 && (
            <span className="bg-[#1e293b] text-emerald-400 text-[10px] px-1.5 py-0.5 border border-[#334155] rounded">
              LATEST: #{events[events.length - 1].stream_seq || events[events.length - 1].seq || events.length}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-slate-400">
            {isExpanded ? '[COLLAPSE LOGS]' : '[EXPAND LOGS]'}
          </span>
          {isExpanded ? (
            <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
          ) : (
            <ChevronUp className="w-3.5 h-3.5 text-slate-400" />
          )}
        </div>
      </div>

      {/* Expanded Timeline Drawer */}
      {isExpanded && (
        <div className="p-3 bg-[#0b0f19] max-h-48 overflow-y-auto space-y-2">
          {/* Replay Controls */}
          <div className="flex items-center justify-between bg-[#111827] p-2 border border-[#334155] rounded text-[10px]">
            <div className="flex items-center gap-2">
              <span className="text-slate-400">REPLAY FROM STREAM_SEQ:</span>
              <input
                type="number"
                value={replaySinceSeq}
                onChange={(e) => setReplaySinceSeq(e.target.value)}
                className="w-16 bg-[#0b0f19] border border-[#334155] rounded text-slate-200 px-2 py-0.5 outline-none focus:border-sky-500"
              />
              <button
                onClick={handleTriggerReplay}
                disabled={isReplaying}
                className="bg-[#1e293b] hover:bg-slate-700 text-sky-400 px-2.5 py-1 border border-[#334155] rounded font-bold"
              >
                {isReplaying ? 'PULLING...' : 'TRIGGER REPLAY'}
              </button>
            </div>
            {replayError && (
              <span className="text-red-400 font-semibold">{replayError}</span>
            )}
          </div>

          {/* Events List */}
          {events.length === 0 ? (
            <div className="p-3 text-center text-slate-500 border border-dashed border-[#334155] rounded">
              NO STREAM EVENTS RECEIVED YET. WAITING FOR WEBSOCKET OUTBOX BURSTS...
            </div>
          ) : (
            <div className="space-y-1">
              {events.slice(-20).reverse().map((ev, idx) => {
                const seq = ev.stream_seq || ev.seq || idx + 1;
                const type = ev.event_type || ev.type || 'STREAM_EVENT';
                const ts = ev.created_at || ev.timestamp;

                return (
                  <div
                    key={ev.id || `${seq}-${idx}`}
                    className="flex items-center justify-between p-1.5 bg-[#111827] border border-[#334155] rounded text-[10px] hover:border-slate-500"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-emerald-400 font-bold">#{seq}</span>
                      <span className="bg-[#1e293b] text-sky-300 px-1.5 py-0.5 border border-[#334155] rounded">
                        {type}
                      </span>
                      <span className="text-slate-300">
                        {typeof ev.payload === 'string'
                          ? ev.payload
                          : JSON.stringify(ev.payload || {}).substring(0, 60)}
                      </span>
                    </div>
                    <span className="text-slate-400">
                      {ts ? new Date(ts).toLocaleTimeString() : 'JUST NOW'}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
