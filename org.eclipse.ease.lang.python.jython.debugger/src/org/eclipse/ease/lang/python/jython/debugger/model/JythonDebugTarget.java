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

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.ease.Script;
import org.eclipse.ease.debugging.ScriptDebugTarget;
import org.eclipse.ease.debugging.events.IDebugEvent;

public class JythonDebugTarget extends ScriptDebugTarget {
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

	@Override
	public String getName() throws DebugException {
		return "EASE Jython Debugger";
	}

	// ************************************************************
	// IEventProcessor
	// ************************************************************

	@Override
	public void handleEvent(final IDebugEvent event) {
		// TODO: remove
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
	
	@Override
	public void breakpointAdded(final IBreakpoint breakpoint) {
		System.out.println("I should now add a breakpoint");
	}
	

	@Override
	public void breakpointRemoved(final IBreakpoint breakpoint, final IMarkerDelta delta) {
		System.out.println("I should now remove a breakpoint");

	}

	@Override
	public void breakpointChanged(final IBreakpoint breakpoint, final IMarkerDelta delta) {
		System.out.println("I should now update a breakpoint");
	}
	
	@Override
	public boolean supportsBreakpoint(final IBreakpoint breakpoint) {
		// TODO Auto-generated method stub
		return true;
	}
}
