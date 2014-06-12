/*******************************************************************************
 * Copyright (c) 2013 Martin Kloesch and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Christian Pontesegger - initial API
 *     Martin Kloesch - implementation
 *******************************************************************************/
package org.eclipse.ease.lang.python.jython.debugger.model;

import java.io.File;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.ease.Script;
import org.eclipse.ease.debugging.ScriptDebugTarget;
import org.eclipse.ease.debugging.events.EngineStartedEvent;
import org.eclipse.ease.debugging.events.IDebugEvent;
import org.eclipse.ease.lang.python.jython.debugger.BreakpointInfo;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.InteractiveInterpreter;

public class JythonDebugTarget extends ScriptDebugTarget {
	private InteractiveInterpreter mInterpreter;
	private String mPyDir;
	private PyObject mPyDebugger;
	
	/**
	 * Declarations for variables and function names in Jython:
	 */
	public static final String PyDebuggerName = "eclipse_jython_debugger";
	private static final String PySetBreakpointCmd = "set_break";
	private static final String PyRemoveBreakpointCmd = "clear_break";
	private static final String PyUpdateBreakpointCmd = "update_break";
	
	
	private static final String pyBreakpointType = "org.python.pydev.debug";
	
	/**
	 * Constructor for now only calls super constructor and 
	 * fires CreationEvent.
	 * 
	 * @param launch
	 * @param suspendOnStartup
	 */
	public JythonDebugTarget(final ILaunch launch, final boolean suspendOnStartup) {
		super(launch, suspendOnStartup);
		fireCreationEvent();
	}
	
	/**
	 * Setter method for mInterpreter.
	 * 
	 * TODO: Think if it would be to handle this via event...
	 * @param interpreter: InteractiveInterpreter to be set
	 */
	public void setInterpreter(InteractiveInterpreter interpreter) {
		mInterpreter = interpreter;
	}
	
	/**
	 * Setter method for mPyDir.
	 * 
	 * TODO: Think if it would be to handle this via event...
	 * @param pyDir: python directory to be set
	 */
	public void setPyDir(String pyDir) {
		mPyDir = pyDir;
	}
	
	/**
	 * Calls Python code on mInterpreter Jython engine to setup debugger.
	 * 
	 * @param interpreter: Jython interpreter to execute on.
	 * @note: not best solution, but otherwise we cannot setup debugger before engine is created.
	 */
	public void setupPythonObjects() {
		mInterpreter.execfile(new File(new File(mPyDir), "setup_debugger.py").getAbsolutePath());
		mPyDebugger = mInterpreter.get(PyDebuggerName);
		installBreakpoints();
	}
	
	/**
	 * Installs all breakpoints that match pyBreakpointType (PyDevBreakpoint for now)
	 */
	private void installBreakpoints() {
		IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(pyBreakpointType);
	         
		for (int i = 0; i < breakpoints.length; i++) {
			breakpointAdded(breakpoints[i]);
	    }
	}
	
	@Override
	public String getName() throws DebugException {
		return "EASE Jython Debugger";
	}

	// ************************************************************
	// IEventProcessor
	// ************************************************************

	@Override
	public void handleEvent(final IDebugEvent event) {
		if (event instanceof EngineStartedEvent) {
			setupPythonObjects();
		}
		super.handleEvent(event);
	}

	/**
	 * Getter methods for all matching breakpoints in given script.
	 * 
	 * Currently EASE Jython Debugger uses PyDev breakpoints, 
	 * this could change though.
	 */
	@Override
	protected IBreakpoint[] getBreakpoints(final Script script) {
		return DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(pyBreakpointType);
	}
	
	/**
	 * Adds a new Breakpoint to Jython Edb object.
	 * 
	 * Actual functionality see /python/edb.py -> Edb.set_break
	 * @param breakpoint: Breakpoint object to be added.
	 */
	@Override
	public void breakpointAdded(final IBreakpoint breakpoint) {
		// Create BreakpointInfo object to have easier access
		BreakpointInfo break_info = new BreakpointInfo(breakpoint);
		
		// Create parameters as PyObjects
		PyObject[] args = new PyObject[2];
		args[0] = new PyString(break_info.getFilename());
		args[1] = new PyInteger(break_info.getLinenumber());
		
		// Actually invoke set_break command
		mPyDebugger.invoke(PySetBreakpointCmd, args);
		
		System.out.println("JythonDebugTarget: successfully added Breakpoint.");
	}


	/**
	 * Removes an existing Breakpoint from Jython Edb object.
	 * 
	 * Actual functionality see JYTHON/Lib/bdb.py -> Bdb.clear_break
	 * @param breakpoint: Breakpoint to be removed.
	 */
	@Override
	public void breakpointRemoved(final IBreakpoint breakpoint, final IMarkerDelta delta) {
		// Create BreakpointInfo object to have easier access
		BreakpointInfo break_info = new BreakpointInfo(breakpoint);

		// Create parameters as PyObjects
		PyObject[] args = new PyObject[2];
		args[0] = new PyString(break_info.getFilename());
		args[1] = new PyInteger(break_info.getLinenumber());
		
		// Actually invoke clear_break command
		mPyDebugger.invoke(PyRemoveBreakpointCmd, args);
		
		System.out.println("JythonDebugTarget: successfully removed Breakpoint.");
	}

	/**
	 * Either updates an existing Breakpoint or creates new one in Jython Edb object.
	 * 
	 * Actual functionality see /python/edb.py -> Edb.update_break
	 * @param breakpoint: Breakpoint with changed information
	 */
	@Override
	public void breakpointChanged(final IBreakpoint breakpoint, final IMarkerDelta delta) {
		// Create BreakpointInfo object to have easier access
		BreakpointInfo break_info = new BreakpointInfo(breakpoint);

		// Create parameters as PyObjects
		PyObject[] args = new PyObject[2];
		args[0] = new PyString(break_info.getFilename());
		args[1] = new PyInteger(break_info.getLinenumber());
		
		// Actually invoke update_break command
		mPyDebugger.invoke(PyUpdateBreakpointCmd, args);
		
		System.out.println("JythonDebugTarget: successfully changed Breakpoint.");
	}
	
	@Override
	public boolean supportsBreakpoint(final IBreakpoint breakpoint) {
		if (breakpoint.getModelIdentifier().equals(pyBreakpointType)) {
			// TODO: perform actual check
		}
		return true;
	}

}
