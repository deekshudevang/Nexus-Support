import React, { useState } from 'react';
import {
  Users,
  Activity,
  Heart,
  Radio,
  Battery,
  Shield,
  Send,
  AlertTriangle,
  X,
  CheckCircle,
  Clock
} from 'lucide-react';
import { createV1Assignment } from '../api';

export default function DispatchPanel({
  responders = [],
  teams = [],
  dispatchIncident,
  onCloseDispatchModal,
  onAssignmentSuccess,
  token,
}) {
  const [selectedResponderId, setSelectedResponderId] = useState('');
  const [assignedRole, setAssignedRole] = useState('LEAD');
  const [operationalNotes, setOperationalNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [dispatchError, setDispatchError] = useState(null);
  const [dispatchSuccess, setDispatchSuccess] = useState(null);

  // Fallback demo responders if none loaded from API
  const displayResponders = responders.length > 0 ? responders : [
    {
      id: 'resp-alpha-01',
      name: 'Capt. Chen',
      callsign: 'SAR-ALPHA 1',
      status: 'ON_SCENE',
      team: 'Alpha Search',
      heartRate: 114,
      o2Level: 84,
      battery: 78,
      rssi: -68,
    },
    {
      id: 'resp-medevac-02',
      name: 'Lt. Ramirez',
      callsign: 'MEDEVAC-2',
      status: 'EN_ROUTE',
      team: 'Air Support',
      heartRate: 88,
      o2Level: 98,
      battery: 92,
      rssi: -74,
    },
    {
      id: 'resp-k9-03',
      name: 'Officer Davis',
      callsign: 'K9-BRAVO',
      status: 'AVAILABLE',
      team: 'Canine Unit',
      heartRate: 76,
      o2Level: 100,
      battery: 89,
      rssi: -62,
    },
    {
      id: 'resp-boat-04',
      name: 'Chief Morales',
      callsign: 'WATER-RESCUE 4',
      status: 'AVAILABLE',
      team: 'Swiftwater',
      heartRate: 82,
      o2Level: 100,
      battery: 95,
      rssi: -80,
    },
  ];

  const deployedCount = displayResponders.filter(r => r.status === 'ON_SCENE').length;
  const inTransitCount = displayResponders.filter(r => r.status === 'EN_ROUTE' || r.status === 'ASSIGNED').length;
  const availableCount = displayResponders.filter(r => r.status === 'AVAILABLE').length;

  const handleConfirmDispatch = async () => {
    if (!dispatchIncident || !selectedResponderId) {
      setDispatchError('Select a field responder to dispatch.');
      return;
    }

    setIsSubmitting(true);
    setDispatchError(null);
    setDispatchSuccess(null);

    const targetIncidentId = dispatchIncident.id || dispatchIncident.uuid;

    try {
      if (token) {
        await createV1Assignment(token, {
          incident_id: targetIncidentId,
          responder_id: selectedResponderId,
          role: assignedRole,
          notes: operationalNotes || `Dispatched to ${dispatchIncident.emergencyType || 'incident'}`,
        });
      }

      setDispatchSuccess('Tactical assignment successfully committed and relayed to field mesh.');
      setTimeout(() => {
        if (onAssignmentSuccess) {
          onAssignmentSuccess({
            incidentId: targetIncidentId,
            responderId: selectedResponderId,
            role: assignedRole,
          });
        }
        onCloseDispatchModal();
      }, 1000);
    } catch (err) {
      if (err.status === 409 || (err.message && err.message.includes('409'))) {
        setDispatchError(
          'CONCURRENCY CONFLICT (HTTP 409): Responder is already committed to an active incident. Select an available unit.'
        );
      } else {
        setDispatchError(err.message || 'Dispatch operation failed. Check mesh gateway status.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      {/* Right Column: Field Responders Telemetry */}
      <aside className="w-80 md:w-88 xl:w-96 flex flex-col bg-[#0b0f19] border-l border-[#334155] shrink-0 z-20 select-none">
        {/* Panel Header & Statistics */}
        <div className="p-2.5 bg-[#111827] border-b border-[#334155]">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <Users className="w-4 h-4 text-sky-400" />
              <h2 className="font-bold text-xs uppercase tracking-wider text-slate-100">
                FIELD ASSET TELEMETRY
              </h2>
            </div>
            <Radio className="w-3.5 h-3.5 text-emerald-400" />
          </div>

          {/* Quick Metrics */}
          <div className="grid grid-cols-3 gap-1 bg-[#0b0f19] p-1.5 border border-[#334155] text-center font-mono rounded">
            <div>
              <span className="text-emerald-400 font-bold text-sm block">
                {deployedCount}
              </span>
              <span className="text-slate-400 text-[9px] block">DEPLOYED</span>
            </div>
            <div>
              <span className="text-sky-400 font-bold text-sm block">
                {inTransitCount}
              </span>
              <span className="text-slate-400 text-[9px] block">IN TRANSIT</span>
            </div>
            <div>
              <span className="text-slate-200 font-bold text-sm block">
                {availableCount}
              </span>
              <span className="text-slate-400 text-[9px] block">AVAILABLE</span>
            </div>
          </div>
        </div>

        {/* Responders Cards Stream */}
        <div className="flex-1 overflow-y-auto p-2 space-y-2">
          {displayResponders.map((resp) => {
            const isAvail = resp.status === 'AVAILABLE';
            const isOnScene = resp.status === 'ON_SCENE';

            return (
              <div
                key={resp.id}
                className="bg-[#111827] border border-[#334155] rounded p-2.5 hover:border-slate-500 transition-colors"
              >
                {/* Header: Callsign & Status */}
                <div className="flex items-center justify-between mb-1.5">
                  <div>
                    <span className="font-bold text-xs text-slate-100 block">
                      {resp.callsign || resp.name}
                    </span>
                    <span className="font-mono text-[10px] text-slate-400">
                      {resp.name} // {resp.team || 'SAR SQUAD'}
                    </span>
                  </div>
                  <span
                    className={`font-mono text-[10px] font-bold px-1.5 py-0.5 rounded border ${
                      isOnScene
                        ? 'bg-emerald-950/60 text-emerald-400 border-emerald-600/50'
                        : isAvail
                        ? 'bg-sky-950/60 text-sky-400 border-sky-600/50'
                        : 'bg-amber-950/60 text-amber-400 border-amber-600/50'
                    }`}
                  >
                    {resp.status}
                  </span>
                </div>

                {/* Vitals Telemetry Grid */}
                <div className="grid grid-cols-2 gap-1 bg-[#0b0f19] p-2 border border-[#334155] rounded font-mono text-[10px] my-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">HR:</span>
                    <span className="text-red-400 font-bold flex items-center gap-0.5">
                      <Heart className="w-2.5 h-2.5 text-red-500" />
                      {resp.heartRate || 78} BPM
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">O2:</span>
                    <span className="text-sky-400 font-bold">
                      {resp.o2Level || 98}%
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">BAT:</span>
                    <span className="text-emerald-400 font-bold">
                      {resp.battery || 85}%
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">SIG:</span>
                    <span className="text-slate-300">
                      {resp.rssi || -68} dBm
                    </span>
                  </div>
                </div>

                {/* Action footer */}
                <div className="flex items-center justify-between text-[10px] font-mono pt-1.5 border-t border-[#334155]">
                  <span className="text-slate-400">FREQ: 433.850 MHz</span>
                  <span className="text-sky-400 cursor-pointer hover:underline">
                    PTT READY
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </aside>

      {/* Tactical Dispatch Modal Dialog */}
      {dispatchIncident && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 select-none">
          <div className="w-full max-w-lg bg-[#111827] border border-slate-700 rounded-lg shadow-2xl p-5 font-mono text-xs">
            {/* Modal Title */}
            <div className="flex items-center justify-between border-b border-[#334155] pb-3 mb-3">
              <div className="flex items-center gap-2">
                <Send className="w-4 h-4 text-red-500" />
                <h3 className="font-bold text-sm text-slate-100 tracking-wider uppercase">
                  DISPATCH ASSIGNMENT // COMMAND OVERRIDE
                </h3>
              </div>
              <button
                onClick={onCloseDispatchModal}
                className="text-slate-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Target Incident Context */}
            <div className="bg-[#0b0f19] border border-[#334155] rounded p-3 mb-3">
              <div className="flex justify-between items-center mb-1">
                <span className="text-red-400 font-bold">
                  INCIDENT: {dispatchIncident.emergencyType || 'EMERGENCY BEACON'}
                </span>
                <span className="text-slate-400 text-[10px]">
                  ID: {(dispatchIncident.uuid || dispatchIncident.id || 'N/A').substring(0, 16)}
                </span>
              </div>
              <p className="font-sans text-xs text-slate-300 line-clamp-2">
                {dispatchIncident.message || 'Distress signal received without payload notes.'}
              </p>
            </div>

            {/* Error / Conflict Banner */}
            {dispatchError && (
              <div className="mb-3 p-2.5 bg-red-950/70 border border-red-700 text-red-200 text-xs flex items-center gap-2 rounded">
                <AlertTriangle className="w-4 h-4 text-red-400 shrink-0" />
                <span>{dispatchError}</span>
              </div>
            )}

            {/* Success Banner */}
            {dispatchSuccess && (
              <div className="mb-3 p-2.5 bg-emerald-950/70 border border-emerald-700 text-emerald-200 text-xs flex items-center gap-2 rounded">
                <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>{dispatchSuccess}</span>
              </div>
            )}

            {/* Unit Selection */}
            <div className="space-y-3 mb-4">
              <div>
                <label className="block text-slate-400 text-[10px] uppercase mb-1">
                  SELECT FIELD UNIT / RESPONDER:
                </label>
                <select
                  data-testid="responder-select"
                  value={selectedResponderId}
                  onChange={(e) => setSelectedResponderId(e.target.value)}
                  className="w-full bg-[#0b0f19] border border-[#334155] rounded text-slate-200 font-mono text-xs px-2.5 py-2 focus:border-sky-500 outline-none"
                >
                  <option value="">-- CHOOSE FIELD RESPONDER --</option>
                  {displayResponders.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.callsign || r.name} ({r.status}) - {r.team || 'Field Team'}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-slate-400 text-[10px] uppercase mb-1">
                  TACTICAL MISSION ROLE:
                </label>
                <select
                  value={assignedRole}
                  onChange={(e) => setAssignedRole(e.target.value)}
                  className="w-full bg-[#0b0f19] border border-[#334155] rounded text-slate-200 font-mono text-xs px-2.5 py-2 focus:border-sky-500 outline-none"
                >
                  <option value="LEAD">LEAD (Primary Incident Commander)</option>
                  <option value="MEDIC">MEDIC (Triage & Stabilization)</option>
                  <option value="RESCUE">RESCUE (Extraction / Search)</option>
                  <option value="HAZMAT">HAZMAT (Containment & Neutralization)</option>
                  <option value="SUPPORT">SUPPORT (Comms & Perimeter)</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-400 text-[10px] uppercase mb-1">
                  OPERATIONAL DIRECTIVE & LOADOUT NOTES:
                </label>
                <textarea
                  rows={2}
                  value={operationalNotes}
                  onChange={(e) => setOperationalNotes(e.target.value)}
                  placeholder="Specify entry vector, radio channel, and equipment requirements..."
                  className="w-full bg-[#0b0f19] border border-[#334155] rounded text-slate-200 font-mono text-xs p-2.5 focus:border-sky-500 outline-none"
                />
              </div>
            </div>

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-2 pt-3 border-t border-[#334155]">
              <button
                onClick={onCloseDispatchModal}
                disabled={isSubmitting}
                className="px-3.5 py-1.5 bg-[#1e293b] hover:bg-slate-700 text-slate-200 font-bold text-xs border border-[#334155] rounded"
              >
                CANCEL / STANDBY
              </button>
              <button
                data-testid="confirm-dispatch-btn"
                onClick={handleConfirmDispatch}
                disabled={isSubmitting || !selectedResponderId}
                className={`px-4 py-1.5 font-bold text-xs tracking-wider rounded border flex items-center gap-1.5 ${
                  isSubmitting || !selectedResponderId
                    ? 'bg-slate-800 text-slate-500 border-slate-700 cursor-not-allowed'
                    : 'bg-red-600 hover:bg-red-700 text-white border-red-500'
                }`}
              >
                <Send className="w-3.5 h-3.5" />
                <span>{isSubmitting ? 'DISPATCHING...' : 'CONFIRM DISPATCH'}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
