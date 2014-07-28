import bdb
import threading
import os
    
# Eclipse imports for communication with framework     
from org.eclipse.ease.debugging.events import SuspendedEvent
from org.eclipse.ease.debugging.events import ResumedEvent
from org.eclipse.debug.core import DebugEvent
from org.eclipse.ease.lang.python.jython.debugger import JythonDebugFrame
from java.lang import Thread
from java.util import HashMap
         
class Edb(bdb.Bdb):
    '''
    Eclipse Debugger class.
     
    Inherits from bdb.Bdb and threading.Thread
     
    Used to have safe cross-thread debugging functionality
    '''
    #: member storing current frame object while breakpoint hit.
    #: :note: This member is accessed by several threads, always use
    #:        _frame_lock threading.Lock object to assure thread-safety.
    _current_frame = None
     
    #: member storing "step" function to be called after breakpoint.
    #: :note: Once again, this member is used by multiple threads.
    #:        use _step_lock threading.Lock object to assure thread safety.
    _step_func = None
    _step_param = None
    
    _suspend_on_startup = False
    _current_file = None


    def __init__(self, breakpoints=[]):
        '''
        Constructor calls base class's constructors and
        sets up necessary members.
        '''
        bdb.Bdb.__init__(self)
        self._continue_event = threading.Event()
         
        self._frame_lock = threading.Lock()
        self._step_lock = threading.Lock()

    def set_debugger(self,debugger):
        '''
        Setter method for self._debugger.
        Since the actual object creation is handled by setup.py we need to set 
        the object here.
        
        :param org.eclipse.ease.lang.python.jython.debugger.JythonDebugger debugger:
            JythonDebugger object to handling communication with Eclipse.
        '''
        self._debugger = debugger

    def set_suspend_on_startup(self, suspend):
        '''
        Setter method for suspend_on_startup flag.
        Since the actual object creation is handled by setup.py we need to set 
        the object here.
    
        :param bool suspend:
            Value for _suspend_on_startup to be set.
        '''
        self._suspend_on_startup = suspend

    def set_break(self, breakpoint):
        '''
        Sets a new breakpoint with the given BreakpointInfo.
        If a breakpoint already exists old breakpoint will be deleted.
     
        Overrides bdb.Bdb to use EASE BreakpointInfo class.
         
        :param org.eclipse.ease.lang.python.jython.debugger.BreakpointInfo breakpoint:
            BreakpointInfo object containing all necessary information.
        '''
        # Parse BreakpointInfo to named variables for easier understanding
        filename = breakpoint.getFilename()
        lineno = breakpoint.getLinenumber()
        temporary = breakpoint.getCondition()
        cond = breakpoint.getCondition()
        funcname = None
        hitcount = breakpoint.getHitcount()
        
        self.clear_break(filename, lineno)
        bdb.Bdb.set_break(self, filename, lineno, temporary, cond, funcname)
        if hitcount:
            self.get_break(filename, lineno).ignore = hitcount
 
    def update_break(self, breakpoint):
        '''
        Only wraps to set_break.
        Necessary to have definition because it overrides bdb.Bdb.update_break
         
        :param org.eclipse.ease.lang.python.jython.debugger.BreakpointInfo breakpoint:
            BreakpointInfo object containing all necessary information.
        '''
        self.set_break(breakpoint)

    def dispatch_call(self, frame,arg):
        '''
        Method called before each function call in debugged program.
        
        Only checks if new file is being used and updates breakpoints accordingly.
        '''
        # TODO: Check if locking would interfere with performance
        fn = frame.f_code.co_filename
        if fn != self._current_file:
            if self._current_file and os.path.exists(self._current_file):
                self._debugger.checkBreakpoints(fn);
                
            self._current_file = fn
        return bdb.Bdb.dispatch_call(self, frame, arg)
 
    def user_line(self, frame):
        '''
        This method is called when debugger stops or breaks at line.
         
        Overrides bdb.Bdb.user_line method.
         
        Stores information about frame in member then
        waits for input from other thread.
         
        Thread-safe.
         
        :param frame: bdb.Frame object storing information about current line.
        '''
        filename = frame.f_code.co_filename
        if frame.f_lineno < 1:
            return
         
        with self._frame_lock:
            self._current_frame = frame

        if self._first:
            self._first = False
            self.set_continue()
            return
        
        # threading.Event is thread-safe
        self._break()
        self._continue()

    def _break(self):
        '''
        Function called when Debugger stops (breakpoint or step command).
        
        Calls JythonDebugger to signalize event to Eclipse and waits for user input.
        '''
        # Use suspend in JythonDebugger. Would also be possible to directly raise new SuspendedEvent
        self._debugger.fireSuspendEvent(Thread.currentThread(), self._get_stack_trace())
        
        # Wait for continuation from Eclipse
        self._continue_event.wait()
        self._continue_event.clear()

    def _get_stack_trace(self):
        '''
        Helper method returning current stack as List<JythonDebugFrame>.
        '''
        # Get stack in bdb.Bdb format
        bdb_stack, _ = self.get_stack(self._current_frame, None)
        stack = []
        
        # Convert stack to JythonDebugFrames
        for stack_entry in reversed(bdb_stack):
            frame, lineno = stack_entry
            filename = frame.f_code.co_filename

            
            # If file does not exist we can assume that it is a builtin and can be skipped.            
            if not os.path.exists(filename):
                break
            
            # Convert from JythonDictionary to Java.util.HashMap
            java_locals = HashMap()
            for key, val in frame.f_locals.items():
                java_locals.put(key,val)
                
            # Append frame to stack
            stack.append(JythonDebugFrame(filename, lineno, java_locals))
            
        return stack

    def _continue(self):
        '''
        Function called when Debugger is about to continue (step or resume).
        '''
        # Double checked locking to assure thread safety
        if self._step_func:
            with self._step_lock:
                if self._step_func:
                    self._step_func(*(self._step_param or []))
                self._step_func = self._step_param = None
        self._resume_event_type = -1
             
    def _continue_wrapper(func):
        '''
        Decorator calls function and sets the _continue_event and 
        assures thread-safety of member _step_func.
         
        This can be used for all execution handling methods like:
         
          * continue
          * step over
          * step into
          * quit
        '''
        def wrapped(self, *args, **kwargs):
            with self._step_lock:
                func(self, *args, **kwargs)
            self._continue_event.set()
        return wrapped
            
    @_continue_wrapper
    def step_continue(self):
        '''
        Simply stores self.set_continue method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_continue
        self._resume_event_type = 0x20
     
    @_continue_wrapper
    def step_stepover(self):
        '''
        Simply stores self.set_step method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_until
        self._step_param = [self._current_frame]
        self._resume_event_type = DebugEvent.STEP_OVER
 
    @_continue_wrapper
    def step_stepinto(self):
        '''
        Simply stores self.set_next method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_step
        self._resume_event_type = DebugEvent.STEP_INTO
 
    @_continue_wrapper
    def step_stepout(self):
        '''
        Simply stores self.set_next method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_return
        self._step_param = [self._current_frame]
        self._resume_event_type = DebugEvent.STEP_RETURN
 
    @_continue_wrapper
    def step_quit(self):
        '''
        Simply stores self.set_continue method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_quit
             
    def get_var(self, var):
        '''
        Thread-safe getter for local variables.
         
        Uses double checked locking to acquire variable by name.
         
        :note:  local variables have priority.
                if no local variable found, tries to get global.
        :param var: variable name as string
        :returns: * **Value of variable** if successful.
                  * **None** does not return if not at breakpoint
        '''
        # FIXME: not used yet <kmh>
        if self._current_frame:
            with self._frame_lock:
                if self._current_frame:
                    return self._current_frame.f_locals.get(var) or \
                           self._current_frame.f_globals.get(var)
         
    def list_vars(self):
        '''
        Thread-safe getter for map of local variables.
         
        Uses double checked locking to get all locals.
         
        :returns: * **Map<variable_name, variable_value**
                    returns map of all local variables if successful
                  * **None** if not at breakpoint
        '''
        # FIXME: not used yet <kmh>
        if self._current_frame:
            with self._frame_lock:
                if self._current_frame:
                    as_java = HashMap()
                    for key, val in self._current_frame.f_locals.items():
                        as_java.put(key,val)
                    return as_java
                    return self._current_frame.f_locals
             
    def set_var(self, var, val):
        '''
        Thread-safe setter for variables.
         
        Uses double checked locking to set variable by name.
         
        Checks if local variable can be updated, otherwise global will be set.
         
        :param var: variable name as string.
        :param val: value to be set.
        :returns: * **True** if successful
                  * **False** in case of error
        '''
        # FIXME: not used yet <kmh>
        if self._current_frame:
            with self._frame_lock:
                if self._current_frame:
                    if self._current_frame.f_locals.get(var):
                        self._current_frame.f_locals.update({var:val})
                    else:
                        self._current_frame.f_globals.update({var:val})
                    return True
        return False
             
    def run(self, file_to_run):
        '''
        Executes the file given using the bdb.Bdb.run method.
         
        :raises ValueError: if empty filename given.
        :raises IOError: if file does not exist.
        '''
        self.reset()
        if not file_to_run:
            raise ValueError("filename for run must not be empty")
        if not os.path.exists(file_to_run):
            raise IOError("file {} does not exist".format(file_to_run))

        self._first = True
        cmd = 'execfile({})'.format(repr(file_to_run))
        bdb.Bdb.run(self, cmd)

 
eclipse_jython_debugger = Edb()