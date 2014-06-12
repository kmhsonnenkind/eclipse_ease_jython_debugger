/*******************************************************************************
 * Copyright (c) 2014 Martin Kloesch and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Christian Pontesegger - initial API
 *     Martin Kloesch - implementation
 *******************************************************************************/
package org.eclipse.ease.lang.python.jython.debugger;

import java.util.Map;

import org.eclipse.ease.IExecutionListener;
import org.eclipse.ease.IScriptEngine;
import org.eclipse.ease.Script;
import org.eclipse.ease.debugging.EventDispatchJob;
import org.eclipse.ease.debugging.IEventProcessor;
import org.eclipse.ease.debugging.IScriptDebugFrame;
import org.eclipse.ease.debugging.events.BreakpointRequest;
import org.eclipse.ease.debugging.events.EngineStartedEvent;
import org.eclipse.ease.debugging.events.EngineTerminatedEvent;
import org.eclipse.ease.debugging.events.GetStackFramesRequest;
import org.eclipse.ease.debugging.events.IDebugEvent;
import org.eclipse.ease.debugging.events.ResumeRequest;
import org.eclipse.ease.debugging.events.ScriptStartRequest;
import org.eclipse.ease.debugging.events.TerminateRequest;

public class JythonDebugger implements IEventProcessor, IExecutionListener {
	private JythonDebuggerEngine mEngine;
	private EventDispatchJob mDispatcher;

	private boolean mShowDynamicCode;
	private Script mLastScript;

	public JythonDebugger(final JythonDebuggerEngine engine, final boolean showDynamicCode) {
		mEngine = engine;
		mEngine.addExecutionListener(this);
	
		mShowDynamicCode = showDynamicCode;
	}
		
	public void setDispatcher(final EventDispatchJob dispatcher) {
		mDispatcher = dispatcher;
	}

	private void fireDispatchEvent(final IDebugEvent event) {
		System.out.println("Jython Debugger firing event: " + event);
		synchronized(mDispatcher) {
			if(mDispatcher != null)
				mDispatcher.addEvent(event);
		}
	}
	
	@Override
	public void notify(IScriptEngine engine, Script script, int status) {
		switch(status) {
		case ENGINE_START:
			fireDispatchEvent(new EngineStartedEvent());
			break;
		case ENGINE_END:
			fireDispatchEvent(new EngineTerminatedEvent());
			
			// allow for garbage collection
			mEngine = null;
			synchronized(mDispatcher) {
				mDispatcher = null;
			}
			break;
		
		case SCRIPT_START:
			// fall through
		case SCRIPT_INJECTION_START:
			//if(mLastScript != null)
			//	throw new RuntimeException("LastScript has to be null");

			mLastScript = script;
			break;

		case SCRIPT_END:
			// fall through
		case SCRIPT_INJECTION_END:
			// nothing to do
			break;

		default:
			// unknown event
			break;
		}
	}

	@Override
	public void handleEvent(IDebugEvent event) {
		// TODO: actually implement
		System.out.println("Jython Debugger received event: " + event);
		
		if (event instanceof ResumeRequest) {
			System.out.println("    Jython Debugger:  going to resume.");
		} else if(event instanceof ScriptStartRequest) {
			System.out.println("    Jython Debugger:  going to start script");
		} else if(event instanceof BreakpointRequest) {
			final Script script = ((BreakpointRequest)event).getScript();
			System.out.println("    Jython Debugger:  Setting breakpoint for script " + script.getFile().toString());
		} else if(event instanceof GetStackFramesRequest) {
			System.out.println("    Jython Debugger:  Stack frames request");
		} else if(event instanceof TerminateRequest) {
			System.out.println("    Jython Debugger:  Terminate");
		}
	}
	
	public class JythonDebugFrame implements IScriptDebugFrame {
		public JythonDebugFrame() {
			System.out.println("Createing new JythonDebugFrame");
		}
		
		
		@Override
		public int getLineNumber() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public Script getScript() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getType() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public String getName() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Map<String, Object> getVariables() {
			// TODO Auto-generated method stub
			return null;
		}
	}
}