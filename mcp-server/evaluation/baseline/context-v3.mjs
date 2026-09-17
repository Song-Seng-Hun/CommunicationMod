// Frozen pre-optimization context implementation; only its utility import path is relocated.
import { createHash } from 'node:crypto';
import { obj, list } from '../../dist/view.js';
const pick = (o, keys) => Object.fromEntries(keys.filter(k => k in o).map(k => [k, o[k]]));
const present = (v) => v != null && (Array.isArray(v) ? v.length > 0 : typeof v === 'object' ? Object.keys(obj(v)).length > 0 : true);
const child = (ref, key) => ref + '/' + encodeURIComponent(key);
const size = (v) => Array.isArray(v) || typeof v === 'string' ? v.length : Object.keys(obj(v)).length;
const provenance = new Set(['description_source', 'description_rendering', 'tooltips_source', 'tooltips_rendering', 'render_frame']);
const nativeKeys = new Set(['class', 'act', 'floor', 'ascension_level', 'current_hp', 'max_hp', 'gold', 'keys', 'relics', 'potions', 'screen_type', 'screen_state', 'deck', 'map', 'map_plan', 'combat_state', 'mechanics', 'narrative', 'seed', 'choice_list']);
const controlKeys = ['tutorial', 'selection_controls', 'reward_controls', 'card_reward_header', 'potion_controls', 'rest_controls', 'shop_controls', 'reward_navigation', 'card_selection_controls'];
const rules = {
    act: 'Use only offered actions from ready state. Read needed referenced details. One action per call; never replay unknown/applied_waiting. Refresh state after stale rejection.',
    cost: 'Use native displayed_cost_text and cost_components (energy/reserves/X/Pyre). Incomplete or unrendered costs unknown. Read target_playability and unplayable_reason before playing.',
    upgrade: 'Before acquisition or upgrade, read offered card upgrade_preview and relevant keyword details. Only next standard upgrade, not random/event/relic outcomes. Read selected_after before separate branch/tree confirmation.',
    event: 'Read and present current event body, situation and choices before acknowledgement. Supply observed reading_id and meaningful commentary; discuss result pages. Do not silently acknowledge incomplete text.',
    map: 'Map planning draws route, never moves character. Use offered map_id/revision and node IDs; stale revisions rejected. Native edges only, not predicted travel powers.',
    reward: 'Read reward_navigation before leaving: unclaimed rewards may be abandoned. Never auto-discard potion for space; discarding: separate destructive choice.'
};
const handVisible = (c, o) => (c.hand_complete === true || obj(o.combat_decision).hand_complete === true)
    && c.hand_complete !== false && obj(o.combat_decision).hand_complete !== false && obj(o.combat_decision).mode !== 'selection';
function scope(state) {
    const o = obj(state.observation), g = obj(o.game_state), c = obj(g.combat_state);
    const screen = String(obj(o.menu).screen ?? g.screen_type ?? 'unavailable');
    const actions = state.ready === true ? list(state.actions) : [];
    const hasAction = (prefix) => actions.some(a => String(obj(a).id).startsWith(prefix));
    const combat = Object.keys(c).length > 0 && !['EVENT', 'MAP', 'SHOP_SCREEN', 'SHOP_ROOM', 'REST', 'COMBAT_REWARD', 'BOSS_REWARD', 'CHEST', 'COMPLETE', 'GAME_OVER', 'VICTORY'].includes(screen)
        && (screen !== 'CARD_REWARD' || obj(o.combat_decision).mode === 'selection');
    const roots = {};
    const add = (key, value) => { if (present(value))
        roots[key] = value; };
    add('actions', actions);
    if (Object.keys(g).length) {
        add('player', { ...pick(g, ['class', 'act', 'floor', 'ascension_level', 'current_hp', 'max_hp', 'gold', 'keys', 'relics', 'potions']), ...(combat ? obj(c.player) : {}) });
        add('deck', g.deck);
        add('screen', g.screen_state);
        const mechanics = combat ? obj(c.player).mechanics ?? g.mechanics : g.mechanics;
        add('mechanics', mechanics);
        add('collection', obj(mechanics).collection);
        if (combat) {
            add('combat', { ...pick(c, ['turn', 'cards_discarded_this_turn', 'times_damaged']), hand_complete: handVisible(c, o) });
            add('card_in_play', c.card_in_play);
            add('combat_collection', obj(mechanics).combat_collection);
            if (handVisible(c, o))
                add('hand', c.hand);
            add('monsters', c.monsters);
            add('piles', pick(c, ['draw_pile', 'discard_pile', 'exhaust_pile', 'draw_pile_order_visible', 'draw_pile_order']));
        }
        if (screen === 'MAP') {
            add('map', g.map);
            add('map_plan', g.map_plan);
        }
        const narrative = obj(g.narrative), current = list(narrative.entries).filter(e => obj(e).currently_displayed !== false);
        add('dialogue', current);
        // Off-screen history is relevant only while interpreting current dialogue/event text.
        if (current.length || screen === 'EVENT')
            add('history', narrative);
    }
    add('menu', o.menu);
    const gated = {
        tutorial: true, potion_controls: Object.keys(g).length > 0,
        rest_controls: screen === 'REST' || hasAction('run.rest.'),
        shop_controls: screen === 'SHOP_SCREEN' || hasAction('run.shop.'),
        reward_controls: ['COMBAT_REWARD', 'CARD_REWARD', 'BOSS_REWARD'].includes(screen),
        reward_navigation: ['COMBAT_REWARD', 'CARD_REWARD', 'BOSS_REWARD'].includes(screen),
        card_reward_header: screen === 'CARD_REWARD',
        selection_controls: ['GRID', 'HAND_SELECT', 'CARD_REWARD'].includes(screen),
        card_selection_controls: ['GRID', 'HAND_SELECT', 'CARD_REWARD'].includes(screen)
    };
    for (const key of controlKeys)
        if (gated[key])
            add(key, o[key]);
    const relevantRules = { act: rules.act };
    if (combat)
        relevantRules.cost = rules.cost;
    if (['GRID', 'CARD_REWARD', 'SHOP_SCREEN', 'REST', 'EVENT'].includes(screen))
        relevantRules.upgrade = rules.upgrade;
    if (screen === 'EVENT')
        relevantRules.event = rules.event;
    if (screen === 'MAP')
        relevantRules.map = rules.map;
    if (roots.reward_navigation || roots.potion_controls)
        relevantRules.reward = rules.reward;
    if (Object.keys(roots).length)
        add('rules', relevantRules);
    const knownO = new Set(['in_game', 'game_state', 'combat_decision', 'menu', ...controlKeys]);
    return { screen, combat, roots, unsupported: Object.keys(g).some(k => !nativeKeys.has(k)) || Object.keys(o).some(k => !knownO.has(k)) };
}
function entry(ref, value, title) {
    const v = obj(value);
    return { ref, title: String(title ?? v.name ?? v.label ?? v.title ?? v.id ?? ref.split('/').at(-1)).slice(0, 64), count: size(value) };
}
function resolve(roots, ref) {
    let value = roots;
    for (const encoded of ref.split('/')) {
        let key;
        try {
            key = decodeURIComponent(encoded);
        }
        catch {
            throw new Error('Reference not available in current state.');
        }
        if (!key || ['__proto__', 'constructor', 'prototype'].includes(key) || value === null || typeof value !== 'object'
            || !Object.hasOwn(value, key) || (Array.isArray(value) && !/^(0|[1-9]\d*)$/.test(key)))
            throw new Error('Reference not available in current state.');
        value = value[key];
    }
    return value;
}
/** One shallow fragment; offset is a field/row index, or a UTF-16 text offset. */
function fragment(ref, value, offset, limit) {
    // An explicitly selected small card is a useful semantic unit. Do not force a
    // second call for every short keyword or upgrade field already inside its budget.
    if (offset === 0 && /^(?:(?:hand|deck|screen\/cards|collection\/cards|combat_collection\/cards)\/\d+|card_in_play)$/.test(ref)
        && value !== null && typeof value === 'object' && JSON.stringify(value).length <= 1600) {
        const clean = (v) => Array.isArray(v) ? v.map(clean) : v !== null && typeof v === 'object' ? Object.fromEntries(Object.entries(obj(v)).filter(([k]) => !provenance.has(k)).map(([k, x]) => [k, clean(x)])) : v;
        return { ref, format: 'card', data: clean(value), page_complete: true, next_offset: null };
    }
    if (typeof value === 'string') {
        let end = Math.min(value.length, offset + 1600);
        while (JSON.stringify({ ref, text: value.slice(offset, end) }).length > 2200 && end > offset + 1)
            end = offset + Math.floor((end - offset) * 0.8);
        if (end < value.length && /[\uD800-\uDBFF]/.test(value[end - 1]))
            end--;
        return { ref, format: 'text', text: value.slice(offset, end), total: value.length, offset, next_offset: end < value.length ? end : null };
    }
    if (value === null || typeof value !== 'object')
        return { ref, format: 'value', value };
    const array = Array.isArray(value), fields = array ? value.map((v, i) => [String(i), v]) : Object.entries(obj(value)).filter(([key]) => !provenance.has(key));
    const out = { ref, format: array ? 'index' : 'fields', total: fields.length, offset, next_offset: null };
    const data = Object.create(null), toc = [];
    let i = offset;
    for (; i < Math.min(fields.length, offset + limit); i++) {
        const [key, item] = fields[i], path = child(ref, key);
        if (path.length > 512 || ['__proto__', 'constructor', 'prototype'].includes(key)) {
            out.information_complete = false;
            out.unavailable_reason = 'field_reference_budget_or_unsupported_key';
            continue;
        }
        if (!array && (item === null || ['number', 'boolean'].includes(typeof item) || typeof item === 'string' && item.length <= 240))
            data[key] = item;
        else
            toc.push(entry(path, item, array ? undefined : key));
        if (JSON.stringify({ ...out, data, toc }).length > 2400) {
            delete data[key];
            if (toc.at(-1)?.ref === path)
                toc.pop();
            if (i === offset) {
                out.information_complete = false;
                out.unavailable_reason = 'field_budget_exceeded';
                i++;
            }
            break;
        }
    }
    out.next_offset = i < fields.length ? i : null;
    out.page_complete = offset === 0 && i === fields.length;
    if (!array)
        out.data = data;
    if (toc.length || array)
        out.toc = toc;
    return out;
}
export function readContext(state, refs, offset = 0, limit = 20) {
    if (!Array.isArray(refs) || refs.length < 1 || refs.length > 8 || refs.some(r => typeof r !== 'string' || r.length < 1 || r.length > 512)
        || !Number.isSafeInteger(offset) || offset < 0 || !Number.isInteger(limit) || limit < 1 || limit > 30)
        throw new Error('Invalid fragment request.');
    const { roots } = scope(state);
    return { session_id: state.session_id, state_id: state.state_id, fragments: [...new Set(refs)].map(ref => fragment(ref, resolve(roots, ref), offset, limit)) };
}
function brief(value, ref, keys) {
    const v = obj(value), data = keys ? pick(v, keys) : v, out = { ref };
    let more = false;
    for (const [key, item] of Object.entries(data)) {
        if (provenance.has(key))
            continue;
        if (item === null || typeof item === 'number' || typeof item === 'boolean' || typeof item === 'string' && item.length <= 240)
            out[key] = item;
        else
            more = true;
    }
    if (keys && Object.keys(v).some(k => !keys.includes(k) && !provenance.has(k)))
        more = true;
    if (more)
        out.details_required = true;
    return out;
}
/** Compact default decision; the catalog, not an arbitrary native-field dump, owns discovery. */
export function decision(state) {
    const { screen, combat, roots, unsupported } = scope(state), o = obj(state.observation), g = obj(o.game_state), c = obj(g.combat_state);
    const out = { session_id: state.session_id, state_id: state.state_id, ready: state.ready === true, screen };
    if (state.connection)
        out.connection = pick(obj(state.connection), ['status', 'pending_request_id']);
    if (unsupported)
        out.unsupported_information = true;
    if (roots.player)
        out.player = brief(roots.player, 'player', ['class', 'act', 'floor', 'current_hp', 'max_hp', 'gold', 'energy', 'block']);
    const actions = list(roots.actions);
    out.actions = actions.slice(0, 12).map((a, i) => brief(a, 'actions/' + i, ['id', 'label']));
    if (actions.length > 12)
        out.actions_more = 'actions';
    if (combat) {
        const hand = c.hand_complete === false ? [] : list(roots.hand), monsters = list(roots.monsters);
        out.combat = { hand_complete: c.hand_complete ?? obj(o.combat_decision).hand_complete ?? false,
            hand: hand.slice(0, 10).map((v, i) => brief(v, 'hand/' + i, ['id', 'uuid', 'name', 'type', 'upgrades', 'description', 'is_playable', 'displayed_cost_text', 'displayed_cost_complete', 'cost_components_complete', 'unplayable_reason', 'description_complete'])),
            monsters: monsters.slice(0, 8).map((v, i) => brief(v, 'monsters/' + i)) };
        if (roots.combat)
            Object.assign(obj(out.combat), roots.combat);
        if (hand.length > 10)
            obj(out.combat).hand_more = 'hand';
        if (monsters.length > 8)
            obj(out.combat).monsters_more = 'monsters';
    }
    if (roots.mechanics)
        out.mechanics = brief(roots.mechanics, 'mechanics');
    if (roots.screen)
        out.screen_state = brief(roots.screen, 'screen', ['body_text', 'event_id', 'event_name', 'for_upgrade']);
    if (screen === 'EVENT' && present(obj(roots.screen).event_reading)) {
        const reading = obj(obj(roots.screen).event_reading);
        out.event_reading = { ...brief(reading, 'screen/event_reading', ['reading_id', 'body_complete', 'options_complete', 'status']), body_ref: 'screen/event_reading/body_text',
            options: list(reading.options).slice(0, 12).map((value, i) => brief(value, 'screen/event_reading/options/' + i, ['text', 'disabled', 'choice_index'])) };
    }
    for (const key of ['shop_controls', 'rest_controls', 'reward_controls'])
        if (roots[key])
            out[key] = list(roots[key]).slice(0, 12).map((value, i) => brief(value, child(key, String(i))));
    if (['SHOP_SCREEN', 'CARD_REWARD', 'GRID', 'BOSS_REWARD'].includes(screen) && Array.isArray(obj(roots.screen).cards))
        out.offers = list(obj(roots.screen).cards).slice(0, 12).map((value, i) => brief(value, 'screen/cards/' + i, ['id', 'uuid', 'name', 'type', 'description', 'description_complete', 'displayed_cost_text', 'displayed_cost_complete']));
    for (const key of ['reward_navigation', 'selection_controls', 'card_selection_controls'])
        if (roots[key])
            out[key] = brief(roots[key], key);
    if (roots.map_plan)
        out.map_plan = brief(roots.map_plan, 'map_plan', ['map_id', 'revision', 'route_author', 'current_node', 'next_planned_node', 'status', 'editing']);
    if (roots.menu)
        out.menu = brief(roots.menu, 'menu');
    out.toc = Object.entries(roots).map(([ref, value]) => entry(ref, value, ref));
    return out;
}
export function conditionalDecision(state, knownView, mode = 'decision') {
    const current = decision(state), view = mode === 'map_plan' ? { ...pick(current, ['session_id', 'state_id', 'ready', 'screen', 'connection']), map_plan: current.map_plan ?? { status: 'unavailable' } } : current;
    const viewId = createHash('sha256').update(mode + JSON.stringify(view)).digest('hex').slice(0, 24);
    if (knownView === viewId)
        return { ...pick(view, ['session_id', 'state_id', 'ready', 'connection']), view_id: viewId, unchanged: true };
    return { ...view, view_id: viewId };
}
