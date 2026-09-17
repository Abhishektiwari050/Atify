package io.github.sekademi.spotufi.connect

/**
 * Embedded single-page web app for controlling Atify from any browser on the local Wi-Fi.
 * Self-contained HTML, CSS, and JS with zero external dependencies.
 */
object WebRemoteAssets {
    const val HTML: String = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Atify Web Remote</title>
<style>
  :root {
    --bg-primary: #0E0E13;
    --bg-card: #181820;
    --accent: #1ED760;
    --text-primary: #FFFFFF;
    --text-secondary: #A0A0B0;
    --border: #262632;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
  body {
    background-color: var(--bg-primary);
    color: var(--text-primary);
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    padding: 20px;
  }
  .player-card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: 20px;
    padding: 30px;
    width: 100%;
    max-width: 440px;
    box-shadow: 0 20px 50px rgba(0,0,0,0.6);
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .header {
    width: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
  }
  .brand {
    font-size: 14px;
    font-weight: 800;
    letter-spacing: 1.5px;
    color: var(--accent);
    text-transform: uppercase;
  }
  .status-badge {
    font-size: 11px;
    background: #1C2B22;
    color: var(--accent);
    padding: 4px 10px;
    border-radius: 20px;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .dot { width: 6px; height: 6px; background: var(--accent); border-radius: 50%; }
  .artwork-container {
    width: 240px;
    height: 240px;
    border-radius: 14px;
    overflow: hidden;
    margin-bottom: 24px;
    box-shadow: 0 12px 30px rgba(0,0,0,0.5);
    background: #242430;
  }
  .artwork-container img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .track-info {
    text-align: center;
    width: 100%;
    margin-bottom: 20px;
  }
  .title {
    font-size: 20px;
    font-weight: 700;
    margin-bottom: 6px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .artist {
    font-size: 14px;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .progress-container {
    width: 100%;
    margin-bottom: 20px;
  }
  .slider {
    width: 100%;
    accent-color: var(--accent);
    cursor: pointer;
  }
  .time-labels {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 4px;
  }
  .controls {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 24px;
    margin-bottom: 24px;
  }
  .btn {
    background: none;
    border: none;
    color: var(--text-primary);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.1s ease;
  }
  .btn:active { transform: scale(0.92); }
  .btn-play {
    width: 58px;
    height: 58px;
    background: var(--text-primary);
    color: #000;
    border-radius: 50%;
  }
  .btn-play svg { fill: #000; width: 24px; height: 24px; }
  .btn-skip svg { fill: var(--text-primary); width: 28px; height: 28px; }
  .volume-container {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding-top: 16px;
    border-top: 1px solid var(--border);
  }
  .volume-icon svg { fill: var(--text-secondary); width: 20px; height: 20px; }
</style>
</head>
<body>
<div class="player-card">
  <div class="header">
    <div class="brand">Atify Connect</div>
    <div class="status-badge"><div class="dot"></div> Live Wi-Fi</div>
  </div>
  <div class="artwork-container">
    <img id="cover" src="" alt="Album Art" onerror="this.src='data:image/svg+xml;utf8,<svg xmlns=\'http://www.w3.org/2000/svg\' width=\'240\' height=\'240\' fill=\'%23333\'><rect width=\'100%25\' height=\'100%25\'/></svg>'">
  </div>
  <div class="track-info">
    <div class="title" id="title">Connecting...</div>
    <div class="artist" id="artist">Atify Music Player</div>
  </div>
  <div class="progress-container">
    <input type="range" id="seek-bar" class="slider" min="0" max="100" value="0">
    <div class="time-labels">
      <span id="current-time">0:00</span>
      <span id="duration-time">0:00</span>
    </div>
  </div>
  <div class="controls">
    <button class="btn btn-skip" id="prev-btn">
      <svg viewBox="0 0 24 24"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>
    </button>
    <button class="btn btn-play" id="play-btn">
      <svg id="play-icon" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
    </button>
    <button class="btn btn-skip" id="next-btn">
      <svg viewBox="0 0 24 24"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
    </button>
  </div>
  <div class="volume-container">
    <div class="volume-icon">
      <svg viewBox="0 0 24 24"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg>
    </div>
    <input type="range" id="volume-bar" class="slider" min="0" max="100" value="80">
  </div>
</div>
<script>
  let isDragging = false;
  function formatTime(ms) {
    if (!ms || isNaN(ms)) return '0:00';
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const remSec = s % 60;
    return m + ':' + (remSec < 10 ? '0' : '') + remSec;
  }

  function sendAction(action, value = 0) {
    fetch('/api/action', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, value })
    }).catch(console.error);
  }

  document.getElementById('play-btn').onclick = () => sendAction('toggle_play');
  document.getElementById('next-btn').onclick = () => sendAction('next');
  document.getElementById('prev-btn').onclick = () => sendAction('prev');

  const seekBar = document.getElementById('seek-bar');
  seekBar.onmousedown = () => isDragging = true;
  seekBar.ontouchstart = () => isDragging = true;
  seekBar.onchange = () => {
    isDragging = false;
    sendAction('seek', Number(seekBar.value));
  };

  const volumeBar = document.getElementById('volume-bar');
  volumeBar.oninput = () => sendAction('volume', Number(volumeBar.value));

  async function updateState() {
    try {
      const res = await fetch('/api/state');
      if (!res.ok) return;
      const data = await res.json();
      document.getElementById('title').textContent = data.title || 'Not Playing';
      document.getElementById('artist').textContent = data.artist || 'Atify';
      if (data.coverUri) document.getElementById('cover').src = data.coverUri;
      
      const playIcon = document.getElementById('play-icon');
      if (data.isPlaying) {
        playIcon.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
      } else {
        playIcon.innerHTML = '<path d="M8 5v14l11-7z"/>';
      }

      if (!isDragging && data.durationMs > 0) {
        seekBar.max = data.durationMs;
        seekBar.value = data.positionMs;
        document.getElementById('current-time').textContent = formatTime(data.positionMs);
        document.getElementById('duration-time').textContent = formatTime(data.durationMs);
      }
    } catch (e) {
      console.error(e);
    }
  }

  setInterval(updateState, 800);
  updateState();
</script>
</body>
</html>"""
}
