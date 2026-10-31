// Host errors are not tool calls. Approval denials must stop the evaluation,
// never trigger a policy override or repeated attempts.
export function hostBlocker(event){
 const item=event.item;
 if(item?.type==='mcp_tool_call'&&item.error?.message?.includes('requires approval'))return 'approval_required';
 if(event.type==='turn.failed')return 'host_turn_failed';
 return null;
}
