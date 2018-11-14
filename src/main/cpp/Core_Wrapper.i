// Module Name
%module Core_Wrapper

// Included stuff
%{
#include "ValueHolder.hpp"
#include "TrackerCore.hpp"
#include "ContentsTrackerCore.hpp"
%}

// The prototypes to convert
%include "std_string.i"
%include "std_map.i"
%include "TrackerCore.hpp"
%include "ContentsTrackerCore.hpp"
