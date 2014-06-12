_ImportErrorString = "No module name {}"
def _is_javapackage(module):
    return repr(type(module)) == "<type 'javapackage'>"

from edb import Edb
if _is_javapackage(Edb): raise ImportError(_ImportErrorString.format('edb'))

eclipse_jython_debugger = Edb()
