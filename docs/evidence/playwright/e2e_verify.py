import json
import urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright

EVIDENCE_DIR = Path("docs/evidence/playwright")
EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)

API_BASE = "http://127.0.0.1:8000"
WEB_BASE = "http://127.0.0.1:5173"
OP_KEY = "meshlink_op_dev_secret"
GW_KEY = "meshlink_gw_dev_secret"


def api_call(path, method="GET", body=None, headers=None):
    hdrs = {"Authorization": f"Bearer {OP_KEY}", "Content-Type": "application/json"}
    if headers:
        hdrs.update(headers)
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(f"{API_BASE}{path}", data=data, headers=hdrs, method=method)
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def run():
    console_errors = []
    page_errors = []
    results = {}

    # Always seed a fresh AVAILABLE responder so dispatch succeeds cleanly without hitting prior test's 409 lock
    ts_tag = int(__import__("time").time()) % 100000
    teams = api_call("/api/v1/teams")
    team_id = teams[0]["id"] if teams else api_call("/api/v1/teams", method="POST", body={"name": "Alpha Rescue", "callsign": f"TEAM-{ts_tag}"})["id"]
    fresh_callsign = f"SAR-{ts_tag}"
    resp = api_call("/api/v1/responders", method="POST", body={
        "callsign": fresh_callsign,
        "responder_type": "SAR",
        "skills": ["MEDIC", "RESCUE"],
    })
    api_call(f"/api/v1/teams/{team_id}/members", method="POST", body={"responder_id": resp["id"], "role_in_team": "LEAD"})
    results["seeded_responder"] = resp["id"]
    results["seeded_callsign"] = fresh_callsign

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()
        page.on("console", lambda msg: console_errors.append(f"{msg.type}: {msg.text}") if msg.type == "error" else None)
        page.on("pageerror", lambda err: page_errors.append(str(err)))

        page.goto(WEB_BASE, wait_until="networkidle")
        page.screenshot(path=str(EVIDENCE_DIR / "01_login.png"))

        # 1. Negative auth test
        page.get_by_test_id("token-input").fill("invalid_operator_key_999")
        page.get_by_test_id("auth-submit").click()
        page.get_by_test_id("auth-error").wait_for(state="visible", timeout=5000)
        results["invalid_login_banner"] = page.get_by_test_id("auth-error").inner_text().replace("\n", " | ")

        # 2. Valid auth
        page.get_by_test_id("token-input").fill(OP_KEY)
        page.get_by_test_id("auth-submit").click()
        page.get_by_test_id("ws-status-badge").wait_for(state="visible", timeout=10000)
        page.wait_for_function(
            "() => document.querySelector('[data-testid=\"ws-status-badge\"]')?.innerText.includes('WSS LIVE')",
            timeout=10000,
        )
        results["ws_live_initial"] = page.get_by_test_id("ws-status-badge").inner_text()

        # 3. Live SOS via gateway API -> check WS broadcast arrival in UI (<3s)
        sos_uuid = f"e2e-sos-{int(__import__('time').time())}"
        unique_msg = f"Playwright live WS E2E incident {sos_uuid}"
        ingest_res = api_call(
            "/api/sync/sos",
            method="POST",
            headers={"Authorization": f"Bearer {GW_KEY}", "X-Gateway-Key": GW_KEY},
            body=[{
                "uuid": sos_uuid,
                "senderId": "gw-playwright-e2e",
                "senderName": "Playwright Test",
                "emergencyType": "MEDICAL",
                "message": unique_msg,
                "status": "ACTIVE",
                "gpsFixAvailable": True,
                "timestamp": int(__import__("time").time() * 1000),
                "severity": 9,
                "hopCount": 0,
                "latitude": 12.9716,
                "longitude": 77.5946,
            }],
        )
        results["sos_ingest"] = ingest_res
        page.get_by_text(unique_msg).wait_for(state="visible", timeout=5000)
        results["ws_live_sos_visible"] = True
        page.screenshot(path=str(EVIDENCE_DIR / "02_desktop_live_sos.png"))

        # 4. Dispatch responder via UI modal
        card = page.get_by_text(unique_msg).first
        card.scroll_into_view_if_needed()
        # Click DISPATCH button on the card or top bar
        dispatch_btns = page.locator("button:has-text('DISPATCH')")
        dispatch_btns.first.click()
        page.get_by_test_id("responder-select").wait_for(state="visible", timeout=5000)
        options = page.get_by_test_id("responder-select").locator("option")
        opt_count = options.count()
        results["responder_options_count"] = opt_count
        if opt_count > 1:
            page.get_by_test_id("responder-select").select_option(resp["id"])
            page.get_by_test_id("confirm-dispatch-btn").click()
            page.get_by_text("Tactical assignment successfully committed").wait_for(state="visible", timeout=5000)
            results["dispatch_committed"] = True
        page.screenshot(path=str(EVIDENCE_DIR / "03_desktop_dispatched.png"))

        # 5. Measure desktop & mobile 390x844 geometry / z-index / touch targets
        page.set_viewport_size({"width": 390, "height": 844})
        page.wait_for_timeout(300)
        page.screenshot(path=str(EVIDENCE_DIR / "04_mobile_queue_390x844.png"))
        page.get_by_test_id("mobile-tab-map").click()
        page.wait_for_timeout(400)
        page.screenshot(path=str(EVIDENCE_DIR / "05_mobile_map_390x844.png"))
        layout_audit = page.evaluate("""() => {
            const docW = document.documentElement.clientWidth;
            const overflowX = document.documentElement.scrollWidth > docW;
            const mapEl = document.querySelector('.leaflet-container');
            const mapRect = mapEl ? mapEl.getBoundingClientRect() : null;
            const visibleSmallBtns = Array.from(document.querySelectorAll('button')).map(b => {
                const r = b.getBoundingClientRect();
                return { text: b.innerText.trim().slice(0, 24), w: Math.round(r.width), h: Math.round(r.height) };
            }).filter(b => b.w > 0 && b.h > 0 && (b.h < 32));
            return {
                docW,
                overflowX,
                mapRect: mapRect ? { w: Math.round(mapRect.width), h: Math.round(mapRect.height), x: Math.round(mapRect.x) } : null,
                tinyButtonsCount: visibleSmallBtns.length,
                tinyButtonsSample: visibleSmallBtns.slice(0, 10),
            };
        }""")
        results["mobile_layout_audit"] = layout_audit

        # 6. WS drop & reconnect recovery when backend container stops/restarts
        page.set_viewport_size({"width": 1440, "height": 900})
        import subprocess
        subprocess.run(["docker", "compose", "restart", "backend"], check=True)
        page.wait_for_function(
            "() => !document.querySelector('[data-testid=\"ws-status-badge\"]')?.innerText.includes('WSS LIVE')",
            timeout=15000,
        )
        results["ws_dropped_state"] = page.get_by_test_id("ws-status-badge").inner_text()
        page.wait_for_function(
            "() => document.querySelector('[data-testid=\"ws-status-badge\"]')?.innerText.includes('WSS LIVE')",
            timeout=30000,
        )
        results["ws_recovered_state"] = page.get_by_test_id("ws-status-badge").inner_text()
        page.screenshot(path=str(EVIDENCE_DIR / "06_ws_recovered.png"))

        browser.close()

    results["console_errors"] = console_errors
    results["page_errors"] = page_errors
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    run()
