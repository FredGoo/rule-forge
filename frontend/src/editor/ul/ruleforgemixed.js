
import CodeMirror from 'codemirror';

CodeMirror.defineMode("rulemixed", function(config, parserConfig) {
  var ruleforgeMode = CodeMirror.getMode(config, "ruleforge");
  var javaMode = CodeMirror.getMode(config, "text/x-java");
  ruleforgeMode.block="import";
  function ruleforge(stream, state) {
    var style = ruleforgeMode.token(stream, ruleforgeMode.startState()),
    	cur=stream.current();
    if ("function"==cur){
    	 state.token = java;
         state.localMode = javaMode;
         state.localState = javaMode.startState()
    }else if("rule"==cur||"规则"==cur){
    	state.block="rule";
    }else if("loopRule"==cur||"循环规则"==cur){
      state.block="rule";
    }else if("loopStart"==cur||"循环开始前动作"==cur){
      state.block="then";
    }else if("loopTarget"==cur||"循环对象"==cur){
      state.block="if";
    }else if("if"==cur||"如果"==cur){
      state.block="if";
    }else if("then"==cur||"那么"==cur){
    	state.block="then";
    }else if("loopEnd"==cur||"循环结束后动作"==cur){
      state.block="then";
    }else if("end"==cur||"结束"==cur){
    	state.block="end";
    }else if(/^(importVariableLibrary|importConstantLibrary|importActionLibrary)$/.test(cur)){
    	state.block="import";
    }
    return style;
  };
  
  function java(stream, state) {
	var style=state.localMode.token(stream, state.localState),
		cur=stream.current();
    if (/rule|\u89C4\u5219/.test(cur)) {
      state.token = ruleforge;
      state.localState = state.localMode = null;
      stream.backUp(cur.length);
      ruleforgeMode.bloack="rule";
      return null;
    } if (/function/.test(cur)){
   	  state.token = ruleforge;
   	  state.localState = state.localMode = null;
      stream.backUp(cur.length);
      return null;
    }
    return style;
  };
 
  return {
    startState: function() {
      var state = ruleforgeMode.startState();
      return {token: ruleforge, localMode: null, localState: null, ruleforgeState: state};
    },

    copyState: function(state) {
      if (state.localState)
        var local = CodeMirror.copyState(state.localMode, state.localState);
      return {token: state.token, localMode: state.localMode, localState: local,
              ruleforgeState: CodeMirror.copyState(ruleforgeMode, state.ruleforgeState),block:state.block};
    },

    token: function(stream, state) {
      return state.token(stream, state);
    },

    indent: function(state, textAfter) {
      if (!state.localMode || /^\s*<\//.test(textAfter))
        return ruleforgeMode.indent(state.ruleforgeState, textAfter);
      else if (state.localMode.indent)
        return state.localMode.indent(state.localState, textAfter);
      else
        return CodeMirror.Pass;
    },

    innerMode: function(state) {
      return {state: state.localState || state.ruleforgeState, mode: state.localMode || ruleforgeMode};
    }
  };
}, "ruleforge", "text/x-java");
