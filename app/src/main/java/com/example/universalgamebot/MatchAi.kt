package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** ระดับความยากของคู่แข่ง: ยิ่งยากยิ่งวิ่งเร็ว คิดถี่ เล็งแม่น แย่งบ่อย */
enum class AiLevel(val speedMul: Float, val thinkS: Float, val aimErr: Float, val tackleEager: Float, val shootRangeDp: Float) {
    EASY(0.78f, 0.40f, 0.34f, 0.45f, 85f),
    NORMAL(0.90f, 0.25f, 0.18f, 0.70f, 105f),
    HARD(0.97f, 0.20f, 0.14f, 0.78f, 115f);

    companion object { fun parse(s: String?): AiLevel = values().firstOrNull { it.name.equals(s, true) } ?: EASY }
}

/**
 * ผู้เล่น AI ของหนึ่งทีม (ใช้ทั้งคู่แข่ง และเพื่อนร่วมทีมที่มนุษย์/บอทไม่ได้คุม) ไม่ผูก Android
 * คิดเป็นรอบ ๆ (thinkS) แล้วเดินตามแผนเดิมระหว่างรอบ = มีเวลาตอบสนองช้าลงตามระดับ
 * บทบาท: ถือบอล (ยิง/ส่ง/เลี้ยง) · เพื่อนถือบอล (ซัพพอร์ต) · คู่แข่งถือบอล (ตัวใกล้สุดเข้าแย่ง ที่เหลือยืนป้องกันฝั่งประตู) · บอลหลุด (ตัวใกล้สุดไล่ ที่เหลือซัพพอร์ต)
 * ผู้รักษาประตูเดินตามบอล/ทำนายจุดที่ลูกจะผ่านเส้น ถือบอลแล้วส่งให้เพื่อนภายใน 0.7 วินาที
 * AI รู้ความจริงของเกมได้ (มันคือเกมเอง) ต่างจากบอทผู้เล่นที่ต้องมองจากภาพ
 */
class TeamAi(private val team: Team, private val lv: AiLevel, private val rnd: Random) {
    private class Plan {
        var tx = 0f; var ty = 0f; var next = 0f
        var shoot = false; var pass = false; var tackle = false
        var sx = 0f; var sy = 0f; var jitter = 0f
        var passTo: Agent? = null
        var holdT = 0f
    }

    private val plans = Array(6) { Plan() }
    private var clock = 0f

    fun reset() { clock = 0f; for (pl in plans) { pl.next = 0f; pl.shoot = false; pl.pass = false; pl.tackle = false; pl.holdT = 0f } }

    fun fill(w: MatchWorld, inputs: Array<AgentInput>, skipId: Int, dt: Float) {
        clock += dt
        for (a in w.agents) {
            if (a.team != team || a.id == skipId) continue
            val pl = plans[a.id]
            if (clock >= pl.next) { think(w, a, pl); pl.next = clock + lv.thinkS * (0.7f + 0.6f * rnd.nextFloat()) }
            drive(w, a, pl, inputs[a.id], dt)
        }
    }

    private fun dist(a: Agent, b: Agent) = hypot(a.x - b.x, a.y - b.y)

    private fun think(w: MatchWorld, a: Agent, pl: Plan) {
        val p = w.p; val d = p.d
        val dir = team.attackDir
        val own = p.ownGoalX(team); val att = p.attackGoalX(team)
        val car = w.carrier
        pl.shoot = false; pl.pass = false; pl.tackle = false
        if (a.keeper) {
            val kx = p.keeperX(team)
            var ty = w.by
            if (w.bvx * (-dir) > 20f * d) { val t = (kx - w.bx) / w.bvx; if (t > 0f) ty = w.by + w.bvy * t }
            ty = p.h / 2f + (ty - p.h / 2f) * 0.8f
            pl.tx = kx; pl.ty = ty.coerceIn(p.mouthTop + p.keeperR, p.mouthBottom - p.keeperR)
            return
        }
        val mates = w.agents.filter { it.team == team && !it.keeper && it.id != a.id }
        val opps = w.agents.filter { it.team != team && !it.keeper }
        val oppKeeper = w.agents.first { it.team != team && it.keeper }
        val chaser = (mates + a).minByOrNull { hypot(it.x - w.bx, it.y - w.by) }
        when {
            car === a -> carrierThink(w, a, pl, mates, opps, oppKeeper, att, dir)
            car != null && car.team == team -> { // เพื่อนถือบอล: ขึ้นไปซัพพอร์ตข้างหน้าอีกฝั่งหนึ่งของสนาม
                pl.tx = (car.x + dir * 90f * d).coerceIn(p.leftLineX + 2f * p.playerR, p.rightLineX - 2f * p.playerR)
                pl.ty = if (car.y < p.h / 2f) p.h * 0.72f else p.h * 0.28f
            }
            car != null -> { // คู่แข่งถือบอล
                if (a === chaser) {
                    pl.tx = car.x; pl.ty = car.y
                    pl.tackle = dist(a, car) <= p.tackleReach * 1.05f && rnd.nextFloat() < lv.tackleEager
                } else {
                    pl.tx = (own + (w.bx - own) * 0.55f).coerceIn(p.leftLineX + p.playerR, p.rightLineX - p.playerR)
                    pl.ty = w.by * 0.6f + p.h / 2f * 0.4f
                }
            }
            else -> { // ลูกหลุด
                if (a === chaser) { pl.tx = w.bx + w.bvx * 0.25f; pl.ty = w.by + w.bvy * 0.25f }
                else { pl.tx = (w.bx - dir * 50f * d).coerceIn(p.leftLineX + p.playerR, p.rightLineX - p.playerR); pl.ty = if (w.by < p.h / 2f) p.h * 0.7f else p.h * 0.3f }
            }
        }
    }

    private fun carrierThink(w: MatchWorld, a: Agent, pl: Plan, mates: List<Agent>, opps: List<Agent>, oppKeeper: Agent, att: Float, dir: Float) {
        val p = w.p; val d = p.d
        val goalDist = abs(att - a.x)
        if (goalDist < lv.shootRangeDp * d && a.kickCd <= 0f && rnd.nextFloat() < 0.4f) {
            val half = p.mouthH / 2f; val cy = p.h / 2f
            val aimY = if (oppKeeper.y > cy) cy - half * 0.45f else cy + half * 0.45f
            val vx = att - a.x; val vy = aimY - a.y; val len = max(hypot(vx, vy), 0.001f)
            pl.shoot = true; pl.sx = vx / len; pl.sy = vy / len; pl.jitter = (rnd.nextFloat() * 2f - 1f) * lv.aimErr
            return
        }
        val nearOpp = opps.minByOrNull { dist(a, it) }
        val nd = if (nearOpp != null) dist(a, nearOpp) else Float.MAX_VALUE
        if (nd < 55f * d) {
            var best: Agent? = null; var bs = -Float.MAX_VALUE
            for (m in mates) {
                val lane = opps.none { distToSegment(it.x, it.y, a.x, a.y, m.x, m.y) < 20f * d }
                val open = opps.minOfOrNull { dist(m, it) } ?: Float.MAX_VALUE
                if (!lane || open < 50f * d) continue
                val score = (m.x - a.x) * dir + open * 0.2f
                if (score > bs) { bs = score; best = m }
            }
            if (best != null) { pl.pass = true; pl.passTo = best; return }
        }
        pl.tx = att - dir * 20f * d
        pl.ty = if (nearOpp != null && abs(nearOpp.x - a.x) < 120f * d) (a.y + (if (a.y >= nearOpp.y) 1f else -1f) * 60f * d).coerceIn(p.playerR, p.h - p.playerR)
        else a.y + (p.h / 2f - a.y) * 0.5f
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
        val t = if (l2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun drive(w: MatchWorld, a: Agent, pl: Plan, inp: AgentInput, dt: Float) {
        val d = w.p.d
        if (a.keeper) {
            if (w.carrier === a) {
                pl.holdT += dt
                if (pl.holdT >= 0.7f) {
                    val opps = w.agents.filter { it.team != team && !it.keeper }
                    val mate = w.agents.filter { it.team == team && !it.keeper }.maxByOrNull { m -> opps.minOfOrNull { hypot(m.x - it.x, m.y - it.y) } ?: 0f }
                    if (mate != null) {
                        val vx = mate.x - a.x; val vy = mate.y - a.y; val len = max(hypot(vx, vy), 0.001f)
                        inp.mx = vx / len; inp.my = vy / len; inp.pass = true
                    }
                    pl.holdT = 0f
                }
            } else {
                pl.holdT = 0f
                inp.my = ((pl.ty - a.y) / (6f * d)).coerceIn(-1f, 1f)
            }
            return
        }
        if (pl.shoot) { inp.mx = pl.sx; inp.my = pl.sy; inp.shoot = true; inp.aimJitter = pl.jitter; pl.shoot = false; return }
        val to = pl.passTo
        if (pl.pass && to != null) {
            val vx = to.x - a.x; val vy = to.y - a.y; val len = max(hypot(vx, vy), 0.001f)
            inp.mx = vx / len; inp.my = vy / len; inp.pass = true; pl.pass = false; return
        }
        if (pl.tackle) { inp.tackle = true; pl.tackle = false }
        val dx = pl.tx - a.x; val dy = pl.ty - a.y; val dd = hypot(dx, dy)
        if (dd > 4f * d) { val mag = min(1f, max(0.25f, dd / (25f * d))); inp.mx = dx / dd * mag; inp.my = dy / dd * mag }
    }
}
