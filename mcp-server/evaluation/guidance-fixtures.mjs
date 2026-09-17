// Independent, reviewed source inventory: never derive expectations from the catalog.
export const providerLiterals = {
 'devclient/McpBridge.java':['run.','menu.','acknowledge_event_reading'],
 'devclient/MenuRequestInbox.java':['menu.','run.','acknowledge_event_reading'],
 'map/MapPlanner.java':['run.map.plan'],
 'observation/GridSelectionUi.java':['run.grid.','run.grid.confirm','run.grid.cancel'],
 'observation/HandSelectionUi.java':['run.hand.select.','run.hand.deselect.','run.hand.confirm'],
 'observation/MenuUi.java':['menu.','menu.panel.','menu.character.','run.embark'],
 'observation/PotionUi.java':['run.potion.discard.','run.potion.use.'],
 'observation/RewardUi.java':['run.reward.','run.reward.proceed','run.card_reward.','run.card_reward.skip','run.card_reward.bowl'],
 'observation/RoomUi.java':['run.shop.enter','run.room.proceed','run.chest.open','run.rest.','run.rest.proceed','run.shop.card.','run.shop.relic.','run.shop.potion.','run.shop.purge','run.shop.leave','run.boss_relic.','run.boss_relic.skip'],
 'observation/RunUi.java':['run.event.','run.map.boss','run.map.','run.tutorial.confirm','run.end_turn','run.play.'],
 'observation/UpgradeChoiceUi.java':['run.grid.upgrade.'],
 'protocol/EventReadingActions.java':['acknowledge_event_reading'],
};
export const actionFactories = ['map/MapPlanner.java','observation/HandSelectionUi.java','observation/MenuUi.java','observation/RewardUi.java','observation/RoomUi.java','observation/RunUi.java','protocol/CombatActions.java','protocol/EventReadingActions.java'];
// This legacy provider is explicitly inventoried but rejected by the live MCP bridge.
export const legacyActions = ['end_turn','select:','confirm_selection','cancel_selection','play:'];
export const controlRoots = ['tutorial','selection_controls','reward_controls','card_reward_header','potion_controls','rest_controls','shop_controls','reward_navigation','card_selection_controls'];
export const contextRoots = ['actions','player','deck','screen','mechanics','collection','combat','card_in_play','combat_collection','hand','monsters','piles','map','map_plan','dialogue','history','menu','rules',...controlRoots];
export const dynamicContracts = [
 {file:'observation/RewardPolicy.java',pattern:'STANDARD=new HashSet.*?Arrays\\.asList\\(([^)]*)\\)',values:['GOLD','STOLEN_GOLD','CARD','RELIC','POTION','EMERALD_KEY','SAPPHIRE_KEY']},
 {file:'observation/RewardUi.java',pattern:'cardKind\\(CardRewardScreen owner\\).*?new String\\[\\]\\{([^}]+)\\}',values:['draft','discovery','chooseOne','codex']},
 {file:'observation/GridSelectionUi.java',pattern:'String op=([^;]+);',values:['deselect','select']},
 {file:'observation/UpgradeChoiceUi.java',pattern:'String kind=([^;]+);',values:['branch','normal','run.grid.upgrade.']},
 {file:'observation/MenuUi.java',pattern:null,values:['PLAY','RESUME_GAME','PLAY_NORMAL','PLAY_EVIL']},
];
// Mock observations only. IDs exercise dispatch coverage; capsules must bind live values.
export const coverageFixtures = [
 ['lifecycle.status','tool.status'],['lifecycle.start','tool.start'],['lifecycle.setup','setup'],
 ['state.decision','tool.state'],['context.read','tool.context'],['action.simple','action.empty'],
 ['action.parameters','action.parameters'],['combat.play','combat.targeted','run.play.fixture.0'],
 ['combat.play','combat.untargeted','run.play.fixture.-1'],['combat.end','combat.end','run.end_turn'],
 ['cards.inspect','cards.inspect'],['cards.collections','collection.deck'],['cards.collections','collection.piles'],
 ['cards.collections','collection.collector'],['cards.collections','collection.combat'],
 ...['energy','reserves','X','Pyre','encode','ghostflames','stasis','gremlins','spells','stance','slime','sockets','unknown_origin','temporary_hp'].map(k=>['resources.public','resource.'+k]),
 ...['GOLD','STOLEN_GOLD','CARD','RELIC','POTION','EMERALD_KEY','SAPPHIRE_KEY'].map(k=>['reward.take','reward.'+k,'run.reward.0']),
 ...['reward','draft','discovery','chooseOne','codex'].map(k=>['reward.take','card_mode.'+k,'run.card_reward.fixture']),
 ['reward.skip','reward.skip','run.card_reward.skip'],['reward.bowl','reward.bowl','run.card_reward.bowl'],
 ['reward.proceed','reward.proceed','run.reward.proceed'],
 ['boss.take','boss.take','run.boss_relic.0'],['boss.skip','boss.skip','run.boss_relic.skip'],['chest.open','chest.open','run.chest.open'],
 ['potion.use','potion.targeted','run.potion.use.0.1'],['potion.use','potion.untargeted','run.potion.use.0'],['potion.discard','potion.discard','run.potion.discard.0'],
 ...['enter','card','relic','potion','purge','leave'].map(k=>['shop.'+k,'shop.'+k,'run.shop.'+k+(['card','relic','potion'].includes(k)?'.fixture':'')]),
 ['rest.option','rest.option','run.rest.0'],['rest.proceed','rest.proceed','run.rest.proceed'],
 ...['select','deselect','confirm','cancel'].map(k=>['selection.grid.'+k,'grid.'+k,'run.grid.'+k+(['select','deselect'].includes(k)?'.fixture':'')]),
 ...['select','deselect','confirm'].map(k=>['selection.hand.'+k,'hand.'+k,'run.hand.'+k+(k==='confirm'?'':'.fixture')]),
 ['selection.upgrade','upgrade.normal','run.grid.upgrade.normal'],['selection.upgrade','upgrade.branch','run.grid.upgrade.branch'],['selection.upgrade','upgrade.tree','run.grid.upgrade.0'],
 ['event.ack','event.ack','acknowledge_event_reading'],['event.choice','event.choice','run.event.0'],
 ['narrative.history','narrative.history'],['map.plan','map.plan','run.map.plan'],
 ['map.travel','map.node','run.map.1.2'],['map.travel','map.boss','run.map.boss'],
 ['menu.navigate','menu.play','menu.play'],['menu.navigate','menu.resume','menu.resume_game'],
 ['menu.panel','menu.normal','menu.panel.PLAY_NORMAL'],['menu.panel','menu.evil','menu.panel.PLAY_EVIL'],
 ['menu.character','menu.character','menu.character.fixture'],['menu.embark','menu.embark','run.embark'],
 ['tutorial.confirm','tutorial.confirm','run.tutorial.confirm'],['room.proceed','room.proceed','run.room.proceed'],
 ['recovery.stale','recovery.stale'],['recovery.receipt','tool.request'],['recovery.unsupported','recovery.unsupported'],
].map(([capability,cover,action])=>({capability,cover,...(action?{action}:{})}));

// Required coverage is a second, independent contract, NOT generated from
// coverageFixtures or capabilities. Removing both cannot erase these obligations.
// Each row joins a reviewed Java literal to its semantic owner and required cases.
export const requiredProviderCoverage = {
 'map/MapPlanner.java': [
  ['run.map.plan','map.plan',['map.plan']],
 ],
 'observation/GridSelectionUi.java': [
  ['run.grid.','selection.grid.select',['grid.select']],
  ['run.grid.','selection.grid.deselect',['grid.deselect']],
  ['run.grid.confirm','selection.grid.confirm',['grid.confirm']],
  ['run.grid.cancel','selection.grid.cancel',['grid.cancel']],
 ],
 'observation/HandSelectionUi.java': [
  ['run.hand.select.','selection.hand.select',['hand.select']],
  ['run.hand.deselect.','selection.hand.deselect',['hand.deselect']],
  ['run.hand.confirm','selection.hand.confirm',['hand.confirm']],
 ],
 'observation/MenuUi.java': [
  ['menu.','menu.navigate',['menu.play','menu.resume']],
  ['menu.panel.','menu.panel',['menu.normal','menu.evil']],
  ['menu.character.','menu.character',['menu.character']],
  ['run.embark','menu.embark',['menu.embark']],
 ],
 'observation/PotionUi.java': [
  ['run.potion.use.','potion.use',['potion.targeted','potion.untargeted']],
  ['run.potion.discard.','potion.discard',['potion.discard']],
 ],
 'observation/RewardUi.java': [
  ['run.reward.','reward.take',['reward.GOLD','reward.STOLEN_GOLD','reward.CARD','reward.RELIC','reward.POTION','reward.EMERALD_KEY','reward.SAPPHIRE_KEY']],
  ['run.reward.proceed','reward.proceed',['reward.proceed']],
  ['run.card_reward.','reward.take',['card_mode.reward','card_mode.draft','card_mode.discovery','card_mode.chooseOne','card_mode.codex']],
  ['run.card_reward.skip','reward.skip',['reward.skip']],
  ['run.card_reward.bowl','reward.bowl',['reward.bowl']],
 ],
 'observation/RoomUi.java': [
  ['run.shop.enter','shop.enter',['shop.enter']],
  ['run.shop.card.','shop.card',['shop.card']],
  ['run.shop.relic.','shop.relic',['shop.relic']],
  ['run.shop.potion.','shop.potion',['shop.potion']],
  ['run.shop.purge','shop.purge',['shop.purge']],
  ['run.shop.leave','shop.leave',['shop.leave']],
  ['run.room.proceed','room.proceed',['room.proceed']],
  ['run.chest.open','chest.open',['chest.open']],
  ['run.rest.','rest.option',['rest.option']],
  ['run.rest.proceed','rest.proceed',['rest.proceed']],
  ['run.boss_relic.','boss.take',['boss.take']],
  ['run.boss_relic.skip','boss.skip',['boss.skip']],
 ],
 'observation/RunUi.java': [
  ['run.event.','event.choice',['event.choice']],
  ['run.map.boss','map.travel',['map.boss']],
  ['run.map.','map.travel',['map.node']],
  ['run.tutorial.confirm','tutorial.confirm',['tutorial.confirm']],
  ['run.end_turn','combat.end',['combat.end']],
  ['run.play.','combat.play',['combat.targeted','combat.untargeted']],
 ],
 'observation/UpgradeChoiceUi.java': [
  ['run.grid.upgrade.','selection.upgrade',['upgrade.normal','upgrade.branch','upgrade.tree']],
 ],
 'protocol/EventReadingActions.java': [
  ['acknowledge_event_reading','event.ack',['event.ack']],
 ],
};
// Transport allowlists do not provide actions; these are the only exclusions.
export const transportInventoryFiles = ['devclient/McpBridge.java','devclient/MenuRequestInbox.java'];
// Every public root has an explicit owner, including all nine native controls.
// These are semantic dependencies, not runtime selector root gates.
export const requiredRootCoverage = {
 actions: [['action.simple',['action.empty']],['action.parameters',['action.parameters']]],
 player: [['resources.public',['resource.energy','resource.temporary_hp']]],
 deck: [['cards.collections',['collection.deck']],['cards.inspect',['cards.inspect']]],
 screen: [['cards.inspect',['cards.inspect']]],
 mechanics: [['resources.public',['resource.reserves','resource.X','resource.Pyre','resource.encode','resource.ghostflames','resource.stasis','resource.gremlins','resource.spells','resource.stance','resource.slime','resource.sockets','resource.unknown_origin']]],
 collection: [['cards.collections',['collection.collector']]],
 combat: [['combat.play',['combat.targeted','combat.untargeted']],['combat.end',['combat.end']]],
 card_in_play: [['cards.inspect',['cards.inspect']]],
 combat_collection: [['cards.collections',['collection.combat']]],
 hand: [['combat.play',['combat.targeted','combat.untargeted']]],
 monsters: [['combat.play',['combat.targeted']]],
 piles: [['cards.collections',['collection.piles']]],
 map: [['map.travel',['map.node','map.boss']],['map.plan',['map.plan']]],
 map_plan: [['map.plan',['map.plan']]],
 dialogue: [['narrative.history',['narrative.history']],['event.ack',['event.ack']]],
 history: [['narrative.history',['narrative.history']]],
 menu: [['menu.navigate',['menu.play','menu.resume']],['menu.panel',['menu.normal','menu.evil']],['menu.character',['menu.character']],['menu.embark',['menu.embark']]],
 rules: [['context.read',['tool.context']],['state.decision',['tool.state']]],
 tutorial: [['tutorial.confirm',['tutorial.confirm']]],
 selection_controls: [['selection.grid.select',['grid.select']],['selection.grid.deselect',['grid.deselect']],['selection.grid.confirm',['grid.confirm']],['selection.grid.cancel',['grid.cancel']],['selection.hand.select',['hand.select']],['selection.hand.deselect',['hand.deselect']],['selection.hand.confirm',['hand.confirm']],['selection.upgrade',['upgrade.normal','upgrade.branch','upgrade.tree']]],
 reward_controls: [['reward.take',['reward.GOLD','reward.STOLEN_GOLD','reward.CARD','reward.RELIC','reward.POTION','reward.EMERALD_KEY','reward.SAPPHIRE_KEY']]],
 card_reward_header: [['reward.take',['card_mode.reward','card_mode.draft','card_mode.discovery','card_mode.chooseOne','card_mode.codex']],['reward.skip',['reward.skip']],['reward.bowl',['reward.bowl']]],
 potion_controls: [['potion.use',['potion.targeted','potion.untargeted']],['potion.discard',['potion.discard']]],
 rest_controls: [['rest.option',['rest.option']],['rest.proceed',['rest.proceed']]],
 shop_controls: [['shop.enter',['shop.enter']],['shop.card',['shop.card']],['shop.relic',['shop.relic']],['shop.potion',['shop.potion']],['shop.purge',['shop.purge']],['shop.leave',['shop.leave']]],
 reward_navigation: [['reward.proceed',['reward.proceed']],['room.proceed',['room.proceed']]],
 card_selection_controls: [['reward.take',['card_mode.reward','card_mode.draft','card_mode.discovery','card_mode.chooseOne','card_mode.codex']]],
};

