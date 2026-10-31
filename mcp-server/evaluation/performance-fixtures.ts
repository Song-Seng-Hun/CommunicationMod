// Synthetic parsed-JSON-compatible snapshots; no live state or saves.
export function performanceState(count=10000,screen='MAP'){
 return {session_id:'performance',state_id:42,ready:true,connection:{status:'connected',pending_request_id:null,age_ms:0},
  actions:[{id:'run.map.plan',label:'계획',parameters:{}}],observation:{game_state:{screen_type:screen,current_hp:30,max_hp:60,
   mechanics:{collection:{count,cards:Array.from({length:count},(_,i)=>({id:`card-${i}`,name:`카드 ${i}`,description:'피해 6. 사용 불가 조건 유지.',displayed_cost_complete:false}))}},
   narrative:{entries:Array.from({length:count},(_,i)=>({id:i,text:'이전 대화',currently_displayed:false}))},
   map_plan:{map_id:'map-1',revision:3,route_author:'agent',current_node:'0,1',next_planned_node:'1,2',status:'ready',editing:false,route:['0,1','1,2']},
   combat_state:{hand_complete:true,hand:[{id:'a',description:'타격'}],monsters:[{name:'적',current_hp:6}]},screen_state:{body_text:'현재 본문'}},
   menu:{screen},tutorial:{text:'도움말'},reward_navigation:{unclaimed_rewards:2}}};
}

export function countedArray(values){
 let reads=0;
 const array=new Proxy(values,{get(target,key,receiver){if(typeof key==='string'&&/^(0|[1-9]\d*)$/.test(key))reads++;return Reflect.get(target,key,receiver);}});
 return {array,get reads(){return reads;}};
}
