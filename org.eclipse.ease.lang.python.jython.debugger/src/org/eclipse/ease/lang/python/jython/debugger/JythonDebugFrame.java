package org.eclipse.ease.lang.python.jython.debugger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.ease.Script;
import org.eclipse.ease.debugging.IScriptDebugFrame;

public class JythonDebugFrame implements IScriptDebugFrame {
	private String mName;
	private int mLineNumber;
	private Script mScript;
	private Map<String, Object> mLocals = new HashMap<String, Object>();
		
	public JythonDebugFrame(String filename, int linenumber, Map<String, Object> locals) {
		mLineNumber = linenumber;
		mLocals = locals;
		String wsPath = "/" + ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toURI().relativize(new File(filename).toURI()).getPath();
		mScript = new Script(new JythonFile(wsPath));
		mName = wsPath;
	}
	
	/**
	 * Overrides File class to have accessible constructor.
	 * @author kloeschmartin
	 */
	@SuppressWarnings("restriction")
	private class JythonFile extends org.eclipse.core.internal.resources.File {
		public JythonFile(String fn) {
			super(new Path(fn), (Workspace) ResourcesPlugin.getWorkspace());
		}
	}
	
	@Override
	public int getLineNumber() {
		return mLineNumber;
	}

	IWorkspace ws = ResourcesPlugin.getWorkspace();
	@Override
	public Script getScript() {
		return mScript;
	}

	@Override
	public int getType() {
		return 0;
	}

	@Override
	public String getName() {
		return mName;
	}

	@Override
	public Map<String, Object> getVariables() {
		return mLocals;
	}
}
