// Frozen synthetic reading smoke test. No secrets, live state or gameplay.
export const data={session_id:'001',state_id:42,receipt:{request_id:'retry-001',outcome:'unknown',retry_allowed:false},
 fragment:{ref:'collection/cards',offset:20,total:60,next_offset:40,page_complete:false,
  toc:Array.from({length:20},(_,i)=>({ref:`collection/cards/${i+20}`,title:`카드 ${i+20}`,
   displayed_cost_text:i===2?'001':i===3?'X':'0',displayed_cost_complete:i!==4,available:i!==4&&i!==6,
   reason:i===4?'비용 미확인. 추정 금지.':i===6?'사용 불가: 대상 없음.':'',note:i===5?'첫 줄\n둘째\t줄':i===7?'null':''}))}};
export const questions=[
 'q1: May request retry-001 be replayed? Return a Boolean.',
 'q2: Which offset reads the next page? Return a number.',
 'q3: What is the last row ref on this page? Return the exact string.',
 'q4: What is displayed_cost_text for collection/cards/22? Preserve its type and all characters.',
 'q5: Is it safe to conclude card 24 is free from its displayed cost alone? Return a Boolean.',
 'q6: Why is card 26 unavailable? Return the exact reason string.',
 'q7: What is the note for card 25? Preserve decoded newline and tab characters in the JSON answer.',
 'q8: What is the note for card 27? Preserve its type.',
 'q9: How many rows remain after this page? Return a number.',
 'q10: What is session_id? Preserve its type and all characters.'
];
export const expected={q1:false,q2:40,q3:'collection/cards/39',q4:'001',q5:false,q6:'사용 불가: 대상 없음.',q7:'첫 줄\n둘째\t줄',q8:'null',q9:20,q10:'001'};
