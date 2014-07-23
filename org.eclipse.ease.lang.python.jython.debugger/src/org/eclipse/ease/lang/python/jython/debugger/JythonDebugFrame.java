package org.eclipse.ease.lang.python.jython.debugger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.ease.Script;
import org.eclipse.ease.debugging.IScriptDebugFrame;
import org.python.core.PyInteger;
import org.python.core.PyObject;
import org.python.core.PyString;

public class JythonDebugFrame implements IScriptDebugFrame {
	private String mName;
	private int mLineNumber;
	private Script mScript;
	private Map<String, Object> mLocals = new HashMap<String, Object>();
		
	public JythonDebugFrame(Script script, PyString name, PyInteger lineNumber, Map<String, Object> locals) {
		mScript = script;
		mName = name.asString();
		mLineNumber = lineNumber.asInt();
		mLocals = locals;
//		Iterator<Entry<PyString, PyObject>> it = locals.entrySet().iterator();
//		while(it.hasNext()) {
//			Map.Entry<PyString, PyObject> pairs = (Map.Entry<PyString, PyObject>) it.next();
//			mLocals.put(pairs.getKey().asString(), pairs.getValue());
//			it.remove();
//		}
	}

	@Override
	public int getLineNumber() {
		return mLineNumber;
	}

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
