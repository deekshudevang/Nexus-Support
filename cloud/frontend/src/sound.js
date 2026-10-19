// Audio notification utility using Web Audio API (Offline, Zero Assets)

export function playEmergencyAlert(severity = 8) {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;

    const ctx = new AudioContextClass();
    const now = ctx.currentTime;

    // High severity (>= 8) produces urgent two-tone alert
    const isCritical = severity >= 8;

    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();

    osc1.type = isCritical ? 'sawtooth' : 'sine';
    osc2.type = 'sine';

    osc1.frequency.setValueAtTime(isCritical ? 960 : 660, now);
    osc1.frequency.setValueAtTime(isCritical ? 720 : 550, now + 0.15);
    osc1.frequency.setValueAtTime(isCritical ? 960 : 660, now + 0.3);

    gain.gain.setValueAtTime(0.25, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + (isCritical ? 0.6 : 0.35));

    osc1.connect(gain);
    gain.connect(ctx.destination);

    osc1.start(now);
    osc1.stop(now + (isCritical ? 0.6 : 0.35));
  } catch (err) {
    // Audio autoplay or permissions policy may block before interaction
  }
}

export function requestNotificationPermission() {
  if (typeof window !== 'undefined' && 'Notification' in window) {
    if (Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }
}

export function sendBrowserNotification(title, body, severity) {
  if (typeof window !== 'undefined' && 'Notification' in window) {
    if (Notification.permission === 'granted') {
      try {
        new Notification(`[SEV ${severity}] ${title}`, {
          body: body,
          icon: '/favicon.ico',
          tag: 'meshlink-alert',
          requireInteraction: severity >= 8,
        });
      } catch (e) {
        // Ignored if notifications fail
      }
    }
  }
}
