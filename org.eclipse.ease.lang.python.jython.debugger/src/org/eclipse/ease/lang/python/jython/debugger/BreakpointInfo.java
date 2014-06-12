package org.eclipse.ease.lang.python.jython.debugger;

import java.io.File;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.LineBreakpoint;

/**
 * Helper class to parse IBreakpoint and have easy access to information
 * 
 * @author kloeschmartin
 */
public class BreakpointInfo {
	/**
	 * All necessary info for breakpoints (from Jython Edb point of view)
	 */
	private String mFilename;
	private int mLinenumber = -1;
	private String mCondition = null;
	private int mHitcount = 0;
	private boolean mTemporary = false;

	/**
	 * Parses breakpoint info from IBreakpoint to members
	 * 
	 * @param breakpoint
	 */
	public BreakpointInfo(final IBreakpoint breakpoint) {
		mFilename = breakpoint.getMarker().getResource().getFullPath()
				.toOSString();
		mFilename = new File(ResourcesPlugin.getWorkspace().getRoot()
				.getLocation().toFile(), mFilename).getAbsolutePath();

		if (breakpoint instanceof LineBreakpoint) {
			try {
				mLinenumber = ((LineBreakpoint) breakpoint).getLineNumber();
			} catch (CoreException e) {
				System.err
						.println("BreakpointInfor: did not get linenumber for breakpoint.");
				e.printStackTrace();
			}
		}
	}

	// ************************************************************
	// Getter methods
	// ************************************************************

	public String getFilename() {
		return mFilename;
	}

	public int getLinenumber() {
		return mLinenumber;
	}

	public String getCondition() {
		return mCondition;
	}

	public int getHitcount() {
		return mHitcount;
	}

	public boolean getTemporary() {
		return mTemporary;
	}
}
