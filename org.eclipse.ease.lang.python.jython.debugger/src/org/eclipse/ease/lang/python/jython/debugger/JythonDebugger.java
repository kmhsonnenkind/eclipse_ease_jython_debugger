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

import java.io.File;
import java.util.Map;

import org.eclipse.debug.core.DebugEvent;
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
import org.eclipse.ease.debugging.events.ScriptReadyEvent;
import org.eclipse.ease.debugging.events.ScriptStartRequest;
import org.eclipse.ease.debugging.events.TerminateRequest;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.util.InteractiveInterpreter;

public class JythonDebugger implements IEventProcessor, IExecutionListener {
	private InteractiveInterpreter mInterpreter;
	private PyObject mPyDebugger;
	private String mPyDir;
	
	/**
	 * Declarations for variables and function names in Jython:
	 */
	public static final String PyDebuggerName = "eclipse_jython_debugger";
	private static final String PySetBreakpointCmd = "set_break";
	// private static final String PyRemoveBreakpointCmd = "clear_break";
	// private static final String PyUpdateBreakpointCmd = "update_break";
	
	private static final String PyStepoverCmd = "step_stepover";
	private static final String PyStepintoCmd = "step_stepinto";
	private static final String PyStepoutCmd = "step_stepout";
	private static final String PyResumeCmd = "step_continue";
	
	private JythonDebuggerEngine mEngine;
	private EventDispatchJob mDispatcher;

	public JythonDebugger(final JythonDebuggerEngine engine) {
		mEngine = engine;
		mEngine.addExecutionListener(this);
	}

	public void setInterpreter(InteractiveInterpreter interpreter) {
		mInterpreter = interpreter;
	}
	
	public void setPyDir(String pyDir) {
		mPyDir = pyDir;
	}

	private void setupJythonObjects() {
		mInterpreter.execfile(new File(new File(mPyDir), "setup_debugger.py").getAbsolutePath());
		mPyDebugger = mInterpreter.get(PyDebuggerName);
		mPyDebugger.invoke("set_dispatcher", Py.java2py(mDispatcher));
	}
	
	public void setDispatcher(final EventDispatchJob dispatcher) {
		mDispatcher = dispatcher;
	}

	private void fireDispatchEvent(final IDebugEvent event) {
		synchronized(mDispatcher) {
			if(mDispatcher != null)
				mDispatcher.addEvent(event);
		}
	}
	
	@Override
	public void notify(IScriptEngine engine, Script script, int status) {
		switch(status) {
		case ENGINE_START:
			setupJythonObjects();
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
		case SCRIPT_INJECTION_START:
			break;

		case SCRIPT_END:
		case SCRIPT_INJECTION_END:
			break;

		default:
			// unknown event
			break;
		}
	}
	
	@Override
	public void handleEvent(IDebugEvent event) {
		if (event instanceof ResumeRequest) {
			handleResumeRequest((ResumeRequest)event);
		} else if(event instanceof ScriptStartRequest) {
			
		} else if(event instanceof BreakpointRequest) {
			handleBreakpointRequest((BreakpointRequest)event);
		} else if(event instanceof GetStackFramesRequest) {
			System.out.println("    Jython Debugger:  Stack frames request");
		} else if(event instanceof TerminateRequest) {
			System.out.println("    Jython Debugger:  Terminate");
		}
	}
	
	/**
	 * Handles ResumeRequest from DebugTarget.
	 * 
	 * Depending on type of ResumeRequest different method in Jython will be called.
	 * Currently implemented cases are:
	 * 	 STEP_INTO
	 *   STEP_OVER
	 *   STEP_RETURN
	 *  
	 * If other type given, then resume will be called.
	 *  
	 * Response events will be raised from Jython itself.
	 *  
	 * @param event ResumeRequest containing necessary information for action to be performed
	 */
	private void handleResumeRequest(ResumeRequest event) {
		// Simply switch over the type and call according function
		switch (event.getType()) {
		case DebugEvent.STEP_INTO:
			mPyDebugger.invoke(PyStepintoCmd);
			break;
		case DebugEvent.STEP_OVER:
			mPyDebugger.invoke(PyStepoverCmd);
			break;
		case DebugEvent.STEP_RETURN:
			mPyDebugger.invoke(PyStepoutCmd);
			break;
		default:
			// TODO: think if it would be better to only handle resume request
			mPyDebugger.invoke(PyResumeCmd);
			break;
		}
	}
	
	/**
	 * Handles BreakpointRequest by setting Breakpoint in Jython.
	 * 
	 * @param event Event containing all necessary information for the desired Breakpoint.
	 */
	private void handleBreakpointRequest(BreakpointRequest event) {
		// Create parameters in correct format
		PyObject[] args = new PyObject[2];
		args[0] = Py.java2py(new BreakpointInfo(((BreakpointRequest)event).getBreakpoint()));
		args[1] = Py.java2py(event.getScript());
		
		mPyDebugger.invoke(PySetBreakpointCmd,args);
	}
	
	public void scriptReady(Script script) {
		
		ScriptReadyEvent ev = new ScriptReadyEvent(script, Thread.currentThread(), true);
		fireDispatchEvent(ev);
	}
	
	public class JythonDebugFrame implements IScriptDebugFrame {
		public JythonDebugFrame() {
			System.out.println("Creating new JythonDebugFrame");
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