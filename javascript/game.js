/* The Lost Echo — full JavaScript port of the Android game.
 * All game logic (map gen, A*, vision/hearing AI, guards/drones/turrets,
 * acoustic puzzles, dialogue, quests, saves, endings) in one dependency-free
 * file. Runs in a WebView or desktop browser.
 */
'use strict';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------
const TILE = 32;
const TILE_FLOOR = 0, TILE_WALL = 1, TILE_DECOR = 2;
const SURFACE_CONCRETE = 0, SURFACE_METAL = 1, SURFACE_GRASS = 2;
const MODE_IDLE = 0, MODE_WALK = 1, MODE_SNEAK = 2, MODE_RUN = 3;
const AREA_SLUM = 0, AREA_LAB = 1, AREA_BASEMENT = 2, AREA_DATA = 3;
const TAU = Math.PI * 2;
const TONE_LOW = 0, TONE_MID = 1, TONE_HIGH = 2;
const TONE_FREQ = [220, 330, 440];
const CHOICE_SAVE_PARENTS = 1, CHOICE_PARALYZE_CITY = 2;

// ---------------------------------------------------------------------------
// Tile map
// ---------------------------------------------------------------------------
class TileMap {
  constructor() {
    this.tiles = null; this.coll = null; this.shadow = null; this.surf = null;
    this.mapW = 0; this.mapH = 0;
    this.camX = 48; this.camY = 48;
    this.viewW = 800; this.viewH = 600;
    this.parallax = true;
    this.areaSurface = SURFACE_CONCRETE;
  }
  setViewSize(w, h) { this.viewW = w; this.viewH = h; }

  generateMap(w, h, seed, surface) {
    this.mapW = w; this.mapH = h; this.areaSurface = surface;
    this.tiles = new Uint8Array(w * h).fill(TILE_FLOOR);
    const rng = this._rng(seed);
    const idx = (x, y) => y * w + x;
    for (let x = 0; x < w; x++) { this.tiles[idx(x, 0)] = TILE_WALL; this.tiles[idx(x, h - 1)] = TILE_WALL; }
    for (let y = 0; y < h; y++) { this.tiles[idx(0, y)] = TILE_WALL; this.tiles[idx(w - 1, y)] = TILE_WALL; }
    const rooms = [];
    const roomCount = 5 + Math.floor(rng() * 4);
    for (let i = 0; i < roomCount; i++) {
      const rw = 6 + Math.floor(rng() * 7), rh = 5 + Math.floor(rng() * 6);
      const rx = 2 + Math.floor(rng() * Math.max(1, w - rw - 4));
      const ry = 2 + Math.floor(rng() * Math.max(1, h - rh - 4));
      rooms.push([rx, ry, rw, rh]);
      for (let x = rx; x < rx + rw; x++)
        for (let y = ry; y < ry + rh; y++)
          if (x > 0 && y > 0 && x < w - 1 && y < h - 1) this.tiles[idx(x, y)] = TILE_FLOOR;
    }
    for (let i = 1; i < roomCount; i++) {
      const a = rooms[i], b = rooms[i - 1];
      const sx = a[0] + (a[2] >> 1), sy = a[1] + (a[3] >> 1);
      const tx = b[0] + (b[2] >> 1), ty = b[1] + (b[3] >> 1);
      this._carve(sx, sy, tx, sy); this._carve(tx, sy, tx, ty);
    }
    const segs = 3 + Math.floor(rng() * 3);
    for (let i = 0; i < segs; i++) {
      const sx = 3 + Math.floor(rng() * (w - 8)), sy = 3 + Math.floor(rng() * (h - 8));
      const len = 4 + Math.floor(rng() * 6), horiz = rng() < 0.5;
      for (let j = 0; j < len; j++) {
        const x = horiz ? sx + j : sx, y = horiz ? sy : sy + j;
        if (x > 1 && y > 1 && x < w - 2 && y < h - 2) this.tiles[idx(x, y)] = TILE_WALL;
      }
    }
    const dec = (w * h / 40) | 0;
    for (let i = 0; i < dec; i++) {
      const x = 2 + Math.floor(rng() * (w - 4)), y = 2 + Math.floor(rng() * (h - 4));
      if (this.tiles[idx(x, y)] === TILE_FLOOR) this.tiles[idx(x, y)] = TILE_DECOR;
    }
    this._derived();
  }
  _rng(seed0) {
    let s = seed0 >>> 0;
    return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
  }
  _carve(x0, y0, x1, y1) {
    const w = this.mapW, h = this.mapH;
    const sx = x1 > x0 ? 1 : -1;
    for (let x = x0; x !== x1; x += sx) { this.tiles[y0 * w + x] = TILE_FLOOR; if (y0 + 1 < h) this.tiles[(y0 + 1) * w + x] = TILE_FLOOR; }
    const sy = y1 > y0 ? 1 : -1;
    for (let y = y0; y !== y1; y += sy) { this.tiles[y * w + x1] = TILE_FLOOR; if (x1 + 1 < w) this.tiles[y * w + x1 + 1] = TILE_FLOOR; }
  }
  _derived() {
    const w = this.mapW, h = this.mapH;
    this.coll = new Uint8Array(w * h);
    this.shadow = new Uint8Array(w * h);
    this.surf = new Uint8Array(w * h).fill(this.areaSurface);
    for (let x = 0; x < w; x++) for (let y = 0; y < h; y++) {
      const i = y * w + x;
      this.coll[i] = this.tiles[i] === TILE_WALL ? 1 : 0;
      if (this.tiles[i] !== TILE_WALL) {
        this.shadow[i] = (x > 0 && this.tiles[(y) * w + x - 1] === TILE_WALL) || (x < w - 1 && this.tiles[y * w + x + 1] === TILE_WALL) ||
          (y > 0 && this.tiles[(y - 1) * w + x] === TILE_WALL) || (y < h - 1 && this.tiles[(y + 1) * w + x] === TILE_WALL) ? 1 : 0;
      }
    }
  }
  isBlocked(tx, ty) {
    if (tx < 0 || ty < 0 || tx >= this.mapW || ty >= this.mapH) return true;
    return this.coll[ty * this.mapW + tx] === 1;
  }
  isWalkablePixel(x, y) { return !this.isBlocked(Math.floor(x / TILE), Math.floor(y / TILE)); }
  isWalkableRect(l, t, r, b) {
    return this.isWalkablePixel(l, t) && this.isWalkablePixel(r, t) &&
      this.isWalkablePixel(l, b) && this.isWalkablePixel(r, b);
  }
  isShadowPixel(x, y) {
    const tx = Math.floor(x / TILE), ty = Math.floor(y / TILE);
    if (tx < 0 || ty < 0 || tx >= this.mapW || ty >= this.mapH) return false;
    return this.shadow[ty * this.mapW + tx] === 1;
  }
  getSurfaceAt(x, y) {
    const tx = Math.floor(x / TILE), ty = Math.floor(y / TILE);
    if (tx < 0 || ty < 0 || tx >= this.mapW || ty >= this.mapH) return this.areaSurface;
    return this.surf[ty * this.mapW + tx];
  }
  isFloorTile(tx, ty) { return !this.isBlocked(tx, ty); }
  setBlocked(tx, ty, b) {
    if (tx < 0 || ty < 0 || tx >= this.mapW || ty >= this.mapH) return;
    const i = ty * this.mapW + tx;
    this.coll[i] = b ? 1 : 0;
    if (!b) this.shadow[i] = 0;
  }
  openDoorTile(tx, ty) {
    if (tx < 0 || ty < 0 || tx >= this.mapW || ty >= this.mapH) return;
    const i = ty * this.mapW + tx;
    this.tiles[i] = TILE_FLOOR; this.coll[i] = 0; this.shadow[i] = 0;
  }
  mapWidthPx() { return this.mapW * TILE; }
  mapHeightPx() { return this.mapH * TILE; }
  updateCamera(tx, ty) {
    const dz = 120;
    let cx = this.camX, cy = this.camY;
    if (tx < cx + dz) cx += tx - (cx + dz);
    else if (tx > cx + this.viewW - dz) cx += tx - (cx + this.viewW - dz);
    if (ty < cy + dz) cy += ty - (cy + dz);
    else if (ty > cy + this.viewH - dz) cy += ty - (cy + this.viewH - dz);
    this.camX = Math.max(0, Math.min(Math.max(0, this.mapWidthPx() - this.viewW), cx));
    this.camY = Math.max(0, Math.min(Math.max(0, this.mapHeightPx() - this.viewH), cy));
  }
}

// ---------------------------------------------------------------------------
// Pathfinding (A*, 4-connected)
// ---------------------------------------------------------------------------
class AStar {
  constructor(map) { this.map = map; }
  findPath(sx, sy, tx, ty) {
    const map = this.map, w = map.mapW, h = map.mapH;
    let x0 = Math.floor(sx / TILE), y0 = Math.floor(sy / TILE);
    let x1 = Math.floor(tx / TILE), y1 = Math.floor(ty / TILE);
    const path = [];
    if (map.isBlocked(x1, y1)) {
      const n = this._nearest(x1, y1);
      if (!n) return path;
      x1 = n[0]; y1 = n[1];
    }
    if (x0 === x1 && y0 === y1) { path.push([x0, y0]); return path; }
    const closed = new Uint8Array(w * h);
    const g = new Float64Array(w * h).fill(Infinity);
    const f = new Float64Array(w * h).fill(Infinity);
    const parent = new Int32Array(w * h).fill(-1);
    const key = (x, y) => y * w + x;
    g[key(x0, y0)] = 0;
    f[key(x0, y0)] = Math.abs(x1 - x0) + Math.abs(y1 - y0);
    const open = [[x0, y0]];
    const target = key(x1, y1);
    let found = -1;
    let head = 0, scans = 0;
    while (head < open.length && scans++ < 2400) {
      let bi = -1, bf = Infinity;
      for (let i = head; i < open.length; i++) {
        const fi = f[key(open[i][0], open[i][1])];
        if (fi < bf) { bf = fi; bi = i; }
      }
      if (bi < 0) break;
      const [nx, ny] = open[bi];
      open[bi] = open[open.length - 1]; open.pop();
      if (closed[key(nx, ny)]) continue;
      closed[key(nx, ny)] = 1;
      if (key(nx, ny) === target) { found = key(nx, ny); break; }
      const ng = g[key(nx, ny)] + 1;
      for (const [ax, ay] of [[nx + 1, ny], [nx - 1, ny], [nx, ny + 1], [nx, ny - 1]]) {
        if (ax < 0 || ay < 0 || ax >= w || ay >= h) continue;
        if (map.isBlocked(ax, ay) || closed[key(ax, ay)]) continue;
        if (ng < g[key(ax, ay)]) {
          g[key(ax, ay)] = ng; f[key(ax, ay)] = ng + Math.abs(x1 - ax) + Math.abs(y1 - ay);
          parent[key(ax, ay)] = key(nx, ny);
          open.push([ax, ay]);
        }
      }
    }
    if (found === -1 && parent[target] === -1) return path;
    const out = [];
    let cur = found === -1 ? target : found;
    while (cur !== -1 && out.length < 400) {
      out.push([cur % w, (cur / w) | 0]);
      if (cur === key(x0, y0)) break;
      cur = parent[cur];
    }
    out.reverse();
    return out;
  }
  _nearest(x, y) {
    const w = this.map.mapW, h = this.map.mapH;
    for (let r = 1; r <= 4; r++)
      for (let dy = -r; dy <= r; dy++)
        for (let dx = -r; dx <= r; dx++) {
          const nx = x + dx, ny = y + dy;
          if (nx >= 0 && ny >= 0 && nx < w && ny < h && !this.map.isBlocked(nx, ny)) return [nx, ny];
        }
    return null;
  }
}

// ---------------------------------------------------------------------------
// Vision + hearing
// ---------------------------------------------------------------------------
function normAngle(a) { while (a > Math.PI) a -= TAU; while (a < -Math.PI) a += TAU; return a; }

function rayClear(map, x0, y0, x1, y1) {
  const t0x = Math.floor(x0 / TILE), t0y = Math.floor(y0 / TILE);
  const t1x = Math.floor(x1 / TILE), t1y = Math.floor(y1 / TILE);
  const stepX = t1x > t0x ? 1 : (t1x < t0x ? -1 : 0);
  const stepY = t1y > t0y ? 1 : (t1y < t0y ? -1 : 0);
  const dx = x1 - x0, dy = y1 - y0;
  let tMaxX, tMaxY, tDelX, tDelY;
  if (stepX !== 0) { tMaxX = ((stepX > 0 ? t0x + 1 : t0x) * TILE - x0) / dx; tDelX = TILE / Math.abs(dx); }
  else { tMaxX = Infinity; tDelX = Infinity; }
  if (stepY !== 0) { tMaxY = ((stepY > 0 ? t0y + 1 : t0y) * TILE - y0) / dy; tDelY = TILE / Math.abs(dy); }
  else { tMaxY = Infinity; tDelY = Infinity; }
  let cx = t0x, cy = t0y;
  for (;;) {
    if (map.isBlocked(cx, cy)) return false;
    if (cx === t1x && cy === t1y) break;
    if (tMaxX < tMaxY) { tMaxX += tDelX; cx += stepX; } else { tMaxY += tDelY; cy += stepY; }
    if (cx < 0 || cy < 0 || cx >= map.mapW || cy >= map.mapH) break;
  }
  return true;
}

function canSee(map, ex, ey, facing, cone, range, px, py) {
  const dx = px - ex, dy = py - ey;
  const d2 = dx * dx + dy * dy;
  if (d2 > range * range) return false;
  if (d2 < 0.0001) return true;
  const d = Math.sqrt(d2);
  if (Math.abs(normAngle(Math.atan2(dy, dx) - facing)) > cone * 0.5) return false;
  return rayClear(map, ex, ey, ex + dx / d * (d - 8), ey + dy / d * (d - 8));
}

function hears(ex, ey, px, py, radius, mode) {
  const mult = [0.5, 1.0, 0.5, 1.5][mode];
  return (px - ex) * (px - ex) + (py - ey) * (py - ey) <= (radius * mult) * (radius * mult);
}

// ---------------------------------------------------------------------------
// Audio (tiny WebAudio synth)
// ---------------------------------------------------------------------------
class AudioSys {
  constructor() {
    this.ctx = null; this.sfxV = 0.8; this.droneGain = null;
    try { this.ctx = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { this.ctx = null; }
    if (this.ctx) {
      this.droneGain = this.ctx.createGain(); this.droneGain.gain.value = 0;
      const o = this.ctx.createOscillator(); o.type = 'triangle'; o.frequency.value = 55;
      o.connect(this.droneGain); this.droneGain.connect(this.ctx.destination); o.start();
    }
  }
  resume() { if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }
  tone(freq, dur, vol, slideTo) {
    if (!this.ctx) return;
    const t = this.ctx.currentTime;
    const o = this.ctx.createOscillator(), g = this.ctx.createGain();
    o.type = 'sine'; o.frequency.setValueAtTime(freq, t);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(Math.max(1, slideTo), t + dur);
    g.gain.setValueAtTime(Math.max(0.001, (vol || 0.2) * this.sfxV), t);
    g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g); g.connect(this.ctx.destination); o.start(t); o.stop(t + dur);
  }
  sfx(kind) {
    if (kind === 'menu') this.tone(900, 0.05, 0.1);
    else if (kind === 'sonar') this.tone(240, 0.5, 0.3, 700);
    else if (kind === 'collect') { this.tone(700, 0.08, 0.18); this.tone(1050, 0.1, 0.18); }
    else if (kind === 'open') this.tone(300, 0.22, 0.18, 500);
    else if (kind === 'locked') { this.tone(160, 0.12, 0.18); this.tone(120, 0.18, 0.18); }
    else if (kind === 'alarm') { this.tone(520, 0.16, 0.22); setTimeout(() => this.tone(390, 0.16, 0.22), 170); }
    else if (kind === 'stun') this.tone(1000, 0.22, 0.22, 200);
    else if (kind === 'interact') this.tone(640, 0.08, 0.12);
    else if (kind === 'turret') this.tone(140, 0.18, 0.22, 60);
    else if (kind === 'alert') this.tone(880, 0.13, 0.18);
  }
  footstep(sneak) { this.tone(sneak ? 140 : 110, 0.05, sneak ? 0.04 : 0.08, 70); }
  setDroneHum(v) { if (this.droneGain && this.ctx) this.droneGain.gain.setTargetAtTime(Math.min(0.1, v * 0.1), this.ctx.currentTime, 0.3); }
  playTone(t) { this.tone(TONE_FREQ[t] || 220, 0.28, 0.22); }
}

// ---------------------------------------------------------------------------
// Player
// ---------------------------------------------------------------------------
class Player {
  constructor(game) {
    this.game = game; this.map = game.map;
    this.x = 200; this.y = 200; this.rot = 0;
    this.w = 20; this.h = 20;
    this.stamina = 100; this.cooldown = 0; this.invuln = 0; this.anim = 0;
    this.moveTarget = null; this.repath = 0; this.path = []; this.pathIdx = 0;
    this.sneaking = false; this.hidden = false; this.mode = MODE_IDLE;
    this.checkX = 200; this.checkY = 200; this.footT = 0;
    this.astar = new AStar(this.map);
    this.pulsePending = false; this.interactPending = false; this.stepPending = false; this.stepSneak = false;
  }
  update(dt) {
    if (this.cooldown > 0) this.cooldown -= dt;
    if (this.invuln > 0) this.invuln -= dt;
    this.anim += dt;
    if (this.stamina < 100) this.stamina = Math.min(100, this.stamina + 5 * dt);
    let mx = 0, my = 0, moving = false;
    if (this.moveTarget) {
      const t = this._follow(dt);
      if (t) {
        const dx = t[0] - this.x, dy = t[1] - this.y, d = Math.hypot(dx, dy);
        if (d > 4) { mx = dx / d; my = dy / d; moving = true; }
      } else this.moveTarget = null;
    }
    if (this.sneaking && moving) this.stamina = Math.max(0, this.stamina - 0.5 * dt);
    const speed = this.sneaking ? 95 : 215;
    if (moving) {
      this.rot += normAngle(Math.atan2(my, mx) - this.rot) * Math.min(1, dt * 12);
      const nx = this.x + mx * speed * dt, ny = this.y + my * speed * dt;
      if (this.map.isWalkableRect(nx - this.w / 2, this.y - this.h / 2, nx + this.w / 2, this.y + this.h / 2)) this.x = nx;
      if (this.map.isWalkableRect(this.x - this.w / 2, ny - this.h / 2, this.x + this.w / 2, ny + this.h / 2)) this.y = ny;
      this.footT -= dt;
      if (this.footT <= 0) { this.footT = this.sneaking ? 0.55 : 0.32; this.stepPending = true; this.stepSneak = this.sneaking; }
    } else this.footT = 0;
    const h = TILE / 2;
    this.x = Math.max(h, Math.min(this.map.mapWidthPx() - h, this.x));
    this.y = Math.max(h, Math.min(this.map.mapHeightPx() - h, this.y));
    this.hidden = this.sneaking && this.map.isShadowPixel(this.x, this.y);
    this.mode = this.sneaking ? MODE_SNEAK : (this.moveTarget ? MODE_RUN : MODE_IDLE);
  }
  _follow(dt) {
    if (this.repath <= 0) {
      this.repath = 0.5;
      this.path = this.astar.findPath(this.x, this.y, this.moveTarget.x, this.moveTarget.y) || [];
      this.pathIdx = 0;
    } else this.repath -= dt;
    while (this.pathIdx < this.path.length) {
      const [px, py] = this.path[this.pathIdx];
      const tx = px * TILE + TILE / 2, ty = py * TILE + TILE / 2;
      const d = Math.hypot(tx - this.x, ty - this.y);
      if (d < 8) { this.pathIdx++; continue; }
      return [tx, ty];
    }
    return null;
  }
  setMoveTarget(x, y) { this.moveTarget = { x, y }; this.repath = 0; }
  respawn() {
    this.x = this.checkX; this.y = this.checkY; this.moveTarget = null;
    this.stamina = 100; this.cooldown = 0; this.invuln = 1.5;
  }
}

// ---------------------------------------------------------------------------
// Enemies
// ---------------------------------------------------------------------------
class Enemy {
  constructor(game, kind) {
    this.game = game; this.kind = kind;
    this.x = 0; this.y = 0; this.active = true;
    this.w = kind === 'drone' ? 34 : 20;
    this.state = 'PATROL';
    this.waypoints = []; this.wi = 0; this.wp = 0; this.look = 0;
    this.rot = 0; this.anim = 0;
    this.sightT = 0; this.suspT = 0; this.alarmT = 0; this.lostT = 0; this.stunT = 0;
    this.lastX = 0; this.lastY = 0; this.startX = 0; this.startY = 0;
    this.path = []; this.pathIdx = 0; this.repath = 0;
    this.alert = false;
    this.astar = new AStar(game.map);
  }
  init(x, y) {
    this.x = x; this.y = y; this.startX = x; this.startY = y;
    this.active = true; this.state = 'PATROL';
    this.sightT = this.suspT = this.alarmT = this.lostT = this.stunT = 0;
    this.alert = false; this.path = [];
  }
  setWaypoints(pts) {
    this.waypoints = pts;
    this.wi = 0;
    if (pts.length) this.rot = Math.atan2(pts[0][1] - this.y, pts[0][0] - this.x);
  }
  _linear(tx, ty, dt, spd) {
    const dx = tx - this.x, dy = ty - this.y, d = Math.hypot(dx, dy);
    if (d < 8) return;
    const step = Math.min(spd * dt, d), h = this.w / 2;
    const nx = this.x + dx / d * step, ny = this.y + dy / d * step;
    if (this.game.map.isWalkableRect(nx - h, this.y - h, nx + h, this.y + h)) this.x = nx;
    if (this.game.map.isWalkableRect(this.x - h, ny - h, this.x + h, ny + h)) this.y = ny;
  }
  _move(tx, ty, dt, spd) {
    if (this.repath <= 0) {
      this.repath = 0.35;
      this.path = this.astar.findPath(this.x, this.y, tx, ty) || [];
      this.pathIdx = 0;
    } else this.repath -= dt;
    while (this.pathIdx < this.path.length) {
      const [px, py] = this.path[this.pathIdx];
      const cx = px * TILE + TILE / 2, cy = py * TILE + TILE / 2;
      const d = Math.hypot(cx - this.x, cy - this.y);
      if (d < 6) { this.pathIdx++; continue; }
      const h = this.w / 2, step = Math.min(spd * dt, d);
      const nx = this.x + (cx - this.x) / d * step, ny = this.y + (cy - this.y) / d * step;
      if (this.game.map.isWalkableRect(nx - h, this.y - h, nx + h, this.y + h)) this.x = nx;
      if (this.game.map.isWalkableRect(this.x - h, ny - h, this.x + h, ny + h)) this.y = ny;
      this.rot = Math.atan2(cy - this.y, cx - this.x);
      break;
    }
  }
  _patrol(dt, speed) {
    if (!this.waypoints.length) return;
    if (this.look > 0) { this.look -= dt; this.rot += dt * 0.9; return; }
    if (this.wp > 0) { this.wp -= dt; return; }
    const wp = this.waypoints[this.wi];
    const d = Math.hypot(wp[0] - this.x, wp[1] - this.y);
    if (d < 12) {
      this.wi = (this.wi + 1) % this.waypoints.length;
      this.wp = 0.8 + Math.random() * 1.6;
      this.look = 0.4 + Math.random() * 1.2;
      this.rot = Math.atan2(this.waypoints[this.wi][1] - this.y, this.waypoints[this.wi][0] - this.x);
    } else { this.rot = Math.atan2(wp[1] - this.y, wp[0] - this.x); this._linear(wp[0], wp[1], dt, speed); }
  }
  reset() {
    this.x = this.startX; this.y = this.startY; this.state = 'PATROL';
    this.wi = 0; this.wp = 0; this.look = 0; this.stunT = 0;
    this.sightT = this.suspT = this.alarmT = this.lostT = 0;
    this.alert = false; this.path = [];
  }
}

class Guard extends Enemy {
  constructor(game) { super(game, 'guard'); }
  update(dt, px, py, mode, hidden) {
    if (!this.active) return;
    this.anim += dt;
    if (this.stunT > 0) { this.stunT -= dt; if (this.stunT <= 0) this.state = 'PATROL'; return; }
    const vr = 320 * (hidden ? 0.3 : 1);
    const see = !hidden && canSee(this.game.map, this.x, this.y, this.rot, Math.PI / 3.6, vr, px, py);
    const hear = hears(this.x, this.y, px, py, 210, mode);
    const S = this.state;
    if (S === 'PATROL') {
      if (see || hear) { this.lastX = px; this.lastY = py; this.state = 'SUSPICIOUS'; }
    } else if (S === 'SUSPICIOUS') {
      if (see) { this.sightT += dt; this.lastX = px; this.lastY = py; } else this.sightT = 0;
      if (this.sightT >= 0.4) { this.alarmT = 0; this.state = 'ALERT'; return; }
      if (hear) { this.lastX = px; this.lastY = py; this.suspT = 0; }
      else { this.suspT += dt; if (this.suspT >= 4) { this.state = 'PATROL'; return; } }
    } else if (S === 'INVESTIGATE') {
      if (see) { this.sightT += dt; this.lastX = px; this.lastY = py; if (this.sightT >= 0.4) { this.alarmT = 0; this.state = 'ALERT'; return; } }
      else this.sightT = 0;
      if (Math.hypot(this.lastX - this.x, this.lastY - this.y) < 30) this.state = 'PATROL';
    } else if (S === 'ALERT') {
      this.lastX = px; this.lastY = py;
      this.alarmT += dt;
      if (this.alarmT >= 2) { this.alert = true; this.alarmT = 0; }
    }
    if (S === 'PATROL') this._patrol(dt, 120);
    else if (S === 'SUSPICIOUS') this.rot = Math.atan2(this.lastY - this.y, this.lastX - this.x);
    else if (S === 'INVESTIGATE') this._move(this.lastX, this.lastY, dt, 120);
    else if (S === 'ALERT') { this._move(this.lastX, this.lastY, dt, 210); this.rot = Math.atan2(py - this.y, px - this.x); }
  }
  forceAlert(ax, ay) {
    if (!this.active || this.stunT > 0) return;
    this.lastX = ax; this.lastY = ay; this.alarmT = 0; this.alert = true; this.state = 'ALERT';
  }
  stun(sec) { if (this.active) { this.stunT = Math.max(this.stunT, sec); this.alarmT = 0; this.alert = false; this.state = 'PATROL'; } }
  distract(tx, ty) { if (this.active && this.stunT <= 0 && this.state !== 'ALERT') { this.lastX = tx; this.lastY = ty; this.state = 'INVESTIGATE'; } }
  get isAlert() { return this.state === 'ALERT'; }
  get isChasing() { return this.state === 'ALERT' || this.state === 'SUSPICIOUS' || this.state === 'INVESTIGATE'; }
}

class Drone extends Enemy {
  constructor(game) { super(game, 'drone'); this.dome = '#00b4ff'; }
  update(dt, px, py, mode, hidden) {
    if (!this.active) return;
    this.anim += dt * 40;
    const vr = 400 * (hidden ? 0.3 : 1);
    const see = !hidden && canSee(this.game.map, this.x, this.y, this.rot, Math.PI / 3, vr, px, py);
    const hear = hears(this.x, this.y, px, py, 250, mode);
    const S = this.state;
    if (S === 'PATROL') {
      if (see || hear) { this.lastX = px; this.lastY = py; this.state = 'SUSPICIOUS'; }
    } else if (S === 'SUSPICIOUS') {
      if (see) { this.sightT += dt; this.lastX = px; this.lastY = py; }
      else this.sightT = 0;
      if (this.sightT >= 0.3) { this.state = 'ALERT'; return; }
      if (!hear) { this.suspT += dt; if (this.suspT >= 3) { this.state = 'PATROL'; return; } }
      else this.suspT = 0;
    } else if (S === 'ALERT') {
      this.lastX = px; this.lastY = py;
      if (see) this.lostT = 0;
      else { this.lostT += dt; if (this.lostT >= 5) this.state = 'RETURN'; }
    } else if (S === 'RETURN') {
      this._move(this.startX, this.startY, dt, 150);
      if (Math.hypot(this.startX - this.x, this.startY - this.y) < 24) this.state = 'PATROL';
    }
    if (S === 'PATROL') this._patrol(dt, 150);
    else if (S === 'SUSPICIOUS') this._move(this.lastX, this.lastY, dt, 150);
    else if (S === 'ALERT') { this._move(this.lastX, this.lastY, dt, 195); this.rot = Math.atan2(py - this.y, px - this.x); }
    this.dome = this.state === 'ALERT' ? '#ff3b3b' : (this.state === 'SUSPICIOUS' ? '#ffd54f' : '#00b4ff');
  }
  forceAlert(ax, ay) {
    if (!this.active) return;
    this.lastX = ax; this.lastY = ay; this.alert = true; this.state = 'ALERT';
  }
  get isAlert() { return this.state === 'ALERT'; }
  get isChasing() { return this.state === 'ALERT' || this.state === 'SUSPICIOUS'; }
}

class Turret {
  constructor(game) {
    this.game = game;
    this.x = 0; this.y = 0; this.active = true;
    this.angle = 0; this.lock = 0; this.scan = 1; this.disabled = false;
    this.state = 'IDLE'; this.firePending = false; this.anim = 0;
  }
  init(x, y) { this.x = x; this.y = y; this.active = true; this.angle = 0; this.lock = 0; this.disabled = false; }
  update(dt, px, py, mode, hidden) {
    if (!this.active) return;
    this.anim += dt;
    if (this.disabled) { this.state = 'IDLE'; return; }
    const vr = 340 * (hidden ? 0.3 : 1);
    const see = !hidden && canSee(this.game.map, this.x, this.y, this.angle, Math.PI * 0.5, vr, px, py);
    const hear = hears(this.x, this.y, px, py, 140, mode);
    if (see) {
      this.lock += dt; this.state = 'SCANNING';
      const target = Math.atan2(py - this.y, px - this.x);
      const diff = normAngle(target - this.angle);
      if (Math.abs(diff) <= 1.1 * dt) this.angle = target; else this.angle += Math.sign(diff) * 1.1 * dt;
      if (this.lock >= 1.0 && Math.abs(normAngle(target - this.angle)) < 0.1) {
        this.firePending = true; this.lock = 0; this.state = 'FIRING';
      }
    } else {
      this.lock = 0;
      this.state = hear ? 'SCANNING' : 'IDLE';
      if (this.state === 'IDLE') {
        this.angle += dt * 0.6 * this.scan;
        if (this.angle > Math.PI * 0.5 || this.angle < -Math.PI * 0.5) this.scan *= -1;
      }
    }
  }
  disable() { this.disabled = true; }
}

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------
class Projectile {
  constructor(game) { this.game = game; this.active = false; this.x = 0; this.y = 0; this.vx = 0; this.vy = 0; }
  init(x, y, angle) {
    this.active = true; this.x = x; this.y = y;
    this.vx = Math.cos(angle) * 420; this.vy = Math.sin(angle) * 420;
    this.x0 = x; this.y0 = y;
  }
  update(dt) {
    if (!this.active) return;
    const nx = this.x + this.vx * dt, ny = this.y + this.vy * dt;
    if (!this.game.map.isWalkablePixel(nx, ny) || Math.hypot(nx - this.x0, ny - this.y0) > 700) { this.active = false; return; }
    this.x = nx; this.y = ny;
    const p = this.game.player;
    if (Math.hypot(p.x - this.x, p.y - this.y) < 16 && p.invuln <= 0) this.game.gameOver();
  }
}
class Collectible {
  constructor(game, id, color) { this.game = game; this.id = id; this.color = color; this.x = 0; this.y = 0; this.anim = 0; this.active = true; }
  init(x, y) { this.x = x; this.y = y; this.active = true; }
  update(dt) {
    this.anim += dt;
    const p = this.game.player;
    if (this.active && Math.hypot(p.x - this.x, p.y - this.y) < 30) { this.active = false; this.game.onCollect(this.id); }
  }
}
class DoorTerminal {
  constructor(game, id, key) { this.game = game; this.id = id; this.key = key; this.x = 0; this.y = 0; this.locked = true; }
  init(x, y) { this.x = x; this.y = y; }
  interact() {
    if (!this.locked) return true;
    if (this.game.inventory.has(this.key)) { this.locked = false; return true; }
    return false;
  }
}
class CrateProp {
  constructor(game) { this.game = game; this.x = 0; this.y = 0; }
  init(x, y) { this.x = x; this.y = y; }
}
class ConsoleProp {
  constructor(game, choice) { this.game = game; this.x = 0; this.y = 0; this.choice = choice; }
  init(x, y) { this.x = x; this.y = y; }
}
class NPC {
  constructor(game, name, dialogueId, color) {
    this.game = game; this.name = name; this.dialogueId = dialogueId; this.color = color;
    this.x = 0; this.y = 0;
  }
  init(x, y) { this.x = x; this.y = y; }
}
class AcousticPuzzle {
  constructor(game) {
    this.game = game; this.x = 0; this.y = 0;
    this.active = true; this.solved = false; this.count = 0;
    this.seq = [0, 1, 2, 1]; this.turretIdx = -1; this.doorId = '';
    this.anim = 0;
  }
  init(x, y, seq) { this.x = x; this.y = y; this.seq = seq; this.count = 0; this.solved = false; this.active = true; }
  bind(turretIdx, doorId) { this.turretIdx = turretIdx; this.doorId = doorId; }
  pressTone(tone) {
    if (this.solved) return;
    if (tone === this.seq[this.count]) {
      this.count++;
      if (this.count === this.seq.length) { this.solved = true; if (this.game) this.game.onPuzzleSolved(this); }
    } else this.count = 0;
  }
  update(dt) { this.anim += dt; }
  get progress() { return this.count; }
}

// ---------------------------------------------------------------------------
// Managers
// ---------------------------------------------------------------------------
class Inventory {
  constructor() { this.items = new Set(); }
  add(id) { this.items.add(id); }
  has(id) { return this.items.has(id); }
  remove(id) { this.items.delete(id); }
  get list() { return [...this.items]; }
  clear() { this.items.clear(); }
}

const QUEST_DEFS = {
  q_awakening: { title: 'The Lost Echo', objectives: [['COLLECT_ITEM', 'walkman', 1], ['TALK_TO_NPC', 'meera', 1]] },
  q_first_escape: { title: 'Into the Machine', objectives: [['REACH_LOCATION', 'lab_entrance', 1]] },
  q_lab_clearance: { title: 'Lab Clearance', objectives: [['COLLECT_ITEM', 'keycard_blue', 1], ['SOLVE_PUZZLE', 'lab_door', 1]] },
  q_silence_drones: { title: 'Silence the Watch', objectives: [['SOLVE_PUZZLE', 'turret_1', 1], ['SOLVE_PUZZLE', 'turret_2', 1]] },
  q_data_choice: { title: 'The Last Signal', objectives: [['REACH_LOCATION', 'data_center', 1]] },
};
class QuestManager {
  constructor() {
    this.quests = new Map(); // id -> {def, progress:[n,...]}
    this.completed = new Set();
  }
  start(id) {
    const def = QUEST_DEFS[id];
    if (def && !this.quests.has(id)) this.quests.set(id, { def, prog: def.objectives.map(() => 0) });
  }
  advance(type, target, amt) {
    for (const [id, q] of this.quests) {
      if (this.completed.has(id)) continue;
      let all = true;
      q.def.objectives.forEach(([t, tar, count], i) => {
        if (t === type && tar === target) q.prog[i] = Math.min(count, q.prog[i] + (amt || 1));
        if (q.prog[i] < count) all = false;
      });
      if (all) this.completed.add(id);
    }
  }
  isCompleted(id) { return this.completed.has(id); }
  isActive(id) { return this.quests.has(id) && !this.completed.has(id); }
  active() { return [...this.quests.values()].filter(q => !this.completed.has(q.def.title === null ? q.id : this._idOf(q))); }
  _idOf(q) { for (const [id, qq] of this.quests) if (qq === q) return id; return ''; }
}
// simpler active(): keep map of id -> quest
QuestManager.prototype.active = function() {
  const out = [];
  for (const [id, q] of this.quests) if (!this.completed.has(id)) out.push(q);
  return out;
};

const DIALOGUE_NODES = {
  meera_intro: { speaker: 'Meera', text: 'Raka! The city is quieter than it has ever been. I heard a pulse down by the old slum ruins. Find it. It might be the only way to fight the drones.', choices: [{ text: "I'll find the Walkman.", next: 'meera_walkman', startQuest: 'q_awakening' }, { text: 'The drones... how many are there?', next: 'meera_drones' }] },
  meera_walkman: { speaker: 'Meera', text: 'Good. And if you find a keycard, keep it. Aethelgard doors still open for the right colours.', choices: [] },
  meera_drones: { speaker: 'Meera', text: 'Too many. But they hunt by sound and sight. Move soft, and they will never know you are here.', choices: [{ text: 'Understood.', next: null }] },
  juno_talk: { speaker: 'Old Juno', text: 'The lab keeps its secrets under the city. The blue keycard opens the west gate. Watch the turret. It hums before it fires.', choices: [{ text: 'Thanks, Juno.', next: null, startQuest: 'q_first_escape' }] },
  warden_lab: { speaker: 'Warden Kess', text: "You're not cleared for this floor. If you had a blue keycard, the gate would open. The security office keeps spares.", choices: [{ text: "I'll get that keycard.", next: null, startQuest: 'q_lab_clearance' }, { text: 'The turrets?', next: 'warden_turrets' }] },
  warden_turrets: { speaker: 'Warden Kess', text: 'Old units. They answer to acoustic codes. Play the right sequence and they power down for good.', choices: [{ text: 'Good to know.', next: null, startQuest: 'q_silence_drones' }] },
  data_choice: { speaker: 'Meera', text: 'This is it, Raka. Two consoles. The left sends your parents to safety but keeps the city watched. The right shuts Aethelgard down forever. Choose for all of us.', choices: [{ text: 'Save my parents. Keep the city watched.', next: null }, { text: 'Paralyze the city. Free everyone.', next: null }] },
};
class DialogueManager {
  constructor(game) { this.game = game; this.cur = null; this.active = false; }
  start(id) {
    const n = DIALOGUE_NODES[id];
    if (!n) return;
    this.cur = n; this.active = true;
    this.page = 0;
    if (n.choices && n.choices.length) this.awaitChoice = true; else this.awaitChoice = false;
  }
  get speaker() { return this.cur ? this.cur.speaker : ''; }
  get text() { return this.cur ? this.cur.text : ''; }
  get choices() { return this.cur && this.cur.choices && this.cur.choices.length ? this.cur.choices : null; }
  choose(i) {
    const c = this.cur.choices[i];
    if (!c) return;
    if (c.startQuest) this.game.quests.start(c.startQuest);
    if (c.next) this.start(c.next);
    else this.end();
  }
  tap() {
    // advance: if choices available, wait for explicit pick
    if (this.cur && this.cur.choices && this.cur.choices.length) return;
    this.end();
  }
  end() { this.active = false; this.cur = null; }
}

class SaveSystem {
  constructor() { this.keyName = 'lostecho_save'; }
  load() { try { return JSON.parse(localStorage.getItem(this.keyName)); } catch (e) { return null; } }
  save(data) { try { localStorage.setItem(this.keyName, JSON.stringify(data)); } catch (e) {} }
  clear() { localStorage.removeItem(this.keyName); }
  get has() { return !!this.load(); }
}

// ---------------------------------------------------------------------------
// Game
// ---------------------------------------------------------------------------
class Game {
  constructor() {
    this.canvas = document.getElementById('game');
    this.ctx = this.canvas.getContext('2d');
    this.dpr = window.devicePixelRatio || 1;
    this.w = window.innerWidth; this.h = window.innerHeight;
    this.canvas.width = this.w * this.dpr; this.canvas.height = this.h * this.dpr;

    this.map = new TileMap();
    this.audio = new AudioSys();
    this.inventory = new Inventory();
    this.quests = new QuestManager();
    this.dialogue = new DialogueManager(this);
    this.save = new SaveSystem();
    this.player = new Player(this);

    this.state = 'MENU';
    this.area = AREA_SLUM;
    this.guards = []; this.drones = []; this.turrets = [];
    this.props = []; this.doors = []; this.puzzles = []; this.npcs = [];
    this.collectibles = []; this.projectiles = [];
    this.doorTiles = {};
    this.unlocked = new Set(); this.disabledTurrets = new Set(); this.lore = new Set();
    this.alerts = 0; this.playTime = 0; this.gameOverT = 0; this.detection = 0;
    this.walkman = false; this.activePuzzle = null; this.hudInv = false;
    this.sonarWave = null; this.transition = null; this.endingChoice = 0;
    this.menuBtns = []; this.choiceRects = [];
    this.showHelp = false;
    this._lastT = performance.now();
  }

  resize() {
    this.w = window.innerWidth; this.h = window.innerHeight;
    this.canvas.width = this.w * this.dpr; this.canvas.height = this.h * this.dpr;
    this.map.setViewSize(this.w, this.h);
  }

  // --- flow ---------------------------------------------------------------
  startNew() {
    this.inventory.clear();
    this.quests = new QuestManager();
    this.dialogue = new DialogueManager(this);
    this.unlocked = new Set(); this.disabledTurrets = new Set(); this.lore = new Set();
    this.alerts = 0; this.playTime = 0; this.endingChoice = 0;
    this.loadArea(AREA_SLUM);
    this.state = 'GAMEPLAY';
    this.dialogue.start('meera_intro');
    this.autoSave();
    this.audio.resume();
  }

  continueGame() {
    const d = this.save.load();
    if (!d) { this.startNew(); return; }
    this.inventory.clear(); for (const it of d.inventory || []) this.inventory.add(it);
    this.quests = new QuestManager();
    for (const id of d.activeQuests || []) this.quests.start(id);
    for (const id of d.completedQuests || []) this.quests.completed.add(id);
    this.unlocked = new Set(d.unlockedDoors || []);
    this.disabledTurrets = new Set(d.disabledTurrets || []);
    this.alerts = d.alerts || 0; this.playTime = d.playTime || 0;
    this.endingChoice = d.endingChoice || 0;
    this.loadArea(d.area || AREA_SLUM);
    this.player.x = d.px || 200; this.player.y = d.py || 200;
    this.player.checkX = d.cx || this.player.x; this.player.checkY = d.cy || this.player.y;
    this.state = 'GAMEPLAY';
    this.audio.resume();
  }

  autoSave() {
    const d = {
      area: this.area,
      px: Math.round(this.player.x), py: Math.round(this.player.y),
      cx: Math.round(this.player.checkX), cy: Math.round(this.player.checkY),
      inventory: this.inventory.list,
      activeQuests: [...this.quests.quests.keys()],
      completedQuests: [...this.quests.completed],
      unlockedDoors: [...this.unlocked],
      disabledTurrets: [...this.disabledTurrets],
      alerts: this.alerts, playTime: Math.floor(this.playTime),
      endingChoice: this.endingChoice,
    };
    this.save.save(d);
  }

  resetProgress() {
    this.save.clear();
    this.inventory.clear();
    this.quests = new QuestManager();
    this.unlocked = new Set(); this.disabledTurrets = new Set(); this.lore = new Set();
    this.alerts = 0;
  }

  // --- areas --------------------------------------------------------------
  loadArea(area) {
    this.area = area;
    const cfg = AREA_CFG[area];
    this.map.generateMap(cfg.w, cfg.h, cfg.seed, cfg.surface);
    this.map.setViewSize(this.w, this.h);
    this.guards = []; this.drones = []; this.turrets = [];
    this.props = []; this.doors = []; this.puzzles = []; this.npcs = [];
    this.collectibles = []; this.projectiles = []; this.doorTiles = {};
    this.walkman = false; this.activePuzzle = null;
    AREA_BUILD[area](this);
    for (const id of this.unlocked) this.unlockDoorSilent(id);
    for (const i of this.disabledTurrets) if (this.turrets[i]) this.turrets[i].disabled = true;
    this.player.astar = new AStar(this.map);
  }

  floorNear(tx, ty) {
    const w = this.map.mapW, h = this.map.mapH;
    for (let r = 0; r <= 10; r++)
      for (let dy = -r; dy <= r; dy++)
        for (let dx = -r; dx <= r; dx++) {
          const x = tx + dx, y = ty + dy;
          if (x > 0 && y > 0 && x < w - 1 && y < h - 1 && this.map.isFloorTile(x, y))
            return { x: x * TILE + TILE / 2, y: y * TILE + TILE / 2 };
        }
    return { x: tx * TILE + TILE / 2, y: ty * TILE + TILE / 2 };
  }

  addDoor(id, tx, ty, key) {
    this.map.setBlocked(tx, ty, true); this.map.setBlocked(tx + 1, ty, true);
    const d = new DoorTerminal(this, id, key);
    d.init(tx * TILE + TILE / 2, ty * TILE + TILE / 2);
    this.doors.push(d);
    this.doorTiles[id] = [tx, ty, tx + 1, ty];
  }
  addCrate(tx, ty) {
    this.map.setBlocked(tx, ty, true);
    const c = new CrateProp(this); c.init(tx * TILE + TILE / 2, ty * TILE + TILE / 2);
    this.props.push(c);
  }
  addCollectible(tx, ty, id, color) {
    const p = this.floorNear(tx, ty);
    const c = new Collectible(this, id, color); c.init(p.x, p.y);
    this.collectibles.push(c);
  }
  addNpc(tx, ty, name, dialogueId, color) {
    const p = this.floorNear(tx, ty);
    const n = new NPC(this, name, dialogueId, color); n.init(p.x, p.y);
    this.npcs.push(n);
  }
  addGuard(x, y, wps) {
    const g = new Guard(this); g.init(x, y); g.setWaypoints(wps);
    this.guards.push(g);
  }
  addDrone(x, y, wps) {
    const d = new Drone(this); d.init(x, y); d.setWaypoints(wps);
    this.drones.push(d);
  }
  addTurret(tx, ty) {
    const p = this.floorNear(tx, ty);
    const t = new Turret(this); t.init(p.x, p.y);
    this.turrets.push(t);
  }
  addPuzzle(tx, ty, seq, turretIdx, doorId) {
    const p = this.floorNear(tx, ty);
    const z = new AcousticPuzzle(this); z.init(p.x, p.y, seq); z.bind(turretIdx, doorId);
    this.puzzles.push(z);
  }
  addConsole(tx, ty, choice) {
    const p = this.floorNear(tx, ty);
    const c = new ConsoleProp(this, choice); c.init(p.x, p.y);
    this.props.push(c);
  }

  // --- events -------------------------------------------------------------
  onCollect(id) {
    this.audio.sfx('collect');
    this.inventory.add(id);
    this.quests.advance('COLLECT_ITEM', id, 1);
    this.autoSave();
  }

  onPuzzleSolved(z) {
    this.audio.sfx('collect');
    if (z.turretIdx >= 0 && this.turrets[z.turretIdx]) {
      this.turrets[z.turretIdx].disable();
      this.disabledTurrets.add(z.turretIdx);
      this.quests.advance('SOLVE_PUZZLE', 'turret_' + (z.turretIdx + 1), 1);
    }
    if (z.doorId && !this.unlocked.has(z.doorId)) this.unlockDoor(z.doorId);
    if (this.activePuzzle === z) { this.activePuzzle = null; this.walkman = false; }
    this.autoSave();
  }

  unlockDoor(id) {
    for (const d of this.doors) if (d.id === id && d.locked) d.locked = false;
    this.unlockDoorSilent(id);
    this.quests.advance('SOLVE_PUZZLE', id, 1);
    this.autoSave();
  }
  unlockDoorSilent(id) {
    const tiles = this.doorTiles[id];
    if (tiles) { this.map.openDoorTile(tiles[0], tiles[1]); if (tiles[2] >= 0) this.map.openDoorTile(tiles[2], tiles[3]); }
    this.unlocked.add(id);
  }

  onInteract() {
    const R = 70;
    const p = this.player;
    let bestN = null, bd = R * R;
    for (const n of this.npcs) {
      const d = (n.x - p.x) ** 2 + (n.y - p.y) ** 2;
      if (d < bd) { bd = d; bestN = n; }
    }
    if (bestN) {
      this.dialogue.start(bestN.dialogueId);
      this.audio.sfx('interact');
      if (bestN.dialogueId === 'meera_intro') this.quests.advance('TALK_TO_NPC', 'meera', 1);
      return;
    }
    for (const d of this.doors) {
      if (Math.hypot(d.x - p.x, d.y - p.y) < R) {
        const ok = d.interact();
        this.audio.sfx(ok ? 'open' : 'locked');
        if (ok) this.unlockDoor(d.id);
        return;
      }
    }
    for (const z of this.puzzles) {
      if (z.solved) continue;
      if (Math.hypot(z.x - p.x, z.y - p.y) < 90) {
        this.activePuzzle = z; this.walkman = true;
        this.audio.sfx('interact');
        return;
      }
    }
    for (const pr of this.props) {
      if (Math.hypot(pr.x - p.x, pr.y - p.y) < R) {
        if (pr instanceof ConsoleProp) this.useConsole(pr.choice);
        else this.pushCrate(pr);
        return;
      }
    }
  }

  useConsole(choice) {
    if (this.endingChoice) return;
    this.endingChoice = choice;
    this.audio.sfx('alarm');
    this.transition = { t: 0, dur: 1.4 };
    this.autoSave();
  }

  pushCrate(c) {
    const tx = Math.floor(c.x / TILE), ty = Math.floor(c.y / TILE);
    const dx = c.x > this.player.x ? 1 : (c.x < this.player.x ? -1 : 0);
    const dy = c.y > this.player.y ? 1 : (c.y < this.player.y ? -1 : 0);
    const nx = tx + dx, ny = ty + dy;
    if (this.map.isFloorTile(nx, ny)) {
      this.map.setBlocked(tx, ty, false); this.map.setBlocked(nx, ny, true);
      c.x = nx * TILE + TILE / 2; c.y = ny * TILE + TILE / 2;
      this.audio.sfx('open');
    } else this.audio.sfx('locked');
  }

  gameOver() {
    this.state = 'GAMEOVER';
    this.audio.sfx('alarm');
    this.transition = { t: 0, dur: 0.9 };
  }

  fireTurret(t) {
    this.audio.sfx('turret');
    const pr = new Projectile(this);
    pr.init(t.x, t.y, t.angle);
    this.projectiles.push(pr);
  }

  // --- update -------------------------------------------------------------
  tick(dt) {
    this.audio.setDroneHum(0);
    if (this.state === 'MENU' || this.state === 'CREDITS' || this.state === 'HELP' || this.state === 'ENDING') return;
    if (this.state === 'PAUSE') return;
    if (this.state === 'GAMEOVER') {
      if (this.transition) {
        this.transition.t += dt;
        if (this.transition.t >= this.transition.dur) {
          this.player.respawn();
          this.state = 'GAMEPLAY';
          this.transition = null;
          this.audio.sfx('sonar');
        }
      }
      return;
    }
    if (this.endingChoice) {
      if (this.transition) {
        this.transition.t += dt;
        if (this.transition.t >= this.transition.dur) {
          this.state = 'ENDING';
          this.transition = null;
        }
      }
      return;
    }

    this.playTime += dt;
    this.map.updateCamera(this.player.x, this.player.y);

    if (this.dialogue.active) return;

    this.player.update(dt);
    if (this.player.pulsePending) { this.pulse(); }
    if (this.player.stepPending) { this.audio.footstep(this.player.stepSneak); }
    if (this.player.interactPending) { this.onInteract(); }
    this.player.pulsePending = this.player.interactPending = this.player.stepPending = false;

    for (const d of this.drones) {
      d.update(dt, this.player.x, this.player.y, this.player.mode, this.player.hidden);
      if (d.alert) { d.alert = false; this.alerts++; this.audio.sfx('alert'); for (const o of this.drones) if (o !== d && Math.hypot(o.x - d.x, o.y - d.y) < 300) o.forceAlert(d.x, d.y); }
    }
    for (const g of this.guards) {
      g.update(dt, this.player.x, this.player.y, this.player.mode, this.player.hidden);
      if (g.alert) {
        g.alert = false; this.alerts++;
        this.audio.sfx('alarm');
        for (const d of this.drones) d.forceAlert(g.x, g.y);
        for (const o of this.guards) if (o !== g && !o.stunT) o.forceAlert(g.x, g.y);
      }
    }
    for (const t of this.turrets) {
      t.update(dt, this.player.x, this.player.y, this.player.mode, this.player.hidden);
      if (t.firePending) { t.firePending = false; this.fireTurret(t); }
    }
    for (const pr of this.projectiles) pr.update(dt);
    for (const z of this.puzzles) z.update(dt);
    for (const c of this.collectibles) c.update(dt);

    let hum = 0;
    for (const d of this.drones) hum = Math.max(hum, Math.max(0, Math.min(1, 1 - Math.hypot(d.x - this.player.x, d.y - this.player.y) / 700)));
    this.audio.setDroneHum(hum);

    let level = 0, anyAlert = false, near = Infinity;
    for (const d of this.drones) {
      if (d.isAlert) { anyAlert = true; level = 2; near = Math.min(near, Math.hypot(d.x - this.player.x, d.y - this.player.y)); }
      else if (d.isChasing) level = Math.max(level, 1);
    }
    for (const g of this.guards) {
      if (g.isAlert) { anyAlert = true; level = 2; near = Math.min(near, Math.hypot(g.x - this.player.x, g.y - this.player.y)); }
      else if (g.isChasing) level = Math.max(level, 1);
    }
    this.detection = level;
    if (anyAlert && near < 100) {
      this.gameOverT += dt;
      if (this.gameOverT >= 2) this.gameOver();
    } else this.gameOverT = 0;

    if (this.sonarWave) { this.sonarWave.r += dt * 520; if (this.sonarWave.r > 520) this.sonarWave = null; }
  }

  pulse() {
    this.audio.sfx('sonar');
    this.sonarWave = { x: this.player.x, y: this.player.y, r: 0 };
    for (const g of this.guards) if (Math.hypot(g.x - this.player.x, g.y - this.player.y) < 90) { g.stun(6); this.audio.sfx('stun'); }
  }

  // --- input --------------------------------------------------------------
  handleTap(sx, sy) {
    if (this.state === 'MENU') {
      for (const b of this.menuBtns) {
        if (Math.abs(b.y - sy) < 16 && Math.abs(b.x - sx) < 140) {
          this.audio.sfx('menu');
          if (b.label === 'NEW GAME') this.startNew();
          else if (b.label === 'CONTINUE') this.continueGame();
          else if (b.label === 'HOW TO PLAY') this.state = 'HELP';
          else if (b.label === 'RESET SAVE') { this.resetProgress(); }
          else if (b.label === 'CREDITS') this.state = 'CREDITS';
          return;
        }
      }
      return;
    }
    if (this.state === 'CREDITS' || this.state === 'HELP' || this.state === 'ENDING') { this.state = 'MENU'; return; }
    if (this.state === 'PAUSE') { this.state = 'GAMEPLAY'; return; }
    if (this.state === 'GAMEOVER') return;

    // UI buttons (bottom corners)
    const b = this.buttonAt(sx, sy);
    if (b) {
      if (b === 'SNEAK') { this.player.sneaking = !this.player.sneaking; this.audio.sfx('menu'); }
      else if (b === 'PULSE') {
        if (this.player.cooldown <= 0 && this.player.stamina >= 20) {
          this.player.stamina -= 20; this.player.cooldown = 1; this.pulse();
        }
      } else if (b === 'USE') { this.player.interactPending = true; }
      else if (b === 'WALK') { this.walkman = !this.walkman; this.audio.sfx('menu'); }
      else if (b === 'INV') { this.hudInv = !this.hudInv; this.audio.sfx('menu'); }
      else if (b === 'PAUSE') { this.state = 'PAUSE'; }
      return;
    }

    // dialogue
    if (this.dialogue.active) {
      if (this.dialogue.choices) {
        for (let i = 0; i < this.dialogue.choices.length; i++) {
          const r = this.choiceRects[i];
          if (r && sx >= r.x && sx <= r.x + r.w && sy >= r.y && sy <= r.y + r.h) {
            this.dialogue.choose(i);
            return;
          }
        }
        return; // must pick a choice
      }
      this.dialogue.tap();
      return;
    }

    // walkman tones
    if (this.walkman || this.activePuzzle) {
      const n = 3, r = 26, gap = 20, total = n * (r * 2 + gap);
      const cy = this.h - 90, x0 = this.w / 2 - total / 2 + r;
      for (let i = 0; i < n; i++) {
        const x = x0 + i * (r * 2 + gap);
        if (Math.hypot(sx - x, sy - cy) < r + 12) {
          this.audio.playTone(i);
          if (this.activePuzzle) this.activePuzzle.pressTone(i);
          return;
        }
      }
      this.walkman = false;
      return;
    }

    // world move
    this.player.setMoveTarget(sx + this.map.camX, sy + this.map.camY);
    if (!this.player.sneaking) this.player.sneaking = false;
  }

  buttonAt(sx, sy) {
    const B = [
      { id: 'SNEAK', x: this.w - 76, y: this.h - 76 },
      { id: 'PULSE', x: this.w - 40, y: this.h - 120 },
      { id: 'USE', x: this.w - 76, y: this.h - 40 },
      { id: 'WALK', x: 14, y: this.h - 76 },
      { id: 'INV', x: 14, y: this.h - 40 },
      { id: 'PAUSE', x: 14, y: 14 },
    ];
    for (const b of B) if (Math.hypot(sx - b.x, sy - b.y) < 20) return b.id;
    return null;
  }

  // --- render -------------------------------------------------------------
  render() {
    const ctx = this.ctx, w = this.w, h = this.h;
    ctx.clearRect(0, 0, w, h);
    ctx.save();
    ctx.scale(this.dpr, this.dpr);

    if (this.state === 'MENU') { this.renderMenu(); ctx.restore(); return; }
    if (this.state === 'CREDITS') { this.renderCredits(); ctx.restore(); return; }
    if (this.state === 'HELP') { this.renderHelp(); ctx.restore(); return; }
    if (this.state === 'ENDING') { this.renderEnding(); ctx.restore(); return; }

    ctx.fillStyle = '#0c0e1a'; ctx.fillRect(0, 0, w, h);

    // world
    ctx.save();
    ctx.translate(-this.map.camX, -this.map.camY);
    this.renderTiles(ctx);
    this.renderProps(ctx);
    this.renderDoors(ctx);
    this.renderPuzzles(ctx);
    this.renderCollectibles(ctx);
    this.renderNpcs(ctx);
    this.renderEnemies(ctx);
    this.renderProjectiles(ctx);
    this.renderPlayer(ctx);
    if (this.sonarWave) {
      ctx.globalAlpha = Math.max(0, 1 - this.sonarWave.r / 520);
      ctx.strokeStyle = '#50e8ff'; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.arc(this.sonarWave.x, this.sonarWave.y, this.sonarWave.r, 0, TAU); ctx.stroke();
      ctx.globalAlpha = 1; ctx.lineWidth = 1;
    }
    ctx.restore();

    // lighting vignette
    const px = this.player.x - this.map.camX, py = this.player.y - this.map.camY;
    const lg = ctx.createRadialGradient(px, py, 10, px, py, 340);
    lg.addColorStop(0, 'rgba(0,0,0,0)'); lg.addColorStop(1, 'rgba(4,5,12,0.7)');
    ctx.fillStyle = lg; ctx.fillRect(0, 0, w, h);

    if (this.walkman || this.activePuzzle) this.renderTones(ctx);
    this.renderHUD(ctx);
    if (this.dialogue.active) this.renderDialogue(ctx);
    if (this.state === 'GAMEOVER') {
      ctx.fillStyle = 'rgba(180,20,20,0.3)'; ctx.fillRect(0, 0, w, h);
      ctx.fillStyle = '#ff5a5a'; ctx.font = 'bold 26px monospace'; ctx.textAlign = 'center';
      ctx.fillText('CAUGHT', w / 2, h / 2);
    }
    if (this.transition) {
      const k = this.transition.dur ? Math.min(1, this.transition.t / this.transition.dur) : 0;
      ctx.fillStyle = 'rgba(0,0,0,' + (k < 0.5 ? k * 2 : (1 - k) * 2).toFixed(3) + ')';
      ctx.fillRect(0, 0, w, h);
    }
    ctx.restore();
  }

  renderTiles(ctx) {
    const camX = this.map.camX, camY = this.map.camY;
    const x0 = Math.max(0, Math.floor(camX / TILE)), x1 = Math.min(this.map.mapW, Math.ceil((camX + this.w) / TILE));
    const y0 = Math.max(0, Math.floor(camY / TILE)), y1 = Math.min(this.map.mapH, Math.ceil((camY + this.h) / TILE));
    const map = this.map;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const i = y * map.mapW + x;
      if (map.tiles[i] === TILE_WALL) continue;
      ctx.fillStyle = (x + y) % 2 ? '#28272e' : '#2e2c34';
      ctx.fillRect(x * TILE, y * TILE, TILE, TILE);
      if (map.tiles[i] === TILE_DECOR) {
        ctx.fillStyle = map.areaSurface === SURFACE_METAL ? '#46506a' : '#3c4660';
        ctx.beginPath(); ctx.arc(x * TILE + TILE / 2, y * TILE + TILE / 2, 3, 0, TAU); ctx.fill();
      }
      if (map.shadow[i]) { ctx.fillStyle = 'rgba(0,0,0,0.45)'; ctx.fillRect(x * TILE, y * TILE, TILE, TILE); }
    }
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      if (map.tiles[y * map.mapW + x] !== TILE_WALL) continue;
      ctx.fillStyle = '#4c5468'; ctx.fillRect(x * TILE, y * TILE, TILE, TILE);
      ctx.fillStyle = '#606a82'; ctx.fillRect(x * TILE, y * TILE, TILE, 3);
    }
  }

  renderProps(ctx) {
    for (const p of this.props) {
      if (p instanceof CrateProp) {
        ctx.fillStyle = '#9c8a78'; ctx.fillRect(p.x - 14, p.y - 14, 28, 28);
        ctx.strokeStyle = '#6d5f50'; ctx.strokeRect(p.x - 14, p.y - 14, 28, 28);
        ctx.fillStyle = '#7a6a56'; ctx.fillRect(p.x - 4, p.y - 4, 8, 8);
      } else {
        ctx.fillStyle = '#3e6f96'; ctx.fillRect(p.x - 12, p.y - 12, 24, 24);
        ctx.fillStyle = '#9df0ff'; ctx.beginPath(); ctx.arc(p.x, p.y, 3, 0, TAU); ctx.fill();
      }
    }
  }
  renderDoors(ctx) {
    for (const d of this.doors) {
      ctx.fillStyle = d.locked ? '#b07830' : '#3fbf6f';
      ctx.fillRect(d.x - 20, d.y - 20, 40, 40);
      ctx.strokeStyle = '#201812'; ctx.strokeRect(d.x - 20, d.y - 20, 40, 40);
      ctx.fillStyle = '#e8d9b0'; ctx.font = '9px monospace'; ctx.textAlign = 'center';
      ctx.fillText(d.locked ? 'LOCK' : 'OPEN', d.x, d.y + 4);
    }
  }
  renderPuzzles(ctx) {
    for (const z of this.puzzles) {
      if (z.solved) {
        ctx.fillStyle = 'rgba(40,255,140,0.8)'; ctx.fillRect(z.x - 24, z.y - 18, 48, 36);
        ctx.strokeStyle = '#28ff8c'; ctx.strokeRect(z.x - 24, z.y - 18, 48, 36);
      } else {
        ctx.fillStyle = '#3c465a'; ctx.fillRect(z.x - 24, z.y - 18, 48, 36);
        const n = z.seq.length, sp = 48 / (n + 1);
        for (let i = 0; i < n; i++) {
          const rx = z.x - 24 + sp * (i + 1);
          ctx.fillStyle = z.progress > i ? '#00dcbe' : '#1d2638';
          ctx.beginPath(); ctx.arc(rx, z.y, z.progress > i ? 5 : 3, 0, TAU); ctx.fill();
        }
      }
    }
  }
  renderCollectibles(ctx) {
    for (const c of this.collectibles) {
      if (!c.active) continue;
      const b = Math.sin(c.anim * 4) * 2;
      ctx.fillStyle = c.color; ctx.beginPath(); ctx.arc(c.x, c.y - 4 + b, 5, 0, TAU); ctx.fill();
      ctx.strokeStyle = 'rgba(255,255,255,0.4)'; ctx.stroke();
    }
  }
  renderNpcs(ctx) {
    for (const n of this.npcs) {
      ctx.fillStyle = n.color; ctx.beginPath(); ctx.arc(n.x, n.y, 9, 0, TAU); ctx.fill();
      ctx.fillStyle = '#fff'; ctx.font = '9px monospace'; ctx.textAlign = 'center';
      ctx.fillText(n.name, n.x, n.y - 14);
    }
  }
  renderEnemies(ctx) {
    for (const d of this.drones) {
      const r = 17;
      ctx.fillStyle = '#383c47'; ctx.beginPath(); ctx.arc(d.x, d.y, r, 0, TAU); ctx.fill();
      ctx.fillStyle = d.dome; ctx.beginPath(); ctx.arc(d.x, d.y - 2, r * 0.55, 0, TAU); ctx.fill();
      ctx.strokeStyle = '#8896b0';
      ctx.beginPath(); ctx.moveTo(d.x, d.y); ctx.lineTo(d.x + Math.cos(d.rot) * (r + 6), d.y + Math.sin(d.rot) * (r + 6)); ctx.stroke();
      if (d.isChasing) {
        ctx.strokeStyle = d.isAlert ? 'rgba(255,60,60,0.4)' : 'rgba(255,213,79,0.35)';
        ctx.beginPath(); ctx.moveTo(d.x, d.y); ctx.lineTo(d.x + Math.cos(d.rot) * 400, d.y + Math.sin(d.rot) * 400); ctx.stroke();
      }
    }
    for (const g of this.guards) {
      const r = 10;
      ctx.fillStyle = '#6e604c'; ctx.beginPath(); ctx.arc(g.x, g.y, r, 0, TAU); ctx.fill();
      ctx.fillStyle = '#46465a'; ctx.beginPath(); ctx.arc(g.x + 2, g.y, r * 0.55, 0, TAU); ctx.fill();
      ctx.strokeStyle = '#dcdce6';
      ctx.beginPath(); ctx.moveTo(g.x, g.y); ctx.lineTo(g.x + Math.cos(g.rot) * (r + 5), g.y + Math.sin(g.rot) * (r + 5)); ctx.stroke();
      if (g.isChasing) {
        ctx.strokeStyle = g.isAlert ? 'rgba(255,60,60,0.35)' : 'rgba(255,200,80,0.3)';
        ctx.beginPath(); ctx.moveTo(g.x, g.y); ctx.lineTo(g.x + Math.cos(g.rot) * 280, g.y + Math.sin(g.rot) * 280); ctx.stroke();
      }
    }
    for (const t of this.turrets) {
      ctx.fillStyle = t.disabled ? '#50505a' : '#787884';
      ctx.beginPath(); ctx.arc(t.x, t.y, 13, 0, TAU); ctx.fill();
      ctx.strokeStyle = '#464654'; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.moveTo(t.x, t.y);
      ctx.lineTo(t.x + Math.cos(t.angle) * 13, t.y + Math.sin(t.angle) * 13); ctx.stroke(); ctx.lineWidth = 1;
      ctx.fillStyle = t.disabled ? '#333' : '#ff5a46';
      ctx.beginPath(); ctx.arc(t.x, t.y, 3.5, 0, TAU); ctx.fill();
    }
  }
  renderProjectiles(ctx) {
    for (const p of this.projectiles) {
      if (!p.active) continue;
      ctx.fillStyle = '#ff8040'; ctx.beginPath(); ctx.arc(p.x, p.y, 5, 0, TAU); ctx.fill();
    }
  }
  renderPlayer(ctx) {
    const p = this.player;
    const bob = Math.sin(p.anim * 10) * (p.sneaking ? 1.5 : 3);
    const cy = p.y + bob, r = 10;
    if (p.invuln > 0 && Math.floor(p.anim * 10) % 2 === 0) {
      ctx.strokeStyle = 'rgba(80,220,255,0.7)'; ctx.beginPath(); ctx.arc(p.x, cy, r + 3, 0, TAU); ctx.stroke();
    }
    ctx.fillStyle = 'rgba(0,0,0,0.3)';
    ctx.beginPath(); ctx.ellipse(p.x, p.y + r + 2, r, r * 0.5, 0, 0, TAU); ctx.fill();
    ctx.fillStyle = '#2e6e6e'; ctx.beginPath(); ctx.arc(p.x, cy, r, 0, TAU); ctx.fill();
    ctx.strokeStyle = '#e1f0eb'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(p.x + Math.cos(p.rot) * 3, cy + Math.sin(p.rot) * 3);
    ctx.lineTo(p.x + Math.cos(p.rot) * (r + 5), cy + Math.sin(p.rot) * (r + 5)); ctx.stroke();
    ctx.lineWidth = 1;
    if (p.sneaking) {
      ctx.strokeStyle = '#e1f0eb';
      ctx.beginPath(); ctx.moveTo(p.x - r + 3, cy - r + 2); ctx.lineTo(p.x + r - 3, cy - r + 2); ctx.stroke();
    }
  }
  renderTones(ctx) {
    const n = 3, r = 26, gap = 20;
    const total = n * (r * 2 + gap), cy = this.h - 90, x0 = this.w / 2 - total / 2 + r;
    const labels = ['LOW', 'MID', 'HI'];
    for (let i = 0; i < n; i++) {
      const x = x0 + i * (r * 2 + gap);
      ctx.fillStyle = 'rgba(50,70,100,0.85)';
      ctx.strokeStyle = '#50e8ff';
      ctx.beginPath(); ctx.arc(x, cy, r, 0, TAU); ctx.fill(); ctx.stroke();
      ctx.fillStyle = '#cfefff'; ctx.font = '10px monospace'; ctx.textAlign = 'center';
      ctx.fillText(labels[i], x, cy + 4);
    }
  }
  renderHUD(ctx) {
    const w = this.w, h = this.h;
    const p = this.player;
    ctx.fillStyle = 'rgba(0,0,0,0.5)'; ctx.fillRect(10, 10, 120, 14);
    ctx.fillStyle = p.stamina >= 20 ? '#50e8ff' : '#ff5050';
    ctx.fillRect(10, 10, 120 * Math.max(0, p.stamina) / 100, 14);
    ctx.fillStyle = '#dfe8ff'; ctx.font = '10px monospace'; ctx.textAlign = 'left';
    ctx.fillText('SONAR', 10, 24);

    ctx.fillStyle = 'rgba(0,0,0,0.5)'; ctx.fillRect(w - 40, 12, 30, 12);
    ctx.fillStyle = this.detection === 0 ? '#3a4a5a' : this.detection === 1 ? '#ffd54f' : '#ff3b3b';
    ctx.fillRect(w - 40, 12, 30, 12);
    ctx.fillStyle = '#dfe8ff'; ctx.font = '10px monospace'; ctx.textAlign = 'right';
    ctx.fillText('WATCH', w - 46, 22);

    ctx.fillStyle = 'rgba(0,0,0,0.5)'; ctx.fillRect(w - 40, 28, 30, 14);
    ctx.fillStyle = '#9be8ff';
    ctx.fillText('#' + this.inventory.list.length, w - 46, 40);

    const B = [
      { id: 'SNEAK', x: w - 76, y: h - 76 },
      { id: 'PULSE', x: w - 40, y: h - 120 },
      { id: 'USE', x: w - 76, y: h - 40 },
      { id: 'WALK', x: 14, y: h - 76 },
      { id: 'INV', x: 14, y: h - 40 },
      { id: 'PAUSE', x: 14, y: 14 },
    ];
    for (const b of B) {
      ctx.fillStyle = b.id === 'SNEAK' && p.sneaking ? 'rgba(80,232,255,0.35)' : 'rgba(20,26,40,0.8)';
      ctx.strokeStyle = '#4c6a94';
      ctx.beginPath(); ctx.arc(b.x, b.y, 18, 0, TAU); ctx.fill(); ctx.stroke();
      ctx.fillStyle = '#cfe0ff'; ctx.font = '9px monospace'; ctx.textAlign = 'center';
      ctx.fillText(b.id, b.x, b.y + 3);
    }

    if (this.hudInv && this.inventory.list.length) {
      ctx.fillStyle = 'rgba(8,10,16,0.88)'; ctx.fillRect(w / 2 - 100, 90, 200, 30 + this.inventory.list.length * 18);
      ctx.fillStyle = '#dff'; ctx.font = '12px monospace'; ctx.textAlign = 'center';
      ctx.fillText('INVENTORY', w / 2, 110);
      ctx.font = '10px monospace'; ctx.textAlign = 'left';
      this.inventory.list.forEach((it, i) => ctx.fillText(it, w / 2 - 90, 128 + i * 18));
    }

    const qs = this.quests.active();
    if (qs.length) {
      ctx.fillStyle = 'rgba(8,10,16,0.72)'; ctx.fillRect(10, 120, 190, 16 + qs.length * 15);
      ctx.fillStyle = '#ffe9a8'; ctx.font = '10px monospace'; ctx.textAlign = 'left';
      qs.forEach((q, i) => ctx.fillText('* ' + q.def.title, 16, 134 + i * 15));
    }
  }
  renderDialogue(ctx) {
    const w = this.w, by = this.h - 150;
    ctx.fillStyle = 'rgba(6,8,14,0.92)'; ctx.fillRect(20, by, w - 40, 100);
    ctx.strokeStyle = '#4fa3a3'; ctx.strokeRect(20, by, w - 40, 100);
    ctx.fillStyle = '#9ee3e3'; ctx.font = 'bold 13px monospace'; ctx.textAlign = 'left';
    ctx.fillText(this.dialogue.speaker, 30, by + 20);
    ctx.fillStyle = '#dfe7ff'; ctx.font = '12px monospace';
    const words = this.dialogue.text.split(' ');
    let line = '', y = by + 40;
    for (const wd of words) {
      const test = line ? line + ' ' + wd : wd;
      if (ctx.measureText(test).width > w - 80 && line) { ctx.fillText(line, 30, y); line = wd; y += 16; }
      else line = test;
    }
    if (line) ctx.fillText(line, 30, y);
    this.choiceRects = [];
    if (this.dialogue.choices) {
      this.dialogue.choices.forEach((c, i) => {
        ctx.fillStyle = '#ffe9a8';
        ctx.fillText('> ' + c.text, 40, by + 70 + i * 16);
        this.choiceRects.push({ x: 40, y: by + 60 + i * 16, w: w - 80, h: 16 });
      });
    }
  }
  renderMenu() {
    const ctx = this.ctx, w = this.w, h = this.h;
    ctx.fillStyle = '#0c0e1a'; ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#aef3ff'; ctx.font = 'bold 30px monospace'; ctx.textAlign = 'center';
    ctx.fillText('THE LOST ECHO', w / 2, h * 0.26);
    ctx.fillStyle = '#55c0d0'; ctx.font = '12px monospace';
    ctx.fillText('a stealth puzzle · JavaScript edition', w / 2, h * 0.26 + 24);
    const labels = ['NEW GAME', this.save.has ? 'CONTINUE' : '', 'HOW TO PLAY', 'RESET SAVE', 'CREDITS'];
    const y0 = h * 0.46;
    this.menuBtns = [];
    let yy = y0;
    for (const l of labels) {
      if (!l) continue;
      this.menuBtns.push({ label: l, x: w / 2, y: yy });
      yy += 34;
    }
    ctx.fillStyle = '#dff5ff'; ctx.font = '15px monospace';
    for (const b of this.menuBtns) ctx.fillText(b.label, b.x, b.y);
  }
  renderCredits() {
    const ctx = this.ctx, w = this.w, h = this.h;
    ctx.fillStyle = '#0c0e1a'; ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#9ee3ff'; ctx.font = '20px monospace'; ctx.textAlign = 'center';
    ctx.fillText('THE LOST ECHO', w / 2, h / 2 - 30);
    ctx.fillStyle = '#7a8aa0'; ctx.font = '12px monospace';
    ctx.fillText('Built entirely in JavaScript.', w / 2, h / 2 - 6);
    ctx.fillText('Tap to return.', w / 2, h / 2 + 20);
  }
  renderHelp() {
    const ctx = this.ctx, w = this.w, h = this.h;
    ctx.fillStyle = '#0c0e1a'; ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#cfe0ff'; ctx.font = '13px monospace'; ctx.textAlign = 'center';
    const lines = [
      'THE LOST ECHO',
      '',
      'Tap ground: move. Avoid vision cones.',
      'BLAST: sonar pulse (stuns guards). Costs stamina.',
      'SNEAK: quieter, hidden in shadows near walls.',
      'USE: talk / open doors / play puzzles.',
      'WALKMAN: play tones. Match the rune sequence.',
      'Walls block sight. Noise alerts enemies.',
      'Collect keycards: blue, yellow, red.',
      '',
      'Tap to return.',
    ];
    lines.forEach((l, i) => ctx.fillText(l, w / 2, h * 0.2 + i * 22));
  }
  renderEnding() {
    const ctx = this.ctx, w = this.w, h = this.h;
    ctx.fillStyle = '#0c0e1a'; ctx.fillRect(0, 0, w, h);
    const guardian = this.endingChoice === CHOICE_SAVE_PARENTS;
    ctx.fillStyle = guardian ? '#9ee3ff' : '#ffd54f';
    ctx.font = 'bold 20px monospace'; ctx.textAlign = 'center';
    ctx.fillText(guardian ? 'ENDING A · THE GUARDIAN\'S CHOICE' : 'ENDING B · THE PARALYZED CITY', w / 2, h * 0.3);
    ctx.fillStyle = '#dfe7ff'; ctx.font = '13px monospace';
    const body = guardian
      ? 'Raka saved their parents. The city remains under Aethelgard watch, but the Echo of freedom still hums in every shadow. The family survives.'
      : 'Raka broadcasts the signal. Every drone goes dark at once. The citizens are free — but Raka\'s parents are unreachable. The city learns to breathe on its own.';
    const words = body.split(' ');
    let line = '', y = h * 0.4;
    for (const wd of words) {
      const test = line ? line + ' ' + wd : wd;
      if (ctx.measureText(test).width > w - 100 && line) { ctx.fillText(line, w / 2, y); line = wd; y += 22; }
      else line = test;
    }
    ctx.fillText(line, w / 2, y);
    ctx.fillStyle = '#7a8aa0'; ctx.font = '12px monospace';
    ctx.fillText('alerts ' + this.alerts + ' · time ' + Math.floor(this.playTime) + 's', w / 2, h - 60);
    ctx.fillText('Tap to return to menu.', w / 2, h - 36);
  }

  loop(now) {
    const dt = Math.min(0.05, (now - this._lastT) / 1000);
    this._lastT = now;
    this.tick(dt);
    this.render();
    requestAnimationFrame(t => this.loop(t));
  }

  bindEvents() {
    window.addEventListener('resize', () => this.resize());
    this.canvas.addEventListener('pointerdown', e => {
      e.preventDefault();
      this.audio.resume();
      const rect = this.canvas.getBoundingClientRect();
      this.handleTap(e.clientX - rect.left, e.clientY - rect.top);
    });
    window.addEventListener('keydown', e => {
      if (e.key === ' ') { this.handleTap(this.w / 2, this.h / 2); }
      if (e.key === 'p') { if (this.state === 'GAMEPLAY') this.state = 'PAUSE'; else if (this.state === 'PAUSE') this.state = 'GAMEPLAY'; }
    });
  }
}

// ---------------------------------------------------------------------------
// Area content
// ---------------------------------------------------------------------------
const AREA_CFG = [
  { w: 60, h: 40, seed: 11, surface: SURFACE_CONCRETE },
  { w: 70, h: 44, seed: 22, surface: SURFACE_METAL },
  { w: 55, h: 40, seed: 33, surface: SURFACE_METAL },
  { w: 64, h: 42, seed: 44, surface: SURFACE_CONCRETE },
];

const AREA_BUILD = {
  [AREA_SLUM]: g => {
    const s = g.floorNear(3, 3);
    g.player.x = s.x; g.player.y = s.y;
    g.player.checkX = s.x; g.player.checkY = s.y;
    g.addNpc(3, 3, 'Meera', 'meera_intro', '#4fa3a3');
    const j = g.floorNear(12, 8);
    g.addNpc(Math.floor(j.x / TILE), Math.floor(j.y / TILE), 'Juno', 'juno_talk', '#a08050');
    g.addCollectible(28, 12, 'walkman', '#ffe082');
    g.addCollectible(20, 20, 'stone', '#bcaaa4');
    g.addCollectible(24, 10, 'stone', '#bcaaa4');
    g.addCollectible(34, 28, 'battery_pack', '#80d8ff');
    g.addCrate(10, 10); g.addCrate(10, 11); g.addCrate(16, 20); g.addCrate(17, 20);
    g.addDoor('lab_gate', 57, 20, 'keycard_blue');
    g.addPuzzle(40, 30, [TONE_LOW, TONE_MID, TONE_HIGH], -1, '');
    g.addDrone(200, 400, [[200, 400], [420, 400], [420, 600], [200, 600]]);
    g.addDrone(900, 200, [[900, 200], [900, 600], [620, 600]]);
  },
  [AREA_LAB]: g => {
    const s = g.floorNear(2, 20);
    g.player.x = s.x; g.player.y = s.y;
    g.player.checkX = s.x; g.player.checkY = s.y;
    const w = g.floorNear(6, 20);
    g.addNpc(Math.floor(w.x / TILE), Math.floor(w.y / TILE), 'Kess', 'warden_lab', '#6e8ca0');
    g.addCollectible(30, 6, 'keycard_blue', '#4fc3f7');
    g.addCollectible(8, 36, 'stone', '#bcaaa4');
    g.addCollectible(8, 37, 'stone', '#bcaaa4');
    g.addCollectible(46, 6, 'battery_pack', '#80d8ff');
    g.addCollectible(50, 8, 'keycard_yellow', '#ffee58');
    g.addTurret(44, 22);
    g.addPuzzle(40, 26, [TONE_MID, TONE_HIGH, TONE_LOW, TONE_HIGH], 0, '');
    g.addDoor('lab_door', 55, 22, 'keycard_blue');
    g.addDoor('basement_gate', 68, 10, 'keycard_yellow');
    g.addGuard(300, 600, [[300, 600], [520, 600]]);
    g.addGuard(600, 300, [[600, 300], [600, 700]]);
    g.addDrone(900, 300, [[900, 300], [1300, 300]]);
  },
  [AREA_BASEMENT]: g => {
    const s = g.floorNear(2, 20);
    g.player.x = s.x; g.player.y = s.y;
    g.player.checkX = s.x; g.player.checkY = s.y;
    g.addCollectible(40, 6, 'usb_drive', '#b388ff');
    g.addCollectible(45, 10, 'freq_modulator', '#69f0ae');
    g.addCollectible(30, 34, 'battery_pack', '#80d8ff');
    g.addCollectible(12, 8, 'keycard_red', '#ff5252');
    g.addCrate(26, 10); g.addCrate(26, 11);
    g.addTurret(30, 18);
    g.addPuzzle(26, 16, [TONE_HIGH, TONE_LOW, TONE_MID, TONE_HIGH, TONE_LOW], 0, '');
    g.addTurret(50, 30);
    g.addPuzzle(46, 28, [TONE_MID, TONE_HIGH, TONE_LOW, TONE_MID, TONE_HIGH, TONE_LOW], 1, '');
    g.addDoor('data_gate', 52, 20, 'keycard_red');
    g.addDrone(500, 200, [[500, 200], [1000, 200], [1000, 600]]);
    g.addGuard(600, 900, [[600, 900], [600, 500]]);
  },
  [AREA_DATA]: g => {
    const s = g.floorNear(2, 20);
    g.player.x = s.x; g.player.y = s.y;
    g.player.checkX = s.x; g.player.checkY = s.y;
    g.addCollectible(30, 6, 'battery_pack', '#80d8ff');
    g.addCollectible(34, 8, 'battery_pack', '#80d8ff');
    g.addConsole(20, 20, CHOICE_SAVE_PARENTS);
    g.addConsole(40, 20, CHOICE_PARALYZE_CITY);
    g.addDrone(900, 300, [[900, 300], [1500, 300], [1500, 700], [900, 700]]);
    g.addGuard(800, 1000, [[800, 1000], [1500, 1000]]);
    g.addPuzzle(30, 30, [TONE_LOW, TONE_HIGH, TONE_MID, TONE_LOW, TONE_HIGH, TONE_MID], -1, '');
  },
};

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------
function boot() {
  window.game = new Game();
  window.game.bindEvents();
  window.game.loop(performance.now());
}
if (typeof window !== 'undefined') {
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
}

// ---------------------------------------------------------------------------
// Headless self-check (node: node game.js check)
// ---------------------------------------------------------------------------
if (typeof window === 'undefined' && typeof module !== 'undefined' && process.argv[2] === 'check') {
  const assert = (cond, msg) => { if (!cond) { console.error('FAIL: ' + msg); process.exitCode = 1; } else console.log('ok: ' + msg); };
  const map = new TileMap();
  map.generateMap(60, 40, 11, SURFACE_CONCRETE);
  assert(map.mapW === 60 && map.mapH === 40, 'map size');
  assert(map.coll[0] === 1, 'border wall blocked');
  const astar = new AStar(map);
  const path = astar.findPath(96, 96, 1500, 900);
  assert(path.length > 0, 'path found from (96,96) to (1500,900) len=' + path.length);
  let walkable = true;
  for (const [px, py] of path) if (map.isBlocked(px, py)) walkable = false;
  assert(walkable, 'path tiles walkable');
  assert(hears(0, 0, 100, 0, 210, MODE_RUN) === true, 'hearing run');
  assert(hears(0, 0, 400, 0, 210, MODE_SNEAK) === false, 'hearing sneak far');
  assert(canSee(map, 100, 100, 0, Math.PI / 3, 400, 200, 100) === true, 'line of sight clear');
  const z = new AcousticPuzzle(null);
  z.init(0, 0, [TONE_LOW, TONE_HIGH, TONE_MID]);
  z.pressTone(TONE_LOW); z.pressTone(TONE_HIGH); z.pressTone(TONE_MID);
  assert(z.solved === true, 'puzzle solves with correct sequence');
  const z2 = new AcousticPuzzle(null);
  z2.init(0, 0, [TONE_LOW, TONE_HIGH]);
  z2.pressTone(TONE_LOW); z2.pressTone(TONE_LOW);
  assert(z2.solved === false && z2.count === 0, 'wrong tone resets puzzle');
  console.log(process.exitCode ? 'SELF-CHECK FAILED' : 'SELF-CHECK PASSED');
}
