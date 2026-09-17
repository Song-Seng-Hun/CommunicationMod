import test from 'node:test';
import assert from 'node:assert/strict';
import {publicParameterSchema,injectFixedArguments} from '../dist/action-projection.js';

test('required pinned const and one-value enum parameters stay server-side',()=>{
 const schema={type:'object',additionalProperties:false,properties:{
  map_id:{type:'string',const:'map-7'},revision:{type:'integer',const:12},reading_id:{type:'string',enum:['reading-4']},
  nodes:{type:'array',items:{type:'string'}},commentary:{type:'string'}},required:['map_id','revision','reading_id','nodes','commentary']};
 const visible=publicParameterSchema(schema);
 assert.deepEqual(Object.keys(visible.properties).sort(),['commentary','nodes']);
 assert.deepEqual(visible.required,['nodes','commentary']);
 assert.equal(JSON.stringify(visible).includes('map-7'),false);assert.equal(JSON.stringify(visible).includes('reading-4'),false);
 const args=injectFixedArguments(schema,{nodes:['1,2','2,3'],commentary:'현재 페이지를 설명함'});
 assert.deepEqual(args,{nodes:['1,2','2,3'],commentary:'현재 페이지를 설명함',map_id:'map-7',revision:12,reading_id:'reading-4'});
 assert.throws(()=>injectFixedArguments(schema,{map_id:'stale',nodes:[],commentary:'x'}),/Fixed action parameter/);
});

test('fully pinned required parameter objects disappear from public schema',()=>{
 const schema={type:'object',additionalProperties:false,properties:{token:{type:'string',const:'opaque'}},required:['token']};
 assert.deepEqual(publicParameterSchema(schema),{});
 assert.deepEqual(injectFixedArguments(schema,{}),{token:'opaque'});
});

test('optional const parameters stay public and are never silently injected',()=>{
 const schema={type:'object',additionalProperties:false,properties:{mode:{type:'string',const:'optional-mode'},value:{type:'integer'}},required:['value']};
 const visible=publicParameterSchema(schema);assert.equal(visible.properties.mode.const,'optional-mode');assert.deepEqual(visible.required,['value']);
 assert.deepEqual(injectFixedArguments(schema,{value:3}),{value:3});
 assert.deepEqual(injectFixedArguments(schema,{value:3,mode:'optional-mode'}),{value:3,mode:'optional-mode'});
});
