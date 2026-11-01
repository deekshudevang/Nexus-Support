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
    <div className="bg-[#090f15] border-t border-[#30353d] font-mono text-xs select-none">
      {/* Timeline Toggle Bar */}
      <div
        onClick={() => setIsExpanded(!isExpanded)}
        className="px-3 py-1 bg-[#171c23] hover:bg-[#1b2027] cursor-pointer flex items-center justify-between border-b border-[#30353d]"
      >
        <div className="flex items-center gap-2">
          <Activity className="w-3.5 h-3.5 text-[#00e3fd]" />
          <span className="font-bold text-[11px] text-[#dee2ec] tracking-wider uppercase">
            REAL-TIME STREAM AUDIT TIMELINE ({events.length} EVENTS BUFFERED)
          </span>
          {events.length > 0 && (
            <span className="bg-[#252a32] text-[#4edea3] text-[10px] px-1.5 py-0.5 border border-[#30353d]">
              LATEST: #{events[events.length - 1].stream_seq || events[events.length - 1].seq || events.length}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-[#8b949e]">
            {isExpanded ? '[COLLAPSE LOGS]' : '[EXPAND LOGS]'}
          </span>
          {isExpanded ? (
            <ChevronDown className="w-3.5 h-3.5 text-[#8b949e]" />
          ) : (
            <ChevronUp className="w-3.5 h-3.5 text-[#8b949e]" />
          )}
        </div>
      </div>

      {/* Expanded Timeline Drawer */}
      {isExpanded && (
        <div className="p-3 bg-[#090f15] max-h-48 overflow-y-auto space-y-2">
          {/* Replay Controls */}
          <div className="flex items-center justify-between bg-[#171c23] p-1.5 border border-[#30353d] text-[10px]">
            <div className="flex items-center gap-2">
              <span className="text-[#8b949e]">REPLAY FROM STREAM_SEQ:</span>
              <input
                type="number"
                value={replaySinceSeq}
                onChange={(e) => setReplaySinceSeq(e.target.value)}
                className="w-16 bg-[#090f15] border border-[#30353d] text-[#dee2ec] px-1.5 py-0.5 outline-none focus:border-[#00e3fd]"
              />
              <button
                onClick={handleTriggerReplay}
                disabled={isReplaying}
                className="bg-[#252a32] hover:bg-[#30353d] text-[#00e3fd] px-2 py-0.5 border border-[#30353d] font-bold"
              >
                {isReplaying ? 'PULLING...' : 'TRIGGER REPLAY'}
              </button>
            </div>
            {replayError && (
              <span className="text-[#ff5625] font-semibold">{replayError}</span>
            )}
          </div>

          {/* Events List */}
          {events.length === 0 ? (
            <div className="p-3 text-center text-[#8b949e] border border-dashed border-[#30353d]">
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
                    className="flex items-center justify-between p-1 bg-[#171c23] border border-[#30353d] text-[10px] hover:border-[#00e3fd]"
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-[#4edea3] font-bold">#{seq}</span>
                      <span className="bg-[#252a32] text-[#9cf0ff] px-1 border border-[#30353d]">
                        {type}
                      </span>
                      <span className="text-[#dee2ec]">
                        {typeof ev.payload === 'string'
                          ? ev.payload
                          : JSON.stringify(ev.payload || {}).substring(0, 60)}
                      </span>
                    </div>
                    <span className="text-[#8b949e]">
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
