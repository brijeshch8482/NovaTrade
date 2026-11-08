/*
 * Drawing engine for the NovaTrade chart.
 *
 * Lightweight Charts has no drawing tools, so drawings live on a canvas laid over the chart and are
 * redrawn every frame from data-space points ({time, price}), which keeps them attached to the
 * candles while the user scrolls and zooms. Anchor handles and order tags are small DOM elements
 * so they can take touches while the rest of the chart keeps panning normally.
 *
 * Uses the globals `chart`, `series`, `bars`, `precision` and `LWC` from index.html.
 */
(function () {
    const BLUE = '#2962FF';
    const BLUE_POS = '#2962FF';
    const RED_POS = '#F23645';
    const BOX_BG = '#0F0F0F';
    const css = (el, s) => Object.assign(el.style, s);

    const canvas = document.createElement('canvas');
    css(canvas, { position: 'fixed', left: '0', top: '0', pointerEvents: 'none', zIndex: '3' });
    document.body.appendChild(canvas);
    const ctx = canvas.getContext('2d');

    const handleLayer = document.createElement('div');
    css(handleLayer, { position: 'fixed', left: '0', top: '0', width: '0', height: '0', zIndex: '6' });
    document.body.appendChild(handleLayer);

    // Covers the plot while a tool is placing points: drags move the cursor, a tap places a point.
    const capture = document.createElement('div');
    css(capture, { position: 'fixed', left: '0', top: '0', display: 'none', zIndex: '5', touchAction: 'none' });
    document.body.appendChild(capture);

    const hint = document.createElement('div');
    css(hint, {
        position: 'fixed', left: '8px', bottom: '34px', zIndex: '7', display: 'none', maxWidth: '230px',
        background: '#2962FF', color: '#fff', font: '13px sans-serif', padding: '8px 28px 8px 10px', borderRadius: '6px',
    });
    const hintText = document.createElement('span');
    const hintClose = document.createElement('span');
    hintClose.textContent = '✕';
    css(hintClose, { position: 'absolute', right: '8px', top: '6px', cursor: 'pointer', padding: '2px' });
    hint.appendChild(hintText);
    hint.appendChild(hintClose);
    document.body.appendChild(hint);

    let tool = 'cursor';
    let stay = false;
    let magnet = 0; // 0 off, 1 weak, 2 strong
    let visible = true;
    let lockedAll = false;
    let drawings = [];
    let nextId = 1;
    let selectedId = null;
    let placing = null; // { type, points: [], cursor: {x, y}, free: [] }
    let hintDismissed = false;
    let order = null; // { price, side, lots, tp, sl, digits }
    const defaults = {}; // per-tool style saved with "templates"

    const android = () => window.Android;

    // ---- coordinates -------------------------------------------------------------------------

    /**
     * Plot area. [x0] is where it starts (the price scale can sit on the left), and [ax] is where
     * price labels go on that scale, both relative to the plot.
     */
    function pane() {
        const rw = scaleWidth('right');
        const lw = scaleWidth('left');
        const tsh = chart.timeScale().height();
        const w = Math.max(0, window.innerWidth - rw - lw);
        return { w: w, h: Math.max(0, window.innerHeight - tsh), psw: rw || lw, tsh: tsh, x0: lw, ax: lw ? 2 - lw : w + 4 };
    }

    function step() {
        return bars.length > 1 ? bars[bars.length - 1].time - bars[bars.length - 2].time : 60;
    }

    function timeToLogical(t) {
        if (!bars.length) return 0;
        const last = bars.length - 1;
        if (t >= bars[last].time) return last + (t - bars[last].time) / step();
        if (t <= bars[0].time) return (t - bars[0].time) / step();
        let lo = 0, hi = last;
        while (hi - lo > 1) {
            const mid = (lo + hi) >> 1;
            if (bars[mid].time <= t) lo = mid; else hi = mid;
        }
        return lo + (t - bars[lo].time) / (bars[hi].time - bars[lo].time);
    }

    function logicalToTime(l) {
        const last = bars.length - 1;
        if (l >= last) return bars[last].time + (l - last) * step();
        if (l <= 0) return bars[0].time + l * step();
        const i = Math.floor(l);
        return bars[i].time + (l - i) * (bars[i + 1].time - bars[i].time);
    }

    function toXY(p) {
        if (p.fx != null) {
            const pn = pane();
            return { x: p.fx * pn.w, y: p.fy * pn.h };
        }
        const x = chart.timeScale().logicalToCoordinate(timeToLogical(p.time));
        const y = series.priceToCoordinate(p.price);
        return x == null || y == null ? null : { x: x, y: y };
    }

    function fromXY(x, y) {
        const l = chart.timeScale().coordinateToLogical(x);
        const price = series.coordinateToPrice(y);
        if (l == null || price == null) return null;
        return { time: logicalToTime(l), price: price };
    }

    function snap(x, y) {
        if (!magnet || !bars.length) return { x: x, y: y };
        const l = Math.round(chart.timeScale().coordinateToLogical(x));
        const b = bars[Math.max(0, Math.min(bars.length - 1, l))];
        const bx = chart.timeScale().logicalToCoordinate(Math.max(0, Math.min(bars.length - 1, l)));
        let best = null;
        [b.open, b.high, b.low, b.close].forEach((v) => {
            const yy = series.priceToCoordinate(v);
            if (yy != null && (best == null || Math.abs(yy - y) < Math.abs(best - y))) best = yy;
        });
        if (best == null) return { x: x, y: y };
        if (magnet === 1 && Math.abs(best - y) > 24) return { x: x, y: y };
        return { x: bx, y: best };
    }

    // ---- drawing helpers ---------------------------------------------------------------------

    let rec = null; // hit-test paths of the drawing being rendered

    function rgba(hex, a) {
        const h = hex.replace('#', '');
        const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16);
        return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')';
    }

    function S(path) {
        ctx.stroke(path);
        if (rec) rec.strokes.push(path);
    }

    function F(path, color) {
        ctx.save();
        ctx.fillStyle = color;
        ctx.fill(path);
        ctx.restore();
        if (rec) rec.fills.push(path);
    }

    function seg(a, b) {
        const p = new Path2D();
        p.moveTo(a.x, a.y);
        p.lineTo(b.x, b.y);
        return p;
    }

    function poly(pts, close) {
        const p = new Path2D();
        pts.forEach((q, i) => (i ? p.lineTo(q.x, q.y) : p.moveTo(q.x, q.y)));
        if (close) p.closePath();
        return p;
    }

    function ext(a, b, back, fwd) {
        const dx = b.x - a.x, dy = b.y - a.y;
        const len = Math.hypot(dx, dy) || 1;
        const L = 20000;
        return {
            a: back ? { x: a.x - (dx / len) * L, y: a.y - (dy / len) * L } : a,
            b: fwd ? { x: b.x + (dx / len) * L, y: b.y + (dy / len) * L } : b,
        };
    }

    function mid(a, b) {
        return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
    }

    function dashed(fn) {
        ctx.save();
        ctx.setLineDash([5, 4]);
        fn();
        ctx.restore();
    }

    function fmt(v) {
        return Number(v).toLocaleString('en-US', { minimumFractionDigits: precision, maximumFractionDigits: precision });
    }

    function text(str, x, y, o) {
        o = o || {};
        const size = o.size || 12;
        ctx.save();
        ctx.font = (o.bold ? 'bold ' : '') + size + 'px sans-serif';
        const lines = String(str).split('\n');
        const w = Math.max.apply(null, lines.map((l) => ctx.measureText(l).width));
        const h = lines.length * (size + 3);
        let bx = x, by = y;
        if (o.align === 'center') bx = x - w / 2;
        if (o.align === 'right') bx = x - w;
        if (o.base === 'middle') by = y - h / 2;
        if (o.base === 'bottom') by = y - h;
        const pad = o.bg ? 4 : 0;
        if (o.bg) {
            const box = new Path2D();
            box.rect(bx - pad, by - pad, w + pad * 2, h + pad * 2);
            ctx.fillStyle = o.bg;
            ctx.fill(box);
            if (o.border) {
                ctx.strokeStyle = o.border;
                ctx.lineWidth = 1;
                ctx.setLineDash([]);
                ctx.stroke(box);
            }
            if (rec) rec.fills.push(box);
        }
        ctx.fillStyle = o.color || '#fff';
        ctx.textBaseline = 'top';
        lines.forEach((l, i) => ctx.fillText(l, bx, by + i * (size + 3)));
        ctx.restore();
        return { x: bx - pad, y: by - pad, w: w + pad * 2, h: h + pad * 2 };
    }

    function arrowHead(from, to, size) {
        const ang = Math.atan2(to.y - from.y, to.x - from.x);
        const p = new Path2D();
        p.moveTo(to.x, to.y);
        p.lineTo(to.x - size * Math.cos(ang - 0.45), to.y - size * Math.sin(ang - 0.45));
        p.lineTo(to.x - size * Math.cos(ang + 0.45), to.y - size * Math.sin(ang + 0.45));
        p.closePath();
        return p;
    }

    function hline(y, x0, x1) {
        return seg({ x: x0, y: y }, { x: x1, y: y });
    }

    function vline(x) {
        return seg({ x: x, y: -5000 }, { x: x, y: 5000 });
    }

    function priceAt(d, i) {
        return d.points[i].price;
    }

    // ---- tool definitions ----------------------------------------------------------------------
    // n: number of points (-1 = until finished, 0 = freehand). draw(P, d) with P in pixels.

    const FIB = [[0, '#787B86'], [0.236, '#F23645'], [0.382, '#FF9800'], [0.5, '#4CAF50'], [0.618, '#089981'], [0.786, '#00BCD4'], [1, '#787B86'], [1.618, '#2962FF']];
    const FIB_TIME = [0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89];
    const FIB_FAN = [0.25, 0.382, 0.5, 0.618, 0.75, 1];

    function fibBands(levels, priceOf, x0, x1, d) {
        const ys = levels.map(([l, c]) => ({ y: series.priceToCoordinate(priceOf(l)), c: c, l: l }));
        for (let i = 0; i < ys.length - 1; i++) {
            if (ys[i].y == null || ys[i + 1].y == null) continue;
            const r = new Path2D();
            r.rect(Math.min(x0, x1), Math.min(ys[i].y, ys[i + 1].y), Math.abs(x1 - x0), Math.abs(ys[i + 1].y - ys[i].y));
            F(r, rgba(ys[i + 1].c, 0.12));
        }
        ys.forEach((o) => {
            if (o.y == null) return;
            ctx.strokeStyle = o.c;
            S(hline(o.y, x0, x1));
            text(o.l + ' (' + fmt(priceOf(o.l)) + ')', Math.min(x0, x1) + 2, o.y, { color: o.c, base: 'bottom', size: 11 });
        });
        ctx.strokeStyle = d.color;
    }

    function labelled(P, labels, d, fill) {
        if (fill) {
            for (let i = 0; i + 2 < P.length; i += 2) F(poly([P[i], P[i + 1], P[i + 2]], true), rgba(d.color, 0.15));
        }
        S(poly(P));
        P.forEach((p, i) => {
            if (!labels[i]) return;
            const up = i > 0 ? p.y < P[i - 1].y : P.length > 1 && p.y < P[1].y;
            text(labels[i], p.x, up ? p.y - 6 : p.y + 6, {
                align: 'center', base: up ? 'bottom' : 'top', bg: d.color, color: '#fff', size: 11,
            });
        });
    }

    function channel(a, b, a2, b2, d) {
        F(poly([a, b, b2, a2], true), rgba(d.color, 0.12));
        S(seg(a, b));
        S(seg(a2, b2));
        dashed(() => S(seg(mid(a, a2), mid(b, b2))));
    }

    function pitch(O, p1, p2, d) {
        const M = mid(p1, p2);
        const e = ext(O, M, false, true);
        const dx = e.b.x - M.x, dy = e.b.y - M.y;
        const u1 = { x: p1.x + dx, y: p1.y + dy }, u2 = { x: p2.x + dx, y: p2.y + dy };
        F(poly([p1, u1, u2, p2], true), rgba(d.color, 0.1));
        S(seg(O, e.b));
        S(seg(p1, u1));
        S(seg(p2, u2));
        S(seg(p1, p2));
    }

    function regression(d, P) {
        const t0 = Math.min(d.points[0].time, d.points[1].time), t1 = Math.max(d.points[0].time, d.points[1].time);
        const pts = bars.filter((b) => b.time >= t0 && b.time <= t1);
        if (pts.length < 2) return S(seg(P[0], P[1]));
        const xs = pts.map((b) => timeToLogical(b.time)), ys = pts.map((b) => b.close);
        const n = xs.length, mx = xs.reduce((a, v) => a + v, 0) / n, my = ys.reduce((a, v) => a + v, 0) / n;
        let num = 0, den = 0;
        xs.forEach((x, i) => {
            num += (x - mx) * (ys[i] - my);
            den += (x - mx) * (x - mx);
        });
        const k = den ? num / den : 0;
        const at = (x) => my + k * (x - mx);
        const sd = Math.sqrt(ys.reduce((a, v, i) => a + Math.pow(v - at(xs[i]), 2), 0) / n);
        const X0 = chart.timeScale().logicalToCoordinate(xs[0]), X1 = chart.timeScale().logicalToCoordinate(xs[n - 1]);
        const line = (off) => [
            { x: X0, y: series.priceToCoordinate(at(xs[0]) + off) },
            { x: X1, y: series.priceToCoordinate(at(xs[n - 1]) + off) },
        ];
        const up = line(2 * sd), mdl = line(0), dn = line(-2 * sd);
        F(poly([up[0], up[1], dn[1], dn[0]], true), rgba(d.color, 0.12));
        S(seg(up[0], up[1]));
        S(seg(dn[0], dn[1]));
        dashed(() => S(seg(mdl[0], mdl[1])));
    }

    function arrowGlyph(p, dir, color) {
        const s = 9;
        const shapes = {
            up: [[0, -s * 1.6], [s, -s * 0.4], [s * 0.4, -s * 0.4], [s * 0.4, s], [-s * 0.4, s], [-s * 0.4, -s * 0.4], [-s, -s * 0.4]],
        };
        const rot = { up: 0, right: Math.PI / 2, down: Math.PI, left: -Math.PI / 2 }[dir];
        const pts = shapes.up.map(([x, y]) => ({
            x: p.x + x * Math.cos(rot) - y * Math.sin(rot),
            y: p.y + x * Math.sin(rot) + y * Math.cos(rot),
        }));
        F(poly(pts, true), color);
    }

    function bubble(p, str, d, tail) {
        const b = text(str || 'Text', p.x, p.y - (tail ? 14 : 0), { align: 'center', base: 'bottom', bg: rgba(d.color, 0.9), color: '#fff', size: 12 });
        if (tail) F(poly([{ x: p.x - 5, y: b.y + b.h }, { x: p.x + 5, y: b.y + b.h }, { x: p.x, y: p.y }], true), rgba(d.color, 0.9));
        return b;
    }

    const TOOLS = {
        // Lines
        trend_line: { n: 2, draw: (P) => S(seg(P[0], P[1])) },
        ray: { n: 2, draw: (P) => { const e = ext(P[0], P[1], false, true); S(seg(e.a, e.b)); } },
        info_line: {
            n: 2,
            draw: (P, d) => {
                S(seg(P[0], P[1]));
                const dp = priceAt(d, 1) - priceAt(d, 0), pct = (dp / priceAt(d, 0)) * 100;
                const nb = Math.round(Math.abs(timeToLogical(d.points[1].time) - timeToLogical(d.points[0].time)));
                const ang = (Math.atan2(P[0].y - P[1].y, P[1].x - P[0].x) * 180) / Math.PI;
                text(fmt(dp) + ' (' + pct.toFixed(2) + '%)\n' + nb + ' bars, ' + ang.toFixed(1) + '°', P[1].x + 8, P[1].y,
                    { base: 'middle', bg: '#1E222D', border: d.color, size: 11 });
            },
        },
        extended_line: { n: 2, draw: (P) => { const e = ext(P[0], P[1], true, true); S(seg(e.a, e.b)); } },
        trend_angle: {
            n: 2,
            draw: (P, d) => {
                S(seg(P[0], P[1]));
                const r = Math.min(40, Math.hypot(P[1].x - P[0].x, P[1].y - P[0].y));
                dashed(() => S(seg(P[0], { x: P[0].x + r + 20, y: P[0].y })));
                const ang = Math.atan2(P[1].y - P[0].y, P[1].x - P[0].x);
                const arc = new Path2D();
                arc.arc(P[0].x, P[0].y, r * 0.7, Math.min(0, ang), Math.max(0, ang));
                S(arc);
                text(((-ang * 180) / Math.PI).toFixed(1) + '°', P[0].x + r + 4, P[0].y, { base: 'middle', color: d.color, size: 11 });
            },
        },
        horizontal_line: { n: 1, axis: true, draw: (P) => S(hline(P[0].y, -10, pane().w)) },
        horizontal_ray: { n: 1, axis: true, draw: (P) => S(hline(P[0].y, P[0].x, pane().w)) },
        vertical_line: { n: 1, draw: (P) => S(vline(P[0].x)) },
        cross_line: { n: 1, axis: true, draw: (P) => { S(vline(P[0].x)); S(hline(P[0].y, -10, pane().w)); } },

        // Channels
        parallel_channel: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return S(seg(P[0], P[1]));
                const t = (P[2].x - P[0].x) / ((P[1].x - P[0].x) || 1);
                const yOn = P[0].y + (P[1].y - P[0].y) * t, off = P[2].y - yOn;
                channel(P[0], P[1], { x: P[0].x, y: P[0].y + off }, { x: P[1].x, y: P[1].y + off }, d);
            },
        },
        regression_trend: { n: 2, draw: (P, d) => regression(d, P) },
        flat_top_bottom: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return S(seg(P[0], P[1]));
                channel(P[0], P[1], { x: P[0].x, y: P[2].y }, { x: P[1].x, y: P[2].y }, d);
            },
        },
        disjoint_channel: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return S(seg(P[0], P[1]));
                const a2 = { x: P[0].x, y: P[2].y }, b2 = { x: P[1].x, y: P[2].y - (P[1].y - P[0].y) };
                F(poly([P[0], P[1], b2, a2], true), rgba(d.color, 0.12));
                S(seg(P[0], P[1]));
                S(seg(a2, b2));
            },
        },

        // Pitchforks
        pitchfork: { n: 3, draw: (P, d) => (P.length < 3 ? S(poly(P)) : pitch(P[0], P[1], P[2], d)) },
        schiff_pitchfork: { n: 3, draw: (P, d) => (P.length < 3 ? S(poly(P)) : pitch({ x: P[0].x, y: (P[0].y + P[1].y) / 2 }, P[1], P[2], d)) },
        modified_schiff_pitchfork: { n: 3, draw: (P, d) => (P.length < 3 ? S(poly(P)) : pitch(mid(P[0], P[1]), P[1], P[2], d)) },
        inside_pitchfork: { n: 3, draw: (P, d) => (P.length < 3 ? S(poly(P)) : pitch(mid(P[0], mid(P[1], P[2])), P[1], P[2], d)) },

        // Fibonacci
        fib_retracement: {
            n: 2,
            draw: (P, d) => {
                const a = priceAt(d, 0), b = priceAt(d, 1);
                fibBands(FIB, (l) => b + (a - b) * l, P[0].x, Math.max(P[1].x, P[0].x + 40), d);
                dashed(() => S(seg(P[0], P[1])));
            },
        },
        trend_fib_extension: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return dashed(() => S(poly(P)));
                const a = priceAt(d, 0), b = priceAt(d, 1), c = priceAt(d, 2);
                fibBands(FIB, (l) => c + (b - a) * l, P[2].x, P[2].x + Math.max(60, Math.abs(P[1].x - P[0].x)), d);
                dashed(() => S(poly(P)));
            },
        },
        fib_channel: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return S(seg(P[0], P[1]));
                const t = (P[2].x - P[0].x) / ((P[1].x - P[0].x) || 1);
                const off = P[2].y - (P[0].y + (P[1].y - P[0].y) * t);
                FIB.forEach(([l, c]) => {
                    ctx.strokeStyle = c;
                    const e = ext({ x: P[0].x, y: P[0].y + off * l }, { x: P[1].x, y: P[1].y + off * l }, false, true);
                    S(seg(e.a, e.b));
                });
                ctx.strokeStyle = d.color;
            },
        },
        fib_time_zone: {
            n: 2,
            draw: (P, d) => {
                const w = P[1].x - P[0].x;
                FIB_TIME.forEach((f, i) => {
                    ctx.strokeStyle = FIB[i % FIB.length][1];
                    S(vline(P[0].x + w * f));
                    text(String(f), P[0].x + w * f + 3, 4, { color: FIB[i % FIB.length][1], size: 11 });
                });
                ctx.strokeStyle = d.color;
            },
        },
        fib_speed_fan: {
            n: 2,
            draw: (P, d) => {
                const w = P[1].x - P[0].x, h = P[1].y - P[0].y;
                const box = new Path2D();
                box.rect(Math.min(P[0].x, P[1].x), Math.min(P[0].y, P[1].y), Math.abs(w), Math.abs(h));
                dashed(() => S(box));
                FIB_FAN.forEach((l, i) => {
                    ctx.strokeStyle = FIB[(i + 1) % FIB.length][1];
                    let e = ext(P[0], { x: P[1].x, y: P[0].y + h * l }, false, true);
                    S(seg(e.a, e.b));
                    e = ext(P[0], { x: P[0].x + w * l, y: P[1].y }, false, true);
                    S(seg(e.a, e.b));
                });
                ctx.strokeStyle = d.color;
            },
        },
        trend_fib_time: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return dashed(() => S(poly(P)));
                const w = P[1].x - P[0].x;
                [0, 0.382, 0.5, 0.618, 1, 1.382, 1.618, 2, 2.618, 3.618, 4.236].forEach((l, i) => {
                    ctx.strokeStyle = FIB[i % FIB.length][1];
                    S(vline(P[2].x + w * l));
                    text(String(l), P[2].x + w * l + 3, 4, { color: FIB[i % FIB.length][1], size: 11 });
                });
                ctx.strokeStyle = d.color;
                dashed(() => S(poly(P)));
            },
        },
        fib_circles: {
            n: 2,
            draw: (P, d) => {
                const r = Math.hypot(P[1].x - P[0].x, P[1].y - P[0].y);
                FIB.slice(1).forEach(([l, c]) => {
                    ctx.strokeStyle = c;
                    const p = new Path2D();
                    p.arc(P[0].x, P[0].y, r * l, 0, Math.PI * 2);
                    S(p);
                });
                ctx.strokeStyle = d.color;
                dashed(() => S(seg(P[0], P[1])));
            },
        },
        fib_spiral: {
            n: 2,
            draw: (P) => {
                const r0 = Math.hypot(P[1].x - P[0].x, P[1].y - P[0].y);
                const a0 = Math.atan2(P[1].y - P[0].y, P[1].x - P[0].x);
                const phi = 1.618033988749895, p = new Path2D();
                for (let t = -8 * Math.PI; t <= 2 * Math.PI; t += 0.05) {
                    const r = r0 * Math.pow(phi, t / (Math.PI / 2));
                    const x = P[0].x + r * Math.cos(a0 + t), y = P[0].y + r * Math.sin(a0 + t);
                    if (t === -8 * Math.PI) p.moveTo(x, y); else p.lineTo(x, y);
                }
                S(p);
                dashed(() => S(seg(P[0], P[1])));
            },
        },
        fib_speed_arcs: {
            n: 2,
            draw: (P, d) => {
                const r = Math.hypot(P[1].x - P[0].x, P[1].y - P[0].y);
                const upward = P[1].y < P[0].y;
                FIB.slice(1, 7).forEach(([l, c]) => {
                    ctx.strokeStyle = c;
                    const p = new Path2D();
                    p.arc(P[1].x, P[1].y, r * l, upward ? 0 : Math.PI, upward ? Math.PI : Math.PI * 2);
                    S(p);
                });
                ctx.strokeStyle = d.color;
                dashed(() => S(seg(P[0], P[1])));
            },
        },
        fib_wedge: {
            n: 3,
            draw: (P, d) => {
                S(seg(P[0], P[1]));
                if (P.length < 3) return;
                S(seg(P[0], P[2]));
                const r = Math.hypot(P[1].x - P[0].x, P[1].y - P[0].y);
                const a1 = Math.atan2(P[1].y - P[0].y, P[1].x - P[0].x), a2 = Math.atan2(P[2].y - P[0].y, P[2].x - P[0].x);
                FIB.slice(1, 7).forEach(([l, c]) => {
                    ctx.strokeStyle = c;
                    const p = new Path2D();
                    p.arc(P[0].x, P[0].y, r * l, Math.min(a1, a2), Math.max(a1, a2));
                    S(p);
                });
                ctx.strokeStyle = d.color;
            },
        },

        // Patterns
        xabcd_pattern: { n: 5, draw: (P, d) => labelled(P, ['X', 'A', 'B', 'C', 'D'], d, true) },
        cypher_pattern: { n: 5, draw: (P, d) => labelled(P, ['X', 'A', 'B', 'C', 'D'], d, true) },
        head_shoulders: {
            n: 7,
            draw: (P, d) => {
                labelled(P, ['', 'Left Shoulder', '', 'Head', '', 'Right Shoulder', ''], d, false);
                if (P.length >= 5) {
                    const e = ext(P[2], P[4], true, true);
                    dashed(() => S(seg(e.a, e.b)));
                }
            },
        },
        abcd_pattern: {
            n: 4,
            draw: (P, d) => {
                labelled(P, ['A', 'B', 'C', 'D'], d, false);
                if (P.length >= 3) dashed(() => S(seg(P[0], P[2])));
                if (P.length >= 4) dashed(() => S(seg(P[1], P[3])));
            },
        },
        triangle_pattern: {
            n: 4,
            draw: (P, d) => {
                if (P.length === 4) F(poly(P, true), rgba(d.color, 0.12));
                labelled(P, ['A', 'B', 'C', 'D'], d, false);
                if (P.length >= 3) dashed(() => S(seg(P[0], P[2])));
                if (P.length >= 4) dashed(() => S(seg(P[1], P[3])));
            },
        },
        three_drives: { n: 7, draw: (P, d) => labelled(P, ['', '', 'Drive 1', '', 'Drive 2', '', 'Drive 3'], d, false) },

        // Elliott waves
        elliott_impulse: { n: 6, draw: (P, d) => labelled(P, ['(0)', '(1)', '(2)', '(3)', '(4)', '(5)'], d, false) },
        elliott_correction: { n: 4, draw: (P, d) => labelled(P, ['(0)', '(A)', '(B)', '(C)'], d, false) },
        elliott_triangle: { n: 6, draw: (P, d) => labelled(P, ['(0)', '(A)', '(B)', '(C)', '(D)', '(E)'], d, false) },
        elliott_double: { n: 4, draw: (P, d) => labelled(P, ['(0)', '(W)', '(X)', '(Y)'], d, false) },
        elliott_triple: { n: 6, draw: (P, d) => labelled(P, ['(0)', '(W)', '(X)', '(Y)', '(X)', '(Z)'], d, false) },

        // Cycles
        cyclic_lines: {
            n: 2,
            draw: (P) => {
                const w = Math.abs(P[1].x - P[0].x) || 10;
                for (let x = P[0].x; x < pane().w + w; x += w) S(vline(x));
            },
        },
        time_cycles: {
            n: 2,
            draw: (P) => {
                const w = Math.abs(P[1].x - P[0].x) || 10;
                for (let x = Math.min(P[0].x, P[1].x); x < pane().w + w; x += w) {
                    const p = new Path2D();
                    p.ellipse(x + w / 2, P[0].y, w / 2, Math.min(w / 2, 200), 0, Math.PI, 0);
                    S(p);
                }
            },
        },
        sine_line: {
            n: 2,
            draw: (P) => {
                const half = Math.abs(P[1].x - P[0].x) || 10, amp = (P[1].y - P[0].y) / 2, cy = (P[0].y + P[1].y) / 2;
                const p = new Path2D();
                for (let x = P[0].x - half * 8; x < pane().w + half; x += 3) {
                    const y = cy - amp * Math.cos(((x - P[0].x) / half) * Math.PI);
                    if (x === P[0].x - half * 8) p.moveTo(x, y); else p.lineTo(x, y);
                }
                S(p);
            },
        },

        // Brushes
        brush: { n: 0, draw: (P) => S(poly(P)) },
        highlighter: {
            n: 0,
            draw: (P, d) => {
                ctx.save();
                ctx.lineWidth = 14;
                ctx.lineCap = 'round';
                ctx.strokeStyle = rgba(d.color, 0.35);
                S(poly(P));
                ctx.restore();
            },
        },

        // Arrows
        arrow_marker: {
            n: 2,
            draw: (P, d) => {
                const a = P[0], b = P[1], ang = Math.atan2(b.y - a.y, b.x - a.x), w = 7, hl = 18;
                const nx = -Math.sin(ang), ny = Math.cos(ang);
                const bx = b.x - Math.cos(ang) * hl, by = b.y - Math.sin(ang) * hl;
                F(poly([
                    { x: a.x + nx * w * 0.4, y: a.y + ny * w * 0.4 }, { x: bx + nx * w, y: by + ny * w }, { x: bx + nx * w * 2, y: by + ny * w * 2 },
                    b, { x: bx - nx * w * 2, y: by - ny * w * 2 }, { x: bx - nx * w, y: by - ny * w }, { x: a.x - nx * w * 0.4, y: a.y - ny * w * 0.4 },
                ], true), d.color);
            },
        },
        arrow: { n: 2, draw: (P, d) => { S(seg(P[0], P[1])); F(arrowHead(P[0], P[1], 12), d.color); } },
        arrow_mark_up: { n: 1, draw: (P) => arrowGlyph(P[0], 'up', '#089981') },
        arrow_mark_down: { n: 1, draw: (P) => arrowGlyph(P[0], 'down', '#F23645') },
        arrow_mark_left: { n: 1, draw: (P, d) => arrowGlyph(P[0], 'left', d.color) },
        arrow_mark_right: { n: 1, draw: (P, d) => arrowGlyph(P[0], 'right', d.color) },

        // Shapes
        rectangle: {
            n: 2,
            draw: (P, d) => {
                const r = new Path2D();
                r.rect(Math.min(P[0].x, P[1].x), Math.min(P[0].y, P[1].y), Math.abs(P[1].x - P[0].x), Math.abs(P[1].y - P[0].y));
                F(r, rgba(d.color, 0.15));
                S(r);
            },
        },
        rotated_rectangle: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return S(seg(P[0], P[1]));
                const dx = P[1].x - P[0].x, dy = P[1].y - P[0].y, len = Math.hypot(dx, dy) || 1;
                const nx = -dy / len, ny = dx / len, h = (P[2].x - P[0].x) * nx + (P[2].y - P[0].y) * ny;
                const pts = [P[0], P[1], { x: P[1].x + nx * h, y: P[1].y + ny * h }, { x: P[0].x + nx * h, y: P[0].y + ny * h }];
                F(poly(pts, true), rgba(d.color, 0.15));
                S(poly(pts, true));
            },
        },
        path: { n: -1, draw: (P, d) => { S(poly(P)); if (P.length > 1) F(arrowHead(P[P.length - 2], P[P.length - 1], 10), d.color); } },
        circle: {
            n: 2,
            draw: (P, d) => {
                const c = new Path2D();
                c.arc(P[0].x, P[0].y, Math.hypot(P[1].x - P[0].x, P[1].y - P[0].y), 0, Math.PI * 2);
                F(c, rgba(d.color, 0.15));
                S(c);
            },
        },
        ellipse: {
            n: 2,
            draw: (P, d) => {
                const e = new Path2D();
                e.ellipse((P[0].x + P[1].x) / 2, (P[0].y + P[1].y) / 2, Math.abs(P[1].x - P[0].x) / 2 || 1, Math.abs(P[1].y - P[0].y) / 2 || 1, 0, 0, Math.PI * 2);
                F(e, rgba(d.color, 0.15));
                S(e);
            },
        },
        polyline: { n: -1, draw: (P, d) => { if (P.length > 2) F(poly(P, true), rgba(d.color, 0.15)); S(poly(P, P.length > 2)); } },
        triangle: { n: 3, draw: (P, d) => { if (P.length === 3) F(poly(P, true), rgba(d.color, 0.15)); S(poly(P, P.length === 3)); } },
        arc: {
            n: 3,
            draw: (P, d) => {
                if (P.length < 3) return S(seg(P[0], P[1]));
                const p = new Path2D();
                p.moveTo(P[0].x, P[0].y);
                p.quadraticCurveTo(P[1].x, P[1].y, P[2].x, P[2].y);
                F(p, rgba(d.color, 0.15));
                S(p);
            },
        },
        curve: {
            n: 3,
            draw: (P) => {
                if (P.length < 3) return S(seg(P[0], P[1]));
                const p = new Path2D();
                p.moveTo(P[0].x, P[0].y);
                p.quadraticCurveTo(P[1].x, P[1].y, P[2].x, P[2].y);
                S(p);
            },
        },
        double_curve: {
            n: 4,
            draw: (P) => {
                if (P.length < 4) return S(poly(P));
                const p = new Path2D();
                p.moveTo(P[0].x, P[0].y);
                p.bezierCurveTo(P[1].x, P[1].y, P[2].x, P[2].y, P[3].x, P[3].y);
                S(p);
            },
        },

        // Prediction and measurement
        long_position: { n: 3, draw: (P, d) => position(P, d, true) },
        short_position: { n: 3, draw: (P, d) => position(P, d, false) },
        forecast: {
            n: 2,
            draw: (P, d) => {
                dashed(() => S(seg(P[0], P[1])));
                F(arrowHead(P[0], P[1], 10), d.color);
                const dp = priceAt(d, 1) - priceAt(d, 0);
                text((dp >= 0 ? '+' : '') + fmt(dp) + ' (' + ((dp / priceAt(d, 0)) * 100).toFixed(2) + '%)', P[1].x + 6, P[1].y,
                    { base: 'middle', bg: dp >= 0 ? '#089981' : '#F23645', size: 11 });
            },
        },
        price_range: { n: 2, draw: (P, d) => measure(P, d, true, false) },
        date_range: { n: 2, draw: (P, d) => measure(P, d, false, true) },
        date_price_range: { n: 2, draw: (P, d) => measure(P, d, true, true) },

        // Text and notes (text comes from a dialog after placing)
        text: { n: 1, text: true, draw: (P, d) => text(d.text || 'Text', P[0].x, P[0].y, { color: d.color, size: 14, base: 'middle' }) },
        anchored_text: { n: 1, text: true, anchored: true, draw: (P, d) => text(d.text || 'Text', P[0].x, P[0].y, { color: d.color, size: 14, base: 'middle' }) },
        note: {
            n: 1, text: true,
            draw: (P, d) => {
                const c = new Path2D();
                c.arc(P[0].x, P[0].y, 9, 0, Math.PI * 2);
                F(c, d.color);
                text('✎', P[0].x, P[0].y, { align: 'center', base: 'middle', size: 11 });
                text(d.text || 'Note', P[0].x + 14, P[0].y, { base: 'middle', bg: '#1E222D', border: d.color, size: 12 });
            },
        },
        price_note: {
            n: 2, text: true,
            draw: (P, d) => {
                S(seg(P[0], P[1]));
                text(fmt(priceAt(d, 0)), P[0].x, P[0].y + 6, { align: 'center', bg: d.color, size: 11 });
                text(d.text || 'Price note', P[1].x, P[1].y - 4, { align: 'center', base: 'bottom', bg: '#1E222D', border: d.color, size: 12 });
            },
        },
        pin: {
            n: 1, text: true,
            draw: (P, d) => {
                const p = new Path2D();
                p.arc(P[0].x, P[0].y - 16, 8, 0, Math.PI * 2);
                F(p, d.color);
                F(poly([{ x: P[0].x - 5, y: P[0].y - 11 }, { x: P[0].x + 5, y: P[0].y - 11 }, P[0]], true), d.color);
                if (d.text) text(d.text, P[0].x, P[0].y - 28, { align: 'center', base: 'bottom', bg: '#1E222D', border: d.color, size: 12 });
            },
        },
        table: {
            n: 1, text: true,
            draw: (P, d) => {
                const rows = (d.text || 'A|B\nC|D').split('\n').map((r) => r.split('|'));
                const cols = Math.max.apply(null, rows.map((r) => r.length));
                ctx.font = '12px sans-serif';
                const cw = Math.max(50, Math.max.apply(null, rows.flat().map((c) => ctx.measureText(c).width)) + 12), ch = 20;
                rows.forEach((r, i) => {
                    for (let j = 0; j < cols; j++) {
                        const cell = new Path2D();
                        cell.rect(P[0].x + j * cw, P[0].y + i * ch, cw, ch);
                        F(cell, i === 0 ? rgba(d.color, 0.5) : '#1E222D');
                        S(cell);
                        text(r[j] || '', P[0].x + j * cw + 6, P[0].y + i * ch + 4, { size: 12 });
                    }
                });
            },
        },
        callout: {
            n: 2, text: true,
            draw: (P, d) => {
                const b = text(d.text || 'Callout', P[1].x, P[1].y, { align: 'center', base: 'middle', bg: rgba(d.color, 0.9), size: 12 });
                F(poly([{ x: b.x + b.w / 2 - 6, y: b.y + b.h / 2 }, { x: b.x + b.w / 2 + 6, y: b.y + b.h / 2 }, P[0]], true), rgba(d.color, 0.9));
            },
        },
        comment: { n: 1, text: true, draw: (P, d) => bubble(P[0], d.text || 'Comment', d, true) },
        price_label: { n: 1, draw: (P, d) => bubble(P[0], fmt(priceAt(d, 0)), d, true) },
        signpost: {
            n: 1, text: true,
            draw: (P, d) => {
                S(seg(P[0], { x: P[0].x, y: P[0].y - 40 }));
                text(d.text || 'Signpost', P[0].x, P[0].y - 40, { align: 'center', base: 'bottom', bg: rgba(d.color, 0.9), size: 12 });
            },
        },
        flag_mark: {
            n: 1,
            draw: (P, d) => {
                S(seg(P[0], { x: P[0].x, y: P[0].y - 26 }));
                F(poly([{ x: P[0].x, y: P[0].y - 26 }, { x: P[0].x + 18, y: P[0].y - 20 }, { x: P[0].x, y: P[0].y - 13 }], true), d.color);
            },
        },
        emoji: { n: 1, text: true, draw: (P, d) => text(d.text || '🙂', P[0].x, P[0].y, { align: 'center', base: 'middle', size: 26 }) },
    };

    function position(P, d, long) {
        if (P.length < 2) return;
        const entry = priceAt(d, 0), target = priceAt(d, 1), stop = P.length > 2 ? priceAt(d, 2) : entry - (target - entry) / 2;
        const x0 = P[0].x, x1 = Math.max(P[1].x, P[0].x + 60);
        const ye = P[0].y, yt = series.priceToCoordinate(target), ys = series.priceToCoordinate(stop);
        const box = (y0, y1, c) => {
            const r = new Path2D();
            r.rect(x0, Math.min(y0, y1), x1 - x0, Math.abs(y1 - y0));
            F(r, rgba(c, 0.25));
        };
        box(ye, yt, long ? '#089981' : '#F23645');
        box(ye, ys, long ? '#F23645' : '#089981');
        // Like the broker chart: only the two zones show until the drawing is tapped; then the
        // entry line, the target/stop labels and the result label appear with its handles.
        if (d.id !== selectedId) return;
        ctx.strokeStyle = '#787B86';
        S(hline(ye, x0, x1));
        ctx.strokeStyle = d.color;
        const rr = Math.abs((target - entry) / ((entry - stop) || 1));
        const pct = (v) => (((v - entry) / entry) * 100).toFixed(2) + '%';
        const dist = (v) => fmt(Math.abs(v - entry)) + ' (' + pct(v).replace('-', '') + ')';
        // Result at the latest price, as if the trade were closed now.
        const last = bars.length ? bars[bars.length - 1].close : entry;
        const pnl = (last - entry) * (long ? 1 : -1);
        const mid = (x0 + x1) / 2;
        text('Target: ' + dist(target), mid, yt, { align: 'center', base: long ? 'bottom' : 'top', bg: '#089981', size: 11 });
        text('Stop: ' + dist(stop), mid, ys, { align: 'center', base: long ? 'top' : 'bottom', bg: '#F23645', size: 11 });
        text('Closed P&L: ' + (pnl >= 0 ? '' : '-') + fmt(Math.abs(pnl)) + ', Risk/Reward Ratio: ' + rr.toFixed(2), mid, ye,
            { align: 'center', base: 'middle', bg: pnl >= 0 ? '#089981' : '#F23645', size: 11 });
    }

    function measure(P, d, price, date) {
        const up = priceAt(d, 1) >= priceAt(d, 0);
        const c = up ? '#2962FF' : '#F23645';
        const r = new Path2D();
        const x0 = date ? P[0].x : P[0].x - 40, x1 = date ? P[1].x : P[0].x + 40;
        const y0 = price ? P[0].y : P[0].y - 30, y1 = price ? P[1].y : P[0].y + 30;
        r.rect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
        F(r, rgba(c, 0.2));
        ctx.strokeStyle = c;
        if (price) F(arrowHead({ x: (x0 + x1) / 2, y: y0 }, { x: (x0 + x1) / 2, y: y1 }, 9), c);
        if (price) S(seg({ x: (x0 + x1) / 2, y: y0 }, { x: (x0 + x1) / 2, y: y1 }));
        if (date) S(seg({ x: x0, y: (y0 + y1) / 2 }, { x: x1, y: (y0 + y1) / 2 }));
        if (date) F(arrowHead({ x: x0, y: (y0 + y1) / 2 }, { x: x1, y: (y0 + y1) / 2 }, 9), c);
        const parts = [];
        if (price) {
            const dp = priceAt(d, 1) - priceAt(d, 0);
            parts.push(fmt(dp) + ' (' + ((dp / priceAt(d, 0)) * 100).toFixed(2) + '%)');
        }
        if (date) {
            const nb = Math.round(timeToLogical(d.points[1].time) - timeToLogical(d.points[0].time));
            const secs = Math.abs(d.points[1].time - d.points[0].time), hrs = Math.floor(secs / 3600), mins = Math.round((secs % 3600) / 60);
            parts.push(nb + ' bars, ' + (hrs ? hrs + 'h ' : '') + mins + 'm');
        }
        text(parts.join('\n'), (x0 + x1) / 2, Math.max(y0, y1) + 6, { align: 'center', bg: c, size: 11 });
        ctx.strokeStyle = d.color;
    }

    // ---- rendering ------------------------------------------------------------------------------

    function resize() {
        const dpr = window.devicePixelRatio || 1;
        const w = window.innerWidth, h = window.innerHeight;
        if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
            canvas.width = Math.round(w * dpr);
            canvas.height = Math.round(h * dpr);
            css(canvas, { width: w + 'px', height: h + 'px' });
        }
        const pn = pane();
        css(capture, { left: pn.x0 + 'px', width: pn.w + 'px', height: pn.h + 'px' });
        css(handleLayer, { left: pn.x0 + 'px' });
        return dpr;
    }

    function applyStyle(d) {
        ctx.strokeStyle = d.color;
        ctx.fillStyle = d.color;
        ctx.lineWidth = d.width;
        ctx.setLineDash(d.style === 1 ? [6, 4] : d.style === 2 ? [2, 3] : []);
        ctx.lineJoin = 'round';
    }

    function renderDrawing(d, pts) {
        const def = TOOLS[d.type];
        if (!def || !pts.length || pts.some((p) => p == null)) return;
        rec = { strokes: [], fills: [] };
        ctx.save();
        applyStyle(d);
        try {
            if (pts.length === 1 && def.n > 1) {
                const c = new Path2D();
                c.arc(pts[0].x, pts[0].y, 3, 0, Math.PI * 2);
                F(c, d.color);
            } else {
                def.draw(pts, d);
            }
        } catch (e) {
            console.log('ERR draw ' + d.type + ' ' + e);
        }
        ctx.restore();
        d._hit = rec;
        rec = null;
    }

    function axisLabels(d, pts, pn) {
        if (!TOOLS[d.type] || (!TOOLS[d.type].axis && d.id !== selectedId)) return;
        ctx.save();
        pts.forEach((p, i) => {
            if (!p || d.points[i].fx != null) return;
            text(fmt(d.points[i].price), pn.ax, p.y, { base: 'middle', bg: d.color, size: 11 });
        });
        ctx.restore();
    }

    function frame() {
        const dpr = resize();
        const pn = pane();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.setTransform(dpr, 0, 0, dpr, pn.x0 * dpr, 0);
        if (bars.length && series) {
            ctx.save();
            ctx.beginPath();
            ctx.rect(0, 0, pn.w, pn.h);
            ctx.clip();
            if (visible) drawings.forEach((d) => { if (!d.hidden) renderDrawing(d, d.points.map(toXY)); });
            drawPositions(pn);
            if (order) drawOrder(pn);
            if (placing) drawPlacing(pn);
            ctx.restore();
            if (visible) drawings.forEach((d) => { if (!d.hidden) axisLabels(d, d.points.map(toXY), pn); });
            if (placing) cursorLabels(pn);
            positionsOverlay(pn);
            if (order) orderAxis(pn);
        }
        layoutHandles();
        requestAnimationFrame(frame);
    }

    function drawPlacing(pn) {
        const d = Object.assign(styleFor(placing.type), { type: placing.type, points: placing.points.slice(), text: '' });
        const pts = d.points.map(toXY);
        if (TOOLS[placing.type].n !== 0) {
            // Preview includes the cursor as the next point, so tools reading prices see it too.
            const c = fromXY(placing.cursor.x, placing.cursor.y);
            if (c) d.points.push(c);
            pts.push(placing.cursor);
        }
        if (TOOLS[placing.type].n === 0) pts.splice(0, pts.length, ...placing.free);
        if (pts.length) renderDrawing(d, pts);
        // crosshair cursor
        if (TOOLS[placing.type].n !== 0) {
            ctx.save();
            ctx.strokeStyle = '#9598A1';
            ctx.lineWidth = 1;
            ctx.setLineDash([4, 4]);
            ctx.stroke(hline(placing.cursor.y, 0, pn.w));
            ctx.stroke(seg({ x: placing.cursor.x, y: 0 }, { x: placing.cursor.x, y: pn.h }));
            ctx.setLineDash([]);
            const c = new Path2D();
            c.arc(placing.cursor.x, placing.cursor.y, 6, 0, Math.PI * 2);
            ctx.strokeStyle = BLUE;
            ctx.lineWidth = 2;
            ctx.stroke(c);
            ctx.restore();
        }
    }

    function cursorLabels(pn) {
        if (TOOLS[placing.type].n === 0) return;
        const p = fromXY(placing.cursor.x, placing.cursor.y);
        if (!p) return;
        text(fmt(p.price), pn.ax, placing.cursor.y, { base: 'middle', bg: BLUE, size: 11 });
        const dt = new Date(p.time * 1000);
        const label = dt.toUTCString().slice(0, 11) + " '" + String(dt.getUTCFullYear()).slice(2) + '  ' + dt.toISOString().slice(11, 16);
        text(label, placing.cursor.x, pn.h + 4, { align: 'center', bg: BLUE, size: 11 });
    }

    // ---- hit testing & selection -------------------------------------------------------------

    function hitTest(x, y) {
        const dpr = window.devicePixelRatio || 1;
        ctx.save();
        ctx.setTransform(dpr, 0, 0, dpr, pane().x0 * dpr, 0);
        ctx.lineWidth = 18;
        let found = null;
        for (let i = drawings.length - 1; i >= 0 && !found; i--) {
            const h = drawings[i]._hit;
            if (!h || drawings[i].hidden) continue;
            if (h.strokes.some((p) => ctx.isPointInStroke(p, x * dpr, y * dpr)) || h.fills.some((p) => ctx.isPointInPath(p, x * dpr, y * dpr))) {
                found = drawings[i];
            }
        }
        ctx.restore();
        return found;
    }

    function select(d) {
        selectedId = d ? d.id : null;
        rebuildHandles();
        if (android()) {
            android().onSelect(d ? JSON.stringify({
                id: d.id, type: d.type, color: d.color, width: d.width, style: d.style, locked: !!d.locked,
                hasText: !!(TOOLS[d.type] && TOOLS[d.type].text), text: d.text || '',
            }) : '');
        }
    }

    chart.subscribeClick((param) => {
        if (placing || !param.point) return;
        const d = visible ? hitTest(param.point.x, param.point.y) : null;
        select(d);
    });

    // ---- handles (anchors) -------------------------------------------------------------------

    let handleEls = [];

    function rebuildHandles() {
        handleEls.forEach((h) => h.remove());
        handleEls = [];
        const d = drawings.find((x) => x.id === selectedId);
        if (!d || d.locked || lockedAll) return;
        d.points.forEach((pt, i) => {
            if (TOOLS[d.type] && TOOLS[d.type].n === 0) return;
            const el = document.createElement('div');
            css(el, {
                position: 'fixed', width: '22px', height: '22px', marginLeft: '-11px', marginTop: '-11px', borderRadius: '50%',
                border: '2px solid ' + BLUE, background: 'rgba(21,27,32,0.6)', boxSizing: 'border-box', touchAction: 'none',
            });
            dragAnchor(el, d, i);
            handleLayer.appendChild(el);
            handleEls.push(el);
        });
    }

    function layoutHandles() {
        const d = drawings.find((x) => x.id === selectedId);
        if (!d) return;
        d.points.forEach((pt, i) => {
            const el = handleEls[i];
            const p = toXY(pt);
            if (!el || !p) return;
            el.style.left = p.x + 'px';
            el.style.top = p.y + 'px';
        });
    }

    function dragAnchor(el, d, i) {
        let last = null;
        el.addEventListener('touchstart', (e) => {
            e.stopPropagation();
            last = { x: e.touches[0].clientX, y: e.touches[0].clientY };
        }, { passive: true });
        el.addEventListener('touchmove', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const t = e.touches[0];
            const cur = toXY(d.points[i]);
            if (!cur || !last) return;
            const s = snap(cur.x + t.clientX - last.x, cur.y + t.clientY - last.y);
            const pn = pane();
            const np = d.points[i].fx != null ? { fx: s.x / pn.w, fy: s.y / pn.h } : fromXY(s.x, s.y);
            if (np) d.points[i] = np;
            last = { x: t.clientX, y: t.clientY };
        }, { passive: false });
        el.addEventListener('touchend', () => {
            last = null;
            notify();
        });
    }

    // ---- placing points ----------------------------------------------------------------------

    function styleFor(type) {
        const base = { color: type === 'highlighter' ? '#FFEB3B' : BLUE, width: 2, style: 0 };
        return Object.assign(base, defaults[type] || {});
    }

    function showHint(msg) {
        if (hintDismissed) return;
        hintText.textContent = msg;
        hint.style.display = 'block';
    }

    hintClose.addEventListener('click', () => {
        hintDismissed = true;
        hint.style.display = 'none';
    });

    function startPlacing(type) {
        const pn = pane();
        placing = { type: type, points: [], cursor: { x: pn.w * 0.6, y: pn.h * 0.5 }, free: [] };
        capture.style.display = 'block';
        select(null);
        showHint(TOOLS[type].n === 0 ? 'Draw with your finger' : 'Move the point to position the anchor then tap to place');
    }

    function stopPlacing() {
        placing = null;
        capture.style.display = 'none';
        hint.style.display = 'none';
    }

    let touch = null;
    capture.addEventListener('touchstart', (e) => {
        e.preventDefault();
        const t = e.touches[0];
        touch = { x: t.clientX, y: t.clientY, sx: t.clientX, sy: t.clientY, at: Date.now() };
        if (placing && TOOLS[placing.type].n === 0) placing.free = [{ x: t.clientX - pane().x0, y: t.clientY }];
    }, { passive: false });
    capture.addEventListener('touchmove', (e) => {
        e.preventDefault();
        if (!placing || !touch) return;
        const t = e.touches[0];
        if (TOOLS[placing.type].n === 0) {
            placing.free.push({ x: t.clientX - pane().x0, y: t.clientY });
        } else {
            const pn = pane();
            const nx = Math.max(0, Math.min(pn.w, placing.cursor.x + t.clientX - touch.x));
            const ny = Math.max(0, Math.min(pn.h, placing.cursor.y + t.clientY - touch.y));
            placing.cursor = snap(nx, ny);
        }
        touch.x = t.clientX;
        touch.y = t.clientY;
    }, { passive: false });
    capture.addEventListener('touchend', () => {
        if (!placing || !touch) return;
        const def = TOOLS[placing.type];
        if (def.n === 0) {
            if (placing.free.length > 1) {
                const pts = placing.free.map((p) => fromXY(p.x, p.y)).filter(Boolean);
                commit({ type: placing.type, points: pts });
            }
            placing.free = [];
            touch = null;
            return;
        }
        const moved = Math.hypot(touch.x - touch.sx, touch.y - touch.sy);
        const isTap = moved < 8 && Date.now() - touch.at < 400;
        touch = null;
        if (!isTap) return;
        const c = placing.cursor;
        const pn = pane();
        // A second tap on the last point finishes open-ended tools (path, polyline).
        if (def.n === -1 && placing.points.length >= 2) {
            const lastP = toXY(placing.points[placing.points.length - 1]);
            if (lastP && Math.hypot(lastP.x - c.x, lastP.y - c.y) < 10) return finishOpenEnded();
        }
        const p = def.anchored ? { fx: c.x / pn.w, fy: c.y / pn.h } : fromXY(c.x, c.y);
        if (!p) return;
        placing.points.push(p);
        if (def.n > 0 && placing.points.length >= def.n) commit({ type: placing.type, points: placing.points });
    });

    function finishOpenEnded() {
        if (placing && placing.points.length >= 2) commit({ type: placing.type, points: placing.points });
    }

    function commit(partial) {
        const d = Object.assign(styleFor(partial.type), partial, { id: nextId++ });
        d.points = d.points.slice();
        drawings.push(d);
        const type = d.type;
        const keepTool = stay;
        if (keepTool) startPlacing(type); else stopPlacing();
        if (TOOLS[type].text && android()) android().requestText(type, d.id);
        notify();
        if (!keepTool) {
            tool = 'cursor';
            if (android()) android().onToolReset();
            select(d);
        }
    }

    function notify() {
        if (!android()) return;
        android().onDrawings(JSON.stringify(drawings.map((d) => {
            const c = Object.assign({}, d);
            delete c._hit;
            return c;
        })));
    }

    // ---- order preview (TP / SL tags on the chart) -------------------------------------------

    const tagBox = document.createElement('div');
    css(tagBox, { position: 'fixed', display: 'none', zIndex: '6', height: '22px', font: '12px sans-serif', touchAction: 'none' });
    handleLayer.appendChild(tagBox);
    const mkTag = (label, bg, color, border) => {
        const t = document.createElement('span');
        t.textContent = label;
        css(t, {
            display: 'inline-block', height: '20px', lineHeight: '20px', padding: '0 7px', marginRight: '3px', borderRadius: '3px',
            background: bg, color: color, border: '1px solid ' + border, verticalAlign: 'top',
        });
        tagBox.appendChild(t);
        return t;
    };
    const tpTag = mkTag('TP', '#0F0F0F', '#2A9D78', '#2A9D78');
    const slTag = mkTag('SL', '#0F0F0F', '#E2972D', '#E2972D');
    tpTag.style.borderStyle = 'dotted';
    slTag.style.borderStyle = 'dotted';
    Object.assign(slTag.style, { textDecoration: 'underline dashed', textUnderlineOffset: '3px' });
    const lotTag = mkTag('0.01', BLUE, '#fff', BLUE);
    const xTag = mkTag('✕', '#0F0F0F', BLUE, BLUE);
    // Lot and close tags join into one pill, like the broker chart.
    Object.assign(lotTag.style, { marginRight: '0', borderRadius: '3px 0 0 3px' });
    Object.assign(xTag.style, { borderRadius: '0 3px 3px 0' });
    slTag.style.marginRight = '6px';
    let dragging = null; // 'tp' | 'sl'

    function levelDrag(el, which) {
        el.addEventListener('touchstart', (e) => {
            e.stopPropagation();
            e.preventDefault();
            dragging = which;
            if (order[which] == null) order[which] = order.price;
        }, { passive: false });
        el.addEventListener('touchmove', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const y = e.touches[0].clientY;
            const pr = series.coordinateToPrice(y);
            if (pr != null) order[which] = pr;
        }, { passive: false });
        el.addEventListener('touchend', () => {
            dragging = null;
            if (android()) android().onOrderLevels(order.tp == null ? NaN : order.tp, order.sl == null ? NaN : order.sl);
        });
    }
    levelDrag(tpTag, 'tp');
    levelDrag(slTag, 'sl');
    xTag.addEventListener('touchend', (e) => {
        e.stopPropagation();
        if (android()) android().onOrderCancel();
    });

    function drawOrder(pn) {
        const y = series.priceToCoordinate(order.price);
        if (y == null) return;
        const c = order.side === 'buy' ? BLUE : '#F23645';
        ctx.save();
        ctx.lineWidth = 1;
        ctx.strokeStyle = c;
        ctx.stroke(hline(y, 0, pn.w));
        [['tp', TP_C], ['sl', SL_C]].forEach(([k, col]) => {
            if (order[k] == null) return;
            const yy = series.priceToCoordinate(order[k]);
            if (yy == null) return;
            ctx.strokeStyle = col;
            ctx.stroke(hline(yy, 0, pn.w));
            const pnl = (order[k] - order.price) * (order.side === 'buy' ? 1 : -1) * order.value;
            const label = (order.lotsLabel || '') + '   ' + (pnl >= 0 ? '+ ' : '- ') + Math.abs(pnl).toFixed(2) + ' USD';
            text(label, Math.max(4, pn.w - 190), yy, { base: 'middle', bg: BOX_BG, border: col, color: col, size: 12 });
        });
        ctx.restore();
    }

    function orderAxis(pn) {
        const y = series.priceToCoordinate(order.price);
        if (y != null) text(fmt(order.price), pn.ax, y, { base: 'middle', bg: order.side === 'buy' ? BLUE : '#F23645', size: 11 });
        ['tp', 'sl'].forEach((k) => {
            if (order[k] == null) return;
            const yy = series.priceToCoordinate(order[k]);
            if (yy != null) text(fmt(order[k]), pn.ax, yy, { base: 'middle', bg: BOX_BG, border: k === 'tp' ? TP_C : SL_C, color: k === 'tp' ? TP_C : SL_C, size: 11 });
        });
        if (y != null) {
            tagBox.style.display = 'block';
            tagBox.style.top = (y - 11) + 'px';
            tagBox.style.left = Math.max(4, pn.w - 150) + 'px';
        }
    }

    // ---- open positions: TP/SL tags on the entry line, draggable levels -------------------------

    const TP_C = '#2A9D78';
    const SL_C = '#E2972D';
    let positions = []; // [{id, price, side, lotsLabel, value, pnl, tp, sl}]
    const posEls = {};
    const edits = {}; // id -> {tp?, sl?} while the user is moving levels

    function span(textValue, s) {
        const e = document.createElement('span');
        e.textContent = textValue;
        css(e, Object.assign({ display: 'inline-block', height: '20px', lineHeight: '20px', padding: '0 7px', verticalAlign: 'top', boxSizing: 'border-box' }, s));
        return e;
    }

    function row() {
        const e = document.createElement('div');
        css(e, { position: 'fixed', display: 'none', zIndex: '6', height: '22px', font: '12px sans-serif', whiteSpace: 'nowrap', touchAction: 'none' });
        handleLayer.appendChild(e);
        return e;
    }

    function level(p, k) {
        const e = edits[p.id];
        return e && k in e ? e[k] : p[k];
    }

    function pnlAt(p, v) {
        const dir = p.side === 'buy' ? 1 : -1;
        const x = (v - p.price) * dir * p.value;
        return (x >= 0 ? '+ ' : '- ') + Math.abs(x).toFixed(2) + ' USD';
    }

    function sendEdit(id) {
        const p = positions.find((q) => q.id === id);
        if (!p || !android()) return;
        const tp = level(p, 'tp'), sl = level(p, 'sl');
        android().onPositionEdit(String(id), tp == null ? NaN : tp, sl == null ? NaN : sl);
    }

    /** Dragging [el] vertically moves the TP or SL of position [id]. */
    function dragLevel(el, id, k) {
        el.addEventListener('touchstart', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const p = positions.find((q) => q.id === id);
            if (!p) return;
            edits[id] = edits[id] || {};
            if (level(p, k) == null) edits[id][k] = p.price;
        }, { passive: false });
        el.addEventListener('touchmove', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const pr = series.coordinateToPrice(e.touches[0].clientY);
            if (pr != null && edits[id]) edits[id][k] = pr;
        }, { passive: false });
        el.addEventListener('touchend', (e) => {
            e.stopPropagation();
            sendEdit(id);
        });
    }

    function tapOnly(el, fn) {
        el.addEventListener('touchstart', (e) => e.stopPropagation(), { passive: true });
        el.addEventListener('touchend', (e) => {
            e.stopPropagation();
            e.preventDefault();
            fn();
        });
    }

    function ensure(p) {
        if (posEls[p.id]) return posEls[p.id];
        const blue = p.side === 'buy' ? BLUE_POS : RED_POS;
        const main = row();
        // Dotted borders, and a dashed underline under SL, as on the broker chart.
        const tp = span('TP', { background: BOX_BG, color: TP_C, border: '1px dotted ' + TP_C, marginRight: '3px', borderRadius: '3px' });
        const sl = span('SL', { background: BOX_BG, color: SL_C, border: '1px dotted ' + SL_C, marginRight: '6px', borderRadius: '3px', textDecoration: 'underline dashed', textUnderlineOffset: '3px' });
        const lot = span(p.lotsLabel, { background: blue, color: '#fff', border: '1px solid ' + blue, borderRadius: '3px 0 0 3px' });
        const pnl = span('', { background: BOX_BG, color: '#E9EEF4', borderTop: '1px solid ' + blue, borderBottom: '1px solid ' + blue, borderLeft: '1px solid ' + blue });
        const x = span('✕', { background: BOX_BG, color: blue, border: '1px solid ' + blue, padding: '0 6px', borderRadius: '0 3px 3px 0' });
        [tp, sl, lot, pnl, x].forEach((s) => main.appendChild(s));
        dragLevel(tp, p.id, 'tp');
        dragLevel(sl, p.id, 'sl');
        tapOnly(lot, () => android() && android().onPositionTap(String(p.id)));
        tapOnly(pnl, () => android() && android().onPositionTap(String(p.id)));
        tapOnly(x, () => android() && android().onPositionClose(String(p.id)));

        const mkLevel = (k, c) => {
            const r = row();
            const l = span(p.lotsLabel, { background: BOX_BG, color: c, border: '1px solid ' + c, borderRadius: '3px 0 0 3px' });
            const v = span('', { background: BOX_BG, color: c, borderTop: '1px solid ' + c, borderBottom: '1px solid ' + c });
            const cx = span('✕', { background: BOX_BG, color: c, border: '1px solid ' + c, padding: '0 6px', borderRadius: '0 3px 3px 0' });
            [l, v, cx].forEach((s) => r.appendChild(s));
            dragLevel(l, p.id, k);
            dragLevel(v, p.id, k);
            tapOnly(cx, () => {
                edits[p.id] = edits[p.id] || {};
                edits[p.id][k] = null;
                sendEdit(p.id);
            });
            return { r, v };
        };
        posEls[p.id] = { main, tp, sl, lot, pnl, tpL: mkLevel('tp', TP_C), slL: mkLevel('sl', SL_C) };
        return posEls[p.id];
    }

    function removeEls(id) {
        const e = posEls[id];
        if (!e) return;
        [e.main, e.tpL.r, e.slL.r].forEach((x) => x.remove());
        delete posEls[id];
        delete edits[id];
    }

    function place(el, y, pn) {
        if (y == null || y < 0 || y > pn.h) {
            el.style.display = 'none';
            return;
        }
        el.style.display = 'block';
        el.style.top = (y - 11) + 'px';
        // Boxes end well before the connector, like the broker chart.
        el.style.left = Math.max(4, pn.w - el.offsetWidth - 66) + 'px';
    }

    function drawPositions(pn) {
        const conn = pn.w - 32;
        positions.forEach((p) => {
            const y = series.priceToCoordinate(p.price);
            const c = p.side === 'buy' ? BLUE_POS : RED_POS;
            ctx.save();
            ctx.lineWidth = 1;
            ctx.setLineDash([]);
            if (y != null) {
                ctx.strokeStyle = c;
                ctx.stroke(hline(y, 0, pn.w));
            }
            let hasLevel = false;
            [['tp', TP_C], ['sl', SL_C]].forEach(([k, col]) => {
                const v = level(p, k);
                if (v == null) return;
                const yy = series.priceToCoordinate(v);
                if (yy == null) return;
                ctx.strokeStyle = col;
                ctx.stroke(hline(yy, 0, pn.w));
                if (y != null) {
                    // Connector from the entry to the level, ending in a hollow ring.
                    hasLevel = true;
                    ctx.strokeStyle = BLUE_POS;
                    ctx.stroke(seg({ x: conn, y: y }, { x: conn, y: yy }));
                    const d = new Path2D();
                    d.arc(conn, yy, 4, 0, Math.PI * 2);
                    ctx.fillStyle = BOX_BG;
                    ctx.fill(d);
                    ctx.lineWidth = 1.5;
                    ctx.stroke(d);
                    ctx.lineWidth = 1;
                }
            });
            if (hasLevel && y != null) {
                const d = new Path2D();
                d.arc(conn, y, 5, 0, Math.PI * 2);
                ctx.fillStyle = BLUE_POS;
                ctx.fill(d);
            }
            ctx.restore();
        });
    }

    function positionsOverlay(pn) {
        positions.forEach((p) => {
            const e = ensure(p);
            const y = series.priceToCoordinate(p.price);
            const tpV = level(p, 'tp'), slV = level(p, 'sl');
            e.tp.style.display = tpV == null ? 'inline-block' : 'none';
            e.sl.style.display = slV == null ? 'inline-block' : 'none';
            e.lot.textContent = p.lotsLabel;
            e.pnl.textContent = p.pnl;
            e.pnl.style.color = p.pnl.startsWith('-') ? '#F23645' : TP_C;
            place(e.main, y, pn);
            const c = p.side === 'buy' ? BLUE_POS : RED_POS;
            if (y != null) text(fmt(p.price), pn.ax, y, { base: 'middle', bg: c, size: 11 });
            [['tp', tpV, e.tpL, TP_C], ['sl', slV, e.slL, SL_C]].forEach(([k, v, box, col]) => {
                if (v == null) {
                    box.r.style.display = 'none';
                    return;
                }
                box.v.textContent = pnlAt(p, v);
                const yy = series.priceToCoordinate(v);
                place(box.r, yy, pn);
                if (yy != null) text(fmt(v), pn.ax, yy, { base: 'middle', bg: BOX_BG, border: col, color: col, size: 11 });
            });
        });
    }

    // A tap near a position's entry line opens its panel.
    chart.subscribeClick((param) => {
        if (placing || !param.point || !android()) return;
        let best = null;
        positions.forEach((p) => {
            const y = series.priceToCoordinate(p.price);
            const d = y == null ? 1e9 : Math.abs(y - param.point.y);
            if (d < 14 && (!best || d < best.d)) best = { id: p.id, d: d };
        });
        if (best) android().onPositionTap(String(best.id));
    });

    // ---- API for Kotlin ----------------------------------------------------------------------

    /** json: [{id, price, side, lotsLabel, value, pnl, tp, sl}] for the shown instrument. */
    window.setPositions = (json) => {
        const next = JSON.parse(json || '[]');
        const ids = new Set(next.map((p) => p.id));
        Object.keys(posEls).forEach((id) => { if (!ids.has(id)) removeEls(id); });
        positions = next;
    };

    /** Forgets unsaved TP/SL moves of a position (after Confirm or Cancel). */
    window.clearPositionEdit = (id) => {
        delete edits[String(id)];
    };

    window.setTool = (name) => {
        finishOpenEnded();
        tool = name;
        if (name === 'cursor' || !TOOLS[name]) stopPlacing();
        else startPlacing(name);
    };

    window.setDrawings = (json) => {
        try {
            drawings = JSON.parse(json) || [];
        } catch (e) {
            drawings = [];
        }
        // Drawings saved by the first version of the app used other type names.
        drawings = drawings.filter((d) => TOOLS[d.type] || { trend: 1, fib: 1, hline: 1, pattern: 1 }[d.type]).map((d) => {
            const map = { trend: 'trend_line', fib: 'fib_retracement', hline: 'horizontal_line', pattern: 'xabcd_pattern' };
            return Object.assign(styleFor(map[d.type] || d.type), d, { type: map[d.type] || d.type });
        });
        nextId = drawings.reduce((m, d) => Math.max(m, d.id), 0) + 1;
        select(null);
    };

    window.setDrawingText = (id, value) => {
        const d = drawings.find((x) => x.id === id);
        if (!d) return;
        d.text = value;
        notify();
        if (selectedId === id) select(d);
    };

    window.removeDrawing = (id) => {
        drawings = drawings.filter((d) => d.id !== id);
        if (selectedId === id) select(null);
        notify();
    };

    window.clearDrawings = () => {
        drawings = drawings.filter((d) => d.locked);
        select(null);
        notify();
    };

    window.styleDrawing = (id, patch) => {
        const d = drawings.find((x) => x.id === id);
        if (!d) return;
        Object.assign(d, patch);
        notify();
        select(d);
    };

    window.saveDrawingTemplate = (id) => {
        const d = drawings.find((x) => x.id === id);
        if (d) defaults[d.type] = { color: d.color, width: d.width, style: d.style };
    };

    window.cloneDrawing = (id) => {
        const d = drawings.find((x) => x.id === id);
        if (!d) return;
        const c = JSON.parse(JSON.stringify(Object.assign({}, d, { _hit: null })));
        c.id = nextId++;
        c.locked = false;
        c.points = c.points.map((p) => (p.fx != null ? { fx: p.fx + 0.03, fy: p.fy + 0.03 } : { time: p.time + step() * 3, price: p.price }));
        drawings.push(c);
        notify();
        select(c);
    };

    window.deselectDrawing = () => select(null);
    window.setDrawingsVisible = (v) => {
        visible = v;
        if (!v) select(null);
    };
    window.setLockedAll = (v) => {
        lockedAll = v;
        rebuildHandles();
    };
    window.setMagnet = (mode) => {
        magnet = mode;
        chart.applyOptions({ crosshair: { mode: mode ? LWC.CrosshairMode.MagnetOHLC : LWC.CrosshairMode.Normal } });
    };
    window.setStayInDrawing = (v) => {
        stay = v;
    };
    window.zoomIn = () => {
        const ts = chart.timeScale();
        ts.applyOptions({ barSpacing: Math.min(ts.options().barSpacing * 1.5, 60) });
    };

    /** Shows or hides the pending order line; json: {price, side, lots, value, tp, sl} or ''. */
    window.setOrderPreview = (json) => {
        if (!json) {
            order = null;
            tagBox.style.display = 'none';
            return;
        }
        const o = JSON.parse(json);
        // While a tag is being dragged the page owns TP/SL; otherwise Kotlin's values win.
        const keep = order && dragging ? { tp: order.tp, sl: order.sl } : {};
        order = Object.assign({ tp: null, sl: null }, o, keep);
        lotTag.textContent = o.lotsLabel || String(o.lots);
        const sideColor = o.side === 'buy' ? BLUE_POS : RED_POS;
        lotTag.style.background = sideColor;
        lotTag.style.borderColor = sideColor;
        xTag.style.color = sideColor;
        xTag.style.borderColor = sideColor;
    };

    requestAnimationFrame(frame);
})();
