import bdb
import threading
import os
 
class BdbRunWrapper(bdb.Bdb):
    '''
    Simple wrapper class to avoid name conflicts with threading.Thread.run .
     
    "Renames" bdb.Bdb.run to _run_wrapper
    '''
    def __init__(self,*args, **kwargs):
        bdb.Bdb.__init__(self, *args, **kwargs)
 
    def _run_wrapper(self, *args, **kwargs):
        bdb.Bdb.run(self, *args, **kwargs)
 
         
class Edb(BdbRunWrapper, threading.Thread):
    '''
    Eclipse Debugger class.
     
    Inherits from bdb.Bdb and threading.Thread
     
    Used to have safe cross-thread debugging functionality
    '''
    _file_to_run = None
 
    #: member storing current frame object while breakpoint hit.
    #: :note: This member is accessed by several threads, always use
    #:        _frame_lock threading.Lock object to assure thread-safety.
    _current_frame = None
     
    #: member storing "step" function to be called after breakpoint.
    #: :note: Once again, this member is used by multiple threads.
    #:        use _step_lock threading.Lock object to assure thread safety.
    _step_func = None
    _step_param = None
     
    def __init__(self, skip=None):
        '''
        Constructor calls base classes' constructors and
        sets up necessary members.
        '''
        BdbRunWrapper.__init__(self, skip)
        threading.Thread.__init__(self)
         
        self._continue_event = threading.Event()
         
        self._frame_lock = threading.Lock()
        self._step_lock = threading.Lock()
         
    def set_break(self,filename, lineno, temporary=0, cond=None, funcname=None, hitcount=0):
        '''
        Sets a new breakpoint with the given parameters.
     
        Overrides bdb.Bdb to add additional parameter hitcount.
         
        :param filename: filename for breakpoint
        :param lineno: linenumber for breakpoint
        :param temporary: flag to signalize if breakpoint is temporary
                          (delete after hit)
        :param cond: string with additional condition for breakpoint
        :param hitcount: hitcount after which the breakpoint is set active
        '''
        print "Setting breakpoint in Python. File {} @ line {}".format(filename, lineno)
        bp = self.get_break(filename, lineno)
        if not bp:
            bdb.Bdb.set_break(self, filename, lineno, temporary, cond, funcname)
         
        if hitcount:
            self.get_break(filename, lineno).ignore = hitcount
 
    def update_break(self, filename, lineno, temporary=0, cond=None, funcname=None, hitcount=0):
        '''
        Simply deletes breakpoint for file/line and adds a new one with given parameters.
         
        :param filename: filename for breakpoint
        :param lineno: linenumber for breakpoint
        :param temporary: flag to signalize if breakpoint is temporary
                          (delete after hit)
        :param cond: string with additional condition for breakpoint
        :param hitcount: hitcount after which the breakpoint is set active
        '''
        self.clear_break(filename, lineno)
        self.set_break(filename, lineno, temporary, cond, funcname, hitcount)
 
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
             
        # threading.Event is thread-safe
        # TODO: actually break ;)
        print "Breakpoint reached at {} line {}".format(filename, frame.f_lineno)
        self.set_continue()
        return 
        # self._continue_event.wait()
        # self._continue_event.clear()
         
        # Use double checked locking to assure thread safety
        if self._step_func:
            with self._step_lock:
                if self._step_func:
                    self._step_func(*(self._step_param or []))
                self._step_func = self._step_param = None
             
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
        print "Continuing with something"
        self._step_func = self.set_continue
     
    @_continue_wrapper
    def step_stepover(self):
        '''
        Simply stores self.set_step method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_until
        self._step_param = [self._current_frame]
 
    @_continue_wrapper
    def step_stepinto(self):
        '''
        Simply stores self.set_next method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_step
 
    @_continue_wrapper
    def step_stepout(self):
        '''
        Simply stores self.set_next method to member.
        Thread safety assured by _continue_wrapper.
        '''
        self._step_func = self.set_return
        self._step_param = [self._current_frame]
 
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
        if self._current_frame:
            with self._frame_lock:
                if self._current_frame:
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
        if self._current_frame:
            with self._frame_lock:
                if self._current_frame:
                    if self._current_frame.f_locals.get(var):
                        self._current_frame.f_locals.update({var:val})
                    else:
                        self._current_frame.f_globals.update({var:val})
                    return True
        return False
             
     
    def run(self):
        '''
        threading.Thread's run method will be started in new Thread.
         
        Executes the file given to self.start in this JythonInstance.
         
        :raises IOError: if file does not exist
        '''
        print "Run in Jython EDB called."
        if not self._file_to_run:
            raise ValueError("filename for run must not be empty")
        if not os.path.exists(self._file_to_run):
            raise IOError("file {} does not exist".format(self._file_to_run))
             
        cmd = 'execfile({})'.format(repr(self._file_to_run))
        self._run_wrapper(cmd)
         
        # Reset thread to be reusable
        threading.Thread.__init__(self)
 
    def start(self, filename):
        '''
        Override of threading.Thread.start.
         
        Take filename as parameter that will be used for debugging.
         
        :param filename: filename of file to be debugged
        '''
        print "Start in Jython EDB called"
        self._file_to_run = os.path.abspath(filename)
        threading.Thread.start(self)
 
eclipse_jython_debugger = Edb()